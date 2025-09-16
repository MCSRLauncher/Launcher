package com.redlimerl.mcsrlauncher.data.instance

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.data.instance.mcsrranked.MCSRRankedPackType
import com.redlimerl.mcsrlauncher.data.meta.LauncherTrait
import com.redlimerl.mcsrlauncher.data.meta.MetaUniqueID
import com.redlimerl.mcsrlauncher.data.meta.file.MetaVersionFile
import com.redlimerl.mcsrlauncher.data.meta.file.MinecraftMetaFile
import com.redlimerl.mcsrlauncher.data.meta.file.SpeedrunModsMetaFile
import com.redlimerl.mcsrlauncher.data.meta.mod.SpeedrunModMeta
import com.redlimerl.mcsrlauncher.data.meta.mod.SpeedrunModTrait
import com.redlimerl.mcsrlauncher.data.meta.mod.SpeedrunModVersion
import com.redlimerl.mcsrlauncher.gui.InstanceCrashLogGui
import com.redlimerl.mcsrlauncher.gui.InstanceOptionGui
import com.redlimerl.mcsrlauncher.gui.component.LogViewerPanel
import com.redlimerl.mcsrlauncher.instance.InstanceProcess
import com.redlimerl.mcsrlauncher.instance.LegacyLaunchFixer
import com.redlimerl.mcsrlauncher.instance.mod.ModCategory
import com.redlimerl.mcsrlauncher.instance.mod.ModData
import com.redlimerl.mcsrlauncher.instance.mod.ModDownloadMethod
import com.redlimerl.mcsrlauncher.launcher.InstanceManager
import com.redlimerl.mcsrlauncher.launcher.MetaManager
import com.redlimerl.mcsrlauncher.network.FileDownloader
import com.redlimerl.mcsrlauncher.util.AssetUtils
import com.redlimerl.mcsrlauncher.util.I18n
import com.redlimerl.mcsrlauncher.util.LauncherWorker
import com.redlimerl.mcsrlauncher.util.SpeedrunUtils
import io.github.z4kn4fein.semver.toVersionOrNull
import kotlinx.coroutines.*
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.JsonNames
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonPrimitive
import org.apache.commons.io.FileUtils
import java.awt.Window
import java.io.File
import java.io.IOException
import java.net.URL
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributeView
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JDialog
import javax.swing.JOptionPane
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText


@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class BasicInstance(
    @JsonNames("id", "name") var id: String,
    var displayName: String,
    var group: String = "",
    var minecraftVersion: String,
    var lwjglVersion: LWJGLVersionData,
    var fabricVersion: FabricVersionData?,
    var mcsrRankedType: MCSRRankedPackType? = null,
    var options: InstanceOptions = InstanceOptions(),
    var playTime: Long = 0,
) {
    @Transient
    var optionDialog: InstanceOptionGui? = null

    @Transient
    var lastLaunch: Long = System.currentTimeMillis()

    @Transient
    var clearingWorlds: Boolean = false

    @Transient
    val logViewerPanel: LogViewerPanel = LogViewerPanel(this.getGamePath())

    fun getInstancePath(): Path {
        return InstanceManager.INSTANCES_PATH.resolve(id)
    }

    fun getGamePath(): Path {
        return this.getInstancePath().resolve(".minecraft")
    }

    fun getModsPath(): Path {
        return this.getGamePath().resolve("mods")
    }

    fun getWorldsPath(): Path {
        return this.getGamePath().resolve("saves")
    }

    fun getNativePath(): Path {
        return this.getInstancePath().resolve("natives")
    }

    fun onCreate() {
        this.getInstancePath().toFile().mkdirs()
        this.getGamePath().toFile().mkdirs()
        this.getModsPath().toFile().mkdirs()
    }

    fun onLaunch() {
        MCSRLauncher.LOGGER.info("Launched instance: $id")
        InstanceManager.refreshInstanceList()
        logViewerPanel.let { getProcess()?.syncLogViewer(it) }
        optionDialog?.setLauncherLaunched(true)
        lastLaunch = System.currentTimeMillis()
    }

    fun onProcessExit(code: Int, exitByUser: Boolean) {
        MCSRLauncher.LOGGER.info("Exited instance: $id ($code)")
        FileUtils.deleteDirectory(this.getNativePath().toFile())
        InstanceManager.refreshInstanceList()
        optionDialog?.setLauncherLaunched(false)
        playTime += (System.currentTimeMillis() - lastLaunch) / 1000
        this.save()

        if (code != 0) {
            val optionDialog = openOptionDialog()
            optionDialog.openTab(4)
            if (!exitByUser) {
                val result = JOptionPane.showConfirmDialog(optionDialog, I18n.translate("message.upload_crash_log"), I18n.translate("text.warning"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
                if (result == JOptionPane.YES_OPTION) {
                    val log = getGamePath().resolve("logs/latest.log").readText()
                    val crashLogDialog = InstanceCrashLogGui(null, log)
                    crashLogDialog.isVisible = true
                }
            }
        }
    }

    fun install(worker: LauncherWorker) {
        worker.setState("installing game files and libraries...")

        // Minecraft
        val minecraftMeta = MetaManager.getVersionMeta<MinecraftMetaFile>(MetaUniqueID.MINECRAFT, this.minecraftVersion, worker) ?: throw IllegalStateException("Minecraft version $minecraftVersion does not exist")
        minecraftMeta.install(worker)
        if (minecraftMeta.traits.contains(LauncherTrait.LEGACY_LAUNCH)) {
            LegacyLaunchFixer.assetFix(minecraftMeta.assetIndex, this.getGamePath().resolve("resources"), worker)
        }

        // LWJGL
        (MetaManager.getVersionMeta<MetaVersionFile>(this.lwjglVersion.type, this.lwjglVersion.version, worker)
            ?: throw IllegalStateException("LWJGL version $minecraftVersion does not exist")).install(worker)

        // Fabric Loader / Intermediary
        val fabric = this.fabricVersion
        if (fabric != null) {
            (MetaManager.getVersionMeta<MetaVersionFile>(MetaUniqueID.FABRIC_LOADER, fabric.loaderVersion, worker)
                ?: throw IllegalStateException("Fabric Loader version $minecraftVersion does not exist")).install(worker)
            worker.properties["intermediary-type"] = fabric.intermediaryType.name
            (MetaManager.getVersionMeta<MetaVersionFile>(MetaUniqueID.FABRIC_INTERMEDIARY, fabric.intermediaryVersion, worker)
                ?: throw IllegalStateException("Fabric Intermediary version ${fabric.intermediaryType} for ${fabric.intermediaryVersion} does not exist")).install(worker)
        }

        if (this.mcsrRankedType != null && fabric != null && this.minecraftVersion == "1.16.1") {
            SpeedrunUtils.getLatestMCSRRankedVersion(worker)?.download(this, worker)
        }

        MCSRLauncher.LOGGER.info("Installed all game files and libraries!")
    }

    fun launchInstance(worker: LauncherWorker) {
        if (this.isRunning()) return

        InstanceProcess(this@BasicInstance).start(worker)
    }

    fun launchWithDialog() {
        if (this.isRunning()) return
        object : LauncherWorker(MCSRLauncher.MAIN_FRAME, I18n.translate("instance.launching"), I18n.translate("message.loading") + "...") {
            override fun work(dialog: JDialog) {
                if (options.autoModUpdates) {
                    val updates = getSpeedRunModUpdates(this)
                    if (updates.isNotEmpty()) updateSpeedrunMods(this)
                }
                this.setState(I18n.translate("text.download.assets") + "...")
                install(this)
                this.setProgress(null)

                if (options.clearBeforeLaunch) {
                    this.setState(I18n.translate("text.clear_worlds") + "...")
                    clearWorlds(this)
                }

                this.setState(I18n.translate("instance.launching") + "...")
                launchInstance(this)
            }
        }.showDialog().start()
    }

    fun getInstanceType(): String {
        return if (this.fabricVersion != null) "Fabric" else "Vanilla"
    }

    fun getIconResource(): URL? {
        return if (this.mcsrRankedType != null) javaClass.getResource("/icons/mcsrranked.png")
            else if (this.fabricVersion != null) javaClass.getResource("/icons/fabric.png")
            else javaClass.getResource("/icons/minecraft.png")
    }

    fun isRunning(): Boolean {
        return this.getProcess() != null
    }

    fun getProcess(): InstanceProcess? {
        return MCSRLauncher.GAME_PROCESSES.find { it.instance == this }
    }

    fun updateName(text: String) {
        this.displayName = text
        val beforeFile = this.getInstancePath().toFile()
        this.id = InstanceManager.getNewInstanceName(this.displayName)
        MCSRLauncher.LOGGER.info("Update instance name to $id from ${beforeFile.name}")
        val afterFile = this.getInstancePath().toFile()
        FileUtils.moveDirectory(beforeFile, afterFile)
    }

    fun getMods(): List<ModData> {
        val modsDir = this.getModsPath().toFile()
        if (!modsDir.exists() || !modsDir.isDirectory) return listOf()
        return modsDir.listFiles()!!.filter { it.isFile }.mapNotNull { ModData.get(it) }
    }

    fun installRecommendedSpeedrunMods(worker: LauncherWorker, modListVersion: String, modCategory: ModCategory, downloadMethod: ModDownloadMethod, accessibility: Boolean): List<ModData> {
        if (downloadMethod == ModDownloadMethod.UPDATE_EXISTING_MODS) return updateSpeedrunMods(worker)

        this.getModsPath().toFile().mkdirs()
        this.fabricVersion ?: throw IllegalStateException("This instance does not have Fabric Loader")

        val modMeta = MetaManager.getVersionMeta<SpeedrunModsMetaFile>(MetaUniqueID.SPEEDRUN_MODS, modListVersion, worker) ?: throw IllegalStateException("Speedrun mods meta is not found")

        val installedMods = this.getMods().toMutableList()

        if (downloadMethod == ModDownloadMethod.DELETE_ALL_DOWNLOAD) {
            installedMods.forEach { it.delete() }
            installedMods.clear()
        }

        val availableMods = arrayListOf<SpeedrunModMeta>()
        modMeta.mods.filter {
            it.recommended &&
                    it.canDownload(this) &&
                    it.traits.all { trait ->
                        when (trait) {
                            SpeedrunModTrait.RSG -> modCategory == ModCategory.RANDOM_SEED
                            SpeedrunModTrait.SSG -> modCategory == ModCategory.SET_SEED
                            SpeedrunModTrait.ACCESSIBILITY -> accessibility
                            else -> true
                        }
                    }
        }.sortedByDescending { it.priority }.forEach {
            if (it.incompatibilities.none { inc -> availableMods.map { mod -> mod.modId }.contains(inc) })
                availableMods.add(it)
        }

        val total = availableMods.size
        val completed = AtomicInteger(0)
        val modList = Collections.synchronizedList(mutableListOf<ModData>())

        AssetUtils.doConcurrency(availableMods) { mod ->
            val version = mod.versions.find { it.isAvailableVersion(this@BasicInstance) }!!
            worker.setState("Downloading ${mod.name} v${version.version}...")

            val file = getModsPath().resolve(version.filename).toFile()
            withContext(Dispatchers.IO) {
                FileDownloader.download(version.url, file)

                installedMods.find { it.id == mod.modId && it.version != version.version }?.delete()
                modList.add(ModData.get(file)!!)

                val done = completed.incrementAndGet()
                worker.setProgress(done.toFloat() / total.toFloat())
            }
        }

        return modList
    }

    fun getSpeedRunModUpdates(worker: LauncherWorker): List<Pair<SpeedrunModMeta, SpeedrunModVersion>> {
        val list = arrayListOf<Pair<SpeedrunModMeta, SpeedrunModVersion>>()
        this.getModsPath().toFile().mkdirs()
        this.fabricVersion ?: throw IllegalStateException("This instance does not have Fabric Loader")

        val modMeta = MetaManager.getVersionMeta<SpeedrunModsMetaFile>(MetaUniqueID.SPEEDRUN_MODS, SpeedrunModMeta.VERIFIED_MODS, worker) ?: throw IllegalStateException("Speedrun mods meta is not found")

        val installedMods = this.getMods()
        for (mod in modMeta.mods.filter { it.canDownload(this) }) {
            val version = mod.versions.find { it.isAvailableVersion(this) }!!

            val canUpdate = installedMods.any {
                it.id == mod.modId &&
                        if (it.version.toVersionOrNull(false) == null) {
                            it.version != version.version
                        } else {
                            val modVersion = it.version.toVersionOrNull(false)
                            val newVersion = version.version.toVersionOrNull(false)
                            if (modVersion == null || newVersion == null) false else modVersion < newVersion
                        }
            }
            if (canUpdate) list.add(mod to version)
        }
        return list
    }

    fun updateSpeedrunMods(worker: LauncherWorker): List<ModData> {
        val updates = getSpeedRunModUpdates(worker)

        val list = arrayListOf<ModData>()
        val installedMods = this.getMods()
        updates.forEach { (mod, version) ->
            worker.setState("Downloading ${mod.name} v${version.version}...")
            val file = this.getModsPath().resolve(version.url.split("/").last()).toFile()
            FileDownloader.download(version.url, file, worker)
            installedMods.find { it.id == mod.modId }?.delete()
            list.add(ModData.get(file)!!)
        }

        return list
    }

    fun save() {
        val configJson = this.getInstancePath().resolve("instance.json")
        configJson.toFile().writeText(MCSRLauncher.JSON.encodeToString(this))
    }

    /**
     * Original source: https://github.com/DuncanRuns/Jingle/blob/main/src/main/java/xyz/duncanruns/jingle/bopping/Bopping.java
     */
    fun clearWorlds(worker: LauncherWorker): Int? {
        if (this.clearingWorlds) return null
        this.clearingWorlds = true
        this.getWorldsPath().toFile().mkdirs()

        fun shouldDelete(file: File, creationTime: Long): Boolean {
            val path = file.toPath()
            if (System.currentTimeMillis() - creationTime < 1000 * 60 * 60 * 48) {
                try {
                    val recordPath = path.resolve("speedrunigt").resolve("record.json")
                    if (recordPath.isRegularFile()) {
                        val recordJson = MCSRLauncher.JSON.decodeFromString<JsonObject>(recordPath.readText())
                        if (recordJson.containsKey("is_completed") && recordJson["is_completed"]!!.jsonPrimitive.boolean) {
                            return false
                        }
                    }
                } catch (e: Exception) {
                    MCSRLauncher.LOGGER.error("Failed to read record.json for ${file.absolutePath}", e)
                }
            }

            if (!file.isDirectory || path.resolve("Reset Safe.txt").exists()) return false

            val name = file.nameWithoutExtension
            if (name.startsWith("Benchmark Reset #")) return true

            if (!path.resolve("level.dat").isRegularFile()) return false
            if (name.startsWith("_")) return false

            return name.startsWith("New World") || name.contains("Speedrun #") || name.contains("Practice Seed") || name.contains("Seed Paster")
        }

        val worlds = this.getWorldsPath().toFile().listFiles()
            ?.mapNotNull {
                try {
                    val creationTime = Files.getFileAttributeView(it.toPath(), BasicFileAttributeView::class.java)
                        .readAttributes().creationTime().toMillis()
                    it to creationTime
                } catch (e: Exception) {
                    MCSRLauncher.LOGGER.debug("Failed to parse creation time", e)
                    null
                }
            }
            ?.filter { (f, t) -> shouldDelete(f, t) }
            ?.sortedByDescending { it.second }
            ?.drop(36)
            ?: emptyList()

        val deleteCount = AtomicInteger()
        worker.setSubText("Deleting Worlds: 0/${worlds.size}")
        worker.setProgress(0.0)

        runBlocking {
            worlds.map { (file, _) ->
                async(Dispatchers.IO) {
                    try {
                        if (file.exists()) {
                            Files.walkFileTree(file.toPath(), object : SimpleFileVisitor<Path>() {
                                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                                    Files.delete(file)
                                    return FileVisitResult.CONTINUE
                                }

                                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                                    Files.delete(dir)
                                    return FileVisitResult.CONTINUE
                                }

                                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                                    MCSRLauncher.LOGGER.error("Failed to delete $file", exc)
                                    return FileVisitResult.CONTINUE
                                }
                            })
                            worker.setSubText("Deleting Worlds: ${deleteCount.incrementAndGet()}/${worlds.size}")
                            worker.setProgress(deleteCount.get() / worlds.size.toDouble())
                        }
                    } catch (e: Exception) {
                        MCSRLauncher.LOGGER.error("Failed to delete ${file.absolutePath}", e)
                    }
                }
            }.awaitAll()
        }

        worker.setSubText(null)
        worker.setProgress(null)
        this.clearingWorlds = false
        return deleteCount.get()
    }

    fun openOptionDialog(window: Window = MCSRLauncher.MAIN_FRAME): InstanceOptionGui {
        if (optionDialog != null) {
            optionDialog?.requestFocus()
        } else {
            optionDialog = InstanceOptionGui(window, this)
        }
        return optionDialog!!
    }
}
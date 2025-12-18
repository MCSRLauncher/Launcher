package com.redlimerl.mcsrlauncher.instance

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.data.device.DeviceOSType
import com.redlimerl.mcsrlauncher.data.instance.BasicInstance
import com.redlimerl.mcsrlauncher.data.meta.LauncherTrait
import com.redlimerl.mcsrlauncher.data.meta.MetaUniqueID
import com.redlimerl.mcsrlauncher.data.meta.file.FabricIntermediaryMetaFile
import com.redlimerl.mcsrlauncher.data.meta.file.FabricLoaderMetaFile
import com.redlimerl.mcsrlauncher.data.meta.file.LWJGLMetaFile
import com.redlimerl.mcsrlauncher.data.meta.file.MinecraftMetaFile
import com.redlimerl.mcsrlauncher.exception.IllegalRequestResponseException
import com.redlimerl.mcsrlauncher.exception.InvalidAccessTokenException
import com.redlimerl.mcsrlauncher.gui.component.LogViewerPanel
import com.redlimerl.mcsrlauncher.launcher.AccountManager
import com.redlimerl.mcsrlauncher.launcher.GameAssetManager
import com.redlimerl.mcsrlauncher.launcher.MetaManager
import com.redlimerl.mcsrlauncher.util.AssetUtils
import com.redlimerl.mcsrlauncher.util.LauncherWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.apache.commons.io.FileUtils
import java.awt.Toolkit
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.io.path.absolutePathString

private const val MAX_LOG_ARCHIVED_COUNT = 1000

class InstanceProcess(val instance: BasicInstance) {

    var process: Process? = null
        private set
    private var exitByUser = false

    private val logArchive: MutableList<String> = Collections.synchronizedList(LinkedList())
    private var logChannel = Channel<String>(10000)
    private var viewerUpdater: Job? = null

    @OptIn(DelicateCoroutinesApi::class)
    fun start(worker: LauncherWorker) {
        val javaTarget = instance.options.getSharedJavaValue { it.javaPath }
        val noJavaException = IllegalStateException("Java has not been properly selected. Try changing your Java path")
        if (javaTarget.isEmpty()) throw noJavaException
        val javaContainer: JavaContainer
        try {
            javaContainer = JavaContainer(Paths.get(javaTarget))
        } catch (e: Exception) {
            MCSRLauncher.LOGGER.error("Failed to find java with \"${javaTarget}\"", e)
            throw noJavaException
        }

        MCSRLauncher.LOGGER.info("Loading Authentication: ${instance.id}")
        val activeAccount = AccountManager.getActiveAccount() ?: throw IllegalStateException("No account found, make sure you have added your account.")
        try {
            if (activeAccount.profile.checkTokenValidForLaunch(worker, activeAccount)) AccountManager.save()
        } catch (e: IllegalRequestResponseException) {
            throw InvalidAccessTokenException("Authentication Failed. Try removing and adding your Minecraft account again.")
        }

        MCSRLauncher.LOGGER.info("Launching instance: ${instance.id}")
        instance.getGamePath().toFile().mkdirs()

        var mainClass: String
        val libraries = linkedSetOf<Path>()
        val libraryMap = arrayListOf<InstanceLibrary>()

        val arguments = arrayListOf(
            "-Xms${instance.options.getSharedJavaValue { it.minMemory }}M",
            "-Xmx${instance.options.getSharedJavaValue { it.maxMemory }}M"
        )

        arguments.addAll(instance.options.getSharedJavaValue { it.jvmArguments }.split(" ").flatMap { it.split("\n") }.filter { it.isNotBlank() })

        val minecraftMetaFile = MetaManager.getVersionMeta<MinecraftMetaFile>(MetaUniqueID.MINECRAFT, instance.minecraftVersion)
            ?: throw IllegalStateException("${MetaUniqueID.MINECRAFT.value} version meta is not found")
        val minCompatibleVersion = minecraftMetaFile.compatibleJavaMajors.min()
        if (minCompatibleVersion > javaContainer.majorVersion) {
            throw IllegalStateException("Required minimum Java version is ${minCompatibleVersion}, while you are using ${javaContainer.majorVersion}")
        }

        if (minecraftMetaFile.traits.contains(LauncherTrait.FIRST_THREAD_MACOS) && DeviceOSType.MACOS.isOn()) {
            arguments.add("-XstartOnFirstThread")
        }

        minecraftMetaFile.libraries.filter { it.shouldApply() }.forEach { libraryMap.add(it.toInstanceLibrary()) }

        val mainJar = minecraftMetaFile.mainJar.getPath()
        mainClass = minecraftMetaFile.mainClass

        val accessToken = activeAccount.profile.accessToken
        val gameArgs = minecraftMetaFile.minecraftArguments
            .split(" ").map {
                it.replace("\${auth_player_name}", activeAccount.profile.nickname)
                    .replace("\${version_name}", minecraftMetaFile.version)
                    .replace("\${game_directory}", instance.getGamePath().absolutePathString())
                    .replace("\${assets_root}", GameAssetManager.ASSETS_PATH.absolutePathString())
                    .replace("\${game_assets}", instance.getGamePath().resolve("resources").absolutePathString())
                    .replace("\${assets_index_name}", minecraftMetaFile.assetIndex.id)
                    .replace("\${auth_uuid}", activeAccount.profile.uuid.toString())
                    .replace("\${auth_access_token}", accessToken ?: "")
                    .replace("\${auth_session}", accessToken ?: "")
                    .replace("\${user_type}", "msa")
                    .replace("\${version_type}", minecraftMetaFile.type.toTypeId())
                    .replace("\${user_properties}", "{}")
            }.toMutableList()

        if (!minecraftMetaFile.traits.contains(LauncherTrait.LEGACY_LAUNCH)) {
            if (instance.options.getSharedJavaValue { it.maximumResolution }) {
                gameArgs.add("--width")
                gameArgs.add(Toolkit.getDefaultToolkit().screenSize.width.toString())
                gameArgs.add("--height")
                gameArgs.add(Toolkit.getDefaultToolkit().screenSize.height.toString())
            } else {
                gameArgs.add("--width")
                gameArgs.add(instance.options.getSharedResolutionValue { it.resolutionWidth }.toString())
                gameArgs.add("--height")
                gameArgs.add(instance.options.getSharedResolutionValue { it.resolutionHeight }.toString())
            }
        }

        val lwjglMetaFile = MetaManager.getVersionMeta<LWJGLMetaFile>(instance.lwjglVersion.type, instance.lwjglVersion.version, worker)
            ?: throw IllegalStateException("LWJGL ${instance.lwjglVersion.version} is not found")
        lwjglMetaFile.libraries.filter { it.shouldApply() }.forEach { libraryMap.add(it.toInstanceLibrary()) }

        val fabric = instance.fabricVersion
        if (fabric != null) {
            val fabricLoaderMetaFile = MetaManager.getVersionMeta<FabricLoaderMetaFile>(MetaUniqueID.FABRIC_LOADER, fabric.loaderVersion)
                ?: throw IllegalStateException("${MetaUniqueID.FABRIC_LOADER.value} fabric loader version is not found")
            mainClass = fabricLoaderMetaFile.mainClass
            fabricLoaderMetaFile.libraries.forEach { libraryMap.add(it.toInstanceLibrary()) }

            val intermediaryMetaFile = MetaManager.getVersionMeta<FabricIntermediaryMetaFile>(MetaUniqueID.FABRIC_INTERMEDIARY, fabric.intermediaryVersion)
                ?: throw IllegalStateException("${fabric.intermediaryVersion} intermediary version is not found")
            libraryMap.add(intermediaryMetaFile.getLibrary(fabric.intermediaryType).toInstanceLibrary())
        }

        InstanceLibrary.fixLibraries(libraryMap)
        libraries.addAll(libraryMap.flatMap { it.paths })

        instance.getNativePath().toFile().mkdirs()
        FileUtils.deleteDirectory(instance.getNativePath().toFile())
        val nativeLibs = arrayListOf<Path>()
        for (libraryPath in libraries) {
            val libFile = libraryPath.toFile()
            if (!libFile.exists()) throw IllegalStateException("Library: ${libFile.name} does not exist!")
            if (libFile.name.endsWith(".jar") && libFile.name.contains("natives")) {
                MCSRLauncher.LOGGER.debug("Native extracting: ${libFile.name}")
                AssetUtils.extractZip(libFile, instance.getNativePath().toFile(), true)
                nativeLibs.add(libraryPath)
            }
        }
        libraries.removeAll(nativeLibs.toSet())

        libraries.add(mainJar)

        val finalizeArgs = arrayListOf<String>()
        finalizeArgs.add(javaTarget)
        finalizeArgs.add("-Djava.library.path=${instance.getNativePath().absolutePathString()}")
        finalizeArgs.addAll(arguments)
        finalizeArgs.addAll(listOf("-cp", libraries.joinToString(File.pathSeparator) { it.absolutePathString() }))
        finalizeArgs.add(mainClass)
        finalizeArgs.addAll(gameArgs)

        var debugArgs = finalizeArgs.joinToString(" ")
        if (accessToken != null) debugArgs = debugArgs.replace(accessToken, "[ACCESS TOKEN]")
        MCSRLauncher.LOGGER.debug(debugArgs)

        GlobalScope.launch {
            val processBuilder = ProcessBuilder(finalizeArgs)
                .directory(instance.getGamePath().toFile())
                .redirectErrorStream(true)
            processBuilder.environment().apply {
                put("INST_ID", instance.id)
                put("INST_NAME", instance.displayName)
                put("INST_DIR", instance.getInstancePath().absolutePathString())
                put("INST_MC_DIR", instance.getGamePath().absolutePathString())
                put("INST_MC_VER", instance.minecraftVersion)
                put("INST_JAVA", javaContainer.path.absolutePathString())
                put("INST_JAVA_ARGS", arguments.joinToString(" "))
            }
            val process = processBuilder.start()

            launch(Dispatchers.IO) {
                BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                    lines.forEach { line ->
                        logChannel.send(line)
                        addLog(line)
                    }
                }
            }

            this@InstanceProcess.process = process
            MCSRLauncher.GAME_PROCESSES.add(this@InstanceProcess)
            instance.onLaunch()

            val javaArch = if (javaContainer.arch.contains("64")) "x64" else "x86"

            val initialLogs = listOf(
                "${MCSRLauncher.APP_NAME} version: ${MCSRLauncher.APP_VERSION}\n\n",
                "Minecraft folder is:\n${instance.getGamePath().absolutePathString()}\n\n",
                "Java path is:\n${javaContainer.path}\n\n",
                "Java is version: ${javaContainer.version} using $javaArch architecture from ${javaContainer.vendor}\n\n",
                "Java arguments are:\n${arguments.joinToString(" ")}\n\n",
                "Main Class:\n$mainClass\n\n",
                "Mods:"
            )
            initialLogs.forEach { message ->
                addLog(message)
            }

            for (mod in instance.getMods()) {
                val status = if (mod.isEnabled) "✅" else "❌"
                val message = "   [$status] ${mod.file.name}${if (!mod.isEnabled) " (disabled)" else ""}"
                addLog(message)
            }

            addLog("\n")

            val exitCode = process!!.waitFor()
            val exitMessage = "\nProcess exited with exit code $exitCode\n"
            addLog(exitMessage)
            Thread.sleep(2000L)
            onExit(exitCode)
        }
    }

    suspend fun addLog(logString: String) {
        synchronized(logArchive) {
            logArchive.add(logString)
            if (logArchive.size > MAX_LOG_ARCHIVED_COUNT) {
                logArchive.removeAt(0)
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun syncLogViewer(logViewer: LogViewerPanel) {
        viewerUpdater?.cancel()

        viewerUpdater = GlobalScope.launch {
            // Clear previous logs and prepare for new ones
            SwingUtilities.invokeLater {
                logViewer.updateLogFiles()
                logViewer.clearLogs()
                synchronized(logArchive) {
                    logViewer.addLogs(logArchive.toList()) // Send a copy of archived logs
                }
            }

            val logBuffer = ConcurrentLinkedQueue<String>()
            val uiTimer = Timer(100) {
                val logsToProcess = mutableListOf<String>()
                while (true) {
                    val log = logBuffer.poll() ?: break
                    logsToProcess.add(log)
                }

                if (logsToProcess.isNotEmpty()) {
                    logViewer.addLogs(logsToProcess)
                    logViewer.onLiveUpdate()
                }
            }
            uiTimer.start()

            // This coroutine will efficiently consume the channel and buffer logs
            launch(Dispatchers.IO) {
                for (line in logChannel) {
                    logBuffer.add(line)
                }
            }

            // When the process ends, stop the timer
            process?.waitFor()
            uiTimer.stop()

            // Process any remaining logs after the process has finished
            SwingUtilities.invokeLater {
                val logsToProcess = mutableListOf<String>()
                while (true) {
                    val log = logBuffer.poll() ?: break
                    logsToProcess.add(log)
                }
                if (logsToProcess.isNotEmpty()) {
                    logViewer.addLogs(logsToProcess)
                    logViewer.onLiveUpdate()
                }
            }
        }
    }

    private fun onExit(code: Int) {
        MCSRLauncher.GAME_PROCESSES.remove(this)
        this.instance.onProcessExit(code, exitByUser)
        this.logChannel.close()
    }

    fun exit() {
        exitByUser = true
        process?.destroy()
    }

}
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
import javax.swing.SwingUtilities
import kotlin.io.path.absolutePathString

class InstanceProcess(val instance: BasicInstance) {

    var process: Process? = null
        private set
    private var exitByUser = false

    private var logArchive = mutableListOf<String>()
    private var logChannel = Channel<String>(Channel.UNLIMITED)
    private var viewerUpdater: Job? = null

    @OptIn(DelicateCoroutinesApi::class)
    fun syncLogViewer(logViewer: LogViewerPanel) {
        viewerUpdater?.cancel()

        viewerUpdater = GlobalScope.launch {
            SwingUtilities.invokeLater {
                logViewer.updateLogFiles()
                logViewer.liveLogPane.text = ""
                logArchive.forEach { logViewer.appendString(logViewer.liveLogPane, it) }
            }
            for (line in logChannel) {
                SwingUtilities.invokeLater {
                    logViewer.appendString(logViewer.liveLogPane, line)
                    logArchive += line
                    logViewer.onLiveUpdate()
                }
            }
        }
    }
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

        val wrapperCmd: String = instance.options.getSharedWorkaroundValue { it.wrapperCommand }
        val customGlfwPath: String = instance.options.getSharedWorkaroundValue { it.customGLFWPath }
        val preLaunchCommand: String = instance.options.getSharedWorkaroundValue { it.preLaunchCommand }
        val postExitCommand: String = instance.options.getSharedWorkaroundValue { it.postExitCommand }
        val enableFeralGamemode: Boolean = instance.options.getSharedWorkaroundValue { it.enableFeralGamemode }
        val enableMangoHud: Boolean = instance.options.getSharedWorkaroundValue { it.enableMangoHud }
        val useDiscreteGpu: Boolean = instance.options.getSharedWorkaroundValue { it.useDiscreteGpu }
        val useZink: Boolean = instance.options.getSharedWorkaroundValue { it.useZink }

        var mainClass: String
        val libraries = linkedSetOf<Path>()
        val libraryMap = arrayListOf<InstanceLibrary>()

        val arguments = arrayListOf(
            "-Xms${instance.options.getSharedJavaValue { it.minMemory }}M",
            "-Xmx${instance.options.getSharedJavaValue { it.maxMemory }}M"
        )

        arguments.addAll(
            instance.options.getSharedJavaValue { it.jvmArguments }
                .split(" ")
                .flatMap { it.split("\n") }
                .filter { it.isNotBlank() }
        )

        if (customGlfwPath.isNotBlank()) {
            arguments.add("-Dorg.lwjgl.glfw.libname=$customGlfwPath")
        }

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

        if (preLaunchCommand.isNotBlank()) {
            GlobalScope.launch {
                logChannel.send("Running pre-launch command:\n$preLaunchCommand\n\n")
                try {
                    val pre = ProcessBuilder("bash", "-c", preLaunchCommand)
                        .directory(instance.getGamePath().toFile())
                        .redirectErrorStream(true)
                        .start()
                    val exitCode = pre.waitFor()
                    if (exitCode == 0) logChannel.send("Pre-launch command finished successfully.\n\n")
                    else logChannel.send("WARN: Pre-launch command exited with code $exitCode.\n\n")
                } catch (e: Exception) {
                    logChannel.send("WARN: Failed to run pre-launch command: ${e.message}\n\n")
                }
            }
        }

        val finalLaunchArgs = mutableListOf<String>()
        if (enableFeralGamemode) finalLaunchArgs += "gamemoded"
        if (enableMangoHud) finalLaunchArgs += "mangohud"
        if (wrapperCmd.isNotBlank()) finalLaunchArgs += wrapperCmd.split("\\s+".toRegex())

        finalLaunchArgs += javaTarget
        finalLaunchArgs += "-Djava.library.path=${instance.getNativePath().absolutePathString()}"
        finalLaunchArgs += arguments
        finalLaunchArgs += listOf("-cp", libraries.joinToString(File.pathSeparator) { it.absolutePathString() })
        finalLaunchArgs += mainClass
        finalLaunchArgs += gameArgs

        val pb = ProcessBuilder(finalLaunchArgs)
            .directory(instance.getGamePath().toFile())
            .redirectErrorStream(true)

        pb.environment().apply {
            put("INST_NAME", instance.displayName)
            put("INST_ID", instance.id)
            put("INST_DIR", instance.getInstancePath().absolutePathString())
            put("INST_MC_DIR", instance.getGamePath().absolutePathString())
            put("INST_MC_VER", instance.minecraftVersion)
            put("INST_JAVA", javaTarget)
            put("INST_JAVA_ARGS", arguments.joinToString(" "))
            if (useDiscreteGpu) put("DRI_PRIME", "1")
            if (useZink) put("MESA_LOADER_DRIVER_OVERRIDE", "zink")
        }

        GlobalScope.launch {
            val process = pb.start()
            launch(Dispatchers.IO) {
                BufferedReader(InputStreamReader(process.inputStream)).useLines { lines ->
                    lines.forEach { line ->
                        logChannel.send(line + "\n")
                    }
                }
            }

            this@InstanceProcess.process = process
            MCSRLauncher.GAME_PROCESSES.add(this@InstanceProcess)
            instance.onLaunch()

            val javaArch = if (javaContainer.arch.contains("64")) "x64" else "x86"

            logChannel.send("${MCSRLauncher.APP_NAME} version: ${MCSRLauncher.APP_VERSION}\n\n\n")
            logChannel.send("Minecraft folder is:\n${instance.getGamePath().absolutePathString()}\n\n\n")
            logChannel.send("Java path is:\n${javaContainer.path}\n\n\n")
            logChannel.send("Java is version: ${javaContainer.version} using $javaArch architecture\n\n\n")
            if (wrapperCmd.isNotBlank()) logChannel.send("Wrapper command is:\n$wrapperCmd\n\n\n")
            if (customGlfwPath.isNotBlank()) logChannel.send("Using custom GLFW library:\n$customGlfwPath\n\n\n")
            if (enableFeralGamemode) logChannel.send("Running with Feral GameMode\n\n\n")
            if (enableMangoHud) logChannel.send("Running with MangoHUD\n\n\n")
            if (useDiscreteGpu) logChannel.send("Running with discrete GPU\n\n\n")
            if (useZink) logChannel.send("Running with Zink renderer\n\n\n")
            if (preLaunchCommand.isNotBlank()) logChannel.send("Pre-launch command:\n$preLaunchCommand\n\n\n")
            if (postExitCommand.isNotBlank()) logChannel.send("Post-exit command:\n$postExitCommand\n\n\n")

            val exitCode = process.waitFor()
            logChannel.send("\nProcess exited with exit code $exitCode")
            Thread.sleep(2000L)
            onExit(exitCode)
        }
    }

    private fun onExit(code: Int) {
        MCSRLauncher.GAME_PROCESSES.remove(this)
        this.instance.onProcessExit(code, exitByUser)

        val postExitCommand = instance.options.getSharedWorkaroundValue { it.postExitCommand }
        if (postExitCommand.isNotBlank()) {
            GlobalScope.launch {
                logChannel.send("Running post-exit command:\n$postExitCommand\n\n")
                try {
                    val post = ProcessBuilder("bash", "-c", postExitCommand)
                        .directory(instance.getGamePath().toFile())
                        .redirectErrorStream(true)
                        .start()
                    val exitCode = post.waitFor()
                    if (exitCode == 0) logChannel.send("Post-exit command ran successfully.\n\n")
                    else logChannel.send("WARN: Post-exit command exited with code $exitCode.\n\n")
                } catch (e: Exception) {
                    logChannel.send("WARN: Failed to run post-exit command: ${e.message}\n\n")
                }
            }
        }
    }


    fun exit() {
        exitByUser = true
        process?.destroy()
    }
}
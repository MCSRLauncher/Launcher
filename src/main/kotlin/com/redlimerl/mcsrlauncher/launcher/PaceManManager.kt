package com.redlimerl.mcsrlauncher.launcher

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.data.meta.MetaUniqueID
import com.redlimerl.mcsrlauncher.data.meta.file.SpeedrunToolsMetaFile
import com.redlimerl.mcsrlauncher.data.meta.tool.SpeedrunToolVersion
import com.redlimerl.mcsrlauncher.network.FileDownloader
import com.redlimerl.mcsrlauncher.util.LauncherWorker
import org.apache.commons.lang3.tuple.Pair
import java.awt.Window
import java.io.File
import java.net.URLClassLoader
import javax.swing.JComponent
import javax.swing.JDialog

object PaceManManager {
    //Tested with Version v0.7.3 (latest 8/26)
    private const val TRACKER_CLASS = "gg.paceman.tracker.PaceManTracker"
    private const val OPTIONS_CLASS = "gg.paceman.tracker.PaceManTrackerOptions"
    private const val PANEL_CLASS = "gg.paceman.tracker.gui.PaceManTrackerPanel"

    private const val TOOL_WRAPPER_VERSION = "paceman-tracker"
    private const val CHECKSUM_KEY = "gg.paceman.tracker"
    private val JAR_NAME = Regex("""paceman-tracker-\d.*\.jar""")

    private val pacemanDir = MCSRLauncher.BASE_PATH.resolve("paceman")

    private var classLoader: URLClassLoader? = null
    private var trackerInstance: Any? = null
    private var initialized = false
    var isRunning = false
        private set

    var updateAvailable = false
        private set

    fun jarFileFor(version: String): File = pacemanDir.resolve("paceman-tracker-$version.jar").toFile()

    private fun installedJar(): File? = pacemanDir.toFile().listFiles()?.firstOrNull { JAR_NAME.matches(it.name) }

    fun isDownloaded(): Boolean = installedJar() != null

    private fun latestToolVersion(worker: LauncherWorker): SpeedrunToolVersion? {
        val toolMeta = MetaManager.getVersionMeta<SpeedrunToolsMetaFile>(MetaUniqueID.SPEEDRUN_TOOLS, TOOL_WRAPPER_VERSION, worker) ?: return null
        return toolMeta.tool.versions.filter { !it.prerelease }.maxByOrNull { it.releaseTime } ?: toolMeta.tool.versions.maxByOrNull { it.releaseTime }
    }

    fun refreshUpdateStatus() {
        updateAvailable = try {
            val latest = latestToolVersion(LauncherWorker.empty())
            latest != null && GameAssetManager.getChecksum(CHECKSUM_KEY) != latest.checksum.hash
        } catch (e: Exception) {
            false
        }
    }

    fun download(worker: LauncherWorker) {
        val latest = latestToolVersion(worker) ?: throw IllegalStateException("No PaceMan Tracker version available in meta")

        unload() // Release open classloader
        installedJar()?.delete()
        worker.setState("Downloading PaceMan Tracker...")
        FileDownloader.download(latest.url, jarFileFor(latest.version), worker)
        GameAssetManager.updateChecksum(CHECKSUM_KEY, latest.checksum.hash)
        updateAvailable = false
        start()
    }

    fun uninstall() {
        unload()
        installedJar()?.delete()
    }

    fun shutdown() {
        unload()
    }

    private fun unload() {
        if (isRunning) {
            trackerInstance?.let { runCatching { it.javaClass.getMethod("stop").invoke(it) } }
            isRunning = false
        }
        classLoader?.close()
        classLoader = null
        trackerInstance = null
        initialized = false
    }

    private fun loader(): URLClassLoader {
        classLoader?.let { return it }
        val jar = installedJar() ?: throw IllegalStateException("PaceMan Tracker is not installed")
        return URLClassLoader(arrayOf(jar.toURI().toURL()), MCSRLauncher::class.java.classLoader).also { classLoader = it }
    }

    private fun ensureInitialized() {
        if (initialized) return
        val optionsClass = Class.forName(OPTIONS_CLASS, true, loader())
        optionsClass.getMethod("ensurePaceManDir").invoke(null)
        val options = optionsClass.getMethod("tryLoad").invoke(null)
        optionsClass.getMethod("save").invoke(options)

        val trackerClass = Class.forName(TRACKER_CLASS, true, loader())
        fun setConsumer(fieldName: String, action: (String) -> Unit) {
            trackerClass.getField(fieldName).set(null, java.util.function.Consumer<String> { action(it) })
        }

        //Paceman logs
        setConsumer("logConsumer") { MCSRLauncher.LOGGER.info("(PaceMan Tracker) $it") }
        setConsumer("debugConsumer") { MCSRLauncher.LOGGER.debug("(PaceMan Tracker) $it") }
        setConsumer("errorConsumer") { MCSRLauncher.LOGGER.error("(PaceMan Tracker) $it") }
        setConsumer("warningConsumer") { MCSRLauncher.LOGGER.warn("(PaceMan Tracker) $it") }

        initialized = true
    }

    private fun tracker(): Any {
        ensureInitialized()
        trackerInstance?.let { return it }
        val trackerClass = Class.forName(TRACKER_CLASS, true, loader())
        return trackerClass.getMethod("getInstance").invoke(null).also { trackerInstance = it }
    }

    fun start() {
        if (isRunning) return
        val instance = tracker()
        instance.javaClass.getMethod("start", Boolean::class.javaPrimitiveType).invoke(instance, true)
        isRunning = true
    }

    fun openConfigWindow(owner: Window) {
        ensureInitialized()
        val panelClass = Class.forName(PANEL_CLASS, true, loader())
        @Suppress("UNCHECKED_CAST")
        val guiPair = panelClass.getMethod("getNewGUIAsPanel").invoke(null) as Pair<Any, JComponent>
        val dialog = JDialog(owner, "PaceMan Tracker")
        dialog.contentPane.add(guiPair.right)
        dialog.pack()
        dialog.setLocationRelativeTo(owner)
        dialog.isVisible = true
    }
}

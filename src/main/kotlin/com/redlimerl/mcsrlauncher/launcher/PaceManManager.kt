package com.redlimerl.mcsrlauncher.launcher

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.data.meta.MetaUniqueID
import com.redlimerl.mcsrlauncher.data.meta.file.SpeedrunProgramsMetaFile
import com.redlimerl.mcsrlauncher.data.meta.program.SpeedrunProgramMeta
import com.redlimerl.mcsrlauncher.network.FileDownloader
import com.redlimerl.mcsrlauncher.util.LauncherWorker
import org.apache.commons.lang3.tuple.Pair
import java.awt.Window
import java.io.File
import java.net.URLClassLoader
import javax.swing.JComponent
import javax.swing.JDialog

object PaceManManager {
    //Tested with Version v0.7.3
    private const val TRACKER_CLASS = "gg.paceman.tracker.PaceManTracker"
    private const val OPTIONS_CLASS = "gg.paceman.tracker.PaceManTrackerOptions"
    private const val PANEL_CLASS = "gg.paceman.tracker.gui.PaceManTrackerPanel"

    const val PROGRAM_ID = "paceman-tracker"
    private val JAR_NAME = Regex("""paceman-tracker-(\d.*)\.jar""")
    private val RELEASE_TAG_URL = Regex("""(https://github\.com/[^/]+/[^/]+)/releases/tag/v?([^/?#]+)""")

    private val pacemanDir = MCSRLauncher.BASE_PATH.resolve("paceman")

    private var classLoader: URLClassLoader? = null
    private var trackerInstance: Any? = null
    private var initialized = false
    var isRunning = false
        private set

    private data class Release(val version: String, val url: String)

    private fun installedJar(): File? = pacemanDir.toFile().listFiles()?.firstOrNull { JAR_NAME.matches(it.name) }

    private fun installedVersion(): String? = installedJar()?.let { JAR_NAME.matchEntire(it.name)?.groupValues?.get(1) }

    fun isDownloaded(): Boolean = installedJar() != null

    fun getProgramMeta(worker: LauncherWorker): SpeedrunProgramMeta? {
        val programs = MetaManager.getVersionMeta<SpeedrunProgramsMetaFile>(MetaUniqueID.SPEEDRUN_PROGRAMS, "verified", worker) ?: return null
        return programs.programs.find { it.id == PROGRAM_ID }
    }

    private fun latestRelease(worker: LauncherWorker): Release? {
        val program = getProgramMeta(worker) ?: return null
        val match = RELEASE_TAG_URL.find(program.downloadPage) ?: return null
        val (repo, version) = match.destructured
        return Release(version, "$repo/releases/download/v$version/paceman-tracker-$version.jar")
    }

    //Make sure Paceman is installed
    @Synchronized
    fun ensureInstalled(worker: LauncherWorker, checkUpdates: Boolean = true) {
        if (!checkUpdates && isDownloaded()) return
        val latest = try {
            latestRelease(worker)
        } catch (e: Exception) {
            if (isDownloaded()) {
                MCSRLauncher.LOGGER.warn("Failed to check PaceMan Tracker updates", e)
                return
            }
            throw e
        }
        if (latest == null) {
            if (isDownloaded()) return
            throw IllegalStateException("No PaceMan Tracker version available in meta")
        }
        if (installedVersion() == latest.version) return

        if (isRunning) return

        unload() // Release open classloader
        val oldJar = installedJar()
        worker.setState("Downloading PaceMan Tracker ${latest.version}...")
        FileDownloader.download(latest.url, pacemanDir.resolve("paceman-tracker-${latest.version}.jar").toFile(), worker)
        oldJar?.delete()
    }

    @Synchronized
    fun shutdown() {
        holders.clear()
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

    private val holders = mutableSetOf<Any>()

    @Synchronized
    fun acquire(holder: Any): Boolean {
        val started = start()
        holders.add(holder)
        return started
    }

    @Synchronized
    fun release(holder: Any) {
        if (!holders.remove(holder) || holders.isNotEmpty()) return
        MCSRLauncher.LOGGER.info("Stopping PaceMan Tracker")
        unload()
    }

    @Synchronized
    private fun start(): Boolean {
        if (isRunning) return false
        val instance = tracker()
        val version = installedJar()?.let { jar -> java.util.jar.JarFile(jar).use { it.manifest?.mainAttributes?.getValue("Implementation-Version") } } ?: installedVersion()
        version?.let { instance.javaClass.getField("VERSION").set(null, it) }
        instance.javaClass.getMethod("start", Boolean::class.javaPrimitiveType).invoke(instance, true)
        isRunning = true
        return true
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

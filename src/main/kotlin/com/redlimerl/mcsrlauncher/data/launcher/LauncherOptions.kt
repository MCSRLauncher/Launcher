package com.redlimerl.mcsrlauncher.data.launcher

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.util.JavaUtils
import com.redlimerl.mcsrlauncher.util.OSUtils
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.commons.io.FileUtils
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.absolutePathString

@Serializable
data class LauncherOptions(
    var language: LauncherLanguage = LauncherLanguage.ENGLISH,
    var metaUrl: String = "https://mcsrlauncher.github.io/meta/",
    var concurrentDownloads: Int = 6,
    val customJavaPaths: LinkedHashSet<String> = linkedSetOf(),
    override var javaPath: String = Paths.get(System.getProperty("java.home")).resolve("bin").resolve(JavaUtils.javaExecutableName()).absolutePathString(),
    override var jvmArguments: String = "",
    override var minMemory: Int = 1024,
    override var maxMemory: Int = if (OSUtils.getTotalMemoryGB() > 15) 4096 else 2048,
    override var maximumResolution: Boolean = false,
    override var resolutionWidth: Int = 854,
    override var resolutionHeight: Int = 480,

    override var customGLFWPath: String = "",
    override var wrapperCommand: String = "",
    override var preLaunchCommand: String = "",
    override var postExitCommand: String = "",
    override var enableFeralGamemode: Boolean = false,
    override var enableMangoHud: Boolean = false,
    override var useDiscreteGpu: Boolean = false,
    override var useZink: Boolean = false,
    override var enableEnvironmentVariables: Boolean = false,
    override var environmentVariables: MutableMap<String, String> = mutableMapOf()
) : LauncherSharedOptions {

    companion object {
        val path: Path = MCSRLauncher.BASE_PATH.resolve("options.json")
        fun load(): LauncherOptions {
            val file = path.toFile()
            return if (file.exists()) {
                val text = file.readText(Charsets.UTF_8)
                val json = Json {
                    ignoreUnknownKeys = true
                    prettyPrint = true
                    encodeDefaults = true
                }
                json.decodeFromString<LauncherOptions>(text)
            } else {
                LauncherOptions()
            }
        }

    }

    fun save() {
        val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }
        FileUtils.writeStringToFile(path.toFile(), json.encodeToString(this), Charsets.UTF_8)
    }
}
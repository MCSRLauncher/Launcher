package com.redlimerl.mcsrlauncher.data.launcher

import com.redlimerl.mcsrlauncher.MCSRLauncher
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.apache.commons.io.FileUtils
import java.nio.file.Path

@Serializable
data class LauncherOptions(
    var debug: Boolean = false,
    var language: LauncherLanguage = LauncherLanguage.ENGLISH,
    var metaUrl: String = "https://mcsrlauncher.github.io/meta/",
    var javaPath: String = "java"
) {
    companion object {
        val path: Path = MCSRLauncher.BASE_PATH.resolve("options.json")
    }

    fun save() {
        val json = Json {
            prettyPrint = true
            encodeDefaults = true
        }
        FileUtils.writeStringToFile(path.toFile(), json.encodeToString(this), Charsets.UTF_8)
    }
}
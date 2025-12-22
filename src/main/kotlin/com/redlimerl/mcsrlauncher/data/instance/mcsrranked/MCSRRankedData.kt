package com.redlimerl.mcsrlauncher.data.instance.mcsrranked

import com.redlimerl.mcsrlauncher.data.instance.BasicInstance
import com.redlimerl.mcsrlauncher.instance.mod.ModData
import com.redlimerl.mcsrlauncher.network.FileDownloader
import com.redlimerl.mcsrlauncher.util.I18n
import com.redlimerl.mcsrlauncher.util.LauncherWorker
import io.github.z4kn4fein.semver.toVersion
import kotlinx.serialization.Serializable
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class MCSRRankedVersionData(
    private val version: String,
    private val sha512: String,
    private val downloadUrl: String,
    val size: Long
) {
    fun download(instance: BasicInstance, worker: LauncherWorker) {
        val modFile = instance.getModsPath().resolve(this.downloadUrl.split("/").last() + ".temp").toFile()
        val oldMod: ModData? = instance.getMods().find { it.id == "mcsrranked" }

        if (oldMod != null && oldMod.version.toVersion(false) >= this.version.toVersion(false)) return

        worker.setState("Downloading MCSRRanked v${this.version}...")
        modFile.parentFile.mkdirs()
        FileDownloader.download(this.downloadUrl, modFile, worker, this.size)

        Files.move(modFile.toPath(), modFile.toPath().parent.resolve(this.downloadUrl.split("/").last()), StandardCopyOption.REPLACE_EXISTING)
        oldMod?.file?.delete()
    }
}

@Serializable
enum class MCSRRankedPackType(val versionName: String?, private val recommended: Boolean) {
    BASIC("mcsrranked-basic", true),
    STANDARD_SETTINGS("mcsrranked-standardsettings", false),
    ALL("mcsrranked-all", false),
    MOD_ONLY(null, false);

    override fun toString(): String {
        return I18n.translate("text.pack_type.mcsrranked.${name.lowercase()}") + (if (this.recommended) " (${I18n.translate("text.recommended")})" else "")
    }

    fun getWarningMessage(): String {
        return I18n.translate("text.pack_type.mcsrranked.${name.lowercase()}.description")
    }
}
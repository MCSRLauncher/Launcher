package com.redlimerl.mcsrlauncher.data.meta.tool

import com.redlimerl.mcsrlauncher.data.instance.BasicInstance
import com.redlimerl.mcsrlauncher.data.meta.file.FileChecksum
import com.redlimerl.mcsrlauncher.data.serializer.ISO8601Serializer
import com.redlimerl.mcsrlauncher.network.FileDownloader
import com.redlimerl.mcsrlauncher.util.I18n
import com.redlimerl.mcsrlauncher.util.LauncherWorker
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class SpeedrunToolVersion(
    val checksum: FileChecksum,
    val name: String,
    val version: String,
    @Serializable(with = ISO8601Serializer::class) val releaseTime: Date,
    val url: String
) {
    fun install(worker: LauncherWorker, instance: BasicInstance, toolMeta: SpeedrunToolMeta) {
        val invalidKey = I18n.translate("message.invalid_file")

        val file = instance.getToolscreenFile() ?: throw IllegalStateException(invalidKey)
        if (!file.name.endsWith(toolMeta.format)) throw IllegalStateException(invalidKey)

        FileDownloader.download(this.url, file)
    }
}

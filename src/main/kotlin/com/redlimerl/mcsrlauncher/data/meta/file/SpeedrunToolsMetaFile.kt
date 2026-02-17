package com.redlimerl.mcsrlauncher.data.meta.file

import com.redlimerl.mcsrlauncher.data.meta.MetaUniqueID
import com.redlimerl.mcsrlauncher.data.meta.tool.SpeedrunToolMeta
import com.redlimerl.mcsrlauncher.data.serializer.ISO8601Serializer
import com.redlimerl.mcsrlauncher.util.LauncherWorker
import kotlinx.serialization.Serializable
import java.util.*

@Serializable
data class SpeedrunToolsMetaFile(
    override val uid: MetaUniqueID,
    override val name: String,
    override val version: String,
    @Serializable(with = ISO8601Serializer::class) override val releaseTime: Date,
    override val formatVersion: Int,
    val tool: SpeedrunToolMeta
) : MetaVersionFile() {
    override fun install(worker: LauncherWorker) {}
}
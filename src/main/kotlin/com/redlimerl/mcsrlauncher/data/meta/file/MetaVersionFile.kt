package com.redlimerl.mcsrlauncher.data.meta.file

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.data.meta.MetaUniqueID
import com.redlimerl.mcsrlauncher.data.serializer.ISO8601Serializer
import com.redlimerl.mcsrlauncher.launcher.MetaManager
import com.redlimerl.mcsrlauncher.util.LauncherWorker
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.commons.io.FileUtils
import java.nio.file.Path
import java.util.*

@Serializable(with = MetaVersionFileSerializer::class)
sealed class MetaVersionFile {
    abstract val uid: MetaUniqueID
    abstract val name: String
    abstract val version: String
    @Serializable(with = ISO8601Serializer::class) abstract val releaseTime: Date
    abstract val formatVersion: Int

    companion object {
        fun getPath(metaUniqueID: MetaUniqueID, version: String): Path {
            return MetaManager.BASE_PATH.resolve(metaUniqueID.value).resolve("$version.json")
        }
    }

    fun write() {
        FileUtils.writeStringToFile(getPath(this.uid, this.version).toFile(), MCSRLauncher.JSON.encodeToString(this), Charsets.UTF_8)
    }

    abstract fun install(worker: LauncherWorker)
}

object MetaVersionFileSerializer : JsonContentPolymorphicSerializer<MetaVersionFile>(MetaVersionFile::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<MetaVersionFile> {
        val uid = MetaUniqueID.entries.find { it.value == element.jsonObject["uid"]?.jsonPrimitive?.content } ?: throw SerializationException("uid is missing")
        return when (uid) {
            MetaUniqueID.FABRIC_LOADER -> FabricLoaderMetaFile.serializer()
            MetaUniqueID.FABRIC_INTERMEDIARY -> FabricIntermediaryMetaFile.serializer()
            MetaUniqueID.MINECRAFT -> MinecraftMetaFile.serializer()
            MetaUniqueID.MOJANG_JAVA -> JavaMetaFile.serializer()
            MetaUniqueID.AZUL_JAVA -> JavaMetaFile.serializer()
            MetaUniqueID.ADOPTIUM_JAVA -> JavaMetaFile.serializer()
            MetaUniqueID.GRAALVM_JAVA -> JavaMetaFile.serializer()
            MetaUniqueID.LWJGL2 -> LWJGLMetaFile.serializer()
            MetaUniqueID.LWJGL3 -> LWJGLMetaFile.serializer()
            MetaUniqueID.SPEEDRUN_MODS -> SpeedrunModsMetaFile.serializer()
            MetaUniqueID.PRACTICE_MAPS -> MinecraftMapsMetaFile.serializer()
            MetaUniqueID.UNKNOWN -> UnknownMetaFile.serializer()
            else -> {UnknownMetaFile.serializer()
            }
        }
    }

}
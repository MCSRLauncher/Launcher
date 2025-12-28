package com.redlimerl.mcsrlauncher.util

import com.redlimerl.mcsrlauncher.data.instance.BasicInstance
import com.redlimerl.mcsrlauncher.data.instance.FabricVersionData
import com.redlimerl.mcsrlauncher.data.instance.LWJGLVersionData
import com.redlimerl.mcsrlauncher.data.meta.IntermediaryType
import com.redlimerl.mcsrlauncher.data.meta.MetaUniqueID
import kotlinx.serialization.json.*
import java.io.File
import java.io.FileReader
import java.util.Properties

object MigrationUtils {

    fun cfgReader(cfg : File) : Properties {
        val props = Properties()
        FileReader(cfg).use { props.load(it) }
        return props
    }

    fun mmcPackReader(json : File) : JsonObject {
        return Json.parseToJsonElement(json.readText()).jsonObject
    }

    fun getLWJGL(patch : File) : LWJGLVersionData? {
        val text = patch.readText()
        val json = Json.parseToJsonElement(text).jsonObject
        val lwjglVersion = json["version"]?.jsonPrimitive?.content
        return lwjglVersion?.let { LWJGLVersionData(MetaUniqueID.LWJGL3, it) }
    }

    fun getMinecraftVersion(json : JsonObject?) : String {
        return json?.get("components")?.jsonArray?.get(1)?.jsonObject?.get("version")?.jsonPrimitive?.content!!
    }

    fun getFabricVersion(json : JsonObject?) : FabricVersionData? {
        val components = json?.get("components")?.jsonArray ?: return null
        val loader = components
            .firstOrNull { it.jsonObject["uid"]?.jsonPrimitive?.content == "net.fabricmc.fabric-loader" }
            ?.jsonObject
        val intermediary = components
            .firstOrNull { it.jsonObject["uid"]?.jsonPrimitive?.content == "net.fabricmc.intermediary" }
            ?.jsonObject

        if (loader == null) return null

        val fabricLoaderVer = loader["version"]?.jsonPrimitive?.content ?: return null
        val fabricIntermediaryVer = intermediary?.get("version")?.jsonPrimitive?.content ?: return null
        val fabricIntermediaryType = IntermediaryType.FABRIC

        return FabricVersionData(fabricLoaderVer, fabricIntermediaryType, fabricIntermediaryVer)
    }
}

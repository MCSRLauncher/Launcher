package com.redlimerl.mcsrlauncher.bridge.api

import com.redlimerl.mcsrlauncher.data.meta.MetaUniqueID
import com.redlimerl.mcsrlauncher.data.meta.MetaVersion
import com.redlimerl.mcsrlauncher.launcher.MetaManager
import kotlinx.serialization.json.*
import org.apache.logging.log4j.LogManager

class MetaApi {
    private val logger = LogManager.getLogger("MetaApi")

    suspend fun handle(method: String, params: JsonObject): JsonElement? {
        return when (method) {
            "meta.getMinecraftVersions" -> getMinecraftVersions()
            "meta.getLWJGLVersions" -> getLWJGLVersions(params)
            "meta.getFabricVersions" -> getFabricVersions(params)
            "meta.hasLoadedPackages" -> hasLoadedPackages()
            else -> throw IllegalArgumentException("Unknown meta method: $method")
        }
    }


    private fun getMinecraftVersions(): JsonElement {
        return buildJsonArray {
            MetaManager.getVersions(MetaUniqueID.MINECRAFT).forEach { version ->
                add(version.toVersionDTO())
            }
        }
    }


    private fun getLWJGLVersions(params: JsonObject): JsonElement {
        val mcVersion = params["minecraftVersion"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("minecraftVersion is required")

        val mcMeta = MetaManager.getVersions(MetaUniqueID.MINECRAFT).find { it.version == mcVersion }
            ?: throw IllegalArgumentException("Minecraft version not found: $mcVersion")

        val lwjglDependency = mcMeta.requires.firstOrNull { it.uid == MetaUniqueID.LWJGL2 || it.uid == MetaUniqueID.LWJGL3 }
            ?: return buildJsonArray { }

        val lwjglUid = lwjglDependency.uid

        return buildJsonArray {
            MetaManager.getVersions(lwjglUid).forEach { version ->
                addJsonObject {
                    put("type", lwjglUid.value)
                    put("version", version.version)
                    put("recommended", version.recommended)
                }
            }
        }
    }


    private fun getFabricVersions(params: JsonObject): JsonElement {
        val mcVersion = params["minecraftVersion"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("minecraftVersion is required")

        return buildJsonObject {
            putJsonArray("loaders") {
                MetaManager.getVersions(MetaUniqueID.FABRIC_LOADER).forEach { version ->
                    addJsonObject {
                        put("version", version.version)
                        put("recommended", version.recommended)
                    }
                }
            }

            val intermediary = MetaManager.getVersions(MetaUniqueID.FABRIC_INTERMEDIARY).find { it.version == mcVersion }
            putJsonArray("compatibleIntermediaries") {
                intermediary?.compatibleIntermediaries?.forEach { intermediaryType ->
                    addJsonObject {
                        put("id", intermediaryType.name)
                        put("name", intermediaryType.intermediaryName)
                        put("recommendLevel", intermediaryType.recommendLevel)
                    }
                }
            }
        }
    }


    private fun hasLoadedPackages(): JsonElement {
        return JsonPrimitive(MetaManager.hasLoadedPackages())
    }

    private fun MetaVersion.toVersionDTO(): JsonObject {
        return buildJsonObject {
            put("version", version)
            put("releaseTime", releaseTime.toInstant().toString())
            put("type", type.name)
            put("recommended", recommended)
        }
    }
}
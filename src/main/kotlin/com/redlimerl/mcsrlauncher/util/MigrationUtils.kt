package com.redlimerl.mcsrlauncher.util

import com.redlimerl.mcsrlauncher.data.device.DeviceOSType
import com.redlimerl.mcsrlauncher.data.instance.FabricVersionData
import com.redlimerl.mcsrlauncher.data.instance.LWJGLVersionData
import com.redlimerl.mcsrlauncher.data.meta.IntermediaryType
import com.redlimerl.mcsrlauncher.data.meta.MetaUniqueID
import kotlinx.serialization.json.*
import java.io.File
import java.io.FileReader
import java.util.Properties
import java.util.zip.ZipFile

object MigrationUtils {

    fun cfgReader(cfg : File) : Properties {
        val props = Properties()
        FileReader(cfg).use { props.load(it) }
        return props
    }

    fun mmcPackReader(json : String) : JsonObject {
        return Json.parseToJsonElement(json).jsonObject
    }

    fun getLWJGL(patch : String) : LWJGLVersionData? {
        val json = Json.parseToJsonElement(patch).jsonObject
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

    fun importMinecraft(zipPath : String, destFolder : String) {
        ZipFile(zipPath).use { zip ->
            val minecraftFolder = zip.entries().asSequence().firstOrNull { entry ->
                entry.isDirectory && entry.name
                    .trimEnd('/')
                    .substringAfterLast('/') in listOf(".minecraft", "minecraft")
            }
                ?: return
            val minecraftPath = minecraftFolder.name.trimEnd('/')
            val destMCFolder = when (OSUtils.getOSType()) {
                DeviceOSType.WINDOWS -> ".minecraft"
                else -> "minecraft"
            }
            val minecraftOutput = File(destFolder, destMCFolder)

            zip.entries().asSequence()
                .filter { it.name.startsWith(minecraftPath) }
                .forEach { entry ->
                    val relativePath = entry.name.removePrefix(minecraftPath).trimStart('/')

                    val outputFile = File(minecraftOutput, relativePath)

                    if (entry.isDirectory) outputFile.mkdirs()
                    else {
                        outputFile.parentFile.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            outputFile.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
        }

    }

    fun extractCfg(zipPath: String) : Properties {
        ZipFile(zipPath).use { zip ->
            val entry = zip.entries().asSequence()
                .firstOrNull { !it.isDirectory && it.name.endsWith("instance.cfg") }
                ?: error("Could not find instance.cfg in ZIP.")

            val props = Properties()
            zip.getInputStream(entry).use { input ->
                props.load(input)
            }

            return props
        }
    }

    fun extractMMCPack(zipPath : String) : JsonObject {
        ZipFile(zipPath).use { zip ->
            val entry = zip.entries().asSequence()
                .firstOrNull { !it.isDirectory && it.name.endsWith("mmc-pack.json") }
                ?: error("Could not find mmc-pack.json in ZIP.")

            val json = zip.getInputStream(entry)
                .bufferedReader()
                .use { it.readText() }

            return mmcPackReader(json)
        }
    }

    fun getZIPLWJGL(zipPath: String) : LWJGLVersionData? {
        ZipFile(zipPath).use { zip ->
            val entry = zip.entries().asSequence()
                .firstOrNull { !it.isDirectory && it.name.endsWith("org.lwjgl3.json") }
                ?: return LWJGLVersionData(MetaUniqueID.LWJGL3, "3.2.2")

            val json = zip.getInputStream(entry)
                .bufferedReader()
                .use { it.readText() }

            return getLWJGL(json)
        }
    }
}

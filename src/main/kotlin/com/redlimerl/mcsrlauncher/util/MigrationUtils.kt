package com.redlimerl.mcsrlauncher.util

import com.redlimerl.mcsrlauncher.data.device.DeviceOSType
import com.redlimerl.mcsrlauncher.data.instance.*
import com.redlimerl.mcsrlauncher.data.meta.IntermediaryType
import com.redlimerl.mcsrlauncher.data.meta.MetaUniqueID
import com.redlimerl.mcsrlauncher.data.meta.MetaVersion
import com.redlimerl.mcsrlauncher.launcher.MetaManager
import io.github.z4kn4fein.semver.toVersion
import kotlinx.serialization.json.*
import java.io.*
import java.util.Properties
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

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

    fun exportInstance(root: File, savePath: File, selectedFiles: List<File>, instance: BasicInstance, type: Int) {
        ZipOutputStream(BufferedOutputStream(savePath.outputStream())).use { out ->
            val instanceId = instance.id

            if (type == 1) {
                out.putNextEntry(ZipEntry("$instanceId/instance.cfg"))
                out.write(constructInstanceCfg(instance).toByteArray())
                out.closeEntry()

                out.putNextEntry(ZipEntry("$instanceId/mmc-pack.json"))
                out.write(constructMMCPack(instance).toByteArray())
                out.closeEntry()
            }

            selectedFiles.forEach { entry ->
                val entryPath = entry.toPath()
                val rootPath = root.toPath()

                if (entryPath == rootPath.parent) return@forEach

                val relative = if (entryPath.startsWith(rootPath)) {
                    "$instanceId/.minecraft/" + rootPath.relativize(entryPath).toString().replace(File.separatorChar, '/')
                } else { "$instanceId/${entry.name}" }

                if (entry.isDirectory) {
                    out.putNextEntry(ZipEntry("$relative/"))
                    out.closeEntry()
                } else {
                    FileInputStream(entry).use { fi ->
                        val zipEntry = ZipEntry(relative)
                        out.putNextEntry(zipEntry)
                        fi.copyTo(out)
                        out.closeEntry()
                    }
                }
            }
        }
    }

    private fun constructInstanceCfg(instance: BasicInstance) : String {
        val options = instance.options
        return buildString {
            appendLine("InstanceType=OneSix")
            appendLine("JavaPath=${options.javaPath.replace(File.separatorChar, '/')}")
            appendLine("JvmArgs=${options.jvmArguments}")
            appendLine("MaxMemAlloc=${options.maxMemory}")
            appendLine("MinMemAlloc=${options.minMemory}")
            appendLine("MinecraftWinHeight=${options.resolutionHeight}")
            appendLine("MinecraftWinWidth=${options.resolutionWidth}")
            appendLine("OverrideJavaArgs=${!options.useLauncherJavaOption}")
            appendLine("OverrideJavaLocation=${!options.useLauncherJavaOption}")
            appendLine("OverrideMemory=${!options.useLauncherJavaOption}")
            appendLine("OverrideWindow=${!options.useLauncherResolutionOption}")

            appendLine("name=${instance.displayName}")
            appendLine("lastLaunchTime=${instance.lastPlaytimeUpdate}")
            appendLine("totalTimePlayed=${instance.playTime}")
        }
    }

    private fun constructMMCPack(instance: BasicInstance) : String {
        val json = Json {
            prettyPrint = true
            encodeDefaults = true
            explicitNulls = false
        }

        val minecraftId = MetaUniqueID.MINECRAFT.value

        val components = mutableListOf(
            Component(
                cachedName = "LWJGL 3",
                cachedVersion = instance.lwjglVersion.version,
                cachedVolatile = true,
                dependencyOnly = true,
                uid = MetaUniqueID.LWJGL3.value,
                version = instance.lwjglVersion.version
            ),
            Component(
                cachedName = "Minecraft",
                cachedRequires = listOf(CachedRequirement(
                    suggests = getSuggestedLWJGL(instance.minecraftVersion).version,
                    uid = MetaUniqueID.LWJGL3.value
                )),
                cachedVersion = instance.minecraftVersion,
                important = true,
                uid = minecraftId,
                version = instance.minecraftVersion
            )
        )

        if (instance.fabricVersion != null) {
            val instanceFabric = instance.fabricVersion!!
            components.add(
                Component(
                    cachedName = "Intermediary Mappings",
                    cachedRequires = listOf(CachedRequirement(
                        equals = instance.minecraftVersion,
                        uid = minecraftId
                    )),
                    cachedVersion = instanceFabric.intermediaryVersion,
                    cachedVolatile = true,
                    dependencyOnly = true,
                    uid = MetaUniqueID.FABRIC_INTERMEDIARY.value,
                    version = instanceFabric.intermediaryVersion
                )
            )
            components.add(
                Component(
                    cachedName = "Fabric Loader",
                    cachedRequires = listOf(CachedRequirement(uid = MetaUniqueID.FABRIC_INTERMEDIARY.value)),
                    cachedVersion = instanceFabric.loaderVersion,
                    uid = MetaUniqueID.FABRIC_LOADER.value,
                    version = instanceFabric.loaderVersion
                )
            )
        }

        val mmcPack = MMCPackData(components)
        return json.encodeToString(mmcPack)
    }

    private fun getSuggestedLWJGL(instanceVersion: String): MetaVersion {
        val minecraftMetaVer = MetaManager.getVersions(MetaUniqueID.MINECRAFT).find { it.version == instanceVersion }!!
        val lwjglRequire = minecraftMetaVer.requires.first()
        val availableLWJGL = MetaManager.getVersions(lwjglRequire.uid)
            .filter { it.version.toVersion(false) >= lwjglRequire.suggests?.toVersion(false )!! }
            .sortedByDescending { it.version.toVersion(false) }

        return availableLWJGL.first { it.version == lwjglRequire.suggests }
    }
}

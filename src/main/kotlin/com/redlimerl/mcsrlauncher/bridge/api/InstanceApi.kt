package com.redlimerl.mcsrlauncher.bridge.api

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.data.instance.BasicInstance
import com.redlimerl.mcsrlauncher.data.instance.FabricVersionData
import com.redlimerl.mcsrlauncher.data.instance.LWJGLVersionData
import com.redlimerl.mcsrlauncher.data.instance.mcsrranked.MCSRRankedPackType
import com.redlimerl.mcsrlauncher.data.meta.IntermediaryType
import com.redlimerl.mcsrlauncher.data.meta.MetaUniqueID
import com.redlimerl.mcsrlauncher.instance.mod.ModCategory
import com.redlimerl.mcsrlauncher.instance.mod.ModDownloadMethod
import com.redlimerl.mcsrlauncher.launcher.InstanceManager
import com.redlimerl.mcsrlauncher.launcher.MetaManager
import com.redlimerl.mcsrlauncher.util.I18n
import com.redlimerl.mcsrlauncher.util.SpeedrunUtils
import com.redlimerl.mcsrlauncher.util.WebLauncherWorker
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import org.apache.logging.log4j.LogManager
import javax.swing.JDialog

class InstanceApi {
    private val logger = LogManager.getLogger("InstanceApi")

    suspend fun handle(method: String, params: JsonObject): JsonElement? {
        return when (method) {
            "instance.getAll" -> getAll()
            "instance.get" -> get(params)
            "instance.launch" -> launch(params)
            "instance.delete" -> delete(params)
            "instance.rename" -> rename(params)
            "instance.moveGroup" -> moveGroup(params)
            "instance.create" -> create(params)
            else -> throw IllegalArgumentException("Unknown instance method: $method")
        }
    }

    private suspend fun create(params: JsonObject): JsonElement? {
        val name = params["name"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("name is required")
        val minecraftVersion = params["minecraftVersion"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("minecraftVersion is required")

        if (!MetaManager.hasLoadedPackages()) {
            throw IllegalStateException(I18n.translate("message.loading") + "...")
        }

        val isRanked = params["isRanked"]?.jsonPrimitive?.booleanOrNull ?: false
        val packTypeRaw = params["mcsrRankedPackType"]?.jsonPrimitive?.contentOrNull
        val mcsrRankedPackType = parseMcsrRankedPackType(packTypeRaw) ?: (if (isRanked) MCSRRankedPackType.BASIC else null)

        if (isRanked && minecraftVersion != "1.16.1") {
            throw IllegalArgumentException("Ranked instances currently require Minecraft 1.16.1")
        }

        fun getLatestVersion(uid: MetaUniqueID): String {
            return MetaManager.getVersions(uid)
                .firstOrNull()?.version ?: throw IllegalStateException("No version found for $uid")
        }

        val fabricLoaderVersion = getLatestVersion(MetaUniqueID.FABRIC_LOADER)

        val fabricIntermediaryVersion = minecraftVersion

        val minorVer = try { minecraftVersion.split(".")[1].toInt() } catch(e: Exception) { 16 }
        val lwjglUid = if(minorVer >= 13)
             MetaUniqueID.LWJGL3
        else MetaUniqueID.LWJGL2

        val lwjglVersion = getLatestVersion(lwjglUid)

        val fabricData = FabricVersionData(
            fabricLoaderVersion,
            IntermediaryType.FABRIC,
            fabricIntermediaryVersion
        )

        val lwjglData = LWJGLVersionData(
            lwjglUid,
            lwjglVersion
        )

        val instance = InstanceManager.createInstance(
            name,
            null,
            minecraftVersion,
            lwjglData,
            fabricData,
            mcsrRankedPackType
        )

        if (mcsrRankedPackType != null) {
            val title = I18n.translate("message.loading")
            val desc = I18n.translate("text.download.assets") + "..."
            val worker = object : WebLauncherWorker(title, desc) {
                override fun work(dialog: JDialog) {
                    try {
                        SpeedrunUtils.getLatestMCSRRankedVersion(this)?.download(instance, this)
                        if (mcsrRankedPackType.versionName != null) {
                            instance.installRecommendedSpeedrunMods(
                                this,
                                mcsrRankedPackType.versionName,
                                ModCategory.RANDOM_SEED,
                                ModDownloadMethod.DOWNLOAD_RECOMMENDS,
                                false
                            )
                        }
                        instance.options.autoModUpdates = true
                        instance.save()
                    } finally {
                        finish()
                    }
                }
            }
            worker.start()
            worker.join()
        }

        return instance.toDTO()
    }

    private fun parseMcsrRankedPackType(raw: String?): MCSRRankedPackType? {
        val value = raw?.trim()?.takeIf { it.isNotBlank() } ?: return null
        return MCSRRankedPackType.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }

    private fun getAll(): JsonElement {
        val result = buildJsonObject {
            InstanceManager.instances.forEach { (group, instances) ->
                putJsonArray(group) {
                    instances.forEach { instance ->
                        add(instance.toDTO())
                    }
                }
            }
        }
        return result
    }

    private fun get(params: JsonObject): JsonElement? {
        val instanceId = params["instanceId"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("instanceId is required")

        val instance = InstanceManager.getInstance(instanceId)
            ?: throw IllegalArgumentException("Instance not found: $instanceId")

        return instance.toDTO()
    }

    private fun launch(params: JsonObject): JsonElement? {
        val instanceId = params["instanceId"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("instanceId is required")

        val instance = InstanceManager.getInstance(instanceId)
            ?: throw IllegalArgumentException("Instance not found: $instanceId")

        if (!instance.isRunning()) {
            instance.launchWithDialog()
        } else {
            logger.warn("Instance $instanceId is already running")
        }

        return null
    }

    private fun delete(params: JsonObject): JsonElement? {
        val instanceId = params["instanceId"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("instanceId is required")

        val instance = InstanceManager.getInstance(instanceId)
            ?: throw IllegalArgumentException("Instance not found: $instanceId")

        if (instance.isRunning()) {
            throw IllegalStateException("Cannot delete a running instance")
        }

        InstanceManager.deleteInstance(instance)
        return null
    }

    private fun rename(params: JsonObject): JsonElement? {
        val instanceId = params["instanceId"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("instanceId is required")
        val newName = params["name"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("name is required")

        val instance = InstanceManager.getInstance(instanceId)
            ?: throw IllegalArgumentException("Instance not found: $instanceId")

        InstanceManager.renameInstance(instance, newName)
        return instance.toDTO()
    }

    private fun moveGroup(params: JsonObject): JsonElement? {
        val instanceId = params["instanceId"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("instanceId is required")
        val newGroup = params["group"]?.jsonPrimitive?.contentOrNull

        val instance = InstanceManager.getInstance(instanceId)
            ?: throw IllegalArgumentException("Instance not found: $instanceId")

        InstanceManager.moveInstanceGroup(instance, newGroup)
        return null
    }

    private fun BasicInstance.toDTO(): JsonObject {
        return buildJsonObject {
            put("id", id)
            put("displayName", displayName)
            put("group", group)
            put("minecraftVersion", minecraftVersion)
            put("type", getInstanceType())
            put("isRunning", isRunning())
            put("playTime", playTime)
            getProcess()?.let { process ->
                put("pid", process.process?.pid())
            }
            put("isMCSRRanked", mcsrRankedType != null)
            fabricVersion?.let { fabric ->
                putJsonObject("fabric") {
                    put("loaderVersion", fabric.loaderVersion)
                    put("intermediaryVersion", fabric.intermediaryVersion)
                }
            }
        }
    }
}
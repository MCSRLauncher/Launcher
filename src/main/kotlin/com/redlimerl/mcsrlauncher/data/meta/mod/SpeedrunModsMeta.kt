package com.redlimerl.mcsrlauncher.data.meta.mod

import com.redlimerl.mcsrlauncher.data.device.RuntimeOSType
import com.redlimerl.mcsrlauncher.data.instance.BasicInstance
import com.redlimerl.mcsrlauncher.data.meta.IntermediaryType
import com.redlimerl.mcsrlauncher.gui.component.SpeedrunModCheckBox
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SpeedrunModMeta(
    @SerialName("modid") val modId: String,
    val name: String,
    val description: String,
    val homepage: String,
    val versions: List<SpeedrunModVersion>,
    val traits: List<SpeedrunModTrait> = listOf(),
    val incompatibilities: List<String> = listOf(),
    val recommended: Boolean = true,
    val obsolete: Boolean = false,
    val priority: Int = 0
) {
    companion object {
        const val VERIFIED_MODS = "verified"
    }

    fun canDownload(instance: BasicInstance, checkObsolete: Boolean = true): Boolean {
        return (!checkObsolete || !this.obsolete) && this.traits.all { trait ->
            when (trait) {
                SpeedrunModTrait.MAC -> RuntimeOSType.MAC_OS.isOn()
                else -> true
            }
        } && this.versions.any { it.isAvailableVersion(instance, checkObsolete) }
    }

    fun createPanel(instance: BasicInstance, checkObsolete: Boolean): SpeedrunModCheckBox {
        val version = this.versions.find { it.isAvailableVersion(instance, checkObsolete) }!!
        return SpeedrunModCheckBox(this, version)
    }
}

@Serializable
data class SpeedrunModVersion(
    @SerialName("target_version") val gameVersions: List<String>,
    val version: String,
    val url: String,
    @SerialName("sha512")
    val hash: String,
    val filename: String,
    val intermediary: List<IntermediaryType>,
    val obsolete: Boolean = false
) {
    fun isAvailableVersion(instance: BasicInstance, checkObsolete: Boolean = true): Boolean {
        val fabric = instance.fabricVersion ?: return false
        return (this.intermediary.isEmpty() || this.intermediary.contains(fabric.intermediaryType))
                && this.gameVersions.contains(instance.minecraftVersion) && (!checkObsolete || !obsolete)
    }
}
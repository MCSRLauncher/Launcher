package com.redlimerl.mcsrlauncher.data.meta

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = LauncherTraitSerializer::class)
enum class LauncherTrait(val serialName: String) {
    @SerialName("FirstThreadOnMacOS") FIRST_THREAD_MACOS("FirstThreadOnMacOS"),
    @SerialName("legacyLaunch") LEGACY_LAUNCH("legacyLaunch"),
    @SerialName("noapplet") NO_APPLET("noapplet"),
    @SerialName("legacyServices") LEGACY_SERVICE("legacyServices"),
    @SerialName("feature:is_quick_play_singleplayer") QUICK_PLAY_SINGLE("feature:is_quick_play_singleplayer"),
    @SerialName("feature:is_quick_play_multiplayer") QUICK_PLAY_SERVER("feature:is_quick_play_multiplayer"),
    @SerialName("stackShadowPages32") STACK_SHADOW_PAGES_32("stackShadowPages32"),
    @SerialName("unknown") UNKNOWN("unknown"),
}

object LauncherTraitSerializer : KSerializer<LauncherTrait> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("MetaUniqueID", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): LauncherTrait {
        val value = decoder.decodeString()
        return LauncherTrait.entries.find { it.serialName == value } ?: LauncherTrait.UNKNOWN
    }

    override fun serialize(encoder: Encoder, value: LauncherTrait) {
        encoder.encodeString(value.serialName)
    }
}

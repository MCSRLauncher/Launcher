package com.redlimerl.mcsrlauncher.data.meta

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = MetaUniqueIDSerializer::class)
enum class MetaUniqueID(val value: String, val isJava: Boolean = false) {
    @SerialName("net.fabricmc.fabric-loader")
    FABRIC_LOADER("net.fabricmc.fabric-loader"),

    @SerialName("net.fabricmc.intermediary")
    FABRIC_INTERMEDIARY("net.fabricmc.intermediary"),

    @SerialName("net.minecraft")
    MINECRAFT("net.minecraft"),

    @SerialName("net.minecraft.java")
    MOJANG_JAVA("net.minecraft.java", true),

    @SerialName("com.azul.java")
    AZUL_JAVA("com.azul.java", true),

    @SerialName("net.adoptium.java")
    ADOPTIUM_JAVA("net.adoptium.java", true),

    @SerialName("org.graalvm.java")
    GRAALVM_JAVA("org.graalvm.java", true),

    @SerialName("org.lwjgl")
    LWJGL2("org.lwjgl"),

    @SerialName("org.lwjgl3")
    LWJGL3("org.lwjgl3"),

    @SerialName("org.mcsr.mods")
    SPEEDRUN_MODS("org.mcsr.mods"),

    @SerialName("org.mcsr.maps.practice")
    PRACTICE_MAPS("org.mcsr.maps.practice"),

    @SerialName("org.mcsr.programs")
    SPEEDRUN_TOOLS("org.mcsr.programs"),

    @SerialName("unknown")
    UNKNOWN("unknown");

    companion object {
        val GAME_METAS = listOf(MINECRAFT, LWJGL2, LWJGL3)
        val FABRIC_METAS = listOf(FABRIC_LOADER, FABRIC_INTERMEDIARY)
        val JAVA_METAS = listOf(MOJANG_JAVA, ADOPTIUM_JAVA, AZUL_JAVA, GRAALVM_JAVA)
    }

}

object MetaUniqueIDSerializer : KSerializer<MetaUniqueID> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("MetaUniqueID", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): MetaUniqueID {
        val value = decoder.decodeString()
        return MetaUniqueID.entries.find { it.value == value } ?: MetaUniqueID.UNKNOWN
    }

    override fun serialize(encoder: Encoder, value: MetaUniqueID) {
        encoder.encodeString(value.value)
    }
}
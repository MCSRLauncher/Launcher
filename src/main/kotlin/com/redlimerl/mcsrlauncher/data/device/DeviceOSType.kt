package com.redlimerl.mcsrlauncher.data.device

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import oshi.PlatformEnum
import oshi.SystemInfo

@Serializable
enum class DeviceOSType(val shellFlags: List<String>, val commandLauncher: (String) -> String) {

    @SerialName("windows") WINDOWS(listOf("cmd", "/c"), { st -> "\"${st.replace(Regex("\\$(\\w+)")) { "%${it.groupValues[1]}%" }}\"" }),
    @SerialName("linux") LINUX(listOf("sh", "c"), { st -> st }),
    @SerialName("osx") MACOS(listOf("sh", "c"), { st -> st });

    companion object {
        val CURRENT_OS = let {
            val osClassifier = when (SystemInfo.getCurrentPlatform()) {
                PlatformEnum.WINDOWS -> WINDOWS
                PlatformEnum.LINUX -> LINUX
                PlatformEnum.MACOS -> MACOS
                else -> throw IllegalArgumentException("Unknown OS: ${SystemInfo.getCurrentPlatform().getName()}")
            }
            osClassifier
        }
    }

    fun isOn(): Boolean {
        return this == CURRENT_OS
    }

}
package com.redlimerl.mcsrlauncher.util

import com.redlimerl.mcsrlauncher.data.device.DeviceOSType
import oshi.SystemInfo
import java.awt.Desktop
import java.io.File
import java.net.URI

object OSUtils {

    val systemInfo = SystemInfo()

    fun getOSType(): DeviceOSType {
        return DeviceOSType.CURRENT_OS
    }

    fun getOSVersion(): String {
        return System.getProperty("os.version")!!
    }

    fun getJavaVersion(): Int {
        val version = System.getProperty("java.version")
        return version.split(".").let {
            if (it[0] == "1") it[1].toInt()
            else it[0].toInt()
        }
    }

    fun getNullFile(): File {
        return if (getOSType() == DeviceOSType.WINDOWS) {
            File("NUL")
        } else {
            File("/dev/null")
        }
    }

    fun getTotalMemoryGB(): Double {
        return systemInfo.hardware.memory.total / (1024.0 * 1024 * 1024)
    }

    fun openURI(uri: URI) {
        if (getOSType() == DeviceOSType.LINUX) {
            Runtime.getRuntime().exec(arrayOf("xdg-open", uri.toString()))
        } else {
            Desktop.getDesktop().browse(uri)
        }
    }

}
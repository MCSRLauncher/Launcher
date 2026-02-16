package com.redlimerl.mcsrlauncher.util

import com.redlimerl.mcsrlauncher.data.device.DeviceOSType
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

object NixGLFWDetector {

    val isNixOS: Boolean by lazy {
        Files.exists(Paths.get("/nix/store"))
    }

    val cachedPath: String? by lazy { detect() }

    fun detect(): String? {
        if (!isNixOS) return null
        return try {
            val process = ProcessBuilder("find", "/nix/store", "-maxdepth", "4", "-name", "libglfw.so", "-type", "f")
                .redirectErrorStream(true)
                .start()
            val finished = process.waitFor(5, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                return null
            }
            val results = process.inputStream.bufferedReader().readLines()
            results.firstOrNull { it.contains("glfw-minecraft") || it.contains("glfw-wayland-minecraft") }
                ?: results.firstOrNull()
        } catch (_: Exception) {
            null
        }
    }
}

/**
 * Caches system capability checks (command existence, GPU detection, etc.)
 * so they only run once instead of every time a settings panel opens.
 */
object SystemCapabilities {

    val feralInstalled: Boolean by lazy { commandExists("gamemoded") }
    val mangoInstalled: Boolean by lazy { commandExists("mangohud") }
    val zinkAvailable: Boolean by lazy { detectZink() }
    val hasNvidia: Boolean by lazy {
        val isLinux = !DeviceOSType.WINDOWS.isOn() && !DeviceOSType.MACOS.isOn()
        isLinux && Files.exists(Path.of("/proc/driver/nvidia/version"))
    }

    private fun commandExists(cmd: String): Boolean {
        val checkCmd = if (DeviceOSType.WINDOWS.isOn()) "where" else "which"
        return try {
            val process = ProcessBuilder(checkCmd, cmd).redirectErrorStream(true).start()
            process.waitFor(3, TimeUnit.SECONDS)
            process.exitValue() == 0
        } catch (_: Exception) {
            false
        }
    }

    private fun detectZink(): Boolean {
        val override = System.getenv("MESA_LOADER_DRIVER_OVERRIDE")
        if (override?.split(':')?.any { it.equals("zink", true) } == true) return true

        val candidates = arrayOf(
            "/usr/lib/dri/zink_dri.so",
            "/usr/lib64/dri/zink_dri.so",
            "/usr/lib/x86_64-linux-gnu/dri/zink_dri.so",
            "/usr/lib/aarch64-linux-gnu/dri/zink_dri.so",
            "/usr/lib/arm-linux-gnueabihf/dri/zink_dri.so"
        )
        if (candidates.any { Files.exists(Path.of(it)) }) return true

        return commandExists("vulkaninfo")
    }

    /** Call early (e.g. at app startup) to warm the cache off the UI thread. */
    fun preload() {
        Thread {
            feralInstalled
            mangoInstalled
            zinkAvailable
            hasNvidia
            NixGLFWDetector.cachedPath
        }.apply { isDaemon = true }.start()
    }
}

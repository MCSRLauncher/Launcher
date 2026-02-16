package com.redlimerl.mcsrlauncher.util

import java.nio.file.Files
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

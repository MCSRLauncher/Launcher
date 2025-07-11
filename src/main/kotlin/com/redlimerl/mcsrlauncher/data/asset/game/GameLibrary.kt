package com.redlimerl.mcsrlauncher.data.asset.game

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.data.asset.BasicAssetObject
import com.redlimerl.mcsrlauncher.data.asset.rule.AssetRule
import com.redlimerl.mcsrlauncher.data.device.DeviceArchitectureType
import com.redlimerl.mcsrlauncher.data.device.RuntimeOSType
import com.redlimerl.mcsrlauncher.launcher.GameAssetManager
import com.redlimerl.mcsrlauncher.network.FileDownloader
import com.redlimerl.mcsrlauncher.util.AssetUtils
import com.redlimerl.mcsrlauncher.util.LauncherWorker
import kotlinx.serialization.Serializable
import java.nio.file.Path

@Serializable
data class GameLibrary(
    val name: String,
    private val downloads: GameLibraryDownload,
    private val natives: Map<RuntimeOSType, String> = mapOf(),
    private val rules: List<AssetRule> = listOf(),
    private val extract: List<GameLibraryExtract> = listOf()
) {
    fun getPath(): Path {
        return AssetUtils.libraryNameToPath(GameAssetManager.LIBRARIES_PATH, this.name)
    }

    fun download(worker: LauncherWorker) {
        if (!this.shouldApply()) return

        val mainJarFile = this.getPath().toFile()
        val mainJarAsset = this.downloads.artifact
        if (mainJarAsset != null) {
            if (!mainJarFile.exists() || mainJarFile.length() != mainJarAsset.size || !AssetUtils.compareHash(mainJarFile, mainJarAsset.sha1)) {
                MCSRLauncher.LOGGER.info("Downloading ${this.name} ...")
                FileDownloader.download(mainJarAsset.url, mainJarFile)
            }
        }

        val classifierKey = this.natives[this.natives.keys.findLast { it.isOn() }]
        if (classifierKey != null) {
            val nativeLibrary = this.downloads.classifiers[classifierKey.replace("\${arch}", DeviceArchitectureType.CURRENT_ARCHITECTURE.bit.toString())]!!
            val nativeLibFile = nativeLibrary.getPathFrom(this).toFile()
            if (!nativeLibFile.exists() || nativeLibFile.length() != nativeLibrary.size || !AssetUtils.compareHash(nativeLibFile, nativeLibrary.sha1)) {
                MCSRLauncher.LOGGER.info("Downloading ${this.name}-native-${classifierKey} ...")
                FileDownloader.download(nativeLibrary.url, nativeLibFile)
            }
        }
    }

    fun getLibraryPaths(): List<Path> {
        val list = arrayListOf<Path>()

        val mainJarAsset = this.downloads.artifact
        if (mainJarAsset != null) list.add(this.getPath())

        val classifierKey = this.natives[this.natives.keys.findLast { it.isOn() }]
        if (classifierKey != null) {
            val nativeLibrary = this.downloads.classifiers[classifierKey.replace("\${arch}", DeviceArchitectureType.CURRENT_ARCHITECTURE.bit.toString())]!!
            list.add(nativeLibrary.getPathFrom(this))
        }
        return list
    }

    fun shouldApply(): Boolean {
        for (rule in this.rules) {
            if (!rule.shouldAllow()) return false
        }
        return true
    }
}

@Serializable
data class GameLibraryDownload(
    val artifact: BasicAssetObject? = null,
    val classifiers: Map<String, BasicAssetObject> = mapOf()
)

@Serializable
data class GameLibraryExtract(
    val exclude: List<String>
)
package com.redlimerl.mcsrlauncher.data.meta.map

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.data.instance.BasicInstance
import com.redlimerl.mcsrlauncher.network.FileDownloader
import com.redlimerl.mcsrlauncher.util.AssetUtils
import com.redlimerl.mcsrlauncher.util.LauncherWorker
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import kotlin.io.path.exists

@Serializable
data class MinecraftMapMeta(
    val name: String,
    val description: String,
    val authors: List<String>,
    val downloadUrl: String,
    val downloadSize: Int,
    val sources: String? = null,
    val versions: List<String>
) {

    fun canDownload(instance: BasicInstance): Boolean {
        for (version in versions) {
            if (AssetUtils.isInVersionRange(version, instance.minecraftVersion)) return true
        }
        return false
    }

    private fun mapName(): String {
        return if (this.downloadUrl.endsWith(".zip")) {
            this.downloadUrl.split("/").last().dropLast(4)
        } else this.name
    }

    fun install(instance: BasicInstance, worker: LauncherWorker) {
        worker.setState("Downloading ${mapName()}...")

        var path = instance.getWorldsPath().resolve(mapName())
        var index = 0
        while (path.exists()) path = instance.getWorldsPath().resolve(mapName() + "_" + ++index)
        val targetDir = path.toFile()
        targetDir.mkdirs()

        val tempFile = File.createTempFile("minecraftMap", MCSRLauncher.APP_NAME)
        FileDownloader.download(this.downloadUrl, tempFile, worker, this.downloadSize.toLong())

        worker.setState("Extracting ${mapName()}...")

        val levelDatPath = ZipFile(tempFile).use { zip ->
            zip.entries().asSequence().map { it.name }.firstOrNull { it.endsWith("level.dat") } ?: throw IllegalStateException("level.dat not found in ZIP")
        }

        val worldDirPrefix = levelDatPath.substring(0, levelDatPath.length - 9)

        ZipInputStream(tempFile.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val shouldExtract = if (worldDirPrefix.isEmpty()) {
                        true
                    } else {
                        entry.name.startsWith(worldDirPrefix)
                    }

                    if (shouldExtract) {
                        val relativeName = if (worldDirPrefix.isEmpty()) {
                            entry.name
                        } else {
                            entry.name.removePrefix(worldDirPrefix)
                        }
                        val outFile = File(targetDir, relativeName)
                        worker.setSubText(relativeName)
                        outFile.parentFile.mkdirs()
                        Files.copy(zip, outFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        tempFile.delete()
    }
}
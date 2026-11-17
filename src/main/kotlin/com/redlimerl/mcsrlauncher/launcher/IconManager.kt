package com.redlimerl.mcsrlauncher.launcher

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.data.instance.BasicInstance
import com.redlimerl.mcsrlauncher.util.I18n
import java.awt.Image
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.ImageIcon
import javax.swing.JOptionPane
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.notExists

object IconManager {

    val path: Path = MCSRLauncher.BASE_PATH.resolve("icons")
    private val iconMap = mutableMapOf<String, ImageIcon>()

    fun scanDirectory() {
        iconMap.clear()

        if (path.notExists()) path.toFile().mkdirs()
        if (path.toFile().list()?.isEmpty() == true) return
        val iconList = Files.walk(path).use { paths ->
            paths.filter { it.fileName.extension.lowercase() in setOf("png", "jpg", "jpeg") }
                .toList()
        }

        iconList.forEach {
            iconMap[it.nameWithoutExtension] = ImageIcon(ImageIcon(it.toString()).image.getScaledInstance(60, 60, Image.SCALE_SMOOTH))
        }
    }

    fun getIcon(iconKey: String?, instance: BasicInstance?) : ImageIcon {
        return iconMap[iconKey]?.takeIf { iconKey != "default" && iconKey != null } ?: when {
            instance?.draftoutFormat != null -> ImageIcon(javaClass.getResource("/icons/draftout.png"))
            instance?.mcsrRankedType != null -> ImageIcon(javaClass.getResource("/icons/mcsrranked.png"))
            instance?.fabricVersion != null -> ImageIcon(javaClass.getResource("/icons/fabric.png"))
            else -> ImageIcon(javaClass.getResource("/icons/minecraft.png"))
        }
    }

    fun getIconMap() : MutableMap<String, ImageIcon> {
        scanDirectory()
        return iconMap
    }

    fun addIcon(iconList: List<*>) {
        iconList.forEach {
            if (it is File) {
                val targetFile = path.resolve(it.name).toFile()
                if (targetFile.exists()) {
                    val overwrite = JOptionPane.showConfirmDialog(null, I18n.translate("text.overwrite"), I18n.translate("text.warning"), JOptionPane.YES_NO_OPTION)
                    if (overwrite != JOptionPane.YES_OPTION) {
                        return
                    }
                }
                it.copyTo(targetFile, overwrite = true)
            }
        }
    }

    fun removeIcon(icons: MutableList<String>) {
        icons.forEach { icon ->
            val file = path.toFile().listFiles()?.first { it.nameWithoutExtension == icon }
            file?.delete()
        }
    }
}

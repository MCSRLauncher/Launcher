package com.redlimerl.mcsrlauncher.data.instance

import com.redlimerl.mcsrlauncher.launcher.InstanceManager
import com.redlimerl.mcsrlauncher.util.I18n
import com.redlimerl.mcsrlauncher.util.LauncherWorker
import java.io.File

data class InstanceCopyOption(
    var worlds: Boolean = false,
    var gameOptions: Boolean = false,
    var mods: Boolean = false,
    var modConfigs: Boolean = false,
    var resourcePacks: Boolean = false,
    var playTime: Boolean = false
) {
    fun copyInstance(instanceName: String, instanceGroup: String?, baseInstance: BasicInstance, worker: LauncherWorker) {
        val newInstance = InstanceManager.createInstance(instanceName, instanceGroup, baseInstance.minecraftVersion, baseInstance.lwjglVersion.copy(), baseInstance.fabricVersion?.copy(), baseInstance.mcsrRankedType, baseInstance.draftoutFormat, baseInstance.iconKey)

        if (worlds) {
            worker.setSubText(I18n.translate("text.copy.worlds.progress"))
            copyFiles(baseInstance, newInstance) { it.getWorldsPath().toFile() }
        }
        if (gameOptions) {
            worker.setSubText(I18n.translate("text.copy.game_options.progress"))
            val files = listOf("hotbar.nbt", "options.txt", "servers.dat", "servers.dat_old")
            for (file in files) {
                copyFiles(baseInstance, newInstance) { it.getGamePath().resolve(file).toFile() }
            }
        }
        if (mods) {
            worker.setSubText(I18n.translate("text.copy.mods.progress"))
            copyFiles(baseInstance, newInstance) { it.getModsPath().toFile() }
        }
        if (modConfigs) {
            worker.setSubText(I18n.translate("text.copy.mod_configs.progress"))
            copyFiles(baseInstance, newInstance) { it.getGamePath().resolve("speedrunigt").toFile() }
            copyFiles(baseInstance, newInstance) { it.getGamePath().resolve("config").toFile() }
        }
        if (resourcePacks) {
            worker.setSubText(I18n.translate("text.copy.resource_packs.progress"))
            copyFiles(baseInstance, newInstance) { it.getGamePath().resolve("resourcepacks").toFile() }
            copyFiles(baseInstance, newInstance) { it.getGamePath().resolve("shaderpacks").toFile() }
            copyFiles(baseInstance, newInstance) { it.getGamePath().resolve("texturepacks").toFile() }
        }
        if (playTime) {
            worker.setSubText(I18n.translate("text.copy.play_time.progress"))
            newInstance.playTime = baseInstance.playTime
        }
        worker.setSubText(null)
        newInstance.options = baseInstance.options.copy()
        newInstance.save()
    }

    private fun copyFiles(baseInstance: BasicInstance, newInstance: BasicInstance, targetFile: (BasicInstance) -> File) {
        val baseFile = targetFile(baseInstance)
        val newFile = targetFile(newInstance)

        if (baseFile.exists()) {
            newFile.parentFile.mkdirs()
            if (baseFile.isDirectory) baseFile.copyRecursively(newFile)
            else baseFile.copyTo(newFile)
        }
    }
}
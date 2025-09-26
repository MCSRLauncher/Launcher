package com.redlimerl.mcsrlauncher.gui

import com.redlimerl.mcsrlauncher.data.instance.BasicInstance
import com.redlimerl.mcsrlauncher.data.meta.mod.SpeedrunModMeta
import com.redlimerl.mcsrlauncher.instance.mod.ModCategory
import com.redlimerl.mcsrlauncher.instance.mod.ModDownloadMethod
import com.redlimerl.mcsrlauncher.util.I18n
import com.redlimerl.mcsrlauncher.util.LauncherWorker
import java.awt.Dimension
import javax.swing.JDialog
import javax.swing.JOptionPane

class SpeedrunModsManageGui(parent: JDialog, val instance: BasicInstance, isNew: Boolean, val updater: () -> Unit) : SpeedrunModsManageDialog(parent) {

    init {
        title = I18n.translate("text.manage_speedrun_mods")
        minimumSize = Dimension(400, 200)
        setLocationRelativeTo(parent)

        ModCategory.entries.forEach { categoryComboBox.addItem(it) }
        if (isNew) {
            downloadTypeComboBox.addItem(ModDownloadMethod.DOWNLOAD_RECOMMENDS)
            downloadTypeComboBox.isEnabled = false
        } else {
            ModDownloadMethod.entries.forEach { downloadTypeComboBox.addItem(it) }
        }

        accessibilityModsCheckBox.addActionListener {
            if (accessibilityModsCheckBox.isSelected) {
                JOptionPane.showMessageDialog(this@SpeedrunModsManageGui, I18n.translate("message.warning.include_accessibility_mods"), I18n.translate("text.warning"), JOptionPane.WARNING_MESSAGE)
            }
        }

        applyButton.addActionListener {
            object : LauncherWorker(this@SpeedrunModsManageGui, I18n.translate("message.loading"), I18n.translate("text.download.assets").plus("...")) {
                override fun work(dialog: JDialog) {
                    instance.installRecommendedSpeedrunMods(this, SpeedrunModMeta.VERIFIED_MODS, categoryComboBox.getItemAt(categoryComboBox.selectedIndex), downloadTypeComboBox.getItemAt(downloadTypeComboBox.selectedIndex), accessibilityModsCheckBox.isSelected)
                    this@SpeedrunModsManageGui.dispose()
                    updater()
                }

                override fun onError(e: Throwable) {
                    updater()
                }
            }.showDialog().start()
        }

        browseButton.addActionListener {
            this.dispose()
            SpeedrunModsBrowseGui(parent, instance)
        }

        cancelButton.addActionListener { this.dispose() }

        I18n.translateGui(this)
        isVisible = true
    }
}
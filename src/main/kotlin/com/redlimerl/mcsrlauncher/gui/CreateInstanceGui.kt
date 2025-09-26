package com.redlimerl.mcsrlauncher.gui

import com.redlimerl.mcsrlauncher.gui.component.GameVersionsPanel
import com.redlimerl.mcsrlauncher.gui.component.InstanceGroupComboBox
import com.redlimerl.mcsrlauncher.instance.mod.ModCategory
import com.redlimerl.mcsrlauncher.instance.mod.ModDownloadMethod
import com.redlimerl.mcsrlauncher.launcher.InstanceManager
import com.redlimerl.mcsrlauncher.util.I18n
import com.redlimerl.mcsrlauncher.util.LauncherWorker
import com.redlimerl.mcsrlauncher.util.SpeedrunUtils
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JOptionPane


class CreateInstanceGui(parent: JFrame) : CreateInstanceDialog(parent) {

    private val gameVersionsPanel: GameVersionsPanel

    init {
        title = I18n.translate("instance.new")
        minimumSize = Dimension(700, 550)
        setLocationRelativeTo(parent)

        instanceNameField.text = I18n.translate("instance.new")
        InstanceGroupComboBox.init(instanceGroupBox)

        cancelButton.addActionListener { this.dispose() }
        createInstanceButton.addActionListener { this.createInstance() }

        this.gameVersionsPanel = GameVersionsPanel(this)
        versionsPanel.layout = BorderLayout()
        versionsPanel.add(this.gameVersionsPanel, BorderLayout.CENTER)

        I18n.translateGui(this)
        isVisible = true
    }

    private fun createInstance() {
        if (instanceNameField.text.isNullOrBlank()) return

        val instance = InstanceManager.createInstance(instanceNameField.text, instanceGroupBox.selectedItem?.toString()?.trimEnd(), gameVersionsPanel.getMinecraftVersion().version, gameVersionsPanel.getLWJGLVersion(), gameVersionsPanel.getFabricVersion(), gameVersionsPanel.getMCSRRankedPackType())
        val mcsrRankedPackType = instance.mcsrRankedType

        this.dispose()
        val launch = {
            val launchConfirm = JOptionPane.showConfirmDialog(this, I18n.translate("message.instance_launch_ask"), I18n.translate("instance.launch"), JOptionPane.YES_NO_OPTION)
            if (launchConfirm == JOptionPane.YES_OPTION) {
                instance.launchWithDialog()
            }
        }

        if (mcsrRankedPackType != null) {
            object : LauncherWorker(this@CreateInstanceGui, I18n.translate("message.loading"), I18n.translate("text.download.assets").plus("...")) {
                override fun work(dialog: JDialog) {
                    SpeedrunUtils.getLatestMCSRRankedVersion(this)?.download(instance, this)
                    if (mcsrRankedPackType.versionName != null) {
                        instance.installRecommendedSpeedrunMods(this, mcsrRankedPackType.versionName, ModCategory.RANDOM_SEED, ModDownloadMethod.DOWNLOAD_RECOMMENDS, false)
                    }
                    instance.options.autoModUpdates = true
                    instance.save()
                    launch()
                }

                override fun onError(e: Throwable) {
                    launch()
                }
            }.showDialog().start()
            return
        } else if (instance.fabricVersion != null) {
            val modInit = JOptionPane.showConfirmDialog(this, I18n.translate("message.speedrun_mods_setup_ask"), I18n.translate("text.manage_speedrun_mods"), JOptionPane.YES_NO_OPTION)
            if (modInit == JOptionPane.YES_OPTION) {
                SpeedrunModsManageGui(this, instance, true) {
                    val autoUpdate = JOptionPane.showConfirmDialog(this, I18n.translate("message.auto_mod_update_ask"), I18n.translate("text.manage_speedrun_mods"), JOptionPane.YES_NO_OPTION)
                    if (autoUpdate == JOptionPane.YES_OPTION) {
                        instance.options.autoModUpdates = true
                        instance.save()
                    }
                    launch()
                }
                return
            }
        }
        launch()
    }

}
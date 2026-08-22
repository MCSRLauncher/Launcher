package com.redlimerl.mcsrlauncher.gui.component

import com.redlimerl.mcsrlauncher.gui.components.AbstractPaceManSettingsPanel
import com.redlimerl.mcsrlauncher.launcher.PaceManManager
import com.redlimerl.mcsrlauncher.util.I18n
import com.redlimerl.mcsrlauncher.util.LauncherWorker
import java.awt.BorderLayout
import javax.swing.JDialog
import javax.swing.JOptionPane

class PaceManSettingsPanel(private val parent: JDialog) : AbstractPaceManSettingsPanel() {

    init {
        layout = BorderLayout()
        add(this.rootPanel, BorderLayout.CENTER)

        installButton.addActionListener {
            if (PaceManManager.isDownloaded()) {
                PaceManManager.uninstall()
                refreshState()
            } else {
                object : LauncherWorker(parent, I18n.translate("text.paceman.download"), I18n.translate("message.loading")) {
                    override fun work(dialog: JDialog) {
                        PaceManManager.download(this)
                        refreshState()
                    }
                }.showDialog().start()
            }
        }

        configureButton.addActionListener {
            try {
                //Opens the Paceman Tracker as an extra Window, could be integrated as well
                PaceManManager.openConfigWindow(parent)
            } catch (e: Exception) {
                JOptionPane.showMessageDialog(parent, e.message, I18n.translate("text.error"), JOptionPane.ERROR_MESSAGE)
            }
        }

        updateButton.addActionListener {
            object : LauncherWorker(parent, I18n.translate("text.paceman.update"), I18n.translate("message.loading")) {
                override fun work(dialog: JDialog) {
                    PaceManManager.download(this)
                    refreshState()
                }
            }.showDialog().start()
        }

        refreshState()
    }

    private fun refreshState() {
        val downloaded = PaceManManager.isDownloaded()
        installButton.text = I18n.translate(if (downloaded) "text.paceman.uninstall" else "text.paceman.download")
        configureButton.isEnabled = downloaded
        updateButton.isVisible = downloaded && PaceManManager.updateAvailable
    }
}

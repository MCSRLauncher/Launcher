package com.redlimerl.mcsrlauncher.gui

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.data.launcher.LauncherLanguage
import com.redlimerl.mcsrlauncher.data.launcher.LauncherOptions
import com.redlimerl.mcsrlauncher.gui.component.JavaSettingsPanel
import com.redlimerl.mcsrlauncher.gui.component.LogViewerPanel
import com.redlimerl.mcsrlauncher.gui.component.ResolutionSettingsPanel
import com.redlimerl.mcsrlauncher.gui.component.WorkaroundSettingsPanel
import com.redlimerl.mcsrlauncher.launcher.MetaManager
import com.redlimerl.mcsrlauncher.util.I18n
import com.redlimerl.mcsrlauncher.util.LauncherWorker
import com.redlimerl.mcsrlauncher.util.SwingUtils
import com.redlimerl.mcsrlauncher.util.UpdaterUtils
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JOptionPane
import javax.swing.SpinnerNumberModel
import kotlin.math.min

class LauncherOptionGui(parent: JFrame, private val onDispose: () -> Unit) : LauncherOptionDialog(parent) {

    init {
        title = I18n.translate("text.settings")
        minimumSize = Dimension(700, 500)
        defaultCloseOperation = DISPOSE_ON_CLOSE
        setLocationRelativeTo(parent)

        this.cancelButton.addActionListener { this.dispose() }

        this.initLauncherTab()
        this.initJavaTab()
        this.initInterfaceTab()
        this.initWorkaroundTab()
        this.initLogTab()

        I18n.translateGui(this)
        isVisible = true
    }

    override fun dispose() {
        super.dispose()
        onDispose()
    }

    private fun initLauncherTab() {
        this.launcherVersionLabel.text = I18n.translate("text.version.launcher") + ": v${MCSRLauncher.APP_VERSION}"

        for ((index, value) in LauncherLanguage.entries.withIndex()) {
            this.languageComboBox.addItem(value)
            if (MCSRLauncher.options.language == value) this.languageComboBox.selectedIndex = index
        }
        this.languageComboBox.addActionListener {
            val language = this.languageComboBox.selectedItem as LauncherLanguage?
            if (language != null) {
                MCSRLauncher.options.language = language
                MCSRLauncher.options.save()
            }
        }

        this.refreshMetaButton.addActionListener {
            object : LauncherWorker(this@LauncherOptionGui, I18n.translate("message.loading"), I18n.translate("message.updating.meta")) {
                override fun work(dialog: JDialog) = MetaManager.load(this, true)
            }.showDialog().start()
        }

        this.checkUpdateButton.addActionListener {
            object : LauncherWorker(this@LauncherOptionGui, I18n.translate("text.check_update"), I18n.translate("message.checking_updates")) {
                override fun work(dialog: JDialog) {
                    val latestVersion = UpdaterUtils.checkLatestVersion(this)
                    dialog.dispose()
                    if (latestVersion != null) {
                        val updateConfirm = JOptionPane.showConfirmDialog(this@LauncherOptionGui, I18n.translate("message.new_update_found").plus("\nCurrent: ${MCSRLauncher.APP_VERSION}\nNew: $latestVersion"), I18n.translate("text.check_update"), JOptionPane.YES_NO_OPTION)
                        if (updateConfirm == JOptionPane.YES_OPTION) {
                            UpdaterUtils.launchUpdater()
                        }
                    } else {
                        JOptionPane.showMessageDialog(this@LauncherOptionGui, I18n.translate("message.latest_version"))
                    }
                }
            }.showDialog().start()
        }

        SwingUtils.fasterScroll(this.tabLauncherScrollPane)

        val resolutionSettingsPanel = ResolutionSettingsPanel(MCSRLauncher.options, MCSRLauncher.options::save)
        this.launcherGameResolutionPane.layout = BorderLayout()
        this.launcherGameResolutionPane.add(resolutionSettingsPanel, BorderLayout.CENTER)

        this.concurrentDownloadsSpinner.model = SpinnerNumberModel(MCSRLauncher.options.concurrentDownloads, 1, 32, 1)
        this.concurrentDownloadsSpinner.addChangeListener {
            this.concurrentDownloadsSpinner.value = min(this.concurrentDownloadsSpinner.value as Int, 32)
            MCSRLauncher.options.concurrentDownloads = this.concurrentDownloadsSpinner.value as Int
            MCSRLauncher.options.save()
        }
    }

    private fun initJavaTab() {
        val javaSettingsPanel = JavaSettingsPanel(this, MCSRLauncher.options, MCSRLauncher.options::save)
        this.tabJavaScrollPane.setViewportView(javaSettingsPanel)
        SwingUtils.fasterScroll(this.tabJavaScrollPane)
    }

    private fun initInterfaceTab() {
    }

    private fun initWorkaroundTab() {
        val launcherOptions = LauncherOptions.load()
        val workaroundPanel = WorkaroundSettingsPanel(
            this,
            launcherOptions,
            { launcherOptions.save() }
        )

        tabWorkaroundScrollPane.viewport.view = workaroundPanel
    }

    private fun initLogTab() {
        logPanel.layout = BorderLayout()
        logPanel.add(LogViewerPanel(MCSRLauncher.BASE_PATH).also { it.syncLauncher() }, BorderLayout.CENTER)
    }
}
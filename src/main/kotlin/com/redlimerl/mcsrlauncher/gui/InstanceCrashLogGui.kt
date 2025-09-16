package com.redlimerl.mcsrlauncher.gui

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.gui.component.AnalysisEditorPane
import com.redlimerl.mcsrlauncher.util.AnalysisUtils.analyzeLog
import com.redlimerl.mcsrlauncher.util.I18n
import com.redlimerl.mcsrlauncher.util.LauncherWorker
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Window
import java.net.URI
import javax.swing.JDialog
import javax.swing.event.HyperlinkEvent

class InstanceCrashLogGui(window: Window?, log: String) : InstanceCrashLogDialog(window) {
    init {
        title = I18n.translate("text.log")
        minimumSize = Dimension(400, 240)
        setLocationRelativeTo(window)

        object : LauncherWorker() {
            override fun work(dialog: JDialog) {
                statusLabel.text = I18n.translate("text.analyzing")
                val result = analyzeLog(log, this)
                analyzeContextLabel.text = result
                minimumSize = Dimension(500, 300)
                statusLabel.text = I18n.translate("text.analyzed")
            }
        }.start()

        AnalysisEditorPane.init(analyzeContextLabel)

        cancelButton.addActionListener { this.dispose() }

        I18n.translateGui(this)
    }
}
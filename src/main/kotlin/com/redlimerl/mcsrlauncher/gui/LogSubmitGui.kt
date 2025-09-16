package com.redlimerl.mcsrlauncher.gui

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.gui.component.AnalysisEditorPane
import com.redlimerl.mcsrlauncher.util.AnalysisUtils.analyzeLog
import com.redlimerl.mcsrlauncher.util.I18n
import com.redlimerl.mcsrlauncher.util.LauncherWorker
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.Window
import java.awt.datatransfer.StringSelection
import java.net.URI
import javax.swing.JDialog
import javax.swing.event.HyperlinkEvent

class LogSubmitGui(window: Window) : LogSubmitDialog(window) {
    var targetUrl: String? = ""

    init {
        title = I18n.translate("text.log")
        minimumSize = Dimension(400, 240)
        setLocationRelativeTo(window)

        openButton.isEnabled = false
        openButton.addActionListener {
            targetUrl?.let {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(it), null)
                Desktop.getDesktop().browse(URI.create(it))
            }
        }

        AnalysisEditorPane.init(analyzeContextLabel)

        analyzeButton.isEnabled = false
        analyzeButton.addActionListener {
            if (targetUrl == null) return@addActionListener

            object : LauncherWorker() {
                override fun work(dialog: JDialog) {
                    analyzeButton.isEnabled = false
                    statusLabel.text = I18n.translate("text.analyzing")
                    targetUrl?.let { url ->
                        val result = analyzeLog(url, this)
                        analyzeContextLabel.text = result
                    }
                    minimumSize = Dimension(500, 300)
                    statusLabel.text = I18n.translate("text.analyzed")
                }
            }.start()
        }

        cancelButton.addActionListener { this.dispose() }

        I18n.translateGui(this)
        analyzeButton.text += " (via Background Pingu)"
    }

    fun updateUrl(url: String, activeAnalyze: Boolean) {
       statusLabel.text = I18n.translate("text.uploaded")
       targetUrl = url
       analyzeContextLabel.text = url
       openButton.isEnabled = true
       analyzeButton.isEnabled = activeAnalyze
    }
}
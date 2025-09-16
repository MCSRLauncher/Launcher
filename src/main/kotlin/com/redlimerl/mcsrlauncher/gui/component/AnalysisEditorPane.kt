package com.redlimerl.mcsrlauncher.gui.component

import com.redlimerl.mcsrlauncher.MCSRLauncher
import java.awt.Desktop
import java.net.URI
import javax.swing.JEditorPane
import javax.swing.event.HyperlinkEvent

object AnalysisEditorPane {

    fun init(editorPane: JEditorPane) {
        editorPane.contentType = "text/html"
        editorPane.addHyperlinkListener {
            if (it.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                try {
                    Desktop.getDesktop().browse(URI(it.url.toString()))
                } catch (ex: Exception) {
                    MCSRLauncher.LOGGER.error("Failed to open: ${it.url}", ex)
                }
            }
        }
    }
}
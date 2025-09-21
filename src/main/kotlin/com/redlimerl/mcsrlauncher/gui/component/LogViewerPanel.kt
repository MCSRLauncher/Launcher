package com.redlimerl.mcsrlauncher.gui.component

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.data.instance.BasicInstance
import com.redlimerl.mcsrlauncher.gui.LogSubmitGui
import com.redlimerl.mcsrlauncher.gui.components.AbstractLogViewerPanel
import com.redlimerl.mcsrlauncher.util.HttpUtils.makeJsonRequest
import com.redlimerl.mcsrlauncher.util.I18n
import com.redlimerl.mcsrlauncher.util.LauncherWorker
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity
import org.apache.hc.core5.http.message.BasicNameValuePair
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Color
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import javax.swing.JDialog
import javax.swing.JOptionPane
import javax.swing.JTextPane
import javax.swing.SwingUtilities
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.text.*

class LogViewerPanel(private val basePath: Path) : AbstractLogViewerPanel() {

    var displayLiveLog = true
    private var autoScrollLive = true
    private var searchCount = 0
    private var instanceMode = false
    private var lastLogName = ""

    init {
        layout = BorderLayout()
        add(this.rootPane, BorderLayout.CENTER)

        (liveLogPane.caret as DefaultCaret).updatePolicy = DefaultCaret.NEVER_UPDATE
        liveScrollPane.viewport.addChangeListener {
            val viewport = liveScrollPane.viewport
            val viewEnd = viewport.viewPosition.y + viewport.height
            val docHeight = liveLogPane.height

            autoScrollLive = (viewEnd >= (docHeight - (liveLogPane.font.size * 2)))
        }

        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = textChanged()
            override fun removeUpdate(e: DocumentEvent) = textChanged()
            override fun changedUpdate(e: DocumentEvent) = textChanged()

            fun textChanged() {
                searchCount = 0
            }
        })
        searchField.addActionListener {
            focusWord(getFocusedArea(), searchField.text)
        }
        findButton.addActionListener {
            focusWord(getFocusedArea(), searchField.text)
        }

        updateLogFiles()
        logFileBox.addActionListener {
            val selected = logFileBox.selectedItem as? String
            if (selected == I18n.translate("text.process")) {
                (logCardPanel.layout as CardLayout).show(logCardPanel, "live")
                displayLiveLog = true
            } else {
                updateLogFile(selected)
                (logCardPanel.layout as CardLayout).show(logCardPanel, "file")
                displayLiveLog = false
            }
            searchCount = 0
        }

        reloadButton.addActionListener {
            if (!displayLiveLog) updateLogFile(logFileBox.selectedItem as String)
        }

        copyButton.addActionListener {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(getFocusedArea().text), null)
        }

        uploadButton.addActionListener {
            val selected = logFileBox.selectedItem as String
            val text = getFocusedArea().text
            if (text.isBlank()) return@addActionListener

            val result = JOptionPane.showConfirmDialog(this@LogViewerPanel, I18n.translate("message.upload_log_ask", selected), I18n.translate("text.warning"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
            if (result == JOptionPane.YES_OPTION) {
                val post = HttpPost("https://api.mclo.gs/1/log")
                post.entity = UrlEncodedFormEntity(listOf(BasicNameValuePair("content", text)), StandardCharsets.UTF_8)

                val submitDialog = LogSubmitGui(SwingUtilities.getWindowAncestor(uploadButton))
                object : LauncherWorker() {
                    override fun work(dialog: JDialog) {
                        val request = makeJsonRequest(post, this)
                        if (request.hasSuccess()) {
                            val json = request.get<JsonObject>()
                            val url = json["url"]?.jsonPrimitive?.content ?: throw IllegalStateException("Unknown response: $json")
                            submitDialog.updateUrl(url, instanceMode)
                        } else {
                            submitDialog.statusLabel.text = I18n.translate("message.upload_log_fail")
                        }
                    }
                }.start()
                submitDialog.isVisible = true
            }
        }
    }

    fun syncInstance(instance: BasicInstance) {
        instance.getProcess()?.syncLogViewer(this)
        debugCheckBox.actionListeners.toMutableList().forEach { debugCheckBox.removeActionListener(it) }
        debugCheckBox.isVisible = false
        this.instanceMode = true
    }

    fun syncLauncher() {
        MCSRLauncher.LOG_APPENDER.syncLogViewer(this)
        debugCheckBox.actionListeners.toMutableList().forEach { debugCheckBox.removeActionListener(it) }
        debugCheckBox.addActionListener {
            liveLogPane.text = ""
            syncLauncher()
        }
        debugCheckBox.isVisible = true
        this.instanceMode = false
    }

    fun onLiveUpdate() {
        if (autoScrollLive) {
            liveLogPane.caretPosition = liveLogPane.document.length
        }
    }

    fun appendString(textPane: JTextPane, message: String, multipleLines: Boolean = false) {
        val doc: StyledDocument = textPane.styledDocument
        val style = SimpleAttributeSet()

        val strings = if (multipleLines) message.lines() else listOf(message)
        for (string in strings) {
            if (string.isEmpty()) continue
            when {
                string.contains("ERROR", true) -> StyleConstants.setForeground(style, Color(255, 60, 60))
                string.contains("WARN") -> StyleConstants.setForeground(style, Color(0xCA7733))
                string.contains("DEBUG") -> StyleConstants.setForeground(style, Color(171, 171, 171))
                else -> StyleConstants.setForeground(style, Color.WHITE)
            }
            doc.insertString(doc.length, string + (if (string.endsWith("\n")) "" else "\n"), style)
        }
    }

    private fun getFocusedArea(): JTextPane {
        return if (displayLiveLog) liveLogPane else fileLogPane
    }

    private fun focusWord(pane: JTextPane, word: String) {
        if (word.isBlank()) return

        val content = pane.text
        val indexes = mutableListOf<Int>()
        var searchIndex = content.indexOf(word)
        while (searchIndex >= 0) {
            indexes.add(searchIndex)
            searchIndex = content.indexOf(word, searchIndex + word.length)
        }

        if (indexes.size <= searchCount) {
            searchCount = 0
        }

        if (indexes.isEmpty()) return

        val targetIndex = indexes[searchCount]
        pane.caretPosition = targetIndex

        val highlighter = pane.highlighter
        highlighter.removeAllHighlights()

        for (index in indexes) {
            highlighter.addHighlight(index, index + word.length, DefaultHighlighter.DefaultHighlightPainter(if (index == targetIndex) Color.ORANGE else Color.ORANGE.darker().darker()))
        }

        pane.modelToView2D(targetIndex)?.let { pane.scrollRectToVisible(it.bounds) }
        searchCount++
    }

    fun updateLogFiles() {
        val selected = logFileBox.selectedItem as? String
        logFileBox.removeAllItems()
        logFileBox.addItem(I18n.translate("text.process"))

        val logsDir = basePath.resolve("logs").toFile()
        if (logsDir.exists() && logsDir.isDirectory) {
            for (file in logsDir.listFiles()!!.sortedByDescending { it.name }) {
                logFileBox.addItem("logs/" + file.name)
            }
        }

        val crashReportDir = basePath.resolve("crash-reports").toFile()
        if (crashReportDir.exists() && crashReportDir.isDirectory) {
            for (file in crashReportDir.listFiles()!!.sortedByDescending { it.name }) {
                logFileBox.addItem("crash-reports/" + file.name)
            }
        }

        for (i in 0 until logFileBox.itemCount) {
            if (logFileBox.getItemAt(i) == selected) {
                logFileBox.selectedIndex = i
                break
            }
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun updateLogFile(fileName: String?) {
        if (fileName == null || fileName == lastLogName) return

        val logFile = basePath.resolve(fileName).toFile()
        if (!logFile.exists()) return

        fileLogPane.text = I18n.translate("message.loading") + "..."
        GlobalScope.launch {
            val text = when (logFile.extension) {
                "gz" -> GZIPInputStream(FileInputStream(logFile)).use { gzip ->
                    InputStreamReader(gzip, Charsets.UTF_8).use { reader -> reader.readText() }
                }
                "txt", "log" -> logFile.readText()
                else -> ""
            }
            SwingUtilities.invokeLater {
                fileLogPane.text = ""
                text.lines().forEach {
                    if (!it.contains("[DEBUG]") || enabledDebug()) {
                        appendString(fileLogPane, it + "\n")
                    }
                }
                fileLogPane.caretPosition = 0
            }
            lastLogName = fileName
        }
    }

    fun enabledDebug(): Boolean {
        return this.debugCheckBox.isSelected
    }
}
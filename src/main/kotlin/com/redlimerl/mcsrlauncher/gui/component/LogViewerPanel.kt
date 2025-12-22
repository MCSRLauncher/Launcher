package com.redlimerl.mcsrlauncher.gui.component

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.data.instance.BasicInstance
import com.redlimerl.mcsrlauncher.gui.LogSubmitGui
import com.redlimerl.mcsrlauncher.gui.components.AbstractLogViewerPanel
import com.redlimerl.mcsrlauncher.util.HttpUtils
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

private const val MAX_LOG_COUNT = 2000

class LogViewerPanel(private val basePath: Path) : AbstractLogViewerPanel() {

    var displayLiveLog = true
    private var autoScrollLive = true
    private var searchCount = 0
    private var instanceMode = false
    private var lastLogName = ""

    companion object {
        private val ERROR_STYLE = SimpleAttributeSet().apply { StyleConstants.setForeground(this, Color(255, 60, 60)) }
        private val WARN_STYLE = SimpleAttributeSet().apply { StyleConstants.setForeground(this, Color(0xCA7733)) }
        private val DEBUG_STYLE = SimpleAttributeSet().apply { StyleConstants.setForeground(this, Color(171, 171, 171)) }
        private val DEFAULT_STYLE = SimpleAttributeSet().apply { StyleConstants.setForeground(this, Color.WHITE) }
    }

    init {
        layout = BorderLayout()
        add(this.rootPane, BorderLayout.CENTER)

        liveLogPane.isEditable = false
        liveScrollPane.setViewportView(liveLogPane)

        // Fix for extra line spacing in JTextPane
        val doc = liveLogPane.styledDocument
        val defaultStyle = StyleContext.getDefaultStyleContext().getStyle(StyleContext.DEFAULT_STYLE)
        val paragraphStyle = doc.addStyle("paragraphStyle", defaultStyle)
        StyleConstants.setLineSpacing(paragraphStyle, 0f)
        StyleConstants.setSpaceBelow(paragraphStyle, 0f)
        doc.setParagraphAttributes(0, doc.length, paragraphStyle, true)


        liveScrollPane.verticalScrollBar.addAdjustmentListener { e ->
            if (e.valueIsAdjusting) return@addAdjustmentListener
            val scrollBar = liveScrollPane.verticalScrollBar
            autoScrollLive = (scrollBar.value + scrollBar.visibleAmount) == scrollBar.maximum
        }

        searchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = textChanged()
            override fun removeUpdate(e: DocumentEvent) = textChanged()
            override fun changedUpdate(e: DocumentEvent) = textChanged()

            fun textChanged() {
                searchCount = 0
                getFocusedTextPane()?.highlighter?.removeAllHighlights()
            }
        })
        searchField.addActionListener {
            focusWordInPane(getFocusedTextPane(), searchField.text)
        }
        findButton.addActionListener {
            focusWordInPane(getFocusedTextPane(), searchField.text)
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
            val textPane = getFocusedTextPane()
            val selectedText = textPane?.selectedText
            if (selectedText != null) {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(selectedText), null)
            } else {
                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(getFocusedText()), null)
            }
        }

        uploadButton.addActionListener {
            val selected = logFileBox.selectedItem as String
            val text = getFocusedText()
            if (text.isBlank()) return@addActionListener

            val result = JOptionPane.showConfirmDialog(this@LogViewerPanel, I18n.translate("message.upload_log_ask", selected), I18n.translate("text.warning"), JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE)
            if (result == JOptionPane.YES_OPTION) {
                val post = HttpPost("https://api.mclo.gs/1/log")
                post.entity = UrlEncodedFormEntity(listOf(BasicNameValuePair("content", text)), StandardCharsets.UTF_8)

                val submitDialog = LogSubmitGui(SwingUtilities.getWindowAncestor(uploadButton))
                object : LauncherWorker() {
                    override fun work(dialog: JDialog) {
                        val request = HttpUtils.makeJsonRequest(post, this)
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

    fun addLogs(logs: List<String>) {
        if (logs.isEmpty()) return

        val doc = liveLogPane.styledDocument
        val currentDocLength = doc.length
        val textToInsert = StringBuilder()
        val styleRanges = mutableListOf<Triple<Int, Int, AttributeSet>>() // Triple: startOffsetInBatch, length, style

        var currentOffsetInBatch = 0
        for (message in logs) {
            val strings = message.lines()
            for (string in strings) {
                val lineText = string
                val styleToApply = when {
                    string.contains("ERROR", true) -> ERROR_STYLE
                    string.contains("WARN", true) -> WARN_STYLE
                    string.contains("DEBUG", true) -> DEBUG_STYLE
                    else -> DEFAULT_STYLE
                }
                styleRanges.add(Triple(currentOffsetInBatch, lineText.length + 1, styleToApply)) // +1 for the newline
                textToInsert.append(lineText).append("\n") // Append newline explicitly
                currentOffsetInBatch += lineText.length + 1
            }
        }

        try {
            doc.insertString(currentDocLength, textToInsert.toString(), null) // Insert all text first
            for ((startOffsetInBatch, length, style) in styleRanges) {
                val startOffset = currentDocLength + startOffsetInBatch
                doc.setCharacterAttributes(startOffset, length, style, false)
            }
        } catch (e: BadLocationException) {
            MCSRLauncher.LOGGER.error("Failed to insert or style logs", e)
        }

        // Reset search state after document modification
        searchCount = 0
        liveLogPane.highlighter.removeAllHighlights()

        val root = doc.defaultRootElement
        val lineCount = root.elementCount
        if (lineCount > MAX_LOG_COUNT) {
            val excess = lineCount - MAX_LOG_COUNT
            val endOffset = root.getElement(excess - 1).endOffset
            try {
                doc.remove(0, endOffset)
            } catch (e: BadLocationException) {
                MCSRLauncher.LOGGER.error("Failed to trim logs", e)
            }
        }
    }

    fun clearLogs() {
        liveLogPane.text = ""
        searchCount = 0
        liveLogPane.highlighter.removeAllHighlights()
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
            clearLogs()
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

    fun appendStringToTextPane(textPane: JTextPane, message: String) {
        val doc: StyledDocument = textPane.styledDocument

        val strings = message.lines()
        for (string in strings) {
            if (string.isEmpty() && strings.size == 1) {
                doc.insertString(doc.length, "\n", DEFAULT_STYLE)
                continue
            }
            if (string.isBlank()) continue

            val styleToApply = when {
                string.contains("ERROR", true) -> ERROR_STYLE
                string.contains("WARN", true) -> WARN_STYLE
                string.contains("DEBUG", true) -> DEBUG_STYLE
                else -> DEFAULT_STYLE
            }
            doc.insertString(doc.length, string + (if (string.endsWith("\n")) "" else "\n"), styleToApply)
        }
    }

    private fun getFocusedText(): String {
        return getFocusedTextPane()?.text ?: ""
    }

    private fun getFocusedTextPane(): JTextPane? {
        return if (displayLiveLog) liveLogPane else fileLogPane
    }

    private fun focusWordInPane(pane: JTextPane?, word: String) {
        if (pane == null || word.isBlank()) return

        val content = pane.styledDocument.getText(0, pane.styledDocument.length)
        val indexes = mutableListOf<Int>()
        var searchIndex = content.indexOf(word, 0, true)
        while (searchIndex >= 0) {
            indexes.add(searchIndex)
            searchIndex = content.indexOf(word, searchIndex + word.length, true)
        }

        if (indexes.isEmpty()) {
            pane.highlighter.removeAllHighlights()
            return
        }

        if (indexes.size <= searchCount) {
            searchCount = 0
        }

        val targetIndex = indexes[searchCount]
        pane.caretPosition = targetIndex

        val highlighter = pane.highlighter
        highlighter.removeAllHighlights()

        val focusedPainter = DefaultHighlighter.DefaultHighlightPainter(Color.ORANGE)
        val otherPainter = DefaultHighlighter.DefaultHighlightPainter(Color.ORANGE.darker().darker())

        for (index in indexes) {
            highlighter.addHighlight(index, index + word.length, if (index == targetIndex) focusedPainter else otherPainter)
        }

        try {
            val view = pane.modelToView2D(targetIndex)
            if (view != null) {
                pane.scrollRectToVisible(view.bounds)
            }
        } catch (e: BadLocationException) {
            // ignore
        }
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
                        appendStringToTextPane(fileLogPane, it)
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
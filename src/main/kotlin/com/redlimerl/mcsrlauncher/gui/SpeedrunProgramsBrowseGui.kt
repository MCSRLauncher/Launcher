package com.redlimerl.mcsrlauncher.gui

import com.redlimerl.mcsrlauncher.data.meta.program.SpeedrunToolMeta
import com.redlimerl.mcsrlauncher.util.I18n
import com.redlimerl.mcsrlauncher.util.OSUtils
import com.redlimerl.mcsrlauncher.util.SwingUtils
import java.awt.*
import java.net.URI
import javax.swing.*


class SpeedrunProgramsBrowseGui(window: Window, title: String, tools: List<SpeedrunToolMeta>) : SpeedrunToolsBrowseDialog(window) {
    init {
        this.title = title
        minimumSize = Dimension(700, 500)
        setLocationRelativeTo(parent)

        SwingUtils.fasterScroll(this.toolsScrollPane)

        this.toolsListPanel.layout = BoxLayout(this.toolsListPanel, BoxLayout.Y_AXIS)
        for (tool in tools) {
            if (!tool.shouldApply()) continue
            this.toolsListPanel.add(ToolPanel(tool))
        }

        this.buttonCancel.addActionListener { this.dispose() }

        I18n.translateGui(this)
        isVisible = true
    }

    class ToolPanel(tool: SpeedrunToolMeta) : JPanel() {
        init {
            layout = BorderLayout(5, 5)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                BorderFactory.createEmptyBorder(5, 5, 10, 5)
            )

            val titleLabel = JLabel("<html><font size='+1'><b>${tool.name}</b></font> by ${tool.authors.joinToString(", ")}</html>")
            titleLabel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

            val descriptionArea = JTextArea(tool.description)
            descriptionArea.lineWrap = true
            descriptionArea.wrapStyleWord = true
            descriptionArea.isEditable = false
            descriptionArea.isOpaque = false

            val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
            val downloadButton = JButton("Download")
            downloadButton.addActionListener {
                OSUtils.openURI(URI.create(tool.downloadPage))
            }
            buttonPanel.add(downloadButton)
            if (tool.sources != null) {
                val sourceButton = JButton("Source")
                sourceButton.addActionListener {
                    OSUtils.openURI(URI.create(tool.sources))
                }
                buttonPanel.add(sourceButton)
            }

            add(titleLabel, BorderLayout.NORTH)
            add(descriptionArea, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
        }
    }
}
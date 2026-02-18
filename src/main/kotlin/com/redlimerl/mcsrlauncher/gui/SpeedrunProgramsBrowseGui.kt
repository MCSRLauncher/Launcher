package com.redlimerl.mcsrlauncher.gui

import com.redlimerl.mcsrlauncher.data.meta.program.SpeedrunProgramMeta
import com.redlimerl.mcsrlauncher.util.I18n
import com.redlimerl.mcsrlauncher.util.OSUtils
import com.redlimerl.mcsrlauncher.util.SwingUtils
import java.awt.*
import java.net.URI
import javax.swing.*


class SpeedrunProgramsBrowseGui(window: Window, title: String, programs: List<SpeedrunProgramMeta>) : SpeedrunToolsBrowseDialog(window) {
    init {
        this.title = title
        minimumSize = Dimension(700, 500)
        setLocationRelativeTo(parent)

        SwingUtils.fasterScroll(this.programsScrollPane)

        this.programsListPanel.layout = BoxLayout(this.programsListPanel, BoxLayout.Y_AXIS)
        for (program in programs) {
            if (!program.shouldApply()) continue
            this.programsListPanel.add(ProgramPanel(program))
        }

        this.buttonCancel.addActionListener { this.dispose() }

        I18n.translateGui(this)
        isVisible = true
    }

    class ProgramPanel(program: SpeedrunProgramMeta) : JPanel() {
        init {
            layout = BorderLayout(5, 5)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                BorderFactory.createEmptyBorder(5, 5, 10, 5)
            )

            val titleLabel = JLabel("<html><font size='+1'><b>${program.name}</b></font> by ${program.authors.joinToString(", ")}</html>")
            titleLabel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

            val descriptionArea = JTextArea(program.description)
            descriptionArea.lineWrap = true
            descriptionArea.wrapStyleWord = true
            descriptionArea.isEditable = false
            descriptionArea.isOpaque = false

            val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
            val downloadButton = JButton("Download")
            downloadButton.addActionListener {
                OSUtils.openURI(URI.create(program.downloadPage))
            }
            buttonPanel.add(downloadButton)
            if (program.sources != null) {
                val sourceButton = JButton("Source")
                sourceButton.addActionListener {
                    OSUtils.openURI(URI.create(program.sources))
                }
                buttonPanel.add(sourceButton)
            }

            add(titleLabel, BorderLayout.NORTH)
            add(descriptionArea, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
        }
    }
}
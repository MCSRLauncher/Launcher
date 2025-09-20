package com.redlimerl.mcsrlauncher.gui

import com.redlimerl.mcsrlauncher.data.instance.BasicInstance
import com.redlimerl.mcsrlauncher.data.meta.map.MinecraftMapMeta
import com.redlimerl.mcsrlauncher.util.I18n
import com.redlimerl.mcsrlauncher.util.LauncherWorker
import com.redlimerl.mcsrlauncher.util.OSUtils
import com.redlimerl.mcsrlauncher.util.SwingUtils
import java.awt.*
import java.net.URI
import javax.swing.*


class MapBrowserGui(window: Window, title: String, maps: List<MinecraftMapMeta>, instance: BasicInstance) : MapBrowserDialog(window) {
    init {
        this.title = title
        minimumSize = Dimension(700, 500)
        setLocationRelativeTo(parent)

        SwingUtils.fasterScroll(this.mapScrollPane)

        this.mapListPanel.layout = BoxLayout(this.mapListPanel, BoxLayout.Y_AXIS)
        for (map in maps.filter { it.canDownload(instance) }) {
            this.mapListPanel.add(MapPanel(this, map, instance))
        }

        this.cancelButton.addActionListener { this.dispose() }

        I18n.translateGui(this)
        isVisible = true
    }

    class MapPanel(parent: MapBrowserGui, map: MinecraftMapMeta, instance: BasicInstance) : JPanel() {
        init {
            layout = BorderLayout(5, 5)
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY),
                BorderFactory.createEmptyBorder(5, 5, 10, 5)
            )

            val titleLabel = JLabel("<html><font size='+1'><b>${map.name}</b></font> by ${map.authors.joinToString(", ")}</html>")
            titleLabel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

            val descriptionArea = JTextArea(map.description)
            descriptionArea.lineWrap = true
            descriptionArea.wrapStyleWord = true
            descriptionArea.isEditable = false
            descriptionArea.isOpaque = false

            val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT))
            val downloadButton = JButton("Download")
            downloadButton.addActionListener {
                object : LauncherWorker(parent, I18n.translate("text.download.assets")) {
                    override fun work(dialog: JDialog) {
                        map.install(instance, this)
                        dialog.dispose()
                        JOptionPane.showMessageDialog(parent, I18n.translate("message.download_map.success"))
                    }
                }.showDialog().start()
            }
            buttonPanel.add(downloadButton)
            if (map.sources != null) {
                val sourceButton = JButton("Source")
                sourceButton.addActionListener {
                    OSUtils.openURI(URI.create(map.sources))
                }
                buttonPanel.add(sourceButton)
            }

            add(titleLabel, BorderLayout.NORTH)
            add(descriptionArea, BorderLayout.CENTER)
            add(buttonPanel, BorderLayout.SOUTH)
        }
    }
}
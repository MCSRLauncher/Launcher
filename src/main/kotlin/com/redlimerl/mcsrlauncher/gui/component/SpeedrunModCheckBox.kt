package com.redlimerl.mcsrlauncher.gui.component

import com.redlimerl.mcsrlauncher.data.meta.mod.SpeedrunModMeta
import com.redlimerl.mcsrlauncher.data.meta.mod.SpeedrunModVersion
import java.awt.Component
import java.awt.Font
import javax.swing.*
import javax.swing.border.EmptyBorder

class SpeedrunModCheckBox(val modMeta: SpeedrunModMeta, modVersion: SpeedrunModVersion) : JPanel() {

    val checkBox: JCheckBox = JCheckBox("${modMeta.name} (v${modVersion.version})")

    init {
        checkBox.alignmentX = Component.LEFT_ALIGNMENT
        checkBox.font = checkBox.font.deriveFont(Font.BOLD)

        val descArea = JTextArea(modMeta.description).apply {
            isEditable = false
            isOpaque = false
            lineWrap = true
            wrapStyleWord = true
            alignmentX = Component.LEFT_ALIGNMENT
        }

        this.layout = BoxLayout(this, BoxLayout.Y_AXIS)
        this.alignmentX = Component.LEFT_ALIGNMENT
        this.border = EmptyBorder(4, 4, 4, 4)
        this.add(checkBox)
        this.add(Box.createHorizontalStrut(4))
        this.add(descArea)
    }

}
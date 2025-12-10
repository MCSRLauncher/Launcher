package com.redlimerl.mcsrlauncher.gui.component

import java.awt.Color
import java.awt.Component
import javax.swing.JTable
import javax.swing.table.DefaultTableCellRenderer

class LogTableCellRenderer : DefaultTableCellRenderer() {

    override fun getTableCellRendererComponent(
        table: JTable?,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int
    ): Component {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)

        val line = value as? String ?: ""

        foreground = when {
            line.contains("ERROR", true) -> Color(255, 85, 85)
            line.contains("WARN", true) -> Color(255, 170, 0)
            line.contains("DEBUG", true) -> Color(170, 170, 170)
            else -> table?.foreground ?: Color.WHITE
        }
        background = table?.background ?: Color.BLACK

        return this
    }
}
package com.redlimerl.mcsrlauncher.gui.component

import java.util.*
import javax.swing.table.AbstractTableModel

private const val MAX_LOG_COUNT = 10000

class LogTableModel : AbstractTableModel() {
    private val columnNames = arrayOf("Logs")
    private val logs = LinkedList<String>()

    fun addLogs(newLogs: List<String>) {
        if (newLogs.isEmpty()) return

        logs.addAll(newLogs)

        val excess = logs.size - MAX_LOG_COUNT
        if (excess > 0) {
            (0 until excess).forEach { _ ->
                logs.removeFirst()
            }
        }

        fireTableDataChanged()
    }

    fun clear() {
        val oldSize = logs.size
        if (oldSize > 0) {
            logs.clear()
            fireTableRowsDeleted(0, oldSize - 1)
        }
    }

    override fun getRowCount(): Int = logs.size
    override fun getColumnCount(): Int = columnNames.size
    override fun getColumnName(column: Int): String = columnNames[column]
    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any = logs[rowIndex]
}
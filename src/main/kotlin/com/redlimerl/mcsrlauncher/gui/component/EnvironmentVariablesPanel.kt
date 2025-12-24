package com.redlimerl.mcsrlauncher.gui.component

import com.redlimerl.mcsrlauncher.util.I18n
import java.awt.*
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.*
import javax.swing.table.DefaultTableModel

class EnvironmentVariablesPanel(
    private val onUpdate: () -> Unit
) : JPanel() {

    val enableCheckBox = JCheckBox(I18n.translate("text.enable_env_variables"))
    private val tableModel: DefaultTableModel
    private val table: JTable
    private val addButton = JButton(I18n.translate("text.add"))
    private val removeButton = JButton(I18n.translate("text.remove"))
    private val clearButton = JButton(I18n.translate("text.clear"))
    private var isUpdating = false

    init {
        layout = BorderLayout(0, 8)
        border = BorderFactory.createEmptyBorder(8, 0, 0, 0)
        
        val headerPanel = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0))
        headerPanel.add(enableCheckBox)
        add(headerPanel, BorderLayout.NORTH)
        
        tableModel = object : DefaultTableModel(
            arrayOf(I18n.translate("text.env_name"), I18n.translate("text.env_value")),
            0
        ) {
            override fun isCellEditable(row: Int, column: Int): Boolean = enableCheckBox.isSelected
        }

        table = JTable(tableModel).apply {
            setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
            tableHeader.reorderingAllowed = false
            putClientProperty("terminateEditOnFocusLost", true)
            rowHeight = 24
        }
        
        table.addKeyListener(object : KeyAdapter() {
            override fun keyPressed(e: KeyEvent) {
                if (e.keyCode == KeyEvent.VK_DELETE && enableCheckBox.isSelected) {
                    removeSelectedRows()
                }
            }
        })

        val scrollPane = JScrollPane(table).apply {
            preferredSize = Dimension(0, 72)
            minimumSize = Dimension(0, 48)
        }
        add(scrollPane, BorderLayout.CENTER)
        
        val buttonPanel = JPanel(FlowLayout(FlowLayout.LEFT, 4, 0))
        buttonPanel.add(addButton)
        buttonPanel.add(removeButton)
        buttonPanel.add(clearButton)
        add(buttonPanel, BorderLayout.SOUTH)
        
        addButton.addActionListener {
            if (table.isEditing) table.cellEditor.stopCellEditing()
            tableModel.addRow(arrayOf("NEW_VAR", "value"))
            val newRow = tableModel.rowCount - 1
            table.setRowSelectionInterval(newRow, newRow)
            table.editCellAt(newRow, 0)
            table.requestFocus()
            onUpdate()
        }

        removeButton.addActionListener {
            removeSelectedRows()
        }

        clearButton.addActionListener {
            if (table.isEditing) table.cellEditor.stopCellEditing()
            tableModel.rowCount = 0
            onUpdate()
        }
        
        enableCheckBox.addActionListener {
            updateEnabledState()
            onUpdate()
        }
        
        tableModel.addTableModelListener {
            if (!isUpdating) {
                isUpdating = true
                try {
                    onUpdate()
                } finally {
                    isUpdating = false
                }
            }
        }

        updateEnabledState()
    }

    private fun removeSelectedRows() {
        if (table.isEditing) table.cellEditor.stopCellEditing()
        val selectedRows = table.selectedRows.sortedDescending()
        for (row in selectedRows) {
            tableModel.removeRow(row)
        }
        onUpdate()
    }

    private fun updateEnabledState() {
        val enabled = enableCheckBox.isSelected
        table.isEnabled = enabled
        addButton.isEnabled = enabled
        removeButton.isEnabled = enabled
        clearButton.isEnabled = enabled
        table.setBackground(if (enabled) UIManager.getColor("Table.background") else UIManager.getColor("Panel.background"))
    }

    fun isEnvEnabled(): Boolean = enableCheckBox.isSelected

    fun setEnvEnabled(enabled: Boolean) {
        enableCheckBox.isSelected = enabled
        updateEnabledState()
    }

    fun getEnvironmentVariables(): MutableMap<String, String> {
        if (table.isEditing && !isUpdating) {
            isUpdating = true
            try {
                table.cellEditor.stopCellEditing()
            } finally {
                isUpdating = false
            }
        }
        val vars = mutableMapOf<String, String>()
        for (i in 0 until tableModel.rowCount) {
            val name = tableModel.getValueAt(i, 0)?.toString()?.trim() ?: ""
            val value = tableModel.getValueAt(i, 1)?.toString() ?: ""
            if (name.isNotEmpty()) {
                vars[name] = value
            }
        }
        return vars
    }

    fun setEnvironmentVariables(vars: Map<String, String>) {
        tableModel.rowCount = 0
        for ((name, value) in vars) {
            tableModel.addRow(arrayOf(name, value))
        }
    }

    fun setEditable(editable: Boolean) {
        enableCheckBox.isEnabled = editable
        if (editable) {
            updateEnabledState()
        } else {
            table.isEnabled = false
            addButton.isEnabled = false
            removeButton.isEnabled = false
            clearButton.isEnabled = false
        }
    }
}
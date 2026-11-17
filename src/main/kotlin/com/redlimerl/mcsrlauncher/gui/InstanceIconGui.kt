package com.redlimerl.mcsrlauncher.gui

import com.redlimerl.mcsrlauncher.launcher.IconManager
import com.redlimerl.mcsrlauncher.util.I18n
import java.awt.Component
import java.awt.Desktop
import java.awt.Dimension
import java.awt.Window
import java.awt.datatransfer.DataFlavor
import java.io.File
import javax.swing.*

class InstanceIconGui(parent: Window) : InstanceIconDialog(parent) {

    var updatedIcon = false
    var selectedIconKey = "default"

    init {
        title = I18n.translate("icon.select")
        minimumSize = Dimension(800, 500)
        setLocationRelativeTo(parent)

        initIconList()

        folderButton.addActionListener {
            Desktop.getDesktop().open(IconManager.path.toFile())
        }

        addButton.addActionListener {
            val fileChooser = JFileChooser().apply {
                dialogTitle = I18n.translate("icon.add")
                isMultiSelectionEnabled = true
                isAcceptAllFileFilterUsed = false
                fileFilter = object : javax.swing.filechooser.FileFilter() {
                    override fun accept(f: File): Boolean {
                        return f.isDirectory ||
                                listOf(".png", ".jpg", ".jpeg").any { f.name.lowercase().endsWith(it) }
                    }

                    override fun getDescription(): String {
                        return "Image Files (*.png, *.jpg, *.jpeg)"
                    }
                }
            }

            val result = fileChooser.showOpenDialog(this)

            if (result == JFileChooser.APPROVE_OPTION) {
                val selectedFiles = fileChooser.selectedFiles
                IconManager.addIcon(selectedFiles.toList())
                initIconList()
            }
        }

        removeButton.addActionListener {
            IconManager.removeIcon(iconList.selectedValuesList)
            initIconList()
        }

        refreshButton.addActionListener {
            initIconList()
        }

        applyButton.addActionListener {
            updatedIcon = true
            selectedIconKey = iconList.selectedValue
            this.dispose()
        }

        cancelButton.addActionListener {
            this.dispose()
        }

        I18n.translateGui(this)
        isVisible = true
    }

    private fun initIconList() {
        val iconMap = IconManager.getIconMap()
        val model = DefaultListModel<String>()

        iconMap.keys.forEach { model.addElement(it) }

        iconList.model = model

        iconList.cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean
            ): Component {
                val label = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as JLabel
                label.verticalTextPosition = SwingConstants.BOTTOM
                label.horizontalTextPosition = SwingConstants.CENTER
                label.horizontalAlignment = SwingConstants.CENTER
                label.icon = iconMap[value]
                label.preferredSize = Dimension(100, 100)
                return label
            }
        }

        iconList.addListSelectionListener {
            if (iconList.isSelectionEmpty) {
                removeButton.isEnabled = false
                applyButton.isEnabled = false
            } else {
                removeButton.isEnabled = true
                applyButton.isEnabled = true
            }
        }

        iconList.transferHandler = object : TransferHandler() {
            override fun canImport(support: TransferSupport?): Boolean {
                if (support != null) return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
                return false
            }

            override fun importData(support: TransferSupport?): Boolean {
                if (!canImport(support) || support == null) return false

                val data = support.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>
                IconManager.addIcon(data)
                initIconList()
                return true
            }
        }
    }
}
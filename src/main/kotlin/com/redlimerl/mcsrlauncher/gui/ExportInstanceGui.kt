package com.redlimerl.mcsrlauncher.gui

import com.redlimerl.mcsrlauncher.data.instance.BasicInstance
import com.redlimerl.mcsrlauncher.gui.component.CheckBoxTreeNode
import com.redlimerl.mcsrlauncher.util.I18n
import com.redlimerl.mcsrlauncher.util.LauncherWorker
import com.redlimerl.mcsrlauncher.util.MigrationUtils.exportInstance
import java.awt.Dimension
import java.awt.Window
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import javax.swing.JCheckBox
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.tree.DefaultTreeModel
import javax.swing.tree.TreeCellRenderer

class ExportInstanceGui(parent: Window, instance: BasicInstance, type: Int) : ExportInstanceDialog(parent) {

    init {
        title = I18n.translate("text.export.instance")
        minimumSize = Dimension(600, 460)
        setLocationRelativeTo(parent)

        val minecraftPath = instance.getGamePath().toAbsolutePath().toFile()

        val root = if (type == 1) {
            createNode(minecraftPath)
        } else {
            createNode(instance.getInstancePath().toAbsolutePath().toFile())
        }

        val includeCheckBox = JCheckBox()

        dotMinecraftTree.model = DefaultTreeModel(root)
        dotMinecraftTree.cellRenderer =
            TreeCellRenderer { tree, value, selected, expanded, leaf, row, hasFocus ->
                val node = value as CheckBoxTreeNode
                includeCheckBox.isSelected = node.selected
                includeCheckBox.text = node.userObject.toString()
                includeCheckBox
            }
        dotMinecraftTree.scrollsOnExpand = false
        dotMinecraftTree.toggleClickCount = 0

        dotMinecraftTree.addTreeSelectionListener { dotMinecraftTree.clearSelection() }
        dotMinecraftTree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                val clicked = dotMinecraftTree.getPathForLocation(e.x, e.y)
                if (clicked != null) {
                    val node = clicked.lastPathComponent as CheckBoxTreeNode
                    node.selected = !node.selected

                    if (clicked.path.size == 1) {
                        val enumeration = root.breadthFirstEnumeration()
                        while (enumeration.hasMoreElements()) {
                            val child = enumeration.nextElement() as CheckBoxTreeNode
                            child.selected = root.selected
                        }
                    }

                    if (node.parent != null) {
                        node.path.forEach {
                            if (!(it as CheckBoxTreeNode).selected && node.selected) it.selected = true
                        }
                    }

                    for (i in 0..<node.childCount) {
                        val child = node.getChildAt(i) as CheckBoxTreeNode
                        child.selected = node.selected
                    }
                }

                dotMinecraftTree.repaint()
            }
        })

        cancelButton.addActionListener {
            this.dispose()
        }

        exportButton.addActionListener {
            val fileChooser = JFileChooser().apply {
                dialogTitle = I18n.translate("text.export.instance")
                isAcceptAllFileFilterUsed = false
                selectedFile = File(instance.id + ".zip")
                fileFilter = object : javax.swing.filechooser.FileFilter() {
                    override fun accept(f: File): Boolean {
                        return f.isDirectory || f.name.lowercase().endsWith(".zip")
                    }

                    override fun getDescription(): String {
                        return "ZIP Files (*.zip)"
                    }
                }
            }

            val result = fileChooser.showSaveDialog(this)

            if (result == JFileChooser.APPROVE_OPTION) {
                val saveLocation = fileChooser.selectedFile

                if (saveLocation.exists()) {
                    val overwrite = JOptionPane.showConfirmDialog(this, I18n.translate("text.overwrite"), I18n.translate("text.warning"), JOptionPane.YES_NO_OPTION)
                    if (overwrite != JOptionPane.YES_OPTION) {
                        return@addActionListener
                    }
                }

                val selectedFiles = mutableListOf<File>()
                val enumeration = root.breadthFirstEnumeration()

                while (enumeration.hasMoreElements()) {
                    val child = enumeration.nextElement() as CheckBoxTreeNode
                    if (child.selected) {
                        val file = child.file
                        if (!file.isDirectory || (file.isDirectory) && file.listFiles()?.isEmpty() == false) selectedFiles.add(file)
                    }
                }

                object : LauncherWorker(this@ExportInstanceGui, I18n.translate("message.loading"), I18n.translate("message.exporting")) {
                    override fun work(dialog: JDialog) {
                        exportInstance(minecraftPath, saveLocation, selectedFiles, instance, type)
                        this@ExportInstanceGui.dispose()
                    }
                }.showDialog().start()
            }
        }

        I18n.translateGui(this)
        isVisible = true
    }

    private fun createNode(file: File): CheckBoxTreeNode {
        val node = CheckBoxTreeNode(file)

        if (file.isDirectory) {
            file.listFiles()
                ?.sortedWith(compareBy<File>( { !it.isDirectory }, { it.name.lowercase() }))
                ?.forEach { child ->
                    node.add(createNode(child))
                }
        }

        return node
    }
}

package com.redlimerl.mcsrlauncher.gui

import com.redlimerl.mcsrlauncher.data.instance.BasicInstance
import com.redlimerl.mcsrlauncher.launcher.AccountManager
import com.redlimerl.mcsrlauncher.util.I18n
import com.redlimerl.mcsrlauncher.util.ShortcutUtils
import java.awt.Dimension
import java.awt.Window
import java.io.File
import java.nio.file.Path
import javax.swing.DefaultComboBoxModel
import javax.swing.JFileChooser
import javax.swing.JOptionPane
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.filechooser.FileFilter
import kotlin.io.path.exists
import kotlin.io.path.extension

class InstanceShortcutGui(parent: Window, instance: BasicInstance) : InstanceShortcutDialog(parent) {

    init {
        title = I18n.translate("text.shortcut.create")
        minimumSize = Dimension(650, 250)
        setLocationRelativeTo(parent)


        val accounts = AccountManager.getAllAccounts()
        val accountModel = DefaultComboBoxModel<String>()
        accounts.toTypedArray().forEach { account ->
            accountModel.addElement(account.profile.nickname)
        }
        if (accounts.isEmpty()) accountCheckBox.isEnabled = false

        accountComboBox.model = accountModel

        accountCheckBox.addActionListener {
            accountComboBox.isEnabled = accountCheckBox.isSelected
        }

        shortcutTextField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updateButton()
            override fun removeUpdate(e: DocumentEvent?) = updateButton()
            override fun changedUpdate(e: DocumentEvent?) = updateButton()
            private fun updateButton() { createButton.isEnabled = shortcutTextField.text.isNotEmpty() }
        })

        browseButton.addActionListener {
            val fileExt = ShortcutUtils.getShortcutExtension()
            val fileChooser = JFileChooser().apply {
                dialogTitle = I18n.translate("text.export.instance")
                isAcceptAllFileFilterUsed = false
                selectedFile = File(instance.id + fileExt)
                fileFilter = object : FileFilter() {
                    override fun accept(f: File): Boolean {
                        return f.isDirectory || f.name.lowercase().endsWith(fileExt)
                    }

                    override fun getDescription(): String {
                        return "$fileExt Files (*$fileExt)"
                    }
                }
            }

            val result = fileChooser.showSaveDialog(this)

            if (result == JFileChooser.APPROVE_OPTION) {
                val saveLocation = fileChooser.selectedFile
                shortcutTextField.text = saveLocation.toString()
            }
        }

        cancelButton.addActionListener {
            this.dispose()
        }

        createButton.addActionListener {
            if (Path.of(shortcutTextField.text).exists()) {
                val overwrite = JOptionPane.showConfirmDialog(
                    this,
                    I18n.translate("text.overwrite"),
                    I18n.translate("text.warning"),
                    JOptionPane.YES_NO_OPTION
                )
                if (overwrite != JOptionPane.YES_OPTION) {
                    return@addActionListener
                }
            }

            val specificAccount = if (!accountCheckBox.isSelected) null else accountComboBox.selectedItem?.toString()
            val shortcutExt = ShortcutUtils.getShortcutExtension()
            val savePath = shortcutTextField.text

            if (".".plus(Path.of(savePath).extension) != shortcutExt) {
                JOptionPane.showMessageDialog(
                    null,
                    I18n.translate("text.shortcut.extension_mismatch", shortcutExt),
                    I18n.translate("text.error"),
                    JOptionPane.ERROR_MESSAGE
                )
            } else {
                ShortcutUtils.createShortcut(savePath, instance.id, specificAccount.toString())
                this.dispose()
            }
        }

        I18n.translateGui(this)
        isVisible = true
    }
}
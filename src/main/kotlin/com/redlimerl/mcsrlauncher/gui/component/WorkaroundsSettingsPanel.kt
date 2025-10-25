package com.redlimerl.mcsrlauncher.gui.component

import com.redlimerl.mcsrlauncher.data.launcher.LauncherOptions
import com.redlimerl.mcsrlauncher.data.instance.InstanceOptions
import com.redlimerl.mcsrlauncher.util.I18n
import javax.swing.*
import java.awt.BorderLayout
import java.awt.GridLayout

class WorkaroundsSettingsPanel(
    parent: JDialog,
    private val options: Any,
    private val onUpdate: () -> Unit
) : JPanel() {

    private val glfPathField = JTextField()
    private val wrapperCommandField = JTextField()
    private val useLauncherWorkarounds = JCheckBox(I18n.translate("text.use_launcher_workaround_settings"))

    init {
        layout = BorderLayout()
        val formPanel = JPanel(GridLayout(0, 2, 10, 10))
        formPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        formPanel.add(useLauncherWorkarounds)
        formPanel.add(Box.createGlue())

        val separator = JSeparator()
        formPanel.add(separator)
        formPanel.add(Box.createGlue())

        formPanel.add(JLabel(I18n.translate("text.glfw_path") + ":"))
        formPanel.add(glfPathField)

        formPanel.add(JLabel(I18n.translate("text.wrapper_command") + ":"))
        formPanel.add(wrapperCommandField)

        add(formPanel, BorderLayout.NORTH)

        when (options) {
            is LauncherOptions -> {
                glfPathField.text = options.customGLFWPath
                wrapperCommandField.text = options.wrapperCommand
                useLauncherWorkarounds.isVisible = false
            }
            is InstanceOptions -> {
                glfPathField.text = options.customGLFWPath
                wrapperCommandField.text = options.wrapperCommand
                useLauncherWorkarounds.isSelected = options.useLauncherWorkarounds
                applyLauncherSettings(options.useLauncherWorkarounds)
            }
        }

        fun saveFields() {
            when (options) {
                is LauncherOptions -> {
                    options.customGLFWPath = glfPathField.text.trim()
                    options.wrapperCommand = wrapperCommandField.text.trim()
                }
                is InstanceOptions -> {
                    options.customGLFWPath = glfPathField.text.trim()
                    options.wrapperCommand = wrapperCommandField.text.trim()
                    options.useLauncherWorkarounds = useLauncherWorkarounds.isSelected
                }
            }
            onUpdate()
        }

        glfPathField.addActionListener { saveFields() }
        glfPathField.addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusLost(e: java.awt.event.FocusEvent?) = saveFields()
        })
        wrapperCommandField.addActionListener { saveFields() }
        wrapperCommandField.addFocusListener(object : java.awt.event.FocusAdapter() {
            override fun focusLost(e: java.awt.event.FocusEvent?) = saveFields()
        })
        useLauncherWorkarounds.addActionListener {
            if (options is InstanceOptions) {
                options.useLauncherWorkarounds = useLauncherWorkarounds.isSelected
                applyLauncherSettings(options.useLauncherWorkarounds)
                onUpdate()
            }
        }
    }

    private fun applyLauncherSettings(useLauncher: Boolean) {
        if (options !is InstanceOptions) return

        if (useLauncher) {
            val launcher = LauncherOptions.load()
            glfPathField.text = launcher.customGLFWPath
            wrapperCommandField.text = launcher.wrapperCommand
        }

        glfPathField.isEnabled = !useLauncher
        wrapperCommandField.isEnabled = !useLauncher
    }
}

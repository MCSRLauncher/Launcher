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
    private val useGlobalCheckBox = JCheckBox(I18n.translate("text.use_global_workarounds"))

    init {
        layout = BorderLayout()
        val formPanel = JPanel(GridLayout(0, 2, 10, 10))
        formPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        formPanel.add(JLabel(I18n.translate("text.glfw_path") + ":"))
        formPanel.add(glfPathField)

        formPanel.add(JLabel(I18n.translate("text.wrapper_command") + ":"))
        formPanel.add(wrapperCommandField)

        formPanel.add(useGlobalCheckBox)
        formPanel.add(Box.createGlue())

        add(formPanel, BorderLayout.NORTH)

        when (options) {
            is LauncherOptions -> {
                glfPathField.text = options.customGLFWPath
                wrapperCommandField.text = options.wrapperCommand
                useGlobalCheckBox.isVisible = false
            }
            is InstanceOptions -> {
                glfPathField.text = options.customGLFWPath
                wrapperCommandField.text = options.wrapperCommand
                useGlobalCheckBox.isSelected = options.useLauncherWorkarounds
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
                    options.useLauncherWorkarounds = useGlobalCheckBox.isSelected
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
        useGlobalCheckBox.addActionListener {
            if (options is InstanceOptions) {
                options.useLauncherWorkarounds = useGlobalCheckBox.isSelected
                onUpdate()
            }
        }
    }
}

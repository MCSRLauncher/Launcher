package com.redlimerl.mcsrlauncher.gui.component

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.data.device.DeviceOSType
import com.redlimerl.mcsrlauncher.data.instance.BasicInstance
import com.redlimerl.mcsrlauncher.data.launcher.LauncherSharedOptions
import com.redlimerl.mcsrlauncher.util.I18n
import com.redlimerl.mcsrlauncher.util.NixGLFWDetector
import java.awt.*
import javax.swing.*

class WorkaroundSettingsPanel(
    parent: JDialog,
    private val instance: BasicInstance?,
    private val onUpdate: () -> Unit
) : JPanel(), Scrollable {

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
    override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = 16
    override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int): Int = 64
    override fun getScrollableTracksViewportWidth(): Boolean = true
    override fun getScrollableTracksViewportHeight(): Boolean = false

    val glfPathField: JTextField
    val wrapperCommandField = JTextField()
    val preLaunchField = JTextField()
    val postExitField = JTextField()

    val feralBox = JCheckBox("${I18n.translate("text.enable")} Feral GameMode")
    val mangoBox = JCheckBox("${I18n.translate("text.enable")} MangoHUD")
    val discreteBox = JCheckBox(I18n.translate("text.use_discrete_gpu"))
    val zinkBox = JCheckBox(I18n.translate("text.use_zink"))
    val useSystemGLFWBox = JCheckBox(I18n.translate("text.use_system_glfw"))
    val nvidiaGlThreadedBox = JCheckBox(I18n.translate("text.disable_gl_threaded_opt"))

    val feralInstalled: Boolean
    val mangoInstalled: Boolean

    val envVarsPanel: EnvironmentVariablesPanel

    init {
        val nixGlfwPath = NixGLFWDetector.cachedPath

        glfPathField = object : JTextField() {
            override fun paintComponent(g: Graphics) {
                super.paintComponent(g)
                if (text.isNullOrEmpty() && !hasFocus() && useSystemGLFWBox.isSelected && nixGlfwPath != null) {
                    val g2 = g as Graphics2D
                    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
                    g2.color = Color(150, 150, 150)
                    g2.font = font.deriveFont(Font.ITALIC)
                    val fm = g2.fontMetrics
                    g2.drawString(nixGlfwPath, insets.left, insets.top + fm.ascent)
                }
            }
        }

        layout = BorderLayout()
        val formPanel = JPanel(GridBagLayout())
        formPanel.border = BorderFactory.createEmptyBorder(10, 10, 10, 10)

        val c = GridBagConstraints().apply {
            fill = GridBagConstraints.HORIZONTAL
            anchor = GridBagConstraints.WEST
            insets = Insets(4, 8, 4, 8)
            weightx = 1.0
        }

        var row = 0
        fun addRow(left: JComponent, right: JComponent) {
            c.gridy = row; c.gridx = 0; c.weightx = 0.0; c.gridwidth = 1
            formPanel.add(left, c)
            c.gridx = 1; c.weightx = 1.0
            formPanel.add(right, c)
            row++
        }

        fun underlineLabel(text: String, tooltip: String): JLabel {
            val label = JLabel("<html><u>$text</u></html>")
            label.toolTipText = tooltip
            label.foreground = java.awt.Color(180, 180, 180)
            return label
        }

        addRow(JLabel(I18n.translate("text.glfw_path") + ":"), glfPathField)

        fun underlineWord(fullText: String, word: String, tooltip: String): JLabel {
            val underlined = fullText.replaceFirst(word, "<u>$word</u>")
            return JLabel("<html>$underlined</html>").apply {
                toolTipText = tooltip
                foreground = java.awt.Color(200, 200, 200)
            }
        }

        addRow(
            underlineWord(
                I18n.translate("text.wrapper_command") + ":",
                "Wrapper",
                I18n.translate("tooltip.wrapper_command")
            ),
            wrapperCommandField
        )

        addRow(
            underlineWord(
                I18n.translate("text.pre_launch_command") + ":",
                "Pre-launch",
                I18n.translate("tooltip.commands")
            ),
            preLaunchField
        )

        addRow(
            underlineWord(
                I18n.translate("text.post_exit_command") + ":",
                "Post-exit",
                I18n.translate("tooltip.commands")
            ),
            postExitField
        )

        formPanel.add(JSeparator(), GridBagConstraints().apply {
            gridx = 0; gridy = row++; gridwidth = 2; fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0; insets = Insets(8, 0, 8, 0)
        })

        val checkboxes = mutableListOf(feralBox, mangoBox, discreteBox, zinkBox)
        if (NixGLFWDetector.isNixOS) checkboxes.add(useSystemGLFWBox)

        val isLinux = !DeviceOSType.WINDOWS.isOn() && !DeviceOSType.MACOS.isOn()
        val hasNvidia = isLinux && java.nio.file.Files.exists(java.nio.file.Path.of("/proc/driver/nvidia/version"))
        if (hasNvidia) checkboxes.add(nvidiaGlThreadedBox)

        checkboxes.forEach { box ->
            c.gridy = row++
            c.gridx = 0
            c.gridwidth = 2
            c.weightx = 1.0
            formPanel.add(box, c)
        }

        formPanel.add(JSeparator(), GridBagConstraints().apply {
            gridx = 0; gridy = row++; gridwidth = 2; fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0; insets = Insets(12, 0, 4, 0)
        })

        fun commandExists(cmd: String): Boolean {
            if (DeviceOSType.WINDOWS.isOn()) {
                return try {
                    val process = ProcessBuilder("where", cmd).redirectErrorStream(true).start()
                    process.waitFor()
                    process.exitValue() == 0
                } catch (_: Exception) {
                    false
                }
            } else {
                return try {
                    val process = ProcessBuilder("which", cmd).redirectErrorStream(true).start()
                    process.waitFor()
                    process.exitValue() == 0
                } catch (_: Exception) {
                    false
                }
            }
        }

        feralInstalled = commandExists("gamemoded")
        mangoInstalled = commandExists("mangohud")

        fun detectZink(): Boolean {
            val override = System.getenv("MESA_LOADER_DRIVER_OVERRIDE")
            if (override?.split(':')?.any { it.equals("zink", true) } == true) return true

            val candidates = arrayOf(
                "/usr/lib/dri/zink_dri.so",
                "/usr/lib64/dri/zink_dri.so",
                "/usr/lib/x86_64-linux-gnu/dri/zink_dri.so",
                "/usr/lib/aarch64-linux-gnu/dri/zink_dri.so",
                "/usr/lib/arm-linux-gnueabihf/dri/zink_dri.so"
            )
            if (candidates.any { java.nio.file.Files.exists(java.nio.file.Path.of(it)) }) return true

            return commandExists("vulkaninfo")
        }

        val zinkAvailable = detectZink()

        val tooltipNotFound = I18n.translate("tooltip.not_found_on_system")
        val tooltipNotAvailable = I18n.translate("tooltip.not_available_on_system")

        if (!feralInstalled) {
            feralBox.isEnabled = false
            feralBox.toolTipText = "Feral Interactive's GameMode $tooltipNotFound"
        }
        if (!mangoInstalled) {
            mangoBox.isEnabled = false
            mangoBox.toolTipText = "MangoHUD $tooltipNotFound"
        }
        if (!zinkAvailable) {
            zinkBox.isEnabled = false
            zinkBox.toolTipText = "Zink $tooltipNotAvailable"
        }
        if (!NixGLFWDetector.isNixOS) {
            useSystemGLFWBox.isEnabled = false
            useSystemGLFWBox.toolTipText = I18n.translate("tooltip.nixos_only")
        }

        fun saveFields() {
            if (instance == null) saveLauncherOptions()
            else saveInstanceOptions(instance)
            onUpdate()
        }

        val envLabel = JLabel(I18n.translate("text.environment_variables"))
        envLabel.font = envLabel.font.deriveFont(java.awt.Font.BOLD)
        formPanel.add(envLabel, GridBagConstraints().apply {
            gridx = 0; gridy = row++; gridwidth = 2; fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0; insets = Insets(4, 8, 4, 8); anchor = GridBagConstraints.WEST
        })

        envVarsPanel = EnvironmentVariablesPanel { saveFields() }
        formPanel.add(envVarsPanel, GridBagConstraints().apply {
            gridx = 0; gridy = row++; gridwidth = 2; fill = GridBagConstraints.BOTH
            weightx = 1.0; weighty = 1.0; insets = Insets(0, 8, 8, 8)
        })

        add(formPanel, BorderLayout.CENTER)

        if (instance == null) loadOptions(MCSRLauncher.options, false)
        else loadOptions(instance.options, instance.options.useLauncherWorkarounds)

        listOf(glfPathField, wrapperCommandField, preLaunchField, postExitField).forEach { field ->
            field.addActionListener { saveFields() }
            field.addFocusListener(object : java.awt.event.FocusAdapter() {
                override fun focusLost(e: java.awt.event.FocusEvent?) = saveFields()
            })
        }
        listOf(feralBox, mangoBox, discreteBox, zinkBox, useSystemGLFWBox, nvidiaGlThreadedBox).forEach { box ->
            box.addActionListener { saveFields() }
        }

        useSystemGLFWBox.addActionListener { glfPathField.repaint() }
    }

    private fun loadOptions(opt: LauncherSharedOptions, useLauncherOption: Boolean) {
        glfPathField.text = opt.customGLFWPath
        wrapperCommandField.text = opt.wrapperCommand
        preLaunchField.text = opt.preLaunchCommand
        postExitField.text = opt.postExitCommand

        feralBox.isSelected = opt.enableFeralGamemode
        mangoBox.isSelected = opt.enableMangoHud
        discreteBox.isSelected = opt.useDiscreteGpu
        zinkBox.isSelected = opt.useZink
        useSystemGLFWBox.isSelected = opt.useSystemGLFW
        nvidiaGlThreadedBox.isSelected = opt.disableGlThreadedOpt

        envVarsPanel.setEnvEnabled(opt.enableEnvironmentVariables)
        envVarsPanel.setEnvironmentVariables(opt.environmentVariables)

        applyLauncherSettings(useLauncherOption)
    }

    private fun saveLauncherOptions() {
        val opt = MCSRLauncher.options
        opt.customGLFWPath = glfPathField.text.trim()
        opt.wrapperCommand = wrapperCommandField.text.trim()
        opt.preLaunchCommand = preLaunchField.text.trim()
        opt.postExitCommand = postExitField.text.trim()
        opt.enableFeralGamemode = feralBox.isSelected
        opt.enableMangoHud = mangoBox.isSelected
        opt.useDiscreteGpu = discreteBox.isSelected
        opt.useZink = zinkBox.isSelected
        opt.useSystemGLFW = useSystemGLFWBox.isSelected
        opt.disableGlThreadedOpt = nvidiaGlThreadedBox.isSelected
        opt.enableEnvironmentVariables = envVarsPanel.isEnvEnabled()
        opt.environmentVariables = envVarsPanel.getEnvironmentVariables()
        opt.save()
    }

    private fun saveInstanceOptions(instance: BasicInstance) {
        instance.options.customGLFWPath = glfPathField.text.trim()
        instance.options.wrapperCommand = wrapperCommandField.text.trim()
        instance.options.preLaunchCommand = preLaunchField.text.trim()
        instance.options.postExitCommand = postExitField.text.trim()
        instance.options.enableFeralGamemode = feralBox.isSelected
        instance.options.enableMangoHud = mangoBox.isSelected
        instance.options.useDiscreteGpu = discreteBox.isSelected
        instance.options.useZink = zinkBox.isSelected
        instance.options.useSystemGLFW = useSystemGLFWBox.isSelected
        instance.options.disableGlThreadedOpt = nvidiaGlThreadedBox.isSelected
        instance.options.enableEnvironmentVariables = envVarsPanel.isEnvEnabled()
        instance.options.environmentVariables = envVarsPanel.getEnvironmentVariables()
        instance.save()
    }


    fun applyLauncherSettings(useLauncher: Boolean) {
        if (useLauncher) {
            glfPathField.text = MCSRLauncher.options.customGLFWPath
            wrapperCommandField.text = MCSRLauncher.options.wrapperCommand
            preLaunchField.text = MCSRLauncher.options.preLaunchCommand
            postExitField.text = MCSRLauncher.options.postExitCommand
            feralBox.isSelected = MCSRLauncher.options.enableFeralGamemode
            mangoBox.isSelected = MCSRLauncher.options.enableMangoHud
            discreteBox.isSelected = MCSRLauncher.options.useDiscreteGpu
            zinkBox.isSelected = MCSRLauncher.options.useZink
            useSystemGLFWBox.isSelected = MCSRLauncher.options.useSystemGLFW
            nvidiaGlThreadedBox.isSelected = MCSRLauncher.options.disableGlThreadedOpt
            envVarsPanel.setEnvEnabled(MCSRLauncher.options.enableEnvironmentVariables)
            envVarsPanel.setEnvironmentVariables(MCSRLauncher.options.environmentVariables)
        }

        val editable = !useLauncher
        listOf(glfPathField, wrapperCommandField, preLaunchField, postExitField).forEach { it.isEnabled = editable }

        feralBox.isEnabled = editable && feralInstalled
        mangoBox.isEnabled = editable && mangoInstalled
        discreteBox.isEnabled = editable
        zinkBox.isEnabled = editable
        useSystemGLFWBox.isEnabled = editable && NixGLFWDetector.isNixOS
        nvidiaGlThreadedBox.isEnabled = editable
        envVarsPanel.setEditable(editable)

        revalidate()
        repaint()
    }
}

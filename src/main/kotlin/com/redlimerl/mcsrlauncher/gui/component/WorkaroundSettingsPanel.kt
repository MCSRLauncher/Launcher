package com.redlimerl.mcsrlauncher.gui.component

import com.redlimerl.mcsrlauncher.data.instance.BasicInstance
import com.redlimerl.mcsrlauncher.data.launcher.LauncherOptions
import com.redlimerl.mcsrlauncher.data.instance.InstanceOptions
import com.redlimerl.mcsrlauncher.util.I18n
import javax.swing.*
import java.awt.BorderLayout
import java.awt.GridBagLayout
import java.awt.GridBagConstraints
import java.awt.Insets

class WorkaroundSettingsPanel(
    parent: JDialog,
    private val instance: BasicInstance?,
    private val instanceOptions: InstanceOptions,
    private val options: Any,
    private val onUpdate: () -> Unit
) : JPanel() {

    val glfPathField = JTextField()
    val wrapperCommandField = JTextField()
    val preLaunchField = JTextField()
    val postExitField = JTextField()

    val feralBox = JCheckBox("${I18n.translate("text.enable")} Feral GameMode")
    val mangoBox = JCheckBox("${I18n.translate("text.enable")} MangoHUD")
    val discreteBox = JCheckBox(I18n.translate("text.use_discrete_gpu"))
    val zinkBox = JCheckBox(I18n.translate("text.use_zink"))

    val feralInstalled: Boolean
    val mangoInstalled: Boolean

    val envVarsPanel: EnvironmentVariablesPanel

    init {
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

        listOf(feralBox, mangoBox, discreteBox, zinkBox).forEach { box ->
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
            return try {
                val process = ProcessBuilder("which", cmd).redirectErrorStream(true).start()
                process.waitFor()
                process.exitValue() == 0
            } catch (_: Exception) {
                false
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

            return try { ProcessBuilder("vulkaninfo").redirectErrorStream(true).start().waitFor() == 0 } catch (_: Exception) { false }
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

        fun saveFields() {
            when (options) {
                is LauncherOptions -> saveLauncherOptions(options)
                is InstanceOptions -> saveInstanceOptions(options)
            }
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

        add(formPanel, BorderLayout.NORTH)

        when (options) {
            is LauncherOptions -> loadLauncherOptions(options)
            is InstanceOptions -> loadInstanceOptions(options)
        }

        listOf(glfPathField, wrapperCommandField, preLaunchField, postExitField).forEach { field ->
            field.addActionListener { saveFields() }
            field.addFocusListener(object : java.awt.event.FocusAdapter() {
                override fun focusLost(e: java.awt.event.FocusEvent?) = saveFields()
            })
        }
        listOf(feralBox, mangoBox, discreteBox, zinkBox).forEach { box ->
            box.addActionListener { saveFields() }
        }
    }

    private fun loadLauncherOptions(opt: LauncherOptions) {
        glfPathField.text = opt.customGLFWPath
        wrapperCommandField.text = opt.wrapperCommand
        preLaunchField.text = opt.preLaunchCommand
        postExitField.text = opt.postExitCommand

        feralBox.isSelected = opt.enableFeralGamemode
        mangoBox.isSelected = opt.enableMangoHud
        discreteBox.isSelected = opt.useDiscreteGpu
        zinkBox.isSelected = opt.useZink

        envVarsPanel.setEnvEnabled(opt.enableEnvironmentVariables)
        envVarsPanel.setEnvironmentVariables(opt.environmentVariables)
    }

    private fun loadInstanceOptions(opt: InstanceOptions) {
        glfPathField.text = opt.customGLFWPath
        wrapperCommandField.text = opt.wrapperCommand
        preLaunchField.text = opt.preLaunchCommand
        postExitField.text = opt.postExitCommand

        feralBox.isSelected = opt.enableFeralGamemode
        mangoBox.isSelected = opt.enableMangoHud
        discreteBox.isSelected = opt.useDiscreteGpu
        zinkBox.isSelected = opt.useZink

        envVarsPanel.setEnvEnabled(opt.enableEnvironmentVariables)
        envVarsPanel.setEnvironmentVariables(opt.environmentVariables)

        applyLauncherSettings(opt.useLauncherWorkarounds)
    }

    private fun saveLauncherOptions(opt: LauncherOptions) {
        opt.customGLFWPath = glfPathField.text.trim()
        opt.wrapperCommand = wrapperCommandField.text.trim()
        opt.preLaunchCommand = preLaunchField.text.trim()
        opt.postExitCommand = postExitField.text.trim()
        opt.enableFeralGamemode = feralBox.isSelected
        opt.enableMangoHud = mangoBox.isSelected
        opt.useDiscreteGpu = discreteBox.isSelected
        opt.useZink = zinkBox.isSelected
        opt.enableEnvironmentVariables = envVarsPanel.isEnvEnabled()
        opt.environmentVariables = envVarsPanel.getEnvironmentVariables()
        opt.save()
    }

    private fun saveInstanceOptions(opt: InstanceOptions) {
        opt.customGLFWPath = glfPathField.text.trim()
        opt.wrapperCommand = wrapperCommandField.text.trim()
        opt.preLaunchCommand = preLaunchField.text.trim()
        opt.postExitCommand = postExitField.text.trim()
        opt.enableFeralGamemode = feralBox.isSelected
        opt.enableMangoHud = mangoBox.isSelected
        opt.useDiscreteGpu = discreteBox.isSelected
        opt.useZink = zinkBox.isSelected
        opt.enableEnvironmentVariables = envVarsPanel.isEnvEnabled()
        opt.environmentVariables = envVarsPanel.getEnvironmentVariables()

        try {
            val instanceId = (try { instance?.id?.takeIf { it.isNotBlank() } } catch (_: Exception) { null }) ?: "unknown_instance"
            val instanceDir = com.redlimerl.mcsrlauncher.launcher.InstanceManager.INSTANCES_PATH.resolve(instanceId)
            val configPath = instanceDir.resolve("instance.json")

            instanceDir.toFile().mkdirs()
            val jsonText = com.redlimerl.mcsrlauncher.MCSRLauncher.JSON.encodeToString(opt)
            configPath.toFile().writeText(jsonText)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }


    fun applyLauncherSettings(useLauncher: Boolean) {
        if (options !is InstanceOptions) return
        val launcher = LauncherOptions.load()

        if (useLauncher) {
            glfPathField.text = launcher.customGLFWPath
            wrapperCommandField.text = launcher.wrapperCommand
            preLaunchField.text = launcher.preLaunchCommand
            postExitField.text = launcher.postExitCommand
            feralBox.isSelected = launcher.enableFeralGamemode
            mangoBox.isSelected = launcher.enableMangoHud
            discreteBox.isSelected = launcher.useDiscreteGpu
            zinkBox.isSelected = launcher.useZink
            envVarsPanel.setEnvEnabled(launcher.enableEnvironmentVariables)
            envVarsPanel.setEnvironmentVariables(launcher.environmentVariables)
        }

        val editable = !useLauncher
        listOf(glfPathField, wrapperCommandField, preLaunchField, postExitField).forEach { it.isEnabled = editable }

        feralBox.isEnabled = editable && feralInstalled
        mangoBox.isEnabled = editable && mangoInstalled
        discreteBox.isEnabled = editable
        zinkBox.isEnabled = editable
        envVarsPanel.setEditable(editable)

        revalidate()
        repaint()
    }
}

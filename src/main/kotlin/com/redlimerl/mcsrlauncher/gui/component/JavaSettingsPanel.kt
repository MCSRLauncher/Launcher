package com.redlimerl.mcsrlauncher.gui.component

import com.redlimerl.mcsrlauncher.data.launcher.LauncherSharedOptions
import com.redlimerl.mcsrlauncher.gui.JavaManagerGui
import com.redlimerl.mcsrlauncher.gui.components.AbstractJavaSettingsPanel
import com.redlimerl.mcsrlauncher.util.OSUtils
import java.awt.BorderLayout
import java.awt.event.FocusEvent
import java.awt.event.FocusListener
import javax.swing.JDialog
import javax.swing.SpinnerNumberModel
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class JavaSettingsPanel(parent: JDialog, val options: LauncherSharedOptions, private val onUpdate: () -> Unit) : AbstractJavaSettingsPanel() {

    init {
        layout = BorderLayout()
        add(this.rootPanel, BorderLayout.CENTER)

        fun saveJavaPath() {
            options.javaPath = this.javaPathField.text
            onUpdate()
        }
        this.javaPathField.addActionListener { saveJavaPath() }
        this.javaPathField.addFocusListener(object : FocusListener {
            override fun focusGained(e: FocusEvent?) {}
            override fun focusLost(e: FocusEvent?) {
                saveJavaPath()
            }
        })

        fun refreshJavaPath() {
            this.javaPathField.text = options.javaPath
        }

        refreshJavaPath()

        this.javaChangeButton.addActionListener {
            JavaManagerGui(parent, options.javaPath) {
                options.javaPath = it
                onUpdate()
                refreshJavaPath()
            }
        }

        val recommendedMax = (OSUtils.getTotalMemoryGB() * 0.75 * 1024).roundToInt()
        this.javaMinMemorySpinner.model = SpinnerNumberModel(options.minMemory, 512, recommendedMax, 512).also {
            this.javaMinMemorySlider.minimum = it.minimum as Int
            this.javaMinMemorySlider.maximum = it.maximum as Int
        }
        this.javaMinMemorySlider.value = options.minMemory
        this.javaMaxMemorySpinner.model = SpinnerNumberModel(options.maxMemory, 512, recommendedMax, 512).also {
            this.javaMaxMemorySlider.minimum = it.minimum as Int
            this.javaMaxMemorySlider.maximum = it.maximum as Int
        }
        this.javaMaxMemorySlider.value = options.maxMemory
        this.javaMinMemorySlider.addChangeListener {
            this.javaMinMemorySlider.value = min(this.javaMinMemorySlider.value, options.maxMemory)
            options.minMemory = this.javaMinMemorySlider.value
            this.javaMinMemorySpinner.value = options.minMemory
            onUpdate()
        }
        this.javaMinMemorySpinner.addChangeListener {
            this.javaMinMemorySpinner.value = min(this.javaMinMemorySpinner.value as Int, options.maxMemory)
            options.minMemory = this.javaMinMemorySpinner.value as Int
            this.javaMinMemorySlider.value = options.minMemory
            onUpdate()
        }
        this.javaMaxMemorySlider.addChangeListener {
            this.javaMaxMemorySlider.value = max(this.javaMaxMemorySlider.value, options.minMemory)
            options.maxMemory = this.javaMaxMemorySlider.value
            this.javaMaxMemorySpinner.value = options.maxMemory
            onUpdate()
        }
        this.javaMaxMemorySpinner.addChangeListener {
            this.javaMaxMemorySpinner.value = max(this.javaMaxMemorySpinner.value as Int, options.minMemory)
            options.maxMemory = this.javaMaxMemorySpinner.value as Int
            this.javaMaxMemorySlider.value = options.maxMemory
            onUpdate()
        }


        this.jvmArgumentArea.text = options.jvmArguments
        this.jvmArgumentArea.lineWrap = true
        this.jvmArgumentArea.wrapStyleWord = true
        this.jvmArgumentArea.addFocusListener(object : FocusListener {
            override fun focusGained(e: FocusEvent?) {}
            override fun focusLost(e: FocusEvent?) {
                options.jvmArguments = jvmArgumentArea.text
                onUpdate()
            }
        })
    }
}
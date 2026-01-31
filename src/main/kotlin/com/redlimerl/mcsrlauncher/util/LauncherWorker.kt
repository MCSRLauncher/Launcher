package com.redlimerl.mcsrlauncher.util

import com.redlimerl.mcsrlauncher.MCSRLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*

abstract class LauncherWorker(
    private val parent: Component? = null,
    title: String = "",
    description: String = ""
) {

    companion object {
        fun empty(): LauncherWorker {
            return object : LauncherWorker() {
                override fun work(dialog: JDialog) {}
            }
        }
    }

    private var shouldShowDialog = false
    private var started = false
    private var cancelled = false
    private var currentJob: Job? = null
    val properties: HashMap<String, String> = hashMapOf()

    private val progressBar = JProgressBar(JProgressBar.HORIZONTAL, 0, 100)
    private val label = JLabel("", SwingConstants.CENTER).apply {
        font = font.let { it.deriveFont(it.size + 2f) }
    }
    private val subLabel = JLabel("", SwingConstants.CENTER).apply { isVisible = false }
    private val southPanel = JPanel(BorderLayout())

    private fun applyState(string: String?) {
        label.text = string ?: ""
        refreshDialogWidth()
    }

    private fun applyProgress(value: Double?) {
        if (value == null) {
            progressBar.isVisible = false
        } else {
            progressBar.isVisible = true
            progressBar.value = (value * 100).toInt()
        }
    }

    val dialog = (if (parent is JFrame?) JDialog(parent, title) else if (parent is JDialog) JDialog(parent, title) else throw IllegalArgumentException("parent should be JFrame, JDialog or null")).apply {
        layout = BorderLayout()
        setSize(300, 150)
        setLocationRelativeTo(parent)

        MCSRLauncher.javaClass.classLoader?.getResource("icon.png")?.let {
            setIconImage(ImageIcon(it).image)
        }

        add(label, BorderLayout.CENTER)
        southPanel.add(subLabel, BorderLayout.NORTH)
        applyProgress(null)
        southPanel.add(progressBar, BorderLayout.CENTER)
        add(southPanel, BorderLayout.SOUTH)

        defaultCloseOperation = JDialog.DO_NOTHING_ON_CLOSE
        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent?) {}
        })

        rootPane.registerKeyboardAction({}, KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW)
        isResizable = false
    }

    init {
        applyState(description)
    }


    protected abstract fun work(dialog: JDialog)

    open fun onError(e: Throwable) {
        val arrayOption = arrayOf(I18n.translate("text.copy.stacktrace_close"), I18n.translate("text.close"))
        val result = JOptionPane.showOptionDialog(parent, e.message, "Error", JOptionPane.DEFAULT_OPTION, JOptionPane.ERROR_MESSAGE, null, arrayOption, arrayOption[1])
        if (result == 0) {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(e.stackTraceToString()), null)

            JOptionPane.showMessageDialog(parent, "Stacktrace copied to clipboard.", "Copied", JOptionPane.INFORMATION_MESSAGE)
        }
    }

    fun start(): LauncherWorker {
        if (shouldShowDialog) {
            SwingUtilities.invokeLater {
                dialog.isModal = true
                dialog.isVisible = true
            }
        }

        currentJob = CoroutineScope(Dispatchers.Default).launch {
            try {
                work(dialog)
                SwingUtilities.invokeLater { if (dialog.isVisible) dialog.dispose() }
            } catch (e: Throwable) {
                MCSRLauncher.LOGGER.error(e.message, e)
                SwingUtilities.invokeLater { if (dialog.isVisible) dialog.dispose() }
                onError(e)
            }
        }
        started = true
        return this
    }

    suspend fun join() {
         currentJob?.join()
    }

    fun showDialog(): LauncherWorker {
        if (started) throw IllegalStateException("should be called before start()!")
        shouldShowDialog = true
        return this
    }

    fun cancelWork() {
        currentJob?.cancel()
        cancelled = true
    }

    fun isCancelled(): Boolean {
        return cancelled
    }

    fun addBottomPanel(component: JComponent) {
        this.southPanel.add(component, BorderLayout.SOUTH)
        this.southPanel.revalidate()
        this.southPanel.repaint()
        this.dialog.pack()
    }

    open fun setState(string: String?, log: Boolean = true): LauncherWorker {
        if (!string.isNullOrBlank() && log) MCSRLauncher.LOGGER.info(string)
        applyState(string)
        return this
    }

    open fun setSubText(string: String?): LauncherWorker {
        subLabel.text = if (string == null) "" else "   $string   "
        if (!subLabel.isVisible && subLabel.text.isNotBlank()) refreshDialogWidth()
        return this
    }

    private fun refreshDialogWidth() {
        if (!started || !dialog.isVisible) return

        label.isVisible = label.text.isNotBlank()
        subLabel.isVisible = subLabel.text.isNotBlank()

        val oldSize = dialog.size
        val oldLocation = dialog.location
        val centerX = oldLocation.x + oldSize.width / 2
        val centerY = oldLocation.y + oldSize.height / 2

        val newWidth = (label.preferredSize.width + 60).coerceAtLeast(300)
        val newHeight = label.preferredSize.height + 90
        dialog.minimumSize = Dimension(dialog.minimumSize.width.coerceAtLeast(newWidth), newHeight)

        dialog.pack()

        val newSize = dialog.size
        val newX = centerX - newSize.width / 2
        val newY = centerY - newSize.height / 2
        dialog.setLocation(newX, newY)
    }

    fun setProgress(value: Float): LauncherWorker {
        return this.setProgress(value.toDouble())
    }

    open fun setProgress(value: Double?): LauncherWorker {
        applyProgress(value)
        return this
    }

    fun indeterminate(): LauncherWorker {
        this.setProgress(1.0)
        progressBar.isIndeterminate = true
        return this
    }
}

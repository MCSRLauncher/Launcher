package com.redlimerl.mcsrlauncher.gui.component

import com.redlimerl.mcsrlauncher.data.instance.BasicInstance
import com.redlimerl.mcsrlauncher.gui.CopyInstanceGui
import com.redlimerl.mcsrlauncher.gui.ExportInstanceGui
import com.redlimerl.mcsrlauncher.launcher.InstanceManager
import com.redlimerl.mcsrlauncher.util.I18n
import com.redlimerl.mcsrlauncher.util.LauncherWorker
import com.redlimerl.mcsrlauncher.util.SwingUtils
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

class InstanceLaunchButton(private val windowParent: Window, val instance: BasicInstance) : JButton() {
    init {
        preferredSize = Dimension(120, 120)
        isContentAreaFilled = true
        horizontalAlignment = SwingConstants.CENTER
        horizontalTextPosition = SwingConstants.CENTER
        verticalTextPosition = SwingConstants.BOTTOM
        object : LauncherWorker() {
            override fun work(dialog: JDialog) {
                icon = ImageIcon(ImageIcon(instance.getIconResource()).image.getScaledInstance(60, 60, Image.SCALE_SMOOTH))
            }
        }.start()
        iconTextGap = 6
        margin = Insets(5, 10, 5, 10)
        isOpaque = false
        isRequestFocusEnabled = false
        text = """<html>
                <div style='text-align:center;'>
                    <div style='font-weight: bold;'>${SwingUtils.autoFitHtmlText(instance.displayName, 92, 16, 8)}</div>
                    <div>${SwingUtils.autoFitHtmlText(if (instance.isRunning()) "<span style='color: yellow'>PLAYING (PID:${instance.getProcess()?.process?.pid()})</span>" else "v${instance.minecraftVersion} (${instance.getInstanceType()})", 92, 11)}</div>
                </div> 
            </html>"""
        if (instance.isRunning()) {
            putClientProperty("JComponent.outline", "warning")
        }

        toolTipText = "${I18n.translate("text.instance")} ID: ${instance.id}\n${I18n.translate("text.play_time")}: ${I18n.translate("text.play_time.value", instance.playTime / 3600.0)}"

        this.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger) getPopupMenu().show(e.component, e.x, e.y)
            }
            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger) getPopupMenu().show(e.component, e.x, e.y)
            }
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && SwingUtilities.isLeftMouseButton(e)) {
                    instance.launchWithDialog()
                }
            }
        })
    }

    private fun getPopupMenu(): JPopupMenu {
        val popupMenu = JPopupMenu()

        if (!instance.isRunning()) {
            val launchItem = JMenuItem(I18n.translate("instance.launch")).apply {
                addActionListener { instance.launchWithDialog() }
            }
            popupMenu.add(launchItem)
        } else {
            popupMenu.add(JMenuItem(I18n.translate("instance.kill_process")).apply {
                addActionListener { instance.getProcess()?.exit() }
            })
        }

        popupMenu.add(JMenuItem(I18n.translate("instance.edit")).apply {
            addActionListener {
                instance.openOptionDialog(windowParent)
            }
        })

        popupMenu.add(JMenuItem(I18n.translate("text.open.dot_minecraft")).apply {
            addActionListener { Desktop.getDesktop().open(instance.getGamePath().toFile().apply { mkdirs() }) }
        })

        popupMenu.add(JMenuItem(I18n.translate("text.copy.instance")).apply {
            addActionListener {
                CopyInstanceGui(windowParent, instance)
            }
        })

        val exportMenu = JMenu(I18n.translate("text.export.instance"))
        exportMenu.add(
            JMenuItem(I18n.translate("MultiMC/Prism Launcher")).apply {
                addActionListener{
                    ExportInstanceGui(windowParent, instance, 1)
                }
            }
        )
        exportMenu.add(
            JMenuItem(I18n.translate("MCSR Launcher")).apply {
                addActionListener{
                    ExportInstanceGui(windowParent, instance, 2)
                }
            }
        )

        popupMenu.add(exportMenu)

        popupMenu.add(JMenuItem(I18n.translate("instance.delete")).apply {
            addActionListener {
                val confirm = JOptionPane.showConfirmDialog(windowParent, I18n.translate("message.delete_instance_confirm"), I18n.translate("instance.delete"), JOptionPane.OK_CANCEL_OPTION)
                if (confirm == JOptionPane.OK_OPTION) {
                    InstanceManager.deleteInstance(instance)
                }
            }
            isEnabled = !instance.isRunning()
        })

        return popupMenu
    }
}
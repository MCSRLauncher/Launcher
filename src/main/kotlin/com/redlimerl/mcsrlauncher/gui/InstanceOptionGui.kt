package com.redlimerl.mcsrlauncher.gui

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.data.device.DeviceOSType
import com.redlimerl.mcsrlauncher.data.instance.BasicInstance
import com.redlimerl.mcsrlauncher.data.meta.MetaUniqueID
import com.redlimerl.mcsrlauncher.data.meta.file.MinecraftMapsMetaFile
<<<<<<< HEAD
import com.redlimerl.mcsrlauncher.gui.component.WorkaroundSettingsPanel
=======
import com.redlimerl.mcsrlauncher.data.meta.file.SpeedrunToolsMetaFile
>>>>>>> upstream/main
import com.redlimerl.mcsrlauncher.gui.component.InstanceGroupComboBox
import com.redlimerl.mcsrlauncher.gui.component.JavaSettingsPanel
import com.redlimerl.mcsrlauncher.gui.component.ResolutionSettingsPanel
import com.redlimerl.mcsrlauncher.instance.mod.ModData
import com.redlimerl.mcsrlauncher.launcher.InstanceManager
import com.redlimerl.mcsrlauncher.launcher.MetaManager
import com.redlimerl.mcsrlauncher.util.AssetUtils
import com.redlimerl.mcsrlauncher.util.I18n
import com.redlimerl.mcsrlauncher.util.LauncherWorker
import com.redlimerl.mcsrlauncher.util.SwingUtils
import org.apache.commons.io.FileUtils
import java.awt.*
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDragEvent
import java.awt.dnd.DropTargetDropEvent
import java.awt.Window
import java.awt.event.WindowEvent
import java.awt.event.WindowFocusListener
import java.io.File
import java.text.SimpleDateFormat
import javax.swing.*
import javax.swing.filechooser.FileFilter
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableModel
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists

class InstanceOptionGui(parent: Window, private val instance: BasicInstance) : InstanceOptionDialog(parent) {
    private val instanceOptions = instance.options
    private val applyInstanceChanges = { instance.save() }

    var mods: List<ModData> = emptyList()
    private val launchBlockComponents = arrayListOf<Component>()

    init {
        title = getUpdatedTitle()
        minimumSize = Dimension(800, 500)
        defaultCloseOperation = DISPOSE_ON_CLOSE
        setLocationRelativeTo(parent)

        this.cancelButton.addActionListener { this.dispose() }
        this.launchButton.addActionListener { instance.launchWithDialog() }
        launchBlockComponents.add(launchButton)

        initInstanceTab()
        initVersionTab()
        initModsTab()
        initJavaTab()
        initLogTab()
        initToolsTab()
        initWorkaroundsTab()

        I18n.translateGui(this)
        setLauncherLaunched(instance.isRunning())
        isVisible = true
    }

    override fun dispose() {
        super.dispose()
        instance.optionDialog = null
    }

    fun openTab(index: Int) {
        this.optionTab.selectedIndex = index
    }

    private fun getUpdatedTitle(): String {
        return "${I18n.translate("instance.edit")} - ${instance.displayName}"
    }

    private fun initInstanceTab() {
        instanceNameField.text = instance.displayName

        InstanceGroupComboBox.init(instanceGroupField)
        instanceGroupField.selectedItem = InstanceManager.getInstanceGroup(instance)

        instanceApplyChangesButton.addActionListener {
            if (instanceNameField.text != instance.displayName) {
                InstanceManager.renameInstance(instance, instanceNameField.text)
                title = getUpdatedTitle()
            }
            if (instanceGroupField.selectedItem?.toString() != InstanceManager.getInstanceGroup(instance)) {
                InstanceManager.moveInstanceGroup(instance, instanceGroupField.selectedItem as String)
            }
        }
        launchBlockComponents.add(instanceApplyChangesButton)

        instanceOpenDirectoryButton.addActionListener {
            Desktop.getDesktop().open(instance.getGamePath().toFile().apply { mkdirs() })
        }

        val resolutionSettingsPanel = ResolutionSettingsPanel(instance.options, instance::save)

        instanceResolutionCheckBox.addActionListener {
            SwingUtils.setEnabledRecursively(resolutionSettingsPanel, !instanceResolutionCheckBox.isSelected)
            instance.options.useLauncherResolutionOption = instanceResolutionCheckBox.isSelected
            instance.save()
        }
        instanceResolutionCheckBox.isSelected = instance.options.useLauncherJavaOption
        SwingUtils.setEnabledRecursively(resolutionSettingsPanel, !instanceResolutionCheckBox.isSelected)

        this.instanceResolutionPanel.layout = BorderLayout()
        this.instanceResolutionPanel.add(resolutionSettingsPanel, BorderLayout.CENTER)
    }

    private fun initVersionTab() {
        versionsTable.tableHeader.reorderingAllowed = false
        versionsTable.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        versionsTable.setDefaultEditor(Object::class.java, null)
        versionsTable.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting && versionsTable.selectedRow == -1 && versionsTable.rowCount > 0)
                versionsTable.setRowSelectionInterval(it.lastIndex, it.lastIndex)
        }

        val tableModel = DefaultTableModel(arrayOf(), arrayOf(I18n.translate("text.type"), I18n.translate("text.version")))
        tableModel.addRow(arrayOf("Minecraft", instance.minecraftVersion))
        tableModel.addRow(arrayOf("LWJGL", instance.lwjglVersion.version))
        val fabric = instance.fabricVersion
        if (fabric != null) {
            tableModel.addRow(arrayOf("Fabric Loader", fabric.loaderVersion))
            tableModel.addRow(arrayOf("Fabric Intermediary", "${fabric.intermediaryVersion} (${fabric.intermediaryType.intermediaryName})"))
        }
        versionsTable.model = tableModel

        changeVersionButton.addActionListener {
            val changeVersion = ChangeGameVersionGui(this@InstanceOptionGui, instance)
            if (changeVersion.hasChanged) {
                initVersionTab()
            }
        }
        launchBlockComponents.add(changeVersionButton)
    }

    private fun updateMods() {
        this.mods = instance.getMods()

        modsTable.tableHeader.reorderingAllowed = false
        modsTable.selectionModel.selectionMode = ListSelectionModel.MULTIPLE_INTERVAL_SELECTION
        modsTable.setDefaultEditor(Object::class.java, null)

        val modTableModel = object : AbstractTableModel() {
            override fun getRowCount(): Int = mods.size

            override fun getColumnCount(): Int = 5

            override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
                val mod = mods[rowIndex]
                return when(columnIndex) {
                    0 -> mod.name
                    1 -> if (mod.isEnabled) "✅" else "❌"
                    2 -> mod.version
                    3 -> SimpleDateFormat.getDateTimeInstance(SimpleDateFormat.MEDIUM, SimpleDateFormat.SHORT, MCSRLauncher.options.language.getLocale()).format(mod.file.lastModified())
                    4 -> AssetUtils.formatFileSize(mod.file.length())
                    else -> ""
                }
            }

            override fun getColumnClass(columnIndex: Int): Class<*> = String::class.java

            override fun getColumnName(column: Int): String {
                return arrayOf(I18n.translate("text.name"), I18n.translate("text.enabled"), I18n.translate("text.version"), I18n.translate("text.last_modified"), I18n.translate("text.size"))[column]
            }
        }
        modsTable.model = modTableModel

        SwingUtils.autoFitTableColumns(modsTable)
        launchBlockComponents.add(changeVersionButton)
    }

    private fun initModsTab() {
        manageSpeedrunModsButton.addActionListener {
            SpeedrunModsManageGui(this, instance, false) {
                updateMods()
            }
        }

        updateSpeedrunModsButton.addActionListener {
            object : LauncherWorker(this@InstanceOptionGui, I18n.translate("text.manage_speedrun_mods"), I18n.translate("message.checking_updates")) {
                override fun work(dialog: JDialog) {
                    val updates = instance.updateSpeedrunMods(this)
                    if (updates.isNotEmpty()) {
                        JOptionPane.showMessageDialog(this@InstanceOptionGui,
                            I18n.translate("message.download_success").plus("\n")
                                .plus(updates.joinToString("\n") { "- ${it.name} v${it.version}" })
                        )
                    }
                }
            }.showDialog().start()
        }

        autoUpdateSpeedrunModsCheckBox.isSelected = instance.options.autoModUpdates
        autoUpdateSpeedrunModsCheckBox.addActionListener {
            instance.options.autoModUpdates = autoUpdateSpeedrunModsCheckBox.isSelected
            instance.save()
        }

        updateMods()

        fun updateModSelection() {
            val enabled = modsTable.selectedRows.isNotEmpty()
            enableModButton.isEnabled = enabled
            disableModButton.isEnabled = enabled
            deleteModButton.isEnabled = enabled
        }

        modsTable.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                updateModSelection()
            }
        }

        updateModSelection()

        openModsDirButton.addActionListener {
            Desktop.getDesktop().open(instance.getModsPath().toFile().apply { mkdirs() })
        }

        addModFileButton.addActionListener {
            val fileChooser = JFileChooser(instance.getModsPath().toFile()).apply {
                isMultiSelectionEnabled = true
                dialogType = JFileChooser.CUSTOM_DIALOG
                dialogTitle = I18n.translate("text.add_mod_file")
                fileSelectionMode = JFileChooser.FILES_ONLY
                fileFilter = object : FileFilter() {
                    override fun accept(f: File): Boolean {
                        return f.isDirectory || f.name.lowercase().endsWith(".jar")
                    }

                    override fun getDescription(): String {
                        return "JAR File (*.jar)"
                    }
                }
            }
            SwingUtils.makeEditablePathFileChooser(fileChooser)

            val result = fileChooser.showDialog(this, I18n.translate("text.select"))

            if (result == JFileChooser.APPROVE_OPTION) {
                val selectedFiles = fileChooser.selectedFiles
                for (file in selectedFiles) {
                    if (file.parentFile.absolutePath == instance.getModsPath().absolutePathString() || instance.getModsPath().resolve(file.name).exists()) continue
                    if (ModData.get(file) == null) continue
                    FileUtils.copyFile(file, instance.getModsPath().resolve(file.name).toFile())
                }
                updateMods()
            }
        }

        this.dropTarget = object : DropTarget() {
            override fun dragOver(dtde: DropTargetDragEvent) {
                if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor) && optionTab.selectedIndex == 2) {
                    dtde.acceptDrag(DnDConstants.ACTION_COPY)
                } else {
                    dtde.rejectDrag()
                }
            }
            override fun drop(dtde: DropTargetDropEvent) {
                if (dtde.isDataFlavorSupported(DataFlavor.javaFileListFlavor) && optionTab.selectedIndex == 2) {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY)
                    val droppedFiles = dtde.transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<*>
                    for (file in droppedFiles) {
                        if (file is File) {
                            if (file.parentFile.absolutePath == instance.getModsPath().absolutePathString() || instance.getModsPath().resolve(file.name).exists()) continue
                            if (ModData.get(file) == null) continue
                            FileUtils.copyFile(file, instance.getModsPath().resolve(file.name).toFile())
                        }
                        updateMods()
                    }
                    dtde.dropComplete(true)
                } else {
                    dtde.rejectDrop()
                    dtde.dropComplete(false)
                }
            }
        }

        enableModButton.addActionListener {
            if (checkInstanceLaunched()) return@addActionListener
            modsTable.selectedRows.map { mods[it] }.forEach { it.isEnabled = true }
            updateMods()
        }

        disableModButton.addActionListener {
            if (checkInstanceLaunched()) return@addActionListener
            modsTable.selectedRows.map { mods[it] }.forEach { it.isEnabled = false }
            updateMods()
        }

        deleteModButton.addActionListener {
            if (checkInstanceLaunched()) return@addActionListener
            modsTable.selectedRows.map { mods[it] }.forEach { it.delete() }
            updateMods()
        }

        this.addWindowFocusListener(object : WindowFocusListener {
            override fun windowGainedFocus(e: WindowEvent?) {
                if (!instance.logViewerPanel.displayLiveLog) instance.logViewerPanel.updateLogFiles()
                SwingUtilities.invokeLater {
                    updateMods()
                }
            }

            override fun windowLostFocus(e: WindowEvent?) {}
        })

        launchBlockComponents.add(manageSpeedrunModsButton)
        launchBlockComponents.add(updateSpeedrunModsButton)
    }

    private fun initJavaTab() {
        val javaSettingsPanel = JavaSettingsPanel(this, instance.options, instance::save)

        javaLauncherSettingCheckBox.addActionListener {
            SwingUtils.setEnabledRecursively(javaSettingsPanel, !javaLauncherSettingCheckBox.isSelected)
            instance.options.useLauncherJavaOption = javaLauncherSettingCheckBox.isSelected
            instance.save()
        }
        javaLauncherSettingCheckBox.isSelected = instance.options.useLauncherJavaOption
        SwingUtils.setEnabledRecursively(javaSettingsPanel, !javaLauncherSettingCheckBox.isSelected)

        javaSettingsPane.layout = BorderLayout()
        javaSettingsPane.add(javaSettingsPanel, BorderLayout.CENTER)
        SwingUtils.fasterScroll(javaScrollPane)
    }

    private fun initLogTab() {
        logPanel.layout = BorderLayout()
        logPanel.add(instance.logViewerPanel.also { it.syncInstance(instance) }, BorderLayout.CENTER)
    }

    private fun checkInstanceLaunched(): Boolean {
        if (instance.isRunning()) {
            JOptionPane.showMessageDialog(this@InstanceOptionGui, I18n.translate("message.instance_launched_warning"), I18n.translate("text.error"), JOptionPane.ERROR_MESSAGE)
            return true
        }
        return false
    }

    fun setLauncherLaunched(launched: Boolean) {
        SwingUtilities.invokeLater { updateMods() }
        launchBlockComponents.forEach { SwingUtils.setEnabledRecursively(it, !launched) }
    }

    private fun initToolsTab() {
        browsePracticeMapsButton.addActionListener {
            object : LauncherWorker(this@InstanceOptionGui, I18n.translate("message.loading")) {
                override fun work(dialog: JDialog) {
                    val maps = MetaManager.getVersionMeta<MinecraftMapsMetaFile>(MetaUniqueID.PRACTICE_MAPS, "verified", this)!!
                    dialog.dispose()
                    SpeedrunMapBrowseGui(this@InstanceOptionGui, I18n.translate("text.download.practice_maps"), maps.maps, instance)
                }
            }.showDialog().start()
        }
        browseToolsButton.addActionListener {
            object : LauncherWorker(this@InstanceOptionGui, I18n.translate("message.loading")) {
                override fun work(dialog: JDialog) {
                    val tools = MetaManager.getVersionMeta<SpeedrunToolsMetaFile>(MetaUniqueID.SPEEDRUN_TOOLS, "verified", this)!!
                    dialog.dispose()
                    SpeedrunToolsBrowseGui(this@InstanceOptionGui, I18n.translate("text.download.practice_maps"), tools.programs, instance)
                }
            }.showDialog().start()
        }

        worldClearButton.addActionListener {
            object : LauncherWorker(this@InstanceOptionGui, I18n.translate("text.clear_worlds"), I18n.translate("message.loading") + "...") {
                override fun work(dialog: JDialog) {
                    worldClearButton.isEnabled = false
                    val deletedCount = instance.clearWorlds(this)
                    worldClearButton.isEnabled = true
                    dialog.dispose()
                    if (deletedCount != null) {
                        JOptionPane.showMessageDialog(this@InstanceOptionGui, I18n.translate("message.clear_worlds.success", deletedCount))
                    }
                }
            }.showDialog().start()
        }

        autoWorldClearComboBox.isSelected = instance.options.clearBeforeLaunch
        autoWorldClearComboBox.addActionListener {
            instance.options.clearBeforeLaunch = autoWorldClearComboBox.isSelected
            instance.save()
        }
    }

    private fun initWorkaroundsTab() {
        if (DeviceOSType.WINDOWS.isOn()) {
            for (i in 0 until optionTab.tabCount) {
                if (optionTab.getComponentAt(i) == workaroundScrollPane) {
                    optionTab.removeTabAt(i)
                    break
                }
            }
            return
        }

        val workaroundPanel = WorkaroundSettingsPanel(
            this,
            instance,
            instance.options,
            instance.options
        ) {
            instance.save()
        }

        fun commandExists(cmd: String): Boolean {
            return try {
                ProcessBuilder("which", cmd).start().waitFor() == 0
            } catch (_: Exception) {
                false
            }
        }

        val feralAvailable = commandExists("gamemoded")
        val mangoAvailable = commandExists("mangohud")

        workaroundPanel.feralBox.isEnabled = feralAvailable
        workaroundPanel.feralBox.toolTipText =
            if (feralAvailable) "Enable Feral GameMode" else "Feral Interactive's GameMode not found in PATH"

        workaroundPanel.mangoBox.isEnabled = mangoAvailable
        workaroundPanel.mangoBox.toolTipText =
            if (mangoAvailable) "Enable MangoHUD" else "mangoHUD not found in PATH"

        workaroundLauncherSettingCheckBox.isSelected = instance.options.useLauncherWorkarounds
        workaroundLauncherSettingCheckBox.addActionListener {
            val selected = workaroundLauncherSettingCheckBox.isSelected
            instance.options.useLauncherWorkarounds = selected
            workaroundPanel.applyLauncherSettings(selected)
            workaroundPanel.feralBox.isEnabled = feralAvailable && !selected
            workaroundPanel.mangoBox.isEnabled = mangoAvailable && !selected
            instance.save()
        }

        workaroundSettingsPane.layout = BorderLayout()
        workaroundSettingsPane.removeAll()
        workaroundSettingsPane.add(workaroundPanel, BorderLayout.CENTER)
        workaroundSettingsPane.revalidate()
        workaroundSettingsPane.repaint()

        SwingUtils.fasterScroll(workaroundScrollPane)
    }
}
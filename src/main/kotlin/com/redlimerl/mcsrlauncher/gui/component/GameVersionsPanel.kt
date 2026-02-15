package com.redlimerl.mcsrlauncher.gui.component

import com.formdev.flatlaf.FlatClientProperties
import com.redlimerl.mcsrlauncher.data.instance.BasicInstance
import com.redlimerl.mcsrlauncher.data.instance.FabricVersionData
import com.redlimerl.mcsrlauncher.data.instance.LWJGLVersionData
import com.redlimerl.mcsrlauncher.data.instance.mcsrranked.MCSRRankedPackType
import com.redlimerl.mcsrlauncher.data.meta.IntermediaryType
import com.redlimerl.mcsrlauncher.data.meta.MetaUniqueID
import com.redlimerl.mcsrlauncher.data.meta.MetaVersion
import com.redlimerl.mcsrlauncher.data.meta.MetaVersionType
import com.redlimerl.mcsrlauncher.gui.components.AbstractGameVersionsPanel
import com.redlimerl.mcsrlauncher.launcher.MetaManager
import com.redlimerl.mcsrlauncher.util.*
import io.github.z4kn4fein.semver.toVersion
import java.awt.BorderLayout
import java.awt.Component
import java.awt.FlowLayout
import java.io.File
import java.io.IOException
import java.net.URI
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import javax.swing.*
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener
import javax.swing.filechooser.FileFilter
import javax.swing.table.DefaultTableModel
import javax.swing.table.TableCellRenderer
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name


class GameVersionsPanel(private val parentWindow: JDialog, val instance: BasicInstance? = null) : AbstractGameVersionsPanel() {

    var launcherPath: Path? = null

    init {
        layout = BorderLayout()
        add(this.gameTabPane, BorderLayout.CENTER)

        initVanillaComponents()
        updateVanillaVersions()

        initFabricComponents()
        updateFabricVersions()

        initMCSRRankedComponents()

        initMigrateLauncherComponents()

        initImportComponents()

        if (instance != null) {
            val mcsrRanked = instance.mcsrRankedType
            val fabric = instance.fabricVersion
            gameTabPane.selectedIndex =
                if (mcsrRanked != null) 2
                else if (fabric != null) 0
                else 0

            if (mcsrRanked != null) {
                for (i in 0 until mcsrRankedPackTypeBox.itemCount) {
                    if (mcsrRankedPackTypeBox.getItemAt(i) == mcsrRanked) {
                        mcsrRankedPackTypeBox.selectedIndex = i
                        break
                    }
                }
            }

            for (i in 0 until fabricVersionTable.rowCount) {
                if (fabricVersionTable.getValueAt(i, 1) == instance.minecraftVersion) {
                    fabricVersionTable.setRowSelectionInterval(i, i)
                    fabricVersionTable.scrollRectToVisible(fabricVersionTable.getCellRect(i, 0, true))
                    break
                }
            }
            for (i in 0 until vanillaVersionTable.rowCount) {
                if (vanillaVersionTable.getValueAt(i, 1) == instance.minecraftVersion) {
                    vanillaVersionTable.setRowSelectionInterval(i, i)
                    vanillaVersionTable.scrollRectToVisible(vanillaVersionTable.getCellRect(i, 0, true))
                    break
                }
            }

            gameTabPane.removeTabAt(gameTabPane.indexOfTab("Migrate Launcher"))
        }
    }

    fun getMinecraftVersion(): MetaVersion {
        // Hardcode for MCSR Ranked Setup
        if (gameTabPane.selectedIndex == 2) return MetaManager.getVersions(MetaUniqueID.MINECRAFT).find { it.version == "1.16.1" }!!

        val isFabric = gameTabPane.selectedIndex == 0
        val currentVersionTable = (if (isFabric) fabricVersionTable else vanillaVersionTable)
        if (currentVersionTable.selectedRow == -1) throw IllegalStateException("Minecraft version has not been selected")

        val selectedMinecraftVersion = currentVersionTable.let { it.getValueAt(it.selectedRow, 1) }.toString()
        return MetaManager.getVersions(MetaUniqueID.MINECRAFT).find { it.version == selectedMinecraftVersion }!!
    }

    fun getLWJGLVersion(): LWJGLVersionData {
        // Hardcode for MCSR Ranked Setup
        if (gameTabPane.selectedIndex == 2) return LWJGLVersionData(MetaUniqueID.LWJGL3, "3.3.3")

        val vanillaVersion = this.getMinecraftVersion()
        val isFabric = gameTabPane.selectedIndex == 0
        val currentLWJGLComboBox = (if (isFabric) fabricLWJGLComboBox else vanillaLWJGLComboBox)
        val lwjglRequire = vanillaVersion.requires.first()
        val lwjglSelected = currentLWJGLComboBox.selectedItem as? String ?: throw IllegalStateException("LWJGL version is not selected")
        if (MetaManager.getVersions(lwjglRequire.uid).none { it.version == lwjglSelected }) throw IllegalStateException("$lwjglSelected is not a selectable version in ${lwjglRequire.uid}")
        return LWJGLVersionData(lwjglRequire.uid, currentLWJGLComboBox.selectedItem as? String ?: throw IllegalStateException("LWJGL version is not selected"))
    }

    fun getFabricVersion(): FabricVersionData? {
        // Hardcode for MCSR Ranked Setup
        if (gameTabPane.selectedIndex == 2) {
            val loaderVersion = MetaManager.getVersions(MetaUniqueID.FABRIC_LOADER).find { loader -> loader.recommended } ?: throw IllegalStateException("Couldn't find any recommended Fabric Loader version")
            val intermediaryVersion = MetaManager.getVersions(MetaUniqueID.FABRIC_INTERMEDIARY).find { it.version == "1.16.1" }!!
            val intermediaryType = intermediaryVersion.compatibleIntermediaries.find { it == IntermediaryType.FABRIC } ?: throw IllegalStateException("Couldn't find any recommended Fabric Intermediary")
            return FabricVersionData(loaderVersion.version, intermediaryType, "1.16.1")
        }

        val isFabric = gameTabPane.selectedIndex == 0
        val fabricVersion = (if (isFabric) fabricLoaderVersionComboBox.model.selectedItem?.toString() else null)
            ?.let { MetaManager.getVersions(MetaUniqueID.FABRIC_LOADER).find { loader -> loader.version == it } }
        val vanillaVersion = this.getMinecraftVersion()
        val intermediaryType = this.getIntermediaryType()

        return if (fabricVersion != null && intermediaryType != null) {
            val loaderVersion = fabricVersion.version
            val intermediaryVersion = vanillaVersion.version
            FabricVersionData(loaderVersion, intermediaryType, intermediaryVersion)
        } else null
    }

    private fun getIntermediaryType(): IntermediaryType? {
        return intermediaryComboBox.selectedItem as IntermediaryType?
    }

    fun getMCSRRankedPackType(): MCSRRankedPackType? {
        if (gameTabPane.selectedIndex != 2) return null
        return mcsrRankedPackTypeBox.getItemAt(mcsrRankedPackTypeBox.selectedIndex)
    }

    private fun initVanillaComponents() {
        vanillaRefreshButton.addActionListener {
            object : LauncherWorker(parent, I18n.translate("message.loading"), I18n.translate("message.updating.meta")) {
                override fun work(dialog: JDialog) {
                    MetaManager.load(this, true)
                    updateVanillaVersions()
                }
            }.showDialog().start()
        }

        listOf(vanillaSpeedrunCheckbox, vanillaReleaseCheckBox, vanillaSnapshotCheckBox, vanillaBetaCheckBox, vanillaAlphaCheckBox, vanillaExperimentCheckBox)
            .forEach { it.addActionListener { updateVanillaVersions() } }

        vanillaVersionSearchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updateVanillaVersions()
            override fun removeUpdate(e: DocumentEvent?) = updateVanillaVersions()
            override fun changedUpdate(e: DocumentEvent?) = Unit
        })
        vanillaVersionTable.tableHeader.reorderingAllowed = false
        vanillaVersionTable.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        vanillaVersionTable.setDefaultEditor(Object::class.java, null)
        vanillaVersionTable.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                if (vanillaVersionTable.selectedRow == -1 && vanillaVersionTable.rowCount > 0)
                    vanillaVersionTable.setRowSelectionInterval(it.lastIndex, it.lastIndex)

                val selectedRow = vanillaVersionTable.selectedRow
                if (selectedRow >= 0) {
                    this.onSelectVanillaMinecraftVersion(vanillaVersionTable.getValueAt(selectedRow, 1).toString())
                }
            }
        }
    }

    private fun updateVanillaVersions() {
        val tableModel = DefaultTableModel(arrayOf(), arrayOf(I18n.translate("text.type"), I18n.translate("text.version"), I18n.translate("text.date")))
        MetaManager.getVersions(MetaUniqueID.MINECRAFT).filter {
            if (it.type == MetaVersionType.RELEASE && !vanillaReleaseCheckBox.isSelected
                && (!vanillaSpeedrunCheckbox.isSelected || !SpeedrunUtils.MAJOR_SPEEDRUN_VERSIONS.contains(it.version))) return@filter false
            if ((it.type == MetaVersionType.SNAPSHOT || it.type == MetaVersionType.OLD_SNAPSHOT) && !vanillaSnapshotCheckBox.isSelected) return@filter false
            if ((it.type == MetaVersionType.BETA || it.type == MetaVersionType.OLD_BETA) && !vanillaBetaCheckBox.isSelected) return@filter false
            if ((it.type == MetaVersionType.ALPHA || it.type == MetaVersionType.OLD_ALPHA) && !vanillaAlphaCheckBox.isSelected) return@filter false
            if (it.type == MetaVersionType.EXPERIMENT && !vanillaExperimentCheckBox.isSelected) return@filter false
            if (vanillaVersionSearchField.text.isNotBlank() && !it.version.contains(vanillaVersionSearchField.text, true)) return@filter false
            return@filter true
        }.sortedByDescending {
            if (vanillaSpeedrunCheckbox.isSelected && SpeedrunUtils.MAJOR_SPEEDRUN_VERSIONS.contains(it.version)) return@sortedByDescending Long.MAX_VALUE - SpeedrunUtils.MAJOR_SPEEDRUN_VERSIONS.indexOf(it.version)
            return@sortedByDescending it.releaseTime.time
        }.forEach {
            tableModel.addRow(it.dataArray())
        }

        vanillaVersionTable.model = tableModel
        if (vanillaVersionTable.rowCount > 0) vanillaVersionTable.setRowSelectionInterval(0, 0)
        SwingUtils.autoFitTableColumns(vanillaVersionTable)
    }

    private fun initFabricComponents() {
        fabricRefreshButton.addActionListener {
            object : LauncherWorker(parentWindow, I18n.translate("message.loading"), I18n.translate("message.updating.versions")) {
                override fun work(dialog: JDialog) {
                    MetaManager.load(this, true)
                    updateVanillaVersions()
                    updateFabricVersions()
                }
            }.showDialog().start()
        }

        listOf(fabricSpeedrunCheckBox, fabricReleaseCheckBox, fabricSnapshotCheckBox, fabricBetaCheckBox, fabricAlphaCheckBox, fabricExperimentCheckBox).forEach { it.addActionListener { updateFabricVersions() } }

        fabricVersionSearchField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) = updateFabricVersions()
            override fun removeUpdate(e: DocumentEvent?) = updateFabricVersions()
            override fun changedUpdate(e: DocumentEvent?) = Unit
        })
        fabricVersionTable.tableHeader.reorderingAllowed = false
        fabricVersionTable.selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
        fabricVersionTable.setDefaultEditor(Object::class.java, null)
        fabricVersionTable.selectionModel.addListSelectionListener {
            if (!it.valueIsAdjusting) {
                if (fabricVersionTable.selectedRow == -1 && fabricVersionTable.rowCount > 0)
                    fabricVersionTable.setRowSelectionInterval(it.lastIndex, it.lastIndex)

                val selectedRow = fabricVersionTable.selectedRow
                if (selectedRow >= 0) {
                    this.onSelectFabricMinecraftVersion(fabricVersionTable.getValueAt(selectedRow, 1).toString())
                }
            }
        }

        intermediaryHelpButton.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_HELP)
        intermediaryHelpButton.addActionListener {
            JOptionPane.showMessageDialog(parent, I18n.translate("message.help.intermediary"))
        }
    }

    private fun updateFabricVersions() {
        val loaderModel = DefaultComboBoxModel<String>(arrayOf())
        val fabricLoaderVersions = MetaManager.getVersions(MetaUniqueID.FABRIC_LOADER)

        fabricLoaderVersions.forEach { loaderModel.addElement(it.version) }
        fabricLoaderVersionComboBox.model = loaderModel
        fabricLoaderVersionComboBox.selectedIndex = fabricLoaderVersions.indexOfFirst {
            val instanceVersion = instance?.fabricVersion?.loaderVersion
            return@indexOfFirst if (instanceVersion != null) it.version == instanceVersion else it.recommended
        }

        val tableModel = DefaultTableModel(arrayOf(), arrayOf(I18n.translate("text.type"), I18n.translate("text.version"), I18n.translate("text.date")))
        MetaManager.getVersions(MetaUniqueID.MINECRAFT).filter {
            if (!MetaManager.containsVersion(MetaUniqueID.FABRIC_INTERMEDIARY, it.version)) return@filter false
            if (it.type == MetaVersionType.RELEASE && !fabricReleaseCheckBox.isSelected
                && (!fabricSpeedrunCheckBox.isSelected || !SpeedrunUtils.MAJOR_SPEEDRUN_VERSIONS.contains(it.version))) return@filter false
            if ((it.type == MetaVersionType.SNAPSHOT || it.type == MetaVersionType.OLD_SNAPSHOT) && !fabricSnapshotCheckBox.isSelected) return@filter false
            if ((it.type == MetaVersionType.BETA || it.type == MetaVersionType.OLD_BETA) && !fabricBetaCheckBox.isSelected) return@filter false
            if ((it.type == MetaVersionType.ALPHA || it.type == MetaVersionType.OLD_ALPHA) && !fabricAlphaCheckBox.isSelected) return@filter false
            if (it.type == MetaVersionType.EXPERIMENT && !fabricExperimentCheckBox.isSelected) return@filter false
            if (fabricVersionSearchField.text.isNotBlank() && !it.version.contains(fabricVersionSearchField.text, true)) return@filter false
            return@filter true
        }.sortedByDescending {
            if (fabricSpeedrunCheckBox.isSelected && SpeedrunUtils.MAJOR_SPEEDRUN_VERSIONS.contains(it.version)) return@sortedByDescending Long.MAX_VALUE - SpeedrunUtils.MAJOR_SPEEDRUN_VERSIONS.indexOf(it.version)
            return@sortedByDescending it.releaseTime.time
        }.forEach {
            tableModel.addRow(it.dataArray())
        }
        fabricVersionTable.model = tableModel
        if (fabricVersionTable.rowCount > 0) fabricVersionTable.setRowSelectionInterval(0, 0)
        SwingUtils.autoFitTableColumns(fabricVersionTable)
    }

    private fun onSelectFabricMinecraftVersion(version: String) {
        intermediaryComboBox.removeAllItems()
        val intermediaryVersion = MetaManager.getVersions(MetaUniqueID.FABRIC_INTERMEDIARY).find { it.version == version }!!
        var instanceIntermediary: IntermediaryType? = null
        for (compatibleIntermediary in intermediaryVersion.compatibleIntermediaries.sortedByDescending { it.recommendLevel }) {
            intermediaryComboBox.addItem(compatibleIntermediary)
            if (instance?.fabricVersion?.intermediaryType == compatibleIntermediary)
                instanceIntermediary = compatibleIntermediary
        }

        if (instanceIntermediary != null) {
            intermediaryComboBox.selectedItem = instanceIntermediary
        }

        val minecraftVersion = MetaManager.getVersions(MetaUniqueID.MINECRAFT).find { it.version == version }!!
        updateLWJGLVersion(fabricLWJGLComboBox, minecraftVersion)
    }

    private fun onSelectVanillaMinecraftVersion(version: String) {
        val minecraftVersion = MetaManager.getVersions(MetaUniqueID.MINECRAFT).find { it.version == version }!!
        updateLWJGLVersion(vanillaLWJGLComboBox, minecraftVersion)
    }

    private fun updateLWJGLVersion(lwjglComboBox: JComboBox<String>, minecraftVersion: MetaVersion) {
        lwjglComboBox.removeAllItems()
        val lwjglRequire = minecraftVersion.requires.first()
        val availableLWJGL = MetaManager.getVersions(lwjglRequire.uid)
            .filter { it.version.toVersion(false) >= lwjglRequire.suggests?.toVersion(false )!! }
            .sortedByDescending { it.version.toVersion(false) }
        availableLWJGL.forEach { lwjglComboBox.addItem(it.version) }
        var instanceLWJGL = instance?.lwjglVersion?.version
        if (availableLWJGL.none { it.version == instanceLWJGL } || (instance != null && instance.minecraftVersion != minecraftVersion.version)) instanceLWJGL = null
        lwjglComboBox.selectedIndex = availableLWJGL.indexOfFirst {
            it.version == (instanceLWJGL ?: lwjglRequire.suggests)
        }
    }

    private fun initMCSRRankedComponents() {
        mcsrRankedHelpButton.addActionListener {
            OSUtils.openURI(URI.create("https://mcsrranked.com/"))
        }

        MCSRRankedPackType.entries.forEach {
            mcsrRankedPackTypeBox.addItem(it)
        }
        mcsrRankedPackTypeBox.addActionListener {
            mcsrRankedPackTypeDescription.text = "<html>${(mcsrRankedPackTypeBox.selectedItem as MCSRRankedPackType).getWarningMessage()}</html>"
        }
    }

    private fun initMigrateLauncherComponents() {
        mmcBrowseButton.addActionListener {
            val fileChooser = JFileChooser().apply {
                dialogType = JFileChooser.CUSTOM_DIALOG
                dialogTitle = I18n.translate("text.java.browse")
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                fileFilter = object : FileFilter() {
                    override fun accept(f: File): Boolean {
                        return f.isDirectory
                    }

                    override fun getDescription(): String {
                        return "All Files"
                    }
                }
            }
            SwingUtils.makeEditablePathFileChooser(fileChooser)

            val result = fileChooser.showDialog(this, I18n.translate("text.select"))

            if (result == JFileChooser.APPROVE_OPTION) {
                mmcPathField.text = fileChooser.selectedFile.path
                updateLauncherInstances("mmc")
            }
        }

        mmcRefreshButton.addActionListener {
            object : LauncherWorker(parentWindow, I18n.translate("message.loading"), I18n.translate("message.updating.versions")) {
                override fun work(dialog: JDialog) {
                    updateLauncherInstances("mmc")
                }
            }.showDialog().start()
        }

        prismBrowseButton.addActionListener {
            val fileChooser = JFileChooser().apply {
                dialogType = JFileChooser.CUSTOM_DIALOG
                dialogTitle = I18n.translate("text.java.browse")
                fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                fileFilter = object : FileFilter() {
                    override fun accept(f: File): Boolean {
                        return f.isDirectory
                    }

                    override fun getDescription(): String {
                        return "All Files"
                    }
                }
            }
            SwingUtils.makeEditablePathFileChooser(fileChooser)

            val result = fileChooser.showDialog(this, I18n.translate("text.select"))

            if (result == JFileChooser.APPROVE_OPTION) {
                prismPathField.text = fileChooser.selectedFile.path
                updateLauncherInstances("prism")
            }
        }

        prismRefreshButton.addActionListener {
            object : LauncherWorker(parentWindow, I18n.translate("message.loading"), I18n.translate("message.updating.versions")) {
                override fun work(dialog: JDialog) {
                    updateLauncherInstances("prism")
                }
            }.showDialog().start()
        }
    }

    private fun updateLauncherInstances(launcher: String) {
        val selectAllCheckbox = JCheckBox()
        val tableModel = object : DefaultTableModel(arrayOf(), arrayOf(selectAllCheckbox, I18n.translate("instance.name"))) {
            override fun getColumnClass(columnIndex: Int): Class<*> {
                return if (columnIndex == 0) java.lang.Boolean::class.java else String::class.java
            }

            override fun isCellEditable(row: Int, column: Int): Boolean {
                return column == 0
            }
        }

        launcherPath = when (launcher) {
            "mmc" -> Path.of(mmcPathField.text)
            else -> Path.of(prismPathField.text)
        }
        val instancesFolder = launcherPath!!.resolve("instances")
        val message = when (launcher) {
            "mmc" -> "Could not find instances folder. Does this path contain MultiMC.exe?"
            else -> "Could not find instances folder. Does this path contain prismlauncher.exe?"
        }
        val table = when (launcher) {
            "mmc" -> mmcInstanceTable
            else -> prismInstanceTable
        }

        try {
            instancesFolder.listDirectoryEntries().filter { it.isDirectory() }.forEach { dir ->
                if (dir.name != "_LAUNCHER_TEMP" && dir.name != ".tmp") tableModel.addRow(arrayOf(false, dir.name))
            }
        } catch (e: NoSuchFileException) {
            JOptionPane.showMessageDialog(this, message)
        } catch (e: IOException) {
            JOptionPane.showMessageDialog(this, "Error reading instances folder: ${e.message}")
        }

        table.model = tableModel

        val tableCheckboxCol = table.columnModel.getColumn(0)
        tableCheckboxCol.preferredWidth = 50
        tableCheckboxCol.maxWidth = 50
        tableCheckboxCol.headerRenderer = object : TableCellRenderer {
            private val panel = JPanel(FlowLayout(FlowLayout.CENTER, 0, 5)).apply {
                isOpaque = false
                add(selectAllCheckbox)
            }
            override fun getTableCellRendererComponent(
                table: JTable?,
                value: Any?,
                isSelected: Boolean,
                hasFocus: Boolean,
                row: Int,
                column: Int
            ): Component {
                return panel
            }
        }

        table.autoResizeMode = JTable.AUTO_RESIZE_LAST_COLUMN
        table.tableHeader.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                val columnIndex = table.columnModel.getColumnIndexAtX(e.x)
                if (columnIndex == 0) {
                    selectAllCheckbox.isSelected = !selectAllCheckbox.isSelected

                    for (i in 0 until tableModel.rowCount) {
                        tableModel.setValueAt(selectAllCheckbox.isSelected, i, 0)
                    }

                    table.tableHeader.repaint()
                }
            }
        })
    }

    private fun initImportComponents() {
        zipBrowseButton.addActionListener {
            val fileChooser = JFileChooser().apply {
                dialogType = JFileChooser.CUSTOM_DIALOG
                dialogTitle = I18n.translate("text.browse")
                fileSelectionMode = JFileChooser.FILES_ONLY
                fileFilter = object : FileFilter() {
                    override fun accept(f: File): Boolean {
                        return f.isDirectory || f.name.lowercase().endsWith(".zip")
                    }

                    override fun getDescription(): String {
                        return "ZIP Files (*.zip)"
                    }
                }
            }
            SwingUtils.makeEditablePathFileChooser(fileChooser)

            val result = fileChooser.showDialog(this, I18n.translate("text.select"))

            if (result == JFileChooser.APPROVE_OPTION) {
                zipPathField.text = fileChooser.selectedFile.path
            }
        }
    }

}
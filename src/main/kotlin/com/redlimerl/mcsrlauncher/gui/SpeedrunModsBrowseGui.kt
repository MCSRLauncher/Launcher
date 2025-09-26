package com.redlimerl.mcsrlauncher.gui

import com.redlimerl.mcsrlauncher.data.instance.BasicInstance
import com.redlimerl.mcsrlauncher.data.meta.MetaUniqueID
import com.redlimerl.mcsrlauncher.data.meta.file.SpeedrunModsMetaFile
import com.redlimerl.mcsrlauncher.data.meta.mod.SpeedrunModTrait
import com.redlimerl.mcsrlauncher.gui.component.SpeedrunModCheckBox
import com.redlimerl.mcsrlauncher.instance.mod.ModCategory
import com.redlimerl.mcsrlauncher.instance.mod.ModData
import com.redlimerl.mcsrlauncher.launcher.MetaManager
import com.redlimerl.mcsrlauncher.network.FileDownloader
import com.redlimerl.mcsrlauncher.util.AssetUtils
import com.redlimerl.mcsrlauncher.util.I18n
import com.redlimerl.mcsrlauncher.util.LauncherWorker
import com.redlimerl.mcsrlauncher.util.SwingUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Dimension
import java.awt.Window
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.*

class SpeedrunModsBrowseGui(parent: Window, private val instance: BasicInstance) : SpeedrunModsBrowseDialog(parent) {

    private var modsMeta: SpeedrunModsMetaFile? = null
    val loadedMods = arrayListOf<SpeedrunModCheckBox>()

    init {
        title = I18n.translate("text.manage_speedrun_mods")
        minimumSize = Dimension(600, 550)
        setLocationRelativeTo(parent)

        modListPanel.layout = BoxLayout(modListPanel, BoxLayout.Y_AXIS)
        modListPanel.alignmentX = 0f
        SwingUtils.fasterScroll(modListScroll)

        applyButton.isEnabled = false

        ModCategory.entries.forEach { categoryComboBox.addItem(it) }
        categoryComboBox.addActionListener {
            loadMods()
        }

        selectAllButton.addActionListener {
            for (loadedMod in loadedMods.sortedByDescending { it.modMeta.priority }) {
                if (loadedMod.checkBox.isEnabled) loadedMod.checkBox.isSelected = true
            }
        }

        deselectButton.addActionListener {
            for (loadedMod in loadedMods) loadedMod.checkBox.isSelected = false
        }

        accessibilityCheckBox.addActionListener {
            if (accessibilityCheckBox.isSelected) {
                JOptionPane.showMessageDialog(this@SpeedrunModsBrowseGui, I18n.translate("message.warning.include_accessibility_mods"), I18n.translate("text.warning"), JOptionPane.WARNING_MESSAGE)
            }
            loadMods()
        }

        obsoleteCheckBox.addActionListener {
            loadMods()
        }

        applyButton.addActionListener {
            object : LauncherWorker(this@SpeedrunModsBrowseGui, I18n.translate("message.loading"), I18n.translate("text.download.assets").plus("...")) {
                override fun work(dialog: JDialog) {
                    instance.getModsPath().toFile().mkdirs()
                    instance.fabricVersion ?: throw IllegalStateException("This instance does not have Fabric Loader")

                    val installedMods = instance.getMods().toMutableList()

                    if (deleteAllCheckBox.isSelected) {
                        installedMods.forEach { it.delete() }
                        installedMods.clear()
                    }

                    val availableMods = loadedMods.filter { it.checkBox.isSelected }.map { it.modMeta }

                    val total = availableMods.size
                    val completed = AtomicInteger(0)
                    val modList = Collections.synchronizedList(mutableListOf<ModData>())
                    val worker = this

                    AssetUtils.doConcurrency(availableMods) { mod ->
                        val version = mod.versions.find { it.isAvailableVersion(instance) }!!
                        this.setState("Downloading ${mod.name} v${version.version}...")

                        val file = instance.getModsPath().resolve(version.filename).toFile()
                        withContext(Dispatchers.IO) {
                            FileDownloader.download(version.url, file)

                            installedMods.find { it.id == mod.modId && it.version != version.version }?.delete()
                            modList.add(ModData.get(file)!!)

                            val done = completed.incrementAndGet()
                            worker.setProgress(done.toFloat() / total.toFloat())
                        }
                    }

                    this@SpeedrunModsBrowseGui.dispose()
                }
            }.showDialog().start()
        }

        buttonCancel.addActionListener { this.dispose() }

        loadMods()

        I18n.translateGui(this)
        isVisible = true
    }

    private fun loadMods() {
        applyButton.isEnabled = false
        categoryComboBox.isEnabled = false
        accessibilityCheckBox.isEnabled = false
        obsoleteCheckBox.isEnabled = false
        val modCategory = categoryComboBox.getItemAt(categoryComboBox.selectedIndex)
        val accessibility = accessibilityCheckBox.isSelected
        val obsolete = obsoleteCheckBox.isSelected

        object : LauncherWorker(this@SpeedrunModsBrowseGui, I18n.translate("message.loading"), I18n.translate("message.loading") + "...") {
            override fun work(dialog: JDialog) {
                modsMeta = MetaManager.getVersionMeta<SpeedrunModsMetaFile>(MetaUniqueID.SPEEDRUN_MODS, "verified", this) ?: throw IllegalStateException("Speedrun mods meta is not found")

                modListPanel.removeAll()
                loadedMods.clear()
                for (mod in modsMeta!!.mods.filter {
                    it.canDownload(instance, !obsolete) &&
                            it.traits.all { trait ->
                                when (trait) {
                                    SpeedrunModTrait.RSG -> modCategory == ModCategory.RANDOM_SEED
                                    SpeedrunModTrait.SSG -> modCategory == ModCategory.SET_SEED
                                    SpeedrunModTrait.ACCESSIBILITY -> accessibility
                                    else -> true
                                }
                            }
                }) {
                    val modCheckBox = mod.createPanel(instance, !obsolete)
                    modListPanel.add(modCheckBox)
                    modListPanel.add(JSeparator())
                    loadedMods.add(modCheckBox)
                    fun updater() {
                        loadedMods.forEach {
                            it.checkBox.isEnabled = true
                            for (incompatibility in it.modMeta.incompatibilities.filter { loadedMods.find { box -> box.modMeta.modId == it }?.checkBox?.isSelected == true }) {
                                it.checkBox.isEnabled = false
                            }
                        }
                    }
                    modCheckBox.checkBox.addItemListener { updater() }
                    modCheckBox.checkBox.addActionListener { updater() }
                }

                categoryComboBox.isEnabled = true
                applyButton.isEnabled = true
                accessibilityCheckBox.isEnabled = true
                obsoleteCheckBox.isEnabled = true
                SwingUtilities.invokeLater {
                    modListScroll.repaint()
                    modListScroll.revalidate()
                    modListScroll.verticalScrollBar.value = 0
                }
            }
        }.showDialog().start()
    }
}
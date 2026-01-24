package com.redlimerl.mcsrlauncher.gui

import com.redlimerl.mcsrlauncher.data.device.DeviceOSType
import com.redlimerl.mcsrlauncher.data.instance.BasicInstance
import com.redlimerl.mcsrlauncher.data.instance.LWJGLVersionData
import com.redlimerl.mcsrlauncher.data.meta.MetaUniqueID
import com.redlimerl.mcsrlauncher.gui.component.GameVersionsPanel
import com.redlimerl.mcsrlauncher.gui.component.InstanceGroupComboBox
import com.redlimerl.mcsrlauncher.instance.mod.ModCategory
import com.redlimerl.mcsrlauncher.instance.mod.ModDownloadMethod
import com.redlimerl.mcsrlauncher.launcher.InstanceManager
import com.redlimerl.mcsrlauncher.util.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import java.nio.file.Path
import java.util.*
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JOptionPane
import kotlin.io.path.exists
import kotlin.io.path.nameWithoutExtension


class CreateInstanceGui(parent: JFrame) : CreateInstanceDialog(parent) {

    private val gameVersionsPanel: GameVersionsPanel

    init {
        title = I18n.translate("instance.new")
        minimumSize = Dimension(800, 550)
        setLocationRelativeTo(parent)

        instanceNameField.text = I18n.translate("instance.new")
        InstanceGroupComboBox.init(instanceGroupBox)

        cancelButton.addActionListener { this.dispose() }
        createInstanceButton.addActionListener { this.createInstance() }

        this.gameVersionsPanel = GameVersionsPanel(this)
        versionsPanel.layout = BorderLayout()
        versionsPanel.add(this.gameVersionsPanel, BorderLayout.CENTER)

        this.gameVersionsPanel.gameTabPane.addChangeListener {
            if (this.gameVersionsPanel.gameTabPane.selectedIndex == 3) {
                createInstanceButton.text = "Migrate Instance(s)"
                for (listener in createInstanceButton.actionListeners) createInstanceButton.removeActionListener(
                    listener
                )
                createInstanceButton.addActionListener { this.migrateInstance(this.gameVersionsPanel.launcherTabPane.selectedIndex) }
            } else if (this.gameVersionsPanel.gameTabPane.selectedIndex == 4) {
                createInstanceButton.text = "Import Instance"
                for (listener in createInstanceButton.actionListeners) createInstanceButton.removeActionListener(
                    listener
                )
                createInstanceButton.addActionListener { this.importInstance() }
            } else {
                createInstanceButton.text = I18n.translate("instance.create")
                for (listener in createInstanceButton.actionListeners) createInstanceButton.removeActionListener(listener)
                createInstanceButton.addActionListener { this.createInstance() }
            }
        }

        I18n.translateGui(this)
        isVisible = true
    }

    private fun createInstance() {
        if (instanceNameField.text.isNullOrBlank()) return

        val instance = InstanceManager.createInstance(instanceNameField.text, instanceGroupBox.selectedItem?.toString()?.trimEnd(), gameVersionsPanel.getMinecraftVersion().version, gameVersionsPanel.getLWJGLVersion(), gameVersionsPanel.getFabricVersion(), gameVersionsPanel.getMCSRRankedPackType())
        val mcsrRankedPackType = instance.mcsrRankedType

        this.dispose()
        val launch = {
            val launchConfirm = JOptionPane.showConfirmDialog(this, I18n.translate("message.instance_launch_ask"), I18n.translate("instance.launch"), JOptionPane.YES_NO_OPTION)
            if (launchConfirm == JOptionPane.YES_OPTION) {
                instance.launchWithDialog()
            }
        }

        if (mcsrRankedPackType != null) {
            object : LauncherWorker(this@CreateInstanceGui, I18n.translate("message.loading"), I18n.translate("text.download.assets").plus("...")) {
                override fun work(dialog: JDialog) {
                    SpeedrunUtils.getLatestMCSRRankedVersion(this)?.download(instance, this)
                    if (mcsrRankedPackType.versionName != null) {
                        instance.installRecommendedSpeedrunMods(this, mcsrRankedPackType.versionName, ModCategory.RANDOM_SEED, ModDownloadMethod.DOWNLOAD_RECOMMENDS, false)
                    }
                    instance.options.autoModUpdates = true
                    instance.save()
                    launch()
                }

                override fun onError(e: Throwable) {
                    launch()
                }
            }.showDialog().start()
            return
        } else if (instance.fabricVersion != null) {
            val modInit = JOptionPane.showConfirmDialog(this, I18n.translate("message.speedrun_mods_setup_ask"), I18n.translate("text.manage_speedrun_mods"), JOptionPane.YES_NO_OPTION)
            if (modInit == JOptionPane.YES_OPTION) {
                SpeedrunModsManageGui(this, instance, true) {
                    val autoUpdate = JOptionPane.showConfirmDialog(this, I18n.translate("message.auto_mod_update_ask"), I18n.translate("text.manage_speedrun_mods"), JOptionPane.YES_NO_OPTION)
                    if (autoUpdate == JOptionPane.YES_OPTION) {
                        instance.options.autoModUpdates = true
                        instance.save()
                    }
                    launch()
                }
                return
            }
        }
        launch()
    }

    private fun migrateInstance(launcher: Int) {
        val model = when (launcher) {
            0 -> this.gameVersionsPanel.mmcInstanceTable.model
            1 -> this.gameVersionsPanel.prismInstanceTable.model
            else -> null
        } ?: return

        val selectedInstances = (0 until model.rowCount)
            .mapNotNull { row ->
                val selected = model.getValueAt(row, 0) as? Boolean ?: false
                if (selected) {
                    val instName = model.getValueAt(row, 1)
                    instName as? String
                } else null
            }

        selectedInstances.forEach { instName ->
            val folderPath = this.gameVersionsPanel.launcherPath?.resolve("instances")?.resolve(instName)
            val newInstFolder = InstanceManager.INSTANCES_PATH.resolve(InstanceManager.getNewInstanceName(instName))
            val prevMCFolder = when {
                (folderPath?.resolve(".minecraft")?.exists() == true) && (!folderPath.resolve("minecraft").exists()) -> ".minecraft"
                (folderPath?.resolve("minecraft")?.exists() == true) && (!folderPath.resolve(".minecraft").exists()) -> "minecraft"
                else -> ".minecraft"
            }
            val newMCFolder = when (OSUtils.getOSType()) {
                DeviceOSType.WINDOWS -> ".minecraft"
                else -> "minecraft"
            }
            object : LauncherWorker(parent, I18n.translate("message.loading"), I18n.translate("message.migrating")) {
                override fun work(dialog: JDialog) {
                    newInstFolder.toFile().mkdirs()
                    folderPath?.resolve(prevMCFolder)?.toFile()?.copyRecursively(newInstFolder.resolve(newMCFolder).toFile())
                    val cfg = folderPath?.resolve("instance.cfg")?.toFile()?.let { MigrationUtils.cfgReader(it) }
                    val mmcPack = folderPath?.resolve("mmc-pack.json")?.toFile()?.let { MigrationUtils.mmcPackReader(it.readText()) }
                    val lwjglPatch = folderPath?.resolve(".minecraft")?.resolve("patches")?.resolve("org.lwjgl3.json")?.toFile()
                    var lwjglVerData = LWJGLVersionData(MetaUniqueID.LWJGL3, "3.2.2")
                    if (lwjglPatch?.exists() == true) {
                        lwjglVerData = MigrationUtils.getLWJGL(lwjglPatch.readText())!!
                    }
                    val fabricVerData = MigrationUtils.getFabricVersion(mmcPack)

                    val instance = InstanceManager.createInstance(
                        instName,
                        null,
                        MigrationUtils.getMinecraftVersion(mmcPack),
                        lwjglVerData,
                        fabricVerData,
                        null
                    )

                    if (cfg != null) this@CreateInstanceGui.applyCfgProperties(instance, cfg)
                    this@CreateInstanceGui.dispose()

                    if (fabricVerData != null) {
                        val autoUpdate = JOptionPane.showConfirmDialog(
                            this@CreateInstanceGui,
                            I18n.translate("message.auto_mod_update_ask"),
                            I18n.translate("text.manage_speedrun_mods"),
                            JOptionPane.YES_NO_OPTION
                        )
                        if (autoUpdate == JOptionPane.YES_OPTION) {
                            instance.options.autoModUpdates = true
                            instance.save()
                        }
                    }
                }
            }.showDialog().start()
        }

    }

    private fun importInstance() {
        val zipPath = this.gameVersionsPanel.zipPathField.text

        if (!File(zipPath).exists()) return

        val instName = Path.of(zipPath).nameWithoutExtension
        val newInstFolder = InstanceManager.INSTANCES_PATH.resolve(InstanceManager.getNewInstanceName(instName))

        object : LauncherWorker(parent, I18n.translate("message.loading"), I18n.translate("message.importing")) {
            override fun work(dialog: JDialog) {
                MigrationUtils.importMinecraft(zipPath, newInstFolder.toString())
                val cfg = MigrationUtils.extractCfg(zipPath)
                val mmcPack = MigrationUtils.extractMMCPack(zipPath)
                val lwjglVerData = MigrationUtils.getZIPLWJGL(zipPath)!!

                val fabricVerData = MigrationUtils.getFabricVersion(mmcPack)

                val instance = InstanceManager.createInstance(
                    instName,
                    null,
                    MigrationUtils.getMinecraftVersion(mmcPack),
                    lwjglVerData,
                    fabricVerData,
                    null
                )

                this@CreateInstanceGui.applyCfgProperties(instance, cfg)
                this@CreateInstanceGui.dispose()

                if (fabricVerData != null) {
                    val autoUpdate = JOptionPane.showConfirmDialog(this@CreateInstanceGui, I18n.translate("message.auto_mod_update_ask"), I18n.translate("text.manage_speedrun_mods"), JOptionPane.YES_NO_OPTION)
                    if (autoUpdate == JOptionPane.YES_OPTION) {
                        instance.options.autoModUpdates = true
                        instance.save()
                    }
                }
            }
        }.showDialog().start()
    }

    private fun applyCfgProperties(instance: BasicInstance, cfg: Properties) {
        cfg.getProperty("OverrideWindow")?.toBoolean()?.let { instance.options.useLauncherResolutionOption = it }
        cfg.getProperty("OverrideJavaArgs")?.toBoolean()?.let { instance.options.useLauncherJavaOption = it }
        cfg.getProperty("JavaPath")?.toString()?.let { instance.options.javaPath = it }
        cfg.getProperty("JvmArgs")?.toString()?.let { instance.options.jvmArguments = it }
        cfg.getProperty("MaxMemAlloc")?.toInt()?.let { instance.options.maxMemory = it }
        cfg.getProperty("MinMemAlloc")?.toInt()?.let { instance.options.maxMemory = it }
    }

}
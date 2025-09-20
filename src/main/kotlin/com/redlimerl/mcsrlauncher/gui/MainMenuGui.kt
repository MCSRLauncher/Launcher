package com.redlimerl.mcsrlauncher.gui

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.data.MicrosoftAccount
import com.redlimerl.mcsrlauncher.gui.component.InstanceLaunchButton
import com.redlimerl.mcsrlauncher.gui.layout.WrapLayout
import com.redlimerl.mcsrlauncher.gui.listener.MouseFillListener
import com.redlimerl.mcsrlauncher.launcher.AccountManager
import com.redlimerl.mcsrlauncher.launcher.InstanceManager
import com.redlimerl.mcsrlauncher.util.I18n
import com.redlimerl.mcsrlauncher.util.LauncherWorker
import com.redlimerl.mcsrlauncher.util.OSUtils
import com.redlimerl.mcsrlauncher.util.SwingUtils
import net.miginfocom.swing.MigLayout
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.net.URI
import javax.swing.*

class MainMenuGui : MainForm() {

    private var optionDialog: LauncherOptionDialog? = null

    init {
        title = "${MCSRLauncher.APP_NAME} " + if (MCSRLauncher.IS_DEV_VERSION) "Developement Ver." else ("v" + MCSRLauncher.APP_VERSION)
        defaultCloseOperation = EXIT_ON_CLOSE
        minimumSize = Dimension(900, 600)
        setLocationRelativeTo(null)
        javaClass.classLoader.getResource("icons/launcher/icon.png")?.let { iconImage = ImageIcon(it).image }

        initLauncherMenu()
        initInstanceList()
        initInstanceOption()

        contentPane = mainPanel

        I18n.translateGui(this)
        isVisible = true
    }

    private fun initLauncherMenu() {
        fun refreshAccountButtonIcon(newAccount: MicrosoftAccount?) {
            if (newAccount != null) {
                object : LauncherWorker() {
                    override fun work(dialog: JDialog) {
                        accountButton.icon = AccountManager.getSkinHead(newAccount, 24)
                    }
                }.start()
            } else {
                accountButton.icon = ImageIcon(ImageIcon(javaClass.getResource("/icons/steve.png")).image.getScaledInstance(24, 24, Image.SCALE_SMOOTH))
            }
        }

        initHeaderButton(accountButton)
        refreshAccountButtonIcon(AccountManager.getActiveAccount())
        accountButton.addActionListener {
            val currentAccount = AccountManager.getActiveAccount()
            AccountListGui(this)
            val newAccount = AccountManager.getActiveAccount()
            if (currentAccount != newAccount) refreshAccountButtonIcon(newAccount)
        }

        initHeaderButton(createInstanceButton)
        createInstanceButton.addActionListener {
            CreateInstanceGui(this)
        }

        initHeaderButton(settingsButton)
        settingsButton.addActionListener {
            if (optionDialog != null) {
                optionDialog!!.requestFocus()
            } else {
                optionDialog = LauncherOptionGui(this) {
                    optionDialog = null
                }
            }
        }

        initHeaderButton(discordButton)
        discordButton.addActionListener {
            OSUtils.openURI(URI.create("https://mcsrlauncher.github.io/discord"))
        }

        initHeaderButton(patreonButton)
        patreonButton.addActionListener {
            OSUtils.openURI(URI.create("https://mcsrlauncher.github.io/patreon"))
        }

        initHeaderButton(githubButton)
        githubButton.addActionListener {
            OSUtils.openURI(URI.create("https://github.com/MCSRLauncher/Launcher"))
        }
    }

    private fun initHeaderButton(button: JButton) {
        button.icon = ImageIcon((button.icon as? ImageIcon)?.image?.getScaledInstance(24, 24, Image.SCALE_SMOOTH))
        button.addMouseListener(MouseFillListener(button))
    }

    private fun initInstanceList() {
        instanceScrollPane.border = BorderFactory.createEmptyBorder()
        SwingUtils.fasterScroll(instanceScrollPane)
        loadInstanceList()
        this.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) {
                instanceScrollPane.viewport.view.revalidate()
                instanceScrollPane.viewport.view.repaint()
            }
        })
    }

    private fun initInstanceOption() {
//        bottomField.isVisible = false
    }

    fun loadInstanceList() {
        val containerPanel = JPanel(MigLayout("wrap 1", "[grow, fill]", ""))

        for (group in InstanceManager.instances.keys) {
            createInstanceGroup(containerPanel, group)
            containerPanel.add(JSeparator())
        }
        containerPanel.revalidate()
        containerPanel.repaint()
        instanceScrollPane.viewport.view = containerPanel
    }

    private fun createInstanceGroup(panel: JPanel, group: String) {
        val groupTitle = JLabel(group)
        groupTitle.border = BorderFactory.createEmptyBorder(0, 10, 0, 0)
        groupTitle.font = groupTitle.font.deriveFont(Font.BOLD, groupTitle.font.size2D + 6F)
        panel.add(groupTitle)

        val groupPanel = JPanel(WrapLayout(FlowLayout.LEFT, 10, 10))
        for (basicInstance in InstanceManager.instances[group]!!) {
            groupPanel.add(InstanceLaunchButton(this, basicInstance), "gap 10")
        }
        panel.add(groupPanel)
    }

}
package com.redlimerl.mcsrlauncher.gui

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.auth.MCTokenReceiverAuth
import com.redlimerl.mcsrlauncher.auth.MSDeviceCodeAuth
import com.redlimerl.mcsrlauncher.auth.MSTokenReceiverAuth
import com.redlimerl.mcsrlauncher.auth.XBLTokenReceiverAuth
import com.redlimerl.mcsrlauncher.data.MicrosoftAccount
import com.redlimerl.mcsrlauncher.gui.component.AccountListComponent
import com.redlimerl.mcsrlauncher.launcher.AccountManager
import com.redlimerl.mcsrlauncher.util.I18n
import com.redlimerl.mcsrlauncher.util.LauncherWorker
import com.redlimerl.mcsrlauncher.util.OSUtils
import com.redlimerl.mcsrlauncher.util.SwingUtils
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.net.URI
import javax.swing.*


class AccountListGui(parent: JFrame) : AccountListDialog() {

    private val accountList = AccountListComponent()

    init {
        title = I18n.translate("account.accounts")
        minimumSize = Dimension(600, 400)
        setLocationRelativeTo(parent)

        addAccountButton.addActionListener { addNewAccount() }

        SwingUtils.fasterScroll(accountListPane)
        accountListPane.setViewportView(accountList)

        closeButton.addActionListener { this.dispose() }

        I18n.translateGui(this)
        isVisible = true
    }

    private fun addNewAccount() {
        object : LauncherWorker(this@AccountListGui, I18n.translate("account.authentication"), I18n.translate("loading") + "...") {
            override fun work(dialog: JDialog) {
                val deviceCode = MSDeviceCodeAuth.create(this)
                this.setState("<html>${I18n.translate("message.login_device_code", deviceCode.userCode)}</html>", false)

                val openPageButton = JButton(I18n.translate("text.copy.code_and_open_page"))
                val cancelButton = JButton(I18n.translate("text.cancel"))

                openPageButton.addActionListener {
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(deviceCode.userCode), null)
                    OSUtils.openURI(URI.create(deviceCode.verificationUrl))
                }
                cancelButton.addActionListener { this.dialog.dispose() }

                val buttons = JPanel()
                buttons.add(openPageButton, BorderLayout.EAST)
                buttons.add(cancelButton, BorderLayout.WEST)

                val worker = this
                worker.dialog.defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
                worker.addBottomPanel(buttons)
                worker.dialog.addWindowListener(object : WindowAdapter() {
                    override fun windowClosed(e: WindowEvent?) {
                        worker.cancelWork()
                    }
                })

                val tokenTime = System.currentTimeMillis()
                val msToken = try {
                    MSTokenReceiverAuth.create(this, deviceCode)
                } catch (e: InterruptedException) {
                    MCSRLauncher.LOGGER.info("Cancelled adding Microsoft account")
                    return
                }
                openPageButton.isEnabled = false
                cancelButton.isEnabled = false

                val xboxUserToken = XBLTokenReceiverAuth.createUserToken(this, msToken.accessToken)
                val xboxXSTSToken = XBLTokenReceiverAuth.createXSTSToken(this, xboxUserToken)
                val mcToken = MCTokenReceiverAuth.create(this, xboxXSTSToken)

                mcToken.checkOwnership(this)
                val mcProfile = mcToken.getProfile(this)

                val account = MicrosoftAccount(mcProfile, msToken.accessToken, msToken.refreshToken, tokenTime + (1000 * msToken.expires))
                account.profile.refreshToken(mcToken)

                AccountManager.addAccount(account)
                SwingUtilities.invokeLater { accountList.loadAll() }
                MCSRLauncher.LOGGER.info("Added Microsoft account: {}", account.profile.nickname)
            }
        }.indeterminate().showDialog().start()
    }

}
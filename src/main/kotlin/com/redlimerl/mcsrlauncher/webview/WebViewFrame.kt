package com.redlimerl.mcsrlauncher.webview

import com.redlimerl.mcsrlauncher.MCSRLauncher
import org.apache.logging.log4j.LogManager
import org.cef.browser.CefBrowser
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Image
import java.awt.event.KeyEvent
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.ImageIcon
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.KeyStroke
import javax.swing.SwingUtilities

class WebViewFrame(
    private val startUrl: String,
    title: String = MCSRLauncher.APP_NAME
) : JFrame() {

    private val logger = LogManager.getLogger("WebViewFrame")
    private var browser: CefBrowser? = null

    init {
        this.title = title
        defaultCloseOperation = DO_NOTHING_ON_CLOSE
        minimumSize = Dimension(900, 600)
        preferredSize = Dimension(1200, 800)
        setLocationRelativeTo(null)

        javaClass.classLoader.getResource("icons/launcher/icon.png")?.let {
            iconImage = ImageIcon(it).image
        }

        addWindowListener(object : WindowAdapter() {
            override fun windowClosing(e: WindowEvent) {
                handleClose()
            }
        })
        rootPane.registerKeyboardAction(
            { openDevTools() },
            KeyStroke.getKeyStroke(KeyEvent.VK_F12, 0),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        )
        rootPane.registerKeyboardAction(
            { openDevTools() },
            KeyStroke.getKeyStroke(KeyEvent.VK_I, KeyEvent.CTRL_DOWN_MASK or KeyEvent.SHIFT_DOWN_MASK),
            JComponent.WHEN_IN_FOCUSED_WINDOW
        )

        layout = BorderLayout()
    }
    fun initBrowser() {
        logger.info("Creating browser for URL: $startUrl")

        val client = WebViewManager.getClient()

        client.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onTitleChange(browser: CefBrowser?, title: String?) {
                SwingUtilities.invokeLater {
                    if (title != null && title.isNotBlank()) {
                        this@WebViewFrame.title = "$title - ${MCSRLauncher.APP_NAME}"
                    }
                }
            }
        })

        client.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadStart(
                browser: CefBrowser?,
                frame: org.cef.browser.CefFrame?,
                transitionType: org.cef.network.CefRequest.TransitionType?
            ) {
                if (frame?.isMain == true) {
                    logger.debug("Page load started: ${browser?.url}")
                }
            }

            override fun onLoadEnd(browser: CefBrowser?, frame: org.cef.browser.CefFrame?, httpStatusCode: Int) {
                if (frame?.isMain == true) {
                    logger.info("Page load complete: ${browser?.url} (status: $httpStatusCode)")
                }
            }

            override fun onLoadError(
                browser: CefBrowser?,
                frame: org.cef.browser.CefFrame?,
                errorCode: org.cef.handler.CefLoadHandler.ErrorCode?,
                errorText: String?,
                failedUrl: String?
            ) {
                if (frame?.isMain == true) {
                    logger.error("Page load error: $failedUrl - $errorText ($errorCode)")

                    val errorHtml = """
                        <html>
                        <head>
                            <style>
                                body {
                                    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
                                    background: #1a1a2e;
                                    color: #eee;
                                    display: flex;
                                    align-items: center;
                                    justify-content: center;
                                    height: 100vh;
                                    margin: 0;
                                }
                                .error-container {
                                    text-align: center;
                                    padding: 40px;
                                }
                                h1 { color: #ff6b6b; margin-bottom: 20px; }
                                p { color: #aaa; }
                                .url { font-family: monospace; background: #2a2a3e; padding: 10px; border-radius: 4px; margin: 20px 0; }
                                button {
                                    background: #4a9eff;
                                    border: none;
                                    color: white;
                                    padding: 12px 24px;
                                    border-radius: 6px;
                                    cursor: pointer;
                                    font-size: 14px;
                                    margin-top: 20px;
                                }
                                button:hover { background: #3a8eef; }
                            </style>
                        </head>
                        <body>
                            <div class="error-container">
                                <h1>Failed to Load</h1>
                                <p>Could not load the launcher interface.</p>
                                <div class="url">$failedUrl</div>
                                <p>Error: $errorText</p>
                                <button onclick="location.reload()">Retry</button>
                            </div>
                        </body>
                        </html>
                    """.trimIndent()
                    browser?.loadURL("data:text/html;base64,${java.util.Base64.getEncoder().encodeToString(errorHtml.toByteArray())}")
                }
            }
        })

        browser = client.createBrowser(startUrl, false, false)
        contentPane.add(browser!!.uiComponent, BorderLayout.CENTER)

        WebViewManager.registerBrowser(browser!!)

        pack()
    }

    fun getBrowser(): CefBrowser? = browser

    fun loadUrl(url: String) {
        browser?.loadURL(url)
    }

    fun reload() {
        browser?.reload()
    }

    fun executeJS(script: String) {
        browser?.let { WebViewManager.executeJS(it, script) }
    }

    fun openDevTools() {
        browser?.let { b ->
            val devTools = b.devTools
            val devToolsFrame = JFrame("DevTools - ${MCSRLauncher.APP_NAME}")
            devToolsFrame.contentPane.add(devTools.uiComponent, BorderLayout.CENTER)
            devToolsFrame.setSize(800, 600)
            devToolsFrame.setLocationRelativeTo(this)
            devToolsFrame.isVisible = true
        }
    }

    private fun handleClose() {
        logger.info("Window close requested")

        if (MCSRLauncher.GAME_PROCESSES.any { it.process?.isAlive == true }) {
            val result = javax.swing.JOptionPane.showConfirmDialog(
                this,
                "Games are still running. Are you sure you want to close the launcher?",
                "Confirm Exit",
                javax.swing.JOptionPane.YES_NO_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE
            )
            if (result != javax.swing.JOptionPane.YES_OPTION) {
                return
            }
        }

        browser?.close(true)
        browser = null

        dispose()

        WebViewManager.shutdown()

        System.exit(0)
    }
}
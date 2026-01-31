package com.redlimerl.mcsrlauncher.webview

import com.redlimerl.mcsrlauncher.MCSRLauncher
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.serializer
import me.friwi.jcefmaven.CefAppBuilder
import me.friwi.jcefmaven.CefInitializationException
import me.friwi.jcefmaven.EnumProgress
import me.friwi.jcefmaven.MavenCefAppHandlerAdapter
import me.friwi.jcefmaven.UnsupportedPlatformException
import org.apache.logging.log4j.LogManager
import org.cef.CefApp
import org.cef.CefClient
import org.cef.CefSettings
import org.cef.browser.CefBrowser
import org.cef.browser.CefMessageRouter
import org.cef.callback.CefQueryCallback
import org.cef.handler.CefMessageRouterHandlerAdapter
import java.io.IOException
import java.nio.file.Path

object WebViewManager {
    private val LOGGER = LogManager.getLogger("WebViewManager")

    private var cefApp: CefApp? = null
    private var cefClient: CefClient? = null
    private var messageRouter: CefMessageRouter? = null
    private var initialized = false
    private var bridgeHandler: ((String, CefQueryCallback) -> Unit)? = null
    private var activeBrowser: CefBrowser? = null
    @Throws(UnsupportedPlatformException::class, CefInitializationException::class, IOException::class, InterruptedException::class)
    fun initialize(installDir: Path, progressHandler: ((String, Int) -> Unit)? = null) {
        if (initialized) {
            LOGGER.warn("WebViewManager already initialized")
            return
        }

        LOGGER.info("Initializing JCEF...")

        val builder = CefAppBuilder()

        builder.setInstallDir(installDir.toFile())

        builder.setAppHandler(object : MavenCefAppHandlerAdapter() {
            override fun stateHasChanged(state: org.cef.CefApp.CefAppState) {
                LOGGER.info("JCEF state changed: $state")
            }
        })

        builder.setProgressHandler { state, percent ->
            val message = when (state) {
                EnumProgress.LOCATING -> "Locating JCEF installation..."
                EnumProgress.DOWNLOADING -> "Downloading Chromium ($percent%)..."
                EnumProgress.EXTRACTING -> "Extracting Chromium ($percent%)..."
                EnumProgress.INITIALIZING -> "Initializing browser engine..."
                EnumProgress.INITIALIZED -> "Browser engine ready"
                else -> "Preparing browser..."
            }
            LOGGER.info(message)
            progressHandler?.invoke(message, percent.toInt())
        }

        builder.cefSettings.apply {
            windowless_rendering_enabled = false
            log_severity = CefSettings.LogSeverity.LOGSEVERITY_WARNING
        }
        builder.addJcefArgs("--disable-gpu-shader-disk-cache")
        builder.addJcefArgs("--disable-software-rasterizer")

        builder.addJcefArgs("--force-device-scale-factor=1")
        builder.addJcefArgs("--disable-gpu-compositing")

        cefApp = builder.build()
        cefClient = cefApp!!.createClient()
        val routerConfig = CefMessageRouter.CefMessageRouterConfig().apply {
            jsQueryFunction = "cefQuery"
            jsCancelFunction = "cefQueryCancel"
        }
        messageRouter = CefMessageRouter.create(routerConfig)
        messageRouter!!.addHandler(object : CefMessageRouterHandlerAdapter() {
            override fun onQuery(
                browser: CefBrowser?,
                frame: org.cef.browser.CefFrame?,
                queryId: Long,
                request: String?,
                persistent: Boolean,
                callback: CefQueryCallback?
            ): Boolean {
                if (request != null && callback != null) {
                    LOGGER.debug("Bridge request: $request")
                    bridgeHandler?.invoke(request, callback)
                    return true
                }
                return false
            }
        }, true)

        cefClient!!.addMessageRouter(messageRouter)

        initialized = true
        LOGGER.info("JCEF initialized successfully")
    }
    fun setBridgeHandler(handler: (request: String, callback: CefQueryCallback) -> Unit) {
        bridgeHandler = handler
    }
    fun getClient(): CefClient {
        check(initialized) { "WebViewManager not initialized. Call initialize() first." }
        return cefClient!!
    }
    fun getApp(): CefApp {
        check(initialized) { "WebViewManager not initialized. Call initialize() first." }
        return cefApp!!
    }
    fun isInitialized(): Boolean = initialized
    fun executeJS(browser: CefBrowser, script: String) {
        browser.executeJavaScript(script, browser.url, 0)
    }
    fun pushEvent(browser: CefBrowser, event: String, data: String) {
        val script = "window.launcher && window.launcher._handleEvent('$event', $data);"
        executeJS(browser, script)
    }

    fun registerBrowser(browser: CefBrowser) {
        activeBrowser = browser
    }

    fun broadcastEvent(event: String, data: JsonElement) {
        val browser = activeBrowser
        if (browser != null) {
            val jsonData = MCSRLauncher.JSON.encodeToString(
                JsonElement.serializer(),
                data
            )
            pushEvent(browser, event, jsonData)
        } else {
            LOGGER.warn("No active browser to broadcast event: $event")
        }
    }

    fun broadcastEvent(event: String, data: Any?) {
        val element = when (data) {
            null -> JsonNull
            is JsonElement -> data
            else -> MCSRLauncher.JSON.encodeToJsonElement(
                serializer(data.javaClass),
                data
            )
        }
        broadcastEvent(event, element)
    }

    fun shutdown() {
        if (!initialized) return

        LOGGER.info("Shutting down JCEF...")
        messageRouter?.dispose()
        cefClient?.dispose()
        cefApp?.dispose()

        messageRouter = null
        cefClient = null
        cefApp = null
        initialized = false

        LOGGER.info("JCEF shutdown complete")
    }
}
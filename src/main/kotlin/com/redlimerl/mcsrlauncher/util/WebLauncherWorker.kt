package com.redlimerl.mcsrlauncher.util

import com.redlimerl.mcsrlauncher.webview.WebViewManager
import kotlinx.serialization.Serializable
import javax.swing.JDialog

@Serializable
data class WorkerProgressEvent(
    val title: String?,
    val description: String?,
    val progress: Double?
)

@Serializable
data class WorkerErrorEvent(
    val message: String?
)

open class WebLauncherWorker(
    private val title: String,
    private val description: String
) : LauncherWorker(null, title, description) {

    private var currentDescription: String? = description
    private var currentProgress: Double? = null

    override fun work(dialog: JDialog) {
    }

    override fun onError(e: Throwable) {
        super.onError(e)
        WebViewManager.broadcastEvent("worker.error", WorkerErrorEvent(e.message))
    }

    override fun setState(string: String?, log: Boolean): LauncherWorker {
        super.setState(string, log)
        currentDescription = string
        broadcastUpdate()
        return this
    }

    override fun setSubText(string: String?): LauncherWorker {
        return super.setSubText(string)
    }

    override fun setProgress(value: Double?): LauncherWorker {
        super.setProgress(value)
        currentProgress = value
        broadcastUpdate()
        return this
    }

    private fun broadcastUpdate() {
        WebViewManager.broadcastEvent(
            "worker.progress",
            WorkerProgressEvent(title, currentDescription, currentProgress)
        )
    }

    fun finish() {
        WebViewManager.broadcastEvent("worker.progress", WorkerProgressEvent(null, null, null))
    }
}

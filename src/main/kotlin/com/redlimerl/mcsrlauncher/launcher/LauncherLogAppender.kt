package com.redlimerl.mcsrlauncher.launcher

import com.redlimerl.mcsrlauncher.gui.component.LogViewerPanel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import org.apache.logging.log4j.core.layout.PatternLayout
import java.util.*
import javax.swing.SwingUtilities

private const val MAX_LOG_ARCHIVE = 5000

class LauncherLogAppender(private val layout: PatternLayout)
    : AbstractAppender("Appender", null, layout, false, Property.EMPTY_ARRAY) {

    private var logArchive: MutableList<String> = LinkedList()
    private var logChannel = Channel<String>(10000)
    private var viewerUpdater: Job? = null

    override fun append(event: LogEvent?) {
        val msg = layout.toSerializable(event) ?: return
        logChannel.trySend(msg)
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun syncLogViewer(logViewer: LogViewerPanel) {
        viewerUpdater?.cancel()

        viewerUpdater = GlobalScope.launch {
            SwingUtilities.invokeLater {
                logViewer.clearLogs()
                logViewer.addLogs(logArchive.filter { !it.contains("[DEBUG]") || logViewer.enabledDebug() })
            }

            while (isActive) {
                val logsToProcess = mutableListOf<String>()
                try {
                    logsToProcess.add(logChannel.receive())
                    while (true) {
                        val log = logChannel.tryReceive().getOrNull() ?: break
                        logsToProcess.add(log)
                    }
                } catch (e: CancellationException) {
                    return@launch
                }


                if (logsToProcess.isNotEmpty()) {
                    SwingUtilities.invokeLater {
                        if (!logViewer.displayLiveLog) return@invokeLater

                        val filteredLogs = logsToProcess.filter { !it.contains("[DEBUG]") || logViewer.enabledDebug() }
                        logViewer.addLogs(filteredLogs)
                        logViewer.onLiveUpdate()

                        synchronized(logArchive) {
                            logArchive.addAll(logsToProcess)
                            while (logArchive.size > MAX_LOG_ARCHIVE) {
                                logArchive.removeAt(0)
                            }
                        }
                    }
                }
                delay(100)
            }
        }
    }
}
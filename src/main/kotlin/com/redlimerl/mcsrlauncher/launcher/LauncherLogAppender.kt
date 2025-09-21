package com.redlimerl.mcsrlauncher.launcher

import com.redlimerl.mcsrlauncher.gui.component.LogViewerPanel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.Property
import org.apache.logging.log4j.core.layout.PatternLayout
import javax.swing.SwingUtilities

class LauncherLogAppender(private val layout: PatternLayout)
    : AbstractAppender("Appender", null, layout, false, Property.EMPTY_ARRAY) {

    private var logArchive = StringBuilder()
    private var logChannel = Channel<String>(Channel.UNLIMITED)
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
                logArchive.lines().forEach {
                    if (!it.contains("[DEBUG]") || logViewer.enabledDebug())
                        logViewer.appendString(logViewer.liveLogPane, it)
                }
            }
            for (line in logChannel) {
                SwingUtilities.invokeLater {
                    if (!line.contains("[DEBUG]") || logViewer.enabledDebug()) {
                        logViewer.appendString(logViewer.liveLogPane, line)
                        logViewer.onLiveUpdate()
                    }
                    logArchive.append(line)
                }
            }
        }
    }
}
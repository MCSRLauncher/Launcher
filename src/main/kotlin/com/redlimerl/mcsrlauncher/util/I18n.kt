package com.redlimerl.mcsrlauncher.util

import com.redlimerl.mcsrlauncher.MCSRLauncher
import java.awt.Container
import java.io.InputStream
import java.net.URL
import java.net.URLConnection
import java.util.*
import javax.swing.*
import javax.swing.border.TitledBorder


object I18n {

    private val resource = ResourceBundle.getBundle("lang/I18n", getLocale(), UTF8Control())

    init {
        MCSRLauncher.LOGGER.info("Loading languages - ${MCSRLauncher.options.language.languageCode}")
        MCSRLauncher.LOGGER.info(translate("text.settings"))
    }

    private fun getLocale(): Locale {
        val localeSplit = MCSRLauncher.options.language.languageCode.split("_")
        return Locale(localeSplit[0], localeSplit[1])
    }

    fun translate(key: String, vararg args: Any): String {
        return String.format(if (resource.containsKey(key)) resource.getString(key) else key, *args)
    }

    fun translateGui(root: Container) {
        for (component in root.components) {
            when (component) {
                is JLabel -> component.text = translate(component.text)
                is JButton -> component.text = translate(component.text)
                is JCheckBox -> component.text = translate(component.text)
                is JRadioButton -> component.text = translate(component.text)
                is JTabbedPane -> {
                    for (i in 0 until component.tabCount) {
                        val title = component.getTitleAt(i)
                        if (title != null) {
                            component.setTitleAt(i, translate(title))
                        }
                        val tooltip = component.getToolTipTextAt(i)
                        if (tooltip != null) {
                            component.setToolTipTextAt(i, translate(tooltip))
                        }
                    }
                }
            }

            if (component is JComponent) {
                val border = component.border
                if (border is TitledBorder) {
                    border.title = border.title?.let { translate(it) }
                }
            }

            if (component is Container) {
                translateGui(component)
            }
        }
    }

    class UTF8Control : ResourceBundle.Control() {
        override fun newBundle(baseName: String, locale: Locale, format: String, loader: ClassLoader, reload: Boolean): ResourceBundle? {
            val bundleName = toBundleName(baseName, locale)
            val resourceName = toResourceName(bundleName, "properties")
            var bundle: ResourceBundle? = null
            var stream: InputStream? = null
            if (reload) {
                val url: URL? = loader.getResource(resourceName)
                if (url != null) {
                    val connection: URLConnection = url.openConnection()
                    connection.useCaches = false
                    stream = connection.getInputStream()
                }
            } else {
                stream = loader.getResourceAsStream(resourceName)
            }
            if (stream != null) {
                try {
                    bundle = PropertyResourceBundle(stream.reader(Charsets.UTF_8))
                } finally {
                    stream.close()
                }
            }
            return bundle
        }
    }

}
package com.redlimerl.mcsrlauncher.bridge.api

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.data.launcher.LauncherOptions
import com.redlimerl.mcsrlauncher.data.launcher.LauncherLanguage
import kotlinx.serialization.json.*
import org.apache.logging.log4j.LogManager

class LauncherApi {
    private val logger = LogManager.getLogger("LauncherApi")

    suspend fun handle(method: String, params: JsonObject): JsonElement? {
        return when (method) {
            "launcher.getOptions" -> getOptions()
            "launcher.updateOptions" -> updateOptions(params)
            "launcher.getInfo" -> getInfo()
            "launcher.openDevTools" -> openDevTools()
            else -> throw IllegalArgumentException("Unknown launcher method: $method")
        }
    }

    private fun getOptions(): JsonElement {
        val options = MCSRLauncher.options
        return buildJsonObject {
            put("language", options.language.languageCode)
            put("metaUrl", options.metaUrl)
            put("concurrentDownloads", options.concurrentDownloads)
            put("javaPath", options.javaPath)
            put("jvmArguments", options.jvmArguments)
            put("minMemory", options.minMemory)
            put("maxMemory", options.maxMemory)
            put("maximumResolution", options.maximumResolution)
            put("resolutionWidth", options.resolutionWidth)
            put("resolutionHeight", options.resolutionHeight)
            putJsonArray("customJavaPaths") {
                options.customJavaPaths.forEach { add(it) }
            }
        }
    }

    private fun updateOptions(params: JsonObject): JsonElement? {
        val options = MCSRLauncher.options

        params["language"]?.jsonPrimitive?.contentOrNull?.let { options.language = parseLanguage(it) }
        params["metaUrl"]?.jsonPrimitive?.contentOrNull?.let { options.metaUrl = it }
        params["concurrentDownloads"]?.jsonPrimitive?.intOrNull?.let { options.concurrentDownloads = it }
        params["javaPath"]?.jsonPrimitive?.contentOrNull?.let { options.javaPath = it }
        params["jvmArguments"]?.jsonPrimitive?.contentOrNull?.let { options.jvmArguments = it }
        params["minMemory"]?.jsonPrimitive?.intOrNull?.let { options.minMemory = it }
        params["maxMemory"]?.jsonPrimitive?.intOrNull?.let { options.maxMemory = it }
        params["maximumResolution"]?.jsonPrimitive?.booleanOrNull?.let { options.maximumResolution = it }
        params["resolutionWidth"]?.jsonPrimitive?.intOrNull?.let { options.resolutionWidth = it }
        params["resolutionHeight"]?.jsonPrimitive?.intOrNull?.let { options.resolutionHeight = it }
        params["customJavaPaths"]?.jsonArray?.let { arr ->
            options.customJavaPaths.clear()
            arr.mapNotNull { it.jsonPrimitive.contentOrNull }
                .filter { it.isNotBlank() }
                .forEach { options.customJavaPaths.add(it) }
        }

        options.save()
        return null
    }

    private fun getInfo(): JsonElement {
        return buildJsonObject {
            put("name", MCSRLauncher.APP_NAME)
            put("version", MCSRLauncher.APP_VERSION)
            put("isDev", MCSRLauncher.IS_DEV_VERSION)
            put("basePath", MCSRLauncher.BASE_PATH.toAbsolutePath().toString())
        }
    }

    private fun openDevTools(): JsonElement? {

        logger.info("DevTools requested")
        return null
    }

    private fun parseLanguage(value: String): LauncherLanguage {
        val normalized = value.trim()
        LauncherLanguage.entries.firstOrNull { it.name.equals(normalized, ignoreCase = true) }?.let { return it }
        LauncherLanguage.entries.firstOrNull { it.languageCode.equals(normalized, ignoreCase = true) }?.let { return it }
        return LauncherLanguage.ENGLISH
    }
}
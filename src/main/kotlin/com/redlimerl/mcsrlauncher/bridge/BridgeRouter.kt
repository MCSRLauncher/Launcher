package com.redlimerl.mcsrlauncher.bridge

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.bridge.api.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.JsonElement
import org.apache.logging.log4j.LogManager
import org.cef.callback.CefQueryCallback

class BridgeRouter {
    private val logger = LogManager.getLogger("BridgeRouter")
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val instanceApi = InstanceApi()
    private val accountApi = AccountApi()
    private val launcherApi = LauncherApi()
    private val metaApi = MetaApi()

    fun handleRequest(requestJson: String, callback: CefQueryCallback) {
        scope.launch {
            try {
                val request = MCSRLauncher.JSON.decodeFromString<BridgeRequest>(requestJson)
                logger.debug("Handling request: ${request.method}")

                val response = routeRequest(request)
                val responseJson = MCSRLauncher.JSON.encodeToString(BridgeResponse.serializer(), response)
                callback.success(responseJson)
            } catch (e: Exception) {
                logger.error("Failed to handle bridge request", e)
                val errorResponse = BridgeResponse.error("unknown", e.message ?: "Unknown error")
                val errorJson = MCSRLauncher.JSON.encodeToString(BridgeResponse.serializer(), errorResponse)
                callback.success(errorJson)
            }
        }
    }

    private suspend fun routeRequest(request: BridgeRequest): BridgeResponse {
        return try {
            val result: JsonElement? = when {
                request.method.startsWith("instance.") -> instanceApi.handle(request.method, request.params)
                request.method.startsWith("account.") -> accountApi.handle(request.method, request.params)
                request.method.startsWith("launcher.") -> launcherApi.handle(request.method, request.params)
                request.method.startsWith("meta.") -> metaApi.handle(request.method, request.params)
                request.method == "ping" -> {
                    kotlinx.serialization.json.JsonPrimitive("pong")
                }
                else -> throw IllegalArgumentException("Unknown method: ${request.method}")
            }
            BridgeResponse.success(request.id, result)
        } catch (e: Exception) {
            logger.error("Error handling ${request.method}", e)
            BridgeResponse.error(request.id, e.message ?: "Unknown error")
        }
    }

    fun shutdown() {
        scope.cancel()
    }
}
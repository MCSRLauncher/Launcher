package com.redlimerl.mcsrlauncher.bridge

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

@Serializable
data class BridgeRequest(
    val id: String,
    val method: String,
    val params: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class BridgeResponse(
    val id: String,
    val result: JsonElement? = null,
    val error: String? = null
) {
    companion object {
        fun success(id: String, result: JsonElement?): BridgeResponse {
            return BridgeResponse(id = id, result = result, error = null)
        }

        fun error(id: String, message: String): BridgeResponse {
            return BridgeResponse(id = id, result = null, error = message)
        }
    }
}

@Serializable
data class BridgeEvent(
    val event: String,
    val data: JsonElement
)
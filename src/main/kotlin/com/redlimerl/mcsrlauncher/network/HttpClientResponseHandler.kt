package com.redlimerl.mcsrlauncher.network

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.util.LauncherWorker
import kotlinx.serialization.SerializationException
import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.io.HttpClientResponseHandler
import org.apache.hc.core5.http.io.entity.EntityUtils


class JsonHttpClientResponseHandler(private val worker: LauncherWorker) : HttpClientResponseHandler<JsonResponseResult> {
    override fun handleResponse(response: ClassicHttpResponse): JsonResponseResult {
        val code = response.code
        val entity = response.entity
        if (entity != null) {
            val stringEntity = EntityUtils.toString(response.entity)
            if (code !in 200..299) {
                MCSRLauncher.LOGGER.error("JSON request failed ($code): \"$stringEntity\"")
            }
            try {
                val json = MCSRLauncher.JSON.parseToJsonElement(stringEntity)
                return JsonResponseResult(code, json)
            } catch (e: SerializationException) {
                MCSRLauncher.LOGGER.error("JSON request failed: Failed to parse json")
                return JsonResponseResult(code, null)
            }
        }
        MCSRLauncher.LOGGER.error("JSON request failed ($code): Missing request result")
        return JsonResponseResult(code, null)
    }
}

class JsonSha256HttpClientResponseHandler(private val worker: LauncherWorker) : HttpClientResponseHandler<JsonSha256ResponseResult> {
    override fun handleResponse(response: ClassicHttpResponse): JsonSha256ResponseResult {
        val code = response.code
        val entity = response.entity
        if (entity != null) {
            val stringEntity = EntityUtils.toString(response.entity)
            if (code !in 200..299) {
                MCSRLauncher.LOGGER.error("Failed to JSON request($code): \"$stringEntity\"")
            }
            try {
                MCSRLauncher.JSON.parseToJsonElement(stringEntity)
                return JsonSha256ResponseResult(code, stringEntity)
            } catch (e: SerializationException) {
                MCSRLauncher.LOGGER.error("JSON request failed: Failed to parse json")
                return JsonSha256ResponseResult(code, null)
            }
        }
        MCSRLauncher.LOGGER.error("Failed to JSON request($code): Missing request result")
        return JsonSha256ResponseResult(code, null)
    }
}
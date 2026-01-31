package com.redlimerl.mcsrlauncher.util

import com.redlimerl.mcsrlauncher.MCSRLauncher.LOGGER
import com.redlimerl.mcsrlauncher.network.*
import org.apache.hc.client5.http.config.RequestConfig
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.apache.hc.core5.http.ClassicHttpRequest
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.util.Timeout

object HttpUtils {
    private val HTTP_CLIENT: CloseableHttpClient = HttpClientBuilder.create()
        .setDefaultRequestConfig(
            RequestConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(20))
                .setResponseTimeout(Timeout.ofSeconds(30))
                .build()
        )
        .build()

    fun makeRawRequest(request: ClassicHttpRequest, worker: LauncherWorker): RawResponseResult {
        LOGGER.debug("Requesting JSON to: " + request.uri.toString())
        return HTTP_CLIENT.execute(request) {
            val code = it.code
            val entity = it.entity
            if (entity != null) {
                val stringEntity = EntityUtils.toString(it.entity)
                if (code !in 200..299) {
                    LOGGER.error("JSON request failed ($code): \"$stringEntity\"")
                }
                return@execute RawResponseResult(code, stringEntity)
            }
            LOGGER.error("JSON request failed ($code): Missing request result")
            return@execute RawResponseResult(code, null)
        }
    }

    fun makeJsonRequest(request: ClassicHttpRequest, worker: LauncherWorker): JsonResponseResult {
        LOGGER.debug("Requesting JSON to: " + request.uri.toString())
        return HTTP_CLIENT.execute(request, JsonHttpClientResponseHandler(worker))
    }

    fun makeJsonSha256Request(request: ClassicHttpRequest, worker: LauncherWorker): JsonSha256ResponseResult {
        LOGGER.debug("Requesting JSON(sha256) to: " + request.uri.toString())
        return HTTP_CLIENT.execute(request, JsonSha256HttpClientResponseHandler(worker))
    }
}

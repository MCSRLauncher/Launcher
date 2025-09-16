package com.redlimerl.mcsrlauncher.util

import com.redlimerl.mcsrlauncher.util.HttpUtils.makeRawRequest
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity
import org.apache.hc.core5.http.message.BasicNameValuePair
import java.nio.charset.StandardCharsets

object AnalysisUtils {

    fun analyzeLog(log: String, worker: LauncherWorker): String {
        val post = HttpPost("https://maskers.xyz/log-analysis/analyse")
        post.entity = UrlEncodedFormEntity(listOf(BasicNameValuePair("loglink", log)), StandardCharsets.UTF_8)
        val request = makeRawRequest(post, worker)
        if (request.hasSuccess()) {
            val regex = Regex("<pre>(.*?)</pre>", RegexOption.DOT_MATCHES_ALL)
            val match = regex.find(request.result!!)
            return "<html>${match?.groups?.get(1)?.value?.replace("<code>", "[")?.replace("</code>", "]")}</html>"
        } else {
            return "\nFailed to analyze: ${request.code}"
        }
    }
}
package com.redlimerl.mcsrlauncher.auth

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.exception.IllegalRequestResponseException
import com.redlimerl.mcsrlauncher.util.HttpUtils
import com.redlimerl.mcsrlauncher.util.LauncherWorker
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity
import org.apache.hc.core5.http.message.BasicNameValuePair

class MicrosoftAuthentication {
    companion object {
        // because of https://aka.ms/AppRegInfo, I will use MCSR Ranked Launcher client instead on development
        fun getAzureClientId(): String {
            return "0d2d1127-aae6-4b73-805e-0672eee704c4"
        }
        const val TOKEN_SCOPE = "XboxLive.SignIn XboxLive.offline_access"

        const val DEVICE_CODE_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"
        const val TOKEN_ACCESS_URL = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"
    }
}

@Serializable
data class MSDeviceCodeAuth(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUrl: String,
    @SerialName("expires_in") val expires: Int,
    val interval: Int,
    val message: String,
) {

    companion object {
        fun create(worker: LauncherWorker): MSDeviceCodeAuth {
            worker.setState("Generating MSA device-code")

            val postRequest = HttpPost(MicrosoftAuthentication.DEVICE_CODE_URL)
            postRequest.setHeader("Content-Type", "application/x-www-form-urlencoded")
            postRequest.setHeader("Accept", "application/json")
            postRequest.entity = UrlEncodedFormEntity(listOf(
                BasicNameValuePair("client_id", MicrosoftAuthentication.getAzureClientId()),
                BasicNameValuePair("scope", MicrosoftAuthentication.TOKEN_SCOPE)
            ))

            val response = HttpUtils.makeJsonRequest(postRequest, worker)

            if (!response.hasSuccess()) throw IllegalRequestResponseException("Failed to get MSA device-code")
            return response.get<MSDeviceCodeAuth>()
        }
    }

}

@Serializable
data class MSTokenReceiverAuth(
    @SerialName("token_type") val type: String,
    @SerialName("expires_in") val expires: Int,
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String
) {

    companion object {
        fun create(worker: LauncherWorker, deviceCode: MSDeviceCodeAuth, nextInterval: Long = 10000): MSTokenReceiverAuth {
            val deadlineMs = System.currentTimeMillis() + (deviceCode.expires * 1000L)
            var currentInterval = nextInterval

            while (true) {
                val remainingMs = deadlineMs - System.currentTimeMillis()
                if (remainingMs <= 0) throw IllegalRequestResponseException("MSA device-code expired")

                val delayMs = currentInterval.coerceAtMost(remainingMs)
                MCSRLauncher.LOGGER.info("Getting token from MSA... Delay: ${delayMs}ms")
                Thread.sleep(delayMs)
                if (worker.isCancelled()) throw InterruptedException("Cancelled")

                val postRequest = HttpPost(MicrosoftAuthentication.TOKEN_ACCESS_URL)
                postRequest.setHeader("Content-Type", "application/x-www-form-urlencoded")
                postRequest.setHeader("Accept", "application/json")
                postRequest.entity = UrlEncodedFormEntity(
                    listOf(
                        BasicNameValuePair("client_id", MicrosoftAuthentication.getAzureClientId()),
                        BasicNameValuePair("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
                        BasicNameValuePair("device_code", deviceCode.deviceCode)
                    )
                )

                val response = try {
                    HttpUtils.makeJsonRequest(postRequest, worker)
                } catch (e: java.net.SocketTimeoutException) {
                    MCSRLauncher.LOGGER.warn("MSA token request timed out, retrying...", e)
                    currentInterval = (currentInterval + 2000).coerceAtMost(30000)
                    continue
                } catch (e: java.io.IOException) {
                    MCSRLauncher.LOGGER.warn("MSA token request failed, retrying...", e)
                    currentInterval = (currentInterval + 2000).coerceAtMost(30000)
                    continue
                }

                if (response.hasSuccess()) return response.get()

                if (response.result == null) {
                    throw IllegalRequestResponseException("Failed to get MSA Token (HTTP ${response.code})")
                }

                val errorType = response.get<MSAuthenticationError>()
                MCSRLauncher.LOGGER.debug("MSA device-code poll failed: ${errorType.error} (HTTP ${response.code})")
                when (errorType.error) {
                    "authorization_pending" -> {
                        currentInterval = (deviceCode.interval * 1000L).coerceAtLeast(1000L)
                        continue
                    }
                    "slow_down" -> {
                        currentInterval = (currentInterval + 5000).coerceAtMost(60000)
                        continue
                    }
                    "expired_token" -> throw IllegalRequestResponseException("MSA device-code expired")
                    "authorization_declined" -> throw IllegalRequestResponseException("MSA authorization declined")
                    "bad_verification_code" -> throw IllegalRequestResponseException("Invalid MSA device-code")
                    else -> throw IllegalRequestResponseException(errorType.errorDescription ?: "Failed to get MSA Token")
                }
            }
        }
    }

}

@Serializable
data class MSAuthenticationError(
    val error: String,
    @SerialName("error_description") val errorDescription: String? = null,
    @SerialName("error_codes") val errorCodes: Array<Int>? = null
) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MSAuthenticationError

        if (error != other.error) return false
        if (errorDescription != other.errorDescription) return false
        if (!errorCodes.contentEquals(other.errorCodes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = error.hashCode()
        result = 31 * result + errorDescription.hashCode()
        result = 31 * result + errorCodes.contentHashCode()
        return result
    }
}

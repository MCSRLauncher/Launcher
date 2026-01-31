package com.redlimerl.mcsrlauncher.bridge.api

import com.redlimerl.mcsrlauncher.auth.MicrosoftAuthentication
import com.redlimerl.mcsrlauncher.auth.MSDeviceCodeAuth
import com.redlimerl.mcsrlauncher.auth.MSTokenReceiverAuth
import com.redlimerl.mcsrlauncher.auth.XBLTokenReceiverAuth
import com.redlimerl.mcsrlauncher.auth.MCTokenReceiverAuth
import com.redlimerl.mcsrlauncher.data.MicrosoftAccount
import com.redlimerl.mcsrlauncher.launcher.AccountManager
import com.redlimerl.mcsrlauncher.util.WebLauncherWorker
import com.redlimerl.mcsrlauncher.webview.WebViewManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import org.apache.logging.log4j.LogManager

class AccountApi {
    private val logger = LogManager.getLogger("AccountApi")

    suspend fun handle(method: String, params: JsonObject): JsonElement? {
        return when (method) {
            "account.getAll" -> getAll()
            "account.getActive" -> getActive()
            "account.setActive" -> setActive(params)
            "account.remove" -> remove(params)
            "account.login" -> login()
            else -> throw IllegalArgumentException("Unknown account method: $method")
        }
    }

    private suspend fun login(): JsonElement? {
        val worker = WebLauncherWorker("Login", "Initializing login...")
        worker.start()

        return withContext(Dispatchers.IO) {
            try {
                val deviceCode = MSDeviceCodeAuth.create(worker)
                WebViewManager.broadcastEvent("auth.deviceCode", deviceCode)

                worker.setState("Waiting for user to authenticate...")
                val msToken = MSTokenReceiverAuth.create(worker, deviceCode)

                worker.setState("Logging in to Xbox Live...")
                val xblToken = XBLTokenReceiverAuth.createUserToken(worker, msToken.accessToken)
                val xstsToken = XBLTokenReceiverAuth.createXSTSToken(worker, xblToken)

                worker.setState("Logging in to Minecraft...")
                val mcToken = MCTokenReceiverAuth.create(worker, xstsToken)
                mcToken.checkOwnership(worker)

                worker.setState("Fetching profile...")
                val profile = mcToken.getProfile(worker)

                val account = MicrosoftAccount(
                    profile,
                    msToken.accessToken,
                    msToken.refreshToken,
                    System.currentTimeMillis() + (msToken.expires * 1000L)
                )

                AccountManager.addAccount(account)
                account.toDTO()
            } catch (e: Exception) {
                logger.error("Login failed", e)
                throw e
            } finally {
                worker.finish()
            }
        }
    }

    private fun getAll(): JsonElement {
        return buildJsonArray {
            AccountManager.getAllAccounts().forEach { account ->
                add(account.toDTO())
            }
        }
    }

    private fun getActive(): JsonElement? {
        return AccountManager.getActiveAccount()?.toDTO()
    }

    private fun setActive(params: JsonObject): JsonElement? {
        val uuid = params["uuid"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("uuid is required")

        val needle = normalizeUuid(uuid)
        val account = AccountManager.getAllAccounts().find { normalizeUuid(it.profile.uuid.toString()) == needle }
            ?: throw IllegalArgumentException("Account not found: $uuid")

        AccountManager.setActiveAccount(account)
        return null
    }

    private fun remove(params: JsonObject): JsonElement? {
        val uuid = params["uuid"]?.jsonPrimitive?.contentOrNull
            ?: throw IllegalArgumentException("uuid is required")

        val needle = normalizeUuid(uuid)
        val account = AccountManager.getAllAccounts().find { normalizeUuid(it.profile.uuid.toString()) == needle }
            ?: throw IllegalArgumentException("Account not found: $uuid")

        AccountManager.removeAccount(account)
        return null
    }
    private fun MicrosoftAccount.toDTO(): JsonObject {
        return buildJsonObject {
            put("uuid", profile.uuid.toString())
            put("username", profile.nickname)
            profile.skin?.let { skin ->
                put("skinData", skin.data)
            }
        }
    }

    private fun normalizeUuid(uuid: String): String {
        return uuid.lowercase().replace("-", "")
    }
}
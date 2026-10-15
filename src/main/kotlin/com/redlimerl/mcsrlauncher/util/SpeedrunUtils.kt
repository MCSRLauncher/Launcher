package com.redlimerl.mcsrlauncher.util

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.data.instance.draftout.DraftoutVersionData
import com.redlimerl.mcsrlauncher.data.instance.mcsrranked.MCSRRankedVersionData
import kotlinx.serialization.json.*
import org.apache.hc.client5.http.classic.methods.HttpGet

object SpeedrunUtils {
    val MAJOR_SPEEDRUN_VERSIONS = listOf(
        "1.16.1",
        "1.16.5",
        "1.17.1",
        "1.15.2",
        "1.14.4",
        "1.12.2",
        "1.11.2",
        "1.8.9",
        "1.8",
        "1.7.10"
    )

    val DRAFTOUT_MC_VERSION = "26.3"

    fun getLatestMCSRRankedVersion(worker: LauncherWorker): MCSRRankedVersionData? {
        worker.setState("Checking the latest version of MCSR Ranked")
        val request = HttpUtils.makeJsonRequest(HttpGet("https://api.modrinth.com/v2/project/mcsr-ranked/version?featured=true"), worker)
        if (!request.hasSuccess()) {
            MCSRLauncher.LOGGER.error("Failed to parse MCSR Ranked version on Modrinth")
            return null
        }

        val json = request.get<JsonArray>().first().jsonObject
        val file = json["files"]?.jsonArray?.first()?.jsonObject
        if (file == null) {
            MCSRLauncher.LOGGER.error("Couldn't find MCSR Ranked version file on Modrinth")
            return null
        }

        val version = json["version_number"]?.jsonPrimitive?.content
        if (version == null) {
            MCSRLauncher.LOGGER.error("Couldn't find MCSR Ranked version name on Modrinth")
            return null
        }

        val hash = file["hashes"]?.jsonObject?.get("sha512")?.jsonPrimitive?.content
        if (hash == null) {
            MCSRLauncher.LOGGER.error("Couldn't find MCSR Ranked file hash on Modrinth")
            return null
        }

        val url = file["url"]?.jsonPrimitive?.content
        if (url == null) {
            MCSRLauncher.LOGGER.error("Couldn't find MCSR Ranked file url on Modrinth")
            return null
        }

        val size = file["size"]?.jsonPrimitive?.long
        if (size == null) {
            MCSRLauncher.LOGGER.error("Couldn't find MCSR Ranked file size on Modrinth")
            return null
        }

        return MCSRRankedVersionData(version, hash, url, size)
    }

    fun getLatestDraftoutVersion(worker: LauncherWorker): DraftoutVersionData? {
        worker.setState("Checking the latest version of Draftout")
        val request = HttpUtils.makeJsonRequest(HttpGet("https://api.modrinth.com/v2/project/draftout/version?featured=true"), worker)
        if (!request.hasSuccess()) {
            MCSRLauncher.LOGGER.error("Failed to parse Draftout version on Modrinth")
            return null
        }

        val json = request.get<JsonArray>().first().jsonObject
        val file = json["files"]?.jsonArray?.first()?.jsonObject
        if (file == null) {
            MCSRLauncher.LOGGER.error("Couldn't find Draftout version file on Modrinth")
            return null
        }

        val version = json["version_number"]?.jsonPrimitive?.content
        if (version == null) {
            MCSRLauncher.LOGGER.error("Couldn't find Draftout version name on Modrinth")
            return null
        }

        val hash = file["hashes"]?.jsonObject?.get("sha512")?.jsonPrimitive?.content
        if (hash == null) {
            MCSRLauncher.LOGGER.error("Couldn't find Draftout file hash on Modrinth")
            return null
        }

        val url = file["url"]?.jsonPrimitive?.content
        if (url == null) {
            MCSRLauncher.LOGGER.error("Couldn't find Draftout file url on Modrinth")
            return null
        }

        val size = file["size"]?.jsonPrimitive?.long
        if (size == null) {
            MCSRLauncher.LOGGER.error("Couldn't find Draftout file size on Modrinth")
            return null
        }

        return DraftoutVersionData(version, hash, url, size)
    }
}
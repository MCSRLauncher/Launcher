package com.redlimerl.mcsrlauncher.launcher

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.data.meta.MetaPackageIndexes
import com.redlimerl.mcsrlauncher.data.meta.MetaUniqueID
import com.redlimerl.mcsrlauncher.data.meta.MetaVersion
import com.redlimerl.mcsrlauncher.data.meta.file.MetaVersionFile
import com.redlimerl.mcsrlauncher.exception.IllegalRequestResponseException
import com.redlimerl.mcsrlauncher.util.LauncherWorker
import org.apache.commons.io.FileUtils
import org.apache.hc.client5.http.classic.methods.HttpGet
import java.net.URI
import java.nio.file.Path

object MetaManager {

    val BASE_PATH: Path = MCSRLauncher.BASE_PATH.resolve("meta")
    private val PACKAGES_PATH = BASE_PATH.resolve("index.json")
    private var META_PACKAGES: MetaPackageIndexes = MetaPackageIndexes()
    private val VERSION_MAP = hashMapOf<MetaUniqueID, Set<String>>()

    fun load(worker: LauncherWorker, force: Boolean = false) {
        BASE_PATH.toFile().mkdirs()
        if (PACKAGES_PATH.toFile().exists()) {
            META_PACKAGES = MCSRLauncher.JSON.decodeFromString(FileUtils.readFileToString(PACKAGES_PATH.toFile(), Charsets.UTF_8))
            for (metaPackage in META_PACKAGES.packages) {
                metaPackage.getVersions(worker)
            }
        }

        if (!force && System.currentTimeMillis() - META_PACKAGES.latestUpdate < 1000 * 60 * 60 * 24 * 6) return onLoad(worker)

        MCSRLauncher.LOGGER.info("Getting meta packages...")
        val response = MCSRLauncher.makeJsonRequest(HttpGet(this.getMetaURI()), worker)
        if (!response.hasSuccess()) throw IllegalRequestResponseException("Failed to get meta packages")

        META_PACKAGES = response.get<MetaPackageIndexes>()
        for (metaPackage in META_PACKAGES.packages) {
            metaPackage.updateVersions(worker)
        }
        META_PACKAGES.latestUpdate = System.currentTimeMillis()

        FileUtils.writeStringToFile(PACKAGES_PATH.toFile(), MCSRLauncher.JSON.encodeToString(META_PACKAGES), Charsets.UTF_8)
        onLoad(worker)
    }

    fun onLoad(worker: LauncherWorker) {
        VERSION_MAP.clear()
        for (metaPackage in META_PACKAGES.packages) {
            VERSION_MAP[metaPackage.uid] = metaPackage.getVersions(worker).versions.map { it.version }.toSet()
        }
    }

    fun getMetaURI(): URI {
        return URI(MCSRLauncher.options.metaUrl.let { if (it.endsWith("/")) it else "$it/" })
    }

    fun getVersions(uid: MetaUniqueID, worker: LauncherWorker = LauncherWorker.empty()): List<MetaVersion> {
        return META_PACKAGES.getVersions(uid, worker)
    }

    fun containsVersion(uid: MetaUniqueID, version: String): Boolean {
        return VERSION_MAP.getOrDefault(uid, setOf()).contains(version)
    }

    inline fun <reified T : MetaVersionFile> getVersionMeta(uid: MetaUniqueID, version: String?, worker: LauncherWorker = LauncherWorker.empty()): T? {
        return getVersions(uid, worker).find { it.version == version }?.getOrLoadMetaVersionFile<T>(uid, worker)
    }

}
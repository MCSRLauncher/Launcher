package com.redlimerl.mcsrlauncher.instance

import com.redlimerl.mcsrlauncher.MCSRLauncher
import com.redlimerl.mcsrlauncher.util.JavaUtils
import java.nio.file.Path
import java.util.*
import kotlin.io.path.absolutePathString

class JavaContainer(val path: Path) {

    val vendor: String
    val version: String
    val majorVersion: Int
    val arch: String

    companion object {
        fun getVersionLists(javaPath: String): List<String> {
            val process = ProcessBuilder(javaPath, "-version").redirectErrorStream(false).start()
            val stderrLines = process.errorStream.bufferedReader().readLines()
            process.waitFor()

            if (stderrLines.isEmpty()) throw IllegalStateException("No output from java -version")

            stderrLines.firstOrNull {
                it.contains("version") || it.contains("openjdk version")
            } ?: throw IllegalStateException("Cannot detect Java version")

            return stderrLines
        }
    }

    init {
        var possibleVendor: String? = null
        var possibleVersion: String? = null
        var possibleArch: String? = null

        val releaseFile = path.parent.parent.resolve("release").toFile()
        if (releaseFile.exists()) {
            try {
                val properties = Properties()
                releaseFile.bufferedReader().use { reader -> properties.load(reader) }

                possibleVendor = properties.getProperty("IMPLEMENTOR")?.replace("\"", "") ?: "Unknown"
                possibleVersion = properties.getProperty("JAVA_VERSION")?.replace("\"", "") ?: "Unknown Version"
                possibleArch = properties.getProperty("OS_ARCH")?.replace("\"", "") ?: "Unknown Architecture"
            } catch (e: Exception) {
                MCSRLauncher.LOGGER.error("Failed to find java information", e)
            }
        }

        if (possibleVersion == null || possibleVendor == null || possibleArch == null) {
            val stderrLines = getVersionLists(this.getJavaRuntimePath())

            val versionLine = stderrLines.firstOrNull {
                it.contains("version") || it.contains("openjdk version")
            } ?: throw IllegalStateException("Cannot detect Java version")

            val versionRegex = Regex("\"([^\"]+)\"")
            val versionMatch = versionRegex.find(versionLine) ?: throw IllegalStateException("Cannot parse Java version from: $versionLine")
            possibleVersion = versionMatch.groupValues[1]

            val vendorLine = stderrLines.drop(1).joinToString(" ")
            possibleVendor = when {
                vendorLine.contains("Oracle", ignoreCase = true) -> "Oracle"
                vendorLine.contains("OpenJDK", ignoreCase = true) -> "OpenJDK"
                vendorLine.contains("Adoptium", ignoreCase = true) -> "Eclipse Adoptium"
                vendorLine.contains("Amazon", ignoreCase = true) -> "Amazon Corretto"
                vendorLine.contains("Zulu", ignoreCase = true) -> "Zulu"
                vendorLine.contains("IBM", ignoreCase = true) -> "IBM"
                else -> vendorLine.take(64)
            }

            val archLine = stderrLines.drop(2).joinToString(" ")
            possibleArch = when {
                archLine.contains("64-Bit", ignoreCase = true) -> "64"
                else -> "32"
            }
        }

        this.version = possibleVersion
        this.vendor = possibleVendor
        this.majorVersion = this.version.split(".").first().toInt().let { if (it == 1) this.version.split(".")[1].toInt() else it }
        this.arch = possibleArch
    }

    private fun getJavaRuntimePath(): String {
        return path.parent.resolve(JavaUtils.javaExecutableName()).absolutePathString()
    }

    override fun equals(other: Any?): Boolean {
        if (other is JavaContainer) {
            return this.version == other.version && this.vendor == other.vendor
        }
        return false
    }

    override fun hashCode(): Int {
        var result = path.hashCode()
        result = 31 * result + vendor.hashCode()
        result = 31 * result + version.hashCode()
        return result
    }

    fun dataArray(): Array<String> {
        return arrayOf(this.version, this.vendor, this.path.absolutePathString())
    }

}
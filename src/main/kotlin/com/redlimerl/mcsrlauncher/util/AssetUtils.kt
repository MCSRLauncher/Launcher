package com.redlimerl.mcsrlauncher.util

import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import com.google.common.io.Files
import com.redlimerl.mcsrlauncher.MCSRLauncher
import io.github.z4kn4fein.semver.constraints.Constraint
import io.github.z4kn4fein.semver.toVersionOrNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.io.File
import java.io.InputStream
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.util.*
import java.util.zip.ZipFile
import kotlin.math.ln
import kotlin.math.pow


object AssetUtils {

    @Suppress("DEPRECATION")
    fun getHashFunction(hashType: String): HashFunction {
        return when(hashType) {
            "sha1" -> Hashing.sha1()
            "sha256" -> Hashing.sha256()
            "sha384" -> Hashing.sha384()
            "sha512" -> Hashing.sha512()
            else -> throw IllegalArgumentException()
        }
    }

    fun compareHash(file: File, sha1: String, hashing: HashFunction = getHashFunction("sha1")): Boolean {
        return Files.asByteSource(file).hash(hashing).toString() == sha1
    }

    fun calculateSha256(inputStream: InputStream): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream.use { stream ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun libraryNameToPath(path: Path, name: String): Path {
        return path.resolve(getLibraryMavenPath(name).replace("/", File.separator))
    }

    fun getLibraryMavenPath(name: String): String {
        val parts = name.split(":")
        if (parts.size < 3) throw IllegalArgumentException("Invalid Maven name format")

        val (group, artifact, version) = parts
        val groupPath = group.replace('.', '/')
        var jarName = "$artifact-$version"
        if (parts.size > 3) jarName += "-" + parts.drop(3).joinToString("-")
        jarName += ".jar"

        return "$groupPath/$artifact/$version/$jarName"
    }

    fun extractZip(jar: File, destDir: File, flatten: Boolean = false) {
        ZipFile(jar).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                val name = entry.name
                if (!entry.isDirectory) {
                    val outFile: File = if (flatten) {
                        File(destDir, Paths.get(name).fileName.toString())
                    } else {
                        File(destDir, entry.name)
                    }
                    outFile.parentFile.mkdirs()
                    zip.getInputStream(entry).use { input ->
                        outFile.outputStream().use { output -> input.copyTo(output) }
                    }
                }
            }
        }
    }

    fun formatFileSize(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt()
        val unit = "KMGTPE"[exp - 1]
        val size = bytes / 1024.0.pow(exp.toDouble())
        return String.format("%.1f %sB", size, unit)
    }

    fun parseUUID(uuid: String): UUID {
        return UUID.fromString(
            uuid.replace("-".toRegex(), "").replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)".toRegex(),
                "$1-$2-$3-$4-$5"
            )
        )
    }

    fun <T> doConcurrency(list: Collection<T>, concurrent: Int = MCSRLauncher.options.concurrentDownloads, consumer: suspend (T) -> Unit) {
        runBlocking {
            val semaphore = Semaphore(concurrent)

            val jobs = list.map { element ->
                async {
                    semaphore.withPermit { consumer(element) }
                }
            }

            jobs.awaitAll()
        }
    }

    fun isInVersionRange(versionRange: String, version: String): Boolean {
        if (versionRange == version) return true
        val v = version.toVersionOrNull(false) ?: return false
        return versionRange.split(" ").map { Constraint.parse(it).isSatisfiedBy(v) }.all { it }
    }


}
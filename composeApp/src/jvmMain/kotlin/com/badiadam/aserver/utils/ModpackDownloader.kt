package com.badiadam.aserver.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

object ModpackDownloader {

    suspend fun downloadMods(
        modList: List<ModDownloadInfo>,
        targetServerDir: File,
        onProgress: (Int, Int, String) -> Unit,
    ) = withContext(Dispatchers.IO) {
        if (!targetServerDir.exists() && !targetServerDir.mkdirs()) {
            throw IOException("Hedef sunucu klasoru olusturulamadi: ${targetServerDir.absolutePath}")
        }

        val canonicalTargetDir = targetServerDir.canonicalFile
        val totalCount = modList.size
        val completedCount = AtomicInteger(0)
        val semaphore = Semaphore(5)

        if (totalCount == 0) {
            onProgress(0, 0, "Indirilecek mod bulunamadi.")
            return@withContext
        }

        coroutineScope {
            modList.map { modInfo ->
                async {
                    semaphore.withPermit {
                        try {
                            val targetFile = resolveSafeTargetFile(canonicalTargetDir, modInfo.path)
                            if (targetFile.exists()) {
                                val finished = completedCount.incrementAndGet()
                                onProgress(finished, totalCount, "${targetFile.name} zaten mevcut, atlandi.")
                                return@withPermit
                            }

                            targetFile.parentFile?.let { parentDir ->
                                if (!parentDir.exists() && !parentDir.mkdirs()) {
                                    throw IOException("Hedef klasor olusturulamadi: ${parentDir.absolutePath}")
                                }
                            }

                            downloadFile(modInfo.downloadUrl, targetFile)
                            val finished = completedCount.incrementAndGet()
                            onProgress(finished, totalCount, "${targetFile.name} indirildi.")
                        } catch (exception: Exception) {
                            val finished = completedCount.incrementAndGet()
                            val fileName = File(modInfo.path).name.ifBlank { modInfo.path }
                            onProgress(
                                finished,
                                totalCount,
                                "HATA: $fileName indirilemedi - ${exception.message ?: "Bilinmeyen hata"}",
                            )
                        }
                    }
                }
            }.awaitAll()
        }
    }

    private fun downloadFile(downloadUrl: String, targetFile: File) {
        var lastError: Exception? = null
        var currentUrl = downloadUrl
        val tempFile = File(targetFile.absolutePath + ".part")

        if (tempFile.exists()) {
            tempFile.delete()
        }

        repeat(MAX_REDIRECTS + 1) { attempt ->
            try {
                val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = CONNECT_TIMEOUT_MS
                    readTimeout = READ_TIMEOUT_MS
                    instanceFollowRedirects = false
                    setRequestProperty("User-Agent", USER_AGENT)
                }

                connection.useConnection { conn ->
                    when (val responseCode = conn.responseCode) {
                        HttpURLConnection.HTTP_OK -> {
                            conn.inputStream.use { input ->
                                tempFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }

                            if (targetFile.exists() && !targetFile.delete()) {
                                throw IOException("Eski dosya silinemedi: ${targetFile.absolutePath}")
                            }
                            if (!tempFile.renameTo(targetFile)) {
                                throw IOException("Gecici dosya hedefe tasinamadi: ${targetFile.absolutePath}")
                            }
                            return
                        }

                        HttpURLConnection.HTTP_MOVED_PERM,
                        HttpURLConnection.HTTP_MOVED_TEMP,
                        HttpURLConnection.HTTP_SEE_OTHER,
                        307,
                        308,
                        -> {
                            if (attempt >= MAX_REDIRECTS) {
                                throw IOException("Cok fazla yonlendirme alindi.")
                            }

                            val redirectedUrl = conn.getHeaderField("Location")
                                ?.takeIf { it.isNotBlank() }
                                ?: throw IOException("Yonlendirme konumu bos dondu.")
                            currentUrl = URL(URL(currentUrl), redirectedUrl).toString()
                        }

                        else -> throw IOException("HTTP hatasi: $responseCode")
                    }
                }
            } catch (exception: Exception) {
                if (tempFile.exists()) {
                    tempFile.delete()
                }
                lastError = exception
            }
        }

        throw IOException(
            "Dosya indirilemedi: ${lastError?.message ?: "Bilinmeyen hata"}",
            lastError,
        )
    }

    private fun resolveSafeTargetFile(targetServerDir: File, relativePath: String): File {
        val sanitizedPath = relativePath.replace('\\', '/').trimStart('/')
        if (sanitizedPath.isBlank()) {
            throw IOException("Bos indirme yolu algilandi.")
        }

        val targetFile = File(targetServerDir, sanitizedPath).canonicalFile
        if (!targetFile.toPath().startsWith(targetServerDir.toPath())) {
            throw IOException("Guvensiz hedef yolu engellendi: $relativePath")
        }

        return targetFile
    }

    private inline fun <T> HttpURLConnection.useConnection(block: (HttpURLConnection) -> T): T {
        return try {
            block(this)
        } finally {
            disconnect()
        }
    }

    private const val MAX_REDIRECTS = 5
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 60_000
    private const val USER_AGENT = "AServer Modpack Downloader"
}

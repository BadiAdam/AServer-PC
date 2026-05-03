package com.badiadam.aserver.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

object CurseForgePackManager {

    suspend fun installServerPack(zipFile: File, serverDir: File) = withContext(Dispatchers.IO) {
        if (!zipFile.exists() || !zipFile.isFile) {
            throw IOException("Server Pack .zip dosyası bulunamadı: ${zipFile.absolutePath}")
        }
        if (!serverDir.exists() && !serverDir.mkdirs()) {
            throw IOException("Hedef sunucu klasörü oluşturulamadı: ${serverDir.absolutePath}")
        }

        val tempDir = java.nio.file.Files.createTempDirectory("serverpack_").toFile()
        try {
            ZipFile(zipFile).use { zip ->
                val entries = zip.entries()
                while (entries.hasMoreElements()) {
                    val entry = entries.nextElement()
                    val destFile = File(tempDir, entry.name)
                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        zip.getInputStream(entry).use { input ->
                            destFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }
                    }
                }
            }

            val hasManifest = File(tempDir, "manifest.json").exists()
            val hasModsAtRoot = File(tempDir, "mods").exists()

            if (hasManifest && !hasModsAtRoot) {
                throw Exception("HATA: Bu bir İstemci (Client) paketidir! Lütfen CurseForge üzerinden bu mod paketinin 'Server Pack' (Sunucu) versiyonunu indirip uygulamaya yükleyin.")
            }

            val modsFolder = tempDir.walkTopDown().find { it.isDirectory && it.name == "mods" }
                ?: throw Exception("HATA: Çıkarılan dosyalar arasında 'mods' klasörü bulunamadı. Bu geçerli bir Server Pack olmayabilir.")

            val rootDir = modsFolder.parentFile ?: tempDir
            rootDir.copyRecursively(serverDir, overwrite = true)

        } finally {
            tempDir.deleteRecursively()
        }
    }

    suspend fun cleanClientOnlyMods(serverDir: File) {
        val modsDir = File(serverDir, "mods")
        if (!modsDir.exists() || !modsDir.isDirectory) return

        val deletedModIds = mutableSetOf<String>()

        withContext(Dispatchers.IO) {
            val jarFiles = modsDir.listFiles { _, name -> name.endsWith(".jar") }?.toMutableList() ?: return@withContext

            // AŞAMA 1 (Klasik X-Ray)
            val iterator = jarFiles.iterator()
            while (iterator.hasNext()) {
                val file = iterator.next()
                try {
                    var isClientOnly = false
                    var modId: String? = null

                    ZipFile(file).use { zip ->
                        val entry = zip.getEntry("fabric.mod.json")
                        if (entry != null) {
                            val content = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                            
                            val idRegex = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")
                            val idMatch = idRegex.find(content)
                            modId = idMatch?.groupValues?.get(1)

                            val envRegex = Regex("\"environment\"\\s*:\\s*\"client\"")
                            if (envRegex.containsMatchIn(content)) {
                                isClientOnly = true
                            }
                        }
                    }

                    if (isClientOnly) {
                        modId?.let { deletedModIds.add(it) }
                        if (file.delete()) {
                            println("Silindi: ${file.name}")
                        }
                        iterator.remove() // Kalan işlemler için listeden çıkar
                    }
                } catch (e: Exception) {
                    println("Mod kontrol edilirken hata oluştu: ${file.name}. Hata: ${e.message}")
                }
            }

            // AŞAMA 2 (Zincirleme Bağımlılık Temizliği - Domino Etkisi)
            var hasDeletedInThisPass = true
            while (hasDeletedInThisPass) {
                hasDeletedInThisPass = false
                val depIterator = jarFiles.iterator()

                while (depIterator.hasNext()) {
                    val file = depIterator.next()
                    try {
                        var shouldDelete = false
                        var modId: String? = null

                        ZipFile(file).use { zip ->
                            val entry = zip.getEntry("fabric.mod.json")
                            if (entry != null) {
                                val content = zip.getInputStream(entry).bufferedReader().use { it.readText() }
                                
                                val idRegex = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")
                                val idMatch = idRegex.find(content)
                                modId = idMatch?.groupValues?.get(1)

                                var dependsBlock = ""
                                val dependsIndex = content.indexOf("\"depends\"")
                                if (dependsIndex != -1) {
                                    val blockStart = content.indexOf('{', dependsIndex)
                                    val blockEnd = content.indexOf('}', blockStart)
                                    if (blockStart != -1 && blockEnd != -1) {
                                        dependsBlock = content.substring(blockStart, blockEnd)
                                    }
                                }

                                if (dependsBlock.isNotBlank() && deletedModIds.any { id -> dependsBlock.contains("\"$id\"") }) {
                                    shouldDelete = true
                                }
                            }
                        }

                        if (shouldDelete) {
                            modId?.let { deletedModIds.add(it) }
                            if (file.delete()) {
                                println("Bağımlılık koptuğu için silindi: ${file.name}")
                            }
                            depIterator.remove()
                            hasDeletedInThisPass = true
                        }
                    } catch (e: Exception) {
                        println("Bağımlılık kontrolü sırasında hata oluştu: ${file.name}. Hata: ${e.message}")
                    }
                }
            }
        }
    }

    private const val CURSEFORGE_MANIFEST_FILE = "manifest.json"
    private const val OVERRIDES_PREFIX = "overrides/"
}

internal fun JsonObjectNode.numberOrNull(key: String): String? {
    return when (val node = values[key]) {
        is JsonNumberNode -> node.rawValue
        is JsonStringNode -> node.value
        else -> null
    }
}

internal fun JsonObjectNode.booleanOrNull(key: String): Boolean? = (values[key] as? JsonBooleanNode)?.value

package com.badiadam.aserver.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.util.LinkedHashMap
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

data class ModDownloadInfo(
    val path: String,
    val downloadUrl: String,
    val fileSize: Long = 0,
)

object ModPackManager {

    suspend fun parseMrPack(mrpackFile: File, targetServerDir: File): List<ModDownloadInfo> =
        withContext(Dispatchers.IO) {
            if (!mrpackFile.exists() || !mrpackFile.isFile) {
                throw IOException("MrPack dosyasi bulunamadi: ${mrpackFile.absolutePath}")
            }

            if (!targetServerDir.exists() && !targetServerDir.mkdirs()) {
                throw IOException("Hedef sunucu klasoru olusturulamadi: ${targetServerDir.absolutePath}")
            }

            val canonicalTargetDir = targetServerDir.canonicalFile
            var indexContent: String? = null

            try {
                ZipFile(mrpackFile).use { zipFile ->
                    val entries = zipFile.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
                        val normalizedName = normalizeEntryName(entry.name)

                        when {
                            normalizedName == MODRINTH_INDEX_FILE -> {
                                indexContent = zipFile.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { reader ->
                                    reader.readText()
                                }
                            }

                            normalizedName.startsWith(OVERRIDES_PREFIX) -> {
                                extractOverrideEntry(
                                    zipFile = zipFile,
                                    entry = entry,
                                    normalizedEntryName = normalizedName,
                                    targetServerDir = canonicalTargetDir,
                                )
                            }
                        }
                    }
                }
            } catch (exception: Exception) {
                throw IOException("MrPack arsivi okunamadi: ${mrpackFile.name}", exception)
            }

            val rawIndex = indexContent
                ?: throw IOException("MrPack icinde $MODRINTH_INDEX_FILE bulunamadi.")

            try {
                parseIndexJson(rawIndex)
            } catch (exception: Exception) {
                throw IOException("modrinth.index.json ayrisirilamadi: ${exception.message}", exception)
            }
        }

    private fun extractOverrideEntry(
        zipFile: ZipFile,
        entry: ZipEntry,
        normalizedEntryName: String,
        targetServerDir: File,
    ) {
        val relativePath = normalizedEntryName.removePrefix(OVERRIDES_PREFIX).trimStart('/')
        if (relativePath.isBlank()) {
            return
        }

        val destinationFile = resolveSafeTargetFile(targetServerDir, relativePath)
        if (entry.isDirectory) {
            if (!destinationFile.exists() && !destinationFile.mkdirs()) {
                throw IOException("Override klasoru olusturulamadi: ${destinationFile.absolutePath}")
            }
            return
        }

        destinationFile.parentFile?.let { parentDir ->
            if (!parentDir.exists() && !parentDir.mkdirs()) {
                throw IOException("Override hedef klasoru olusturulamadi: ${parentDir.absolutePath}")
            }
        }

        zipFile.getInputStream(entry).use { input ->
            destinationFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun parseIndexJson(indexContent: String): List<ModDownloadInfo> {
        val root = MrPackJsonParser(indexContent).parse() as? JsonObjectNode
            ?: throw IOException("modrinth.index.json kok degeri obje olmali.")

        val files = root.arrayOrNull("files")
            ?: throw IOException("modrinth.index.json icinde 'files' dizisi bulunamadi.")

        val modDownloads = mutableListOf<ModDownloadInfo>()

        files.values.forEachIndexed { index, fileNode ->
            val fileObject = fileNode as? JsonObjectNode
                ?: throw IOException("'files[$index]' obje olmali.")

            val serverEnvironment = fileObject
                .objectOrNull("env")
                ?.stringOrNull("server")
                ?.lowercase()

            if (serverEnvironment == "unsupported") {
                return@forEachIndexed
            }

            if (serverEnvironment != "required" && serverEnvironment != "optional") {
                return@forEachIndexed
            }

            val rawPath = fileObject.stringOrNull("path")
                ?: throw IOException("'files[$index].path' eksik.")
            val normalizedPath = rawPath.replace('\\', '/').trimStart('/')
            if (normalizedPath.isBlank()) {
                throw IOException("'files[$index].path' bos olamaz.")
            }

            val downloadUrl = fileObject.arrayOrNull("downloads")
                ?.firstStringOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: throw IOException("'files[$index].downloads' icinde gecerli URL yok.")

            modDownloads += ModDownloadInfo(
                path = normalizedPath,
                downloadUrl = downloadUrl,
                fileSize = fileObject.longOrZero("fileSize"),
            )
        }

        return modDownloads
    }

    private fun normalizeEntryName(entryName: String): String =
        entryName.replace('\\', '/').removePrefix("./")

    private const val MODRINTH_INDEX_FILE = "modrinth.index.json"
    private const val OVERRIDES_PREFIX = "overrides/"
}

internal fun resolveSafeTargetFile(targetServerDir: File, relativePath: String): File {
    val sanitizedPath = relativePath.replace('\\', '/').trimStart('/')
    if (sanitizedPath.isBlank()) {
        throw IOException("Bos hedef yolu algilandi.")
    }

    val targetFile = File(targetServerDir, sanitizedPath).canonicalFile
    if (!targetFile.toPath().startsWith(targetServerDir.toPath())) {
        throw IOException("Guvensiz zip girdisi engellendi: $relativePath")
    }

    return targetFile
}

internal sealed interface JsonNode

internal data class JsonObjectNode(val values: Map<String, JsonNode>) : JsonNode

internal data class JsonArrayNode(val values: List<JsonNode>) : JsonNode

internal data class JsonStringNode(val value: String) : JsonNode

internal data class JsonNumberNode(val rawValue: String) : JsonNode

internal data class JsonBooleanNode(val value: Boolean) : JsonNode

internal data object JsonNullNode : JsonNode

internal fun JsonObjectNode.objectOrNull(key: String): JsonObjectNode? = values[key] as? JsonObjectNode

internal fun JsonObjectNode.arrayOrNull(key: String): JsonArrayNode? = values[key] as? JsonArrayNode

internal fun JsonObjectNode.stringOrNull(key: String): String? = (values[key] as? JsonStringNode)?.value

internal fun JsonObjectNode.longOrZero(key: String): Long {
    val node = values[key] ?: return 0L
    return when (node) {
        is JsonNumberNode -> node.rawValue.toLongOrNull() ?: 0L
        is JsonStringNode -> node.value.toLongOrNull() ?: 0L
        else -> 0L
    }
}

internal fun JsonArrayNode.firstStringOrNull(): String? = (values.firstOrNull() as? JsonStringNode)?.value

internal class MrPackJsonParser(
    private val source: String,
) {
    private var position: Int = 0

    fun parse(): JsonNode {
        skipWhitespace()
        val node = parseValue()
        skipWhitespace()
        if (position != source.length) {
            fail("JSON sonrasinda beklenmeyen karakterler bulundu.")
        }
        return node
    }

    private fun parseValue(): JsonNode {
        skipWhitespace()
        if (position >= source.length) {
            fail("Beklenmeyen JSON sonu.")
        }

        return when (val current = source[position]) {
            '{' -> parseObject()
            '[' -> parseArray()
            '"' -> JsonStringNode(parseString())
            't', 'f' -> parseBoolean()
            'n' -> parseNull()
            '-', in '0'..'9' -> JsonNumberNode(parseNumber())
            else -> fail("Gecersiz JSON baslangici: '$current'")
        }
    }

    private fun parseObject(): JsonObjectNode {
        expect('{')
        skipWhitespace()

        val map = LinkedHashMap<String, JsonNode>()
        if (consumeIf('}')) {
            return JsonObjectNode(map)
        }

        while (true) {
            skipWhitespace()
            val key = parseString()
            skipWhitespace()
            expect(':')
            skipWhitespace()
            map[key] = parseValue()
            skipWhitespace()

            when {
                consumeIf('}') -> return JsonObjectNode(map)
                consumeIf(',') -> continue
                else -> fail("Obje icinde ',' veya '}' bekleniyordu.")
            }
        }
    }

    private fun parseArray(): JsonArrayNode {
        expect('[')
        skipWhitespace()

        val list = mutableListOf<JsonNode>()
        if (consumeIf(']')) {
            return JsonArrayNode(list)
        }

        while (true) {
            skipWhitespace()
            list += parseValue()
            skipWhitespace()

            when {
                consumeIf(']') -> return JsonArrayNode(list)
                consumeIf(',') -> continue
                else -> fail("Dizi icinde ',' veya ']' bekleniyordu.")
            }
        }
    }

    private fun parseString(): String {
        expect('"')
        val builder = StringBuilder()

        while (position < source.length) {
            val current = source[position++]
            when (current) {
                '"' -> return builder.toString()
                '\\' -> builder.append(parseEscapeSequence())
                else -> {
                    if (current.code < 0x20) {
                        fail("Kontrol karakteri string icinde kullanilamaz.")
                    }
                    builder.append(current)
                }
            }
        }

        fail("String kapanis karakteri bulunamadi.")
    }

    private fun parseEscapeSequence(): Char {
        if (position >= source.length) {
            fail("Eksik kacis dizisi.")
        }

        return when (val escaped = source[position++]) {
            '"', '\\', '/' -> escaped
            'b' -> '\b'
            'f' -> '\u000C'
            'n' -> '\n'
            'r' -> '\r'
            't' -> '\t'
            'u' -> {
                if (position + 4 > source.length) {
                    fail("Unicode kacisi eksik.")
                }
                val hexValue = source.substring(position, position + 4)
                position += 4
                hexValue.toIntOrNull(16)?.toChar()
                    ?: fail("Gecersiz unicode kacisi: \\u$hexValue")
            }

            else -> fail("Gecersiz kacis karakteri: '$escaped'")
        }
    }

    private fun parseNumber(): String {
        val start = position

        if (source[position] == '-') {
            position++
        }

        parseDigits(allowOnlyZero = true)

        if (position < source.length && source[position] == '.') {
            position++
            parseDigits(allowOnlyZero = false)
        }

        if (position < source.length && (source[position] == 'e' || source[position] == 'E')) {
            position++
            if (position < source.length && (source[position] == '+' || source[position] == '-')) {
                position++
            }
            parseDigits(allowOnlyZero = false)
        }

        return source.substring(start, position)
    }

    private fun parseDigits(allowOnlyZero: Boolean) {
        if (position >= source.length || !source[position].isDigit()) {
            fail("Sayi bekleniyordu.")
        }

        if (allowOnlyZero && source[position] == '0') {
            position++
            return
        }

        while (position < source.length && source[position].isDigit()) {
            position++
        }
    }

    private fun parseBoolean(): JsonBooleanNode {
        return when {
            source.startsWith("true", position) -> {
                position += 4
                JsonBooleanNode(true)
            }

            source.startsWith("false", position) -> {
                position += 5
                JsonBooleanNode(false)
            }

            else -> fail("Gecersiz boolean degeri.")
        }
    }

    private fun parseNull(): JsonNullNode {
        if (!source.startsWith("null", position)) {
            fail("Gecersiz null degeri.")
        }
        position += 4
        return JsonNullNode
    }

    private fun expect(expected: Char) {
        if (position >= source.length || source[position] != expected) {
            fail("'$expected' bekleniyordu.")
        }
        position++
    }

    private fun consumeIf(expected: Char): Boolean {
        if (position < source.length && source[position] == expected) {
            position++
            return true
        }
        return false
    }

    private fun skipWhitespace() {
        while (position < source.length && source[position].isWhitespace()) {
            position++
        }
    }

    private fun fail(message: String): Nothing {
        throw IllegalArgumentException("Pozisyon $position: $message")
    }
}

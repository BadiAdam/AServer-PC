package com.badiadam.aserver

import com.badiadam.aserver.ui.screens.CreateServerScreen
import com.badiadam.aserver.ui.screens.DashboardScreen
import com.badiadam.aserver.ui.screens.FileManagerScreen
import com.badiadam.aserver.ui.screens.MyServersScreen
import com.badiadam.aserver.ui.screens.PlayersScreen
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.datatransfer.StringSelection
import java.awt.Toolkit
import java.io.BufferedReader
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.net.URL
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import androidx.compose.animation.core.animateDpAsState

// ============================================================================
// ZOMBİ SÜREÇ (ORPHAN PROCESS) ÖNLEYİCİ - ÖLÜM FERMANI KANCASI (TASKKILL)
// ============================================================================
object ProcessManager {
    var serverProcess: Process? = null
    var playitProcess: Process? = null

    fun killProcessTree(process: Process?) {
        if (process == null || !process.isAlive) return
        try {
            val pid = process.pid()
            val os = System.getProperty("os.name").lowercase()
            if (os.contains("win")) {
                ProcessBuilder("taskkill", "/F", "/T", "/PID", pid.toString()).start().waitFor()
            } else {
                ProcessBuilder("kill", "-9", pid.toString()).start().waitFor()
            }
        } catch (e: Exception) {} finally {
            try { process.destroyForcibly() } catch (e: Exception) {}
        }
    }

    init {
        Runtime.getRuntime().addShutdownHook(Thread {
            killProcessTree(serverProcess)
            killProcessTree(playitProcess)
        })
    }
}

// ============================================================================
// AKILLI JAVA İNDİRİCİ MOTORU (AUTO-JAVA) - CROSS PLATFORM DESTEKLİ
// ============================================================================
object AutoJava {
    fun getRequiredJavaVersion(mcVersion: String): Int {
        return when {
            mcVersion.startsWith("1.8") || mcVersion.startsWith("1.12") || mcVersion.startsWith("1.16") -> 8
            mcVersion.startsWith("1.17") || mcVersion.startsWith("1.18") || mcVersion.startsWith("1.19") -> 17
            mcVersion.startsWith("1.20") || mcVersion.startsWith("1.21") -> 21
            // 26.1 ve gelecekteki tüm yeni sürümler için varsayılanı Java 25 yapıyoruz
            else -> 25
        }
    }

    suspend fun ensureJavaAndGetPath(mcVersion: String, logCallback: suspend (String) -> Unit): String {
        val jv = getRequiredJavaVersion(mcVersion)
        val baseRuntimesDir = AppConfig.JAVA_DIR
        val specificRuntimeDir = File(baseRuntimesDir, "jdk$jv")

        val osName = System.getProperty("os.name").lowercase()
        val isWin = osName.contains("win")
        val osString = when {
            isWin -> "windows"
            osName.contains("mac") -> "mac"
            else -> "linux"
        }
        val arch = System.getProperty("os.arch").lowercase()
        val archString = if (arch.contains("aarch64") || arch.contains("arm")) "aarch64" else "x64"
        val ext = if (isWin) "zip" else "tar.gz"
        val exeName = if (isWin) "java.exe" else "java"

        val existingJavaExe = if (specificRuntimeDir.exists()) specificRuntimeDir.walkTopDown().find { it.name == exeName && it.isFile } else null
        if (existingJavaExe != null && existingJavaExe.exists()) {
            if (!isWin) existingJavaExe.setExecutable(true)
            return existingJavaExe.absolutePath
        }

        logCallback("[SİSTEM] Minecraft $mcVersion için Taşınabilir Java $jv ($osString/$archString) gerekiyor. İndiriliyor...")
        specificRuntimeDir.mkdirs()
        val compressedFile = File(baseRuntimesDir, "jdk$jv.$ext")

        val url = "https://api.adoptium.net/v3/binary/latest/$jv/ga/$osString/$archString/jdk/hotspot/normal/eclipse"

        withContext(Dispatchers.IO) {
            var conn = URL(url).openConnection() as HttpURLConnection
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.instanceFollowRedirects = false
            conn.connect()

            var status = conn.responseCode
            var redirects = 0
            var currentUrl = url
            while (status in 300..308 && redirects < 5) {
                currentUrl = conn.getHeaderField("Location")
                conn = URL(currentUrl).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.instanceFollowRedirects = false
                conn.connect()
                status = conn.responseCode
                redirects++
            }

            if (status != 200) throw Exception("Java indirme sunucusu yanıt vermedi! HTTP Kodu: $status")

            conn.inputStream.use { inp -> compressedFile.outputStream().use { out -> inp.copyTo(out) } }

            logCallback("[SİSTEM] Java $jv indirildi, sisteme entegre ediliyor...")

            if (isWin) {
                unzipFolder(compressedFile, specificRuntimeDir)
            } else {
                ProcessBuilder("tar", "-xzf", compressedFile.absolutePath, "-C", specificRuntimeDir.absolutePath).start().waitFor()
            }
            compressedFile.delete()
        }

        val newJavaExe = specificRuntimeDir.walkTopDown().find { it.name == exeName && it.isFile }
        if (newJavaExe != null) {
            if (!isWin) newJavaExe.setExecutable(true)
            logCallback("[SİSTEM] Java $jv başarıyla kuruldu!")
            return newJavaExe.absolutePath
        } else {
            throw Exception("Java paketten çıkarılamadı veya çalıştırılabilir dosya bulunamadı!")
        }
    }
}

// ============================================================================
// CANLI SİSTEM TELEMETRİSİ (RAM VE CPU OKUYUCU)
// ============================================================================
fun getSystemCpuLoad(): Double {
    try {
        val osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean()
        val method = osBean.javaClass.getMethod("getSystemCpuLoad")
        method.isAccessible = true
        val load = method.invoke(osBean) as Double
        if (load >= 0.0) return load * 100.0
    } catch (e: Exception) {}

    try {
        val os = System.getProperty("os.name").lowercase()
        if (os.contains("win")) {
            val process = ProcessBuilder("wmic", "cpu", "get", "loadpercentage").start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val trimmed = line!!.trim()
                if (trimmed.isNotEmpty() && trimmed.all { it.isDigit() }) {
                    return trimmed.toDouble()
                }
            }
        }
    } catch (e: Exception) {}
    return 0.0
}

fun getSystemRamInfo(): Pair<Double, Double> {
    try {
        val osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean()
        val totalMethod = osBean.javaClass.getMethod("getTotalPhysicalMemorySize")
        val freeMethod = osBean.javaClass.getMethod("getFreePhysicalMemorySize")
        totalMethod.isAccessible = true
        freeMethod.isAccessible = true

        val totalBytes = (totalMethod.invoke(osBean) as Long).toDouble()
        val freeBytes = (freeMethod.invoke(osBean) as Long).toDouble()
        val usedBytes = totalBytes - freeBytes

        return Pair(usedBytes / (1024.0 * 1024.0 * 1024.0), totalBytes / (1024.0 * 1024.0 * 1024.0))
    } catch (e: Exception) {
        return Pair(0.0, 0.0)
    }
}

fun getProcessRamUsage(pid: Long): Double {
    try {
        val os = System.getProperty("os.name").lowercase()
        if (os.contains("win")) {
            val process = ProcessBuilder("wmic", "process", "where", "processid=$pid", "get", "WorkingSetSize").start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val trimmed = line!!.trim()
                if (trimmed.isNotEmpty() && trimmed.all { it.isDigit() }) {
                    val bytes = trimmed.toLongOrNull() ?: 0L
                    return bytes / (1024.0 * 1024.0)
                }
            }
        } else {
            val process = ProcessBuilder("ps", "-o", "rss=", "-p", pid.toString()).start()
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            if (line != null) {
                val kb = line.trim().toLongOrNull() ?: 0L
                return kb / 1024.0
            }
        }
    } catch (e: Exception) {}
    return 0.0
}

fun isPortAvailable(port: Int): Boolean {
    return try {
        ServerSocket(port).use { true }
    } catch (e: Exception) {
        false
    }
}

// ============================================================================
// RCON VE YARDIMCI MOTORLAR
// ============================================================================
fun buildRconPacket(id: Int, type: Int, body: ByteArray): ByteArray {
    val length = 10 + body.size
    val buffer = ByteBuffer.allocate(length + 4)
    buffer.order(ByteOrder.LITTLE_ENDIAN)
    buffer.putInt(length)
    buffer.putInt(id)
    buffer.putInt(type)
    buffer.put(body)
    buffer.put(0.toByte())
    buffer.put(0.toByte())
    return buffer.array()
}

fun readRconResponse(input: InputStream): String {
    try {
        val lengthBytes = ByteArray(4)
        if (input.read(lengthBytes) < 4) return ""
        val buffer = ByteBuffer.wrap(lengthBytes).order(ByteOrder.LITTLE_ENDIAN)
        val length = buffer.int
        if (length < 10 || length > 4096) return ""

        val payload = ByteArray(length)
        var bytesRead = 0
        while (bytesRead < length) {
            val result = input.read(payload, bytesRead, length - bytesRead)
            if (result == -1) break
            bytesRead += result
        }
        val bodyBytes = payload.copyOfRange(8, payload.size - 2)
        return String(bodyBytes, Charsets.UTF_8).trim()
    } catch (e: Exception) { return "" }
}

fun sendRconCommand(command: String, onResult: (String) -> Unit = {}) {
    if (command.isBlank()) return
    Thread {
        try {
            Socket("127.0.0.1", 25575).use { socket ->
                socket.soTimeout = 3000
                val out = socket.getOutputStream()
                val input = socket.getInputStream()
                out.write(buildRconPacket(1, 3, "aserver123".toByteArray()))
                out.flush()
                readRconResponse(input)

                out.write(buildRconPacket(2, 2, command.toByteArray()))
                out.flush()
                val response = readRconResponse(input)
                onResult(response)
            }
        } catch (e: Exception) { onResult("Bağlantı Hatası: Sunucu açık değil veya RCON aktif değil.") }
    }.start()
}

fun zipFolder(sourceFile: File, zipFile: File) {
    ZipOutputStream(BufferedOutputStream(FileOutputStream(zipFile))).use { zos ->
        val prefix = sourceFile.absolutePath + File.separator
        sourceFile.walkTopDown().forEach { file ->
            val zipFileName = file.absolutePath.removePrefix(prefix).replace("\\", "/")
            if (zipFileName.isNotEmpty() && zipFileName != sourceFile.absolutePath.replace("\\", "/")) {
                val entry = ZipEntry(zipFileName + (if (file.isDirectory) "/" else ""))
                zos.putNextEntry(entry)
                if (file.isFile) { file.inputStream().use { it.copyTo(zos) } }
            }
        }
    }
}

fun unzipFolder(zipFile: File, targetDir: File) {
    ZipInputStream(java.io.BufferedInputStream(java.io.FileInputStream(zipFile))).use { zis ->
        val targetDirPath = targetDir.canonicalFile.canonicalPath + File.separator
        var entry = zis.nextEntry
        while (entry != null) {
            val newFile = File(targetDir, entry.name)
            if (!newFile.canonicalPath.startsWith(targetDirPath)) {
                throw SecurityException("Zip Slip zafiyeti engellendi: ${entry.name}")
            }
            if (entry.isDirectory) {
                newFile.mkdirs()
            } else {
                newFile.parentFile?.mkdirs()
                FileOutputStream(newFile).use { fos -> zis.copyTo(fos) }
            }
            zis.closeEntry()
            entry = zis.nextEntry
        }
    }
}

data class SpigetPlugin(val id: Int, val name: String, val tag: String)
data class ModrinthMod(val id: String, val title: String, val description: String)
data class ScheduledTask(val id: String = UUID.randomUUID().toString(), val type: String, val payload: String, val intervalMinutes: Float, val isRunning: Boolean = true)

fun parseSpiget(json: String): List<SpigetPlugin> {
    val list = mutableListOf<SpigetPlugin>()
    val objects = json.split("}")
    for (obj in objects) {
        val id = Regex("\"id\":\\s*(\\d+)").find(obj)?.groupValues?.get(1)?.toIntOrNull()
        val name = Regex("\"name\":\\s*\"([^\"]+)\"").find(obj)?.groupValues?.get(1)
        val tag = Regex("\"tag\":\\s*\"([^\"]*)\"").find(obj)?.groupValues?.get(1) ?: "Açıklama yok"
        if (id != null && name != null) { list.add(SpigetPlugin(id, name, tag)) }
    }
    return list.distinctBy { it.id }
}

fun parseModrinth(json: String): List<ModrinthMod> {
    val list = mutableListOf<ModrinthMod>()
    val objects = json.split("}")
    for (obj in objects) {
        val id = Regex("\"project_id\":\\s*\"([^\"]+)\"").find(obj)?.groupValues?.get(1)
        val title = Regex("\"title\":\\s*\"([^\"]+)\"").find(obj)?.groupValues?.get(1)
        val desc = Regex("\"description\":\\s*\"([^\"]*)\"").find(obj)?.groupValues?.get(1) ?: ""
        if (id != null && title != null) { list.add(ModrinthMod(id, title, desc)) }
    }
    return list.distinctBy { it.id }
}

fun parseModrinthVersion(json: String): Pair<String, String>? {
    val objects = json.split("}")
    for (obj in objects) {
        val url = Regex("\"url\":\\s*\"([^\"]+)\"").find(obj)?.groupValues?.get(1)
        val filename = Regex("\"filename\":\\s*\"([^\"]+)\"").find(obj)?.groupValues?.get(1)
        if (url != null && filename != null && url.startsWith("http")) { return Pair(url, filename) }
    }
    return null
}

// ============================================================================
// RENK PALETİ (SİBER-MODERN UI)
// ============================================================================
val BgDeepDark = Color(0xFF0B0F19)
val SurfaceDark = Color(0xFF111827)
val CardDark = Color(0xFF1F2937)
val AccentEmerald = Color(0xFF10B981)
val AccentBlue = Color(0xFF3B82F6)
val AccentRed = Color(0xFFEF4444)
val AccentOrange = Color(0xFFF59E0B)
val BorderColor = Color(0xFF374151)

@Composable
fun CustomDropdownMenu(label: String, options: List<String>, selectedOption: String, onOptionSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = selectedOption, onValueChange = {}, readOnly = true, label = { Text(label, color = Color.Gray) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = AccentEmerald, unfocusedBorderColor = BorderColor, backgroundColor = SurfaceDark),
            shape = RoundedCornerShape(12.dp),
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Gray) }
        )
        Spacer(modifier = Modifier.matchParentSize().clickable { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(CardDark)) {
            options.forEach { selectionOption ->
                DropdownMenuItem(onClick = { onOptionSelected(selectionOption); expanded = false }) { Text(text = selectionOption, color = Color.White) }
            }
        }
    }
}

@Composable
fun NavItem(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .background(if (isSelected) AccentEmerald.copy(alpha = 0.15f) else Color.Transparent)
            .padding(vertical = 14.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = title, tint = if (isSelected) AccentEmerald else Color.Gray, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, color = if (isSelected) AccentEmerald else Color.LightGray, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}

// ============================================================================
// ANA UYGULAMA İSKELETİ (FLOATING UI - YÜZEN KATLANABİLİR MENÜ)
// ============================================================================
@Composable
fun App() {
    val CURRENT_VERSION = "1.4.0"
    var showUpdateDialog by remember { mutableStateOf(false) }
    var updateUrl by remember { mutableStateOf("") }
    var latestVersionStr by remember { mutableStateOf("") }

    val appScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    DisposableEffect(Unit) {
        onDispose { appScope.cancel() }
    }

    var currentScreen by remember { mutableStateOf("MyServers") }
    var activeServerName by remember { mutableStateOf("") }

    var consoleText by remember { mutableStateOf("[AServer] Masaüstü arayüzü hazır.\n[AServer] Sistem boşta.\n") }
    var isProcessing by remember { mutableStateOf(false) }
    var isServerRunning by remember { mutableStateOf(false) }
    var isServerReady by remember { mutableStateOf(false) }
    var isShuttingDown by remember { mutableStateOf(false) }

    var activeProcess by remember { mutableStateOf<Process?>(null) }
    var activePlayitProcess by remember { mutableStateOf<Process?>(null) }
    var allocatedRam by remember { mutableStateOf("2") }

    val scheduledTasks = remember { mutableStateListOf<ScheduledTask>() }

    // Yüzen Menü State'i
    var isMenuExpanded by remember { mutableStateOf(false) }
    val menuWidth by animateDpAsState(targetValue = if (isMenuExpanded) 240.dp else 80.dp)

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            try {
                val url = java.net.URL("https://badiadam.github.io/AServer-Web/latest.json")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                connection.connectTimeout = 5000
                connection.readTimeout = 5000
                val response = connection.getInputStream().bufferedReader().use { it.readText() }
                
                // Regex ile basit JSON ayrıştırma
                val versionRegex = Regex("\"version\"\\s*:\\s*\"(.*?)\"")
                val urlRegex = Regex("\"url\"\\s*:\\s*\"(.*?)\"")
                
                val fetchedVersion = versionRegex.find(response)?.groupValues?.get(1)
                val fetchedUrl = urlRegex.find(response)?.groupValues?.get(1)
                
                if (fetchedVersion != null && fetchedUrl != null) {
                    // Basit String karşılaştırması (1.5.0 > 1.4.0)
                    if (fetchedVersion > CURRENT_VERSION) {
                        latestVersionStr = fetchedVersion
                        updateUrl = fetchedUrl
                        showUpdateDialog = true
                    }
                }
            } catch (e: Exception) {
                // İnternet yoksa veya site çökerse sessizce kal, uygulamayı bozma
                println("GÜNCELLEME RADARI HATASI: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(isServerRunning, activeServerName) {
        if (isServerRunning && activeServerName.isNotEmpty()) {
            isServerReady = false
            while (isServerRunning && !isServerReady) {
                val serverReady = withContext(Dispatchers.IO) {
                    try {
                        Socket().use { s ->
                            s.connect(java.net.InetSocketAddress("127.0.0.1", 25575), 1000)
                            true
                        }
                    } catch (e: Exception) {
                        false
                    }
                }
                isServerReady = serverReady
                if (!serverReady) delay(2000)
            }
        } else {
            isServerReady = false
        }
    }

    LaunchedEffect(isServerReady) {
        if (isServerReady && isServerRunning) {
            val successMsg = "\n[SİSTEM] RCON Bağlantısı Başarılı! Sunucu tamamen aktif ve komutlara açık."
            val newText = consoleText + successMsg
            val lines = newText.split("\n")
            consoleText = if (lines.size > 500) lines.takeLast(500).joinToString("\n") else newText
        }
    }

    scheduledTasks.filter { it.isRunning }.forEach { task ->
        LaunchedEffect(task.id, isServerReady) {
            if (isServerReady) {
                while (true) {
                    delay((task.intervalMinutes * 60 * 1000L).toLong())
                    val cmd = if (task.type == "Duyuru") "say [Duyuru] ${task.payload}" else task.payload
                    sendRconCommand(cmd) { response ->
                        appScope.launch(Dispatchers.Main) {
                        val logMsg = "\n[OTOMASYON] Görev Çalıştı: $cmd" + if (response.isNotBlank()) "\n[RCON] $response" else ""
                        val newText = consoleText + logMsg
                        val lines = newText.split("\n")
                        consoleText = if (lines.size > 500) lines.takeLast(500).joinToString("\n") else newText
                        }
                    }
                }
            }
        }
    }

    MaterialTheme(colors = darkColors(background = BgDeepDark)) {
        if (showUpdateDialog) {
            AlertDialog(
                onDismissRequest = { /* Zorunlu güncelleme değilse kapatılabilir */ showUpdateDialog = false },
                backgroundColor = Color(0xFF2D2D2D), // CardDark
                title = { Text("Yeni Güncelleme Mevcut!", color = Color.White, fontWeight = FontWeight.Bold) },
                text = { Text("AServer'ın yeni sürümü (v$latestVersionStr) yayınlandı.\nŞu anki sürümünüz: v$CURRENT_VERSION\n\nYeni özellikleri indirmek ister misiniz?", color = Color.LightGray) },
                confirmButton = {
                    Button(
                        onClick = {
                            showUpdateDialog = false
                            try {
                                java.awt.Desktop.getDesktop().browse(java.net.URI(updateUrl))
                            } catch (e: Exception) { e.printStackTrace() }
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF10B981)) // AccentEmerald
                    ) { Text("ŞİMDİ İNDİR", color = Color.Black, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showUpdateDialog = false }) { Text("Daha Sonra", color = Color.Gray) }
                }
            )
        }

        Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {

            // YÜZEN KATLANABİLİR MENÜ KISMI
            Column(
                modifier = Modifier
                    .width(menuWidth)
                    .fillMaxHeight()
                    .background(SurfaceDark)
                    .border(BorderStroke(1.dp, BorderColor))
                    .padding(vertical = 20.dp, horizontal = 12.dp),
                horizontalAlignment = if (isMenuExpanded) Alignment.Start else Alignment.CenterHorizontally
            ) {
                // Menü Toggle Butonu
                IconButton(
                    onClick = { isMenuExpanded = !isMenuExpanded },
                    modifier = Modifier.padding(bottom = 30.dp)
                ) {
                    Icon(Icons.Default.Menu, contentDescription = "Menü", tint = AccentEmerald, modifier = Modifier.size(32.dp))
                }

                // Navigasyon İtemleri
                NavItem(if(isMenuExpanded) "Konsol" else "", Icons.Default.Terminal, currentScreen == "Dashboard") { currentScreen = "Dashboard" }
                Spacer(Modifier.height(12.dp))
                NavItem(if(isMenuExpanded) "Sunucularım" else "", Icons.Default.Storage, currentScreen == "MyServers") { currentScreen = "MyServers" }
                Spacer(Modifier.height(12.dp))
                NavItem(if(isMenuExpanded) "Dosyalar" else "", Icons.Default.Folder, currentScreen == "FileManager") { currentScreen = "FileManager" }
                Spacer(Modifier.height(12.dp))
                NavItem(if(isMenuExpanded) "Oyuncular" else "", Icons.Default.People, currentScreen == "Players") { currentScreen = "Players" }
                Spacer(Modifier.height(12.dp))
                NavItem(if(isMenuExpanded) "Yeni Sunucu" else "", Icons.Default.AddCircle, currentScreen == "CreateServer") { currentScreen = "CreateServer" }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (currentScreen) {
                    "Dashboard" -> DashboardScreen(
                        activeServerName = activeServerName,
                        consoleText = consoleText,
                        onConsoleAppend = { appendedText ->
                            val newText = consoleText + appendedText
                            val lines = newText.split("\n")
                            consoleText = if (lines.size > 500) lines.takeLast(500).joinToString("\n") else newText
                        },
                        isServerRunning = isServerRunning, onServerRunningChange = { isServerRunning = it },
                        isServerReady = isServerReady, onServerReadyChange = { isServerReady = it },
                        isShuttingDown = isShuttingDown, onShuttingDownChange = { isShuttingDown = it },
                        isProcessing = isProcessing, onProcessingChange = { isProcessing = it },
                        activeProcess = activeProcess, onActiveProcessChange = { activeProcess = it },
                        activePlayitProcess = activePlayitProcess, onPlayitProcessChange = { activePlayitProcess = it },
                        allocatedRam = allocatedRam, onRamChange = { allocatedRam = it },
                        scheduledTasks = scheduledTasks,
                        lifecycleScope = appScope
                    )
                    "MyServers" -> MyServersScreen(
                        activeServerName = activeServerName,
                        isServerRunning = isServerRunning || isProcessing,
                        onPlayServer = { serverName -> activeServerName = serverName; currentScreen = "Dashboard" }
                    )
                    "FileManager" -> FileManagerScreen()
                    "Players" -> PlayersScreen(
                        activeServerName = activeServerName,
                        isServerRunning = isServerRunning,
                        isServerReady = isServerReady
                    )
                    "CreateServer" -> CreateServerScreen(onServerCreated = { currentScreen = "MyServers" })
                }
            }
        }
    }
}


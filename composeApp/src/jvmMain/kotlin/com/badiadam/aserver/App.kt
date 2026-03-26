package com.badiadam.aserver

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
import kotlinx.coroutines.Dispatchers
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
            else -> 21
        }
    }

    suspend fun ensureJavaAndGetPath(mcVersion: String, logCallback: suspend (String) -> Unit): String {
        val jv = getRequiredJavaVersion(mcVersion)
        val baseRuntimesDir = File(System.getProperty("user.home"), "Desktop/AServer/runtimes")
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
        var entry = zis.nextEntry
        while (entry != null) {
            val newFile = File(targetDir, entry.name)
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
// ANA UYGULAMA İSKELETİ
// ============================================================================
@Composable
fun App() {
    var currentScreen by remember { mutableStateOf("MyServers") }
    var activeServerName by remember { mutableStateOf("") }

    var consoleText by remember { mutableStateOf("[AServer] Masaüstü arayüzü hazır.\n[AServer] Sistem boşta.\n") }
    var isProcessing by remember { mutableStateOf(false) }
    var isServerRunning by remember { mutableStateOf(false) }
    var isServerReady by remember { mutableStateOf(false) }

    var activeProcess by remember { mutableStateOf<Process?>(null) }
    var activePlayitProcess by remember { mutableStateOf<Process?>(null) }
    var allocatedRam by remember { mutableStateOf("2") }

    val scheduledTasks = remember { mutableStateListOf<ScheduledTask>() }

    LaunchedEffect(isServerRunning, activeServerName) {
        if (isServerRunning && activeServerName.isNotEmpty()) {
            isServerReady = false
            while (isServerRunning && !isServerReady) {
                withContext(Dispatchers.IO) {
                    try {
                        Socket().use { s ->
                            s.connect(java.net.InetSocketAddress("127.0.0.1", 25575), 1000)
                            isServerReady = true
                        }
                    } catch (e: Exception) {
                        isServerReady = false
                    }
                }
                if (!isServerReady) delay(2000)
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
                        val logMsg = "\n[OTOMASYON] Görev Çalıştı: $cmd" + if (response.isNotBlank()) "\n[RCON] $response" else ""
                        val newText = consoleText + logMsg
                        val lines = newText.split("\n")
                        consoleText = if (lines.size > 500) lines.takeLast(500).joinToString("\n") else newText
                    }
                }
            }
        }
    }

    MaterialTheme(colors = darkColors(background = BgDeepDark)) {
        Row(modifier = Modifier.fillMaxSize().background(MaterialTheme.colors.background)) {

            Column(modifier = Modifier.width(260.dp).fillMaxHeight().background(SurfaceDark).border(BorderStroke(1.dp, BorderColor)).padding(20.dp)) {

                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 40.dp, top = 10.dp)) {
                    Icon(Icons.Default.Dns, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("AServer", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                }

                NavItem("Konsol", Icons.Default.Terminal, currentScreen == "Dashboard") { currentScreen = "Dashboard" }
                Spacer(Modifier.height(8.dp))
                NavItem("Sunucularım", Icons.Default.Storage, currentScreen == "MyServers") { currentScreen = "MyServers" }
                Spacer(Modifier.height(8.dp))
                NavItem("Dosya Yöneticisi", Icons.Default.Folder, currentScreen == "FileManager") { currentScreen = "FileManager" }
                Spacer(Modifier.height(8.dp))
                NavItem("Oyuncular", Icons.Default.People, currentScreen == "Players") { currentScreen = "Players" }
                Spacer(Modifier.height(8.dp))
                NavItem("Yeni Sunucu", Icons.Default.AddCircle, currentScreen == "CreateServer") { currentScreen = "CreateServer" }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (currentScreen) {
                    "Dashboard" -> DashboardScreen(
                        activeServerName = activeServerName,
                        consoleText = consoleText,
                        onConsoleTextChange = { newText ->
                            val lines = newText.split("\n")
                            consoleText = if (lines.size > 500) lines.takeLast(500).joinToString("\n") else newText
                        },
                        isServerRunning = isServerRunning, onServerRunningChange = { isServerRunning = it },
                        isServerReady = isServerReady,
                        isProcessing = isProcessing, onProcessingChange = { isProcessing = it },
                        activeProcess = activeProcess, onActiveProcessChange = { activeProcess = it },
                        activePlayitProcess = activePlayitProcess, onPlayitProcessChange = { activePlayitProcess = it },
                        allocatedRam = allocatedRam, onRamChange = { allocatedRam = it },
                        scheduledTasks = scheduledTasks
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

// ============================================================================
// EKRAN: OYUNCULAR VE ADALET SARAYI (RCON)
// ============================================================================
@Composable
fun PlayersScreen(activeServerName: String, isServerRunning: Boolean, isServerReady: Boolean) {
    var selectedTab by remember { mutableStateOf("Online") }
    var playersList by remember { mutableStateOf<List<String>>(emptyList()) }
    var bannedList by remember { mutableStateOf<List<String>>(emptyList()) }
    var whitelistList by remember { mutableStateOf<List<String>>(emptyList()) }
    var whitelistEnabled by remember { mutableStateOf(false) }
    var newWlPlayer by remember { mutableStateOf("") }
    var rconStatus by remember { mutableStateOf("Bağlantı Bekleniyor...") }

    LaunchedEffect(isServerReady, activeServerName, selectedTab) {
        if (isServerReady && activeServerName.isNotEmpty()) {
            val serverDir = File(System.getProperty("user.home"), "Desktop/AServer/servers/$activeServerName")
            while (true) {
                if (selectedTab == "Online") {
                    sendRconCommand("list") { response ->
                        if (response.contains("online:")) {
                            val names = response.substringAfter("online:").trim()
                            playersList = if (names.isNotEmpty()) names.split(",").map { it.trim() } else emptyList()
                            rconStatus = "Bağlandı (Aktif)"
                        } else {
                            rconStatus = "Bağlantı Hatası"
                            playersList = emptyList()
                        }
                    }
                } else if (selectedTab == "Banlılar") {
                    val banFile = File(serverDir, "banned-players.json")
                    if (banFile.exists()) {
                        val content = banFile.readText()
                        val matches = Regex("\"name\":\\s*\"([^\"]+)\"").findAll(content)
                        bannedList = matches.map { it.groupValues[1] }.toList().distinct()
                    } else bannedList = emptyList()
                } else if (selectedTab == "Whitelist") {
                    val wlFile = File(serverDir, "whitelist.json")
                    if (wlFile.exists()) {
                        val content = wlFile.readText()
                        val matches = Regex("\"name\":\\s*\"([^\"]+)\"").findAll(content)
                        whitelistList = matches.map { it.groupValues[1] }.toList().distinct()
                    } else whitelistList = emptyList()

                    val propsFile = File(serverDir, "server.properties")
                    if (propsFile.exists()) { whitelistEnabled = propsFile.readText().contains("white-list=true") }
                }
                delay(3000)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(40.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Adalet Sarayı", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        !isServerRunning -> "Sunucu Kapalı - RCON Devre Dışı"
                        !isServerReady -> "RCON Durumu: Başlatılıyor, bağlantı bekleniyor..."
                        else -> "RCON Durumu: $rconStatus"
                    },
                    color = if (isServerReady && rconStatus.contains("Bağlandı")) AccentEmerald else Color.Gray,
                    fontSize = 14.sp
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = { selectedTab = "Online" }, colors = ButtonDefaults.buttonColors(backgroundColor = if (selectedTab == "Online") AccentEmerald else CardDark), shape = RoundedCornerShape(12.dp), modifier = Modifier.height(45.dp)) { Text("Aktif Oyuncular", color = if (selectedTab == "Online") Color.Black else Color.White, fontWeight = FontWeight.SemiBold) }
                Button(onClick = { selectedTab = "Banlılar" }, colors = ButtonDefaults.buttonColors(backgroundColor = if (selectedTab == "Banlılar") AccentRed else CardDark), shape = RoundedCornerShape(12.dp), modifier = Modifier.height(45.dp)) { Text("Banlılar", color = if (selectedTab == "Banlılar") Color.White else Color.White, fontWeight = FontWeight.SemiBold) }
                Button(onClick = { selectedTab = "Whitelist" }, colors = ButtonDefaults.buttonColors(backgroundColor = if (selectedTab == "Whitelist") AccentBlue else CardDark), shape = RoundedCornerShape(12.dp), modifier = Modifier.height(45.dp)) { Text("Whitelist", color = if (selectedTab == "Whitelist") Color.White else Color.White, fontWeight = FontWeight.SemiBold) }
            }
        }
        Spacer(Modifier.height(40.dp))

        if (!isServerRunning || !isServerReady) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (!isServerRunning) "Oyuncuları yönetmek için sunucunun çalışıyor olması gerekir." else "Sunucu motoru ateşleniyor, lütfen bekleyin...",
                    color = Color.Gray,
                    fontSize = 16.sp
                )
            }
        } else {
            when (selectedTab) {
                "Online" -> {
                    if (playersList.isEmpty()) { Text("Şu an sunucuda kimse yok.", color = Color.Gray) }
                    else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(playersList) { player ->
                                Card(backgroundColor = CardDark, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
                                    Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Person, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(28.dp))
                                            Spacer(Modifier.width(16.dp))
                                            Text(player, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                            Button(onClick = { sendRconCommand("op $player") }, colors = ButtonDefaults.buttonColors(backgroundColor = AccentBlue), shape = RoundedCornerShape(8.dp)) { Text("OP VER", color = Color.White) }
                                            Button(onClick = { sendRconCommand("kick $player AServer panelinden atıldınız.") }, colors = ButtonDefaults.buttonColors(backgroundColor = AccentOrange), shape = RoundedCornerShape(8.dp)) { Text("KİCK", color = Color.Black) }
                                            Button(onClick = { sendRconCommand("ban $player Kuralları ihlal ettin.") }, colors = ButtonDefaults.buttonColors(backgroundColor = AccentRed), shape = RoundedCornerShape(8.dp)) { Text("BAN", color = Color.White) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                "Banlılar" -> {
                    if (bannedList.isEmpty()) { Text("Yasaklı oyuncu bulunmuyor.", color = Color.Gray) }
                    else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(bannedList) { player ->
                                Card(backgroundColor = CardDark, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
                                    Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Block, contentDescription = null, tint = AccentRed, modifier = Modifier.size(28.dp))
                                            Spacer(Modifier.width(16.dp))
                                            Text(player, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Button(onClick = { sendRconCommand("pardon $player") }, colors = ButtonDefaults.buttonColors(backgroundColor = AccentEmerald), shape = RoundedCornerShape(8.dp)) { Text("AFFET (UNBAN)", color = Color.Black, fontWeight = FontWeight.Bold) }
                                    }
                                }
                            }
                        }
                    }
                }
                "Whitelist" -> {
                    Card(backgroundColor = CardDark, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("Whitelist Kalkanı", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                    Spacer(Modifier.height(4.dp))
                                    Text("Sadece listedeki oyuncular girebilir.", color = Color.Gray, fontSize = 14.sp)
                                }
                                Switch(checked = whitelistEnabled, onCheckedChange = {
                                    whitelistEnabled = it
                                    sendRconCommand(if(it) "whitelist on" else "whitelist off")
                                }, colors = SwitchDefaults.colors(checkedThumbColor = AccentBlue))
                            }
                            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = newWlPlayer, onValueChange = { newWlPlayer = it },
                                    label = { Text("Oyuncu Adı", color = Color.Gray) },
                                    modifier = Modifier.weight(1f),
                                    colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = AccentBlue, backgroundColor = SurfaceDark),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                Spacer(Modifier.width(16.dp))
                                Button(onClick = { if (newWlPlayer.isNotBlank()) { sendRconCommand("whitelist add $newWlPlayer"); newWlPlayer = "" } }, colors = ButtonDefaults.buttonColors(backgroundColor = AccentBlue), modifier = Modifier.height(55.dp), shape = RoundedCornerShape(12.dp)) { Text("EKLE", color = Color.White, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }

                    if (whitelistList.isEmpty()) { Text("Liste boş.", color = Color.Gray) }
                    else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(whitelistList) { player ->
                                Card(backgroundColor = CardDark, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
                                    Row(modifier = Modifier.padding(20.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = AccentBlue, modifier = Modifier.size(28.dp))
                                            Spacer(Modifier.width(16.dp))
                                            Text(player, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Button(onClick = { sendRconCommand("whitelist remove $player") }, colors = ButtonDefaults.buttonColors(backgroundColor = AccentRed), shape = RoundedCornerShape(8.dp)) { Text("KALDIR", color = Color.White) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// EKRAN: DASHBOARD (ZOMBİ MOTOR KALKANI GÜNCELLENDİ)
// ============================================================================
@Composable
fun DashboardScreen(
    activeServerName: String,
    consoleText: String, onConsoleTextChange: (String) -> Unit,
    isServerRunning: Boolean, onServerRunningChange: (Boolean) -> Unit,
    isServerReady: Boolean,
    isProcessing: Boolean, onProcessingChange: (Boolean) -> Unit,
    activeProcess: Process?, onActiveProcessChange: (Process?) -> Unit,
    activePlayitProcess: Process?, onPlayitProcessChange: (Process?) -> Unit,
    allocatedRam: String, onRamChange: (String) -> Unit,
    scheduledTasks: MutableList<ScheduledTask>
) {
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    var playitIp by remember { mutableStateOf<String?>(null) }
    var playitLink by remember { mutableStateOf<String?>(null) }
    var copyStatus by remember { mutableStateOf("") }
    var commandInput by remember { mutableStateOf("") }

    var showTaskDialog by remember { mutableStateOf(false) }
    var taskType by remember { mutableStateOf("Duyuru") }
    var taskPayload by remember { mutableStateOf("") }
    var taskInterval by remember { mutableStateOf(10f) }

    var isShuttingDown by remember { mutableStateOf(false) }

    var isPlayitEnabled by remember { mutableStateOf(false) }
    var serverPort by remember { mutableStateOf("25565") }

    var currentServerRamMb by remember { mutableStateOf(0.0) }
    var systemUsedRamGb by remember { mutableStateOf(0.0) }
    var systemTotalRamGb by remember { mutableStateOf(0.0) }
    var currentCpuUsage by remember { mutableStateOf(0.0) }

    val macroCommands = listOf(
        "Sabah Yap" to "time set day",
        "Gece Yap" to "time set night",
        "Hava Açık" to "weather clear",
        "Yaratıcı Mod (@a)" to "gamemode creative @a",
        "Hayatta Kalma (@a)" to "gamemode survival @a",
        "Tümünü Kaydet" to "save-all"
    )

    LaunchedEffect(consoleText) { scrollState.animateScrollTo(scrollState.maxValue) }

    LaunchedEffect(isServerRunning, activeServerName) {
        if (activeServerName.isNotEmpty()) {
            val serverDir = File(System.getProperty("user.home"), "Desktop/AServer/servers/$activeServerName")
            val pFile = File(serverDir, "playit_enabled.txt")
            isPlayitEnabled = pFile.exists() && pFile.readText() == "true"

            val propsFile = File(serverDir, "server.properties")
            if (propsFile.exists()) {
                val portLine = propsFile.readLines().find { it.startsWith("server-port=") }
                serverPort = portLine?.substringAfter("=")?.trim() ?: "25565"
            }
        }

        if (!isServerRunning) isShuttingDown = false

        if (isServerRunning) {
            val serverDir = File(System.getProperty("user.home"), "Desktop/AServer/servers/$activeServerName")
            val playitLogFile = File(serverDir, "playit_log.txt")

            while (isServerRunning) {
                if (playitLogFile.exists()) {
                    try {
                        val rawText = playitLogFile.readText()
                        val cleanText = rawText.replace(Regex("\u001B\\[[;\\d]*[a-zA-Z]"), "")
                        val ipRegex = Regex("([a-zA-Z0-9.-]+(?:joinmc\\.link|playit\\.gg))")
                        val matches = ipRegex.findAll(cleanText)
                        var tempIp: String? = null
                        for (m in matches) {
                            val found = m.value
                            if (found != "playit.gg" && found != "api.playit.gg" && !found.contains("playit.gg/claim")) {
                                tempIp = found
                                break
                            }
                        }
                        if (tempIp != null) playitIp = tempIp

                        if (playitIp == null) {
                            val match = Regex("https://playit\\.gg/claim/[a-zA-Z0-9]+").find(cleanText)
                            if (match != null && playitLink != match.value) {
                                playitLink = match.value
                            }
                        }
                    } catch (e: Exception) {}
                }
                delay(2000)
            }
        } else {
            playitIp = null
            playitLink = null
            copyStatus = ""
        }
    }

    LaunchedEffect(isServerRunning, activeProcess) {
        if (isServerRunning) {
            while (isServerRunning) {
                val pid = try { activeProcess?.pid() ?: -1L } catch(e: Exception) { -1L }
                withContext(Dispatchers.IO) {
                    if (pid != -1L) {
                        currentServerRamMb = getProcessRamUsage(pid)
                    }
                    currentCpuUsage = getSystemCpuLoad()
                    val ramInfo = getSystemRamInfo()
                    systemUsedRamGb = ramInfo.first
                    systemTotalRamGb = ramInfo.second
                }
                delay(3000)
            }
        } else {
            currentServerRamMb = 0.0
            currentCpuUsage = 0.0
            systemUsedRamGb = 0.0
            systemTotalRamGb = 0.0
        }
    }

    if (showTaskDialog) {
        AlertDialog(
            onDismissRequest = { showTaskDialog = false },
            backgroundColor = CardDark,
            modifier = Modifier.width(500.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, BorderColor, RoundedCornerShape(16.dp)),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, contentDescription = null, tint = AccentOrange)
                    Spacer(Modifier.width(8.dp))
                    Text("Otomasyon ve Zamanlayıcı", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Card(backgroundColor = SurfaceDark, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Yeni Görev Ekle", color = AccentEmerald, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(Modifier.height(12.dp))
                            CustomDropdownMenu("Görev Türü", listOf("Duyuru", "Komut"), taskType) { taskType = it }
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = taskPayload, onValueChange = { taskPayload = it },
                                label = { Text(if (taskType == "Duyuru") "Gönderilecek Mesaj" else "Çalıştırılacak Komut (Örn: save-all)", color = Color.Gray) },
                                colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = AccentEmerald),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                            Text("Tekrar Etme Süresi: ${taskInterval.toInt()} dakika", color = Color.LightGray, fontSize = 12.sp)
                            Slider(value = taskInterval, onValueChange = { taskInterval = it }, valueRange = 1f..60f, colors = SliderDefaults.colors(thumbColor = AccentEmerald, activeTrackColor = AccentEmerald))
                            Button(
                                onClick = {
                                    if (taskPayload.isNotBlank()) {
                                        scheduledTasks.add(ScheduledTask(type = taskType, payload = taskPayload, intervalMinutes = taskInterval))
                                        taskPayload = ""
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(backgroundColor = AccentEmerald), modifier = Modifier.align(Alignment.End), shape = RoundedCornerShape(8.dp)
                            ) { Text("EKLE", color = Color.Black, fontWeight = FontWeight.Bold) }
                        }
                    }

                    if (scheduledTasks.isNotEmpty()) {
                        Text("Aktif Görevler", color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                        LazyColumn(modifier = Modifier.heightIn(max = 200.dp)) {
                            items(scheduledTasks) { task ->
                                Card(backgroundColor = SurfaceDark, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                    Row(modifier = Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(task.type, color = AccentEmerald, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            Text(task.payload, color = Color.White, fontSize = 14.sp)
                                            Text("Her ${task.intervalMinutes.toInt()} dakikada bir", color = Color.Gray, fontSize = 10.sp)
                                        }
                                        Switch(
                                            checked = task.isRunning,
                                            onCheckedChange = { isChecked ->
                                                val index = scheduledTasks.indexOf(task)
                                                if (index != -1) scheduledTasks[index] = task.copy(isRunning = isChecked)
                                            },
                                            colors = SwitchDefaults.colors(checkedThumbColor = AccentEmerald)
                                        )
                                        IconButton(onClick = { scheduledTasks.remove(task) }) { Icon(Icons.Default.Delete, contentDescription = "Sil", tint = AccentRed) }
                                    }
                                }
                            }
                        }
                    } else {
                        Text("Henüz bir otomasyon görevi eklenmedi.", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTaskDialog = false }) { Text("Kapat", color = Color.Gray) } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(40.dp)) {
        if (activeServerName.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Lütfen 'Sunucularım' sekmesinden sunucu seçin.", color = Color.Gray) }
            return@Column
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Konsol: $activeServerName", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = when {
                            isServerReady && !isShuttingDown -> "Sistem durumu: 🟢 AKTİF"
                            isShuttingDown -> "Sistem durumu: 🟠 KAPATILIYOR... (Harita Kaydediliyor)"
                            isServerRunning -> "Sistem durumu: 🟡 BAŞLATILIYOR... (Lütfen bekleyin)"
                            isProcessing -> "Sistem durumu: 🟡 İşlem yapılıyor..."
                            else -> "Sistem durumu: 🔴 Çevrimdışı"
                        },
                        color = when {
                            isServerReady && !isShuttingDown -> AccentEmerald
                            isShuttingDown || isServerRunning || isProcessing -> AccentOrange
                            else -> Color.Gray
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                if (isServerReady && !isShuttingDown) {
                    Spacer(Modifier.width(20.dp))
                    IconButton(onClick = { showTaskDialog = true }, modifier = Modifier.background(CardDark, RoundedCornerShape(12.dp)).border(1.dp, BorderColor, RoundedCornerShape(12.dp))) {
                        Icon(Icons.Default.Timer, contentDescription = "Otomasyon", tint = AccentOrange)
                    }
                }
            }

            Button(
                onClick = {
                    if (isServerRunning && !isShuttingDown) {
                        isShuttingDown = true
                        coroutineScope.launch(Dispatchers.IO) {
                            withContext(Dispatchers.Main) { onConsoleTextChange(consoleText + "\n[Sistem] Kapatma komutu gönderiliyor...") }

                            // TIKANIKLIK ÇÖZÜLDÜ: Doğrudan baytları gönder ve boruyu zorla boşalt
                            try {
                                val out = activeProcess?.outputStream
                                out?.write("stop\n".toByteArray(Charsets.UTF_8))
                                out?.flush()
                            } catch (e: Exception) {}

                            // NOT: Playit'i burada anında öldürmüyoruz.
                            // Ana döngü (process.waitFor) sunucunun kapandığını algılayınca kendi öldürecek.

                            // SİGORTA: Eğer sunucu 15 saniye içinde kapanmazsa, ZORLA öldür (Anti-Softlock)
                            delay(15000)
                            if (activeProcess?.isAlive == true) {
                                withContext(Dispatchers.Main) { onConsoleTextChange(consoleText + "\n[Sistem] Sunucu yanıt vermiyor, zorla kapatılıyor (Timeout)!") }
                                ProcessManager.killProcessTree(activeProcess)
                                ProcessManager.killProcessTree(activePlayitProcess)
                            }
                        }
                    } else if (!isProcessing && !isServerRunning && !isShuttingDown) {
                        onProcessingChange(true)
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                val serverDir = File(System.getProperty("user.home"), "Desktop/AServer/servers/$activeServerName")

                                val propsFile = File(serverDir, "server.properties")
                                var portToCheck = 25565
                                if (propsFile.exists()) {
                                    val portLine = propsFile.readLines().find { it.startsWith("server-port=") }
                                    portToCheck = portLine?.substringAfter("=")?.trim()?.toIntOrNull() ?: 25565
                                }

                                if (!isPortAvailable(portToCheck)) {
                                    withContext(Dispatchers.Main) {
                                        onConsoleTextChange(consoleText + "\n[HATA] $portToCheck numaralı port şu an meşgul! \n[ÇÖZÜM] Arka planda asılı kalmış bir Java işlemi olabilir veya başka bir program bu portu kullanıyor.")
                                        onProcessingChange(false)
                                    }
                                    return@launch
                                }

                                val mcVerFile = File(serverDir, "mc_version.txt")
                                val mcVersion = if (mcVerFile.exists()) mcVerFile.readText().trim() else "1.20.4"

                                val javaCmd = AutoJava.ensureJavaAndGetPath(mcVersion) { logMsg ->
                                    withContext(Dispatchers.Main) { onConsoleTextChange(consoleText + "\n" + logMsg) }
                                }

                                val playitEnabledFile = File(serverDir, "playit_enabled.txt")
                                val playitEnabled = if (playitEnabledFile.exists()) playitEnabledFile.readText() == "true" else false

                                if (playitEnabled) {
                                    withContext(Dispatchers.Main) { onConsoleTextChange(consoleText + "\n[Sistem] Playit Tüneli (Uzak Bağlantı) hazırlanıyor...") }
                                    val playitExe = File(serverDir, "playit.exe")
                                    if (!playitExe.exists()) {
                                        withContext(Dispatchers.Main) { onConsoleTextChange(consoleText + "\n[Sistem] Windows için Playit indiriliyor (İlk Kurulum)...") }
                                        val dl = URL("https://github.com/playit-cloud/playit-agent/releases/latest/download/playit-windows-x86_64.exe")
                                        dl.openStream().use { inp -> playitExe.outputStream().use { out -> inp.copyTo(out) } }
                                        withContext(Dispatchers.Main) { onConsoleTextChange(consoleText + "\n[Sistem] Playit başarıyla indirildi.") }
                                    }

                                    val pPb = ProcessBuilder(playitExe.absolutePath)
                                    pPb.directory(serverDir)
                                    val logFile = File(serverDir, "playit_log.txt")
                                    if(logFile.exists()) logFile.delete()

                                    pPb.redirectOutput(logFile)
                                    pPb.redirectErrorStream(true)
                                    val pProcess = pPb.start()

                                    ProcessManager.playitProcess = pProcess
                                    withContext(Dispatchers.Main) { onPlayitProcessChange(pProcess) }
                                }

                                if (propsFile.exists()) {
                                    var propsContent = propsFile.readText()
                                    if (!propsContent.contains("enable-rcon=true")) {
                                        propsContent += "\nenable-rcon=true\nrcon.port=25575\nrcon.password=aserver123\n"
                                        propsFile.writeText(propsContent)
                                    }
                                }

                                val ramFile = File(serverDir, "ram.txt")
                                val ramGb = if (ramFile.exists()) ramFile.readText().trim() else "2"
                                withContext(Dispatchers.Main) { onRamChange(ramGb) }

                                val fabricLaunch = File(serverDir, "fabric-server-launch.jar")
                                val jarToRun = if (fabricLaunch.exists()) "fabric-server-launch.jar" else "server.jar"

                                if (!File(serverDir, jarToRun).exists()) {
                                    withContext(Dispatchers.Main) {
                                        onConsoleTextChange(consoleText + "\n[HATA] $jarToRun bulunamadı!")
                                        onProcessingChange(false)
                                    }
                                    return@launch
                                }

                                withContext(Dispatchers.Main) {
                                    onConsoleTextChange(consoleText + "\n[Sistem] Sunucu motoru ateşleniyor ($ramGb GB RAM)...")
                                }

                                val pb = ProcessBuilder(javaCmd, "-Xmx${ramGb}G", "-Xms${ramGb}G", "-jar", jarToRun, "nogui")
                                pb.directory(serverDir)
                                pb.redirectErrorStream(true)
                                val process = pb.start()

                                ProcessManager.serverProcess = process

                                withContext(Dispatchers.Main) {
                                    onActiveProcessChange(process)
                                    onServerRunningChange(true)
                                    onProcessingChange(false)
                                }

                                val reader = BufferedReader(InputStreamReader(process.inputStream))
                                var line: String?
                                while (reader.readLine().also { line = it } != null) {
                                    withContext(Dispatchers.Main) {
                                        val newText = consoleText + "\n$line"
                                        val lines = newText.split("\n")
                                        onConsoleTextChange(if (lines.size > 500) lines.takeLast(500).joinToString("\n") else newText)
                                    }
                                }

                                process.waitFor()

                                ProcessManager.serverProcess = null
                                ProcessManager.killProcessTree(activePlayitProcess)
                                ProcessManager.playitProcess = null

                                withContext(Dispatchers.Main) {
                                    isShuttingDown = false
                                    onServerRunningChange(false)
                                    onActiveProcessChange(null)
                                    onPlayitProcessChange(null)
                                    onConsoleTextChange(consoleText + "\n[Sistem] Sunucu durduruldu.")
                                }
                            } catch (e: Exception) {
                                ProcessManager.serverProcess = null
                                ProcessManager.killProcessTree(activePlayitProcess)
                                ProcessManager.playitProcess = null

                                withContext(Dispatchers.Main) {
                                    isShuttingDown = false
                                    onConsoleTextChange(consoleText + "\n[HATA] ${e.message}")
                                    onProcessingChange(false)
                                    onServerRunningChange(false)
                                    onPlayitProcessChange(null)
                                }
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = when {
                        isShuttingDown -> AccentOrange
                        isServerRunning -> AccentRed
                        isProcessing -> Color.Gray
                        else -> AccentEmerald
                    }
                ),
                modifier = Modifier.height(55.dp).padding(horizontal = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = when {
                        isShuttingDown -> "KAPANIYOR..."
                        isServerRunning -> "⏹ STOP SERVER"
                        isProcessing -> "BEKLEYİN..."
                        else -> "▶ START SERVER"
                    },
                    color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (isServerRunning) {
            val srvRam = String.format(java.util.Locale.US, "%.2f", currentServerRamMb / 1024.0)
            val sysUsedRam = String.format(java.util.Locale.US, "%.1f", systemUsedRamGb)
            val sysTotRam = String.format(java.util.Locale.US, "%.1f", systemTotalRamGb)
            val cpuStr = String.format(java.util.Locale.US, "%.1f", currentCpuUsage)

            Card(backgroundColor = SurfaceDark, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("💾 Sunucu RAM:", color = AccentBlue, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(120.dp))
                        Text("$srvRam GB / $allocatedRam GB (Ayrılan Sınır)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🖥️ Sistem RAM:", color = AccentEmerald, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(120.dp))
                        Text("$sysUsedRam GB / $sysTotRam GB (Tüm PC)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚙️ Sistem CPU:", color = AccentOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp, modifier = Modifier.width(120.dp))
                        Text("%$cpuStr (Genel Yük)", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        if (!isPlayitEnabled) {
            Card(
                backgroundColor = SurfaceDark, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, BorderColor),
                modifier = Modifier.fillMaxWidth().clickable {
                    val selection = StringSelection("127.0.0.1:$serverPort")
                    Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                    copyStatus = " (Kopyalandı!)"
                }
            ) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Dns, contentDescription = null, tint = AccentEmerald)
                    Spacer(Modifier.width(8.dp))
                    Text("YEREL BAĞLANTI (LOCALHOST): 127.0.0.1:$serverPort $copyStatus", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        } else {
            if (playitLink != null && playitIp == null) {
                Button(
                    onClick = { try { Desktop.getDesktop().browse(URI(playitLink!!)) } catch(e: Exception){} },
                    colors = ButtonDefaults.buttonColors(backgroundColor = AccentOrange), modifier = Modifier.fillMaxWidth().height(55.dp), shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("PLAYIT HESABINI BAĞLA (Tarayıcıda Açmak İçin Tıkla)", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Spacer(modifier = Modifier.height(16.dp))
            } else if (playitIp != null) {
                Card(
                    backgroundColor = AccentBlue, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().clickable {
                        val selection = StringSelection(playitIp)
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
                        copyStatus = " (Kopyalandı!)"
                    }
                ) {
                    Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Share, contentDescription = null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("UZAK BAĞLANTI IP: $playitIp $copyStatus", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        if (isServerReady && !isShuttingDown) {
            LazyRow(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(macroCommands) { macro ->
                    Button(
                        onClick = {
                            sendRconCommand(macro.second) { res ->
                                coroutineScope.launch(Dispatchers.Main) {
                                    onConsoleTextChange(consoleText + "\n[MAKRO] ${macro.first} -> " + if (res.isNotBlank()) res else "Başarılı")
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = SurfaceDark),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(40.dp).border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                    ) { Text(macro.first, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.Black).border(1.dp, BorderColor, RoundedCornerShape(16.dp)).padding(20.dp)) {
            Column(modifier = Modifier.verticalScroll(scrollState)) {
                Text(text = consoleText, color = Color(0xFFA3BE8C), fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }

        if (isServerRunning && !isShuttingDown) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = commandInput, onValueChange = { commandInput = it },
                    placeholder = { Text(if(isServerReady) "Komut yazın (Örn: say Merhaba)..." else "Sunucu başlatılırken komut gönder...", color = Color.Gray) },
                    modifier = Modifier.weight(1f), singleLine = true,
                    colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = AccentEmerald, backgroundColor = SurfaceDark),
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = {
                        if (commandInput.isNotBlank()) {
                            val cmd = commandInput
                            commandInput = ""
                            if (isServerReady) {
                                sendRconCommand(cmd) { res ->
                                    coroutineScope.launch(Dispatchers.Main) {
                                        onConsoleTextChange(consoleText + "\n[RCON] /$cmd -> " + if (res.isNotBlank()) res else "Gönderildi")
                                    }
                                }
                            } else {
                                // TIKANIKLIK ÇÖZÜLDÜ: Stdin komutunu saf bayt olarak ilet
                                try {
                                    val out = activeProcess?.outputStream
                                    out?.write("$cmd\n".toByteArray(Charsets.UTF_8))
                                    out?.flush()
                                    coroutineScope.launch(Dispatchers.Main) {
                                        onConsoleTextChange(consoleText + "\n[STDIN] > /$cmd (Motora direkt iletildi)")
                                    }
                                } catch(e: Exception) {}
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = AccentEmerald),
                    modifier = Modifier.height(55.dp),
                    shape = RoundedCornerShape(12.dp)
                ) { Icon(Icons.Default.Send, contentDescription = "Gönder", tint = Color.Black) }
            }
        }
    }
}

// ============================================================================
// EKRAN: DOSYA YÖNETİCİSİ VE AKILLI MAĞAZALAR
// ============================================================================
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun FileManagerScreen() {
    val coroutineScope = rememberCoroutineScope()
    val desktopPath = System.getProperty("user.home") + "/Desktop/AServer"
    val baseDir = File(desktopPath, "servers")
    if (!baseDir.exists()) baseDir.mkdirs()

    var currentDir by remember { mutableStateOf(baseDir) }
    var files by remember { mutableStateOf(currentDir.listFiles()?.toList()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList()) }

    var fileToEdit by remember { mutableStateOf<File?>(null) }
    var fileContent by remember { mutableStateOf("") }

    var showNewFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileName by remember { mutableStateOf("") }

    var statusMessage by remember { mutableStateOf("") }

    var showPluginStore by remember { mutableStateOf(false) }
    var showModStore by remember { mutableStateOf(false) }
    var downloadingItem by remember { mutableStateOf<String?>(null) }

    fun refreshFiles() { files = currentDir.listFiles()?.toList()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList() }

    if (showNewFolderDialog) {
        AlertDialog(
            onDismissRequest = { showNewFolderDialog = false },
            backgroundColor = CardDark,
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, BorderColor, RoundedCornerShape(16.dp)),
            title = { Text("Yeni Klasör", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFolderName, onValueChange = { newFolderName = it },
                    label = { Text("Klasör Adı", color = Color.Gray) },
                    colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = AccentEmerald),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newFolderName.isNotBlank()) {
                        File(currentDir, newFolderName).mkdirs()
                        refreshFiles()
                        showNewFolderDialog = false
                        newFolderName = ""
                    }
                }, colors = ButtonDefaults.buttonColors(backgroundColor = AccentEmerald), shape = RoundedCornerShape(8.dp)) { Text("OLUŞTUR", color = Color.Black, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showNewFolderDialog = false }) { Text("İptal", color = Color.Gray) } }
        )
    }

    if (showNewFileDialog) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            backgroundColor = CardDark,
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, BorderColor, RoundedCornerShape(16.dp)),
            title = { Text("Yeni Dosya Oluştur", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = newFileName, onValueChange = { newFileName = it },
                    label = { Text("Dosya Adı (Örn: ops.json, test.txt)", color = Color.Gray) },
                    colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = AccentBlue),
                    shape = RoundedCornerShape(12.dp)
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (newFileName.isNotBlank()) {
                        try {
                            File(currentDir, newFileName).createNewFile()
                            statusMessage = "Dosya oluşturuldu!"
                            refreshFiles()
                        } catch (e: Exception) {
                            statusMessage = "Dosya oluşturulamadı: ${e.message}"
                        }
                        showNewFileDialog = false
                        newFileName = ""
                    }
                }, colors = ButtonDefaults.buttonColors(backgroundColor = AccentBlue), shape = RoundedCornerShape(8.dp)) { Text("OLUŞTUR", color = Color.White, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { showNewFileDialog = false }) { Text("İptal", color = Color.Gray) } }
        )
    }

    if (showPluginStore) {
        var searchQuery by remember { mutableStateOf("") }
        var searchResults by remember { mutableStateOf<List<SpigetPlugin>>(emptyList()) }
        var isSearching by remember { mutableStateOf(false) }

        fun searchSpiget() {
            if (searchQuery.isBlank()) return
            isSearching = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val encodedQuery = java.net.URLEncoder.encode(searchQuery, "UTF-8")
                    val url = URL("https://api.spiget.org/v2/search/resources/$encodedQuery?field=name&size=20")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("User-Agent", "AServerApp")

                    if (conn.responseCode == 200) {
                        val response = conn.inputStream.bufferedReader().readText()
                        val results = parseSpiget(response)
                        withContext(Dispatchers.Main) { searchResults = results; isSearching = false }
                    } else { withContext(Dispatchers.Main) { isSearching = false } }
                } catch (e: Exception) { withContext(Dispatchers.Main) { isSearching = false } }
            }
        }

        AlertDialog(
            onDismissRequest = { if (downloadingItem == null) showPluginStore = false },
            backgroundColor = CardDark,
            modifier = Modifier.width(600.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, BorderColor, RoundedCornerShape(16.dp)),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ShoppingCart, contentDescription = null, tint = AccentOrange)
                    Spacer(Modifier.width(8.dp))
                    Text("Spiget Eklenti Mağazası", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = searchQuery, onValueChange = { searchQuery = it },
                            placeholder = { Text("Eklenti ara (Örn: AuthMe)...", color = Color.Gray) },
                            modifier = Modifier.weight(1f), singleLine = true,
                            colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = AccentOrange),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { searchSpiget() }, colors = ButtonDefaults.buttonColors(backgroundColor = AccentOrange), modifier = Modifier.height(55.dp), shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Default.Search, contentDescription = "Ara", tint = Color.Black)
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    if (isSearching) {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AccentOrange) }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                            items(searchResults) { plugin ->
                                Card(backgroundColor = SurfaceDark, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(plugin.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text(plugin.tag, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                                        Button(
                                            onClick = {
                                                downloadingItem = plugin.name
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    try {
                                                        val downloadUrl = "https://api.spiget.org/v2/resources/${plugin.id}/download"
                                                        var conn = URL(downloadUrl).openConnection() as HttpURLConnection
                                                        conn.instanceFollowRedirects = false
                                                        conn.connect()

                                                        var status = conn.responseCode
                                                        var redirects = 0
                                                        var currentUrl = downloadUrl
                                                        while (status in 300..308 && redirects < 5) {
                                                            currentUrl = conn.getHeaderField("Location")
                                                            conn = URL(currentUrl).openConnection() as HttpURLConnection
                                                            conn.instanceFollowRedirects = false
                                                            conn.connect()
                                                            status = conn.responseCode
                                                            redirects++
                                                        }

                                                        if (status == 200 || status == 201) {
                                                            val fileName = "${plugin.name.replace(Regex("[^a-zA-Z0-9.-]"), "_")}.jar"
                                                            val destFile = File(currentDir, fileName)
                                                            conn.inputStream.use { inp -> destFile.outputStream().use { out -> inp.copyTo(out) } }
                                                            withContext(Dispatchers.Main) {
                                                                refreshFiles()
                                                                downloadingItem = null
                                                                statusMessage = "${plugin.name} indirildi!"
                                                            }
                                                        } else throw Exception("Bu eklenti doğrudan indirilemiyor.")
                                                    } catch (e: Exception) {
                                                        withContext(Dispatchers.Main) { downloadingItem = null; statusMessage = "HATA: ${e.message}" }
                                                    }
                                                }
                                            },
                                            enabled = downloadingItem == null,
                                            colors = ButtonDefaults.buttonColors(backgroundColor = AccentOrange),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            if (downloadingItem == plugin.name) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                                            } else {
                                                Text("İNDİR", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPluginStore = false }, enabled = downloadingItem == null) { Text("Kapat", color = Color.Gray) } }
        )
    }

    if (showModStore) {
        var searchModQuery by remember { mutableStateOf("") }
        var searchModResults by remember { mutableStateOf<List<ModrinthMod>>(emptyList()) }
        var isModSearching by remember { mutableStateOf(false) }

        fun searchModrinth() {
            if (searchModQuery.isBlank()) return
            isModSearching = true
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    var mcVersion = "1.21.11"
                    try {
                        val verFile = File(currentDir.parentFile, "mc_version.txt")
                        if (verFile.exists()) mcVersion = verFile.readText().trim()
                    } catch (e: Exception) {}

                    val encodedQuery = java.net.URLEncoder.encode(searchModQuery, "UTF-8")
                    val facetString = "%5B%5B%22categories%3Afabric%22%5D%2C%5B%22project_type%3Amod%22%5D%2C%5B%22versions%3A$mcVersion%22%5D%5D"
                    val url = URL("https://api.modrinth.com/v2/search?query=$encodedQuery&facets=$facetString")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "GET"
                    conn.setRequestProperty("User-Agent", "AServerApp")

                    if (conn.responseCode == 200) {
                        val response = conn.inputStream.bufferedReader().readText()
                        val results = parseModrinth(response)
                        withContext(Dispatchers.Main) { searchModResults = results; isModSearching = false }
                    } else { withContext(Dispatchers.Main) { isModSearching = false } }
                } catch (e: Exception) { withContext(Dispatchers.Main) { isModSearching = false } }
            }
        }

        AlertDialog(
            onDismissRequest = { if (downloadingItem == null) showModStore = false },
            backgroundColor = CardDark,
            modifier = Modifier.width(600.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, BorderColor, RoundedCornerShape(16.dp)),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Build, contentDescription = null, tint = AccentEmerald)
                    Spacer(Modifier.width(8.dp))
                    Text("Modrinth Mod Mağazası", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = searchModQuery, onValueChange = { searchModQuery = it },
                            placeholder = { Text("Mod ara (Örn: lithium)...", color = Color.Gray) },
                            modifier = Modifier.weight(1f), singleLine = true,
                            colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = AccentEmerald),
                            shape = RoundedCornerShape(12.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = { searchModrinth() }, colors = ButtonDefaults.buttonColors(backgroundColor = AccentEmerald), modifier = Modifier.height(55.dp), shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Default.Search, contentDescription = "Ara", tint = Color.Black)
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    if (isModSearching) {
                        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AccentEmerald) }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp)) {
                            items(searchModResults) { mod ->
                                Card(backgroundColor = SurfaceDark, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Text(mod.title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text(mod.description, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                                        Button(
                                            onClick = {
                                                downloadingItem = mod.title
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    try {
                                                        var mcVersion = "1.21.11"
                                                        try {
                                                            val verFile = File(currentDir.parentFile, "mc_version.txt")
                                                            if (verFile.exists()) mcVersion = verFile.readText().trim()
                                                        } catch (e: Exception) {}

                                                        val versionUrlStr = "https://api.modrinth.com/v2/project/${mod.id}/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%22$mcVersion%22%5D"
                                                        val vConn = URL(versionUrlStr).openConnection() as HttpURLConnection
                                                        vConn.requestMethod = "GET"

                                                        if (vConn.responseCode == 200) {
                                                            val vResponse = vConn.inputStream.bufferedReader().readText()
                                                            val fileInfo = parseModrinthVersion(vResponse)
                                                            if (fileInfo != null) {
                                                                val (downloadUrl, fileName) = fileInfo
                                                                val dConn = URL(downloadUrl).openConnection() as HttpURLConnection
                                                                val destFile = File(currentDir, fileName)
                                                                dConn.inputStream.use { inp -> destFile.outputStream().use { out -> inp.copyTo(out) } }
                                                                withContext(Dispatchers.Main) {
                                                                    refreshFiles()
                                                                    downloadingItem = null
                                                                    statusMessage = "$fileName indirildi!"
                                                                }
                                                            } else throw Exception("Bu sürüme uygun dosya bulunamadı!")
                                                        } else throw Exception("Modrinth API Hatası")
                                                    } catch (e: Exception) {
                                                        withContext(Dispatchers.Main) { downloadingItem = null; statusMessage = "HATA: ${e.message}" }
                                                    }
                                                }
                                            },
                                            enabled = downloadingItem == null,
                                            colors = ButtonDefaults.buttonColors(backgroundColor = AccentEmerald),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.align(Alignment.End)
                                        ) {
                                            if (downloadingItem == mod.title) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.Black, strokeWidth = 2.dp)
                                            } else {
                                                Text("MODU İNDİR", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showModStore = false }, enabled = downloadingItem == null) { Text("Kapat", color = Color.Gray) } }
        )
    }

    if (fileToEdit != null) {
        Column(modifier = Modifier.fillMaxSize().padding(40.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { fileToEdit = null }, modifier = Modifier.background(SurfaceDark, RoundedCornerShape(12.dp)).border(1.dp, BorderColor, RoundedCornerShape(12.dp))) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = Color.White)
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(fileToEdit!!.name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (statusMessage.isNotEmpty()) { Text(statusMessage, color = AccentEmerald, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 16.dp)) }
                    Button(onClick = { fileToEdit!!.writeText(fileContent); statusMessage = "Kaydedildi!" }, colors = ButtonDefaults.buttonColors(backgroundColor = AccentEmerald), shape = RoundedCornerShape(12.dp), modifier = Modifier.height(45.dp)) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = Color.Black)
                        Spacer(Modifier.width(8.dp))
                        Text("KAYDET", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = fileContent, onValueChange = { fileContent = it; statusMessage = "" }, modifier = Modifier.fillMaxSize(),
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.LightGray, fontFamily = FontFamily.Monospace, fontSize = 14.sp, lineHeight = 22.sp),
                colors = TextFieldDefaults.outlinedTextFieldColors(backgroundColor = Color.Black, focusedBorderColor = AccentEmerald, unfocusedBorderColor = BorderColor),
                shape = RoundedCornerShape(16.dp)
            )
        }
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(40.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Dosya Yöneticisi", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    if (statusMessage.isNotEmpty()) {
                        Spacer(Modifier.width(16.dp))
                        Text(statusMessage, color = AccentEmerald, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
                Row {
                    if (currentDir.name.equals("plugins", ignoreCase = true)) {
                        IconButton(onClick = { showPluginStore = true }, modifier = Modifier.background(SurfaceDark, RoundedCornerShape(12.dp)).border(1.dp, BorderColor, RoundedCornerShape(12.dp))) {
                            Icon(Icons.Default.ShoppingCart, contentDescription = "Eklenti Mağazası", tint = AccentOrange)
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    else if (currentDir.name.equals("mods", ignoreCase = true)) {
                        IconButton(onClick = { showModStore = true }, modifier = Modifier.background(SurfaceDark, RoundedCornerShape(12.dp)).border(1.dp, BorderColor, RoundedCornerShape(12.dp))) {
                            Icon(Icons.Default.Build, contentDescription = "Mod Mağazası", tint = AccentEmerald)
                        }
                        Spacer(Modifier.width(12.dp))
                    }

                    IconButton(onClick = { showNewFileDialog = true }, modifier = Modifier.background(SurfaceDark, RoundedCornerShape(12.dp)).border(1.dp, BorderColor, RoundedCornerShape(12.dp))) {
                        Icon(Icons.Default.NoteAdd, contentDescription = "Yeni Dosya", tint = AccentBlue)
                    }
                    Spacer(Modifier.width(12.dp))

                    IconButton(onClick = { showNewFolderDialog = true }, modifier = Modifier.background(SurfaceDark, RoundedCornerShape(12.dp)).border(1.dp, BorderColor, RoundedCornerShape(12.dp))) {
                        Icon(Icons.Default.CreateNewFolder, contentDescription = "Yeni Klasör", tint = AccentEmerald)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            Card(backgroundColor = CardDark, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (currentDir.absolutePath != baseDir.absolutePath) {
                        IconButton(onClick = { currentDir = currentDir.parentFile ?: baseDir; refreshFiles(); statusMessage = "" }) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Bir Üst Klasör", tint = Color.White)
                        }
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(currentDir.absolutePath.substringAfter("Desktop").replace("\\", "/"), color = AccentEmerald, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(16.dp))

            if (files.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Bu klasör boş.", color = Color.Gray, fontSize = 16.sp) }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(files) { file ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                if (file.isDirectory) {
                                    currentDir = file
                                    refreshFiles()
                                    statusMessage = ""
                                } else if (file.name.endsWith(".properties") || file.name.endsWith(".json") || file.name.endsWith(".txt") || file.name.endsWith(".yml") || file.name.endsWith(".log")) {
                                    fileContent = file.readText()
                                    fileToEdit = file
                                    statusMessage = ""
                                }
                            }.background(SurfaceDark).border(1.dp, BorderColor, RoundedCornerShape(12.dp)).padding(16.dp)
                        ) {
                            Icon(imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile, contentDescription = null, tint = if (file.isDirectory) AccentOrange else Color.LightGray, modifier = Modifier.size(32.dp))
                            Spacer(Modifier.width(16.dp))
                            Column {
                                Text(file.name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                                if (!file.isDirectory) { Text("${file.length() / 1024} KB", color = Color.Gray, fontSize = 12.sp) }
                            }
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = { if (file.isDirectory) file.deleteRecursively() else file.delete(); refreshFiles() }) {
                                Icon(Icons.Default.Delete, contentDescription = "Sil", tint = AccentRed)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// EKRAN: SUNUCULARIM
// ============================================================================
@Composable
fun MyServersScreen(activeServerName: String, isServerRunning: Boolean, onPlayServer: (String) -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val desktopPath = System.getProperty("user.home") + "/Desktop/AServer"
    val baseDir = File(desktopPath, "servers")
    val backupsDir = File(desktopPath, "backups")
    if (!baseDir.exists()) baseDir.mkdirs()
    if (!backupsDir.exists()) backupsDir.mkdirs()

    var serverFolders by remember { mutableStateOf(baseDir.listFiles()?.filter { it.isDirectory } ?: emptyList()) }
    var backingUpFolder by remember { mutableStateOf<String?>(null) }
    var editingServerProps by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf("") }

    var restoringServer by remember { mutableStateOf<String?>(null) }
    var restoringFolderProgress by remember { mutableStateOf<String?>(null) }

    var worldToReset by remember { mutableStateOf<String?>(null) }

    fun refreshList() { serverFolders = baseDir.listFiles()?.filter { it.isDirectory } ?: emptyList() }

    if (editingServerProps != null) { ServerPropertiesEditorDialog(serverName = editingServerProps!!, onDismiss = { editingServerProps = null }) }

    if (worldToReset != null) {
        AlertDialog(
            onDismissRequest = { worldToReset = null },
            backgroundColor = CardDark,
            modifier = Modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, BorderColor, RoundedCornerShape(16.dp)),
            title = { Text("Dünyayı (Haritayı) Sıfırla", color = AccentRed, fontWeight = FontWeight.Bold) },
            text = { Text("'$worldToReset' sunucusunun içindeki harita dosyaları kalıcı olarak silinecek. Yeni bir dünya otomatik üretilecek. Emin misiniz?", color = Color.White) },
            confirmButton = {
                Button(onClick = {
                    coroutineScope.launch(Dispatchers.IO) {
                        val folder = File(baseDir, worldToReset!!)
                        File(folder, "world").deleteRecursively()
                        File(folder, "world_nether").deleteRecursively()
                        File(folder, "world_the_end").deleteRecursively()
                        withContext(Dispatchers.Main) {
                            statusMessage = "'$worldToReset' dünyası başarıyla sıfırlandı!"
                            worldToReset = null
                        }
                    }
                }, colors = ButtonDefaults.buttonColors(backgroundColor = AccentRed)) {
                    Text("SIFIRLA", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { worldToReset = null }) { Text("İptal", color = Color.Gray) }
            }
        )
    }

    if (restoringServer != null) {
        RestoreBackupDialog(
            serverName = restoringServer!!,
            onDismiss = { restoringServer = null },
            onRestore = { zipFile ->
                val targetServer = restoringServer!!
                restoringServer = null
                restoringFolderProgress = targetServer
                statusMessage = "$targetServer geri yükleniyor..."
                coroutineScope.launch(Dispatchers.IO) {
                    try {
                        val serverDir = File(baseDir, targetServer)
                        serverDir.deleteRecursively()
                        serverDir.mkdirs()
                        unzipFolder(zipFile, serverDir)

                        withContext(Dispatchers.Main) {
                            statusMessage = "Yedek başarıyla geri yüklendi!"
                            restoringFolderProgress = null
                            refreshList()
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            statusMessage = "Geri Yükleme Hatası: ${e.message}"
                            restoringFolderProgress = null
                        }
                    }
                }
            }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(40.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Sunucularım", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            if (statusMessage.isNotEmpty()) Text(statusMessage, color = if(statusMessage.contains("HATA")) AccentRed else AccentEmerald, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(30.dp))
        if (serverFolders.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Henüz bir sunucu kurmadınız. 'Yeni Sunucu' sekmesinden başlayın.", color = Color.Gray, fontSize = 16.sp) }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                items(serverFolders) { folder ->
                    Card(backgroundColor = CardDark, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
                        Row(modifier = Modifier.padding(24.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Storage, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.width(20.dp))
                                Column {
                                    Text(folder.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(4.dp))
                                    Text("Konum: Masaüstü/AServer/servers/${folder.name}", color = Color.Gray, fontSize = 13.sp)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { editingServerProps = folder.name }, modifier = Modifier.background(SurfaceDark, RoundedCornerShape(12.dp)).border(1.dp, BorderColor, RoundedCornerShape(12.dp))) { Icon(Icons.Default.Settings, contentDescription = "Ayarlar", tint = Color.LightGray) }
                                Spacer(Modifier.width(12.dp))

                                val isThisServerActive = (activeServerName == folder.name) && isServerRunning
                                IconButton(
                                    onClick = {
                                        if (isThisServerActive) {
                                            statusMessage = "HATA: Sunucu çalışırken dünyayı sıfırlayamazsınız!"
                                        } else {
                                            worldToReset = folder.name
                                        }
                                    },
                                    modifier = Modifier.background(SurfaceDark, RoundedCornerShape(12.dp)).border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                                ) {
                                    Icon(Icons.Default.Public, contentDescription = "Dünyayı Sıfırla", tint = if (isThisServerActive) Color.Gray else AccentRed)
                                }
                                Spacer(Modifier.width(12.dp))

                                if (restoringFolderProgress == folder.name) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AccentOrange, strokeWidth = 2.dp)
                                } else {
                                    IconButton(
                                        onClick = {
                                            if (isThisServerActive) {
                                                statusMessage = "HATA: Lütfen önce sunucuyu durdurun!"
                                            } else {
                                                restoringServer = folder.name
                                            }
                                        },
                                        modifier = Modifier.background(SurfaceDark, RoundedCornerShape(12.dp)).border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                                    ) {
                                        Icon(Icons.Default.Restore, contentDescription = "Geri Yükle", tint = AccentOrange)
                                    }
                                }
                                Spacer(Modifier.width(12.dp))

                                if (backingUpFolder == folder.name) { CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AccentBlue, strokeWidth = 2.dp) } else {
                                    IconButton(onClick = {
                                        backingUpFolder = folder.name
                                        statusMessage = "${folder.name} yedekleniyor..."
                                        coroutineScope.launch(Dispatchers.IO) {
                                            try {
                                                val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
                                                val zipFile = File(backupsDir, "${folder.name}_Backup_$dateStr.zip")
                                                zipFolder(folder, zipFile)
                                                statusMessage = "Yedek başarıyla alındı."
                                            } catch (e: Exception) { statusMessage = "Hata: ${e.message}" } finally { backingUpFolder = null }
                                        }
                                    }, modifier = Modifier.background(SurfaceDark, RoundedCornerShape(12.dp)).border(1.dp, BorderColor, RoundedCornerShape(12.dp))) { Icon(Icons.Default.Backup, contentDescription = "Yedekle", tint = AccentBlue) }
                                }
                                Spacer(Modifier.width(12.dp))

                                IconButton(
                                    onClick = {
                                        if (isThisServerActive) {
                                            statusMessage = "HATA: Çalışan bir sunucuyu silemezsiniz!"
                                        } else {
                                            folder.deleteRecursively()
                                            refreshList()
                                        }
                                    },
                                    modifier = Modifier.background(SurfaceDark, RoundedCornerShape(12.dp)).border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Sil", tint = if (isThisServerActive) Color.Gray else AccentRed)
                                }
                                Spacer(Modifier.width(16.dp))

                                val isAnotherServerActive = (activeServerName.isNotEmpty() && activeServerName != folder.name) && isServerRunning
                                Button(
                                    onClick = {
                                        onPlayServer(folder.name)
                                    },
                                    enabled = !isAnotherServerActive,
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = if (isThisServerActive) AccentOrange else AccentEmerald,
                                        disabledBackgroundColor = Color(0xFF374151)
                                    ),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.height(48.dp)
                                ) {
                                    if (isThisServerActive) {
                                        Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.Black)
                                        Spacer(Modifier.width(6.dp))
                                        Text("KONSOLA GİT", color = Color.Black, fontWeight = FontWeight.Bold)
                                    } else {
                                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = if (isAnotherServerActive) Color.Gray else Color.Black)
                                        Spacer(Modifier.width(6.dp))
                                        Text("BAŞLAT", color = if (isAnotherServerActive) Color.Gray else Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================================
// EKRAN: YEDEK SEÇME (RESTORE) DİYALOĞU
// ============================================================================
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun RestoreBackupDialog(serverName: String, onDismiss: () -> Unit, onRestore: (File) -> Unit) {
    val backupsDir = File(System.getProperty("user.home") + "/Desktop/AServer/backups")
    val allBackups = backupsDir.listFiles { file ->
        file.extension == "zip" && file.name.startsWith(serverName + "_")
    }?.sortedByDescending { it.lastModified() } ?: emptyList()

    AlertDialog(
        onDismissRequest = onDismiss,
        backgroundColor = CardDark,
        modifier = Modifier.width(500.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, BorderColor, RoundedCornerShape(16.dp)),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Restore, contentDescription = null, tint = AccentOrange)
                Spacer(Modifier.width(8.dp))
                Text("$serverName - Yedekleri", color = Color.White, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            if (allBackups.isEmpty()) {
                Text("Bu sunucuya ait herhangi bir yedek bulunamadı.", color = Color.Gray, fontSize = 14.sp)
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                    items(allBackups) { backupFile ->
                        Card(
                            backgroundColor = SurfaceDark,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onRestore(backupFile) }.border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                        ) {
                            Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Archive, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(28.dp))
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(backupFile.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                    val date = SimpleDateFormat("dd/MM/yyyy HH:mm").format(Date(backupFile.lastModified()))
                                    Text("${backupFile.length() / (1024 * 1024)} MB • $date", color = Color.Gray, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat", color = Color.Gray) } }
    )
}

// ============================================================================
// EKRAN: SUNUCU ÖZELLİKLERİ EDİTÖR DİYALOĞU
// ============================================================================
@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ServerPropertiesEditorDialog(serverName: String, onDismiss: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val desktopPath = System.getProperty("user.home") + "/Desktop/AServer"
    val propsFile = File("$desktopPath/servers/$serverName/server.properties")

    var propsMap by remember { mutableStateOf(mutableMapOf<String, String>()) }
    var isLoaded by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }

    LaunchedEffect(serverName) {
        if (propsFile.exists()) {
            val map = mutableMapOf<String, String>()
            propsFile.readLines().forEach { line ->
                if (!line.startsWith("#") && line.contains("=")) {
                    val parts = line.split("=", limit = 2)
                    map[parts[0].trim()] = parts[1].trim()
                }
            }
            propsMap = map
            isLoaded = true
        } else {
            statusMessage = "Hata: server.properties bulunamadı."
        }
    }

    if (isLoaded) {
        var motd by remember { mutableStateOf(propsMap["motd"] ?: "AServer") }
        var maxPlayers by remember { mutableStateOf(propsMap["max-players"] ?: "20") }
        var onlineMode by remember { mutableStateOf(propsMap["online-mode"] == "true") }
        var hardcore by remember { mutableStateOf(propsMap["hardcore"] == "true") }
        var pvp by remember { mutableStateOf(propsMap["pvp"] != "false") }
        var viewDistance by remember { mutableFloatStateOf((propsMap["view-distance"] ?: "8").toFloatOrNull() ?: 8f) }
        var difficulty by remember { mutableStateOf(propsMap["difficulty"] ?: "easy") }

        AlertDialog(
            onDismissRequest = onDismiss,
            backgroundColor = CardDark,
            modifier = Modifier.width(550.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, BorderColor, RoundedCornerShape(16.dp)),
            title = { Text("$serverName Ayarları", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
            text = {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    if (statusMessage.isNotEmpty()) Text(statusMessage, color = AccentEmerald, modifier = Modifier.padding(bottom = 12.dp))

                    OutlinedTextField(value = motd, onValueChange = { motd = it }, label = { Text("MOTD (Açıklama)", color = Color.Gray) }, colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = AccentEmerald), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))
                    OutlinedTextField(value = maxPlayers, onValueChange = { maxPlayers = it }, label = { Text("Max Players", color = Color.Gray) }, colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = AccentEmerald), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp))

                    CustomDropdownMenu(label = "Zorluk (Difficulty)", options = listOf("peaceful", "easy", "normal", "hard"), selectedOption = difficulty, onOptionSelected = { difficulty = it })
                    Spacer(Modifier.height(20.dp))

                    Text("Görüş Mesafesi: ${viewDistance.toInt()} chunk", color = Color.LightGray, fontSize = 14.sp)
                    Slider(value = viewDistance, onValueChange = { viewDistance = it }, valueRange = 2f..32f, colors = SliderDefaults.colors(thumbColor = AccentEmerald, activeTrackColor = AccentEmerald))

                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text("PvP Açık", color = Color.White); Switch(checked = pvp, onCheckedChange = { pvp = it }, colors = SwitchDefaults.colors(checkedThumbColor = AccentEmerald)) }
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text("Online Mode (Premium)", color = Color.White); Switch(checked = onlineMode, onCheckedChange = { onlineMode = it }, colors = SwitchDefaults.colors(checkedThumbColor = AccentEmerald)) }
                    Row(Modifier.fillMaxWidth().padding(top = 12.dp), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text("Hardcore Modu", color = Color.White); Switch(checked = hardcore, onCheckedChange = { hardcore = it }, colors = SwitchDefaults.colors(checkedThumbColor = AccentEmerald)) }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch(Dispatchers.IO) {
                            try {
                                propsMap["motd"] = motd
                                propsMap["max-players"] = maxPlayers
                                propsMap["difficulty"] = difficulty
                                propsMap["view-distance"] = viewDistance.toInt().toString()
                                propsMap["pvp"] = pvp.toString()
                                propsMap["online-mode"] = onlineMode.toString()
                                propsMap["hardcore"] = hardcore.toString()

                                val builder = StringBuilder()
                                builder.append("# Minecraft server properties (Edited by AServer PC)\n")
                                propsMap.forEach { (key, value) -> builder.append("$key=$value\n") }
                                propsFile.writeText(builder.toString())

                                statusMessage = "Başarıyla Kaydedildi!"
                            } catch (e: Exception) { statusMessage = "Hata: ${e.message}" }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = AccentEmerald), shape = RoundedCornerShape(8.dp)
                ) { Text("KAYDET", color = Color.Black, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Kapat", color = Color.Gray) } }
        )
    } else {
        AlertDialog(onDismissRequest = onDismiss, backgroundColor = CardDark, title = { Text("Yükleniyor...", color = Color.White) }, buttons = {})
    }
}

// ============================================================================
// EKRAN: YENİ SUNUCU OLUŞTUR
// ============================================================================
@Composable
fun CreateServerScreen(onServerCreated: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()

    val versionMap = mapOf(
        "1.21.11" to "1.21.11",
        "1.21.4" to "1.21.4",
        "1.21.1" to "1.21.1",
        "1.20.4" to "1.20.4",
        "1.19.4" to "1.19.4",
        "1.16.5" to "1.16.5",
        "1.12.2" to "1.12.2",
        "1.8.8" to "1.8.8"
    )
    val versionOptions = versionMap.keys.toList()

    var profileName by remember { mutableStateOf("") }
    var version by remember { mutableStateOf(versionOptions[0]) }

    val softwareOptions = listOf("PaperMC (Eklentiler)", "Fabric (Modlar)")
    var software by remember { mutableStateOf(softwareOptions[0]) }

    var motd by remember { mutableStateOf("AServer") }
    var opName by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("25565") }
    var ram by remember { mutableStateOf(4f) }

    val gamemodeOptions = listOf("survival", "creative", "adventure", "spectator")
    var gamemode by remember { mutableStateOf(gamemodeOptions[0]) }
    val difficultyOptions = listOf("peaceful", "easy", "normal", "hard")
    var difficulty by remember { mutableStateOf(difficultyOptions[1]) }

    var viewDistance by remember { mutableStateOf(8f) }
    var maxPlayers by remember { mutableStateOf(20f) }
    var hardcore by remember { mutableStateOf(false) }
    var onlineMode by remember { mutableStateOf(false) }
    var playitEnabled by remember { mutableStateOf(true) }

    var isCreating by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize().padding(40.dp).verticalScroll(scrollState)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Yeni Sunucu İnşa Et", color = Color.White, fontSize = 32.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 30.dp))
                if (statusMessage.isNotEmpty()) Text(statusMessage, color = if (statusMessage.contains("HATA")) AccentRed else AccentEmerald, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {

                Column(modifier = Modifier.weight(1f)) {
                    Card(backgroundColor = CardDark, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 20.dp)) {
                                Icon(Icons.Default.Settings, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("PROFİL VE YAZILIM", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            OutlinedTextField(value = profileName, onValueChange = { profileName = it.replace(" ", "_") }, label = { Text("Profil Adı (ZORUNLU)", color = Color.Gray) }, colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = AccentEmerald, backgroundColor = SurfaceDark), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
                            CustomDropdownMenu(label = "Yazılım Seç", options = softwareOptions, selectedOption = software, onOptionSelected = { software = it })
                            Spacer(Modifier.height(16.dp))
                            CustomDropdownMenu(label = "Sürüm Seç", options = versionOptions, selectedOption = version, onOptionSelected = { version = it })
                        }
                    }
                    Card(backgroundColor = CardDark, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 20.dp)) {
                                Icon(Icons.Default.List, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("GENEL AYARLAR", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            OutlinedTextField(value = motd, onValueChange = { motd = it }, label = { Text("Açıklama (MOTD)", color = Color.Gray) }, colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = AccentEmerald, backgroundColor = SurfaceDark), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp))
                            OutlinedTextField(value = opName, onValueChange = { opName = it }, label = { Text("Kurucu (OP) Oyuncu", color = Color.Gray) }, colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = AccentEmerald, backgroundColor = SurfaceDark), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Card(backgroundColor = CardDark, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 20.dp)) {
                                Icon(Icons.Default.Build, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("OYUN KURALLARI", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
                                Box(modifier = Modifier.weight(1f)) { CustomDropdownMenu(label = "Oyun Modu", options = gamemodeOptions, selectedOption = gamemode, onOptionSelected = { gamemode = it }) }
                                Box(modifier = Modifier.weight(1f)) { CustomDropdownMenu(label = "Zorluk", options = difficultyOptions, selectedOption = difficulty, onOptionSelected = { difficulty = it }) }
                            }
                            Text("Görüş Mesafesi: ${viewDistance.toInt()} chunks", color = Color.LightGray, fontSize = 14.sp)
                            Slider(value = viewDistance, onValueChange = { viewDistance = it }, valueRange = 2f..32f, colors = SliderDefaults.colors(thumbColor = AccentEmerald, activeTrackColor = AccentEmerald))

                            Text("Maks. Oyuncu: ${maxPlayers.toInt()} players", color = Color.LightGray, fontSize = 14.sp)
                            Slider(value = maxPlayers, onValueChange = { maxPlayers = it }, valueRange = 1f..100f, colors = SliderDefaults.colors(thumbColor = AccentEmerald, activeTrackColor = AccentEmerald))

                            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Hardcore Modu", color = Color.White, fontWeight = FontWeight.SemiBold); Switch(checked = hardcore, onCheckedChange = { hardcore = it }, colors = SwitchDefaults.colors(checkedThumbColor = AccentEmerald)) }
                        }
                    }

                    Card(backgroundColor = CardDark, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(24.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 20.dp)) {
                                Icon(Icons.Default.Share, contentDescription = null, tint = AccentEmerald, modifier = Modifier.size(28.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Text("AĞ VE DONANIM", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            }
                            OutlinedTextField(value = port, onValueChange = { port = it }, label = { Text("Port", color = Color.Gray) }, colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = AccentEmerald, backgroundColor = SurfaceDark), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth())
                            Spacer(Modifier.height(20.dp))
                            Text("Sunucu Belleği (RAM): ${ram.toInt()} GB", color = Color.LightGray, fontSize = 14.sp)
                            Slider(value = ram, onValueChange = { ram = it }, valueRange = 1f..16f, colors = SliderDefaults.colors(thumbColor = AccentEmerald, activeTrackColor = AccentEmerald))

                            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("Uzak Bağlantı (Playit.gg)", color = Color.White, fontWeight = FontWeight.SemiBold)
                                    Text("Modem portu açmadan arkadaşlarınla oyna", color = Color.Gray, fontSize = 11.sp)
                                }
                                Switch(checked = playitEnabled, onCheckedChange = { playitEnabled = it }, colors = SwitchDefaults.colors(checkedThumbColor = AccentEmerald))
                            }

                            Row(modifier = Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text("Online Mode (Orijinal Hesap Zorunlu)", color = Color.White, fontWeight = FontWeight.SemiBold); Switch(checked = onlineMode, onCheckedChange = { onlineMode = it }, colors = SwitchDefaults.colors(checkedThumbColor = AccentEmerald)) }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(120.dp))
        }

        Button(
            onClick = {
                if (profileName.isBlank()) {
                    statusMessage = "HATA: Sunucuya bir isim (Profil Adı) vermek zorundasınız!"
                    return@Button
                }

                if (!isCreating) {
                    isCreating = true
                    statusMessage = "İşlem başlatıldı..."
                    coroutineScope.launch(Dispatchers.IO) {
                        try {
                            val baseDir = File(System.getProperty("user.home") + "/Desktop/AServer/servers")
                            if (!baseDir.exists()) baseDir.mkdirs()
                            val serverDir = File(baseDir, profileName)
                            if (!serverDir.exists()) serverDir.mkdirs()

                            File(serverDir, "eula.txt").writeText("eula=true")

                            val actualVersion = versionMap[version] ?: "1.21.11"
                            File(serverDir, "ram.txt").writeText(ram.toInt().toString())
                            File(serverDir, "mc_version.txt").writeText(actualVersion)

                            File(serverDir, "playit_enabled.txt").writeText(playitEnabled.toString())

                            val props = """
                                motd=$motd
                                server-port=$port
                                view-distance=${viewDistance.toInt()}
                                max-players=${maxPlayers.toInt()}
                                online-mode=$onlineMode
                                hardcore=$hardcore
                                gamemode=$gamemode
                                difficulty=$difficulty
                                enable-rcon=true
                                rcon.port=25575
                                rcon.password=aserver123
                            """.trimIndent()
                            File(serverDir, "server.properties").writeText(props)

                            if (software.contains("Fabric")) {
                                statusMessage = "Fabric Yükleyici aranıyor..."
                                val metaUrl = URL("https://meta.fabricmc.net/v2/versions/installer")
                                val metaConn = metaUrl.openConnection() as HttpURLConnection
                                val metaResponse = metaConn.inputStream.bufferedReader().readText()
                                val latestInstaller = Regex("\"version\":\"([^\"]+)\"").find(metaResponse)?.groupValues?.get(1) ?: "1.0.1"

                                statusMessage = "Fabric İndiriliyor..."
                                val installerUrl = URL("https://maven.fabricmc.net/net/fabricmc/fabric-installer/$latestInstaller/fabric-installer-$latestInstaller.jar")
                                val installerFile = File(serverDir, "fabric-installer.jar")
                                installerUrl.openStream().use { input -> installerFile.outputStream().use { output -> input.copyTo(output) } }

                                statusMessage = "Java kontrol ediliyor..."
                                val javaCmd = AutoJava.ensureJavaAndGetPath(actualVersion) { msg -> statusMessage = msg }

                                statusMessage = "Fabric Kuruluyor (Lütfen bekleyin)..."
                                val pb = ProcessBuilder(javaCmd, "-jar", "fabric-installer.jar", "server", "-mcversion", actualVersion, "-downloadMinecraft")
                                pb.directory(serverDir)
                                val process = pb.start()
                                process.waitFor()

                                if (!File(serverDir, "fabric-server-launch.jar").exists()) {
                                    throw Exception("Fabric başlatıcı dosyası oluşturulamadı!")
                                }
                                if (!File(serverDir, "server.jar").exists()) {
                                    throw Exception("Vanilla server.jar indirilemedi! Girdiğin sürüm ($actualVersion) Mojang sunucularında bulunmuyor olabilir.")
                                }

                            } else {
                                statusMessage = "PaperMC $actualVersion aranıyor..."
                                val apiUrl = URL("https://api.papermc.io/v2/projects/paper/versions/$actualVersion")
                                val apiConn = apiUrl.openConnection() as HttpURLConnection
                                val response = apiConn.inputStream.bufferedReader().readText()
                                val buildsStr = response.substringAfter("\"builds\":[").substringBefore("]")
                                val builds = buildsStr.split(",").map { it.trim() }
                                val latestBuild = builds.last()

                                statusMessage = "PaperMC ($latestBuild) indiriliyor..."
                                val jarFile = File(serverDir, "server.jar")
                                val downloadUrl = URL("https://api.papermc.io/v2/projects/paper/versions/$actualVersion/builds/$latestBuild/downloads/paper-$actualVersion-$latestBuild.jar")
                                downloadUrl.openStream().use { input -> jarFile.outputStream().use { output -> input.copyTo(output) } }
                            }

                            statusMessage = "BAŞARILI!"
                            launch(Dispatchers.Main) { onServerCreated() }
                        } catch (e: Exception) {
                            statusMessage = "HATA: ${e.message}"
                        } finally {
                            isCreating = false
                        }
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(backgroundColor = if (isCreating) Color.Gray else AccentEmerald),
            modifier = Modifier.align(Alignment.BottomEnd).padding(40.dp).height(65.dp).width(320.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(if (isCreating) "BEKLEYİN..." else "OLUŞTUR VE KAYDET", color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        }
    }
}
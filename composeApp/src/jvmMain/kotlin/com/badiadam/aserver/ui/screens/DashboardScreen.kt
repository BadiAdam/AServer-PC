package com.badiadam.aserver.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Public
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.badiadam.aserver.AccentEmerald
import com.badiadam.aserver.AccentOrange
import com.badiadam.aserver.AccentRed
import com.badiadam.aserver.AutoJava
import com.badiadam.aserver.BorderColor
import com.badiadam.aserver.CardDark
import com.badiadam.aserver.ProcessManager
import com.badiadam.aserver.ScheduledTask
import com.badiadam.aserver.SurfaceDark
import com.badiadam.aserver.AccentBlue
import com.badiadam.aserver.getProcessRamUsage
import com.badiadam.aserver.getSystemCpuLoad
import com.badiadam.aserver.getSystemRamInfo
import com.badiadam.aserver.isPortAvailable
import com.badiadam.aserver.sendRconCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.URL

@Composable
fun DashboardScreen(
    activeServerName: String,
    consoleText: String, onConsoleAppend: (String) -> Unit,
    isServerRunning: Boolean, onServerRunningChange: (Boolean) -> Unit,
    isServerReady: Boolean, onServerReadyChange: (Boolean) -> Unit,
    isShuttingDown: Boolean, onShuttingDownChange: (Boolean) -> Unit,
    isProcessing: Boolean, onProcessingChange: (Boolean) -> Unit,
    activeProcess: Process?, onActiveProcessChange: (Process?) -> Unit,
    activePlayitProcess: Process?, onPlayitProcessChange: (Process?) -> Unit,
    allocatedRam: String, onRamChange: (String) -> Unit,
    scheduledTasks: MutableList<ScheduledTask>,
    lifecycleScope: CoroutineScope
) {
    val scrollState = rememberScrollState()

    var playitIp by remember { mutableStateOf<String?>(null) }
    var playitLink by remember { mutableStateOf<String?>(null) }
    var copyStatus by remember { mutableStateOf("") }
    var commandInput by remember { mutableStateOf("") }
    var isPlayitEnabled by remember { mutableStateOf(false) }
    var serverPort by remember { mutableStateOf("25565") }

    var currentServerRamMb by remember { mutableStateOf(0.0) }
    var systemUsedRamGb by remember { mutableStateOf(0.0) }
    var systemTotalRamGb by remember { mutableStateOf(0.0) }
    var currentCpuUsage by remember { mutableStateOf(0.0) }

    val macroCommands = listOf("Sabah" to "time set day", "Gece" to "time set night", "Yağmuru Kapat" to "weather clear", "Tümünü Kaydet" to "save-all")

    LaunchedEffect(consoleText) { scrollState.animateScrollTo(scrollState.maxValue) }

    LaunchedEffect(isServerRunning, activeServerName) {
        if (activeServerName.isNotEmpty()) {
            val serverDir = File(com.badiadam.aserver.AppConfig.SERVERS_DIR, activeServerName)
            val pFile = File(serverDir, "playit_enabled.txt")
            isPlayitEnabled = pFile.exists() && pFile.readText() == "true"

            val propsFile = File(serverDir, "server.properties")
            if (propsFile.exists()) {
                val portLine = propsFile.readLines().find { it.startsWith("server-port=") }
                serverPort = portLine?.substringAfter("=")?.trim() ?: "25565"
            }
        }
        if (!isServerRunning && isShuttingDown) onShuttingDownChange(false)

        if (isServerRunning) {
            val serverDir = File(com.badiadam.aserver.AppConfig.SERVERS_DIR, activeServerName)
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
                            if (found != "playit.gg" && found != "api.playit.gg" && !found.contains("playit.gg/claim")) { tempIp = found; break }
                        }
                        if (tempIp != null) playitIp = tempIp

                        if (playitIp == null) {
                            val match = Regex("https://playit\\.gg/claim/[a-zA-Z0-9]+").find(cleanText)
                            if (match != null && playitLink != match.value) { playitLink = match.value }
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
                val pid = try { activeProcess?.pid() ?: -1L } catch (e: Exception) { -1L }
                val telemetry = withContext(Dispatchers.IO) {
                    val serverRamMb = if (pid != -1L) getProcessRamUsage(pid) else 0.0
                    val cpuUsage = getSystemCpuLoad()
                    val ramInfo = getSystemRamInfo()
                    Triple(serverRamMb, cpuUsage, ramInfo)
                }
                currentServerRamMb = telemetry.first
                currentCpuUsage = telemetry.second
                systemUsedRamGb = telemetry.third.first
                systemTotalRamGb = telemetry.third.second
                delay(3000)
            }
        } else {
            currentServerRamMb = 0.0; currentCpuUsage = 0.0; systemUsedRamGb = 0.0; systemTotalRamGb = 0.0
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(32.dp)) {
        if (activeServerName.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Lütfen 'Sunucularım' sekmesinden sunucu seçin.", color = Color.Gray) }
            return@Column
        }

        Row(modifier = Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Card(backgroundColor = CardDark, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderColor), modifier = Modifier.weight(1f).fillMaxHeight()) {
                Row(modifier = Modifier.fillMaxSize().padding(20.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(activeServerName, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(50)).background(when {
                                isServerReady && !isShuttingDown -> AccentEmerald
                                isShuttingDown || isServerRunning || isProcessing -> AccentOrange
                                else -> AccentRed
                            }))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = when {
                                    isServerReady && !isShuttingDown -> "Sistem Çevrimiçi"
                                    isShuttingDown -> "Kapatılıyor..."
                                    isServerRunning -> "Başlatılıyor..."
                                    isProcessing -> "İşlem Yapılıyor..."
                                    else -> "Sistem Çevrimdışı"
                                },
                                color = Color.LightGray, fontSize = 14.sp
                            )
                        }
                    }

                    Button(
                        onClick = {
                            if (isServerRunning && !isShuttingDown) {
                                onShuttingDownChange(true)
                                lifecycleScope.launch {
                                    withContext(Dispatchers.Main) { onConsoleAppend("\n[Sistem] Kapatma komutu gönderiliyor...") }
                                    try {
                                        val out = ProcessManager.serverProcess?.outputStream ?: activeProcess?.outputStream
                                        out?.write("stop\n".toByteArray(Charsets.UTF_8))
                                        out?.flush()
                                    } catch (e: Exception) {}
                                    delay(15000)
                                    if (ProcessManager.serverProcess?.isAlive == true) {
                                        withContext(Dispatchers.Main) { onConsoleAppend("\n[Sistem] Sunucu zorla kapatılıyor (Timeout)!") }
                                        ProcessManager.killProcessTree(ProcessManager.serverProcess)
                                        ProcessManager.killProcessTree(ProcessManager.playitProcess)
                                    }
                                }
                            } else if (!isProcessing && !isServerRunning && !isShuttingDown) {
                                onProcessingChange(true)
                                lifecycleScope.launch {
                                    try {
                                        val serverDir = File(com.badiadam.aserver.AppConfig.SERVERS_DIR, activeServerName)
                                        val propsFile = File(serverDir, "server.properties")
                                        var portToCheck = 25565
                                        if (propsFile.exists()) {
                                            val portLine = propsFile.readLines().find { it.startsWith("server-port=") }
                                            portToCheck = portLine?.substringAfter("=")?.trim()?.toIntOrNull() ?: 25565
                                        }

                                        if (!isPortAvailable(portToCheck)) {
                                            withContext(Dispatchers.Main) {
                                                onConsoleAppend("\n[HATA] $portToCheck portu dolu!")
                                                onProcessingChange(false)
                                            }
                                            return@launch
                                        }

                                        val mcVerFile = File(serverDir, "mc_version.txt")
                                        val mcVersion = if (mcVerFile.exists()) mcVerFile.readText().trim() else "1.20.4"

                                        val javaCmd = AutoJava.ensureJavaAndGetPath(mcVersion) { logMsg -> withContext(Dispatchers.Main) { onConsoleAppend("\n" + logMsg) } }

                                        val playitEnabledFile = File(serverDir, "playit_enabled.txt")
                                        val playitEnabled = if (playitEnabledFile.exists()) playitEnabledFile.readText() == "true" else false

                                        if (playitEnabled) {
                                            val playitExe = File(serverDir, "playit.exe")
                                            if (!playitExe.exists()) {
                                                val dl = URL("https://github.com/playit-cloud/playit-agent/releases/latest/download/playit-windows-x86_64.exe")
                                                dl.openStream().use { inp -> playitExe.outputStream().use { out -> inp.copyTo(out) } }
                                            }
                                            val pPb = ProcessBuilder(playitExe.absolutePath).directory(serverDir)
                                            val logFile = File(serverDir, "playit_log.txt")
                                            if (logFile.exists()) logFile.delete()
                                            pPb.redirectOutput(logFile)
                                            pPb.redirectErrorStream(true)
                                            val pProcess = pPb.start()
                                            ProcessManager.playitProcess = pProcess
                                            withContext(Dispatchers.Main) { onPlayitProcessChange(pProcess) }
                                        }

                                        val ramFile = File(serverDir, "ram.txt")
                                        val ramGb = if (ramFile.exists()) ramFile.readText().trim() else "2"
                                        withContext(Dispatchers.Main) { onRamChange(ramGb) }

                                        val fabricLaunch = File(serverDir, "fabric-server-launch.jar")
                                        val jarToRun = if (fabricLaunch.exists()) "fabric-server-launch.jar" else "server.jar"

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

                                        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                                            try {
                                                process.inputStream.bufferedReader().use { reader ->
                                                    var line: String?
                                                    while (reader.readLine().also { line = it } != null) {
                                                        val currentLine = line!!
                                                        withContext(Dispatchers.Main) { 
                                                            onConsoleAppend("\n$currentLine")
                                                            // Yedek Sensör: Konsolda "Done (" geçerse UI'ı hazırla
                                                            if (currentLine.contains("Done (") && currentLine.contains("For help, type")) {
                                                                onServerReadyChange(true)
                                                            }
                                                        }
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    onConsoleAppend("\nLog okuma hatası: ${e.message}")
                                                }
                                            }
                                        }
                                        
                                        process.waitFor()

                                        val playitProcess = ProcessManager.playitProcess
                                        ProcessManager.serverProcess = null
                                        ProcessManager.killProcessTree(playitProcess)
                                        ProcessManager.playitProcess = null

                                        withContext(Dispatchers.Main) {
                                            onShuttingDownChange(false)
                                            onServerRunningChange(false)
                                            onServerReadyChange(false)
                                            onActiveProcessChange(null)
                                            onPlayitProcessChange(null)
                                            onConsoleAppend("\n[Sistem] Sunucu durduruldu.")
                                        }
                                    } catch (e: Exception) {
                                        val playitProcess = ProcessManager.playitProcess
                                        ProcessManager.serverProcess = null
                                        ProcessManager.killProcessTree(playitProcess)
                                        ProcessManager.playitProcess = null
                                        withContext(Dispatchers.Main) {
                                            onShuttingDownChange(false)
                                            onConsoleAppend("\n[HATA] ${e.message}")
                                            onProcessingChange(false)
                                            onServerRunningChange(false)
                                            onServerReadyChange(false)
                                            onActiveProcessChange(null)
                                            onPlayitProcessChange(null)
                                        }
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = when {
                            isShuttingDown -> AccentOrange
                            isServerRunning -> AccentRed
                            isProcessing -> Color.Gray
                            else -> AccentEmerald
                        }),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.width(160.dp).height(55.dp)
                    ) {
                        Text(
                            text = when {
                                isShuttingDown -> "KAPANIYOR"
                                isServerRunning -> "DURDUR"
                                isProcessing -> "BEKLEYİN"
                                else -> "BAŞLAT"
                            },
                            color = Color.Black, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp
                        )
                    }
                }
            }

            Card(backgroundColor = CardDark, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderColor), modifier = Modifier.weight(1f).fillMaxHeight()) {
                Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center) {
                    val srvRam = String.format(java.util.Locale.US, "%.1f", currentServerRamMb / 1024.0)
                    val sysUsedRam = String.format(java.util.Locale.US, "%.1f", systemUsedRamGb)
                    val sysTotRam = String.format(java.util.Locale.US, "%.1f", systemTotalRamGb)
                    val cpuStr = String.format(java.util.Locale.US, "%.1f", currentCpuUsage)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column { Text("Sunucu RAM", color = Color.Gray, fontSize = 12.sp); Text("$srvRam / $allocatedRam GB", color = AccentBlue, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                        Column(horizontalAlignment = Alignment.End) { Text("Sistem RAM", color = Color.Gray, fontSize = 12.sp); Text("$sysUsedRam / $sysTotRam GB", color = AccentEmerald, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("CPU Kullanımı: %$cpuStr", color = AccentOrange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    LinearProgressIndicator(progress = (currentCpuUsage / 100.0).toFloat(), color = AccentOrange, backgroundColor = SurfaceDark, modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)))
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Row(modifier = Modifier.fillMaxWidth().height(90.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Card(backgroundColor = SurfaceDark, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).fillMaxHeight()) {
                Row(modifier = Modifier.fillMaxSize().clickable {
                    val target = if (!isPlayitEnabled) "127.0.0.1:$serverPort" else playitIp ?: ""
                    if (target.isNotEmpty()) {
                        val sel = StringSelection(target)
                        Toolkit.getDefaultToolkit().systemClipboard.setContents(sel, sel)
                        copyStatus = " (Kopyalandı!)"
                    }
                }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (!isPlayitEnabled) Icons.Default.Dns else Icons.Default.Public, contentDescription = null, tint = AccentEmerald)
                    Spacer(Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.Center) {
                        Text(if (!isPlayitEnabled) "Yerel Bağlantı (Localhost)" else "Uzak Bağlantı (Playit)", color = Color.Gray, fontSize = 12.sp)
                        Text(
                            text = if (!isPlayitEnabled) "127.0.0.1:$serverPort $copyStatus" else if (playitIp != null) "$playitIp $copyStatus" else "Bağlantı Bekleniyor...",
                            color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp
                        )
                    }
                }
            }

            Card(backgroundColor = SurfaceDark, shape = RoundedCornerShape(12.dp), modifier = Modifier.weight(1f).fillMaxHeight()) {
                LazyRow(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    items(macroCommands) { macro ->
                        Button(
                            onClick = { sendRconCommand(macro.second) { res -> lifecycleScope.launch(Dispatchers.Main) { onConsoleAppend("\n[MAKRO] ${macro.first} -> " + if (res.isNotBlank()) res else "Başarılı") } } },
                            colors = ButtonDefaults.buttonColors(backgroundColor = CardDark), shape = RoundedCornerShape(8.dp), modifier = Modifier.height(45.dp).border(1.dp, BorderColor, RoundedCornerShape(8.dp))
                        ) { Text(macro.first, color = Color.LightGray, fontSize = 12.sp) }
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Card(backgroundColor = Color(0xFF07090E), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(16.dp)) {
                    Column(modifier = Modifier.verticalScroll(scrollState)) {
                        Text(text = consoleText, color = Color(0xFFA3BE8C), fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp)
                    }
                }

                Divider(color = BorderColor, thickness = 1.dp)

                Row(modifier = Modifier.fillMaxWidth().background(CardDark).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = AccentEmerald)
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = commandInput, onValueChange = { commandInput = it },
                        placeholder = { Text("Sunucuya komut gönder (örn: say Merhaba)", color = Color.Gray, fontSize = 13.sp) },
                        modifier = Modifier.weight(1f).height(50.dp), singleLine = true,
                        colors = TextFieldDefaults.outlinedTextFieldColors(textColor = Color.White, focusedBorderColor = Color.Transparent, unfocusedBorderColor = Color.Transparent, backgroundColor = Color.Transparent)
                    )
                    Button(
                        onClick = {
                            if (commandInput.isNotBlank()) {
                                val cmd = commandInput
                                commandInput = ""
                                if (isServerReady) {
                                    sendRconCommand(cmd) { res -> lifecycleScope.launch(Dispatchers.Main) { onConsoleAppend("\n[RCON] /$cmd -> " + if (res.isNotBlank()) res else "Gönderildi") } }
                                } else {
                                    try {
                                        val out = activeProcess?.outputStream
                                        out?.write("$cmd\n".toByteArray(Charsets.UTF_8))
                                        out?.flush()
                                        lifecycleScope.launch(Dispatchers.Main) { onConsoleAppend("\n[STDIN] > /$cmd (Motora direkt iletildi)") }
                                    } catch (e: Exception) {}
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(backgroundColor = AccentEmerald), shape = RoundedCornerShape(8.dp)
                    ) { Text("GÖNDER", color = Color.Black, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

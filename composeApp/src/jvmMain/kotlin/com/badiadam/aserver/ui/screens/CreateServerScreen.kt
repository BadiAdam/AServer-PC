package com.badiadam.aserver.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.badiadam.aserver.AccentEmerald
import com.badiadam.aserver.AccentRed
import com.badiadam.aserver.AutoJava
import com.badiadam.aserver.BorderColor
import com.badiadam.aserver.CardDark
import com.badiadam.aserver.CustomDropdownMenu
import com.badiadam.aserver.SurfaceDark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@Composable
fun CreateServerScreen(onServerCreated: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()

    val versionMap = mapOf(
        "26.1" to "26.1",
        "1.21.11" to "1.21.11",
        "1.21.4" to "1.21.4",
        "1.21.1" to "1.21.1",
        "1.20.4" to "1.20.4",
        "1.20.1" to "1.20.1",
        "1.19.4" to "1.19.4",
        "1.19.2" to "1.19.2",
        "1.17.1" to "1.17.1",
        "1.16.5" to "1.16.5",
        "1.12.2" to "1.12.2",
        "1.8.8" to "1.8.8"
    )
    val versionOptions = versionMap.keys.toList()

    var profileName by remember { mutableStateOf("") }
    var version by remember { mutableStateOf(versionOptions[0]) }

    val softwareOptions = listOf("Vanilla (Saf/Orijinal)", "PaperMC (Eklentiler)", "Fabric (Modlar)")
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

    suspend fun updateStatus(message: String) {
        withContext(Dispatchers.Main) {
            statusMessage = message
        }
    }

    suspend fun updateCreatingState(creating: Boolean) {
        withContext(Dispatchers.Main) {
            isCreating = creating
        }
    }

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
                            val baseDir = com.badiadam.aserver.AppConfig.SERVERS_DIR
                            if (!baseDir.exists()) baseDir.mkdirs()
                            val serverDir = File(baseDir, profileName)
                            if (!serverDir.exists()) serverDir.mkdirs()

                            File(serverDir, "eula.txt").writeText("eula=true")

                            val actualVersion = versionMap[version] ?: "26.1"
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
                                updateStatus("Fabric Yükleyici aranıyor...")
                                val metaUrl = URL("https://meta.fabricmc.net/v2/versions/installer")
                                val metaConn = metaUrl.openConnection() as HttpURLConnection
                                val metaResponse = metaConn.inputStream.bufferedReader().readText()
                                val latestInstaller = Regex("\"version\":\"([^\"]+)\"").find(metaResponse)?.groupValues?.get(1) ?: "1.0.1"

                                updateStatus("Fabric İndiriliyor...")
                                val installerUrl = URL("https://maven.fabricmc.net/net/fabricmc/fabric-installer/$latestInstaller/fabric-installer-$latestInstaller.jar")
                                val installerFile = File(serverDir, "fabric-installer.jar")
                                installerUrl.openStream().use { input -> installerFile.outputStream().use { output -> input.copyTo(output) } }

                                updateStatus("Java kontrol ediliyor...")
                                val javaCmd = AutoJava.ensureJavaAndGetPath(actualVersion) { msg -> updateStatus(msg) }

                                updateStatus("Fabric Kuruluyor (Lütfen bekleyin)...")
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
                            } else if (software.contains("Paper")) {
                                updateStatus("PaperMC $actualVersion aranıyor...")
                                val apiUrl = URL("https://api.papermc.io/v2/projects/paper/versions/$actualVersion")
                                val apiConn = apiUrl.openConnection() as HttpURLConnection

                                if (apiConn.responseCode != 200) {
                                    throw Exception("PaperMC bu sürümü ($actualVersion) henüz desteklemiyor!")
                                }

                                val response = apiConn.inputStream.bufferedReader().readText()
                                val buildsStr = response.substringAfter("\"builds\":[").substringBefore("]")
                                val builds = buildsStr.split(",").map { it.trim() }
                                val latestBuild = builds.last()

                                updateStatus("PaperMC ($latestBuild) indiriliyor...")
                                val jarFile = File(serverDir, "server.jar")
                                val downloadUrl = URL("https://api.papermc.io/v2/projects/paper/versions/$actualVersion/builds/$latestBuild/downloads/paper-$actualVersion-$latestBuild.jar")
                                downloadUrl.openStream().use { input -> jarFile.outputStream().use { output -> input.copyTo(output) } }
                            } else {
                                updateStatus("Vanilla $actualVersion aranıyor...")
                                val manifestUrl = URL("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json")
                                val manifestConn = manifestUrl.openConnection() as HttpURLConnection
                                val manifestResponse = manifestConn.inputStream.bufferedReader().readText()

                                val versionRegex = Regex("\"id\"\\s*:\\s*\"$actualVersion\".*?\"url\"\\s*:\\s*\"([^\"]+)\"", RegexOption.DOT_MATCHES_ALL)
                                val versionUrlStr = versionRegex.find(manifestResponse)?.groupValues?.get(1)
                                    ?: throw Exception("Mojang sunucularında $actualVersion sürümü bulunamadı!")

                                updateStatus("Vanilla meta verileri çekiliyor...")
                                val versionConn = URL(versionUrlStr).openConnection() as HttpURLConnection
                                val versionResponse = versionConn.inputStream.bufferedReader().readText()

                                val serverRegex = Regex("\"server\"\\s*:\\s*\\{[^}]*\"url\"\\s*:\\s*\"([^\"]+)\"")
                                val serverJarUrlStr = serverRegex.find(versionResponse)?.groupValues?.get(1)
                                    ?: throw Exception("Mojang bu sürüm ($actualVersion) için sunucu dosyası (server.jar) sağlamamış!")

                                updateStatus("Orijinal Vanilla Server indiriliyor...")
                                val jarFile = File(serverDir, "server.jar")
                                val downloadUrl = URL(serverJarUrlStr)
                                downloadUrl.openStream().use { input -> jarFile.outputStream().use { output -> input.copyTo(output) } }
                            }

                            withContext(Dispatchers.Main) {
                                statusMessage = "BAŞARILI!"
                                onServerCreated()
                            }
                        } catch (e: Exception) {
                            updateStatus("HATA: ${e.message}")
                        } finally {
                            updateCreatingState(false)
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

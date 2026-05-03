package com.badiadam.aserver.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.badiadam.aserver.AccentBlue
import com.badiadam.aserver.AccentEmerald
import com.badiadam.aserver.AccentOrange
import com.badiadam.aserver.AccentRed
import com.badiadam.aserver.BorderColor
import com.badiadam.aserver.CardDark
import com.badiadam.aserver.SurfaceDark
import com.badiadam.aserver.sendRconCommand
import kotlinx.coroutines.delay
import java.io.File

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
            val serverDir = File(com.badiadam.aserver.AppConfig.SERVERS_DIR, activeServerName)
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
                                    sendRconCommand(if (it) "whitelist on" else "whitelist off")
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

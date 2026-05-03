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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Slider
import androidx.compose.material.SliderDefaults
import androidx.compose.material.Switch
import androidx.compose.material.SwitchDefaults
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.badiadam.aserver.AccentEmerald
import com.badiadam.aserver.AccentOrange
import com.badiadam.aserver.AccentRed
import com.badiadam.aserver.AccentBlue
import com.badiadam.aserver.BorderColor
import com.badiadam.aserver.CardDark
import com.badiadam.aserver.CustomDropdownMenu
import com.badiadam.aserver.SurfaceDark
import com.badiadam.aserver.unzipFolder
import com.badiadam.aserver.zipFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.material.Surface
import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MyServersScreen(activeServerName: String, isServerRunning: Boolean, onPlayServer: (String) -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val baseDir = com.badiadam.aserver.AppConfig.SERVERS_DIR
    val backupsDir = File(com.badiadam.aserver.AppConfig.BASE_DIR, "backups")
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
            if (statusMessage.isNotEmpty()) Text(statusMessage, color = if (statusMessage.contains("HATA")) AccentRed else AccentEmerald, fontWeight = FontWeight.SemiBold)
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
                                TooltipArea(
                                    tooltip = {
                                        Surface(
                                            modifier = Modifier.shadow(4.dp),
                                            color = Color(0xFF2D2D2D),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "Ayarlar", 
                                                color = Color.White, 
                                                fontSize = 12.sp, 
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                            )
                                        }
                                    },
                                    delayMillis = 400,
                                    tooltipPlacement = TooltipPlacement.CursorPoint(
                                        alignment = Alignment.BottomEnd,
                                        offset = DpOffset(0.dp, 16.dp)
                                    )
                                ) {
                                    IconButton(onClick = { editingServerProps = folder.name }, modifier = Modifier.background(SurfaceDark, RoundedCornerShape(12.dp)).border(1.dp, BorderColor, RoundedCornerShape(12.dp))) { Icon(Icons.Default.Settings, contentDescription = "Ayarlar", tint = Color.LightGray) }
                                }
                                Spacer(Modifier.width(12.dp))

                                val isThisServerActive = (activeServerName == folder.name) && isServerRunning
                                TooltipArea(
                                    tooltip = {
                                        Surface(
                                            modifier = Modifier.shadow(4.dp),
                                            color = Color(0xFF2D2D2D),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "Dünyayı Sıfırla", 
                                                color = Color.White, 
                                                fontSize = 12.sp, 
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                            )
                                        }
                                    },
                                    delayMillis = 400,
                                    tooltipPlacement = TooltipPlacement.CursorPoint(
                                        alignment = Alignment.BottomEnd,
                                        offset = DpOffset(0.dp, 16.dp)
                                    )
                                ) {
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
                                }
                                Spacer(Modifier.width(12.dp))

                                if (restoringFolderProgress == folder.name) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AccentOrange, strokeWidth = 2.dp)
                                } else {
                                    TooltipArea(
                                        tooltip = {
                                            Surface(
                                                modifier = Modifier.shadow(4.dp),
                                                color = Color(0xFF2D2D2D),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = "Geri Yükle", 
                                                    color = Color.White, 
                                                    fontSize = 12.sp, 
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                                )
                                            }
                                        },
                                        delayMillis = 400,
                                        tooltipPlacement = TooltipPlacement.CursorPoint(
                                            alignment = Alignment.BottomEnd,
                                            offset = DpOffset(0.dp, 16.dp)
                                        )
                                    ) {
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
                                }
                                Spacer(Modifier.width(12.dp))

                                if (backingUpFolder == folder.name) { CircularProgressIndicator(modifier = Modifier.size(24.dp), color = AccentBlue, strokeWidth = 2.dp) } else {
                                    TooltipArea(
                                        tooltip = {
                                            Surface(
                                                modifier = Modifier.shadow(4.dp),
                                                color = Color(0xFF2D2D2D),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text(
                                                    text = "Yedekle", 
                                                    color = Color.White, 
                                                    fontSize = 12.sp, 
                                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                                )
                                            }
                                        },
                                        delayMillis = 400,
                                        tooltipPlacement = TooltipPlacement.CursorPoint(
                                            alignment = Alignment.BottomEnd,
                                            offset = DpOffset(0.dp, 16.dp)
                                        )
                                    ) {
                                        IconButton(onClick = {
                                            backingUpFolder = folder.name
                                            statusMessage = "${folder.name} yedekleniyor..."
                                            coroutineScope.launch(Dispatchers.IO) {
                                                try {
                                                    val dateStr = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
                                                    val zipFile = File(backupsDir, "${folder.name}_Backup_$dateStr.zip")
                                                    zipFolder(folder, zipFile)
                                                    withContext(Dispatchers.Main) {
                                                        statusMessage = "Yedek başarıyla alındı."
                                                    }
                                                } catch (e: Exception) {
                                                    withContext(Dispatchers.Main) {
                                                        statusMessage = "Hata: ${e.message}"
                                                    }
                                                } finally {
                                                    withContext(Dispatchers.Main) {
                                                        backingUpFolder = null
                                                    }
                                                }
                                            }
                                        }, modifier = Modifier.background(SurfaceDark, RoundedCornerShape(12.dp)).border(1.dp, BorderColor, RoundedCornerShape(12.dp))) { Icon(Icons.Default.Backup, contentDescription = "Yedekle", tint = AccentBlue) }
                                    }
                                }
                                Spacer(Modifier.width(12.dp))

                                TooltipArea(
                                    tooltip = {
                                        Surface(
                                            modifier = Modifier.shadow(4.dp),
                                            color = Color(0xFF2D2D2D),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                text = "Sil", 
                                                color = Color.White, 
                                                fontSize = 12.sp, 
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                            )
                                        }
                                    },
                                    delayMillis = 400,
                                    tooltipPlacement = TooltipPlacement.CursorPoint(
                                        alignment = Alignment.BottomEnd,
                                        offset = DpOffset(0.dp, 16.dp)
                                    )
                                ) {
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

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun RestoreBackupDialog(serverName: String, onDismiss: () -> Unit, onRestore: (File) -> Unit) {
    val backupsDir = File(com.badiadam.aserver.AppConfig.BASE_DIR, "backups")
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
                                    Text(backupFile.name, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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

@OptIn(ExperimentalMaterialApi::class)
@Composable
fun ServerPropertiesEditorDialog(serverName: String, onDismiss: () -> Unit) {
    val coroutineScope = rememberCoroutineScope()
    val propsFile = File(com.badiadam.aserver.AppConfig.SERVERS_DIR, "$serverName/server.properties")

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

                                withContext(Dispatchers.Main) {
                                    statusMessage = "Başarıyla Kaydedildi!"
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    statusMessage = "Hata: ${e.message}"
                                }
                            }
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

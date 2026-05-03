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
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.Card
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.badiadam.aserver.AccentBlue
import com.badiadam.aserver.AccentEmerald
import com.badiadam.aserver.AccentOrange
import com.badiadam.aserver.AccentRed
import com.badiadam.aserver.BorderColor
import com.badiadam.aserver.CardDark
import com.badiadam.aserver.ModrinthMod
import com.badiadam.aserver.SpigetPlugin
import com.badiadam.aserver.SurfaceDark
import com.badiadam.aserver.parseModrinth
import com.badiadam.aserver.parseModrinthVersion
import com.badiadam.aserver.parseSpiget
import com.badiadam.aserver.utils.CurseForgePackManager
import com.badiadam.aserver.utils.ModDownloadInfo
import com.badiadam.aserver.utils.ModPackManager
import com.badiadam.aserver.utils.ModpackDownloader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.material.Surface
import androidx.compose.foundation.ExperimentalFoundationApi

@OptIn(ExperimentalMaterialApi::class, ExperimentalFoundationApi::class)
@Composable
fun FileManagerScreen() {
    val coroutineScope = rememberCoroutineScope()
    val baseDir = com.badiadam.aserver.AppConfig.SERVERS_DIR
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
    var isModpackInstalling by remember { mutableStateOf(false) }
    var modpackProgress by remember { mutableStateOf(0f) }
    var modpackStatusText by remember { mutableStateOf("") }
    var showModpackDialog by remember { mutableStateOf(false) }

    fun refreshFiles() { files = currentDir.listFiles()?.toList()?.sortedWith(compareBy({ !it.isDirectory }, { it.name })) ?: emptyList() }

    fun pickModpackFile(): File? {
        val chooser = JFileChooser().apply {
            dialogTitle = "Modpack Kur (.mrpack, .zip)"
            fileSelectionMode = JFileChooser.FILES_ONLY
            isAcceptAllFileFilterUsed = false
            fileFilter = FileNameExtensionFilter("Modpack Dosyası (*.mrpack, *.zip)", "mrpack", "zip")
        }

        val result = chooser.showOpenDialog(null)
        return if (result == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
    }

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
                    var mcVersion = "26.1"
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

    if (showModpackDialog) {
        AlertDialog(
            onDismissRequest = { if (!isModpackInstalling) showModpackDialog = false },
            backgroundColor = CardDark,
            modifier = Modifier.width(560.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, BorderColor, RoundedCornerShape(16.dp)),
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Archive, contentDescription = null, tint = AccentBlue)
                    Spacer(Modifier.width(8.dp))
                    Text("Modpack Kurulumu", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column {
                    Text(modpackStatusText.ifBlank { "Lutfen bekleyin..." }, color = Color.LightGray, fontSize = 14.sp)
                    Spacer(Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = modpackProgress.coerceIn(0f, 1f),
                        color = AccentEmerald,
                        backgroundColor = SurfaceDark,
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50))
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("${(modpackProgress.coerceIn(0f, 1f) * 100).toInt()}%", color = AccentEmerald, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            confirmButton = {
                if (!isModpackInstalling) {
                    TextButton(onClick = { showModpackDialog = false }) {
                        Text("Tamam", color = AccentEmerald, fontWeight = FontWeight.Bold)
                    }
                }
            },
            dismissButton = {}
        )
    }

    if (fileToEdit != null) {
        Column(modifier = Modifier.fillMaxSize().padding(40.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TooltipArea(
                        tooltip = {
                            Surface(
                                modifier = Modifier.shadow(4.dp),
                                color = Color(0xFF2D2D2D),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Geri", 
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
                        IconButton(onClick = { fileToEdit = null }, modifier = Modifier.background(SurfaceDark, RoundedCornerShape(12.dp)).border(1.dp, BorderColor, RoundedCornerShape(12.dp))) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Geri", tint = Color.White)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(fileToEdit!!.name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (statusMessage.isNotEmpty()) { Text(statusMessage, color = if (statusMessage.contains("HATA")) AccentRed else AccentEmerald, fontWeight = FontWeight.Bold, modifier = Modifier.padding(end = 16.dp)) }
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
                        Text(statusMessage, color = if (statusMessage.contains("HATA")) AccentRed else AccentEmerald, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
                Row {
                    if (currentDir.name.equals("plugins", ignoreCase = true)) {
                        TooltipArea(
                            tooltip = {
                                Surface(
                                    modifier = Modifier.shadow(4.dp),
                                    color = Color(0xFF2D2D2D),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Eklenti Mağazası", 
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
                            IconButton(onClick = { showPluginStore = true }, modifier = Modifier.background(SurfaceDark, RoundedCornerShape(12.dp)).border(1.dp, BorderColor, RoundedCornerShape(12.dp))) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = "Eklenti Mağazası", tint = AccentOrange)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                    } else if (currentDir.name.equals("mods", ignoreCase = true)) {
                        TooltipArea(
                            tooltip = {
                                Surface(
                                    modifier = Modifier.shadow(4.dp),
                                    color = Color(0xFF2D2D2D),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Mod Mağazası", 
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
                            IconButton(onClick = { showModStore = true }, modifier = Modifier.background(SurfaceDark, RoundedCornerShape(12.dp)).border(1.dp, BorderColor, RoundedCornerShape(12.dp))) {
                                Icon(Icons.Default.Build, contentDescription = "Mod Mağazası", tint = AccentEmerald)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                    }

                    if (currentDir.name.equals("mods", ignoreCase = true)) {
                        TooltipArea(
                            tooltip = {
                                Surface(
                                    modifier = Modifier.shadow(4.dp),
                                    color = Color(0xFF2D2D2D),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Modpack Kur (.mrpack, .zip)", 
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
                                    val serverDir = currentDir.parentFile
                                    if (serverDir == null || !serverDir.exists()) {
                                        statusMessage = "HATA: Sunucu klasoru bulunamadi."
                                        return@IconButton
                                    }
    
                                    val selectedFile = pickModpackFile() ?: return@IconButton
                                    coroutineScope.launch {
                                        val hasErrors = java.util.concurrent.atomic.AtomicBoolean(false)
                                        try {
                                            showModpackDialog = true
                                            isModpackInstalling = true
                                            modpackProgress = 0f
                                            modpackStatusText = "Modpack ayristiriliyor..."
                                            statusMessage = ""
    
                                            val extension = selectedFile.extension.lowercase()
                                            if (extension == "zip") {
                                                modpackStatusText = "Server Pack cikariliyor..."
                                                CurseForgePackManager.installServerPack(selectedFile, serverDir)
                                                
                                                modpackProgress = 1f
                                                modpackStatusText = "Server Pack basariyla kuruldu."
                                                statusMessage = "Server Pack basariyla kuruldu!"
                                            } else {
                                                val modList: List<ModDownloadInfo> = when (extension) {
                                                    "mrpack" -> ModPackManager.parseMrPack(selectedFile, serverDir)
                                                    else -> throw IllegalArgumentException("Desteklenmeyen mod paketi türü: .$extension")
                                                }
    
                                                if (modList.isEmpty()) {
                                                    modpackProgress = 1f
                                                    modpackStatusText = "Sunucuya uygun mod bulunamadi veya pakette mod yok."
                                                    statusMessage = "Modpack ayristirildi fakat indirilecek sunucu modu yok."
                                                } else {
                                                    modpackStatusText = "${modList.size} mod indiriliyor..."
                                                    ModpackDownloader.downloadMods(modList, serverDir) { current, total, msg ->
                                                        if (msg.startsWith("HATA:")) {
                                                            hasErrors.set(true)
                                                        }
                                                        coroutineScope.launch(Dispatchers.Main) {
                                                            modpackProgress = if (total > 0) current.toFloat() / total else 1f
                                                            modpackStatusText = msg
                                                        }
                                                    }
                                                    
                                                    modpackProgress = 1f
                                                    modpackStatusText = if (hasErrors.get()) "Kurulum tamamlandi, ancak bazi modlar indirilemedi." else "Tum modlar basariyla kuruldu."
                                                    statusMessage = if (hasErrors.get()) "Modpack kuruldu, ancak bazi dosyalar indirilemedi." else "Modpack basariyla kuruldu!"
                                                }
                                            }
                                        } catch (e: Exception) {
                                            modpackProgress = 0f
                                            modpackStatusText = "Kurulum basarisiz oldu: ${e.message}"
                                            statusMessage = "HATA: ${e.message}"
                                            showModpackDialog = true
                                        } finally {
                                            refreshFiles()
                                            isModpackInstalling = false
                                        }
                                    }
                                },
                                enabled = !isModpackInstalling,
                                modifier = Modifier.background(SurfaceDark, RoundedCornerShape(12.dp)).border(1.dp, BorderColor, RoundedCornerShape(12.dp))
                            ) {
                                Icon(Icons.Default.Archive, contentDescription = "Modpack Kur (.mrpack, .zip)", tint = AccentBlue)
                            }
                        }
                        Spacer(Modifier.width(12.dp))
                    }

                    TooltipArea(
                        tooltip = {
                            Surface(
                                modifier = Modifier.shadow(4.dp),
                                color = Color(0xFF2D2D2D),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Yeni Dosya", 
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
                        IconButton(onClick = { showNewFileDialog = true }, modifier = Modifier.background(SurfaceDark, RoundedCornerShape(12.dp)).border(1.dp, BorderColor, RoundedCornerShape(12.dp))) {
                            Icon(Icons.Default.NoteAdd, contentDescription = "Yeni Dosya", tint = AccentBlue)
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
                                    text = "Yeni Klasör", 
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
                        IconButton(onClick = { showNewFolderDialog = true }, modifier = Modifier.background(SurfaceDark, RoundedCornerShape(12.dp)).border(1.dp, BorderColor, RoundedCornerShape(12.dp))) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = "Yeni Klasör", tint = AccentEmerald)
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))

            Card(backgroundColor = CardDark, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, BorderColor), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (currentDir.absolutePath != baseDir.absolutePath) {
                        TooltipArea(
                            tooltip = {
                                Surface(
                                    modifier = Modifier.shadow(4.dp),
                                    color = Color(0xFF2D2D2D),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Bir Üst Klasör", 
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
                            IconButton(onClick = { currentDir = currentDir.parentFile ?: baseDir; refreshFiles(); statusMessage = "" }) {
                                Icon(Icons.Default.ArrowUpward, contentDescription = "Bir Üst Klasör", tint = Color.White)
                            }
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
}

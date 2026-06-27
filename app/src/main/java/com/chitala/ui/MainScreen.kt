package com.chitala.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chitala.reader.FileReader
import com.chitala.reader.PrefsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { PrefsManager(context) }
    var isDark by remember { mutableStateOf(prefs.isDarkTheme) }
    var fileContent by remember { mutableStateOf<FileReader.FileContent?>(null) }
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showHistory by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            isLoading = true
            errorMessage = null
            scope.launch {
                try {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.takePersistableUriPermission(
                            uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        val cursor = context.contentResolver.query(uri, null, null, null, null)
                        val name = cursor?.use {
                            if (it.moveToFirst()) {
                                val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                                if (idx >= 0) it.getString(idx) else "unknown"
                            } else "unknown"
                        } ?: "unknown"

                        val result = FileReader.read(context, uri, name)

                        if (result.type == FileReader.FileType.PDF) {
                            val inputStream = context.contentResolver.openInputStream(uri)
                            val tmpFile = File(context.cacheDir, "temp_pdf.pdf")
                            tmpFile.outputStream().use { out -> inputStream?.copyTo(out) }
                            inputStream?.close()
                            withContext(Dispatchers.Main) { pdfFile = tmpFile }
                        } else {
                            withContext(Dispatchers.Main) {
                                fileContent = result
                                prefs.addRecentFile(name, uri.toString())
                            }
                        }
                    }
                } catch (e: Exception) {
                    errorMessage = "Ошибка: ${e.message}"
                } finally {
                    isLoading = false
                }
            }
        }
    }

    if (pdfFile != null) {
        PdfViewer(
            file = pdfFile!!,
            onClose = { pdfFile = null },
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = fileContent?.title ?: "Читалка",
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    if (fileContent != null) {
                        IconButton(onClick = { fileContent = null; searchQuery = "" }) {
                            Icon(Icons.Default.ArrowBack, "Назад")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                actions = {
                    if (fileContent != null) {
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(Icons.Default.Search, "Поиск", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                        IconButton(onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, fileContent!!.content)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Поделиться"))
                        }) {
                            Icon(Icons.Default.Share, "Поделиться", tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    IconButton(onClick = {
                        isDark = !isDark
                        prefs.isDarkTheme = isDark
                    }) {
                        Icon(
                            if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            "Тема",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                    IconButton(onClick = { showHistory = !showHistory }) {
                        Icon(Icons.Default.History, "История", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                    IconButton(onClick = { launcher.launch(FileReader.getMimeType()) }) {
                        Icon(Icons.Default.FolderOpen, "Открыть", tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                errorMessage != null -> {
                    ErrorMessage(
                        message = errorMessage!!,
                        onDismiss = { errorMessage = null },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                showHistory -> {
                    RecentFilesScreen(
                        prefs = prefs,
                        onSelect = { file ->
                            showHistory = false
                            isLoading = true
                            try {
                                val uri = Uri.parse(file.uri)
                                val result = FileReader.read(context, uri, file.name)
                                if (result.type == FileReader.FileType.PDF) {
                                    val inputStream = context.contentResolver.openInputStream(uri)
                                    val tmpFile = File(context.cacheDir, "temp_pdf.pdf")
                                    tmpFile.outputStream().use { out -> inputStream?.copyTo(out) }
                                    inputStream?.close()
                                    pdfFile = tmpFile
                                } else {
                                    fileContent = result
                                }
                            } catch (e: Exception) {
                                errorMessage = "Ошибка: ${e.message}"
                            } finally {
                                isLoading = false
                            }
                        },
                        onClear = { prefs.clearRecentFiles() },
                        onDismiss = { showHistory = false },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                fileContent == null -> {
                    WelcomeScreen(
                        onOpenFile = { launcher.launch(FileReader.getMimeType()) },
                        recentFiles = prefs.getRecentFiles().take(5),
                        onRecentClick = { file ->
                            isLoading = true
                            try {
                                val uri = Uri.parse(file.uri)
                                val result = FileReader.read(context, uri, file.name)
                                if (result.type == FileReader.FileType.PDF) {
                                    val inputStream = context.contentResolver.openInputStream(uri)
                                    val tmpFile = File(context.cacheDir, "temp_pdf.pdf")
                                    tmpFile.outputStream().use { out -> inputStream?.copyTo(out) }
                                    inputStream?.close()
                                    pdfFile = tmpFile
                                } else {
                                    fileContent = result
                                }
                            } catch (e: Exception) {
                                errorMessage = "Ошибка: ${e.message}"
                            } finally {
                                isLoading = false
                            }
                        },
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                else -> {
                    TextViewer(
                        content = fileContent!!.content,
                        type = fileContent!!.type,
                        searchQuery = searchQuery,
                        onSearchQueryChange = { searchQuery = it },
                        showSearch = showSearch,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

@Composable
fun WelcomeScreen(
    onOpenFile: () -> Unit,
    recentFiles: List<PrefsManager.RecentFile>,
    onRecentClick: (PrefsManager.RecentFile) -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = remember { SimpleDateFormat("dd.MM HH:mm", Locale.getDefault()) }

    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.Description,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text("Читалка", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Универсальный читатель файлов",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("TXT", "MD", "CSV", "DOCX", "XLSX", "PDF").forEach { ext ->
                SuggestionChip(onClick = {}, label = { Text(ext, fontSize = 12.sp) })
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onOpenFile, modifier = Modifier.fillMaxWidth(0.6f)) {
            Icon(Icons.Default.FolderOpen, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Открыть файл")
        }

        if (recentFiles.isNotEmpty()) {
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                "Недавние:",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            recentFiles.forEach { file ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onRecentClick(file) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(dateFormat.format(Date(file.timestamp)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RecentFilesScreen(
    prefs: PrefsManager,
    onSelect: (PrefsManager.RecentFile) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val files = remember { mutableStateListOf(*prefs.getRecentFiles().toTypedArray()) }
    val dateFormat = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    Column(modifier = modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Недавние файлы", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            if (files.isNotEmpty()) {
                TextButton(onClick = { onClear(); files.clear() }) {
                    Text("Очистить")
                }
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, "Закрыть")
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (files.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Нет недавних файлов", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            files.forEach { file ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onSelect(file) }
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(dateFormat.format(Date(file.timestamp)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TextViewer(
    content: String,
    type: FileReader.FileType,
    searchQuery: String = "",
    onSearchQueryChange: (String) -> Unit = {},
    showSearch: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val prefs = remember { PrefsManager(context) }
    var fontSize by remember { mutableIntStateOf(prefs.fontSize) }

    val displayContent = remember(content, searchQuery) {
        if (searchQuery.isBlank()) content
        else {
            val regex = Regex(Regex.escape(searchQuery), RegexOption.IGNORE_CASE)
            content.replace(regex) { "【${it.value}】" }
        }
    }

    Column(modifier = modifier) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    when (type) {
                        FileReader.FileType.MARKDOWN -> Icons.Default.Code
                        FileReader.FileType.CSV -> Icons.Default.TableChart
                        FileReader.FileType.PDF -> Icons.Default.PictureAsPdf
                        else -> Icons.Default.TextSnippet
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(type.name, style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { fontSize = (fontSize - 1).coerceAtLeast(10); prefs.fontSize = fontSize }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Remove, "Уменьшить", modifier = Modifier.size(18.dp))
                }
                Text("${fontSize}sp", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 4.dp))
                IconButton(onClick = { fontSize = (fontSize + 1).coerceAtMost(28); prefs.fontSize = fontSize }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, "Увеличить", modifier = Modifier.size(18.dp))
                }
                Spacer(modifier = Modifier.weight(1f))
                Text("${content.length} символов", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        AnimatedVisibility(visible = showSearch) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Поиск...") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { onSearchQueryChange("") }) {
                                Icon(Icons.Default.Clear, "Очистить")
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(8.dp)
                )
            }
        }

        Text(
            text = displayContent,
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(16.dp),
            style = MaterialTheme.typography.bodyLarge.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.6).sp
            )
        )
    }
}

@Composable
fun ErrorMessage(message: String, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Default.Error, null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(16.dp))
        Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onDismiss) { Text("Закрыть") }
    }
}

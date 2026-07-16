package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainAppScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppScreen(viewModel: PDFViewModel = viewModel()) {
    val context = LocalContext.current
    val activePdfState by viewModel.activePdf.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val scannedPdfs by viewModel.scannedPdfs.collectAsState()
    val recentPdfs by viewModel.recentPdfs.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()

    var showPermissionSettingsDialog by remember { mutableStateOf(false) }

    // System Picker launcher
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            if (uri != null) {
                viewModel.openPdfFromUri(context, uri)
            }
        }
    )

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                Toast.makeText(context, "تم منح الصلاحيات بنجاح", Toast.LENGTH_SHORT).show()
                viewModel.scanDevicePdfFiles(context)
            } else {
                Toast.makeText(context, "الصلاحيات مطلوبة لمسح الملفات", Toast.LENGTH_LONG).show()
            }
        }
    )

    // Check and request permissions
    fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+: We can also request MANAGE_EXTERNAL_STORAGE for deep scanning if needed,
            // but typical READ_EXTERNAL_STORAGE / direct system picker is clean.
            // Let's ask for READ_EXTERNAL_STORAGE (legacy) or directly run scan.
            val legacyPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

            if (legacyPermission) {
                viewModel.scanDevicePdfFiles(context)
            } else {
                permissionLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE))
            }
        } else {
            // Android 10 and below: READ & WRITE storage are dangerous
            val permissionsToRequest = mutableListOf<String>()
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }

            if (permissionsToRequest.isNotEmpty()) {
                permissionLauncher.launch(permissionsToRequest.toTypedArray())
            } else {
                viewModel.scanDevicePdfFiles(context)
            }
        }
    }

    // Trigger initial scan and loading of recents on startup
    LaunchedEffect(Unit) {
        viewModel.loadRecents(context)
        checkAndRequestPermissions()
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (activePdfState == null) {
                // Home Dashboard Screen
                HomeScreen(
                    scannedPdfs = scannedPdfs,
                    recentPdfs = recentPdfs,
                    searchQuery = searchQuery,
                    isScanning = isScanning,
                    onSearchChange = { viewModel.setSearchQuery(it) },
                    onOpenFilePicker = { filePickerLauncher.launch(arrayOf("application/pdf")) },
                    onOpenSamplePdf = { viewModel.openSamplePdf() },
                    onOpenFile = { file ->
                        viewModel.openPdfFromUri(context, Uri.fromFile(File(file.path)), file.name)
                    },
                    onTriggerScan = { checkAndRequestPermissions() },
                    onClearRecents = { viewModel.clearRecents(context) }
                )
            } else {
                // Active PDF Reader Screen
                PdfReaderScreen(
                    activeState = activePdfState!!,
                    onBack = { viewModel.closeActivePdf() },
                    onPageChanged = { page, total -> viewModel.updateCurrentPage(page, total) },
                    onScaleChanged = { scale -> viewModel.updateScale(scale) },
                    onThemeChanged = { theme -> viewModel.updateTheme(theme) },
                    onScrollModeChanged = { mode -> viewModel.updateScrollDirection(mode) },
                    onSearchQueryChanged = { query -> viewModel.updatePdfSearchQuery(query) }
                )
            }

            // Simple loading overlay
            if (isScanning && activePdfState == null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.5f))
                        .clickable(enabled = false) {},
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "جاري مسح وتحميل ملفات PDF...",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    scannedPdfs: List<PdfFile>,
    recentPdfs: List<PdfFile>,
    searchQuery: String,
    isScanning: Boolean,
    onSearchChange: (String) -> Unit,
    onOpenFilePicker: () -> Unit,
    onOpenSamplePdf: () -> Unit,
    onOpenFile: (PdfFile) -> Unit,
    onTriggerScan: () -> Unit,
    onClearRecents: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    val filteredFiles = scannedPdfs.filter {
        it.name.contains(searchQuery, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Upper Gradient Panel with Beautiful Welcome Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "قارئ PDF الممتاز",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Right
                        )
                        Text(
                            text = "محرك عرض ذكي مدعوم بـ Mozilla PDF.js",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.PictureAsPdf,
                        contentDescription = "PDF Pro",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action cards layout
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Open File Picker Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("picker_card")
                            .clickable { onOpenFilePicker() },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = "فتح ملف",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "فتح ملف PDF",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Open Sample PDF Card
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sample_card")
                            .clickable { onOpenSamplePdf() },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Book,
                                contentDescription = "ملف تجريبي",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "ملف تجريبي",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }

        // Native Search Input inside Home Panel
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .testTag("file_search_input"),
            placeholder = { Text("ابحث عن ملفات PDF...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "بحث") },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "مسح")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Tabs Row
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { selectedTab = 0 },
                text = { Text("الملفات الممسوحة (${filteredFiles.size})", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { selectedTab = 1 },
                text = { Text("المفتوحة مؤخراً (${recentPdfs.size})", fontWeight = FontWeight.Bold) }
            )
        }

        // List display based on selected tab
        Box(modifier = Modifier.weight(1f)) {
            if (selectedTab == 0) {
                // Scanned Files Tab
                if (filteredFiles.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Default.Storage,
                        message = if (searchQuery.isNotEmpty()) "لا توجد نتائج بحث مطابقة" else "لم يتم العثور على ملفات PDF في الجهاز",
                        buttonText = "إعادة فحص الذاكرة",
                        onAction = onTriggerScan
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredFiles) { file ->
                            PdfFileRow(file = file, onClick = { onOpenFile(file) })
                        }
                    }
                }
            } else {
                // Recent Files Tab
                if (recentPdfs.isEmpty()) {
                    EmptyStateView(
                        icon = Icons.Default.History,
                        message = "لا توجد ملفات مفتوحة مؤخراً",
                        buttonText = "اختر ملفاً لعرضه",
                        onAction = onOpenFilePicker
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onClearRecents) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("مسح السجل", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .weight(1f),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(recentPdfs) { file ->
                                PdfFileRow(file = file, onClick = { onOpenFile(file) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(
    imageVector: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.PictureAsPdf,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    message: String,
    buttonText: String,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = onAction,
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
        ) {
            Text(buttonText, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun PdfFileRow(file: PdfFile, onClick: () -> Unit) {
    val formatter = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val formattedDate = remember(file.dateModified) { formatter.format(Date(file.dateModified)) }
    val formattedSize = remember(file.size) {
        val kb = file.size / 1024
        if (kb > 1024) {
            String.format(Locale.US, "%.2f MB", kb / 1024.0)
        } else {
            "$kb KB"
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pdf_file_${file.id}")
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Document Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFFFDE8E8), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = Color(0xFFC62828),
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // File Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfReaderScreen(
    activeState: ActivePdfState,
    onBack: () -> Unit,
    onPageChanged: (Int, Int) -> Unit,
    onScaleChanged: (Float) -> Unit,
    onThemeChanged: (ReaderTheme) -> Unit,
    onScrollModeChanged: (ScrollDirection) -> Unit,
    onSearchQueryChanged: (String) -> Unit
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var showJumpDialog by remember { mutableStateOf(false) }
    var showThemeMenu by remember { mutableStateOf(false) }
    var isSearchExpanded by remember { mutableStateOf(false) }

    val activeSearchQuery = activeState.pdfSearchQuery

    // Format current URL
    val webViewerUrl = remember(activeState.uriPath) {
        "file:///android_asset/pdfjs/web/viewer.html?file=${Uri.encode(activeState.uriPath)}"
    }

    // Share PDF intent
    fun sharePdf() {
        if (activeState.isSample) {
            Toast.makeText(context, "الملف التجريبي لا يمكن مشاركته", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val file = File(activeState.uriPath.replace("file://", ""))
            if (file.exists()) {
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "مشاركة ملف PDF"))
            } else {
                Toast.makeText(context, "تعذر تحديد موقع الملف لمشاركته", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "فشلت مشاركة الملف", Toast.LENGTH_SHORT).show()
        }
    }

    // Web bridge JS runner helper
    fun runJs(script: String) {
        webViewInstance?.post {
            webViewInstance?.evaluateJavascript(script, null)
        }
    }

    // React to changes in theme, scale, search or scroll direction
    LaunchedEffect(activeState.theme) {
        val filter = activeState.theme.filterCss
        if (filter == "none") {
            runJs("document.getElementById('viewer').style.filter = 'none';")
        } else {
            runJs("document.getElementById('viewer').style.filter = '$filter';")
        }
    }

    LaunchedEffect(activeState.scrollDirection) {
        runJs("""
            if (window.PDFViewerApplication && PDFViewerApplication.pdfViewer) {
                PDFViewerApplication.pdfViewer.scrollMode = ${activeState.scrollDirection.value};
            }
        """.trimIndent())
    }

    LaunchedEffect(activeSearchQuery) {
        if (activeSearchQuery.isNotEmpty()) {
            runJs("""
                if (window.PDFViewerApplication) {
                    PDFViewerApplication.eventBus.dispatch('find', {
                        query: '${activeSearchQuery.replace("'", "\\'")}',
                        phraseSearch: true,
                        caseSensitive: false,
                        entireWord: false,
                        highlightAll: true,
                        findPrevious: false,
                        matchDiacritics: false
                    });
                }
            """.trimIndent())
        } else {
            // Clear find highlight
            runJs("""
                if (window.PDFViewerApplication) {
                    PDFViewerApplication.eventBus.dispatch('find', {
                        query: '',
                        phraseSearch: true,
                        caseSensitive: false,
                        entireWord: false,
                        highlightAll: true,
                        findPrevious: false,
                        matchDiacritics: false
                    });
                }
            """.trimIndent())
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Fullscreen WebView
        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .testTag("pdf_webview"),
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewInstance = this
                    settings.apply {
                        javaScriptEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        allowFileAccessFromFileURLs = true
                        allowUniversalAccessFromFileURLs = true
                        domStorageEnabled = true
                        builtInZoomControls = false
                        displayZoomControls = false
                    }

                    // Expose safe JS interface to Android
                    addJavascriptInterface(object : Any() {
                        @JavascriptInterface
                        fun onPageChanged(pageNumber: Int, pagesCount: Int) {
                            onPageChanged(pageNumber, pagesCount)
                        }
                    }, "AndroidBridge")

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            // Initialize JS listeners and event listeners for page changing
                            val initScript = """
                                if (window.PDFViewerApplication) {
                                    PDFViewerApplication.initializedPromise.then(() => {
                                        PDFViewerApplication.eventBus.on('pagechanging', (e) => {
                                            AndroidBridge.onPageChanged(e.pageNumber, PDFViewerApplication.pagesCount || e.pagesCount);
                                        });
                                        // Send initial page change events once loaded
                                        setTimeout(() => {
                                            AndroidBridge.onPageChanged(
                                                PDFViewerApplication.pdfViewer.currentPageNumber,
                                                PDFViewerApplication.pdfViewer.pagesCount
                                            );
                                            // Apply current scroll direction and theme
                                            PDFViewerApplication.pdfViewer.scrollMode = ${activeState.scrollDirection.value};
                                            var filter = "${activeState.theme.filterCss}";
                                            if (filter !== "none") {
                                                document.getElementById('viewer').style.filter = filter;
                                            }
                                        }, 1000);
                                    });
                                }
                            """.trimIndent()
                            view?.evaluateJavascript(initScript, null)
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            return false // Keep inside webview
                        }
                    }

                    loadUrl(webViewerUrl)
                }
            },
            update = { /* Updates handled by LaunchedEffects */ }
        )

        // Rounded CAPSULE Top Bar Floating on top
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .testTag("capsule_top_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("top_bar_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Content Area: Either File Name or Expanded Search Input
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!isSearchExpanded) {
                            // File Name with autoscale style
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = activeState.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = if (activeState.name.length > 25) 12.sp else 14.sp,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = "صفحة ${activeState.currentPage} من ${activeState.totalPages}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        } else {
                            // Realtime Search inside the PDF
                            val keyboardController = LocalSoftwareKeyboardController.current
                            OutlinedTextField(
                                value = activeSearchQuery,
                                onValueChange = { onSearchQueryChanged(it) },
                                placeholder = { Text("بحث داخل الملف...", fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                trailingIcon = {
                                    Row {
                                        if (activeSearchQuery.isNotEmpty()) {
                                            IconButton(
                                                onClick = {
                                                    runJs("""
                                                        if (window.PDFViewerApplication) {
                                                            PDFViewerApplication.eventBus.dispatch('find', {
                                                                query: '${activeSearchQuery.replace("'", "\\'")}',
                                                                phraseSearch: true,
                                                                caseSensitive: false,
                                                                entireWord: false,
                                                                highlightAll: true,
                                                                findPrevious: true,
                                                                matchDiacritics: false
                                                            });
                                                        }
                                                    """.trimIndent())
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "السابق", modifier = Modifier.size(20.dp))
                                            }
                                            IconButton(
                                                onClick = {
                                                    runJs("""
                                                        if (window.PDFViewerApplication) {
                                                            PDFViewerApplication.eventBus.dispatch('find', {
                                                                query: '${activeSearchQuery.replace("'", "\\'")}',
                                                                phraseSearch: true,
                                                                caseSensitive: false,
                                                                entireWord: false,
                                                                highlightAll: true,
                                                                findPrevious: false,
                                                                matchDiacritics: false,
                                                                findNext: true
                                                            });
                                                        }
                                                    """.trimIndent())
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "التالي", modifier = Modifier.size(20.dp))
                                            }
                                        }
                                        IconButton(
                                            onClick = {
                                                onSearchQueryChanged("")
                                                isSearchExpanded = false
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(Icons.Default.Close, contentDescription = "إغلاق البحث", modifier = Modifier.size(20.dp))
                                        }
                                    }
                                },
                                singleLine = true,
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .padding(vertical = 2.dp)
                                    .testTag("pdf_search_input"),
                                shape = RoundedCornerShape(20.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color.Transparent,
                                    focusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f),
                                    unfocusedContainerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.5f)
                                ),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() })
                            )
                        }
                    }

                    // Actions Area: Search and Share buttons
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isSearchExpanded) {
                            IconButton(onClick = { isSearchExpanded = true }, modifier = Modifier.testTag("top_bar_search")) {
                                Icon(Icons.Default.Search, contentDescription = "بحث", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        IconButton(onClick = { sharePdf() }, modifier = Modifier.testTag("top_bar_share")) {
                            Icon(Icons.Default.Share, contentDescription = "مشاركة", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }

        // Native Floating Bottom Control Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("native_bottom_bar")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Zoom Out
                    IconButton(
                        onClick = {
                            onScaleChanged((activeState.currentScale - 0.25f).coerceAtLeast(0.5f))
                            runJs("PDFViewerApplication.pdfViewer.currentScale = ${(activeState.currentScale - 0.25f).coerceAtLeast(0.5f)};")
                        },
                        modifier = Modifier.testTag("zoom_out_button")
                    ) {
                        Icon(Icons.Default.ZoomOut, contentDescription = "تصغير", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Previous Page
                    IconButton(
                        onClick = { runJs("PDFViewerApplication.pdfViewer.previousPage();") },
                        enabled = activeState.currentPage > 1,
                        modifier = Modifier.testTag("prev_page_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NavigateBefore,
                            contentDescription = "الصفحة السابقة",
                            tint = if (activeState.currentPage > 1) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }

                    // Current Page Index / Total (Click to Jump)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                            .clickable { showJumpDialog = true }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .testTag("page_indicator_badge"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${activeState.currentPage} / ${activeState.totalPages}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // Next Page
                    IconButton(
                        onClick = { runJs("PDFViewerApplication.pdfViewer.nextPage();") },
                        enabled = activeState.currentPage < activeState.totalPages,
                        modifier = Modifier.testTag("next_page_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.NavigateNext,
                            contentDescription = "الصفحة التالية",
                            tint = if (activeState.currentPage < activeState.totalPages) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    }

                    // Zoom In
                    IconButton(
                        onClick = {
                            onScaleChanged((activeState.currentScale + 0.25f).coerceAtMost(3.0f))
                            runJs("PDFViewerApplication.pdfViewer.currentScale = ${(activeState.currentScale + 0.25f).coerceAtMost(3.0f)};")
                        },
                        modifier = Modifier.testTag("zoom_in_button")
                    ) {
                        Icon(Icons.Default.ZoomIn, contentDescription = "تكبير", tint = MaterialTheme.colorScheme.primary)
                    }

                    // Reading View customization settings toggler
                    IconButton(
                        onClick = { showThemeMenu = true },
                        modifier = Modifier.testTag("customization_button")
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "تخصيص القراءة", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // Jump to Page Dialog
        if (showJumpDialog) {
            var inputPage by remember { mutableStateOf("") }
            Dialog(onDismissRequest = { showJumpDialog = false }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "الانتقال إلى صفحة",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = inputPage,
                            onValueChange = { inputPage = it.filter { char -> char.isDigit() } },
                            label = { Text("أدخل رقم الصفحة (1 - ${activeState.totalPages})") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showJumpDialog = false }) {
                                Text("إلغاء")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val target = inputPage.toIntOrNull()
                                    if (target != null && target in 1..activeState.totalPages) {
                                        runJs("PDFViewerApplication.pdfViewer.currentPageNumber = $target;")
                                        showJumpDialog = false
                                    } else {
                                        Toast.makeText(context, "رقم صفحة غير صالح", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text("انتقال")
                            }
                        }
                    }
                }
            }
        }

        // Customization bottom settings sheet popup
        if (showThemeMenu) {
            Dialog(onDismissRequest = { showThemeMenu = false }) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.fillMaxWidth(0.9f)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Text(
                            text = "تخصيص تجربة القراءة",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        // Reading themes selection
                        Text(
                            text = "مظهر القراءة (السمات):",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReaderTheme.values().forEach { theme ->
                                val isSelected = activeState.theme == theme
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            when (theme) {
                                                ReaderTheme.DAY -> Color(0xFFF0F0F0)
                                                ReaderTheme.NIGHT -> Color(0xFF262626)
                                                ReaderTheme.SEPIA -> Color(0xFFF4ECD8)
                                                ReaderTheme.EYE_CARE -> Color(0xFFE2F0D9)
                                            }
                                        )
                                        .clickable {
                                            onThemeChanged(theme)
                                        }
                                        .padding(4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = when (theme) {
                                            ReaderTheme.DAY -> "نهاري"
                                            ReaderTheme.NIGHT -> "ليلي"
                                            ReaderTheme.SEPIA -> "ورق دافئ"
                                            ReaderTheme.EYE_CARE -> "حماية العين"
                                        },
                                        color = when (theme) {
                                            ReaderTheme.NIGHT -> Color.White
                                            ReaderTheme.SEPIA -> Color(0xFF5B4636)
                                            ReaderTheme.EYE_CARE -> Color(0xFF2C4C1B)
                                            else -> Color.Black
                                        },
                                        fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                                        fontSize = 11.sp,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                    // Highlight indicator
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .border(
                                                    2.dp,
                                                    MaterialTheme.colorScheme.primary,
                                                    RoundedCornerShape(8.dp)
                                                )
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Layout/Scroll direction selection
                        Text(
                            text = "اتجاه التمرير وطريقة العرض:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ScrollDirection.values().forEach { direction ->
                                val isSelected = activeState.scrollDirection == direction
                                Button(
                                    onClick = { onScrollModeChanged(direction) },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                        contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = when (direction) {
                                            ScrollDirection.VERTICAL -> "رأسي"
                                            ScrollDirection.HORIZONTAL -> "أفقي"
                                            ScrollDirection.WRAPPED -> "تلقائي"
                                        },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { showThemeMenu = false },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("تمت التهيئة", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

package com.example

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream

data class PdfFile(
    val id: Long,
    val name: String,
    val path: String,
    val size: Long,
    val dateModified: Long
)

enum class ReaderTheme(val displayName: String, val filterCss: String) {
    DAY("Day", "none"),
    NIGHT("Night", "invert(1) hue-rotate(180deg)"),
    SEPIA("Sepia", "sepia(0.8) contrast(0.9)"),
    EYE_CARE("Eye-Care", "sepia(0.25) hue-rotate(60deg) saturate(0.8)")
}

enum class ScrollDirection(val value: Int, val displayName: String) {
    VERTICAL(0, "Vertical"),
    HORIZONTAL(1, "Horizontal"),
    WRAPPED(2, "Wrapped")
}

data class ActivePdfState(
    val uriPath: String = "",
    val name: String = "",
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val currentScale: Float = 1.0f,
    val theme: ReaderTheme = ReaderTheme.DAY,
    val scrollDirection: ScrollDirection = ScrollDirection.VERTICAL,
    val pdfSearchQuery: String = "",
    val isSample: Boolean = false
)

class PDFViewModel : ViewModel() {

    private val _scannedPdfs = MutableStateFlow<List<PdfFile>>(emptyList())
    val scannedPdfs: StateFlow<List<PdfFile>> = _scannedPdfs.asStateFlow()

    private val _recentPdfs = MutableStateFlow<List<PdfFile>>(emptyList())
    val recentPdfs: StateFlow<List<PdfFile>> = _recentPdfs.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _activePdf = MutableStateFlow<ActivePdfState?>(null)
    val activePdf: StateFlow<ActivePdfState?> = _activePdf.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun closeActivePdf() {
        _activePdf.value = null
    }

    fun updateCurrentPage(page: Int, total: Int) {
        _activePdf.value = _activePdf.value?.copy(currentPage = page, totalPages = total)
    }

    fun updateScale(scale: Float) {
        _activePdf.value = _activePdf.value?.copy(currentScale = scale)
    }

    fun updateTheme(theme: ReaderTheme) {
        _activePdf.value = _activePdf.value?.copy(theme = theme)
    }

    fun updateScrollDirection(direction: ScrollDirection) {
        _activePdf.value = _activePdf.value?.copy(scrollDirection = direction)
    }

    fun updatePdfSearchQuery(query: String) {
        _activePdf.value = _activePdf.value?.copy(pdfSearchQuery = query)
    }

    // Load recent PDFs from SharedPreferences
    fun loadRecents(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val sharedPreferences = context.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)
            val jsonString = sharedPreferences.getString("recent_pdfs", "[]") ?: "[]"
            val list = mutableListOf<PdfFile>()
            try {
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    list.add(
                        PdfFile(
                            id = obj.optLong("id", i.toLong()),
                            name = obj.optString("name", ""),
                            path = obj.optString("path", ""),
                            size = obj.optLong("size", 0L),
                            dateModified = obj.optLong("dateModified", 0L)
                        )
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            _recentPdfs.value = list
        }
    }

    // Add PDF to recently opened and save to SharedPreferences
    fun addToRecent(context: Context, pdfFile: PdfFile) {
        viewModelScope.launch(Dispatchers.IO) {
            val sharedPreferences = context.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)
            val currentList = _recentPdfs.value.toMutableList()
            
            // Remove if already exists, then add to front
            currentList.removeAll { it.path == pdfFile.path }
            currentList.add(0, pdfFile)
            
            // Keep at most 10 recent items
            val limitedList = currentList.take(10)
            
            val jsonArray = JSONArray()
            for (item in limitedList) {
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("name", item.name)
                obj.put("path", item.path)
                obj.put("size", item.size)
                obj.put("dateModified", item.dateModified)
                jsonArray.put(obj)
            }
            
            sharedPreferences.edit().putString("recent_pdfs", jsonArray.toString()).apply()
            _recentPdfs.value = limitedList
        }
    }

    // Open sample PDF
    fun openSamplePdf() {
        _activePdf.value = ActivePdfState(
            uriPath = "file:///android_asset/pdfjs/web/compressed.tracemonkey-pldi-09.pdf",
            name = "Sample_Document.pdf",
            isSample = true
        )
    }

    // Copy selected URI to cache and set as active PDF
    fun openPdfFromUri(context: Context, uri: Uri, customName: String? = null) {
        _isScanning.value = true
        viewModelScope.launch(Dispatchers.IO) {
            val fileName = customName ?: getFileNameFromUri(context, uri) ?: "Selected_Document.pdf"
            val copiedFile = copyUriToCache(context, uri, "temp_reader_view.pdf")
            if (copiedFile != null) {
                withContext(Dispatchers.Main) {
                    _activePdf.value = ActivePdfState(
                        uriPath = "file://" + copiedFile.absolutePath,
                        name = fileName,
                        isSample = false
                    )
                    // Add to recents
                    addToRecent(
                        context,
                        PdfFile(
                            id = System.currentTimeMillis(),
                            name = fileName,
                            path = copiedFile.absolutePath,
                            size = copiedFile.length(),
                            dateModified = System.currentTimeMillis()
                        )
                    )
                }
            }
            _isScanning.value = false
        }
    }

    // Helper to extract file name from URI
    private fun getFileNameFromUri(context: Context, uri: Uri): String? {
        var name: String? = null
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        }
        if (name == null) {
            val path = uri.path
            val cut = path?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                name = path?.substring(cut + 1)
            }
        }
        return name
    }

    // Helper to copy file
    private fun copyUriToCache(context: Context, uri: Uri, targetFileName: String): File? {
        return try {
            val cacheFile = File(context.cacheDir, targetFileName)
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                FileOutputStream(cacheFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                }
            }
            cacheFile
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    // Clear recent files list
    fun clearRecents(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val sharedPreferences = context.getSharedPreferences("pdf_reader_prefs", Context.MODE_PRIVATE)
            sharedPreferences.edit().remove("recent_pdfs").apply()
            _recentPdfs.value = emptyList()
        }
    }

    // Scan the device for PDF files
    fun scanDevicePdfFiles(context: Context) {
        if (_isScanning.value) return
        _isScanning.value = true
        
        viewModelScope.launch(Dispatchers.IO) {
            val pdfList = mutableListOf<PdfFile>()
            
            // 1. Scan via MediaStore
            val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Files.getContentUri("external")
            }

            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED
            )

            val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR ${MediaStore.Files.FileColumns.DATA} LIKE ?"
            val selectionArgs = arrayOf("application/pdf", "%.pdf")
            val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

            try {
                context.contentResolver.query(
                    collection,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                    val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                    val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idCol)
                        val name = cursor.getString(nameCol) ?: "Document.pdf"
                        val path = cursor.getString(pathCol) ?: ""
                        val size = cursor.getLong(sizeCol)
                        val date = cursor.getLong(dateCol)

                        val file = File(path)
                        if (file.exists() && file.isFile) {
                            pdfList.add(
                                PdfFile(
                                    id = id,
                                    name = name,
                                    path = path,
                                    size = size,
                                    dateModified = date * 1000
                                )
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // 2. Scan standard folders (Download, Documents) as a fallback/enhancement
            val commonDirs = listOf(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                context.getExternalFilesDir(null)
            )

            for (dir in commonDirs) {
                if (dir != null && dir.exists() && dir.isDirectory) {
                    scanDirectoryForPdfs(dir, pdfList)
                }
            }

            // Remove duplicates by file path
            val uniqueList = pdfList.distinctBy { it.path }.sortedByDescending { it.dateModified }

            withContext(Dispatchers.Main) {
                _scannedPdfs.value = uniqueList
                _isScanning.value = false
            }
        }
    }

    private fun scanDirectoryForPdfs(dir: File, list: MutableList<PdfFile>) {
        val files = dir.listFiles() ?: return
        for (file in files) {
            if (file.isDirectory) {
                // Avoid scanning system or deep nested app directories to keep it fast
                if (!file.name.startsWith(".") && file.name != "Android") {
                    scanDirectoryForPdfs(file, list)
                }
            } else if (file.isFile && file.name.endsWith(".pdf", ignoreCase = true)) {
                list.add(
                    PdfFile(
                        id = file.hashCode().toLong(),
                        name = file.name,
                        path = file.absolutePath,
                        size = file.length(),
                        dateModified = file.lastModified()
                    )
                )
            }
        }
    }
}

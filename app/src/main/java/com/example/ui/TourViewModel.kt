package com.example.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.api.GeminiClient
import com.example.api.TourEntryOcrResult
import com.example.data.EmployeeProfile
import com.example.data.TourDatabase
import com.example.data.TourEntry
import com.example.data.CaseEntry
import com.example.data.TourRepository
import com.example.utils.PdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class TourViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TourRepository

    // Current profile
    val profile: StateFlow<EmployeeProfile>
    
    // List of months that have tour entries
    val availableMonths: StateFlow<List<String>>

    // Live map of month Year string to tour entry counts
    val entriesCountByMonth: StateFlow<Map<String, Int>>

    // Selected Month-Year for filter (default e.g. "2026-05")
    private val _selectedMonth = MutableStateFlow("")
    val selectedMonth: StateFlow<String> = _selectedMonth.asStateFlow()

    // Flow of tour entries filtered by the selected month
    val filteredEntries: StateFlow<List<TourEntry>>

    // Case Entry States
    private val _caseSearchQuery = MutableStateFlow("")
    val caseSearchQuery: StateFlow<String> = _caseSearchQuery.asStateFlow()

    private val _caseFilterPS = MutableStateFlow("")
    val caseFilterPS: StateFlow<String> = _caseFilterPS.asStateFlow()

    private val _caseFilterDistrict = MutableStateFlow("")
    val caseFilterDistrict: StateFlow<String> = _caseFilterDistrict.asStateFlow()

    private val _caseFilterDate = MutableStateFlow("")
    val caseFilterDate: StateFlow<String> = _caseFilterDate.asStateFlow()

    private val _caseFilterTag = MutableStateFlow("")
    val caseFilterTag: StateFlow<String> = _caseFilterTag.asStateFlow()

    val filteredCases: StateFlow<List<CaseEntry>>

    // Case Register PDF OCR scanning status
    private val _caseOcrLoading = MutableStateFlow(false)
    val caseOcrLoading: StateFlow<Boolean> = _caseOcrLoading.asStateFlow()

    private val _caseOcrError = MutableStateFlow<String?>(null)
    val caseOcrError: StateFlow<String?> = _caseOcrError.asStateFlow()

    private val _caseOcrResult = MutableStateFlow<com.example.api.CaseEntryOcrResult?>(null)
    val caseOcrResult: StateFlow<com.example.api.CaseEntryOcrResult?> = _caseOcrResult.asStateFlow()

    // OCR scanning status
    private val _ocrLoading = MutableStateFlow(false)
    val ocrLoading: StateFlow<Boolean> = _ocrLoading.asStateFlow()

    private val _ocrError = MutableStateFlow<String?>(null)
    val ocrError: StateFlow<String?> = _ocrError.asStateFlow()

    private val _ocrResult = MutableStateFlow<TourEntryOcrResult?>(null)
    val ocrResult: StateFlow<TourEntryOcrResult?> = _ocrResult.asStateFlow()

    // Multi-File Upload Queue State
    private val _uploadItems = MutableStateFlow<List<UploadItem>>(emptyList())
    val uploadItems: StateFlow<List<UploadItem>> = _uploadItems.asStateFlow()

    fun updateUploadItem(updated: UploadItem) {
        _uploadItems.value = _uploadItems.value.map {
            if (it.id == updated.id) updated else it
        }
    }

    fun removeUploadItem(id: String) {
        _uploadItems.value = _uploadItems.value.filter { it.id != id }
    }

    fun clearUploadQueue() {
        _uploadItems.value = emptyList()
    }

    fun injectMockUploadItem() {
        val mock = UploadItem(
            fileName = "Sikar_CS_129_Letter_Sample.png",
            isLoading = false,
            date = "2025-12-12",
            depTime = "09:10",
            arrTime = "14:30",
            travelMode = "MT Vehicle (Govt.)",
            distance = "30.0",
            csNumber = "Sikar CS 129/25",
            firNumber = "293/25",
            policeStation = "Dhod",
            district = "Sikar",
            reportDate = "2025-12-12",
            remarks = "Scene visit at PS Dhod, crime scene reference CS-129/25. Checked for physical and dynamic clues."
        )
        _uploadItems.value = _uploadItems.value + mock
    }

    fun processMultipleUris(context: Context, uris: List<Uri>) {
        val resolver = context.contentResolver
        val newItems = uris.map { uri ->
            val fName = getFileName(context, uri)
            UploadItem(
                fileName = fName,
                isLoading = true
            )
        }
        
        // Append new loading items to current queue
        val currentList = _uploadItems.value.toMutableList()
        val idsToProcess = newItems.map { it.id }
        currentList.addAll(newItems)
        _uploadItems.value = currentList

        // Launch parsing for each uri in parallel
        uris.forEachIndexed { index, uri ->
            val targetId = newItems[index].id
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val mimeType = resolver.getType(uri) ?: ""
                    val isPdf = mimeType.equals("application/pdf", ignoreCase = true) || 
                                getFileName(context, uri).endsWith(".pdf", ignoreCase = true)

                    val bitmap: Bitmap? = if (isPdf) {
                        renderPdfPageToBitmap(context, uri)
                    } else {
                        uriToBitmap(context, uri)
                    }

                    if (bitmap == null) {
                        updateUploadItemById(targetId) {
                            it.copy(isLoading = false, error = "Failed to render or read file.")
                        }
                        return@launch
                    }

                    val base64 = bitmapToBase64(bitmap)
                    val ocrParsedResult = GeminiClient.parseDocument(base64, "image/jpeg")
                    if (ocrParsedResult != null) {
                        updateUploadItemById(targetId) {
                            it.copy(
                                isLoading = false,
                                date = ocrParsedResult.date ?: "",
                                depTime = ocrParsedResult.depTime ?: "",
                                arrTime = ocrParsedResult.arrTime ?: "",
                                travelMode = ocrParsedResult.travelMode ?: "MT Vehicle",
                                distance = ocrParsedResult.distance?.toString() ?: "0.0",
                                csNumber = ocrParsedResult.csNumber ?: "",
                                firNumber = ocrParsedResult.firNumber ?: "",
                                policeStation = ocrParsedResult.policeStation ?: "",
                                district = ocrParsedResult.district ?: "",
                                reportDate = ocrParsedResult.reportDate ?: ""
                            )
                        }
                    } else {
                        updateUploadItemById(targetId) {
                            it.copy(isLoading = false, error = "Gemini AI returned empty results. Please log manually.")
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    updateUploadItemById(targetId) {
                        it.copy(isLoading = false, error = "Extraction error: ${e.message}")
                    }
                }
            }
        }
    }

    private inline fun updateUploadItemById(id: String, crossinline transform: (UploadItem) -> UploadItem) {
        _uploadItems.value = _uploadItems.value.map {
            if (it.id == id) transform(it) else it
        }
    }

    fun saveUploadItemToDiary(id: String, callback: (Boolean) -> Unit) {
        val item = _uploadItems.value.find { it.id == id }
        if (item == null) {
            callback(false)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val distDouble = item.distance.toDoubleOrNull() ?: 0.0
            val repDate = item.reportDate.ifBlank { item.date }
            val monthYear = if (item.date.length >= 7) item.date.substring(0, 7) else "unknown"
            
            val entry = TourEntry(
                date = item.date,
                depTime = item.depTime,
                arrTime = item.arrTime,
                travelMode = item.travelMode,
                distance = distDouble,
                csNumber = item.csNumber,
                firNumber = item.firNumber,
                policeStation = item.policeStation,
                district = item.district,
                reportDate = repDate,
                remarks = item.remarks,
                monthYear = monthYear,
                arrDate = if (item.arrDate.isNotBlank()) item.arrDate else item.date
            )
            repository.saveTourEntry(entry)
            
            // Auto select month so it becomes visible
            if (monthYear != "unknown") {
                _selectedMonth.value = monthYear
            }

            // Remove from queue
            withContext(Dispatchers.Main) {
                removeUploadItem(id)
                callback(true)
            }
        }
    }

    init {
        val database = TourDatabase.getDatabase(application)
        repository = TourRepository(database.tourDao())

        // Initialize selected month to current date's Month (YYYY-MM)
        val sDate = SimpleDateFormat("yyyy-MM", Locale.getDefault()).format(Date())
        _selectedMonth.value = sDate

        // Load profile with sensible defaults if empty
        profile = repository.profile
            .map { it ?: EmployeeProfile(name = "", designation = "", posting = "") }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EmployeeProfile())

        // Load distinct list of available months (merged with current month so it always exists)
        availableMonths = repository.availableMonths
            .map { list ->
                val current = sDate
                if (!list.contains(current)) {
                    (list + current).sortedDescending()
                } else {
                    list
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(sDate))

        // Compute count of entries per month
        entriesCountByMonth = repository.allTourEntries
            .map { list ->
                list.groupBy { it.monthYear }
                    .mapValues { it.value.size }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

        // Collect matching entries when month selection changes
        filteredEntries = _selectedMonth
            .flatMapLatest { month ->
                repository.getTourEntriesForMonth(month).map { list ->
                    list.sortedWith(compareBy<TourEntry> { it.date }.thenBy { it.depTime })
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // Collect and filter Case Entries
        filteredCases = combine(
            repository.allCaseEntries,
            _caseSearchQuery,
            _caseFilterPS,
            _caseFilterDistrict,
            _caseFilterDate,
            _caseFilterTag
        ) { array ->
            @Suppress("UNCHECKED_CAST")
            val cases = array[0] as List<CaseEntry>
            val query = array[1] as String
            val ps = array[2] as String
            val district = array[3] as String
            val date = array[4] as String
            val tag = array[5] as String

            cases.filter { case ->
                val matchesQuery = query.isBlank() || 
                    case.caseNumber.contains(query, ignoreCase = true) ||
                    case.firNumber.contains(query, ignoreCase = true) ||
                    case.policeStation.contains(query, ignoreCase = true) ||
                    case.district.contains(query, ignoreCase = true) ||
                    case.notes.contains(query, ignoreCase = true) ||
                    case.investigatingOfficer.contains(query, ignoreCase = true) ||
                    case.tags.contains(query, ignoreCase = true)

                val matchesPS = ps.isBlank() || case.policeStation.contains(ps, ignoreCase = true)
                val matchesDistrict = district.isBlank() || case.district.contains(district, ignoreCase = true)
                val matchesDate = date.isBlank() || case.date == date
                
                val matchesTag = tag.isBlank() || case.tags.split(",")
                    .map { it.trim().lowercase() }
                    .any { it.contains(tag.trim().lowercase()) }

                matchesQuery && matchesPS && matchesDistrict && matchesDate && matchesTag
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun selectMonth(monthYear: String) {
        _selectedMonth.value = monthYear
    }

    fun selectPreviousMonth() {
        val current = _selectedMonth.value
        val parts = current.split("-")
        if (parts.size == 2) {
            val year = parts[0].toIntOrNull() ?: 2026
            val month = parts[1].toIntOrNull() ?: 5
            var prevMonth = month - 1
            var prevYear = year
            if (prevMonth < 1) {
                prevMonth = 12
                prevYear -= 1
            }
            val formatted = String.format(Locale.getDefault(), "%04d-%02d", prevYear, prevMonth)
            _selectedMonth.value = formatted
        }
    }

    fun selectNextMonth() {
        val current = _selectedMonth.value
        val parts = current.split("-")
        if (parts.size == 2) {
            val year = parts[0].toIntOrNull() ?: 2026
            val month = parts[1].toIntOrNull() ?: 5
            var nextMonth = month + 1
            var nextYear = year
            if (nextMonth > 12) {
                nextMonth = 1
                nextYear += 1
            }
            val formatted = String.format(Locale.getDefault(), "%04d-%02d", nextYear, nextMonth)
            _selectedMonth.value = formatted
        }
    }

    fun saveProfile(name: String, designation: String, posting: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.saveProfile(EmployeeProfile(id = 1, name = name, designation = designation, posting = posting))
        }
    }

    fun saveTourEntry(
        date: String,
        depTime: String,
        arrTime: String,
        travelMode: String,
        distance: Double,
        csNumber: String,
        firNumber: String,
        policeStation: String,
        district: String,
        reportDate: String,
        remarks: String = "",
        arrDate: String = ""
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            // derive monthYear for filtering
            val monthYear = if (date.length >= 7) date.substring(0, 7) else "unknown"
            val entry = TourEntry(
                date = date,
                depTime = depTime,
                arrTime = arrTime,
                travelMode = travelMode,
                distance = distance,
                csNumber = csNumber,
                firNumber = firNumber,
                policeStation = policeStation,
                district = district,
                reportDate = reportDate,
                remarks = remarks,
                monthYear = monthYear,
                arrDate = if (arrDate.isNotBlank()) arrDate else date
            )
            repository.saveTourEntry(entry)

            // Auto select the month of the added entry so it is visible in the list!
            if (monthYear != "unknown") {
                _selectedMonth.value = monthYear
            }
        }
    }

    fun updateTourEntry(entry: TourEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            val monthYear = if (entry.date.length >= 7) entry.date.substring(0, 7) else "unknown"
            val normalizedEntry = if (entry.monthYear != monthYear) {
                entry.copy(monthYear = monthYear)
            } else {
                entry
            }
            repository.saveTourEntry(normalizedEntry)
        }
    }

    fun deleteTourEntry(entry: TourEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteTourEntry(entry)
        }
    }

    // Case Entry DB Actions
    fun saveCaseEntry(
        id: Long = 0,
        caseNumber: String,
        firNumber: String,
        policeStation: String,
        district: String,
        date: String,
        tags: String,
        notes: String,
        investigatingOfficer: String,
        status: String
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val case = CaseEntry(
                id = id,
                caseNumber = caseNumber,
                firNumber = firNumber,
                policeStation = policeStation,
                district = district,
                date = date,
                tags = tags,
                notes = notes,
                investigatingOfficer = investigatingOfficer,
                status = status
            )
            repository.saveCaseEntry(case)
        }
    }

    fun deleteCaseEntry(case: CaseEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.deleteCaseEntry(case)
        }
    }

    fun setCaseSearchQuery(query: String) {
        _caseSearchQuery.value = query
    }

    fun setCaseFilterPS(ps: String) {
        _caseFilterPS.value = ps
    }

    fun setCaseFilterDistrict(district: String) {
        _caseFilterDistrict.value = district
    }

    fun setCaseFilterDate(date: String) {
        _caseFilterDate.value = date
    }

    fun setCaseFilterTag(tag: String) {
        _caseFilterTag.value = tag
    }

    fun resetFilters() {
        _caseSearchQuery.value = ""
        _caseFilterPS.value = ""
        _caseFilterDistrict.value = ""
        _caseFilterDate.value = ""
        _caseFilterTag.value = ""
    }

    fun clearOcrState() {
        _ocrResult.value = null
        _ocrError.value = null
    }

    fun clearCaseOcrState() {
        _caseOcrResult.value = null
        _caseOcrError.value = null
    }

    /**
     * Parse Case/Crime Scene Register Document at Uri (Image or PDF)
     */
    fun parseCaseDocument(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _caseOcrLoading.value = true
            _caseOcrError.value = null
            _caseOcrResult.value = null

            try {
                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(uri) ?: ""
                val isPdf = mimeType.equals("application/pdf", ignoreCase = true) || 
                            getFileName(context, uri).endsWith(".pdf", ignoreCase = true)

                val bitmap: Bitmap? = if (isPdf) {
                    renderPdfPageToBitmap(context, uri)
                } else {
                    uriToBitmap(context, uri)
                }

                if (bitmap == null) {
                    _caseOcrError.value = "Failed to load or render your register file. Try another format or image."
                    _caseOcrLoading.value = false
                    return@launch
                }

                val base64 = bitmapToBase64(bitmap)
                val caseOcrParsedResult = GeminiClient.parseCrimeSceneRegister(base64, "image/jpeg")
                if (caseOcrParsedResult != null) {
                    _caseOcrResult.value = caseOcrParsedResult
                } else {
                    _caseOcrError.value = "Failed to extract case details. Ensure the register scan is clear and contains readable text."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _caseOcrError.value = "Register Parsing Error: ${e.message}"
            } finally {
                _caseOcrLoading.value = false
            }
        }
    }

    /**
     * Parse handwritten drawing/scribble and post resulting transcript to note field
     */
    fun transcribeScribbleDrawing(bitmap: Bitmap, onComplete: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val base64 = bitmapToBase64(bitmap)
                val transcript = GeminiClient.transcribeScribbleDrawing(base64)
                if (transcript != null) {
                    withContext(Dispatchers.Main) {
                        onComplete(transcript)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * Parse document at Uri (could be Image or PDF)
     */
    fun parseDocument(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _ocrLoading.value = true
            _ocrError.value = null
            _ocrResult.value = null

            try {
                // Determine file type
                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(uri) ?: ""
                val isPdf = mimeType.equals("application/pdf", ignoreCase = true) || 
                            getFileName(context, uri).endsWith(".pdf", ignoreCase = true)

                // Render or extract bitmap
                val bitmap: Bitmap? = if (isPdf) {
                    renderPdfPageToBitmap(context, uri)
                } else {
                    uriToBitmap(context, uri)
                }

                if (bitmap == null) {
                    _ocrError.value = "Failed to load/render selected file. Please verify it works."
                    _ocrLoading.value = false
                    return@launch
                }

                // Compress and encode base64
                val base64 = bitmapToBase64(bitmap)
                
                // Call Gemini OCR directly
                val ocrParsedResult = GeminiClient.parseDocument(base64, "image/jpeg")
                if (ocrParsedResult != null) {
                    _ocrResult.value = ocrParsedResult
                } else {
                    _ocrError.value = "Failed to extract fields. Hand-written log books or low contrast files might need manual logging."
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _ocrError.value = "OCR Parsing Error: ${e.message}"
            } finally {
                _ocrLoading.value = false
            }
        }
    }

    /**
     * Generation Wrapper for UI
     */
    fun createMonthlyDiaryPdf(context: Context, month: String, isLegalSize: Boolean): File? {
        val currentEntries = filteredEntries.value
        val currentProfile = profile.value
        return PdfGenerator.generateMonthlyDiary(
            context = context,
            profile = currentProfile,
            entries = currentEntries,
            monthYear = month,
            isLegalSize = isLegalSize
        )
    }

    // --- Private Helper Utilities ---

    private fun uriToBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val contentResolver = context.contentResolver
            
            // First decode with inJustDecodeBounds=true to check dimensions
            val options = android.graphics.BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            contentResolver.openInputStream(uri)?.use { input ->
                android.graphics.BitmapFactory.decodeStream(input, null, options)
            }
            
            // Calculate inSampleSize to resize the image to a max dimension of 1600
            val maxDim = 1600
            var sampleSize = 1
            if (options.outHeight > maxDim || options.outWidth > maxDim) {
                val halfHeight = options.outHeight / 2
                val halfWidth = options.outWidth / 2
                while ((halfHeight / sampleSize) >= maxDim && (halfWidth / sampleSize) >= maxDim) {
                    sampleSize *= 2
                }
            }
            
            // Decode bitmap with inSampleSize set
            val decodeOptions = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            
            contentResolver.openInputStream(uri)?.use { input ->
                android.graphics.BitmapFactory.decodeStream(input, null, decodeOptions)
            }
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    private fun renderPdfPageToBitmap(context: Context, uri: Uri): Bitmap? {
        return try {
            val parcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            val pdfRenderer = PdfRenderer(parcelFileDescriptor)
            if (pdfRenderer.pageCount == 0) {
                pdfRenderer.close()
                parcelFileDescriptor.close()
                return null
            }
            val page = pdfRenderer.openPage(0)
            
            // Limit PDF render size to avoid massive bitmap allocation
            val maxPdfDim = 1600f
            val pageWidth = page.width
            val pageHeight = page.height
            
            val scale = if (pageWidth > maxPdfDim || pageHeight > maxPdfDim) {
                val scaleW = maxPdfDim / pageWidth
                val scaleH = maxPdfDim / pageHeight
                kotlin.math.min(scaleW, scaleH)
            } else {
                2.0f // standard high quality scale
            }
            
            val w = (pageWidth * scale).toInt().coerceAtLeast(1)
            val h = (pageHeight * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()
            pdfRenderer.close()
            parcelFileDescriptor.close()
            bitmap
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 75, outputStream)
        return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "file.bin"
    }
}

data class UploadItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val fileName: String,
    val isLoading: Boolean = false,
    val error: String? = null,
    val date: String = "",
    val depTime: String = "",
    val arrTime: String = "",
    val travelMode: String = "MT Vehicle",
    val distance: String = "0.0",
    val csNumber: String = "",
    val firNumber: String = "",
    val policeStation: String = "",
    val district: String = "",
    val reportDate: String = "",
    val remarks: String = "",
    val arrDate: String = ""
)

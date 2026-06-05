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
import com.example.utils.WordGenerator
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

    // Flow of all tour entries in database
    val allTourEntries: StateFlow<List<TourEntry>>

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
            val fName = newItems[index].fileName
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val mimeType = resolver.getType(uri) ?: ""
                    val isPdf = mimeType.equals("application/pdf", ignoreCase = true) || 
                                getFileName(context, uri).endsWith(".pdf", ignoreCase = true)
                    val isDocx = fName.endsWith(".docx", ignoreCase = true)

                    val userProfile = profile.value
                    val userKey = userProfile.geminiApiKey.ifBlank { null }

                    val ocrParsedResult = if (isDocx) {
                        val text = extractTextFromDocx(context, uri)
                        if (text.isBlank()) {
                            null
                        } else {
                            if (isOnline(context)) {
                                try {
                                    GeminiClient.parseTextDocument(text, userKey)
                                } catch (e: Exception) {
                                    null
                                }
                            } else {
                                null
                            }
                        }
                    } else {
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
                        if (isOnline(context)) {
                            try {
                                GeminiClient.parseDocument(base64, "image/jpeg", userKey)
                            } catch (e: Exception) {
                                val single = com.example.api.OfflineOcrEngine.parseFromFilename(fName)
                                com.example.api.MultipleTourEntriesOcrResult(entries = listOf(single))
                            }
                        } else {
                            val single = com.example.api.OfflineOcrEngine.parseFromFilename(fName)
                            com.example.api.MultipleTourEntriesOcrResult(entries = listOf(single))
                        }
                    }

                    if (ocrParsedResult != null && ocrParsedResult.entries.isNotEmpty()) {
                        val entries = ocrParsedResult.entries
                        val finishedItems = entries.mapIndexed { idx, resVal ->
                            UploadItem(
                                fileName = if (entries.size > 1) "$fName [Day ${idx + 1}]" else fName,
                                isLoading = false,
                                date = resVal.date ?: "",
                                depTime = resVal.depTime ?: "",
                                arrTime = resVal.arrTime ?: "",
                                travelMode = resVal.travelMode ?: "MT Vehicle",
                                distance = resVal.distance?.toString() ?: "0.0",
                                csNumber = resVal.csNumber ?: "",
                                firNumber = resVal.firNumber ?: "",
                                policeStation = resVal.policeStation ?: "",
                                district = resVal.district ?: "",
                                reportDate = resVal.reportDate ?: ""
                            )
                        }

                        _uploadItems.value = _uploadItems.value.flatMap { item ->
                            if (item.id == targetId) {
                                finishedItems
                            } else {
                                listOf(item)
                            }
                        }
                    } else {
                        updateUploadItemById(targetId) {
                            it.copy(isLoading = false, error = if (isDocx && !isOnline(context)) "Word processing requires active internet." else "Gemini AI returned empty results. Please log manually.")
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

    private fun isOnline(context: Context): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val capabilities = cm?.getNetworkCapabilities(cm.activeNetwork)
            capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        } catch (e: Exception) {
            false
        }
    }

    private inline fun updateUploadItemById(id: String, crossinline transform: (UploadItem) -> UploadItem) {
        _uploadItems.value = _uploadItems.value.map {
            if (it.id == id) transform(it) else it
        }
    }

    private fun timeToMinutes(timeStr: String): Int {
        return try {
            val parts = timeStr.split(":")
            val hrs = parts[0].trim().toInt()
            val mins = parts[1].trim().toInt()
            hrs * 60 + mins
        } catch (e: Exception) {
            0
        }
    }

    private fun minutesToTime(minutes: Int): String {
        val mins = minutes % (24 * 60)
        val positiveMins = if (mins < 0) mins + (24 * 60) else mins
        val hrs = positiveMins / 60
        val m = positiveMins % 60
        return String.format(Locale.getDefault(), "%02d:%02d", hrs, m)
    }

    fun saveRajasthanRoundTripEntry(
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
            val depMin = timeToMinutes(depTime)
            val arrMin = timeToMinutes(arrTime)
            
            // Speed of travel is 40 km/h -> duration of segment in minutes is (distance / 40.0) * 60
            val travelDurationMin = if (distance > 0) {
                ((distance / 40.0) * 60.0).toInt()
            } else {
                30
            }
            
            val outwardArrMin = depMin + travelDurationMin
            val returnDepMin = arrMin - travelDurationMin
            
            val outwardArrTime = minutesToTime(outwardArrMin)
            val returnDepTime = minutesToTime(returnDepMin)
            val monthYear = if (date.length >= 7) date.substring(0, 7) else "unknown"
            
            // Outward segment
            val outwardEntry = TourEntry(
                date = date,
                depTime = depTime,
                arrTime = outwardArrTime,
                travelMode = travelMode,
                distance = distance,
                csNumber = csNumber,
                firNumber = firNumber,
                policeStation = policeStation,
                district = district,
                reportDate = reportDate,
                remarks = remarks.ifBlank { "Outward leg to PS $policeStation (40 km/h)" },
                monthYear = monthYear,
                arrDate = date
            )
            
            // Return segment
            val returnEntry = TourEntry(
                date = arrDate.ifBlank { date },
                depTime = returnDepTime,
                arrTime = arrTime,
                travelMode = travelMode,
                distance = distance,
                csNumber = csNumber,
                firNumber = firNumber,
                policeStation = "HQ / Sharing Station",
                district = district,
                reportDate = reportDate,
                remarks = remarks.ifBlank { "Return leg from PS $policeStation (40 km/h)" },
                monthYear = monthYear,
                arrDate = arrDate.ifBlank { date }
            )
            
            repository.saveTourEntry(outwardEntry)
            repository.saveTourEntry(returnEntry)
            
            if (monthYear != "unknown") {
                withContext(Dispatchers.Main) {
                    _selectedMonth.value = monthYear
                }
            }
        }
    }

    fun saveUploadItemToDiary(id: String, isRoundTrip: Boolean = false, callback: (Boolean) -> Unit) {
        val item = _uploadItems.value.find { it.id == id }
        if (item == null) {
            callback(false)
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            val distDouble = item.distance.toDoubleOrNull() ?: 0.0
            val repDate = item.reportDate.ifBlank { item.date }
            val monthYear = if (item.date.length >= 7) item.date.substring(0, 7) else "unknown"
            
            if (isRoundTrip) {
                val depMin = timeToMinutes(item.depTime)
                val arrMin = timeToMinutes(item.arrTime)
                val travelDurationMin = if (distDouble > 0) {
                    ((distDouble / 40.0) * 60.0).toInt()
                } else {
                    30
                }
                
                val outwardArrMin = depMin + travelDurationMin
                val returnDepMin = arrMin - travelDurationMin
                
                val outwardArrTime = minutesToTime(outwardArrMin)
                val returnDepTime = minutesToTime(returnDepMin)
                
                val outwardEntry = TourEntry(
                    date = item.date,
                    depTime = item.depTime,
                    arrTime = outwardArrTime,
                    travelMode = item.travelMode,
                    distance = distDouble,
                    csNumber = item.csNumber,
                    firNumber = item.firNumber,
                    policeStation = item.policeStation,
                    district = item.district,
                    reportDate = repDate,
                    remarks = item.remarks.ifBlank { "Outward leg to PS ${item.policeStation} (Auto 40 km/h)" },
                    monthYear = monthYear,
                    arrDate = item.date
                )
                
                val returnEntry = TourEntry(
                    date = item.arrDate.ifBlank { item.date },
                    depTime = returnDepTime,
                    arrTime = item.arrTime,
                    travelMode = item.travelMode,
                    distance = distDouble,
                    csNumber = item.csNumber,
                    firNumber = item.firNumber,
                    policeStation = "HQ / Sharing Station",
                    district = item.district,
                    reportDate = repDate,
                    remarks = item.remarks.ifBlank { "Return leg from PS ${item.policeStation} (Auto 40 km/h)" },
                    monthYear = monthYear,
                    arrDate = item.arrDate.ifBlank { item.date }
                )
                repository.saveTourEntry(outwardEntry)
                repository.saveTourEntry(returnEntry)
            } else {
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
            }
            
            // Auto select month so it becomes visible
            if (monthYear != "unknown") {
                withContext(Dispatchers.Main) {
                    _selectedMonth.value = monthYear
                }
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

        // Initialize allTourEntries flow sorted by date then time descending
        allTourEntries = repository.allTourEntries
            .map { list ->
                list.sortedWith(compareByDescending<TourEntry> { it.date }.thenByDescending { it.depTime })
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

        restoreUploadItemsFromPrefs()

        viewModelScope.launch(Dispatchers.IO) {
            _uploadItems.collect { items ->
                saveUploadItemsToPrefs(items)
            }
        }
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

    fun saveProfile(name: String, designation: String, posting: String, geminiApiKey: String? = null, cloudBackupEmail: String? = null, basicSalary: Int = 38000, taCategory: String = "D") {
        viewModelScope.launch(Dispatchers.IO) {
            val current = profile.value
            val finalKey = geminiApiKey ?: current.geminiApiKey
            val finalEmail = cloudBackupEmail ?: current.cloudBackupEmail
            repository.saveProfile(
                EmployeeProfile(
                    id = 1,
                    name = name,
                    designation = designation,
                    posting = posting,
                    geminiApiKey = finalKey,
                    cloudBackupEmail = finalEmail,
                    basicSalary = basicSalary,
                    taCategory = taCategory
                )
            )
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

    fun saveTourEntry(entry: TourEntry) {
        viewModelScope.launch(Dispatchers.IO) {
            val monthYear = if (entry.date.length >= 7) entry.date.substring(0, 7) else "unknown"
            val normalizedEntry = if (entry.monthYear != monthYear) {
                entry.copy(monthYear = monthYear)
            } else {
                entry
            }
            repository.saveTourEntry(normalizedEntry)
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
                val fName = getFileName(context, uri)
                val isPdf = mimeType.equals("application/pdf", ignoreCase = true) || 
                            fName.endsWith(".pdf", ignoreCase = true)

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
                val userProfile = profile.value
                val userKey = userProfile.geminiApiKey.ifBlank { null }

                val caseOcrParsedResult = if (isOnline(context)) {
                    try {
                        GeminiClient.parseCrimeSceneRegister(base64, "image/jpeg", userKey)
                    } catch (e: Exception) {
                        com.example.api.OfflineOcrEngine.parseCaseFromFilename(fName)
                    }
                } else {
                    com.example.api.OfflineOcrEngine.parseCaseFromFilename(fName)
                }

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
                val userProfile = profile.value
                val userKey = userProfile.geminiApiKey.ifBlank { null }
                
                val transcript = if (isOnline(getApplication())) {
                    GeminiClient.transcribeScribbleDrawing(base64, userKey)
                } else {
                    "Note: Cannot transcribe drawing offline. Ensure you are connected to the internet and click again."
                }
                
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
     * Parse document at Uri (could be Image, PDF or Word)
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
                val fName = getFileName(context, uri)
                val isPdf = mimeType.equals("application/pdf", ignoreCase = true) || 
                            fName.endsWith(".pdf", ignoreCase = true)
                val isDocx = fName.endsWith(".docx", ignoreCase = true)

                val userProfile = profile.value
                val userKey = userProfile.geminiApiKey.ifBlank { null }
                
                val ocrParsedResult = if (isDocx) {
                    val text = extractTextFromDocx(context, uri)
                    if (text.isBlank()) {
                        null
                    } else if (isOnline(context)) {
                        GeminiClient.parseTextDocument(text, userKey)
                    } else {
                        null
                    }
                } else {
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
                    if (isOnline(context)) {
                        try {
                            GeminiClient.parseDocument(base64, "image/jpeg", userKey)
                        } catch (e: Exception) {
                            val single = com.example.api.OfflineOcrEngine.parseFromFilename(fName)
                            com.example.api.MultipleTourEntriesOcrResult(entries = listOf(single))
                        }
                    } else {
                        val single = com.example.api.OfflineOcrEngine.parseFromFilename(fName)
                        com.example.api.MultipleTourEntriesOcrResult(entries = listOf(single))
                    }
                }

                if (ocrParsedResult != null && ocrParsedResult.entries.isNotEmpty()) {
                    _ocrResult.value = ocrParsedResult.entries.first()
                } else {
                    _ocrError.value = if (isDocx && !isOnline(context)) "Word parsing requires active internet." else "Failed to extract fields. Hand-written log books or low contrast files might need manual logging."
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
        val currentEntries = filteredEntries.value.filter { it.distance >= 29.0 }
        val currentProfile = profile.value
        return PdfGenerator.generateMonthlyDiary(
            context = context,
            profile = currentProfile,
            entries = currentEntries,
            monthYear = month,
            isLegalSize = isLegalSize
        )
    }

    /**
     * Rajasthan TA Bill PDF Generation Wrapper
     */
    fun createRajasthanTaBillPdf(context: Context, month: String, isLegalSize: Boolean): File? {
        val currentEntries = filteredEntries.value
        val currentProfile = profile.value
        return PdfGenerator.generateTaBillPdf(
            context = context,
            profile = currentProfile,
            entries = currentEntries,
            monthYear = month,
            isLegalSize = isLegalSize
        )
    }

    /**
     * Generation Wrapper for UI - Word Format (.doc)
     */
    fun createMonthlyDiaryDoc(context: Context, month: String): File? {
        val currentEntries = filteredEntries.value.filter { it.distance >= 29.0 }
        val currentProfile = profile.value
        return WordGenerator.generateMonthlyDiaryDoc(
            context = context,
            profile = currentProfile,
            entries = currentEntries,
            monthYear = month
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

    private fun extractTextFromDocx(context: Context, uri: Uri): String {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri) ?: return ""
            val zipInputStream = java.util.zip.ZipInputStream(inputStream)
            var entry = zipInputStream.nextEntry
            var documentXmlText = ""
            while (entry != null) {
                if (entry.name == "word/document.xml") {
                    val byteArrayOutputStream = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(2048)
                    var len = zipInputStream.read(buffer)
                    while (len != -1) {
                        byteArrayOutputStream.write(buffer, 0, len)
                        len = zipInputStream.read(buffer)
                    }
                    documentXmlText = byteArrayOutputStream.toString("UTF-8")
                    break
                }
                entry = zipInputStream.nextEntry
            }
            zipInputStream.close()
            inputStream.close()
            
            if (documentXmlText.isNotBlank()) {
                val sb = StringBuilder()
                val regex = Regex("<w:t[^>]*>(.*?)</w:t>")
                regex.findAll(documentXmlText).forEach { match ->
                    val rawText = match.groupValues[1]
                    // Decode common XML entities if present
                    val cleanText = rawText
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&quot;", "\"")
                        .replace("&apos;", "'")
                    sb.append(cleanText).append(" ")
                }
                sb.toString().trim()
            } else {
                ""
            }
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
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

    private val backupMoshi = com.squareup.moshi.Moshi.Builder()
        .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
        .build()

    fun exportBackupToJson(context: Context, destinationEmail: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allTours = repository.allTourEntries.first()
                val allCases = repository.allCaseEntries.first()
                
                val backupMap = mapOf(
                    "export_date" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()),
                    "employee_name" to profile.value.name,
                    "tour_entries" to allTours,
                    "case_entries" to allCases
                )
                
                // Convert backupMap to JSON string
                val mapType = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
                val adapter = backupMoshi.adapter<Map<String, Any>>(mapType)
                val jsonText = adapter.toJson(backupMap)
                
                // Write jsonText to cache file
                val cacheFile = File(context.cacheDir, "forensic_diary_backup.json")
                cacheFile.writeText(jsonText)
                
                // Create file Uri using standard FileProvider
                val fileUri = androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "com.example.fileprovider",
                    cacheFile
                )
                
                // Launch send intent to prefilled backup email via Gmail / Share
                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(android.content.Intent.EXTRA_EMAIL, arrayOf(destinationEmail))
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Forensic Tour Diary Backup - ${profile.value.name}")
                    putExtra(android.content.Intent.EXTRA_TEXT, "Attached is your forensic tour diary backup file containing ${allTours.size} trip entries and ${allCases.size} cases.\n\nYou can restore this file anytime in the app under Profile/Settings tab.")
                    putExtra(android.content.Intent.EXTRA_STREAM, fileUri)
                    addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                
                val chooser = android.content.Intent.createChooser(intent, "Share/Save Backup to Gmail...")
                chooser.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun importBackupFromJson(context: Context, uri: Uri, callback: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val resolver = context.contentResolver
                val jsonText = resolver.openInputStream(uri)?.use { 
                    it.bufferedReader().readText() 
                } ?: ""
                
                if (jsonText.isBlank()) {
                    withContext(Dispatchers.Main) { callback(false) }
                    return@launch
                }
                
                val mapType = com.squareup.moshi.Types.newParameterizedType(Map::class.java, String::class.java, Any::class.java)
                val adapter = backupMoshi.adapter<Map<String, Any>>(mapType)
                val backupMap = adapter.fromJson(jsonText)
                
                if (backupMap == null) {
                    withContext(Dispatchers.Main) { callback(false) }
                    return@launch
                }
                
                val tourListRaw = backupMap["tour_entries"] as? List<*>
                val caseListRaw = backupMap["case_entries"] as? List<*>
                
                if (tourListRaw != null) {
                    val tourAdapter = backupMoshi.adapter(TourEntry::class.java)
                    for (item in tourListRaw) {
                        try {
                            val jsonItem = backupMoshi.adapter(Any::class.java).toJson(item)
                            val entry = tourAdapter.fromJson(jsonItem)
                            if (entry != null) {
                                // Save with 0 identifier to auto generate unique primary key and merge cleanly
                                repository.saveTourEntry(entry.copy(id = 0))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                
                if (caseListRaw != null) {
                    val caseAdapter = backupMoshi.adapter(CaseEntry::class.java)
                    for (item in caseListRaw) {
                        try {
                            val jsonItem = backupMoshi.adapter(Any::class.java).toJson(item)
                            val caseEntry = caseAdapter.fromJson(jsonItem)
                            if (caseEntry != null) {
                                repository.saveCaseEntry(caseEntry.copy(id = 0))
                            }
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                
                withContext(Dispatchers.Main) {
                    callback(true)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    callback(false)
                }
            }
        }
    }

    private fun saveUploadItemsToPrefs(items: List<UploadItem>) {
        try {
            val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, UploadItem::class.java)
            val adapter = backupMoshi.adapter<List<UploadItem>>(listType)
            val json = adapter.toJson(items)
            val sharedPrefs = getApplication<Application>().getSharedPreferences("TOUR_DIARY_FORM_PREFS", Context.MODE_PRIVATE)
            sharedPrefs.edit().putString("uploadItems", json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun restoreUploadItemsFromPrefs() {
        try {
            val sharedPrefs = getApplication<Application>().getSharedPreferences("TOUR_DIARY_FORM_PREFS", Context.MODE_PRIVATE)
            val json = sharedPrefs.getString("uploadItems", "") ?: ""
            if (json.isNotBlank()) {
                val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, UploadItem::class.java)
                val adapter = backupMoshi.adapter<List<UploadItem>>(listType)
                val items = adapter.fromJson(json)
                if (items != null) {
                    _uploadItems.value = items
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
    val arrDate: String = "",
    val isRoundTrip: Boolean = false
)

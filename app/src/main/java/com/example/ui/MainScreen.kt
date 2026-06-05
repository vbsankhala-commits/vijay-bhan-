package com.example.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.Canvas
import android.graphics.Bitmap
import android.graphics.Paint
import android.speech.RecognizerIntent
import androidx.core.content.FileProvider
import com.example.api.TourEntryOcrResult
import com.example.data.EmployeeProfile
import com.example.data.TourEntry
import com.example.data.CaseEntry
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: TourViewModel) {
    val context = LocalContext.current
    val profile by viewModel.profile.collectAsState()
    val availableMonths by viewModel.availableMonths.collectAsState()
    val selectedMonth by viewModel.selectedMonth.collectAsState()
    val filteredEntries by viewModel.filteredEntries.collectAsState()

    val ocrLoading by viewModel.ocrLoading.collectAsState()
    val ocrError by viewModel.ocrError.collectAsState()
    val ocrResult by viewModel.ocrResult.collectAsState()

    val sharedPrefs = remember { context.getSharedPreferences("TOUR_DIARY_FORM_PREFS", android.content.Context.MODE_PRIVATE) }

    var activeTab by remember { mutableIntStateOf(sharedPrefs.getInt("activeTab", 0)) } // 0: Diary, 1: Journeys, 2: Cases, 3: Add, 4: Profile

    var showSplashScreen by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(2000)
        showSplashScreen = false
    }

    // Form states
    var dateInput by remember { mutableStateOf(sharedPrefs.getString("dateInput", "") ?: "") }
    var arrDateInput by remember { mutableStateOf(sharedPrefs.getString("arrDateInput", "") ?: "") }
    var depTimeInput by remember { mutableStateOf(sharedPrefs.getString("depTimeInput", "") ?: "") }
    var arrTimeInput by remember { mutableStateOf(sharedPrefs.getString("arrTimeInput", "") ?: "") }
    var travelModeInput by remember { mutableStateOf(sharedPrefs.getString("travelModeInput", "") ?: "") }
    var distanceInput by remember { mutableStateOf(sharedPrefs.getString("distanceInput", "") ?: "") }
    var csNoInput by remember { mutableStateOf(sharedPrefs.getString("csNoInput", "") ?: "") }
    var firNoInput by remember { mutableStateOf(sharedPrefs.getString("firNoInput", "") ?: "") }
    var psInput by remember { mutableStateOf(sharedPrefs.getString("psInput", "") ?: "") }
    var districtInput by remember { mutableStateOf(sharedPrefs.getString("districtInput", "") ?: "") }
    var reportDateInput by remember { mutableStateOf(sharedPrefs.getString("reportDateInput", "") ?: "") }
    var remarksInput by remember { mutableStateOf(sharedPrefs.getString("remarksInput", "") ?: "") }
    var formCases by remember {
        val json = sharedPrefs.getString("formCases", "") ?: ""
        val initialCases = if (json.isNotBlank()) {
            try {
                val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, FormCase::class.java)
                val adapter = com.squareup.moshi.Moshi.Builder()
                    .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                    .build()
                    .adapter<List<FormCase>>(listType)
                adapter.fromJson(json)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
        mutableStateOf(initialCases ?: listOf(FormCase()))
    }
    var isRoundTripManual by remember { mutableStateOf(sharedPrefs.getBoolean("isRoundTripManual", false)) }

    // Auto-save form draft whenever any of the form states change or the active tab changes
    LaunchedEffect(
        dateInput,
        arrDateInput,
        depTimeInput,
        arrTimeInput,
        travelModeInput,
        distanceInput,
        csNoInput,
        firNoInput,
        psInput,
        districtInput,
        reportDateInput,
        remarksInput,
        formCases,
        isRoundTripManual,
        activeTab
    ) {
        sharedPrefs.edit().apply {
            putString("dateInput", dateInput)
            putString("arrDateInput", arrDateInput)
            putString("depTimeInput", depTimeInput)
            putString("arrTimeInput", arrTimeInput)
            putString("travelModeInput", travelModeInput)
            putString("distanceInput", distanceInput)
            putString("csNoInput", csNoInput)
            putString("firNoInput", firNoInput)
            putString("psInput", psInput)
            putString("districtInput", districtInput)
            putString("reportDateInput", reportDateInput)
            putString("remarksInput", remarksInput)
            putBoolean("isRoundTripManual", isRoundTripManual)
            putInt("activeTab", activeTab)
            
            try {
                val listType = com.squareup.moshi.Types.newParameterizedType(List::class.java, FormCase::class.java)
                val adapter = com.squareup.moshi.Moshi.Builder()
                    .addLast(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory())
                    .build()
                    .adapter<List<FormCase>>(listType)
                val json = adapter.toJson(formCases)
                putString("formCases", json)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            apply()
        }
    }

    // Dialog state for PDF settings
    var showPdfExportDialog by remember { mutableStateOf(false) }
    var pdfExportDialogInitialReportType by remember { mutableStateOf(0) }
    var showIfmsBotDialog by remember { mutableStateOf(false) }
    var showProfileEditDialog by remember { mutableStateOf(false) }
    var editingTourEntry by remember { mutableStateOf<TourEntry?>(null) }

    // Document Picker for AI OCR (single)
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.parseDocument(context, uri)
        }
    }

    // Document Picker for multiple AI OCR documents
    val multipleDocsPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.processMultipleUris(context, uris)
            Toast.makeText(context, "Processing ${uris.size} document(s)...", Toast.LENGTH_SHORT).show()
        }
    }

    // Effect to prefill inputs from OCR result
    LaunchedEffect(ocrResult) {
        val res = ocrResult
        if (res != null) {
            dateInput = res.date ?: ""
            depTimeInput = res.depTime ?: ""
            arrTimeInput = res.arrTime ?: ""
            travelModeInput = res.travelMode ?: ""
            distanceInput = res.distance?.toString() ?: ""
            csNoInput = res.csNumber ?: ""
            firNoInput = res.firNumber ?: ""
            psInput = res.policeStation ?: ""
            districtInput = res.district ?: ""
            reportDateInput = res.reportDate ?: ""
            formCases = listOf(
                FormCase(
                    csNo = res.csNumber ?: "",
                    firNo = res.firNumber ?: "",
                    policeStation = res.policeStation ?: "",
                    district = res.district ?: ""
                )
            )
        }
    }

    val initials = remember(profile.name) {
        if (profile.name.isNotBlank()) {
            profile.name.trim().split("\\s+".toRegex()).take(2).map { it.take(1).uppercase() }.joinToString("")
        } else {
            "JD"
        }
    }
    val displayName = if (profile.name.isNotBlank()) profile.name else "John Doe"
    val displayJob = if (profile.designation.isNotBlank() || profile.posting.isNotBlank()) {
        val desPart = profile.designation.ifBlank { "Sub-Inspector" }
        val postPart = profile.posting.ifBlank { "West District" }
        "$desPart • $postPart"
    } else {
        "Sub-Inspector • West District"
    }

    if (showSplashScreen) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF6750A4)), // Primary purple
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "App Logo",
                    tint = Color.White,
                    modifier = Modifier.size(72.dp)
                )
                Text(
                    text = "Monthly Tour Diary",
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Mobile Forensic Units",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
                
                Spacer(modifier = Modifier.height(48.dp))
                
                Text(
                    text = "Developed by",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                )
                Text(
                    text = "Vijay Bhan Sankhala",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                title = {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { showProfileEditDialog = true }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Initials Avatar
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xFFEADDFF)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initials,
                                color = Color(0xFF21005D),
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }

                        // Name & Role Details
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = displayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF1D1B20),
                                    lineHeight = 18.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Icon(
                                    imageVector = Icons.Filled.Edit,
                                    contentDescription = "Edit Profile",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            Text(
                                text = displayJob,
                                fontSize = 11.sp,
                                color = Color(0xFF49454F),
                                lineHeight = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            Toast.makeText(context, "Tour Diary App v1.0.0. Secure Offline Storage.", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFF3EDF7)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "?",
                                color = Color(0xFF49454F),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFFFEF7FF),
                    titleContentColor = Color(0xFF1D1B20)
                )
            )
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(72.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    border = BorderStroke(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icon 0: Diary View
                        val selected0 = activeTab == 0
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { activeTab = 0 }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.List,
                                contentDescription = "Diary View",
                                tint = if (selected0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(if (selected0) 26.dp else 22.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Diary",
                                fontSize = 10.sp,
                                fontWeight = if (selected0) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (selected0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }

                        // Icon 1: Journeys
                        val selected1 = activeTab == 1
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1.1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { activeTab = 1 }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.DirectionsCar,
                                contentDescription = "Journeys",
                                tint = if (selected1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(if (selected1) 26.dp else 22.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Journeys",
                                fontSize = 10.sp,
                                fontWeight = if (selected1) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (selected1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }

                        // Icon 2: Cases
                        val selected2 = activeTab == 2
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { activeTab = 2 }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Star,
                                contentDescription = "Cases",
                                tint = if (selected2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(if (selected2) 26.dp else 22.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Cases",
                                fontSize = 10.sp,
                                fontWeight = if (selected2) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (selected2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }

                        // Icon 3: Log Entry
                        val selected3 = activeTab == 3
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1.1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { activeTab = 3 }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Log Entry",
                                tint = if (selected3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                modifier = Modifier.size(if (selected3) 26.dp else 22.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Log Entry",
                                fontSize = 10.sp,
                                fontWeight = if (selected3) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (selected3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }

                        // Icon 4: Profile
                        val selected4 = activeTab == 4
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(20.dp))
                                .clickable { activeTab = 4 }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = "Profile",
                                tint = if (selected4) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(if (selected4) 26.dp else 22.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Profile",
                                fontSize = 10.sp,
                                fontWeight = if (selected4) FontWeight.ExtraBold else FontWeight.Medium,
                                color = if (selected4) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when (activeTab) {
                0 -> DiaryListTab(
                    viewModel = viewModel,
                    profile = profile,
                    availableMonths = availableMonths,
                    selectedMonth = selectedMonth,
                    filteredEntries = filteredEntries,
                    onExportPdfClicked = { 
                        pdfExportDialogInitialReportType = 0
                        showPdfExportDialog = true 
                    },
                    onGenerateTaReportClicked = {
                        pdfExportDialogInitialReportType = 1
                        showPdfExportDialog = true
                    },
                    onOpenIfmsBot = { showIfmsBotDialog = true },
                    onRequestEditProfile = { showProfileEditDialog = true },
                    onEditEntry = { editingTourEntry = it },
                    onDuplicateEntry = { editingTourEntry = it }
                )
                1 -> FilledJourneysTab(
                    viewModel = viewModel,
                    onEditEntry = { editingTourEntry = it },
                    onDuplicateEntry = { editingTourEntry = it }
                )
                2 -> CasesTab(viewModel = viewModel)
                3 -> AddEntryTab(
                    viewModel = viewModel,
                    ocrLoading = ocrLoading,
                    ocrError = ocrError,
                    ocrResult = ocrResult,
                    dateVal = dateInput,
                    onDateChange = { 
                        dateInput = it
                        if (arrDateInput.isBlank()) arrDateInput = it 
                    },
                    arrDateVal = arrDateInput,
                    onArrDateChange = { arrDateInput = it },
                    depVal = depTimeInput,
                    onDepChange = { depTimeInput = it },
                    arrVal = arrTimeInput,
                    onArrChange = { arrTimeInput = it },
                    modeVal = travelModeInput,
                    onModeChange = { travelModeInput = it },
                    distVal = distanceInput,
                    onDistChange = { distanceInput = it },
                    casesList = formCases,
                    onCasesChange = { formCases = it },
                    repDateVal = reportDateInput,
                    onRepDateChange = { reportDateInput = it },
                    remarksVal = remarksInput,
                    onRemarksChange = { remarksInput = it },
                    isRoundTripManualVal = isRoundTripManual,
                    onRoundTripManualChange = { isRoundTripManual = it },
                    onLaunchPicker = { documentPickerLauncher.launch("*/*") },
                    onLaunchMultiplePicker = { multipleDocsPickerLauncher.launch("*/*") },
                    onClearOcr = { viewModel.clearOcrState() },
                    onSave = {
                        val serialized = serializeCases(formCases)
                        val finalPs = serialized["policeStation"] ?: ""
                        if (dateInput.isBlank() || finalPs.isBlank()) {
                            Toast.makeText(context, "Please enter Date and Police Station", Toast.LENGTH_SHORT).show()
                        } else {
                            val distDouble = distanceInput.toDoubleOrNull() ?: 0.0
                            val repDate = reportDateInput.ifBlank { dateInput }
                              
                            if (isRoundTripManual) {
                                viewModel.saveRajasthanRoundTripEntry(
                                    date = dateInput,
                                    depTime = depTimeInput,
                                    arrTime = arrTimeInput,
                                    travelMode = travelModeInput,
                                    distance = distDouble,
                                    csNumber = serialized["csNumber"] ?: "",
                                    firNumber = serialized["firNumber"] ?: "",
                                    policeStation = finalPs,
                                    district = serialized["district"] ?: "",
                                    reportDate = repDate,
                                    remarks = remarksInput,
                                    arrDate = arrDateInput
                                )
                            } else {
                                viewModel.saveTourEntry(
                                    date = dateInput,
                                    depTime = depTimeInput,
                                    arrTime = arrTimeInput,
                                    travelMode = travelModeInput,
                                    distance = distDouble,
                                    csNumber = serialized["csNumber"] ?: "",
                                    firNumber = serialized["firNumber"] ?: "",
                                    policeStation = finalPs,
                                    district = serialized["district"] ?: "",
                                    reportDate = repDate,
                                    remarks = remarksInput,
                                    arrDate = arrDateInput
                                )
                            }
                            Toast.makeText(context, "Journey entry saved successfully!", Toast.LENGTH_SHORT).show()
                            
                            // Clear form and switch to history view
                            dateInput = ""
                            arrDateInput = ""
                            depTimeInput = ""
                            arrTimeInput = ""
                            travelModeInput = ""
                            distanceInput = ""
                            formCases = listOf(FormCase())
                            reportDateInput = ""
                            remarksInput = ""
                            isRoundTripManual = false
                            viewModel.clearOcrState()
                            activeTab = 0
                        }
                    }
                )
                4 -> ProfileTab(
                    profile = profile,
                    viewModel = viewModel,
                    onSaveProfile = { name, des, posting, geminiKey, backupEmail, basicSalary, taCategory ->
                        viewModel.saveProfile(name, des, posting, geminiKey, backupEmail, basicSalary, taCategory)
                        Toast.makeText(context, "Employee Profile Updated!", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Export tour diary dialog (Support both PDF and Word .doc formats)
            if (showPdfExportDialog) {
                PdfExportDialog(
                    selectedMonth = selectedMonth,
                    initialReportType = pdfExportDialogInitialReportType,
                    onDismiss = { showPdfExportDialog = false },
                    onConfirmPdf = { isLegal, docType ->
                        showPdfExportDialog = false
                        val file = if (docType == 1) {
                            viewModel.createRajasthanTaBillPdf(context, selectedMonth, isLegal)
                        } else {
                            viewModel.createMonthlyDiaryPdf(context, selectedMonth, isLegal)
                        }
                        if (file != null) {
                            sharePdfFile(context, file)
                        } else {
                            Toast.makeText(context, "Failed to build PDF. Please try again.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onConfirmWord = {
                        showPdfExportDialog = false
                        val file = viewModel.createMonthlyDiaryDoc(context, selectedMonth)
                        if (file != null) {
                            shareDocFile(context, file)
                        } else {
                            Toast.makeText(context, "Failed to build Word file. Please try again.", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            if (showIfmsBotDialog) {
                IfmsAutoFillBotDialog(
                    selectedMonth = selectedMonth,
                    profile = profile,
                    entries = filteredEntries,
                    onDismiss = { showIfmsBotDialog = false }
                )
            }

            // Profile Edit Dialog
            if (showProfileEditDialog) {
                ProfileEditDialog(
                    profile = profile,
                    onDismiss = { showProfileEditDialog = false },
                    onSaveProfile = { name, designation, posting, salary, category ->
                        viewModel.saveProfile(name, designation, posting, null, null, salary, category)
                        Toast.makeText(context, "Employee Profile Updated!", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Tour Entry Edit Dialog
            if (editingTourEntry != null) {
                TourEntryEditDialog(
                    entry = editingTourEntry!!,
                    profile = profile,
                    onDismiss = { editingTourEntry = null },
                    onSave = { updatedEntry ->
                        if (updatedEntry.id == 0L) {
                            viewModel.saveTourEntry(updatedEntry)
                            Toast.makeText(context, "Copied Journey Inserted Successfully!", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.updateTourEntry(updatedEntry)
                            Toast.makeText(context, "Journey Entry Updated!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDelete = {
                        viewModel.deleteTourEntry(editingTourEntry!!)
                        editingTourEntry = null
                        Toast.makeText(context, "Journey Entry Deleted Successfully.", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
    }
}

@Composable
fun TableHeaderCell(text: String, width: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(horizontal = 6.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF21005D),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun TableDataCell(
    text: String, 
    width: Dp, 
    isBold: Boolean = false, 
    isSNo: Boolean = false,
    alignLeft: Boolean = false
) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        contentAlignment = if (alignLeft) Alignment.CenterStart else Alignment.Center
    ) {
        Text(
            text = text.ifBlank { "-" },
            fontSize = 12.sp,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            color = if (isSNo) MaterialTheme.colorScheme.primary else Color(0xFF1D1B20),
            textAlign = if (alignLeft) TextAlign.Start else TextAlign.Center,
            maxLines = 8,
            overflow = TextOverflow.Ellipsis
        )
    }
}

fun formatDateView(dateStr: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val formatter = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
        val parsed = parser.parse(dateStr)
        if (parsed != null) formatter.format(parsed) else dateStr
    } catch (e: Exception) {
        dateStr
    }
}

@Composable
fun DiaryListTab(
    viewModel: TourViewModel,
    profile: EmployeeProfile,
    availableMonths: List<String>,
    selectedMonth: String,
    filteredEntries: List<TourEntry>,
    onExportPdfClicked: () -> Unit,
    onGenerateTaReportClicked: () -> Unit,
    onOpenIfmsBot: () -> Unit,
    onRequestEditProfile: () -> Unit,
    onEditEntry: (TourEntry) -> Unit,
    onDuplicateEntry: (TourEntry) -> Unit
) {
    val entriesCountByMonth by viewModel.entriesCountByMonth.collectAsState()

    val formatMonthYear: (String) -> String = { mYear ->
        try {
            val parser = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.getDefault())
            val formatter = java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.getDefault())
            val date = parser.parse(mYear)
            if (date != null) formatter.format(date) else mYear
        } catch (e: Exception) {
            mYear
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Profile Alert warning if empty
        if (profile.name.isBlank()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFD8E4)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRequestEditProfile() }
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = "Warning", tint = Color(0xFF31111D))
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            "Profile Incomplete", 
                            fontWeight = FontWeight.Bold, 
                            fontSize = 13.sp,
                            color = Color(0xFF31111D)
                        )
                        Text(
                            "Tap here to quickly fill your name, designation, and posting place for output headers.", 
                            fontSize = 11.sp,
                            color = Color(0xFF31111D).copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }

        // Selected Month & Year section on top, bilingual with quick navigation arrows
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7)),
            border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.6f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Prev Month Button
                IconButton(
                    onClick = { viewModel.selectPreviousMonth() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Previous Month",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Month-Year Selection Dropdowns
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    var monthExpanded by remember { mutableStateOf(false) }
                    var yearExpanded by remember { mutableStateOf(false) }

                    val monthParts = selectedMonth.split("-")
                    val currentYear = monthParts.getOrNull(0) ?: "2026"
                    val currentMonthCode = monthParts.getOrNull(1) ?: "05"

                    val monthsList = listOf(
                        "01" to "Jan",
                        "02" to "Feb",
                        "03" to "Mar",
                        "04" to "Apr",
                        "05" to "May",
                        "06" to "Jun",
                        "07" to "Jul",
                        "08" to "Aug",
                        "09" to "Sep",
                        "10" to "Oct",
                        "11" to "Nov",
                        "12" to "Dec"
                    )
                    val yearsList = listOf("2024", "2025", "2026", "2027", "2028", "2029", "2030")

                    val currentMonthName = monthsList.find { it.first == currentMonthCode }?.second?.split(" ")?.firstOrNull() ?: "May"

                    // Month Dropdown Trigger
                    Box {
                        TextButton(
                            onClick = { monthExpanded = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(currentMonthName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Select Month", modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = monthExpanded,
                            onDismissRequest = { monthExpanded = false }
                        ) {
                            monthsList.forEach { (code, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, fontSize = 14.sp) },
                                    onClick = {
                                        viewModel.selectMonth("$currentYear-$code")
                                        monthExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Divider dot or slashes
                    Text("/", color = Color.Gray.copy(alpha = 0.5f), fontSize = 16.sp, fontWeight = FontWeight.Bold)

                    // Year Dropdown Trigger
                    Box {
                        TextButton(
                            onClick = { yearExpanded = true },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(currentYear, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Icon(Icons.Filled.ArrowDropDown, contentDescription = "Select Year", modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(
                            expanded = yearExpanded,
                            onDismissRequest = { yearExpanded = false }
                        ) {
                            yearsList.forEach { y ->
                                DropdownMenuItem(
                                    text = { Text(y, fontSize = 14.sp) },
                                    onClick = {
                                        viewModel.selectMonth("$y-$currentMonthCode")
                                        yearExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Next Month Button
                IconButton(
                    onClick = { viewModel.selectNextMonth() },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Next Month",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Month-wise Folders / Saved Diaries Row
        if (availableMonths.isNotEmpty()) {
            Text(
                text = "Saved Monthly Folders",
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 6.dp, start = 4.dp),
                letterSpacing = 0.5.sp
            )
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                items(availableMonths) { mYear ->
                    val count = entriesCountByMonth[mYear] ?: 0
                    val isSelected = mYear == selectedMonth
                    val monthLabel = formatMonthYear(mYear)
                    
                    Surface(
                        onClick = { viewModel.selectMonth(mYear) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFFCAC4D0).copy(alpha = 0.6f)
                        ),
                        tonalElevation = if (isSelected) 2.dp else 0.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = if (isSelected) Icons.Filled.FolderOpen else Icons.Filled.Folder,
                                contentDescription = "Folder Icon",
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = monthLabel,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 12.sp,
                                color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "$count trips",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // High-Fidelity "Sleek Interface" October Tour Summary Card
        val totalDist = filteredEntries.sumOf { it.distance }
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8DEF8)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Header (Title + Badge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${formatMonthYear(selectedMonth).uppercase()} TOUR SUMMARY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF49454F),
                        letterSpacing = 1.sp
                    )
                    
                    // Status Badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFFFD8E4))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = "PROCESSED",
                            color = Color(0xFF31111D),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Center numeric summaries
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${filteredEntries.size}",
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF21005D)
                            )
                            Text(
                                text = "Entries",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFF21005D).copy(alpha = 0.7f),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        
                        Text(
                            text = "Total Distance: " + String.format(Locale.getDefault(), "%.1f KM", totalDist),
                            fontSize = 13.sp,
                            color = Color(0xFF49454F),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Divider(
                    color = Color(0xFFCAC4D0).copy(alpha = 0.4f),
                    thickness = 0.8.dp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onExportPdfClicked,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6750A4), 
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = "Export Diary Icon", modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Export Diary",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Button(
                        onClick = onGenerateTaReportClicked,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF006874), 
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.AccountBalanceWallet, contentDescription = "TA Report Icon", modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "TA Report (PDF)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = onOpenIfmsBot,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.AutoMode, contentDescription = "IFMS Bot Icon", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Rajasthan IFMS 3.0 TA Auto-Fill Bot",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // State for list view mode (Card view as default for better mobile ergonomics, with Table toggle)
        var listViewMode by remember { mutableStateOf("card") } // "card" or "table"

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${formatMonthYear(selectedMonth)} Journeys",
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.primary
            )

            // Segmented Control Switcher
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFFF3EDF7))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Card View Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (listViewMode == "card") Color(0xFFEADDFF) else Color.Transparent)
                        .clickable { listViewMode = "card" }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.List,
                            contentDescription = "Card Mode",
                            tint = if (listViewMode == "card") Color(0xFF21005D) else Color(0xFF49454F),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Cards",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (listViewMode == "card") Color(0xFF21005D) else Color(0xFF49454F)
                        )
                    }
                }

                // Table View Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (listViewMode == "table") Color(0xFFEADDFF) else Color.Transparent)
                        .clickable { listViewMode = "table" }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = "Table Mode",
                            tint = if (listViewMode == "table") Color(0xFF21005D) else Color(0xFF49454F),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Table",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (listViewMode == "table") Color(0xFF21005D) else Color(0xFF49454F)
                        )
                    }
                }
            }
        }

        if (filteredEntries.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = "No entries",
                        modifier = Modifier.size(56.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("No logs found for this month.", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("Tap \"Log Entry\" in the bottom bar to log a trip.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                }
            }
        } else {
            if (listViewMode == "card") {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(filteredEntries, key = { it.id }) { entry ->
                        TourEntryCard(
                            entry = entry,
                            onEdit = { onEditEntry(entry) },
                            onDelete = { viewModel.deleteTourEntry(entry) },
                            onDuplicate = { onDuplicateEntry(entry.copy(id = 0L)) }
                        )
                    }
                }
            } else {
                // Elegant Table Layout with consecutive Serial Numbers
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .border(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.8f), RoundedCornerShape(12.dp))
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.White)
                ) {
                    // Wrap in local horizontal scroll container so table forms a wide spreadsheet grid!
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Column {
                            // 1. Table Header Row (Material 3 style)
                            Row(
                                modifier = Modifier
                                    .background(Color(0xFFF3EDF7)) // Primary container-style background
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TableHeaderCell("S.No.", 55.dp)
                                TableHeaderCell("Dep. Date", 100.dp)
                                TableHeaderCell("Dep. Time", 85.dp)
                                TableHeaderCell("Arr. Date", 100.dp)
                                TableHeaderCell("Arr. Time", 85.dp)
                                TableHeaderCell("Mode", 100.dp)
                                TableHeaderCell("Dist (km)", 70.dp)
                                TableHeaderCell("C.S. No.", 110.dp)
                                TableHeaderCell("FIR No.", 90.dp)
                                TableHeaderCell("Police Station", 130.dp)
                                TableHeaderCell("District", 90.dp)
                                TableHeaderCell("Rep. Date", 85.dp)
                                TableHeaderCell("Action", 120.dp)
                            }

                            Divider(color = Color(0xFFCAC4D0), thickness = 1.dp)

                            // 2. Table Data Rows inside vertical scroll container
                            LazyColumn(
                                modifier = Modifier.fillMaxHeight(),
                                verticalArrangement = Arrangement.Top
                            ) {
                                itemsIndexed(filteredEntries, key = { _, item -> item.id }) { index, entry ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onEditEntry(entry) }
                                            .padding(horizontal = 4.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Highlight S.No (sequential number to all entries in month)
                                        TableDataCell((index + 1).toString(), 55.dp, isBold = true, isSNo = true)
                                        TableDataCell(formatDateView(entry.date), 100.dp)
                                        TableDataCell(entry.depTime, 85.dp)
                                        TableDataCell(formatDateView(entry.arrDate.ifBlank { entry.date }), 100.dp)
                                        TableDataCell(entry.arrTime, 85.dp)
                                        TableDataCell(entry.travelMode, 100.dp)
                                        TableDataCell(String.format(Locale.getDefault(), "%.1f", entry.distance), 70.dp)
                                        TableDataCell(entry.csNumber, 110.dp)
                                        TableDataCell(entry.firNumber, 90.dp)
                                        TableDataCell(entry.policeStation, 130.dp, alignLeft = true)
                                        TableDataCell(entry.district, 90.dp, alignLeft = true)
                                        TableDataCell(formatDateView(entry.reportDate), 85.dp)

                                        // Action: Edit & Delete side-by-side
                                        Row(
                                            modifier = Modifier.width(120.dp),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            IconButton(
                                                onClick = { onEditEntry(entry) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Edit,
                                                    contentDescription = "Edit entry",
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            IconButton(
                                                onClick = { viewModel.deleteTourEntry(entry) },
                                                modifier = Modifier.size(36.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Delete,
                                                    contentDescription = "Delete entry",
                                                    tint = Color(0xFFBA1A1A),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }

                                    Divider(color = Color(0xFFCAC4D0).copy(alpha = 0.5f), thickness = 0.5.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilledJourneysTab(
    viewModel: TourViewModel,
    onEditEntry: (TourEntry) -> Unit,
    onDuplicateEntry: (TourEntry) -> Unit
) {
    val allEntries by viewModel.allTourEntries.collectAsState()
    var searchQuery by remember { mutableStateOf("") }

    val filteredAll = remember(allEntries, searchQuery) {
        if (searchQuery.isBlank()) {
            allEntries
        } else {
            val q = searchQuery.lowercase(Locale.getDefault())
            allEntries.filter {
                it.policeStation.lowercase(Locale.getDefault()).contains(q) ||
                it.csNumber.lowercase(Locale.getDefault()).contains(q) ||
                it.firNumber.lowercase(Locale.getDefault()).contains(q) ||
                it.district.lowercase(Locale.getDefault()).contains(q) ||
                it.date.contains(q) ||
                it.travelMode.lowercase(Locale.getDefault()).contains(q) ||
                it.remarks.lowercase(Locale.getDefault()).contains(q)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "Filled Journeys",
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Inspect, search, edit, or remove all logged travels in one place",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Sleek Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search logs by station, C.S. No, mode, date...") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = "Search icon")
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color(0xFFCAC4D0)
            )
        )

        // Summary of results and reset option
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Showing ${filteredAll.size} of ${allEntries.size} trips",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.secondary
            )
            if (searchQuery.isNotBlank()) {
                Text(
                    text = "Clear Filters",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { searchQuery = "" }
                )
            }
        }

        if (filteredAll.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.DirectionsCar,
                        contentDescription = "No match",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (allEntries.isEmpty()) "No journeys found" else "No matching journeys",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = if (allEntries.isEmpty()) "Logged travels will be listed here. Tap Log Entry to add!" else "Try typing a different keyword or date series",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredAll, key = { it.id }) { entry ->
                    TourEntryCard(
                        entry = entry,
                        onEdit = { onEditEntry(entry) },
                        onDelete = { viewModel.deleteTourEntry(entry) },
                        onDuplicate = { onDuplicateEntry(entry.copy(id = 0L)) }
                    )
                }
            }
        }
    }
}

@Composable
fun TourEntryCard(
    entry: TourEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit
) {
    val dateParts = entry.date.split("-")
    val dayText = if (dateParts.size >= 3) dateParts[2] else ""
    val monthNoText = if (dateParts.size >= 2) dateParts[1] else ""
    val monthShortName = when (monthNoText) {
        "01" -> "Jan"
        "02" -> "Feb"
        "03" -> "Mar"
        "04" -> "Apr"
        "05" -> "May"
        "06" -> "Jun"
        "07" -> "Jul"
        "08" -> "Aug"
        "09" -> "Sep"
        "10" -> "Oct"
        "11" -> "Nov"
        "12" -> "Dec"
        else -> "Jan"
    }

    val travelIcon = when {
        entry.travelMode.contains("bike", ignoreCase = true) || entry.travelMode.contains("motorcycle", ignoreCase = true) || entry.travelMode.contains("two", ignoreCase = true) -> Icons.Filled.TwoWheeler
        entry.travelMode.contains("bus", ignoreCase = true) -> Icons.Filled.DirectionsBus
        entry.travelMode.contains("train", ignoreCase = true) -> Icons.Filled.Train
        else -> Icons.Filled.DirectionsCar
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primaryContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // Enlarged Calendar Badge
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (dayText.isNotBlank()) dayText else "??",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = monthShortName.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        lineHeight = 12.sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Icon(
                        imageVector = travelIcon,
                        contentDescription = "Travel Mode",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Center details
            Column(
                modifier = Modifier
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Header: Police Station (Enlarged with Location Icon)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.LocationOn,
                        contentDescription = "Location",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (entry.policeStation.isNotBlank()) "P.S. ${entry.policeStation}" else "No Station",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Badges row: CS No & FIR Number
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (entry.csNumber.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "C.S. No: ${entry.csNumber}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                    if (entry.firNumber.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFEADDFF))
                                .border(1.dp, Color(0xFF6750A4), RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "FIR: ${entry.firNumber}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF21005D)
                            )
                        }
                    }
                }

                // Timeline Row (Enlarged and highlighted with Icons)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DEPARTURE", 
                            fontSize = 9.sp, 
                            fontWeight = FontWeight.Bold, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            letterSpacing = 0.5.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(Icons.Filled.Schedule, contentDescription = "Time", modifier = Modifier.size(12.dp), tint = Color.Gray)
                            Text(entry.depTime.ifBlank { "--:--" }, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    
                    Icon(
                        imageVector = Icons.Filled.ArrowForward,
                        contentDescription = "to",
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "ARRIVAL", 
                            fontSize = 9.sp, 
                            fontWeight = FontWeight.Bold, 
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            letterSpacing = 0.5.sp
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(entry.arrTime.ifBlank { "--:--" }, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                            Icon(Icons.Filled.Schedule, contentDescription = "Time", modifier = Modifier.size(12.dp), tint = Color.Gray)
                        }
                    }
                }

                // District, Mode & Distance prominent footer
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Dist: ${entry.district.ifBlank { "N/A" }} • ${entry.travelMode}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )

                    Spacer(modifier = Modifier.width(4.dp))

                    // Distance Badge (Enlarged)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${entry.distance} KM",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            // Right actions: taller touch targets, high clickability
            Column(
                modifier = Modifier.align(Alignment.Top),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(38.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit travel entry",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDuplicate,
                    modifier = Modifier
                        .size(38.dp)
                        .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ContentCopy,
                        contentDescription = "Duplicate travel entry",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(38.dp)
                        .background(MaterialTheme.colorScheme.errorContainer, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Delete travel entry",
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun UploadedItemCard(
    item: UploadItem,
    onUpdate: (UploadItem) -> Unit,
    onRemove: () -> Unit,
    onSave: (isRoundTrip: Boolean) -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (item.error != null) Color(0xFFFFDAD9) else Color(0xFFFBF8FD)
        ),
        border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Name and close icon
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (item.fileName.endsWith(".pdf", ignoreCase = true)) {
                            Icons.Filled.List
                        } else {
                            Icons.Filled.Add
                        },
                        contentDescription = "File Type",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = item.fileName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF1D1B20),
                        maxLines = 1
                    )
                }
                
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Remove File From Queue",
                        tint = Color(0xFFBA1A1A),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (item.isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                    Text(
                        "Gemini is analyzing document...",
                        fontSize = 12.sp,
                        color = Color(0xFF49454F),
                        fontWeight = FontWeight.Medium
                    )
                }
            } else if (item.error != null) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        "Extraction Failed",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFFBA1A1A)
                    )
                    Text(
                        item.error,
                        fontSize = 11.sp,
                        color = Color(0xFF31111D)
                    )
                    Button(
                        onClick = onRemove,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFDAD9), contentColor = Color(0xFFBA1A1A)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier
                            .align(Alignment.End)
                            .height(28.dp)
                    ) {
                        Text("Dismiss", fontSize = 11.sp)
                    }
                }
            } else {
                Divider(color = Color(0xFFCAC4D0).copy(alpha = 0.5f))

                // Dates
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val calendar = Calendar.getInstance()
                    val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
                        val fMonth = String.format(Locale.getDefault(), "%02d", month + 1)
                        val fDay = String.format(Locale.getDefault(), "%02d", dayOfMonth)
                        onUpdate(item.copy(date = "$year-$fMonth-$fDay"))
                    }
                    val repDateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
                        val fMonth = String.format(Locale.getDefault(), "%02d", month + 1)
                        val fDay = String.format(Locale.getDefault(), "%02d", dayOfMonth)
                        onUpdate(item.copy(reportDate = "$year-$fMonth-$fDay"))
                    }

                    OutlinedTextField(
                        value = item.date,
                        onValueChange = { onUpdate(item.copy(date = it)) },
                        label = { Text("Trip Date", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                DatePickerDialog(context, dateSetListener, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                            }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.DateRange, contentDescription = "Pick Date", modifier = Modifier.size(16.dp))
                            }
                        }
                    )

                    OutlinedTextField(
                        value = item.reportDate,
                        onValueChange = { onUpdate(item.copy(reportDate = it)) },
                        label = { Text("Report Date", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                DatePickerDialog(context, repDateSetListener, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                            }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.DateRange, contentDescription = "Pick Report Date", modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                }

                // Times
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val depTimeListener = TimePickerDialog.OnTimeSetListener { _, hr, min ->
                        onUpdate(item.copy(depTime = String.format(Locale.getDefault(), "%02d:%02d", hr, min)))
                    }
                    val arrTimeListener = TimePickerDialog.OnTimeSetListener { _, hr, min ->
                        onUpdate(item.copy(arrTime = String.format(Locale.getDefault(), "%02d:%02d", hr, min)))
                    }

                    OutlinedTextField(
                        value = item.depTime,
                        onValueChange = { onUpdate(item.copy(depTime = it)) },
                        label = { Text("Dep (HH:MM)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                TimePickerDialog(context, depTimeListener, 10, 0, true).show()
                            }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Pick Dep Time", modifier = Modifier.size(16.dp))
                            }
                        }
                    )

                    OutlinedTextField(
                        value = item.arrTime,
                        onValueChange = { onUpdate(item.copy(arrTime = it)) },
                        label = { Text("Arr (HH:MM)", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                TimePickerDialog(context, arrTimeListener, 16, 0, true).show()
                            }, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Pick Arr Time", modifier = Modifier.size(16.dp))
                            }
                        }
                    )
                }

                val itemCases = remember(item.csNumber, item.firNumber, item.policeStation, item.district) {
                    parseCases(item.csNumber, item.firNumber, item.policeStation, item.district)
                }

                JourneyCasesSection(
                    cases = itemCases,
                    onCasesChange = { updatedCases ->
                        val serialized = serializeCases(updatedCases)
                        onUpdate(
                            item.copy(
                                csNumber = serialized["csNumber"] ?: "",
                                firNumber = serialized["firNumber"] ?: "",
                                policeStation = serialized["policeStation"] ?: "",
                                district = serialized["district"] ?: ""
                            )
                        )
                    },
                    title = "Review Case Details",
                    isCompact = true
                )

                // Travel Details
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = item.travelMode,
                        onValueChange = { onUpdate(item.copy(travelMode = it)) },
                        label = { Text("Travel Mode", fontSize = 11.sp) },
                        modifier = Modifier.weight(1.2f),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = item.distance,
                        onValueChange = { onUpdate(item.copy(distance = it)) },
                        label = { Text("Distance (km)", fontSize = 11.sp) },
                        modifier = Modifier.weight(0.8f),
                        singleLine = true
                    )
                }

                // Remarks
                OutlinedTextField(
                    value = item.remarks,
                    onValueChange = { onUpdate(item.copy(remarks = it)) },
                    label = { Text("Remarks & Scene Visit Details", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )

                // Round trip toggle row for Rajasthan Rules
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto 40 km/h Round-Trip Split", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Text("Generates outward + return legs automatically.", fontSize = 9.sp, color = Color.Gray)
                    }
                    Switch(
                        checked = item.isRoundTrip,
                        onCheckedChange = { onUpdate(item.copy(isRoundTrip = it)) }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Save button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            if (item.date.isBlank() || item.policeStation.isBlank()) {
                                Toast.makeText(context, "Trip Date and Police Station are required fields!", Toast.LENGTH_LONG).show()
                            } else {
                                onSave(item.isRoundTrip)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Icon(Icons.Filled.Done, contentDescription = "Accept", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Trip Entry", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

data class FormCase(
    val csNo: String = "",
    val firNo: String = "",
    val policeStation: String = "",
    val district: String = ""
)

fun parseCases(csNumber: String, firNumber: String, policeStation: String, district: String): List<FormCase> {
    val csList = csNumber.split("\n")
    val firList = firNumber.split("\n")
    val psList = policeStation.split("\n")
    val distList = district.split("\n")
    
    val count = maxOf(1, csList.size, firList.size, psList.size, distList.size)
    return (0 until count).map { i ->
        FormCase(
            csNo = csList.getOrNull(i)?.trim() ?: "",
            firNo = firList.getOrNull(i)?.trim() ?: "",
            policeStation = psList.getOrNull(i)?.trim() ?: "",
            district = distList.getOrNull(i)?.trim() ?: ""
        )
    }
}

fun serializeCases(cases: List<FormCase>): Map<String, String> {
    if (cases.isEmpty()) {
        return mapOf(
            "csNumber" to "",
            "firNumber" to "",
            "policeStation" to "",
            "district" to ""
        )
    }
    return mapOf(
        "csNumber" to cases.joinToString("\n") { it.csNo.trim() },
        "firNumber" to cases.joinToString("\n") { it.firNo.trim() },
        "policeStation" to cases.joinToString("\n") { it.policeStation.trim() },
        "district" to cases.joinToString("\n") { it.district.trim() }
    )
}

@Composable
fun JourneyCasesSection(
    cases: List<FormCase>,
    onCasesChange: (List<FormCase>) -> Unit,
    title: String = "2. POLICE STATION & CASE DETAILS",
    isCompact: Boolean = false
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Info, 
                    contentDescription = "Case Info Icon", 
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (isCompact) 11.sp else 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 0.5.sp
                )
            }
        }

        cases.forEachIndexed { index, itemCase ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
                border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CASE #${index + 1}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        if (cases.size > 1) {
                            IconButton(
                                onClick = {
                                    val newList = cases.toMutableList()
                                    newList.removeAt(index)
                                    onCasesChange(newList)
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Delete,
                                    contentDescription = "Remove Case",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = itemCase.csNo,
                            onValueChange = { newVal ->
                                val newList = cases.toMutableList()
                                newList[index] = itemCase.copy(csNo = newVal)
                                onCasesChange(newList)
                            },
                            label = { Text("C.S. No.", fontSize = 10.sp) },
                            placeholder = { Text("e.g. 71/2025", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = itemCase.firNo,
                            onValueChange = { newVal ->
                                val newList = cases.toMutableList()
                                newList[index] = itemCase.copy(firNo = newVal)
                                onCasesChange(newList)
                            },
                            label = { Text("F.I.R No.", fontSize = 10.sp) },
                            placeholder = { Text("e.g. 195/2025", fontSize = 11.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = itemCase.policeStation,
                            onValueChange = { newVal ->
                                val newList = cases.toMutableList()
                                val autoDistrict = com.example.utils.RajasthanPoliceHelper.getDistrict(newVal)
                                val finalDistrict = if (!autoDistrict.isNullOrBlank() && itemCase.district.isBlank()) autoDistrict else itemCase.district
                                newList[index] = itemCase.copy(
                                    policeStation = newVal,
                                    district = finalDistrict
                                )
                                onCasesChange(newList)
                            },
                            label = { Text("Police Station", fontSize = 10.sp) },
                            placeholder = { Text("e.g. Udaipurwati", fontSize = 11.sp) },
                            modifier = Modifier.weight(1.2f),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = itemCase.district,
                            onValueChange = { newVal ->
                                val newList = cases.toMutableList()
                                newList[index] = itemCase.copy(district = newVal)
                                onCasesChange(newList)
                            },
                            label = { Text("District", fontSize = 10.sp) },
                            placeholder = { Text("e.g. Jhunjhunu", fontSize = 11.sp) },
                            modifier = Modifier.weight(0.8f),
                            singleLine = true
                        )
                    }
                }
            }
        }

        OutlinedButton(
            onClick = {
                onCasesChange(cases + FormCase())
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            contentPadding = PaddingValues(vertical = 6.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add More Cases", modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add More Cases in this Journey", fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AddEntryTab(
    viewModel: TourViewModel,
    ocrLoading: Boolean,
    ocrError: String?,
    ocrResult: TourEntryOcrResult?,
    dateVal: String,
    onDateChange: (String) -> Unit,
    arrDateVal: String,
    onArrDateChange: (String) -> Unit,
    depVal: String,
    onDepChange: (String) -> Unit,
    arrVal: String,
    onArrChange: (String) -> Unit,
    modeVal: String,
    onModeChange: (String) -> Unit,
    distVal: String,
    onDistChange: (String) -> Unit,
    casesList: List<FormCase>,
    onCasesChange: (List<FormCase>) -> Unit,
    repDateVal: String,
    onRepDateChange: (String) -> Unit,
    remarksVal: String,
    onRemarksChange: (String) -> Unit,
    isRoundTripManualVal: Boolean,
    onRoundTripManualChange: (Boolean) -> Unit,
    onLaunchPicker: () -> Unit,
    onLaunchMultiplePicker: () -> Unit,
    onClearOcr: () -> Unit,
    onSave: () -> Unit
) {
    val context = LocalContext.current
    val uploadItems by viewModel.uploadItems.collectAsState()
    val profile by viewModel.profile.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Multi-File Upload Controller Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AddCircle,
                            contentDescription = "Import",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Unlimited Batch Upload & AI Extraction",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Text(
                        "Upload unlimited requisition letters, FSL reports, or vehicle logbook pages in batch. Gemini OCR will automatically extract all fields and auto-fill them into your diary entries!",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = onLaunchMultiplePicker,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Add Files", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Batch Upload (Unlimited)", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                viewModel.injectMockUploadItem()
                                Toast.makeText(context, "Injected fully-editable Sikar CS 129 mock document!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            modifier = Modifier.weight(0.8f)
                        ) {
                            Icon(Icons.Filled.Star, contentDescription = "Test Mock", modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Test Mock", fontSize = 12.sp)
                        }
                    }
                }
            }
        }

        // Active Review List header & controls
        if (uploadItems.isNotEmpty()) {
            item {
                val completedCount = uploadItems.count { !it.isLoading && it.error == null && it.date.isNotBlank() && it.policeStation.isNotBlank() }
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Pending Review Logs",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                "${uploadItems.size} file(s) in queue ($completedCount ready to save)",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { viewModel.clearUploadQueue() }) {
                                Text("Clear All", fontSize = 12.sp)
                            }
                            if (completedCount > 0) {
                                Button(
                                    onClick = {
                                        val itemsToSave = uploadItems.filter { !it.isLoading && it.error == null && it.date.isNotBlank() && it.policeStation.isNotBlank() }
                                        itemsToSave.forEach { item ->
                                            viewModel.saveUploadItemToDiary(item.id, isRoundTrip = item.isRoundTrip) { }
                                        }
                                        Toast.makeText(context, "Saved $completedCount journey logs successfully!", Toast.LENGTH_LONG).show()
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(34.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp)
                                ) {
                                    Text("Save All ($completedCount)", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Display individual editable cards for each active upload item
        items(
            items = uploadItems,
            key = { it.id }
        ) { item ->
            UploadedItemCard(
                item = item,
                onUpdate = { viewModel.updateUploadItem(it) },
                onRemove = { viewModel.removeUploadItem(item.id) },
                onSave = { roundTrip ->
                    viewModel.saveUploadItemToDiary(item.id, isRoundTrip = roundTrip) { success ->
                        if (success) {
                            Toast.makeText(context, "Saved journey for ${item.policeStation} successfully!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }

        // Split Divider indicating manual entry fallback
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Divider(modifier = Modifier.weight(1f), color = Color(0xFFCAC4D0).copy(alpha = 0.4f))
                Text(
                    "OR LOG TRIP MANUALLY",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
                Divider(modifier = Modifier.weight(1f), color = Color(0xFFCAC4D0).copy(alpha = 0.4f))
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.DateRange, 
                            contentDescription = "Dates Icon", 
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "1. TIMING & DATES",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // Departure Date & Arrival Date row
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val calendar = Calendar.getInstance()
                        val dateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
                            val fMonth = String.format(Locale.getDefault(), "%02d", month + 1)
                            val fDay = String.format(Locale.getDefault(), "%02d", dayOfMonth)
                            onDateChange("$year-$fMonth-$fDay")
                        }
                        val arrDateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
                            val fMonth = String.format(Locale.getDefault(), "%02d", month + 1)
                            val fDay = String.format(Locale.getDefault(), "%02d", dayOfMonth)
                            onArrDateChange("$year-$fMonth-$fDay")
                        }

                        OutlinedTextField(
                            value = dateVal,
                            onValueChange = onDateChange,
                            label = { Text("Dep. Date") },
                            placeholder = { Text("YYYY-MM-DD") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    DatePickerDialog(context, dateSetListener, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                                }) {
                                    Icon(Icons.Filled.DateRange, contentDescription = "Pick Date")
                                }
                            }
                        )

                        OutlinedTextField(
                            value = arrDateVal,
                            onValueChange = onArrDateChange,
                            label = { Text("Arr. Date") },
                            placeholder = { Text("YYYY-MM-DD") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    DatePickerDialog(context, arrDateSetListener, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                                }) {
                                    Icon(Icons.Filled.DateRange, contentDescription = "Pick Arrival Date")
                                }
                            }
                        )
                    }

                    // Report date row
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            val calendar = Calendar.getInstance()
                            val repDateSetListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
                                val fMonth = String.format(Locale.getDefault(), "%02d", month + 1)
                                val fDay = String.format(Locale.getDefault(), "%02d", dayOfMonth)
                                onRepDateChange("$year-$fMonth-$fDay")
                            }

                            OutlinedTextField(
                                value = repDateVal,
                                onValueChange = onRepDateChange,
                                label = { Text("Rep. Date", fontSize = 11.sp, maxLines = 1) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = {
                                        DatePickerDialog(context, repDateSetListener, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                                    }) {
                                        Icon(Icons.Filled.DateRange, contentDescription = "Pick Report Date", modifier = Modifier.size(16.dp))
                                    }
                                }
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // "Same" Report Date option (equals date of travelling)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (repDateVal == dateVal && dateVal.isNotBlank()) 
                                                MaterialTheme.colorScheme.primaryContainer 
                                            else 
                                                MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable { onRepDateChange(dateVal) }
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Same", 
                                        fontSize = 9.sp, 
                                        fontWeight = FontWeight.Bold,
                                        color = if (repDateVal == dateVal && dateVal.isNotBlank()) 
                                            MaterialTheme.colorScheme.onPrimaryContainer 
                                        else 
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                // "Pending" Report Date option
                                Box(
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (repDateVal == "Pending") 
                                                Color(0xFFFFD8E4) 
                                            else 
                                                MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .clickable { onRepDateChange("Pending") }
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Pending", 
                                        fontSize = 9.sp, 
                                        fontWeight = FontWeight.Bold,
                                        color = if (repDateVal == "Pending") 
                                            Color(0xFF31111D) 
                                        else 
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Departure & Arrival Times row
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        val depTimeListener = TimePickerDialog.OnTimeSetListener { _, hr, min ->
                            onDepChange(String.format(Locale.getDefault(), "%02d:%02d", hr, min))
                        }
                        val arrTimeListener = TimePickerDialog.OnTimeSetListener { _, hr, min ->
                            onArrChange(String.format(Locale.getDefault(), "%02d:%02d", hr, min))
                        }

                        OutlinedTextField(
                            value = depVal,
                            onValueChange = onDepChange,
                            label = { Text("Dep (HH:MM)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    TimePickerDialog(context, depTimeListener, 10, 0, true).show()
                                }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Pick Time")
                                }
                            }
                        )

                        OutlinedTextField(
                            value = arrVal,
                            onValueChange = onArrChange,
                            label = { Text("Arr (HH:MM)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            trailingIcon = {
                                IconButton(onClick = {
                                    TimePickerDialog(context, arrTimeListener, 16, 0, true).show()
                                }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Pick Time")
                                }
                            }
                        )
                    }
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    JourneyCasesSection(
                        cases = casesList,
                        onCasesChange = onCasesChange,
                        title = "2. POLICE STATION & CASE"
                    )
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.Star, 
                            contentDescription = "Travel Icon", 
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "3. TRAVEL MODE & METRICS",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            letterSpacing = 0.5.sp
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = modeVal,
                                onValueChange = onModeChange,
                                label = { Text("Travel Mode") },
                                placeholder = { Text("e.g. Government Vehicle") },
                                modifier = Modifier.weight(1.2f),
                                singleLine = true
                            )

                            OutlinedTextField(
                                value = distVal,
                                onValueChange = onDistChange,
                                label = { Text("Distance (km)") },
                                placeholder = { Text("e.g. 64.0") },
                                modifier = Modifier.weight(0.8f),
                                singleLine = true
                            )
                        }

                        // Assist chips row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val travelModes = listOf(
                                "Government Vehicle",
                                "Bus",
                                "Train",
                                "Private Vehicle"
                            )
                            travelModes.forEach { mode ->
                                TravelModeChip(
                                    text = mode,
                                    isSelected = (modeVal == mode),
                                    onClick = { onModeChange(mode) }
                                )
                            }
                        }

                        // Distance entry method helper (Manual vs Google Map KM)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        ) {
                            Text(
                                "KM Method:", 
                                fontSize = 11.sp, 
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            
                            AssistChip(
                                onClick = { /* User types in the input above */ },
                                label = { Text("Manual", fontSize = 10.sp) },
                                leadingIcon = { Icon(Icons.Filled.Edit, "manual icon", modifier = Modifier.size(12.dp)) },
                                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                            )
                            
                            val stations = casesList.map { it.policeStation }.filter { it.isNotBlank() }
                            AssistChip(
                                onClick = {
                                    val hq = if (profile.posting.isNotBlank()) profile.posting else "HQ"
                                    val gDist = com.example.utils.RajasthanPoliceHelper.getGoogleMapsDistance(hq, stations)
                                    if (gDist > 0.0) {
                                        onDistChange(String.format(Locale.US, "%.1f", gDist))
                                        Toast.makeText(context, "Calculated via Google Maps ($hq ➔ ${stations.joinToString(" ➔ ") { "PS $it" }} ➔ $hq): $gDist km", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Please enter visited Police Station(s) to fetch Google Maps distance!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                label = { Text("As per Google Map", fontSize = 10.sp) },
                                leadingIcon = { Icon(Icons.Filled.Place, "maps icon", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary) },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = remarksVal,
                onValueChange = onRemarksChange,
                label = { Text("Extra Remarks & Travel Details") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Auto 40 km/h Round-Trip Split", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    Text("Logs outward + return trip segments automatically.", fontSize = 10.sp, color = Color.Gray)
                }
                Switch(
                    checked = isRoundTripManualVal,
                    onCheckedChange = onRoundTripManualChange
                )
            }
        }

        item {
            Spacer(modifier = Modifier.height(4.dp))
            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Icon(Icons.Filled.Done, contentDescription = "Save Journey")
                Spacer(modifier = Modifier.width(6.dp))
                Text("Save Journey Entry", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ProfileTab(
    profile: EmployeeProfile,
    viewModel: TourViewModel,
    onSaveProfile: (String, String, String, String, String, Int, String) -> Unit
) {
    val context = LocalContext.current
    var nameState by remember(profile) { mutableStateOf(profile.name) }
    var designationState by remember(profile) { mutableStateOf(profile.designation) }
    var postingState by remember(profile) { mutableStateOf(profile.posting) }
    var geminiApiKeyState by remember(profile) { mutableStateOf(profile.geminiApiKey) }
    var backupEmailState by remember(profile) { mutableStateOf(profile.cloudBackupEmail) }
    var basicSalaryState by remember(profile) { mutableStateOf(profile.basicSalary.toString()) }
    var taCategoryState by remember(profile) { mutableStateOf(profile.taCategory) }

    val fileImportLauncher = rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importBackupFromJson(context, uri) { success ->
                if (success) {
                    Toast.makeText(context, "Backup Restored Successfully!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Failed to Restore Backup.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Employee Profile details",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "These details are used as headers throughout generated PDFs to match statutory forensic department layout guidelines.",
            fontSize = 11.sp,
            color = Color.Gray
        )

        OutlinedTextField(
            value = nameState,
            onValueChange = { nameState = it },
            label = { Text("Employee Full Name") },
            leadingIcon = { Icon(Icons.Filled.AccountBox, contentDescription = "Name") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = designationState,
            onValueChange = { designationState = it },
            label = { Text("Designation") },
            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = "Designation") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. Sr. Scientific Assistant") }
        )

        OutlinedTextField(
            value = postingState,
            onValueChange = { postingState = it },
            label = { Text("Posting Place") },
            leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = "Posting") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. Mobile Forensic Unit Jodhpur") }
        )

        OutlinedTextField(
            value = basicSalaryState,
            onValueChange = {
                basicSalaryState = it
                val sal = it.trim().toIntOrNull() ?: 38000
                taCategoryState = com.example.utils.RajasthanTaRules.getCategoryBySalary(sal)
            },
            label = { Text("Basic Salary (Pay Matrix)") },
            leadingIcon = { Icon(Icons.Filled.Star, contentDescription = "Basic Salary") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
            ),
            placeholder = { Text("e.g. 38000") }
        )

        // Category selection dropdown
        var categoryExpanded by remember { mutableStateOf(false) }
        Box {
            OutlinedTextField(
                value = "Category $taCategoryState (DA Rate: ₹${com.example.utils.RajasthanTaRules.getFullDaRate(taCategoryState).toInt()}/day)",
                onValueChange = {},
                readOnly = true,
                label = { Text("Rajasthan TA Rules Category") },
                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = "Category") },
                trailingIcon = { IconButton(onClick = { categoryExpanded = true }) { Icon(Icons.Filled.ArrowDropDown, "Select") } },
                modifier = Modifier.fillMaxWidth().clickable { categoryExpanded = true }
            )
            DropdownMenu(
                expanded = categoryExpanded,
                onDismissRequest = { categoryExpanded = false }
            ) {
                listOf("A", "B", "C", "D", "E").forEach { cat ->
                    DropdownMenuItem(
                        text = { Text("Category $cat (Full DA Rate: ₹${com.example.utils.RajasthanTaRules.getFullDaRate(cat).toInt()})") },
                        onClick = {
                            taCategoryState = cat
                            categoryExpanded = false
                        }
                    )
                }
            }
        }

        // Mock Representation of output Signature block
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text("PDF Signature Block Preview:", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(20.dp))
                Divider(color = Color.Gray, thickness = 0.5.dp, modifier = Modifier.width(120.dp).align(Alignment.End))
                Text("Signature", fontSize = 11.sp, modifier = Modifier.align(Alignment.End).padding(top = 4.dp))
                Text("Designation: ${designationState.ifBlank { "N/A" }}", fontSize = 10.sp, modifier = Modifier.align(Alignment.End))
                Text("Mobile Forensic Unit: ${postingState.ifBlank { "N/A" }}", fontSize = 10.sp, modifier = Modifier.align(Alignment.End))
            }
        }

        Divider(color = Color(0xFFCAC4D0).copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 4.dp))

        Text(
            "Cloud Sync & Backup",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "Back up and sync your forensic travel indices and crime scene portfolios to Google Cloud/Gmail securely. Works offline by gathering files and online by loading/saving from Drive/Attachments.",
            fontSize = 11.sp,
            color = Color.Gray
        )

        OutlinedTextField(
            value = backupEmailState,
            onValueChange = { backupEmailState = it },
            label = { Text("Backup Gmail Address") },
            leadingIcon = { Icon(Icons.Filled.Email, contentDescription = "Email") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. yourname@gmail.com") },
            singleLine = true
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Button(
                onClick = {
                    if (backupEmailState.isBlank() || !backupEmailState.contains("@")) {
                        Toast.makeText(context, "Please enter a valid backup Gmail address first.", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.exportBackupToJson(context, backupEmailState)
                    }
                },
                modifier = Modifier.weight(1.2f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Filled.Share, contentDescription = "Export Backup", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Backup via Gmail", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }

            Button(
                onClick = {
                    fileImportLauncher.launch(arrayOf("application/json", "*/*"))
                },
                modifier = Modifier.weight(0.8f),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Filled.ArrowUpward, contentDescription = "Import Backup", modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Restore", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }

        Divider(color = Color(0xFFCAC4D0).copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 4.dp))

        Text(
            "API Keys & OCR settings",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "If you receive a forbidden (HTTP 403 Forbidden) warning or quota issues from Google AI Studio, provide your private key below to run OCR commands independently.",
            fontSize = 11.sp,
            color = Color.Gray
        )

        OutlinedTextField(
            value = geminiApiKeyState,
            onValueChange = { geminiApiKeyState = it },
            label = { Text("Custom Gemini API Key") },
            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = "API Key") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("AI Studio Gemini API Key") },
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val salInt = basicSalaryState.toIntOrNull() ?: 38000
                onSaveProfile(nameState, designationState, postingState, geminiApiKeyState, backupEmailState, salInt, taCategoryState)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Filled.Done, contentDescription = "Confirm profile changes")
            Spacer(modifier = Modifier.width(6.dp))
            Text("Update settings", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }

        OutlinedButton(
            onClick = {
                nameState = ""
                designationState = ""
                postingState = ""
                geminiApiKeyState = ""
                backupEmailState = ""
                basicSalaryState = "38000"
                taCategoryState = "D"
                onSaveProfile("", "", "", "", "", 38000, "D")
                Toast.makeText(context, "Employee Profile details cleared successfully!", Toast.LENGTH_SHORT).show()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(Icons.Filled.Delete, contentDescription = "Clear Profile Info", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Clear Profile Info", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
    }
}

@Composable
fun PdfExportDialog(
    selectedMonth: String,
    initialReportType: Int = 0,
    onDismiss: () -> Unit,
    onConfirmPdf: (isLegal: Boolean, reportType: Int) -> Unit,
    onConfirmWord: () -> Unit
) {
    var selectedLegalSize by remember { mutableStateOf(false) } // Default Letter Size (false), Legal Size (true)
    var activeExportTab by remember { mutableStateOf(0) } // 0 = PDF, 1 = Word
    var reportType by remember { mutableStateOf(initialReportType) } // 0 = Tour Diary, 1 = Rajasthan TA Bill

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Export Monthly Diary Details", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Text("Select report parameters for $selectedMonth", fontSize = 12.sp, color = Color.Gray)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Formatting Tab switcher
                TabRow(
                    selectedTabIndex = activeExportTab,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                    containerColor = Color(0xFFF3EDF7)
                ) {
                    Tab(
                        selected = activeExportTab == 0,
                        onClick = { activeExportTab = 0 },
                        text = { Text("PDF Document", fontWeight = FontWeight.SemiBold, fontSize = 10.sp) },
                        icon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = "PDF format", modifier = Modifier.size(16.dp)) }
                    )
                    Tab(
                        selected = activeExportTab == 1,
                        onClick = { activeExportTab = 1 },
                        text = { Text("Word .doc (Editable)", fontWeight = FontWeight.SemiBold, fontSize = 10.sp) },
                        icon = { Icon(Icons.Filled.Description, contentDescription = "Word format", modifier = Modifier.size(16.dp)) }
                    )
                }

                if (activeExportTab == 0) {
                    // PDF Form Options
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Choose PDF Document Type:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { reportType = 0 },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (reportType == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (reportType == 0) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Filled.List, contentDescription = "Tour Diary", modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Tour Diary", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text("Official (>=29km)", fontSize = 9.sp, color = Color.Gray)
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { reportType = 1 },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (reportType == 1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (reportType == 1) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Filled.AccountBalanceWallet, contentDescription = "Road TA Bill", modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Rajasthan TA Bill", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text("Full Claim Ledger", fontSize = 9.sp, color = Color.Gray)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = "Choose statutory paper size setup:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedLegalSize = false },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (!selectedLegalSize) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (!selectedLegalSize) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Filled.InsertDriveFile, contentDescription = "Letter", modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Letter", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text("8.5\" x 11\"", fontSize = 9.sp, color = Color.Gray)
                                }
                            }

                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { selectedLegalSize = true },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (selectedLegalSize) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (selectedLegalSize) MaterialTheme.colorScheme.primary else Color.LightGray.copy(alpha = 0.5f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Filled.InsertDriveFile, contentDescription = "Legal", modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Legal (Long)", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text("8.5\" x 14\"", fontSize = 9.sp, color = Color.Gray)
                                }
                            }
                        }

                        Text(
                            text = "PDF exports prioritize printing on a single page using a compressed layout vector grid.",
                            fontSize = 10.sp,
                            color = Color.Gray,
                            lineHeight = 13.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                } else {
                    // Word Form Options
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Editable Word Document (.doc)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Generates a professional landscape structured document. The resulting file maintains full styling and is completely editable inside Office apps like Word, Google Docs, WPS, or LibreOffice.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 14.sp
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "Ready", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            Text(
                                text = "Full table formatting & employee headers included.",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (activeExportTab == 0) {
                        onConfirmPdf(selectedLegalSize, reportType)
                    } else {
                        onConfirmWord()
                    }
                }
            ) {
                Icon(Icons.Filled.Share, contentDescription = "Export icon", modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Generate & Share")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ProfileEditDialog(
    profile: EmployeeProfile,
    onDismiss: () -> Unit,
    onSaveProfile: (String, String, String, Int, String) -> Unit
) {
    var nameState by remember(profile) { mutableStateOf(profile.name) }
    var designationState by remember(profile) { mutableStateOf(profile.designation) }
    var postingState by remember(profile) { mutableStateOf(profile.posting) }
    var basicSalaryState by remember(profile) { mutableStateOf(profile.basicSalary.toString()) }
    var taCategoryState by remember(profile) { mutableStateOf(profile.taCategory) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Edit Employee Profile", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = nameState,
                    onValueChange = { nameState = it },
                    label = { Text("Employee Full Name") },
                    leadingIcon = { Icon(Icons.Filled.AccountBox, contentDescription = "Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = designationState,
                    onValueChange = { designationState = it },
                    label = { Text("Designation") },
                    leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = "Designation") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. Sr. Scientific Assistant") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = postingState,
                    onValueChange = { postingState = it },
                    label = { Text("Posting Place") },
                    leadingIcon = { Icon(Icons.Filled.LocationOn, contentDescription = "Posting") },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("e.g. Mobile Forensic Unit Jodhpur") },
                    singleLine = true
                )

                OutlinedTextField(
                    value = basicSalaryState,
                    onValueChange = {
                        basicSalaryState = it
                        val sal = it.trim().toIntOrNull() ?: 38000
                        taCategoryState = com.example.utils.RajasthanTaRules.getCategoryBySalary(sal)
                    },
                    label = { Text("Basic Salary") },
                    leadingIcon = { Icon(Icons.Filled.Star, contentDescription = "Basic Salary") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                    ),
                    singleLine = true
                )

                var categoryExpanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedTextField(
                        value = "Category $taCategoryState (DA: ₹${com.example.utils.RajasthanTaRules.getFullDaRate(taCategoryState).toInt()}/d)",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Rajasthan TA Category") },
                        leadingIcon = { Icon(Icons.Filled.Info, contentDescription = "Category") },
                        trailingIcon = { IconButton(onClick = { categoryExpanded = true }) { Icon(Icons.Filled.ArrowDropDown, "Select") } },
                        modifier = Modifier.fillMaxWidth().clickable { categoryExpanded = true }
                    )
                    DropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        listOf("A", "B", "C", "D", "E").forEach { cat ->
                            DropdownMenuItem(
                                text = { Text("Category $cat (Full DA: ₹${com.example.utils.RajasthanTaRules.getFullDaRate(cat).toInt()})") },
                                onClick = {
                                    taCategoryState = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val salInt = basicSalaryState.toIntOrNull() ?: 38000
                    onSaveProfile(nameState, designationState, postingState, salInt, taCategoryState)
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun TourEntryEditDialog(
    entry: TourEntry,
    profile: EmployeeProfile,
    onDismiss: () -> Unit,
    onSave: (TourEntry) -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var dateState by remember(entry) { mutableStateOf(entry.date) }
    var depTimeState by remember(entry) { mutableStateOf(entry.depTime) }
    var arrDateState by remember(entry) { mutableStateOf(entry.arrDate.ifBlank { entry.date }) }
    var arrTimeState by remember(entry) { mutableStateOf(entry.arrTime) }
    var travelModeState by remember(entry) { mutableStateOf(entry.travelMode) }
    var distanceState by remember(entry) { mutableStateOf(entry.distance.toString()) }
    var editCasesState by remember(entry) {
        mutableStateOf(parseCases(entry.csNumber, entry.firNumber, entry.policeStation, entry.district))
    }
    var reportDateState by remember(entry) { mutableStateOf(entry.reportDate) }
    var remarksState by remember(entry) { mutableStateOf(entry.remarks) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (entry.id == 0L) "Insert Copied Journey (Editable)" else "Modify Journey Entry", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                val calendar = Calendar.getInstance()

                // Date Set Listeners
                val depDateListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
                    val fMonth = String.format(Locale.getDefault(), "%02d", month + 1)
                    val fDay = String.format(Locale.getDefault(), "%02d", dayOfMonth)
                    val selected = "$year-$fMonth-$fDay"
                    dateState = selected
                    if (arrDateState.isBlank() || arrDateState == entry.date) {
                        arrDateState = selected
                    }
                }
                val arrDateListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
                    val fMonth = String.format(Locale.getDefault(), "%02d", month + 1)
                    val fDay = String.format(Locale.getDefault(), "%02d", dayOfMonth)
                    arrDateState = "$year-$fMonth-$fDay"
                }
                val repDateListener = DatePickerDialog.OnDateSetListener { _, year, month, dayOfMonth ->
                    val fMonth = String.format(Locale.getDefault(), "%02d", month + 1)
                    val fDay = String.format(Locale.getDefault(), "%02d", dayOfMonth)
                    reportDateState = "$year-$fMonth-$fDay"
                }

                // Time Set Listeners
                val depTimeListener = TimePickerDialog.OnTimeSetListener { _, hr, min ->
                    depTimeState = String.format(Locale.getDefault(), "%02d:%02d", hr, min)
                }
                val arrTimeListener = TimePickerDialog.OnTimeSetListener { _, hr, min ->
                    arrTimeState = String.format(Locale.getDefault(), "%02d:%02d", hr, min)
                }

                // Section 1: Timings & Dates
                Text("1. TIMING & DATES", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dateState,
                        onValueChange = { dateState = it },
                        label = { Text("Dep. Date", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                DatePickerDialog(context, depDateListener, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                            }) {
                                Icon(Icons.Filled.DateRange, contentDescription = "Pick Departure Date")
                            }
                        }
                    )
                    OutlinedTextField(
                        value = depTimeState,
                        onValueChange = { depTimeState = it },
                        label = { Text("Dep. Time", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                TimePickerDialog(context, depTimeListener, 10, 0, true).show()
                            }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Pick Departure Time")
                            }
                        }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = arrDateState,
                        onValueChange = { arrDateState = it },
                        label = { Text("Arr. Date", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                DatePickerDialog(context, arrDateListener, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                            }) {
                                Icon(Icons.Filled.DateRange, contentDescription = "Pick Arrival Date")
                            }
                        }
                    )
                    OutlinedTextField(
                        value = arrTimeState,
                        onValueChange = { arrTimeState = it },
                        label = { Text("Arr. Time", fontSize = 11.sp) },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        trailingIcon = {
                            IconButton(onClick = {
                                TimePickerDialog(context, arrTimeListener, 16, 0, true).show()
                            }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Pick Arrival Time")
                            }
                        }
                    )
                }

                OutlinedTextField(
                    value = reportDateState,
                    onValueChange = { reportDateState = it },
                    label = { Text("Rep. Date", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    trailingIcon = {
                        IconButton(onClick = {
                            DatePickerDialog(context, repDateListener, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH), calendar.get(Calendar.DAY_OF_MONTH)).show()
                        }) {
                            Icon(Icons.Filled.DateRange, contentDescription = "Pick Report Date")
                        }
                    }
                )

                Divider(color = Color.LightGray, thickness = 0.5.dp)

                // Section 2: Case info
                JourneyCasesSection(
                    cases = editCasesState,
                    onCasesChange = { editCasesState = it },
                    title = "2. POLICE STATION & CASE DETAILS"
                )

                Divider(color = Color.LightGray, thickness = 0.5.dp)

                // Section 3: Travel Parameters
                Text("3. VEHICLE & TRIP DETAILS", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = travelModeState,
                            onValueChange = { travelModeState = it },
                            label = { Text("Mode", fontSize = 11.sp) },
                            modifier = Modifier.weight(1.2f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = distanceState,
                            onValueChange = { distanceState = it },
                            label = { Text("Dist (km)", fontSize = 11.sp) },
                            modifier = Modifier.weight(0.8f),
                            singleLine = true
                        )
                    }

                    // Assist chips row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val travelModes = listOf(
                            "Government Vehicle",
                            "Bus",
                            "Train",
                            "Private Vehicle"
                        )
                        travelModes.forEach { mode ->
                            TravelModeChip(
                                text = mode,
                                isSelected = (travelModeState == mode),
                                onClick = { travelModeState = mode }
                            )
                        }
                    }

                    // Distance entry method helper (Manual vs Google Map KM)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            "KM Method:", 
                            fontSize = 11.sp, 
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        
                        AssistChip(
                            onClick = { /* User types in the input above */ },
                            label = { Text("Manual", fontSize = 10.sp) },
                            leadingIcon = { Icon(Icons.Filled.Edit, "manual icon", modifier = Modifier.size(12.dp)) },
                            colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f))
                        )
                        
                        val stations = editCasesState.map { it.policeStation }.filter { it.isNotBlank() }
                        AssistChip(
                            onClick = {
                                val hq = if (profile.posting.isNotBlank()) profile.posting else "HQ"
                                val gDist = com.example.utils.RajasthanPoliceHelper.getGoogleMapsDistance(hq, stations)
                                if (gDist > 0.0) {
                                    distanceState = String.format(Locale.US, "%.1f", gDist)
                                    Toast.makeText(context, "Calculated via Google Maps ($hq ➔ ${stations.joinToString(" ➔ ") { "PS $it" }} ➔ $hq): $gDist km", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Please enter visited Police Station(s) to fetch Google Maps distance!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            label = { Text("As per Google Map", fontSize = 10.sp) },
                            leadingIcon = { Icon(Icons.Filled.Place, "maps icon", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                labelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = remarksState,
                    onValueChange = { remarksState = it },
                    label = { Text("Remarks (Optional)", fontSize = 11.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val doubleDist = distanceState.toDoubleOrNull() ?: 0.0
                    val serialized = serializeCases(editCasesState)
                    val updated = entry.copy(
                        date = dateState,
                        depTime = depTimeState,
                        arrDate = if (arrDateState.isNotBlank()) arrDateState else dateState,
                        arrTime = arrTimeState,
                        travelMode = travelModeState,
                        distance = doubleDist,
                        csNumber = serialized["csNumber"] ?: "",
                        firNumber = serialized["firNumber"] ?: "",
                        policeStation = serialized["policeStation"] ?: "",
                        district = serialized["district"] ?: "",
                        reportDate = reportDateState,
                        remarks = remarksState
                    )
                    onSave(updated)
                    onDismiss()
                }
            ) {
                Text(if (entry.id == 0L) "Insert" else "Save")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (entry.id != 0L) {
                    TextButton(
                        onClick = {
                            onDelete()
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete Journey Entry icon", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun TravelModeChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primaryContainer 
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
            .clickable { onClick() }
            .border(
                1.dp, 
                if (isSelected) MaterialTheme.colorScheme.primary 
                else Color.Transparent, 
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer 
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Native Android Sharing Intent Callers
 */
fun sharePdfFile(context: Context, file: File) {
    val authority = "${context.packageName}.fileprovider"
    try {
        val fileUri: Uri = FileProvider.getUriForFile(context, authority, file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Monthly Tour Diary"))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Sharing failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

fun shareDocFile(context: Context, file: File) {
    val authority = "${context.packageName}.fileprovider"
    try {
        val fileUri: Uri = FileProvider.getUriForFile(context, authority, file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "application/msword"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Monthly Tour Diary (Word)"))
    } catch (e: Exception) {
        e.printStackTrace()
        Toast.makeText(context, "Sharing failed: ${e.message}", Toast.LENGTH_LONG).show()
    }
}

// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CasesTab(viewModel: TourViewModel) {
    val context = LocalContext.current
    val cases by viewModel.filteredCases.collectAsState()
    val searchQuery by viewModel.caseSearchQuery.collectAsState()
    val filterPS by viewModel.caseFilterPS.collectAsState()
    val filterDistrict by viewModel.caseFilterDistrict.collectAsState()
    val filterDate by viewModel.caseFilterDate.collectAsState()
    val filterTag by viewModel.caseFilterTag.collectAsState()

    val caseOcrResult by viewModel.caseOcrResult.collectAsState()
    val caseOcrLoading by viewModel.caseOcrLoading.collectAsState()
    val caseOcrError by viewModel.caseOcrError.collectAsState()

    var showAddEditDialog by remember { mutableStateOf(false) }
    var caseToEdit by remember { mutableStateOf<CaseEntry?>(null) }
    var showAdvancedFilters by remember { mutableStateOf(false) }

    val popularTags = listOf("Homicide", "Theft", "Arson", "Cyberspace", "Forensic", "Narcotics")

    // Speech-to-text Launcher for Global Voice Commands & Quick Search dictation
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val text = matches?.firstOrNull() ?: ""
            if (text.isNotBlank()) {
                val lower = text.lowercase()
                if (lower.startsWith("search for ") || lower.startsWith("find ")) {
                    val query = text.substringAfter("for ").substringAfter("find ").trim()
                    viewModel.setCaseSearchQuery(query)
                    Toast.makeText(context, "Voice command: Searching for '$query'", Toast.LENGTH_SHORT).show()
                } else if (lower.contains("clear file") || lower.contains("clear filter") || lower.contains("reset")) {
                    viewModel.resetFilters()
                    Toast.makeText(context, "Voice command: Resetting all filters", Toast.LENGTH_SHORT).show()
                } else if (lower.contains("add case") || lower.contains("new case") || lower.contains("register case")) {
                    caseToEdit = null
                    showAddEditDialog = true
                    Toast.makeText(context, "Voice command: Opening Register Case Form", Toast.LENGTH_SHORT).show()
                } else {
                    // Default fallback search
                    viewModel.setCaseSearchQuery(text)
                }
            }
        }
    }

    // PDF Register / Document Scanner Picker Launcher
    val registerPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.parseCaseDocument(context, uri)
        }
    }

    // Prefill form trigger when Case Register scanning completes
    LaunchedEffect(caseOcrResult) {
        if (caseOcrResult != null) {
            caseToEdit = null
            showAddEditDialog = true
            Toast.makeText(context, "Register entry detected. Review and verify the loaded details.", Toast.LENGTH_LONG).show()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Header Row: Title + Upload + Add Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Case Files",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D1B20)
                    )
                    Text(
                        "Forensic Investigation Case Records",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF49454F)
                    )
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // PDF / Register Scanner Button
                    Button(
                        onClick = {
                            registerPickerLauncher.launch("application/pdf")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF21005D)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Star, contentDescription = "Scan", modifier = Modifier.size(16.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AI Scan Register", fontSize = 11.sp, color = Color.White)
                    }

                    Button(
                        onClick = {
                            viewModel.clearCaseOcrState()
                            caseToEdit = null
                            showAddEditDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Add", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Case", fontSize = 11.sp)
                    }
                }
            }

            // Search Bar, Voice Control & Filter Toggle Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setCaseSearchQuery(it) },
                    placeholder = { Text("Search case, FIR, text...", fontSize = 14.sp) },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", modifier = Modifier.size(20.dp)) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setCaseSearchQuery("") }) {
                                Icon(Icons.Filled.Clear, contentDescription = "Clear", modifier = Modifier.size(18.dp))
                            }
                        }
                    },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF6750A4),
                        unfocusedBorderColor = Color(0xFFCAC4D0)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                // Voice Dictation Commands Button
                IconButton(
                    onClick = {
                        try {
                            val intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                                putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Speak commands like 'search for homicide', 'add case', 'clear filters'")
                            }
                            speechRecognizerLauncher.launch(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Voice command recognition not supported on this device", Toast.LENGTH_LONG).show()
                        }
                    },
                    modifier = Modifier
                        .background(Color(0xFFEADDFF), RoundedCornerShape(12.dp))
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = "Voice Assistant",
                        tint = Color(0xFF21005D),
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Filter expander button
                IconButton(
                    onClick = { showAdvancedFilters = !showAdvancedFilters },
                    modifier = Modifier
                        .background(if (showAdvancedFilters) Color(0xFFEADDFF) else Color(0xFFF3EDF7), RoundedCornerShape(12.dp))
                        .size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Settings,
                        contentDescription = "Filters",
                        tint = if (showAdvancedFilters) Color(0xFF21005D) else Color(0xFF49454F),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Advanced Filters Panel
            AnimatedVisibility(
                visible = showAdvancedFilters,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF3EDF7))
                ) {
                    Column(modifier = Modifier.padding(14.dp).fillMaxWidth()) {
                        Text(
                            "Filter Settings",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp,
                            color = Color(0xFF49454F),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Input filters for PS and District
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = filterPS,
                                onValueChange = { viewModel.setCaseFilterPS(it) },
                                label = { Text("PS Filter", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                            )

                            OutlinedTextField(
                                value = filterDistrict,
                                onValueChange = { viewModel.setCaseFilterDistrict(it) },
                                label = { Text("District", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Date and Tag values
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = filterDate,
                                onValueChange = { viewModel.setCaseFilterDate(it) },
                                label = { Text("Date (YYYY-MM-DD)", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1.3f),
                                shape = RoundedCornerShape(8.dp),
                                placeholder = { Text("YYYY-MM-DD", fontSize = 11.sp) },
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                            )

                            OutlinedTextField(
                                value = filterTag,
                                onValueChange = { viewModel.setCaseFilterTag(it) },
                                label = { Text("Tag Keyword", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Suggested Tags row
                        Text(
                            "Popular Tags",
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = Color(0xFF49454F)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            popularTags.forEach { tag ->
                                val isActive = filterTag.trim().lowercase() == tag.lowercase()
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isActive) Color(0xFF6750A4) else Color(0xFFFEF7FF))
                                        .clickable {
                                            viewModel.setCaseFilterTag(if (isActive) "" else tag)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = tag,
                                        color = if (isActive) Color.White else Color(0xFF6750A4),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        // Reset button
                        val hasActiveFilters = searchQuery.isNotEmpty() || filterPS.isNotEmpty() || filterDistrict.isNotEmpty() || filterDate.isNotEmpty() || filterTag.isNotEmpty()
                        if (hasActiveFilters) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { viewModel.resetFilters() },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD8E4), contentColor = Color(0xFF31111D)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .align(Alignment.End)
                                    .height(32.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp)
                            ) {
                                Text("Clear All Filters", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Results totals summary
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Showing ${cases.size} case file(s)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF49454F)
                )

                if (filterTag.isNotBlank() || filterPS.isNotBlank() || filterDistrict.isNotBlank() || filterDate.isNotBlank()) {
                    Text(
                        text = "Filters Active ◉",
                        fontSize = 11.sp,
                        color = Color(0xFF6750A4),
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // LazyColumn listings
            if (cases.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "Empty",
                            tint = Color(0xFFCAC4D0),
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No cases match search criteria.",
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF49454F),
                            fontSize = 15.sp
                        )
                        Text(
                            text = "Try adjusting your advanced search query or register a new case profile to begin organization.",
                            textAlign = TextAlign.Center,
                            fontSize = 12.sp,
                            color = Color(0xFF49454F).copy(alpha = 0.7f)
                        )
                        Button(
                            onClick = {
                                viewModel.resetFilters()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF3EDF7), contentColor = Color(0xFF6750A4)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Reset All Filters", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 72.dp)
                ) {
                    items(cases, key = { it.id }) { case ->
                        CaseEntryCard(
                            case = case,
                            onEdit = {
                                viewModel.clearCaseOcrState()
                                caseToEdit = case
                                showAddEditDialog = true
                            },
                            onDelete = {
                                viewModel.deleteCaseEntry(case)
                                Toast.makeText(context, "Case deleted successfully.", Toast.LENGTH_SHORT).show()
                            },
                            viewModel = viewModel
                        )
                    }
                }
            }
        }
    }

    // AI SCANNING OVERLAY
    if (caseOcrLoading) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Analyzing Case Document...", fontWeight = FontWeight.Bold, fontSize = 16.sp) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = Color(0xFF6750A4))
                    Text(
                        text = "Gemini Flash OCR is extracting dates, FIR details, crime scene numbers, and forensic keywords from your uploaded register sheet.",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = Color(0xFF49454F)
                    )
                }
            },
            confirmButton = {}
        )
    }

    // SCAN ERROR PROMPT
    if (caseOcrError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearCaseOcrState() },
            title = { Text("AI Scan Failed", color = Color(0xFFBA1A1A), fontWeight = FontWeight.Bold) },
            text = { Text(caseOcrError ?: "Unknown parsing error. Please verify file format.", fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearCaseOcrState() }) {
                    Text("Ok", color = Color(0xFF6750A4))
                }
            }
        )
    }

    // Determine the prefilled transient case entry to feed into Dialog
    val prefilledCase = remember(caseToEdit, caseOcrResult) {
        if (caseToEdit != null) {
            caseToEdit
        } else if (caseOcrResult != null) {
            CaseEntry(
                id = 0,
                caseNumber = caseOcrResult?.caseNumber ?: "",
                firNumber = caseOcrResult?.firNumber ?: "",
                policeStation = caseOcrResult?.policeStation ?: "",
                district = caseOcrResult?.district ?: "",
                date = caseOcrResult?.date ?: "",
                tags = caseOcrResult?.tags ?: "",
                notes = caseOcrResult?.notes ?: "",
                investigatingOfficer = caseOcrResult?.investigatingOfficer ?: "",
                status = caseOcrResult?.status ?: "Under Investigation"
            )
        } else {
            null
        }
    }

    if (showAddEditDialog) {
        CaseFormDialog(
            caseToEdit = prefilledCase,
            onDismiss = {
                viewModel.clearCaseOcrState()
                showAddEditDialog = false
            },
            onSave = { cNum, firNum, ps, dist, sDate, stags, notes, io, status ->
                viewModel.saveCaseEntry(
                    id = caseToEdit?.id ?: 0,
                    caseNumber = cNum,
                    firNumber = firNum,
                    policeStation = ps,
                    district = dist,
                    date = sDate,
                    tags = stags,
                    notes = notes,
                    investigatingOfficer = io,
                    status = status
                )
                viewModel.clearCaseOcrState()
                showAddEditDialog = false
            },
            viewModel = viewModel
        )
    }
}

@Composable
fun CaseEntryCard(
    case: CaseEntry,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    viewModel: TourViewModel
) {
    var expanded by remember { mutableStateOf(false) }
    
    val dateParts = case.date.split("-")
    val dayText = if (dateParts.size >= 3) dateParts[2] else "??"
    val monthNoText = if (dateParts.size >= 2) dateParts[1] else ""
    val monthShortName = when (monthNoText) {
        "01" -> "Jan"
        "02" -> "Feb"
        "03" -> "Mar"
        "04" -> "Apr"
        "05" -> "May"
        "06" -> "Jun"
        "07" -> "Jul"
        "08" -> "Aug"
        "09" -> "Sep"
        "10" -> "Oct"
        "11" -> "Nov"
        "12" -> "Dec"
        else -> "Oct"
    }

    val statusBg = when (case.status.lowercase().trim()) {
        "completed", "closed", "solved" -> Color(0xFFE8DEF8)
        "pending", "active" -> Color(0xFFEADDFF)
        else -> Color(0xFFFFD8E4)
    }
    
    val statusTextCol = when (case.status.lowercase().trim()) {
        "completed", "closed", "solved" -> Color(0xFF21005D)
        "pending", "active" -> Color(0xFF21005D)
        else -> Color(0xFF31111D)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFCAC4D0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                // Calendar Badge
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFF3EDF7)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = dayText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF6750A4),
                            lineHeight = 15.sp
                        )
                        Text(
                            text = monthShortName.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF6750A4),
                            lineHeight = 10.sp
                        )
                    }
                }

                // Middle summary particulars
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CS Case: ${case.caseNumber}",
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D1B20),
                            fontSize = 15.sp
                        )
                        
                        // Status Badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(statusBg)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = case.status.uppercase(),
                                color = statusTextCol,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "F.I.R. No: ${case.firNumber.ifBlank { "N/A" }}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF49454F)
                    )

                    Text(
                        text = "PS: ${case.policeStation} • District: ${case.district.ifBlank { "N/A" }}",
                        fontSize = 11.sp,
                        color = Color(0xFF49454F).copy(alpha = 0.8f)
                    )
                }
            }

            // Keyword Tags Layout
            if (case.tags.isNotBlank()) {
                val tagsList = case.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = "Tags",
                        tint = Color(0xFF6750A4),
                        modifier = Modifier.size(12.dp)
                    )
                    
                    tagsList.forEach { tag ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFFEADDFF))
                                .clickable {
                                    viewModel.setCaseFilterTag(tag)
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "#$tag",
                                color = Color(0xFF21005D),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Expanded Area details
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    HorizontalDivider(color = Color(0xFFCAC4D0).copy(alpha = 0.5f))

                    if (case.investigatingOfficer.isNotBlank()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Person, contentDescription = "I.O.", tint = Color(0xFF49454F), modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Investigating Officer: ${case.investigatingOfficer}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF49454F)
                            )
                        }
                    }

                    if (case.notes.isNotBlank()) {
                        Text(
                            text = "Case Synopsis / Case History / Scribbles:",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF49454F)
                        )
                        Text(
                            text = case.notes,
                            fontSize = 11.sp,
                            color = Color(0xFF49454F).copy(alpha = 0.9f),
                            lineHeight = 15.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFFFEF7FF), RoundedCornerShape(8.dp))
                                .padding(8.dp)
                        )
                    }

                    // Bottom editing triggers
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit case", tint = Color(0xFF6750A4), modifier = Modifier.size(18.dp))
                        }
                        
                        Spacer(modifier = Modifier.width(6.dp))
                        
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete case", tint = Color(0xFFBA1A1A), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaseFormDialog(
    caseToEdit: CaseEntry?,
    onDismiss: () -> Unit,
    onSave: (
        caseNumber: String,
        firNumber: String,
        policeStation: String,
        district: String,
        date: String,
        tags: String,
        notes: String,
        investigatingOfficer: String,
        status: String
    ) -> Unit,
    viewModel: TourViewModel
) {
    var cNum by remember { mutableStateOf(caseToEdit?.caseNumber ?: "") }
    var firNum by remember { mutableStateOf(caseToEdit?.firNumber ?: "") }
    var ps by remember { mutableStateOf(caseToEdit?.policeStation ?: "") }
    var dist by remember { mutableStateOf(caseToEdit?.district ?: "") }
    var sDate by remember { mutableStateOf(caseToEdit?.date ?: "") }
    var tagsInput by remember { mutableStateOf(caseToEdit?.tags ?: "") }
    var notesInput by remember { mutableStateOf(caseToEdit?.notes ?: "") }
    var ioInput by remember { mutableStateOf(caseToEdit?.investigatingOfficer ?: "") }
    var statusInput by remember { mutableStateOf(caseToEdit?.status ?: "Under Investigation") }

    val context = LocalContext.current
    
    val statusOptions = listOf("Under Investigation", "Active", "Pending", "Completed")
    var statusExpanded by remember { mutableStateOf(false) }

    // Floating Scribble Handwriting pad state
    var showScribblePad by remember { mutableStateOf(false) }

    // Launcher for Multi-Field Voice speech-to-text typing dictation
    var activeDictationTarget by remember { mutableStateOf<String?>(null) }
    val speechDictationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val matches = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            val text = matches?.firstOrNull() ?: ""
            if (text.isNotBlank()) {
                when (activeDictationTarget) {
                    "caseNumber" -> cNum = text
                    "firNumber" -> firNum = text
                    "policeStation" -> ps = text
                    "district" -> dist = text
                    "io" -> ioInput = text
                    "tags" -> tagsInput = if (tagsInput.isBlank()) text else "$tagsInput, $text"
                    "notes" -> notesInput = if (notesInput.isBlank()) text else "$notesInput\n$text"
                }
                activeDictationTarget = null
            }
        }
    }

    val triggerSpeechDictation = { target: String ->
        try {
            activeDictationTarget = target
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Dictating into $target...")
            }
            speechDictationLauncher.launch(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Voice dictation is not supported on this device", Toast.LENGTH_LONG).show()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    if (cNum.isBlank() || ps.isBlank()) {
                        Toast.makeText(context, "Case Number and Police Station are required!", Toast.LENGTH_SHORT).show()
                    } else {
                        val formattedDate = sDate.ifBlank {
                            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                        }
                        onSave(cNum, firNum, ps, dist, formattedDate, tagsInput, notesInput, ioInput, statusInput)
                    }
                }
            ) {
                Text("Save Case", fontWeight = FontWeight.Bold, color = Color(0xFF6750A4))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF49454F))
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (caseToEdit?.caseNumber?.isBlank() == false) "Edit Case Profile" else "Register Case Profile",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                
                // Indication labels for Voice support
                Text(
                    "🎙️ Voice Dictation Active",
                    fontSize = 10.sp,
                    color = Color(0xFF49454F).copy(alpha = 0.6f),
                    fontWeight = FontWeight.Medium
                )
            }
        },
        text = {
            // Scrollable fields
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = cNum,
                        onValueChange = { cNum = it },
                        label = { Text("Case Number / C.S. No *", fontSize = 12.sp) },
                        placeholder = { Text("e.g. CS-244/2026", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        trailingIcon = {
                            IconButton(onClick = { triggerSpeechDictation("caseNumber") }) {
                                Icon(Icons.Filled.Mic, contentDescription = "Speak Case Number", tint = Color(0xFF6750A4), modifier = Modifier.size(18.dp))
                            }
                        }
                    )
                }

                item {
                    OutlinedTextField(
                        value = firNum,
                        onValueChange = { firNum = it },
                        label = { Text("F.I.R. Number", fontSize = 12.sp) },
                        placeholder = { Text("e.g. 150/2026", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        trailingIcon = {
                            IconButton(onClick = { triggerSpeechDictation("firNumber") }) {
                                Icon(Icons.Filled.Mic, contentDescription = "Speak FIR Number", tint = Color(0xFF6750A4), modifier = Modifier.size(18.dp))
                            }
                        }
                    )
                }

                item {
                    OutlinedTextField(
                        value = ps,
                        onValueChange = { 
                            ps = it
                            val autoDist = com.example.utils.RajasthanPoliceHelper.getDistrict(it)
                            if (!autoDist.isNullOrBlank() && dist.isBlank()) {
                                dist = autoDist
                            }
                        },
                        label = { Text("Police Station (P.S.) *", fontSize = 12.sp) },
                        placeholder = { Text("e.g. West Crime Branch", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        trailingIcon = {
                            IconButton(onClick = { triggerSpeechDictation("policeStation") }) {
                                Icon(Icons.Filled.Mic, contentDescription = "Speak Police Station", tint = Color(0xFF6750A4), modifier = Modifier.size(18.dp))
                            }
                        }
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = dist,
                            onValueChange = { dist = it },
                            label = { Text("District", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp),
                            trailingIcon = {
                                IconButton(onClick = { triggerSpeechDictation("district") }) {
                                    Icon(Icons.Filled.Mic, contentDescription = "Speak District", tint = Color(0xFF6750A4), modifier = Modifier.size(16.dp))
                                }
                            }
                        )

                        OutlinedTextField(
                            value = sDate,
                            onValueChange = { sDate = it },
                            label = { Text("Date (YYYY-MM-DD)", fontSize = 11.sp) },
                            placeholder = { Text("YYYY-MM-DD", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(8.dp),
                            trailingIcon = {
                                IconButton(
                                    onClick = {
                                        val cal = Calendar.getInstance()
                                        DatePickerDialog(
                                            context,
                                            { _, year, month, day ->
                                                sDate = String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, day)
                                            },
                                            cal.get(Calendar.YEAR),
                                            cal.get(Calendar.MONTH),
                                            cal.get(Calendar.DAY_OF_MONTH)
                                        ).show()
                                    }
                                ) {
                                    Icon(Icons.Filled.DateRange, contentDescription = "Pick Date", modifier = Modifier.size(16.dp))
                                }
                            }
                        )
                    }
                }

                item {
                    OutlinedTextField(
                        value = ioInput,
                        onValueChange = { ioInput = it },
                        label = { Text("Investigating Officer (I.O.)", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        trailingIcon = {
                            IconButton(onClick = { triggerSpeechDictation("io") }) {
                                Icon(Icons.Filled.Mic, contentDescription = "Speak Officer Name", tint = Color(0xFF6750A4), modifier = Modifier.size(18.dp))
                            }
                        }
                    )
                }

                item {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = statusInput,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Case Status", fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            trailingIcon = {
                                IconButton(onClick = { statusExpanded = true }) {
                                    Icon(Icons.Filled.ArrowDropDown, contentDescription = "Dropdown")
                                }
                            }
                        )
                        DropdownMenu(
                            expanded = statusExpanded,
                            onDismissRequest = { statusExpanded = false }
                        ) {
                            statusOptions.forEach { opt ->
                                DropdownMenuItem(
                                    text = { Text(opt) },
                                    onClick = {
                                        statusInput = opt
                                        statusExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = tagsInput,
                        onValueChange = { tagsInput = it },
                        label = { Text("Keywords / Tags (Comma separated)", fontSize = 12.sp) },
                        placeholder = { Text("e.g. Homicide, Arsenic, Forensic", fontSize = 11.sp) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        trailingIcon = {
                            IconButton(onClick = { triggerSpeechDictation("tags") }) {
                                Icon(Icons.Filled.Mic, contentDescription = "Speak Tags", tint = Color(0xFF6750A4), modifier = Modifier.size(18.dp))
                            }
                        }
                    )
                }

                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Synopsis / Scene Notes", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF49454F))
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                // Direct voice dictation for notes
                                IconButton(
                                    onClick = { triggerSpeechDictation("notes") },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Mic, contentDescription = "Dictate notes", tint = Color(0xFF6750A4), modifier = Modifier.size(16.dp))
                                }

                                // Interactive Hand-scribble Pad launch button!
                                IconButton(
                                    onClick = { showScribblePad = true },
                                    modifier = Modifier.size(32.dp).background(Color(0xFFEADDFF), RoundedCornerShape(4.dp))
                                ) {
                                    Icon(Icons.Filled.Star, contentDescription = "Scribble on Screen", tint = Color(0xFF21005D), modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                        
                        OutlinedTextField(
                            value = notesInput,
                            onValueChange = { notesInput = it },
                            placeholder = { Text("Describe main suspects, forensic evidence, or use voice commands & scribbles...", fontSize = 11.sp) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            shape = RoundedCornerShape(8.dp),
                            maxLines = 8
                        )
                    }
                }
            }
        }
    )

    // SCRIBBLE PAD DIALOG BOX
    if (showScribblePad) {
        ScribblePad(
            onDismiss = { showScribblePad = false },
            viewModel = viewModel,
            onTranscriptionComplete = { transcript ->
                notesInput = if (notesInput.isBlank()) {
                    transcript
                } else {
                    "$notesInput\n\n[Scribble Transcription]:\n$transcript"
                }
                Toast.makeText(context, "Handwritten note transcribed successfully!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

// ==========================================
// INTERACTIVE SCRIBBLE SCREEN WRITING PAD
// ==========================================

@Composable
fun ScribblePad(
    onDismiss: () -> Unit,
    viewModel: TourViewModel,
    onTranscriptionComplete: (String) -> Unit
) {
    var drawPaths by remember { mutableStateOf(listOf<List<Offset>>()) }
    var currentPath by remember { mutableStateOf(listOf<Offset>()) }
    var isTranscribing by remember { mutableStateOf(false) }
    
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .fillMaxHeight(0.85f),
        confirmButton = {
            Button(
                onClick = {
                    if (drawPaths.isEmpty()) {
                        onDismiss()
                        return@Button
                    }
                    isTranscribing = true
                    // Render paths onto bitmap for Gemini
                    val width = 800
                    val height = 1000
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    val canvas = android.graphics.Canvas(bitmap)
                    canvas.drawColor(android.graphics.Color.WHITE)
                    val paint = android.graphics.Paint().apply {
                        color = android.graphics.Color.BLACK
                        strokeWidth = 8f
                        style = Paint.Style.STROKE
                        strokeCap = Paint.Cap.ROUND
                        strokeJoin = Paint.Join.ROUND
                    }
                    
                    // Simple path translation & drawing
                    drawPaths.forEach { path ->
                        if (path.isNotEmpty()) {
                            val p = android.graphics.Path()
                            p.moveTo(path[0].x, path[0].y)
                            for (i in 1 until path.size) {
                                p.lineTo(path[i].x, path[i].y)
                            }
                            canvas.drawPath(p, paint)
                        }
                    }
                    
                    viewModel.transcribeScribbleDrawing(bitmap) { typedText ->
                        isTranscribing = false
                        onTranscriptionComplete(typedText)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                enabled = !isTranscribing
            ) {
                if (isTranscribing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("AI Processing Handwriting...", fontSize = 11.sp, color = Color.White)
                } else {
                    Icon(Icons.Filled.Check, contentDescription = "Done", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("AI Transcribe", fontSize = 11.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isTranscribing) {
                Text("Cancel", color = Color(0xFF49454F))
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Handwriting Scratch Pad", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Write or sketch directly on screen with your finger", fontSize = 11.sp, color = Color(0xFF49454F))
                }
                
                IconButton(
                    onClick = { drawPaths = emptyList() },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = "Clear Pad", tint = Color(0xFFBA1A1A))
                }
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(12.dp))
                    .border(2.dp, Color(0xFFCAC4D0), RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                currentPath = listOf(offset)
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                currentPath = currentPath + change.position
                            },
                            onDragEnd = {
                                drawPaths = drawPaths + listOf(currentPath)
                                currentPath = emptyList()
                            }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Draw saved paths
                    drawPaths.forEach { path ->
                        if (path.size > 1) {
                            val p = androidx.compose.ui.graphics.Path().apply {
                                moveTo(path[0].x, path[0].y)
                                for (i in 1 until path.size) {
                                    lineTo(path[i].x, path[i].y)
                                }
                            }
                            drawPath(p, Color(0xFF1D1B20), style = Stroke(width = 6f))
                        }
                    }
                    
                    // Draw active path
                    if (currentPath.size > 1) {
                        val p = androidx.compose.ui.graphics.Path().apply {
                            moveTo(currentPath[0].x, currentPath[0].y)
                            for (i in 1 until currentPath.size) {
                                lineTo(currentPath[i].x, currentPath[i].y)
                            }
                        }
                        drawPath(p, Color(0xFF6750A4), style = Stroke(width = 6f))
                    }
                }
                
                if (drawPaths.isEmpty() && currentPath.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Filled.Star, contentDescription = "Scribble Info", tint = Color(0xFFCAC4D0), modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Scribble or Draw Here", fontWeight = FontWeight.SemiBold, color = Color(0xFF49454F).copy(alpha = 0.5f), fontSize = 14.sp)
                            Text("Take quick field notes or draw/write. Gemini AI will convert your drawings into typed text diary lines.", fontSize = 11.sp, color = Color(0xFF49454F).copy(alpha = 0.5f), textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 24.dp))
                        }
                    }
                }
            }
        }
    )
}

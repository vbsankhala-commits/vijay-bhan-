package com.example.ui

import android.content.Context
import java.text.SimpleDateFormat
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.TourEntry
import java.util.Locale

data class JourneyLeg(
    val date: String,
    val fromPlace: String,
    val toPlace: String,
    val depTime: String,
    val arrTime: String,
    val travelMode: String,
    val distance: Double,
    val purpose: String,
    val sNoLabel: String,
    val legType: String // "Outward" or "Return"
)

private fun splitJourneyTimesForBot(depTime: String, arrTime: String, distance: Double): Pair<String, String> {
    return try {
        val depParts = depTime.split(":")
        val arrParts = arrTime.split(":")
        val depMins = depParts[0].trim().toInt() * 60 + depParts[1].trim().toInt()
        val arrMins = arrParts[0].trim().toInt() * 60 + arrParts[1].trim().toInt()
        
        val totalMins = if (arrMins >= depMins) arrMins - depMins else (1440 - depMins) + arrMins
        
        // Assume 40 km/h average speed. Outward trip is half distance.
        val halfDist = distance / 2.0
        var travelMins = ((halfDist / 40.0) * 60.0).toInt()
        
        // Keep travelMins reasonable, say between 15 and 40% of total trip time.
        if (totalMins > 40) {
            travelMins = travelMins.coerceIn(15, (totalMins * 0.4).toInt())
        } else {
            travelMins = (totalMins * 0.3).toInt().coerceAtLeast(10)
        }
        
        val outwardArrMins = (depMins + travelMins) % 1440
        val returnDepMins = (arrMins - travelMins + 1440) % 1440
        
        val outH = outwardArrMins / 60
        val outM = outwardArrMins % 60
        val retH = returnDepMins / 60
        val retM = returnDepMins % 60
        
        val outStr = String.format(Locale.getDefault(), "%02d:%02d", outH, outM)
        val retStr = String.format(Locale.getDefault(), "%02d:%02d", retH, retM)
        Pair(outStr, retStr)
    } catch (e: Exception) {
        Pair("12:00", "15:00")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IfmsAutoFillBotDialog(
    selectedMonth: String,
    profile: com.example.data.EmployeeProfile,
    entries: List<TourEntry>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("IFMS_BOT_PREFS", Context.MODE_PRIVATE) }
    
    var ssoId by remember { mutableStateOf(sharedPrefs.getString("SSO_ID", "") ?: "") }
    var ssoPassword by remember { mutableStateOf(sharedPrefs.getString("SSO_PWD", "") ?: "") }
    var showPassword by remember { mutableStateOf(false) }
    var isConfigSaved by remember { mutableStateOf(ssoId.isNotEmpty()) }
    
    // Split entries into outward and return legs
    val legs = remember(entries, profile) {
        val list = mutableListOf<JourneyLeg>()
        val sorted = entries.sortedWith(compareBy<TourEntry> { it.date }.thenBy { it.depTime })
        sorted.forEachIndexed { idx, entry ->
            val hq = profile.posting.ifBlank { "HQ" }
            val ps = "PS ${entry.policeStation}"
            
            val (outwardArrTime, returnDepTime) = splitJourneyTimesForBot(entry.depTime, entry.arrTime, entry.distance)
            val halfDist = entry.distance / 2.0
            
            list.add(
                JourneyLeg(
                    date = entry.date,
                    fromPlace = hq,
                    toPlace = ps,
                    depTime = entry.depTime,
                    arrTime = outwardArrTime,
                    travelMode = entry.travelMode,
                    distance = halfDist,
                    purpose = "Inspection of Crime Scene (FIR: ${entry.firNumber}, cs: ${entry.csNumber})",
                    sNoLabel = "${idx + 1}(a)",
                    legType = "Outward"
                )
            )
            list.add(
                JourneyLeg(
                    date = entry.arrDate.ifBlank { entry.date },
                    fromPlace = ps,
                    toPlace = hq,
                    depTime = returnDepTime,
                    arrTime = entry.arrTime,
                    travelMode = entry.travelMode,
                    distance = halfDist,
                    purpose = "Return travel back to Headquarter after crime scene inspection",
                    sNoLabel = "${idx + 1}(b)",
                    legType = "Return"
                )
            )
        }
        list
    }
    
    var selectedEntryIndex by remember { mutableStateOf(0) }
    var showCopilotOverlay by remember { mutableStateOf(true) }
    var activeUrl by remember { mutableStateOf("https://sso.rajasthan.gov.in/signin") }
    var currentProgress by remember { mutableStateOf(0) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var showExplanationDetail by remember { mutableStateOf(true) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Custom Premium Header Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.AutoMode,
                            contentDescription = "AI Bot",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Rajasthan IFMS 3.0 TA Bot",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Live SSO Web Portal Bridge",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Filled.Close, 
                            contentDescription = "Close Bot",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Main Layout Split
                if (!isConfigSaved) {
                    // Pre-config settings view
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Security,
                            contentDescription = "Secure Local Storage",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(56.dp)
                        )
                        
                        Text(
                            text = "Secure Local SSO Key-Vault",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        
                        Text(
                            text = "To automate draft generation, enter your Rajasthan SSO details. Files are saved 100% locally on your device and never transit through third-party servers.",
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center,
                            color = Color.Gray,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        )

                        OutlinedTextField(
                            value = ssoId,
                            onValueChange = { ssoId = it },
                            label = { Text("SSO ID / Username") },
                            placeholder = { Text("e.g. RJJO2018xxxx") },
                            leadingIcon = { Icon(Icons.Filled.Person, contentDescription = "SSO ID") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = ssoPassword,
                            onValueChange = { ssoPassword = it },
                            label = { Text("SSO Password") },
                            leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = "Password") },
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                        contentDescription = "Toggle password visibility"
                                    )
                                }
                            },
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                sharedPrefs.edit()
                                    .putString("SSO_ID", ssoId.trim())
                                    .putString("SSO_PWD", ssoPassword)
                                    .apply()
                                isConfigSaved = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Unlock SSO Copilot Web Bridge", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }

                        // Guidance Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Info, contentDescription = "How it works", modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("How It Works:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                }
                                Text("- The AI bot initializes the SSO login stage automatically.", fontSize = 11.sp, color = Color.DarkGray)
                                Text("- Since government portals enforce graphical CAPTCHA verification, you will key-in the CAPTCHA manually to log in safely.", fontSize = 11.sp, color = Color.DarkGray)
                                Text("- Once logged in, navigate into IFMS 3.0 > ESS > TA bill section, and tap 'Auto-Fill Page Fields' to save as draft!", fontSize = 11.sp, color = Color.DarkGray)
                            }
                        }
                    }
                } else {
                    // Active Browser Bridge view
                    Column(modifier = Modifier.fillMaxSize()) {
                        // Guidelines collapsible header banner
                        AnimatedVisibility(visible = showExplanationDetail) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.TipsAndUpdates,
                                            contentDescription = "Tips",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                "Active Copilot Checklist:",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer
                                            )
                                            Text(
                                                "1. Click 'Quick Fill SSO' to insert login fields, then type CAPTCHA manually and sign in.",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                            )
                                            Text(
                                                "2. Open IFMS 3.0 -> Employee Self Service (ESS) -> TA Claim Form.",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                            )
                                            Text(
                                                "3. Use our smart Auto-Pilot 'Auto-Fill & Next Leg ⚡' to continuously input halved legs with intermediate times sequentially inside the government form!",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                            )
                                        }
                                    }
                                    IconButton(
                                        onClick = { showExplanationDetail = false },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "Dismiss Banner", modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }

                        // Web UI Progress Bar
                        if (currentProgress < 100) {
                            LinearProgressIndicator(
                                progress = currentProgress / 100f,
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        }

                        // Copilot Dynamic Operations Overlay Toolbar
                        AnimatedVisibility(visible = showCopilotOverlay) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Row 1: Actions & Vault Configuration toggle
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    webViewInstance?.let { webView ->
                                                        val js = """
                                                            (function() {
                                                                var uInp = document.querySelector('input[name*="username"], input[id*="username"], input[id*="Txtusername"], #username, #Txtusername');
                                                                var pInp = document.querySelector('input[type="password"], input[name*="password"], input[id*="password"], input[id*="Txtpassword"], #password, #Txtpassword');
                                                                if (uInp) {
                                                                    uInp.value = '${ssoId.replace("'", "\\'")}';
                                                                    uInp.dispatchEvent(new Event('input', { bubbles: true }));
                                                                    uInp.dispatchEvent(new Event('change', { bubbles: true }));
                                                                }
                                                                if (pInp) {
                                                                    pInp.value = '${ssoPassword.replace("'", "\\'")}';
                                                                    pInp.dispatchEvent(new Event('input', { bubbles: true }));
                                                                    pInp.dispatchEvent(new Event('change', { bubbles: true }));
                                                                }
                                                                return "SSO input fields targets mapped successfully";
                                                            })()
                                                        """.trimIndent()
                                                        webView.evaluateJavascript(js) { res ->
                                                            Toast.makeText(context, "Credentials filled. Type CAPTCHA to log in!", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                                modifier = Modifier
                                                    .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(18.dp))
                                                    .background(Color.White, RoundedCornerShape(18.dp))
                                                    .height(30.dp)
                                                    .padding(horizontal = 8.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(Icons.Filled.Key, contentDescription = "Fill", modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text("Quick Fill SSO", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                                }
                                            }
                                            
                                            Spacer(modifier = Modifier.width(8.dp))
                                            
                                            Button(
                                                onClick = {
                                                    webViewInstance?.let { webView ->
                                                        if (selectedEntryIndex in legs.indices) {
                                                            injectAutoFillCompletePage(webView, legs, selectedEntryIndex)
                                                            Toast.makeText(context, "Auto-filled active page fields with leg #${selectedEntryIndex + 1} (${legs[selectedEntryIndex].sNoLabel})!", Toast.LENGTH_SHORT).show()
                                                        } else {
                                                            Toast.makeText(context, "No active legs found!", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                                modifier = Modifier.height(30.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                            ) {
                                                Icon(Icons.Filled.Bolt, contentDescription = "Bolt", modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Auto-Fill Fields", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }

                                            Spacer(modifier = Modifier.width(6.dp))

                                            Button(
                                                onClick = {
                                                    webViewInstance?.let { webView ->
                                                        if (selectedEntryIndex in legs.indices) {
                                                            injectAutoFillCompletePage(webView, legs, selectedEntryIndex)
                                                            Toast.makeText(context, "Filled Leg #${selectedEntryIndex + 1} (${legs[selectedEntryIndex].sNoLabel})! Auto-advancing index...", Toast.LENGTH_SHORT).show()
                                                            if (selectedEntryIndex < legs.size - 1) {
                                                                selectedEntryIndex++
                                                            } else {
                                                                Toast.makeText(context, "Completed last travel leg! All entries processed.", Toast.LENGTH_LONG).show()
                                                            }
                                                        } else {
                                                            Toast.makeText(context, "No active legs found!", Toast.LENGTH_SHORT).show()
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00796B)),
                                                modifier = Modifier.height(30.dp),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp)
                                            ) {
                                                Icon(Icons.Filled.PlaylistAddCheck, contentDescription = "Continuous Auto-Pilot", modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Auto-Fill & Next Leg ⚡", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }

                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(
                                                onClick = {
                                                    isConfigSaved = false // Goes back to edit profile SSO credentials
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Filled.Settings, contentDescription = "Vault Config", modifier = Modifier.size(16.dp))
                                            }
                                            Spacer(modifier = Modifier.width(4.dp))
                                            IconButton(
                                                onClick = {
                                                    webViewInstance?.reload()
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(Icons.Filled.Refresh, contentDescription = "Reload web page", modifier = Modifier.size(16.dp))
                                            }
                                        }
                                    }

                                    Divider(color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.15f))

                                    // Entry Selection Row
                                    if (legs.isNotEmpty()) {
                                        Column {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    "Active Log Leg (${selectedEntryIndex + 1}/${legs.size}):",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                
                                                // Prev and Next controllers
                                                IconButton(
                                                    onClick = { if (selectedEntryIndex > 0) selectedEntryIndex-- },
                                                    enabled = selectedEntryIndex > 0,
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Filled.ArrowBack, contentDescription = "Prev", modifier = Modifier.size(16.dp))
                                                }
                                                val leg = legs[selectedEntryIndex]
                                                val labelLegStr = "${leg.sNoLabel} [${leg.legType}]: ${leg.fromPlace} ➔ ${leg.toPlace}"
                                                Text(
                                                    labelLegStr,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (leg.legType == "Outward") Color(0xFF1E88E5) else Color(0xFF43A047),
                                                    modifier = Modifier.padding(horizontal = 4.dp),
                                                    maxLines = 1
                                                )
                                                IconButton(
                                                    onClick = { if (selectedEntryIndex < legs.size - 1) selectedEntryIndex++ },
                                                    enabled = selectedEntryIndex < legs.size - 1,
                                                    modifier = Modifier.size(24.dp)
                                                ) {
                                                    Icon(Icons.Filled.ArrowForward, contentDescription = "Next", modifier = Modifier.size(16.dp))
                                                }
                                            }

                                            // Highlighted dynamic content detail
                                            val currentItem = legs[selectedEntryIndex]
                                            val currentMode = currentItem.travelMode
                                            val currentDistance = String.format(Locale.US, "%.1f", currentItem.distance)
                                            
                                            // Focus Injector Tool-group (Tap to write into current input directly!)
                                            Text(
                                                "Tap keys to injection-fill active cursor field in Government portal form:",
                                                fontSize = 9.sp,
                                                color = Color.DarkGray,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            )
                                            
                                            LazyRow(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                item {
                                                    InjectionChip("Date: ${currentItem.date}") {
                                                        injectFocusValue(webViewInstance, currentItem.date)
                                                    }
                                                }
                                                item {
                                                    InjectionChip("From: ${currentItem.fromPlace}") {
                                                        injectFocusValue(webViewInstance, currentItem.fromPlace)
                                                    }
                                                }
                                                item {
                                                    InjectionChip("To: ${currentItem.toPlace}") {
                                                        injectFocusValue(webViewInstance, currentItem.toPlace)
                                                    }
                                                }
                                                item {
                                                    InjectionChip("Time: ${currentItem.depTime} to ${currentItem.arrTime}") {
                                                        injectFocusValue(webViewInstance, "${currentItem.depTime} to ${currentItem.arrTime}")
                                                    }
                                                }
                                                item {
                                                    InjectionChip("Dist: ${currentDistance} KM") {
                                                        injectFocusValue(webViewInstance, currentDistance)
                                                    }
                                                }
                                                item {
                                                    InjectionChip("Mode: $currentMode") {
                                                        injectFocusValue(webViewInstance, currentMode)
                                                    }
                                                }
                                                item {
                                                    InjectionChip("Purpose: ${currentItem.purpose}") {
                                                        injectFocusValue(webViewInstance, currentItem.purpose)
                                                    }
                                                }
                                            }
                                        }
                                    } else {
                                        Text("No travel entry logged for month $selectedMonth. Cannot auto-fill entries.", fontSize = 11.sp, color = Color.Red)
                                    }
                                }
                            }
                        }

                        // Web View Controller Container
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            AndroidView(
                                factory = { ctx ->
                                    WebView(ctx).apply {
                                        webViewInstance = this
                                        settings.apply {
                                            javaScriptEnabled = true
                                            domStorageEnabled = true
                                            databaseEnabled = true
                                            loadWithOverviewMode = true
                                            useWideViewPort = true
                                            builtInZoomControls = true
                                            displayZoomControls = false
                                            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                        }
                                        
                                        webViewClient = object : WebViewClient() {
                                            override fun onPageFinished(view: WebView?, url: String?) {
                                                super.onPageFinished(view, url)
                                                url?.let { activeUrl = it }
                                                currentProgress = 100
                                                
                                                // Inject CSS style custom classes occasionally to support visual cues if needed
                                                val customCss = """
                                                    var style = document.createElement('style');
                                                    style.innerHTML = '@keyframes blink { 0% { opacity: 1; } 50% { opacity: 0.4; } 100% { opacity: 1; } }';
                                                    document.head.appendChild(style);
                                                """.trimIndent()
                                                view?.evaluateJavascript(customCss, null)
                                            }
                                        }
                                        
                                        webChromeClient = object : WebChromeClient() {
                                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                                super.onProgressChanged(view, newProgress)
                                                currentProgress = newProgress
                                            }
                                        }
                                        
                                        loadUrl(activeUrl)
                                    }
                                },
                                modifier = Modifier.fillMaxSize(),
                                update = { webView ->
                                    // No updates needed
                                }
                            )

                            // Overlay Button to toggle control hub back if hidden
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(16.dp)
                            ) {
                                FloatingActionButton(
                                    onClick = { showCopilotOverlay = !showCopilotOverlay },
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ) {
                                    Icon(
                                        imageVector = if (showCopilotOverlay) Icons.Filled.VisibilityOff else Icons.Filled.AutoMode,
                                        contentDescription = if (showCopilotOverlay) "Hide Tools" else "Show Tools"
                                    )
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
fun InjectionChip(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.ContentPaste, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(11.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(text, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Black)
        }
    }
}

fun injectFocusValue(webView: WebView?, value: String) {
    webView?.let { view ->
        val escapedValue = value.replace("'", "\\'")
        val js = """
            (function() {
                var el = document.activeElement;
                if (el) {
                    el.focus();
                    el.value = '$escapedValue';
                    el.dispatchEvent(new Event('input', { bubbles: true }));
                    el.dispatchEvent(new Event('change', { bubbles: true }));
                    el.dispatchEvent(new Event('blur', { bubbles: true }));
                    return "Injected: " + el.value;
                }
                return "NotInFocus";
            })()
        """.trimIndent()
        view.evaluateJavascript(js) { res ->
            if (res == null || res.contains("NotInFocus") || res.trim() == "null") {
                // If nothing was in focus, try to copy it to clipboard as backup
                val clipboard = view.context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("Copilot Data", value)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(view.context, "No inputs focused! Copied '$value' to clipboard to paste manually.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(view.context, "Wrote: $value", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

fun injectAutoFillCompletePage(webView: WebView, legs: List<JourneyLeg>, index: Int) {
    if (index < 0 || index >= legs.size) return
    val leg = legs[index]
    val escapedDate = leg.date.replace("'", "\\'")
    val escapedFrom = leg.fromPlace.replace("'", "\\'")
    val escapedTo = leg.toPlace.replace("'", "\\'")
    val escapedDistance = String.format(Locale.US, "%.1f", leg.distance)
    val escapedMode = leg.travelMode.replace("'", "\\'")
    val escapedDepTime = leg.depTime.replace("'", "\\'")
    val escapedArrTime = leg.arrTime.replace("'", "\\'")
    val escapedPurpose = leg.purpose.replace("'", "\\'")
    
    val js = """
        (function() {
            var inputs = document.getElementsByTagName('input');
            var selects = document.getElementsByTagName('select');
            var textareas = document.getElementsByTagName('textarea');
            
            function triggerEvents(el) {
                el.dispatchEvent(new Event('input', { bubbles: true }));
                el.dispatchEvent(new Event('change', { bubbles: true }));
                el.dispatchEvent(new Event('blur', { bubbles: true }));
            }
            
            function isMatch(el, keywords) {
                var id = (el.id || "").toLowerCase();
                var name = (el.name || "").toLowerCase();
                var placeholder = (el.placeholder || "").toLowerCase();
                
                var parentText = "";
                if (el.parentElement) {
                    parentText = el.parentElement.innerText ? el.parentElement.innerText.toLowerCase() : "";
                }
                
                for (var i = 0; i < keywords.length; i++) {
                    var kw = keywords[i].toLowerCase();
                    if (id.includes(kw) || name.includes(kw) || placeholder.includes(kw) || parentText.includes(kw)) {
                        return true;
                    }
                }
                return false;
            }
            
            var count = 0;
            for (var i = 0; i < inputs.length; i++) {
                var inp = inputs[i];
                if (isMatch(inp, ['date', 'journey', 'day'])) {
                    inp.value = '$escapedDate';
                    triggerEvents(inp);
                    count++;
                }
            }
            
            for (var i = 0; i < inputs.length; i++) {
                var inp = inputs[i];
                if (isMatch(inp, ['from', 'source', 'start', 'origin', 'base', 'hq', 'depart_place'])) {
                    inp.value = '$escapedFrom';
                    triggerEvents(inp);
                    count++;
                }
                if (isMatch(inp, ['to', 'dest', 'arrival_place', 'end', 'station', 'police'])) {
                    inp.value = '$escapedTo';
                    triggerEvents(inp);
                    count++;
                }
            }
            
            for (var i = 0; i < inputs.length; i++) {
                var inp = inputs[i];
                if (isMatch(inp, ['deptime', 'dep_time', 'departuretime', 'departure_time', 'time_dep', 'depHour'])) {
                    inp.value = '$escapedDepTime';
                    triggerEvents(inp);
                    count++;
                }
                if (isMatch(inp, ['arrtime', 'arr_time', 'arrivaltime', 'arrival_time', 'time_arr', 'arrHour'])) {
                    inp.value = '$escapedArrTime';
                    triggerEvents(inp);
                    count++;
                }
            }
            
            for (var i = 0; i < inputs.length; i++) {
                var inp = inputs[i];
                if (isMatch(inp, ['dist', 'km', 'kilometer', 'travelled', 'distance'])) {
                    inp.value = '$escapedDistance';
                    triggerEvents(inp);
                    count++;
                }
            }
            
            for (var i = 0; i < textareas.length; i++) {
                var area = textareas[i];
                if (isMatch(area, ['purpose', 'remark', 'reason', 'particulars', 'detail'])) {
                    area.value = '$escapedPurpose';
                    triggerEvents(area);
                    count++;
                }
            }
            
            for (var i = 0; i < selects.length; i++) {
                var sel = selects[i];
                if (isMatch(sel, ['mode', 'travel', 'vehicle', 'class'])) {
                    var modeMatch = '$escapedMode'.toLowerCase();
                    for (var opt = 0; opt < sel.options.length; opt++) {
                        var optText = sel.options[opt].text.toLowerCase();
                        if (optText.includes(modeMatch) || modeMatch.includes(optText)) {
                            sel.selectedIndex = opt;
                            triggerEvents(sel);
                            count++;
                            break;
                        }
                    }
                }
            }
            
            var buttons = document.getElementsByTagName('button');
            for (var i = 0; i < buttons.length; i++) {
                var btn = buttons[i];
                var text = (btn.innerText || "").toLowerCase();
                if (text.includes('draft') || text.includes('save draft') || text.includes('add')) {
                    btn.style.border = "3px dashed #ff007f";
                }
            }
            
            return count;
        })()
    """.trimIndent()
    webView.evaluateJavascript(js, null)
}

private fun formatDateSimple(dateStr: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val formatter = SimpleDateFormat("dd/MM", Locale.getDefault())
        val parsed = parser.parse(dateStr)
        if (parsed != null) formatter.format(parsed) else dateStr
    } catch (e: Exception) {
        dateStr
    }
}

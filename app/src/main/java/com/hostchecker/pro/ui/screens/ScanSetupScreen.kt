package com.hostchecker.pro.ui.screens

import android.net.Uri
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hostchecker.pro.data.prefs.SettingsDataStore
import com.hostchecker.pro.data.repo.SessionRepository
import com.hostchecker.pro.domain.model.ScanConfig
import com.hostchecker.pro.domain.model.Session
import com.hostchecker.pro.ui.theme.AccentCyan
import com.hostchecker.pro.ui.theme.AppBarTeal
import com.hostchecker.pro.ui.theme.Background
import com.hostchecker.pro.ui.theme.Divider
import com.hostchecker.pro.ui.theme.Primary
import com.hostchecker.pro.ui.theme.Surface
import com.hostchecker.pro.ui.theme.SurfaceVariant
import com.hostchecker.pro.ui.theme.TextPrimary
import com.hostchecker.pro.ui.theme.TextSecondary
import com.hostchecker.pro.util.AutoSaveManager
import com.hostchecker.pro.util.FileUtil
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanSetupScreen(
    fileUriString: String?,
    rawPastedText: String?,
    sessionRepository: SessionRepository,
    settingsDataStore: SettingsDataStore,
    onStartScan: (sessionId: Long, hosts: List<String>, config: ScanConfig) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var fileName by remember { mutableStateOf("Manual Input") }
    var loadedHosts by remember { mutableStateOf<List<String>>(emptyList()) }
    var duplicateCount by remember { mutableStateOf(0) }
    var invalidCount by remember { mutableStateOf(0) }
    var isLoadingFile by remember { mutableStateOf(true) }

    var sessionName by remember { mutableStateOf("") }
    var outNameText by remember { mutableStateOf("") }
    var threadsText by remember { mutableStateOf("6") }
    var timeoutText by remember { mutableStateOf("10") }
    var filterText by remember { mutableStateOf("") }
    var retryFailed by remember { mutableStateOf(false) }
    var stealthMode by remember { mutableStateOf(false) }
    var jitterEnabled by remember { mutableStateOf(false) }

    // Load defaults from DataStore and parse file
    LaunchedEffect(Unit) {
        val userSettings = settingsDataStore.settingsFlow.first()
        threadsText = userSettings.defaultThreads.toString()
        timeoutText = userSettings.defaultTimeoutSeconds.toString()
        retryFailed = userSettings.retryFailed
        stealthMode = userSettings.stealthMode
        jitterEnabled = userSettings.jitterEnabled

        if (!fileUriString.isNullOrBlank()) {
            try {
                val uri = Uri.parse(fileUriString)
                val result = FileUtil.readHostsFromUri(context, uri)
                fileName = result.fileName
                loadedHosts = result.validHosts
                duplicateCount = result.duplicateCount
                invalidCount = result.invalidCount
                sessionName = "Scan: ${result.fileName}"
                outNameText = AutoSaveManager.sanitizeFolderName(result.fileName.substringBeforeLast("."))
            } catch (e: Exception) {
                fileName = "Error loading file"
            }
        } else if (!rawPastedText.isNullOrBlank()) {
            val result = FileUtil.parseHostsFromText(rawPastedText)
            fileName = "Pasted List"
            loadedHosts = result.validHosts
            duplicateCount = result.duplicateCount
            invalidCount = result.invalidCount
            sessionName = "Pasted Scan (${loadedHosts.size} hosts)"
            outNameText = "pasted_scan"
        }
        isLoadingFile = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Scan Configuration",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.testTag("setup_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBarTeal),
                modifier = Modifier.testTag("scan_setup_top_app_bar")
            )
        },
        containerColor = Background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        if (isLoadingFile) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(color = Primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Reading host list...", color = TextSecondary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                // File summary card displaying filename, valid hosts, duplicates removed, and invalid entries
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = fileName,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 16.sp,
                                    modifier = Modifier.testTag("loaded_filename_text")
                                )
                                Text(
                                    text = "${loadedHosts.size} valid hosts ready",
                                    color = Primary,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.testTag("valid_hosts_count_text")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .background(Divider.copy(alpha = 0.5f))
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Stats metrics
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Valid", color = TextSecondary, fontSize = 11.sp)
                                Text("${loadedHosts.size}", color = Primary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Column {
                                Text("Duplicates Removed", color = TextSecondary, fontSize = 11.sp)
                                Text("$duplicateCount", color = AccentCyan, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                            Column {
                                Text("Invalid Skipped", color = TextSecondary, fontSize = 11.sp)
                                Text("$invalidCount", color = if (invalidCount > 0) com.hostchecker.pro.ui.theme.Warning else TextSecondary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Session Name
                OutlinedTextField(
                    value = sessionName,
                    onValueChange = { sessionName = it },
                    label = { Text("Session Name") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("session_name_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = Divider,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Output Folder Name (outname)
                OutlinedTextField(
                    value = outNameText,
                    onValueChange = { outNameText = it },
                    label = { Text("Output Folder Name (outname)") },
                    placeholder = { Text("e.g. live_targets") },
                    supportingText = {
                        Text(
                            text = "Auto-saves live hosts in: HostCheckerPro/${AutoSaveManager.sanitizeFolderName(outNameText).ifBlank { "scan" }}/",
                            color = AccentCyan,
                            fontSize = 12.sp
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("outname_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = Divider,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Threads & Timeout in Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = threadsText,
                        onValueChange = { threadsText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Threads (1-64)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("threads_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = Divider,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    OutlinedTextField(
                        value = timeoutText,
                        onValueChange = { timeoutText = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Timeout (sec)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("timeout_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = Divider,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Pre-scan filter
                OutlinedTextField(
                    value = filterText,
                    onValueChange = { filterText = it },
                    label = { Text("Pre-Scan Filter (Optional)") },
                    placeholder = { Text("e.g. .cloudfront.net or substring") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pre_scan_filter_input"),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Primary,
                        unfocusedBorderColor = Divider,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Switches section
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Retry failed switch
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Retry Failed Hosts", color = TextPrimary, fontWeight = FontWeight.Medium)
                                Text("2 retries with exponential backoff", color = TextSecondary, fontSize = 12.sp)
                            }
                            Switch(
                                checked = retryFailed,
                                onCheckedChange = { retryFailed = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = TextPrimary,
                                    checkedTrackColor = Primary
                                ),
                                modifier = Modifier.testTag("retry_failed_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Stealth mode switch
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Stealth Mode", color = TextPrimary, fontWeight = FontWeight.Medium)
                                Text("Rotate 12+ realistic browser User-Agents", color = TextSecondary, fontSize = 12.sp)
                            }
                            Switch(
                                checked = stealthMode,
                                onCheckedChange = { stealthMode = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = TextPrimary,
                                    checkedTrackColor = Primary
                                ),
                                modifier = Modifier.testTag("stealth_mode_switch")
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Jitter delay switch
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Jitter (Random Delay)", color = TextPrimary, fontWeight = FontWeight.Medium)
                                Text("0-500ms random delay per host", color = TextSecondary, fontSize = 12.sp)
                            }
                            Switch(
                                checked = jitterEnabled,
                                onCheckedChange = { jitterEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = TextPrimary,
                                    checkedTrackColor = Primary
                                ),
                                modifier = Modifier.testTag("jitter_switch")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // BIG "START SCAN" button
                Button(
                    onClick = {
                        val threads = threadsText.toIntOrNull()?.coerceIn(1, 64) ?: 6
                        val timeout = timeoutText.toIntOrNull()?.coerceIn(1, 60) ?: 10

                        var targetHosts = loadedHosts
                        if (filterText.isNotBlank()) {
                            targetHosts = targetHosts.filter { it.contains(filterText.trim(), ignoreCase = true) }
                        }

                        val resolvedOutName = outNameText.trim().ifBlank {
                            sessionName.trim().ifBlank { fileName.substringBeforeLast(".") }.ifBlank { "scan" }
                        }

                        val config = ScanConfig(
                            threads = threads,
                            timeoutSeconds = timeout,
                            filterText = filterText,
                            retryFailed = retryFailed,
                            stealthMode = stealthMode,
                            jitterEnabled = jitterEnabled,
                            outName = resolvedOutName
                        )

                        scope.launch {
                            val finalName = sessionName.ifBlank { "Scan: $fileName" }
                            val newSession = Session(
                                name = finalName,
                                fileName = fileName,
                                total = targetHosts.size,
                                threads = threads,
                                status = "RUNNING"
                            )
                            val sessionId = sessionRepository.createSession(newSession)
                            onStartScan(sessionId, targetHosts, config)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("start_scan_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "START SCAN",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = TextPrimary
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Cancel TextButton
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("cancel_setup_button")
                ) {
                    Text(
                        text = "Cancel",
                        color = TextSecondary,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

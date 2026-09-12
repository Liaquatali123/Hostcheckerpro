package com.hostchecker.pro.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hostchecker.pro.domain.export.Exporter
import com.hostchecker.pro.domain.model.ScanResult
import com.hostchecker.pro.util.AutoSaveManager
import com.hostchecker.pro.ui.components.ExportDestination
import com.hostchecker.pro.ui.components.ExportSheet
import com.hostchecker.pro.ui.components.ProgressFooter
import com.hostchecker.pro.ui.components.ResultCard
import com.hostchecker.pro.ui.components.StatRow
import com.hostchecker.pro.ui.theme.AccentCyan
import com.hostchecker.pro.ui.theme.AppBarTeal
import com.hostchecker.pro.ui.theme.Background
import com.hostchecker.pro.ui.theme.Divider
import com.hostchecker.pro.ui.theme.Primary
import com.hostchecker.pro.ui.theme.Status2xx
import com.hostchecker.pro.ui.theme.Status3xx
import com.hostchecker.pro.ui.theme.Status4xx
import com.hostchecker.pro.ui.theme.Status5xx
import com.hostchecker.pro.ui.theme.Surface
import com.hostchecker.pro.ui.theme.SurfaceVariant
import com.hostchecker.pro.ui.theme.TextPrimary
import com.hostchecker.pro.ui.theme.TextSecondary
import com.hostchecker.pro.ui.theme.Warning
import com.hostchecker.pro.ui.viewmodel.ScanUiState
import com.hostchecker.pro.ui.viewmodel.ScanViewModel
import com.hostchecker.pro.ui.viewmodel.SortField
import java.io.File
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanProgressScreen(
    sessionId: Long,
    viewModel: ScanViewModel,
    onNavigateBack: () -> Unit,
    onOpenResultDetail: (resultId: Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val results by viewModel.filteredResults.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    var showSearchBar by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    val exportSheetState = rememberModalBottomSheetState()
    var showFilesSheet by remember { mutableStateOf(false) }
    val filesSheetState = rememberModalBottomSheetState()

    LaunchedEffect(sessionId) {
        viewModel.loadSession(sessionId)
    }

    // Smoothly auto-scroll to top when a new live host appears so the user sees it slide into view
    LaunchedEffect(results.firstOrNull()?.id) {
        if (results.isNotEmpty() && listState.firstVisibleItemIndex <= 1) {
            listState.animateScrollToItem(0)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (uiState.isSelectionMode) {
                // Bulk Selection Mode TopAppBar
                TopAppBar(
                    title = {
                        Text(
                            text = "${uiState.selectedIds.size} Selected",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { viewModel.clearSelection() },
                            modifier = Modifier.testTag("clear_selection_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Selection",
                                tint = TextPrimary
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.selectAll() },
                            modifier = Modifier.testTag("select_all_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SelectAll,
                                contentDescription = "Select All",
                                tint = TextPrimary
                            )
                        }
                        IconButton(
                            onClick = { viewModel.copySelectedHosts(context) },
                            modifier = Modifier.testTag("copy_selected_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Hosts",
                                tint = TextPrimary
                            )
                        }
                        IconButton(
                            onClick = { showExportSheet = true },
                            modifier = Modifier.testTag("export_selected_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Export Selected",
                                tint = TextPrimary
                            )
                        }
                        IconButton(
                            onClick = { viewModel.deleteSelected() },
                            modifier = Modifier.testTag("delete_selected_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete Selected",
                                tint = Warning
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBarTeal),
                    modifier = Modifier.testTag("selection_top_app_bar")
                )
            } else {
                // Normal Scan TopAppBar
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = uiState.session?.name ?: "Host Scanner",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 17.sp,
                                maxLines = 1
                            )
                            if (uiState.currentHost.isNotBlank() && uiState.isScanning) {
                                Text(
                                    text = "Scanning: ${uiState.currentHost}",
                                    color = AccentCyan,
                                    fontSize = 11.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("scan_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = TextPrimary
                            )
                        }
                    },
                    actions = {
                        // Pause / Resume button
                        if (uiState.isScanning) {
                            if (uiState.isPaused) {
                                IconButton(
                                    onClick = { viewModel.resumeScan() },
                                    modifier = Modifier.testTag("resume_scan_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Resume",
                                        tint = AccentCyan
                                    )
                                }
                            } else {
                                IconButton(
                                    onClick = { viewModel.pauseScan() },
                                    modifier = Modifier.testTag("pause_scan_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Pause,
                                        contentDescription = "Pause",
                                        tint = Warning
                                    )
                                }
                            }
                            IconButton(
                                onClick = { viewModel.stopScan() },
                                modifier = Modifier.testTag("stop_scan_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop",
                                    tint = Warning
                                )
                            }
                        } else {
                            if (uiState.scanned < uiState.total) {
                                IconButton(
                                    onClick = { viewModel.resumeStoppedScan() },
                                    modifier = Modifier.testTag("resume_stopped_scan_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Resume Scan",
                                        tint = AccentCyan
                                    )
                                }
                            }
                        }

                        // Search Toggle
                        IconButton(
                            onClick = { showSearchBar = !showSearchBar },
                            modifier = Modifier.testTag("toggle_search_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = if (showSearchBar) AccentCyan else TextPrimary
                            )
                        }

                        // Export button
                        IconButton(
                            onClick = { showExportSheet = true },
                            modifier = Modifier.testTag("open_export_sheet_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Export",
                                tint = TextPrimary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = AppBarTeal),
                    modifier = Modifier.testTag("scan_progress_top_app_bar")
                )
            }
        },
        bottomBar = {
            ProgressFooter(
                scanned = uiState.scanned,
                total = uiState.total,
                elapsedSeconds = uiState.elapsedSeconds,
                hostsPerSecond = uiState.hostsPerSecond
            )
        },
        containerColor = Background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Optional Search / Filter row
            AnimatedVisibility(visible = showSearchBar) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Surface)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text("Search host, server, code, ASN, title...") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = TextSecondary)
                        },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_filter_input"),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Primary,
                            unfocusedBorderColor = Divider,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )
                }
            }

            // Stats row + Sort & Hide Failed chips
            Card(
                colors = CardDefaults.cardColors(containerColor = Surface),
                shape = RoundedCornerShape(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    StatRow(
                        scanned = uiState.scanned,
                        responded = uiState.responded,
                        total = uiState.total
                    )

                    // Filters and Sort Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Hide Failed filter chip
                        FilterChip(
                            selected = !uiState.showFailedScans,
                            onClick = { viewModel.toggleShowFailedScans(!uiState.showFailedScans) },
                            label = { Text("Live Only (${uiState.responded})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Primary,
                                selectedLabelColor = TextPrimary,
                                containerColor = SurfaceVariant,
                                labelColor = TextSecondary
                            ),
                            modifier = Modifier.testTag("filter_live_only_chip")
                        )

                        // Sort Chips Row
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            SortChip(
                                label = "Code",
                                isSelected = uiState.sortField == SortField.CODE,
                                isAsc = uiState.sortAscending,
                                onClick = { viewModel.setSort(SortField.CODE) }
                            )
                            SortChip(
                                label = "Speed",
                                isSelected = uiState.sortField == SortField.MS,
                                isAsc = uiState.sortAscending,
                                onClick = { viewModel.setSort(SortField.MS) }
                            )
                            SortChip(
                                label = "Server",
                                isSelected = uiState.sortField == SortField.SERVER,
                                isAsc = uiState.sortAscending,
                                onClick = { viewModel.setSort(SortField.SERVER) }
                            )
                            SortChip(
                                label = "Hash",
                                isSelected = uiState.sortField == SortField.FAVICON,
                                isAsc = uiState.sortAscending,
                                onClick = { viewModel.setSort(SortField.FAVICON) }
                            )
                        }
                    }
                }
            }

            // Real-time Auto-Save Output Banner
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceVariant),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("auto_save_banner")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = AccentCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = uiState.autoSavePath.ifBlank { "Download/HostCheckerPro/${uiState.outName}" },
                                    color = TextPrimary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${uiState.responded} live hosts saved (separated by code)",
                                    color = AccentCyan,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            // View all saved files list button
                            IconButton(
                                onClick = { showFilesSheet = true },
                                modifier = Modifier.size(32.dp).testTag("view_all_files_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Description,
                                    contentDescription = "View Saved Files",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Share live_hosts.txt button
                            IconButton(
                                onClick = {
                                    val file = viewModel.getShareableAutoSaveFile(context)
                                    if (file != null && file.exists()) {
                                        try {
                                            val intent = AutoSaveManager.createShareIntent(context, file)
                                            context.startActivity(Intent.createChooser(intent, "Share live hosts"))
                                        } catch (e: Exception) {
                                            scope.launch {
                                                snackbarHostState.showSnackbar("Share failed: ${e.message}")
                                            }
                                        }
                                    } else {
                                        scope.launch {
                                            snackbarHostState.showSnackbar("No live hosts saved yet.")
                                        }
                                    }
                                },
                                modifier = Modifier.size(32.dp).testTag("share_live_file_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = "Share live_hosts.txt",
                                    tint = TextPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            // Open folder button
                            IconButton(
                                onClick = {
                                    viewModel.openAutoSaveFolder(context)
                                },
                                modifier = Modifier.size(32.dp).testTag("open_folder_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = "Open Downloads Folder",
                                    tint = AccentCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Real-time chips for individual response code files: 200.txt, 403.txt, etc.
                    if (uiState.responded > 0 || uiState.codeCounts.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // live_hosts.txt chip
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Primary.copy(alpha = 0.18f))
                                    .border(1.dp, Primary.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                    .clickable {
                                        val file = viewModel.getShareableAutoSaveFile(context)
                                        if (file != null && file.exists()) {
                                            try {
                                                val intent = AutoSaveManager.createViewFileIntent(context, file)
                                                context.startActivity(Intent.createChooser(intent, "Open live_hosts.txt"))
                                            } catch (e: Exception) {
                                                scope.launch { snackbarHostState.showSnackbar("Open error: ${e.message}") }
                                            }
                                        }
                                    }
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "📄 live_hosts.txt (${uiState.responded})",
                                    fontSize = 11.sp,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            // Per response code files: 200.txt, 403.txt, etc.
                            for ((code, count) in uiState.codeCounts.toSortedMap()) {
                                val codeColor = when (code) {
                                    in 200..299 -> Status2xx
                                    in 300..399 -> Status3xx
                                    in 400..499 -> Status4xx
                                    else -> Status5xx
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(codeColor.copy(alpha = 0.15f))
                                        .border(1.dp, codeColor.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                                        .clickable {
                                            val file = viewModel.getFileForCode(context, code)
                                            if (file != null && file.exists()) {
                                                try {
                                                    val intent = AutoSaveManager.createViewFileIntent(context, file)
                                                    context.startActivity(Intent.createChooser(intent, "Open $code.txt"))
                                                } catch (e: Exception) {
                                                    scope.launch { snackbarHostState.showSnackbar("Open error: ${e.message}") }
                                                }
                                            } else {
                                                scope.launch { snackbarHostState.showSnackbar("File: $code.txt (${count} hosts)") }
                                            }
                                        }
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "$code.txt ($count)",
                                        fontSize = 11.sp,
                                        color = codeColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // LazyColumn of ResultCards
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("results_lazy_column"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (results.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (uiState.isScanning) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(36.dp),
                                        color = AccentCyan,
                                        strokeWidth = 3.dp
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Scanning hosts...\nLive responsive hosts will appear here at the top in real time",
                                        color = TextSecondary,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                } else if (uiState.searchQuery.isNotBlank()) {
                                    Text(
                                        text = "No results matching \"${uiState.searchQuery}\"",
                                        color = TextSecondary,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                } else {
                                    Text(
                                        text = "No live hosts found for this session.",
                                        color = TextSecondary,
                                        fontSize = 13.sp,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                } else {
                    items(
                        items = results,
                        key = { it.id }
                    ) { result ->
                        val isSelected = uiState.selectedIds.contains(result.id)
                        Box(modifier = Modifier.animateItem()) {
                            ResultCard(
                                result = result,
                                isSelected = isSelected,
                                isSelectionMode = uiState.isSelectionMode,
                                onClick = {
                                    if (uiState.isSelectionMode) {
                                        viewModel.toggleSelection(result.id)
                                    } else {
                                        onOpenResultDetail(result.id)
                                    }
                                },
                                onLongClick = {
                                    onOpenResultDetail(result.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Export Bottom Sheet
    if (showExportSheet) {
        ExportSheet(
            sheetState = exportSheetState,
            hasSelected = uiState.selectedIds.isNotEmpty(),
            onDismiss = { showExportSheet = false },
            onExport = { format, scopeChoice, destination ->
                showExportSheet = false
                scope.launch {
                    val file = viewModel.exportData(context, format, scopeChoice)
                    if (file != null) {
                        if (destination == ExportDestination.SHARE) {
                            val shareIntent = Exporter.createShareIntent(context, file)
                            context.startActivity(Intent.createChooser(shareIntent, "Share scan export"))
                        } else {
                            snackbarHostState.showSnackbar("Export saved to: ${file.absolutePath}")
                        }
                    } else {
                        snackbarHostState.showSnackbar("Export failed or empty.")
                    }
                }
            }
        )
    }

    if (showFilesSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilesSheet = false },
            sheetState = filesSheetState,
            containerColor = SurfaceVariant
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Saved Scan Files",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "📁 Download/HostCheckerPro/${uiState.outName}/",
                            fontSize = 11.sp,
                            color = AccentCyan,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { showFilesSheet = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Live hosts are automatically saved in real-time into separated response code files:",
                    fontSize = 12.sp,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                val savedFiles = viewModel.getAllSavedFiles(context)
                if (savedFiles.isEmpty()) {
                    Text(
                        text = "No files created yet. Live hosts will be written here as responses arrive.",
                        fontSize = 13.sp,
                        color = TextSecondary,
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(savedFiles) { file ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Background),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        val isCodeFile = file.nameWithoutExtension.toIntOrNull() != null
                                        val code = file.nameWithoutExtension.toIntOrNull() ?: 0
                                        val iconTint = if (isCodeFile) {
                                            when (code) {
                                                in 200..299 -> Status2xx
                                                in 300..399 -> Status3xx
                                                in 400..499 -> Status4xx
                                                else -> Status5xx
                                            }
                                        } else AccentCyan

                                        Icon(
                                            imageVector = Icons.Default.InsertDriveFile,
                                            contentDescription = null,
                                            tint = iconTint,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = file.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = TextPrimary
                                            )
                                            val lineCount = try { file.readLines().size } catch (e: Exception) { 0 }
                                            Text(
                                                text = "$lineCount hosts • ${file.length() / 1024 + 1} KB",
                                                fontSize = 11.sp,
                                                color = TextSecondary
                                            )
                                        }
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        // Open
                                        IconButton(
                                            onClick = {
                                                try {
                                                    val intent = AutoSaveManager.createViewFileIntent(context, file)
                                                    context.startActivity(Intent.createChooser(intent, "Open ${file.name}"))
                                                } catch (e: Exception) {
                                                    scope.launch { snackbarHostState.showSnackbar("Viewer error: ${e.message}") }
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.OpenInNew,
                                                contentDescription = "Open",
                                                tint = AccentCyan,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // Share
                                        IconButton(
                                            onClick = {
                                                try {
                                                    val intent = AutoSaveManager.createShareIntent(context, file)
                                                    context.startActivity(Intent.createChooser(intent, "Share ${file.name}"))
                                                } catch (e: Exception) {
                                                    scope.launch { snackbarHostState.showSnackbar("Share error: ${e.message}") }
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Share,
                                                contentDescription = "Share",
                                                tint = TextPrimary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                androidx.compose.material3.Button(
                    onClick = {
                        showFilesSheet = false
                        viewModel.openAutoSaveFolder(context)
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Primary),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, tint = TextPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Output Folder in Files App", color = TextPrimary, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun SortChip(
    label: String,
    isSelected: Boolean,
    isAsc: Boolean,
    onClick: () -> Unit
) {
    val text = if (isSelected) "$label ${if (isAsc) "▲" else "▼"}" else label
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(text, fontSize = 11.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Primary,
            selectedLabelColor = TextPrimary,
            containerColor = SurfaceVariant,
            labelColor = TextSecondary
        ),
        modifier = Modifier.testTag("sort_${label.lowercase()}")
    )
}

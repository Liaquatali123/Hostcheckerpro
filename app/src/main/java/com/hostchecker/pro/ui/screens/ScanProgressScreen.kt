package com.hostchecker.pro.ui.screens

import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hostchecker.pro.domain.export.Exporter
import com.hostchecker.pro.domain.model.ScanResult
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
import com.hostchecker.pro.ui.theme.Surface
import com.hostchecker.pro.ui.theme.SurfaceVariant
import com.hostchecker.pro.ui.theme.TextPrimary
import com.hostchecker.pro.ui.theme.TextSecondary
import com.hostchecker.pro.ui.theme.Warning
import com.hostchecker.pro.ui.viewmodel.ScanUiState
import com.hostchecker.pro.ui.viewmodel.ScanViewModel
import com.hostchecker.pro.ui.viewmodel.SortField
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

    var showSearchBar by remember { mutableStateOf(false) }
    var showExportSheet by remember { mutableStateOf(false) }
    val exportSheetState = rememberModalBottomSheetState()

    LaunchedEffect(sessionId) {
        viewModel.loadSession(sessionId)
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
                            label = { Text("Live Only") },
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

            // LazyColumn of ResultCards
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("results_lazy_column"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(
                    items = results,
                    key = { it.id }
                ) { result ->
                    val isSelected = uiState.selectedIds.contains(result.id)
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

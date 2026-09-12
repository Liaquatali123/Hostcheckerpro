package com.hostchecker.pro.ui.viewmodel

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hostchecker.pro.data.prefs.SettingsDataStore
import com.hostchecker.pro.data.repo.ResultRepository
import com.hostchecker.pro.data.repo.SessionRepository
import com.hostchecker.pro.domain.export.Exporter
import com.hostchecker.pro.domain.model.ScanConfig
import com.hostchecker.pro.domain.model.ScanResult
import com.hostchecker.pro.domain.model.Session
import com.hostchecker.pro.domain.scanner.HostScanner
import com.hostchecker.pro.util.AutoSaveManager
import com.hostchecker.pro.util.NotificationHelper
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class SortField {
    HOST, CODE, MS, SERVER, FAVICON, TIME
}

data class ScanUiState(
    val session: Session? = null,
    val isScanning: Boolean = false,
    val isPaused: Boolean = false,
    val scanned: Int = 0,
    val responded: Int = 0,
    val total: Int = 0,
    val elapsedSeconds: Long = 0L,
    val hostsPerSecond: Double = 0.0,
    val currentHost: String = "",
    val showFailedScans: Boolean = false,
    val searchQuery: String = "",
    val sortField: SortField = SortField.TIME,
    val sortAscending: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val isSelectionMode: Boolean = false,
    val outName: String = "",
    val autoSavePath: String = "",
    val codeCounts: Map<Int, Int> = emptyMap()
)

class ScanViewModel(
    private val sessionRepository: SessionRepository,
    private val resultRepository: ResultRepository,
    private val hostScanner: HostScanner,
    private val settingsDataStore: SettingsDataStore,
    private val notificationHelper: NotificationHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScanUiState())
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var currentSessionId: Long = 0
    private var allHostsForSession: List<String> = emptyList()
    private var timerJob: Job? = null
    private var resultsJob: Job? = null

    // Raw results from Room for current session
    private val _rawResults = MutableStateFlow<List<ScanResult>>(emptyList())

    // Combined filtered & sorted results for UI
    val filteredResults: StateFlow<List<ScanResult>> = combine(
        _rawResults,
        _uiState
    ) { results, state ->
        val query = state.searchQuery.trim().lowercase()
        var filtered = results.filter { r ->
            if (!state.showFailedScans && r.failed) {
                false
            } else if (query.isEmpty()) {
                true
            } else {
                r.host.lowercase().contains(query) ||
                        r.server.lowercase().contains(query) ||
                        r.code.toString().contains(query) ||
                        r.title.lowercase().contains(query) ||
                        r.ip.contains(query) ||
                        r.asn.lowercase().contains(query)
            }
        }

        filtered = when (state.sortField) {
            SortField.HOST -> if (state.sortAscending) filtered.sortedBy { it.host } else filtered.sortedByDescending { it.host }
            SortField.CODE -> if (state.sortAscending) filtered.sortedBy { it.code } else filtered.sortedByDescending { it.code }
            SortField.MS -> if (state.sortAscending) filtered.sortedBy { it.ms } else filtered.sortedByDescending { it.ms }
            SortField.SERVER -> if (state.sortAscending) filtered.sortedBy { it.server } else filtered.sortedByDescending { it.server }
            SortField.FAVICON -> if (state.sortAscending) filtered.sortedBy { it.faviconHash } else filtered.sortedByDescending { it.faviconHash }
            SortField.TIME -> if (state.sortAscending) filtered.sortedBy { it.id } else filtered.sortedByDescending { it.id }
        }
        filtered
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    init {
        // Observe scanner events
        viewModelScope.launch {
            hostScanner.scanEvents.conflate().collect { event ->
                when (event) {
                    is HostScanner.ScanEvent.Progress -> {
                        _uiState.value = _uiState.value.copy(
                            scanned = event.scanned,
                            responded = event.responded,
                            total = event.total,
                            currentHost = event.currentHost,
                            hostsPerSecond = event.hostsPerSecond
                        )
                    }
                    is HostScanner.ScanEvent.Complete -> {
                        _uiState.value = _uiState.value.copy(
                            isScanning = false,
                            isPaused = false,
                            scanned = event.scanned,
                            responded = event.responded
                        )
                        stopTimer()
                        notificationHelper.showScanCompleteNotification(event.responded, event.total)
                    }
                    is HostScanner.ScanEvent.NetworkWarning -> {
                        notificationHelper.showNetworkWarningNotification(event.consecutiveFails)
                    }
                    is HostScanner.ScanEvent.Paused -> {
                        _uiState.value = _uiState.value.copy(isPaused = true)
                    }
                    is HostScanner.ScanEvent.Resumed -> {
                        _uiState.value = _uiState.value.copy(isPaused = false)
                    }
                    is HostScanner.ScanEvent.Stopped -> {
                        _uiState.value = _uiState.value.copy(isScanning = false, isPaused = false)
                        stopTimer()
                    }
                }
            }
        }
    }

    private fun observeResults(sessionId: Long, showFailed: Boolean) {
        if (sessionId <= 0L) return
        resultsJob?.cancel()
        resultsJob = viewModelScope.launch {
            val resultsFlow = if (!showFailed) {
                // Live Only: load all responsive hosts (failed = 0)
                resultRepository.getLiveResultsForSession(sessionId)
            } else {
                // All: load recent results including failures
                resultRepository.getRecentResultsForSession(sessionId)
            }
            resultsFlow.conflate().collect { list ->
                _rawResults.value = list
                val counts = list.filter { !it.failed && it.code > 0 }
                    .groupingBy { it.code }
                    .eachCount()
                _uiState.value = _uiState.value.copy(codeCounts = counts)
            }
        }
    }

    fun loadSession(sessionId: Long, hosts: List<String> = emptyList()) {
        currentSessionId = sessionId
        if (hosts.isNotEmpty()) {
            allHostsForSession = hosts
        }

        viewModelScope.launch {
            val session = sessionRepository.getSessionOnce(sessionId)
            val folderName = if (session != null) {
                AutoSaveManager.sanitizeFolderName(session.fileName.substringBeforeLast("."))
                    .ifBlank { "scan_$sessionId" }
            } else "scan_$sessionId"

            _uiState.value = _uiState.value.copy(
                session = session,
                scanned = session?.scanned ?: 0,
                responded = session?.responded ?: 0,
                total = session?.total ?: 0,
                isScanning = hostScanner.isScanningNow(),
                isPaused = hostScanner.isPausedNow(),
                outName = folderName,
                autoSavePath = AutoSaveManager.getDisplayPath(folderName)
            )

            observeResults(sessionId, _uiState.value.showFailedScans)
        }
    }

    fun startOrResumeScan(
        sessionId: Long,
        hosts: List<String>,
        config: ScanConfig,
        startIndex: Int = 0
    ) {
        currentSessionId = sessionId
        allHostsForSession = hosts

        val folderName = if (config.outName.isNotBlank()) config.outName else "scan_$sessionId"
        _uiState.value = _uiState.value.copy(
            isScanning = true,
            isPaused = false,
            total = hosts.size,
            scanned = startIndex,
            outName = folderName,
            autoSavePath = AutoSaveManager.getDisplayPath(folderName)
        )

        observeResults(sessionId, _uiState.value.showFailedScans)
        startTimer()
        hostScanner.startScan(
            sessionId = sessionId,
            hosts = hosts,
            config = config,
            startIndex = startIndex
        )
    }

    fun pauseScan() {
        hostScanner.pauseScan()
        _uiState.value = _uiState.value.copy(isPaused = true)
    }

    fun resumeScan() {
        hostScanner.resumeScan()
        _uiState.value = _uiState.value.copy(isPaused = false)
    }

    fun stopScan() {
        hostScanner.stopScan()
        _uiState.value = _uiState.value.copy(isScanning = false, isPaused = false)
        stopTimer()
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                if (!_uiState.value.isPaused && _uiState.value.isScanning) {
                    _uiState.value = _uiState.value.copy(
                        elapsedSeconds = _uiState.value.elapsedSeconds + 1
                    )
                }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun toggleShowFailedScans(show: Boolean) {
        _uiState.value = _uiState.value.copy(showFailedScans = show)
        if (currentSessionId > 0) {
            observeResults(currentSessionId, show)
        }
    }

    fun getShareableAutoSaveFile(context: Context): File? {
        return AutoSaveManager.getShareableCleanFile(context, currentSessionId)
    }

    fun getFileForCode(context: Context, code: Int): File? {
        return AutoSaveManager.getFileForCode(context, currentSessionId, code)
    }

    fun getAllSavedFiles(context: Context): List<File> {
        return AutoSaveManager.getAllSavedFiles(context, currentSessionId)
    }

    fun openAutoSaveFolder(context: Context) {
        val folder = _uiState.value.outName.ifBlank { "scan_$currentSessionId" }
        AutoSaveManager.openOutputFolder(context, folder)
    }

    fun setSort(field: SortField) {
        val current = _uiState.value
        val newAsc = if (current.sortField == field) !current.sortAscending else false
        _uiState.value = current.copy(sortField = field, sortAscending = newAsc)
    }

    // Selection mode
    fun toggleSelection(resultId: Long) {
        val selected = _uiState.value.selectedIds.toMutableSet()
        if (selected.contains(resultId)) {
            selected.remove(resultId)
        } else {
            selected.add(resultId)
        }
        _uiState.value = _uiState.value.copy(
            selectedIds = selected,
            isSelectionMode = selected.isNotEmpty()
        )
    }

    fun selectAll() {
        val allIds = filteredResults.value.map { it.id }.toSet()
        _uiState.value = _uiState.value.copy(
            selectedIds = allIds,
            isSelectionMode = allIds.isNotEmpty()
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedIds = emptySet(),
            isSelectionMode = false
        )
    }

    fun copySelectedHosts(context: Context) {
        val selectedIds = _uiState.value.selectedIds
        val selectedResults = _rawResults.value.filter { it.id in selectedIds }
        val hostsText = selectedResults.joinToString("\n") { it.host }
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Hosts", hostsText)
        clipboard.setPrimaryClip(clip)
        clearSelection()
    }

    fun deleteSelected() {
        val selectedIds = _uiState.value.selectedIds.toList()
        viewModelScope.launch {
            resultRepository.deleteResults(selectedIds)
            clearSelection()
        }
    }

    suspend fun exportData(
        context: Context,
        format: Exporter.Format,
        scope: Exporter.Scope
    ): File? {
        val allResults = resultRepository.getAllResultsOnce(currentSessionId)
        val exportList = when (scope) {
            Exporter.Scope.ALL -> allResults
            Exporter.Scope.LIVE_ONLY -> allResults.filter { !it.failed }
            Exporter.Scope.SELECTED -> {
                val selected = _uiState.value.selectedIds
                allResults.filter { it.id in selected }
            }
        }

        val content = when (format) {
            Exporter.Format.CSV -> Exporter.generateCsv(exportList)
            Exporter.Format.TXT -> Exporter.generateTxt(exportList)
            Exporter.Format.JSON -> Exporter.generateJson(exportList)
        }

        val sessionName = _uiState.value.session?.name ?: "session_$currentSessionId"
        return Exporter.saveExportFile(context, sessionName, format, content)
    }

    override fun onCleared() {
        super.onCleared()
        stopTimer()
    }

    class Factory(
        private val sessionRepository: SessionRepository,
        private val resultRepository: ResultRepository,
        private val hostScanner: HostScanner,
        private val settingsDataStore: SettingsDataStore,
        private val notificationHelper: NotificationHelper
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScanViewModel(
                sessionRepository,
                resultRepository,
                hostScanner,
                settingsDataStore,
                notificationHelper
            ) as T
        }
    }
}

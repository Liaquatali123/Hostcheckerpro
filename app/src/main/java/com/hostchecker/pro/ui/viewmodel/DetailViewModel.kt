package com.hostchecker.pro.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hostchecker.pro.data.repo.ResultRepository
import com.hostchecker.pro.domain.model.ScanConfig
import com.hostchecker.pro.domain.model.ScanResult
import com.hostchecker.pro.domain.scanner.HostScanner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DetailViewModel(
    private val resultRepository: ResultRepository,
    private val hostScanner: HostScanner
) : ViewModel() {

    private val _result = MutableStateFlow<ScanResult?>(null)
    val result: StateFlow<ScanResult?> = _result.asStateFlow()

    private val _isRescanning = MutableStateFlow(false)
    val isRescanning: StateFlow<Boolean> = _isRescanning.asStateFlow()

    fun loadResult(resultId: Long) {
        viewModelScope.launch {
            val item = resultRepository.getResultByIdOnce(resultId)
            _result.value = item
        }
    }

    fun rescanHost() {
        val current = _result.value ?: return
        viewModelScope.launch {
            _isRescanning.value = true
            // Run immediate single scan on host
            hostScanner.startScan(
                sessionId = current.sessionId,
                hosts = listOf(current.host),
                config = ScanConfig(threads = 1, timeoutSeconds = 10),
                startIndex = 0
            )
            // Reload after brief delay
            kotlinx.coroutines.delay(2000)
            val updated = resultRepository.getResultByIdOnce(current.id)
            if (updated != null) {
                _result.value = updated
            }
            _isRescanning.value = false
        }
    }

    class Factory(
        private val resultRepository: ResultRepository,
        private val hostScanner: HostScanner
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DetailViewModel(resultRepository, hostScanner) as T
        }
    }
}

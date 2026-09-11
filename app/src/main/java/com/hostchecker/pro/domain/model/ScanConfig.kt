package com.hostchecker.pro.domain.model

data class ScanConfig(
    val threads: Int = 6,
    val timeoutSeconds: Int = 10,
    val filterText: String = "",
    val isRegex: Boolean = false,
    val retryFailed: Boolean = false,
    val stealthMode: Boolean = false,
    val jitterEnabled: Boolean = false,
    val showFailed: Boolean = true
)

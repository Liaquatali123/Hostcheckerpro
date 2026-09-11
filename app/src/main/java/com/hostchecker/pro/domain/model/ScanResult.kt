package com.hostchecker.pro.domain.model

data class ScanResult(
    val id: Long = 0,
    val sessionId: Long,
    val host: String,
    val ip: String = "",
    val asn: String = "",
    val org: String = "",
    val server: String = "",
    val code: Int = 0,
    val ms: Long = 0L,
    val title: String = "",
    val faviconHash: String = "",
    val san: List<String> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val cloudflareFronted: Boolean = false,
    val cloudflareOrigin: String = "",
    val failed: Boolean = false,
    val errorMessage: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

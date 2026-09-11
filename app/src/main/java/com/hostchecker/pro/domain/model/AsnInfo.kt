package com.hostchecker.pro.domain.model

data class AsnInfo(
    val ip: String,
    val asn: String,
    val org: String,
    val cachedAt: Long = System.currentTimeMillis()
)

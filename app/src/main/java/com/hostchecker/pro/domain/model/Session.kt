package com.hostchecker.pro.domain.model

data class Session(
    val id: Long = 0,
    val name: String,
    val fileName: String,
    val total: Int,
    val scanned: Int = 0,
    val responded: Int = 0,
    val threads: Int = 6,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    val status: String = "RUNNING", // RUNNING, PAUSED, COMPLETED, STOPPED
    val lastIndex: Int = 0
)

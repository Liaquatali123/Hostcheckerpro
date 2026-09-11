package com.hostchecker.pro.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.hostchecker.pro.domain.model.ScanResult
import org.json.JSONObject

@Entity(
    tableName = "scan_results",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["sessionId", "createdAt"])
    ]
)
data class ResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val host: String,
    val ip: String,
    val asn: String,
    val org: String,
    val server: String,
    val code: Int,
    val ms: Long,
    val title: String,
    val faviconHash: String,
    val san: String,
    val failed: Boolean,
    val headersJson: String = "",
    val cloudflareFronted: Boolean = false,
    val cloudflareOrigin: String = "",
    val errorMessage: String = "",
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): ScanResult {
        val sanList = if (san.isBlank()) {
            emptyList()
        } else {
            san.split(";").map { it.trim() }.filter { it.isNotEmpty() }
        }

        val headersMap = mutableMapOf<String, String>()
        if (headersJson.isNotBlank()) {
            runCatching {
                val json = JSONObject(headersJson)
                val keys = json.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    headersMap[key] = json.optString(key)
                }
            }
        }

        return ScanResult(
            id = id,
            sessionId = sessionId,
            host = host,
            ip = ip,
            asn = asn,
            org = org,
            server = server,
            code = code,
            ms = ms,
            title = title,
            faviconHash = faviconHash,
            san = sanList,
            headers = headersMap,
            cloudflareFronted = cloudflareFronted,
            cloudflareOrigin = cloudflareOrigin,
            failed = failed,
            errorMessage = errorMessage,
            createdAt = createdAt
        )
    }

    companion object {
        fun fromDomain(result: ScanResult): ResultEntity {
            val headersJson = if (result.headers.isNotEmpty()) {
                val json = JSONObject()
                result.headers.forEach { (k, v) -> json.put(k, v) }
                json.toString()
            } else ""

            return ResultEntity(
                id = result.id,
                sessionId = result.sessionId,
                host = result.host,
                ip = result.ip,
                asn = result.asn,
                org = result.org,
                server = result.server,
                code = result.code,
                ms = result.ms,
                title = result.title,
                faviconHash = result.faviconHash,
                san = result.san.joinToString(";"),
                failed = result.failed,
                headersJson = headersJson,
                cloudflareFronted = result.cloudflareFronted,
                cloudflareOrigin = result.cloudflareOrigin,
                errorMessage = result.errorMessage,
                createdAt = result.createdAt
            )
        }
    }
}

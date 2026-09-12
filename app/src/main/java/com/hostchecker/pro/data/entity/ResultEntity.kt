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
        Index(value = ["sessionId", "createdAt"]),
        Index(value = ["sessionId", "host"])
    ]
)
data class ResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: Long,
    val host: String,
    val scheme: String = "HTTPS",
    val requestedUrl: String = "",
    val finalUrl: String = "",
    val originalCode: Int = 0,
    val finalCode: Int = 0,
    val redirectCount: Int = 0,
    val redirectChain: String = "",
    val httpMethod: String = "GET",
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

        val chainList = if (redirectChain.isBlank()) {
            emptyList()
        } else {
            redirectChain.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
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

        val primaryCode = if (originalCode > 0) originalCode else code

        return ScanResult(
            id = id,
            sessionId = sessionId,
            host = host,
            scheme = scheme.ifBlank { "HTTPS" },
            requestedUrl = requestedUrl,
            finalUrl = finalUrl,
            httpMethod = httpMethod.ifBlank { "GET" },
            originalCode = if (originalCode > 0) originalCode else primaryCode,
            finalCode = if (finalCode > 0) finalCode else primaryCode,
            redirectCount = redirectCount,
            redirectChain = chainList,
            ip = ip,
            asn = asn,
            org = org,
            server = server,
            code = primaryCode,
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

            val primaryCode = if (result.originalCode > 0) result.originalCode else result.code

            return ResultEntity(
                id = result.id,
                sessionId = result.sessionId,
                host = result.host,
                scheme = result.scheme.ifBlank { "HTTPS" },
                requestedUrl = result.requestedUrl,
                finalUrl = result.finalUrl,
                originalCode = if (result.originalCode > 0) result.originalCode else primaryCode,
                finalCode = if (result.finalCode > 0) result.finalCode else primaryCode,
                redirectCount = result.redirectCount,
                redirectChain = result.redirectChain.joinToString("\n"),
                httpMethod = result.httpMethod.ifBlank { "GET" },
                ip = result.ip,
                asn = result.asn,
                org = result.org,
                server = result.server,
                code = primaryCode,
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


package com.hostchecker.pro.domain.export

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.hostchecker.pro.domain.model.ScanResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object Exporter {

    enum class Format {
        CSV, TXT, JSON
    }

    enum class Scope {
        ALL, LIVE_ONLY, SELECTED
    }

    fun generateCsv(results: List<ScanResult>): String {
        val sb = StringBuilder()
        sb.append("Host,IP,ASN,Org,Server,StatusCode,ResponseTimeMs,Title,FaviconHash,SAN,CloudflareFronted,CloudflareOrigin,Failed,ErrorMessage\n")
        for (r in results) {
            sb.append(escapeCsv(r.host)).append(",")
            sb.append(escapeCsv(r.ip)).append(",")
            sb.append(escapeCsv(r.asn)).append(",")
            sb.append(escapeCsv(r.org)).append(",")
            sb.append(escapeCsv(r.server)).append(",")
            sb.append(r.code).append(",")
            sb.append(r.ms).append(",")
            sb.append(escapeCsv(r.title)).append(",")
            sb.append(escapeCsv(r.faviconHash)).append(",")
            sb.append(escapeCsv(r.san.joinToString("; "))).append(",")
            sb.append(r.cloudflareFronted).append(",")
            sb.append(escapeCsv(r.cloudflareOrigin)).append(",")
            sb.append(r.failed).append(",")
            sb.append(escapeCsv(r.errorMessage)).append("\n")
        }
        return sb.toString()
    }

    fun generateTxt(results: List<ScanResult>): String {
        val sb = StringBuilder()
        for (r in results) {
            sb.append(r.host).append("\n")
        }
        return sb.toString()
    }

    fun generateJson(results: List<ScanResult>): String {
        val jsonArray = JSONArray()
        for (r in results) {
            val obj = JSONObject()
            obj.put("host", r.host)
            obj.put("ip", r.ip)
            obj.put("asn", r.asn)
            obj.put("org", r.org)
            obj.put("server", r.server)
            obj.put("code", r.code)
            obj.put("responseTimeMs", r.ms)
            obj.put("title", r.title)
            obj.put("faviconHash", r.faviconHash)
            obj.put("san", JSONArray(r.san))
            val headersObj = JSONObject()
            r.headers.forEach { (k, v) -> headersObj.put(k, v) }
            obj.put("headers", headersObj)
            obj.put("cloudflareFronted", r.cloudflareFronted)
            obj.put("cloudflareOrigin", r.cloudflareOrigin)
            obj.put("failed", r.failed)
            obj.put("errorMessage", r.errorMessage)
            jsonArray.put(obj)
        }
        return jsonArray.toString(2)
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }

    suspend fun saveExportFile(
        context: Context,
        sessionName: String,
        format: Format,
        content: String
    ): File = withContext(Dispatchers.IO) {
        val exportsDir = File(context.getExternalFilesDir(null), "exports")
        if (!exportsDir.exists()) {
            exportsDir.mkdirs()
        }

        val sanitizedSession = sessionName.replace(Regex("[^a-zA-Z0-9_-]"), "_").take(30)
        val timestamp = System.currentTimeMillis()
        val extension = when (format) {
            Format.CSV -> "csv"
            Format.TXT -> "txt"
            Format.JSON -> "json"
        }
        val file = File(exportsDir, "scan_${sanitizedSession}_$timestamp.$extension")
        file.writeText(content, Charsets.UTF_8)
        file
    }

    fun createShareIntent(context: Context, file: File): Intent {
        val authority = "${context.packageName}.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, file)

        val mimeType = when (file.extension.lowercase()) {
            "csv" -> "text/csv"
            "json" -> "application/json"
            else -> "text/plain"
        }

        return Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri)
            putExtra(Intent.EXTRA_SUBJECT, "Host Checker Pro Export - ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

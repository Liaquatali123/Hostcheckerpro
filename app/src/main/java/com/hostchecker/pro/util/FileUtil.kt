package com.hostchecker.pro.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class HostImportResult(
    val fileName: String,
    val validHosts: List<String>,
    val duplicateCount: Int,
    val invalidCount: Int
)

object FileUtil {

    suspend fun readHostsFromUri(context: Context, uri: Uri): HostImportResult =
        withContext(Dispatchers.IO) {
            val fileName = getFileName(context, uri) ?: "hostlist.txt"
            val seen = LinkedHashSet<String>()
            var duplicates = 0
            var invalid = 0

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            // Extract host candidate from comma, space, or tab separated lines (e.g. CSV or list)
                            val rawCandidate = trimmed.split(",", " ", "\t").first().trim()
                            val cleaned = NetworkUtil.cleanHostInput(rawCandidate)
                            if (NetworkUtil.isValidHost(cleaned)) {
                                if (!seen.add(cleaned)) {
                                    duplicates++
                                }
                            } else {
                                invalid++
                            }
                        }
                        line = reader.readLine()
                    }
                }
            }

            HostImportResult(
                fileName = fileName,
                validHosts = seen.toList(),
                duplicateCount = duplicates,
                invalidCount = invalid
            )
        }

    fun parseHostsFromText(rawText: String, defaultName: String = "Pasted List"): HostImportResult {
        val seen = LinkedHashSet<String>()
        var duplicates = 0
        var invalid = 0

        rawText.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                val rawCandidate = trimmed.split(",", " ", "\t").first().trim()
                val cleaned = NetworkUtil.cleanHostInput(rawCandidate)
                if (NetworkUtil.isValidHost(cleaned)) {
                    if (!seen.add(cleaned)) {
                        duplicates++
                    }
                } else {
                    invalid++
                }
            }
        }

        return HostImportResult(
            fileName = defaultName,
            validHosts = seen.toList(),
            duplicateCount = duplicates,
            invalidCount = invalid
        )
    }

    fun getFileName(context: Context, uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = it.getString(index)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1 && result != null) {
                result = result?.substring(cut + 1)
            }
        }
        return result
    }
}

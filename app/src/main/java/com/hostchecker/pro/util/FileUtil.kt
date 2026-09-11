package com.hostchecker.pro.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FileUtil {

    suspend fun readHostsFromUri(context: Context, uri: Uri): Pair<String, List<String>> =
        withContext(Dispatchers.IO) {
            val fileName = getFileName(context, uri) ?: "hostlist.txt"
            val hosts = mutableListOf<String>()

            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                    var line = reader.readLine()
                    while (line != null) {
                        val trimmed = line.trim()
                        if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                            // Extract host if line contains comma or space (e.g. CSV or space-separated)
                            val host = trimmed.split(",", " ", "\t").first().trim()
                            if (host.isNotEmpty()) {
                                hosts.add(host)
                            }
                        }
                        line = reader.readLine()
                    }
                }
            }

            Pair(fileName, hosts)
        }

    fun parseHostsFromText(rawText: String): List<String> {
        val hosts = mutableListOf<String>()
        rawText.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#")) {
                val host = trimmed.split(",", " ", "\t").first().trim()
                if (host.isNotEmpty()) {
                    hosts.add(host)
                }
            }
        }
        return hosts
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

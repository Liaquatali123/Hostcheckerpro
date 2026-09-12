package com.hostchecker.pro.util

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import android.util.Log
import com.hostchecker.pro.domain.model.ScanResult
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

object AutoSaveManager {
    private const val TAG = "AutoSaveManager"
    const val MAIN_STORAGE_APP_FOLDER = "HostCheckerPro"

    data class SessionTarget(
        val displayFolder: String,
        val targetDirs: List<File>,
        val cleanFiles: List<File>,
        val detailsFiles: List<File>,
        val latestFiles: List<File>,
        val lock: Any = Any()
    )

    private val activeSessions = ConcurrentHashMap<Long, SessionTarget>()

    /**
     * Determines all writable root folders for HostCheckerPro.
     * Always ensures the main storage folder exists.
     */
    fun getAppMainFolders(context: Context?): List<File> {
        val folders = mutableListOf<File>()

        try {
            // 1. Public Downloads / HostCheckerPro
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null) {
                val dir = File(downloadsDir, MAIN_STORAGE_APP_FOLDER)
                if (dir.exists() || dir.mkdirs()) {
                    folders.add(dir)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not access Downloads dir: ${e.message}")
        }

        try {
            // 2. Public Documents / HostCheckerPro
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            if (docsDir != null) {
                val dir = File(docsDir, MAIN_STORAGE_APP_FOLDER)
                if (dir.exists() || dir.mkdirs()) {
                    if (!folders.contains(dir)) folders.add(dir)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not access Documents dir: ${e.message}")
        }

        try {
            // 3. Root External Storage / HostCheckerPro
            val extRoot = Environment.getExternalStorageDirectory()
            if (extRoot != null && extRoot.canWrite()) {
                val dir = File(extRoot, MAIN_STORAGE_APP_FOLDER)
                if (dir.exists() || dir.mkdirs()) {
                    if (!folders.contains(dir)) folders.add(dir)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not access ExternalStorageDirectory: ${e.message}")
        }

        try {
            // 4. App External Files Dir (always 100% accessible without extra permissions)
            if (context != null) {
                val appExt = context.getExternalFilesDir(null)
                if (appExt != null) {
                    val dir = File(appExt, MAIN_STORAGE_APP_FOLDER)
                    if (dir.exists() || dir.mkdirs()) {
                        folders.add(dir)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not access getExternalFilesDir: ${e.message}")
        }

        return folders
    }

    /**
     * Returns a human-friendly display path for the UI.
     */
    fun getDisplayPath(outName: String): String {
        val cleanOut = sanitizeFolderName(outName).ifBlank { "scan" }
        return "HostCheckerPro/$cleanOut"
    }

    /**
     * Initializes auto-saving for a scan session.
     * Creates HostCheckerPro/<outname>/ folder and clean separate scan files.
     */
    fun initSession(
        context: Context?,
        sessionId: Long,
        outName: String
    ): SessionTarget? {
        val cleanOutName = sanitizeFolderName(outName).ifBlank { "scan_$sessionId" }
        val mainFolders = getAppMainFolders(context)

        if (mainFolders.isEmpty()) {
            Log.e(TAG, "No writable folders found for auto-save")
            return null
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val targetDirs = mutableListOf<File>()
        val cleanFiles = mutableListOf<File>()
        val detailsFiles = mutableListOf<File>()
        val latestFiles = mutableListOf<File>()

        for (mainDir in mainFolders) {
            try {
                val sessionDir = File(mainDir, cleanOutName)
                if (!sessionDir.exists()) {
                    sessionDir.mkdirs()
                }

                if (sessionDir.exists() && sessionDir.canWrite()) {
                    targetDirs.add(sessionDir)

                    // 1. Timestamped clean live hosts file (clean, separate per scan)
                    val cleanHostFile = File(sessionDir, "live_hosts_$timestamp.txt")
                    cleanFiles.add(cleanHostFile)

                    // 2. Timestamped detailed CSV file
                    val detailsFile = File(sessionDir, "live_details_$timestamp.csv")
                    if (!detailsFile.exists()) {
                        try {
                            detailsFile.writeText(
                                "Host,IP,StatusCode,ResponseTimeMs,Server,Title,FaviconHash,ASN,Org,CloudflareFronted\n",
                                Charsets.UTF_8
                            )
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed writing CSV header", e)
                        }
                    }
                    detailsFiles.add(detailsFile)

                    // 3. Consolidated latest live hosts file
                    val latestHostFile = File(sessionDir, "live_hosts.txt")
                    latestFiles.add(latestHostFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing dir in ${mainDir.path}", e)
            }
        }

        val target = SessionTarget(
            displayFolder = "$MAIN_STORAGE_APP_FOLDER/$cleanOutName",
            targetDirs = targetDirs,
            cleanFiles = cleanFiles,
            detailsFiles = detailsFiles,
            latestFiles = latestFiles
        )

        activeSessions[sessionId] = target

        // Notify MediaScanner
        for (f in cleanFiles + detailsFiles + latestFiles) {
            scanMediaFile(context, f)
        }

        Log.i(TAG, "Auto-save initialized for session $sessionId in ${target.displayFolder}")
        return target
    }

    /**
     * Appends a live host in real-time as soon as it responds during the scan.
     */
    fun saveLiveHost(
        context: Context?,
        sessionId: Long,
        scanResult: ScanResult
    ) {
        if (scanResult.failed) return

        val session = activeSessions[sessionId] ?: return

        synchronized(session.lock) {
            val hostLine = "${scanResult.host}\n"
            val csvLine = buildString {
                append(escapeCsv(scanResult.host)).append(",")
                append(escapeCsv(scanResult.ip)).append(",")
                append(scanResult.code).append(",")
                append(scanResult.ms).append(",")
                append(escapeCsv(scanResult.server)).append(",")
                append(escapeCsv(scanResult.title)).append(",")
                append(escapeCsv(scanResult.faviconHash)).append(",")
                append(escapeCsv(scanResult.asn)).append(",")
                append(escapeCsv(scanResult.org)).append(",")
                append(scanResult.cloudflareFronted).append("\n")
            }

            // Write to session clean files
            for (file in session.cleanFiles) {
                try {
                    OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8).use {
                        it.write(hostLine)
                        it.flush()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing to ${file.name}", e)
                }
            }

            // Write to latest file
            for (file in session.latestFiles) {
                try {
                    OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8).use {
                        it.write(hostLine)
                        it.flush()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing to ${file.name}", e)
                }
            }

            // Write to details CSV files
            for (file in session.detailsFiles) {
                try {
                    OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8).use {
                        it.write(csvLine)
                        it.flush()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error writing to ${file.name}", e)
                }
            }
        }
    }

    /**
     * Finalizes the session and scans all output files with MediaScanner.
     */
    fun closeSession(context: Context?, sessionId: Long) {
        val session = activeSessions.remove(sessionId) ?: return
        for (f in session.cleanFiles + session.detailsFiles + session.latestFiles) {
            scanMediaFile(context, f)
        }
        Log.i(TAG, "Auto-save closed for session $sessionId")
    }

    fun getSessionTarget(sessionId: Long): SessionTarget? {
        return activeSessions[sessionId]
    }

    private fun scanMediaFile(context: Context?, file: File) {
        if (context == null || !file.exists()) return
        try {
            MediaScannerConnection.scanFile(
                context,
                arrayOf(file.absolutePath),
                null,
                null
            )
        } catch (e: Exception) {
            // Ignore failure
        }
    }

    fun sanitizeFolderName(name: String): String {
        return name.trim()
            .replace(Regex("[^a-zA-Z0-9._ -]"), "_")
            .replace(Regex("\\s+"), "_")
            .trim('_')
            .take(60)
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }
}

package com.hostchecker.pro.util

import android.app.DownloadManager
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.hostchecker.pro.domain.model.ScanResult
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.util.concurrent.ConcurrentHashMap

object AutoSaveManager {
    private const val TAG = "AutoSaveManager"
    const val MAIN_STORAGE_APP_FOLDER = "HostCheckerPro"

    data class SessionTarget(
        val sessionId: Long,
        val folderName: String,
        val displayFolder: String,
        val relativePath: String,
        val appExtDir: File?,
        val pubSessionDir: File?,
        // Global live hosts file
        val cleanFiles: MutableList<File> = mutableListOf(),
        // Global CSV file
        val detailsFiles: MutableList<File> = mutableListOf(),
        // Response-code specific files (e.g. 200 -> [200.txt files in appExtDir / pubDir])
        val codeFiles: ConcurrentHashMap<Int, MutableList<File>> = ConcurrentHashMap(),
        // MediaStore URIs for Android 10+
        var mediaStoreHostUri: Uri? = null,
        var mediaStoreCsvUri: Uri? = null,
        val mediaStoreCodeUris: ConcurrentHashMap<Int, Uri> = ConcurrentHashMap(),
        // Real-time counts per status code
        val codeCounts: ConcurrentHashMap<Int, Int> = ConcurrentHashMap(),
        val lock: Any = Any()
    )

    private val activeSessions = ConcurrentHashMap<Long, SessionTarget>()

    /**
     * Returns a human-friendly display path for the UI.
     */
    fun getDisplayPath(outName: String): String {
        val cleanOut = sanitizeFolderName(outName).ifBlank { "scan" }
        return "Download/$MAIN_STORAGE_APP_FOLDER/$cleanOut"
    }

    /**
     * Initializes auto-saving for a scan session.
     * Guarantees that the main folder Download/HostCheckerPro/<outName>/ is created,
     * and live_hosts.txt + live_details.csv are initialized.
     */
    fun initSession(
        context: Context?,
        sessionId: Long,
        outName: String
    ): SessionTarget {
        val cleanOutName = sanitizeFolderName(outName).ifBlank { "scan_$sessionId" }
        val relativePath = "Download/$MAIN_STORAGE_APP_FOLDER/$cleanOutName"

        var appExtDir: File? = null
        val cleanFiles = mutableListOf<File>()
        val detailsFiles = mutableListOf<File>()

        // 1. Guaranteed app-specific external Downloads directory (always writable, shared via FileProvider)
        if (context != null) {
            try {
                val base = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                appExtDir = File(base, "$MAIN_STORAGE_APP_FOLDER/$cleanOutName")
                if (!appExtDir.exists()) {
                    appExtDir.mkdirs()
                }
                if (appExtDir.exists()) {
                    val hostFile = File(appExtDir, "live_hosts.txt")
                    if (!hostFile.exists()) {
                        hostFile.createNewFile()
                    }
                    cleanFiles.add(hostFile)

                    val csvFile = File(appExtDir, "live_details.csv")
                    if (!csvFile.exists() || csvFile.length() == 0L) {
                        csvFile.writeText(
                            "Host,IP,StatusCode,ResponseTimeMs,Server,Title,FaviconHash,ASN,Org,CloudflareFronted\n",
                            Charsets.UTF_8
                        )
                    }
                    detailsFiles.add(csvFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing app external dir", e)
            }
        }

        // 2. Direct public Downloads folder attempt (accessible on legacy Android or if direct I/O permitted)
        var pubSessionDir: File? = null
        try {
            val pubDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (pubDownloads != null) {
                pubSessionDir = File(pubDownloads, "$MAIN_STORAGE_APP_FOLDER/$cleanOutName")
                if (!pubSessionDir.exists()) {
                    pubSessionDir.mkdirs()
                }
                if (pubSessionDir.exists() && pubSessionDir.canWrite()) {
                    val pubHostFile = File(pubSessionDir, "live_hosts.txt")
                    if (!pubHostFile.exists()) {
                        pubHostFile.createNewFile()
                    }
                    if (!cleanFiles.contains(pubHostFile)) {
                        cleanFiles.add(pubHostFile)
                    }

                    val pubCsvFile = File(pubSessionDir, "live_details.csv")
                    if (!pubCsvFile.exists() || pubCsvFile.length() == 0L) {
                        pubCsvFile.writeText(
                            "Host,IP,StatusCode,ResponseTimeMs,Server,Title,FaviconHash,ASN,Org,CloudflareFronted\n",
                            Charsets.UTF_8
                        )
                    }
                    if (!detailsFiles.contains(pubCsvFile)) {
                        detailsFiles.add(pubCsvFile)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Public Downloads direct write not available: ${e.message}")
        }

        // 3. Android 10+ (API 29+) MediaStore Downloads insertion so files appear directly in Files / Downloads
        var mediaHostUri: Uri? = null
        var mediaCsvUri: Uri? = null

        if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                mediaHostUri = getOrCreateMediaStoreUri(
                    context = context,
                    fileName = "live_hosts.txt",
                    mimeType = "text/plain",
                    relativePath = relativePath
                )
                mediaCsvUri = getOrCreateMediaStoreUri(
                    context = context,
                    fileName = "live_details.csv",
                    mimeType = "text/csv",
                    relativePath = relativePath
                )

                // Initialize CSV header if newly created
                if (mediaCsvUri != null) {
                    try {
                        context.contentResolver.openOutputStream(mediaCsvUri, "w")?.use { out ->
                            out.write("Host,IP,StatusCode,ResponseTimeMs,Server,Title,FaviconHash,ASN,Org,CloudflareFronted\n".toByteArray(Charsets.UTF_8))
                            out.flush()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed writing header to MediaStore CSV: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "MediaStore registration failed: ${e.message}")
            }
        }

        val target = SessionTarget(
            sessionId = sessionId,
            folderName = cleanOutName,
            displayFolder = relativePath,
            relativePath = relativePath,
            appExtDir = appExtDir,
            pubSessionDir = pubSessionDir,
            cleanFiles = cleanFiles,
            detailsFiles = detailsFiles,
            mediaStoreHostUri = mediaHostUri,
            mediaStoreCsvUri = mediaCsvUri
        )

        activeSessions[sessionId] = target

        // Trigger MediaScanner immediately so files are registered
        for (f in cleanFiles + detailsFiles) {
            scanMediaFile(context, f)
        }

        Log.i(TAG, "Auto-save successfully initialized for session $sessionId in ${target.displayFolder}")
        return target
    }

    /**
     * Appends a live host in real-time as soon as it responds during the scan:
     * 1. Appends to live_hosts.txt
     * 2. Appends to live_details.csv
     * 3. Appends to specific status-code file (e.g. 200.txt, 403.txt, 301.txt, etc.)!
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
            val code = scanResult.code
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

            // --- 1. Write to all-live hosts files (live_hosts.txt) ---
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

            // --- 2. Write to CSV files (live_details.csv) ---
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

            // --- 3. Write to status code specific file (e.g. 200.txt, 403.txt) ---
            if (code > 0) {
                session.codeCounts[code] = (session.codeCounts[code] ?: 0) + 1

                val codeFileName = "$code.txt"
                var codeFilesList = session.codeFiles[code]
                if (codeFilesList == null) {
                    codeFilesList = mutableListOf()
                    // Create in appExtDir
                    session.appExtDir?.let { dir ->
                        try {
                            val f = File(dir, codeFileName)
                            if (!f.exists()) f.createNewFile()
                            codeFilesList.add(f)
                            scanMediaFile(context, f)
                        } catch (e: Exception) {
                            Log.w(TAG, "Error creating code file in appExtDir: ${e.message}")
                        }
                    }
                    // Create in pubSessionDir if writable
                    session.pubSessionDir?.let { dir ->
                        try {
                            if (dir.exists() && dir.canWrite()) {
                                val f = File(dir, codeFileName)
                                if (!f.exists()) f.createNewFile()
                                if (!codeFilesList.contains(f)) {
                                    codeFilesList.add(f)
                                }
                                scanMediaFile(context, f)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error creating code file in pubDir: ${e.message}")
                        }
                    }
                    session.codeFiles[code] = codeFilesList

                    // Create MediaStore entry on Android 10+
                    if (context != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            val uri = getOrCreateMediaStoreUri(
                                context = context,
                                fileName = codeFileName,
                                mimeType = "text/plain",
                                relativePath = session.relativePath
                            )
                            if (uri != null) {
                                session.mediaStoreCodeUris[code] = uri
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error creating MediaStore code file: ${e.message}")
                        }
                    }
                }

                // Append host to the code files
                for (file in codeFilesList) {
                    try {
                        OutputStreamWriter(FileOutputStream(file, true), Charsets.UTF_8).use {
                            it.write(hostLine)
                            it.flush()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error writing to $codeFileName: ${e.message}")
                    }
                }

                // Append host to MediaStore code URI
                if (context != null) {
                    session.mediaStoreCodeUris[code]?.let { uri ->
                        try {
                            context.contentResolver.openOutputStream(uri, "wa")?.use { out ->
                                out.write(hostLine.toByteArray(Charsets.UTF_8))
                                out.flush()
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error writing to MediaStore $codeFileName: ${e.message}")
                        }
                    }
                }
            }

            // --- 4. Write to MediaStore URIs for live_hosts and live_details ---
            if (context != null) {
                session.mediaStoreHostUri?.let { uri ->
                    try {
                        context.contentResolver.openOutputStream(uri, "wa")?.use { out ->
                            out.write(hostLine.toByteArray(Charsets.UTF_8))
                            out.flush()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error writing to MediaStore live_hosts: ${e.message}")
                    }
                }

                session.mediaStoreCsvUri?.let { uri ->
                    try {
                        context.contentResolver.openOutputStream(uri, "wa")?.use { out ->
                            out.write(csvLine.toByteArray(Charsets.UTF_8))
                            out.flush()
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error writing to MediaStore live_details: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Finalizes the session and scans all output files with MediaScanner.
     */
    fun closeSession(context: Context?, sessionId: Long) {
        val session = activeSessions.remove(sessionId) ?: return
        val allFiles = mutableListOf<File>()
        allFiles.addAll(session.cleanFiles)
        allFiles.addAll(session.detailsFiles)
        session.codeFiles.values.forEach { allFiles.addAll(it) }

        for (f in allFiles) {
            scanMediaFile(context, f)
        }
        Log.i(TAG, "Auto-save finalized for session $sessionId")
    }

    fun getSessionTarget(sessionId: Long): SessionTarget? {
        return activeSessions[sessionId]
    }

    /**
     * Returns a snapshot map of response codes and their counts for a session.
     */
    fun getCodeCounts(sessionId: Long): Map<Int, Int> {
        return activeSessions[sessionId]?.codeCounts?.toMap() ?: emptyMap()
    }

    /**
     * Retrieves all saved files for a given session.
     */
    fun getAllSavedFiles(context: Context?, sessionId: Long): List<File> {
        val target = activeSessions[sessionId]
        val results = LinkedHashSet<File>()

        if (target != null) {
            target.cleanFiles.filter { it.exists() }.forEach { results.add(it) }
            target.codeFiles.values.flatten().filter { it.exists() }.forEach { results.add(it) }
            target.detailsFiles.filter { it.exists() }.forEach { results.add(it) }
        }

        // Also check filesystem
        if (context != null) {
            val appExtDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val sessionDir = File(appExtDir, "$MAIN_STORAGE_APP_FOLDER/${target?.folderName ?: "scan_$sessionId"}")
            if (sessionDir.exists()) {
                sessionDir.listFiles()?.filter { it.isFile }?.forEach { results.add(it) }
            }
        }
        return results.toList()
    }

    /**
     * Retrieves the primary live_hosts.txt file for viewing or sharing.
     */
    fun getShareableCleanFile(context: Context?, sessionId: Long): File? {
        val target = activeSessions[sessionId]
        if (target != null) {
            val file = target.cleanFiles.firstOrNull { it.exists() && it.length() > 0 }
            if (file != null) return file
            val anyFile = target.cleanFiles.firstOrNull { it.exists() }
            if (anyFile != null) return anyFile
        }

        // Fallback: search external files dir
        if (context != null) {
            val appExtDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val found = appExtDir?.walkTopDown()?.filter { it.name == "live_hosts.txt" }?.firstOrNull()
            if (found != null) return found
        }
        return null
    }

    /**
     * Retrieves a file for a specific response code (e.g. 200 -> 200.txt).
     */
    fun getFileForCode(context: Context?, sessionId: Long, code: Int): File? {
        val target = activeSessions[sessionId]
        val codeFileName = "$code.txt"
        if (target != null) {
            val file = target.codeFiles[code]?.firstOrNull { it.exists() && it.length() > 0 }
            if (file != null) return file
        }

        if (context != null) {
            val appExtDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            val sessionDir = File(appExtDir, "$MAIN_STORAGE_APP_FOLDER/${target?.folderName ?: "scan_$sessionId"}")
            val codeFile = File(sessionDir, codeFileName)
            if (codeFile.exists()) return codeFile
        }
        return null
    }

    /**
     * Creates an Intent to share any file with external apps.
     */
    fun createShareIntent(context: Context, file: File, title: String = "Live Hosts - Host Checker Pro"): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = if (file.name.endsWith(".csv")) "text/csv" else "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Creates an Intent to view a file in a text editor/viewer.
     */
    fun createViewFileIntent(context: Context, file: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mime = if (file.name.endsWith(".csv")) "text/csv" else "text/plain"
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * Opens the device file manager or downloads folder.
     */
    fun openOutputFolder(context: Context, outName: String) {
        val cleanOut = sanitizeFolderName(outName).ifBlank { "scan" }
        try {
            val intent = Intent(DownloadManager.ACTION_VIEW_DOWNLOADS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            try {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(
                        Uri.parse(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).path + "/$MAIN_STORAGE_APP_FOLDER/$cleanOut"),
                        "resource/folder"
                    )
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (e2: Exception) {
                Toast.makeText(context, "Saved to: Download/$MAIN_STORAGE_APP_FOLDER/$cleanOut", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun getOrCreateMediaStoreUri(
        context: Context,
        fileName: String,
        mimeType: String,
        relativePath: String
    ): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null

        val resolver = context.contentResolver
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI

        // Query if file already exists
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf(fileName, "%$relativePath%")

        try {
            resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    return ContentUris.withAppendedId(collection, id)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Query MediaStore error: ${e.message}")
        }

        // Insert new entry
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$relativePath/")
            put(MediaStore.MediaColumns.IS_PENDING, 0)
        }

        return try {
            resolver.insert(collection, values)
        } catch (e: Exception) {
            Log.e(TAG, "Insert MediaStore error: ${e.message}")
            null
        }
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

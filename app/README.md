# Host Checker Pro

**Host Checker Pro** is a production-ready, high-performance Android host/domain availability and HTTP metadata scanner engineered in Kotlin and Jetpack Compose. It is designed specifically for authorized domain inventory validation and infrastructure reconnaissance on host lists that you own or have permission to inspect.

---

## 1. Requirements

- **Android OS**: Android 7.0 (API Level 24) minimum; target Android 14 (API Level 34)
- **JDK**: Java 17+
- **Gradle**: Kotlin DSL, Version Catalog (`gradle/libs.versions.toml`)
- **Android Studio**: Iguana (2023.2.1) or newer

---

## 2. Architecture & Tech Stack

- **UI & Design**: Jetpack Compose, Material Design 3 (M3), Dark Industrial Teal palette (`#0E1416` background, `#16211F` surface, `#2AA198` primary, `#4DD0E1` accent cyan, `#0F6E6E` appbar teal). Monospaced typography for hostnames, IPs, hashes, and headers.
- **Architecture**: Clean Architecture + MVVM (Model-View-ViewModel) with Hilt Dependency Injection.
- **Concurrency & Engine**: Structured concurrency using `SupervisorJob + CoroutineScope` with 1–64 configurable parallel workers, non-blocking channels, atomic counters, and graceful pause/resume/stop lifecycle management.
- **Networking**: OkHttp 4.12 with configurable connection/read timeouts, connection pooling, bounded streaming reads (64 KB max HTML title buffer), SSLSocket TLS inspection, and safe resource disposal.
- **Local Persistence**: Room 2.6 database with WAL mode (`AppDatabase`), indexing on `sessionId`, and throttled/conflated `StateFlow` queries capped at 1,000 recent items in memory to handle up to 45,000+ hosts effortlessly.
- **Background Execution**: Android Foreground Service (`ScanForegroundService`) bound to sticky notifications (`NotificationCompat`) with live Pause/Resume/Stop actions and network resilience warnings.
- **Settings & Config**: Jetpack DataStore Preferences for user defaults (threads, timeouts, stealth/jitter, auto-save).

---

## 3. Database Structure

The Room database (`AppDatabase`) comprises three primary entities:

### `SessionEntity`
- `id` (Long, Primary Key auto-generate)
- `name` (String, Session display name)
- `fileName` (String, Imported file or text label)
- `total` (Int, Total hosts in session)
- `scanned` (Int, Count of processed hosts)
- `responded` (Int, Count of successful/alive responses)
- `failed` (Int, Count of unreachable/error hosts)
- `threads` (Int, Concurrency worker count)
- `startedAt` (Long, Millisecond timestamp)
- `finishedAt` (Long?, Completion timestamp)
- `status` (String, `IDLE`, `RUNNING`, `PAUSED`, `STOPPED`, `COMPLETED`, `FAILED`)
- `lastIndex` (Int, Cursor for pause/resume recovery)
- `paused` (Boolean)
- `createdAt` / `updatedAt` (Long)

### `ResultEntity`
- `id` (Long, Primary Key auto-generate)
- `sessionId` (Long, Indexed foreign key)
- `host` (String, Normalized hostname)
- `ip` (String, Resolved IP address)
- `asn` (String, Autonomous System Number)
- `org` (String, Autonomous System Organization)
- `server` (String, HTTP Server banner)
- `code` (Int, HTTP status code: 2xx, 3xx, 4xx, 5xx)
- `ms` (Long, Dispatch-to-header latency in milliseconds)
- `title` (String, Extracted HTML title)
- `faviconHash` (String, Shodan-compatible signed 32-bit MMH3 hash)
- `san` (String, Subject Alternative Names from TLS certificate)
- `headers` (String, Normalized HTTP response headers)
- `failed` (Boolean, Failure indicator)
- `error` (String?, Error message if failed)
- `scheme` (String, `https` or `http`)
- `createdAt` (Long, Millisecond timestamp)

### `AsnCacheEntity`
- `ip` (String, Primary Key)
- `asn` (String)
- `org` (String)
- `cachedAt` (Long, Timestamp with 30-day TTL)

---

## 4. Background Service Behavior

- Scans are hosted within `ScanForegroundService`.
- **Foreground Notification (`scan_status`)**:
  - Displays session title, percentage progress, scanned/total counts, and current processing speed (hosts/sec).
  - Integrated notification action buttons: **Pause**, **Resume**, and **Stop**.
  - Consecutive network failure detector: alerts `"Network may be unavailable"` after 10 consecutive network timeouts.
- **Activity Recreation & Backgrounding**:
  - The scan continues seamlessly if the user locks the device, switches apps, or if the Activity is recreated due to configuration changes.
  - When the app is reopened, `ScanViewModel` reconnects immediately to the active foreground session and reflects the real-time state (`RUNNING`, `PAUSED`, or `COMPLETED`).

---

## 5. File Import & Streaming

- Supports `.txt`, `.list`, and `.csv` files streamed via `ActivityResultContracts.OpenDocument` and `contentResolver.openInputStream(uri)`.
- Does not load entire multi-megabyte files into memory at once.
- Trims whitespace, ignores empty lines, skips comments (`#`), strips URL schemes and trailing paths, validates hostname/IP syntax, and removes duplicate entries while recording precise statistics:
  - Loaded filename
  - Valid host count
  - Duplicate count removed
  - Invalid entries skipped
- Includes a dedicated Paste-Text dialog for quick ad-hoc testing.

---

## 6. Real-Time Auto-Save & Export

- **Real-Time Auto-Save**: As live hosts respond (status code > 0 and not failed), they are immediately appended to `HostCheckerPro/<outname>/alive_hosts.txt` in the app's external files directory.
- **Exports**:
  - **CSV**: Complete export including host, IP, ASN, Org, Server, Status Code, Latency (ms), Title, Favicon MMH3, Scheme, and Timestamps.
  - **TXT**: Plain list of hostnames matching the current filter (All, Live Only, or Selected).
  - **JSON**: Structured session summary and array of result records.
- Stored under `externalFilesDir/exports/` and shared securely via Android's `FileProvider` (`content://`) with `Intent.ACTION_SEND`.

---

## 7. MurmurHash3 (MMH3) 32-Bit Favicon Hashing

- Custom pure-Kotlin MurmurHash3 x86 32-bit implementation producing signed 32-bit outputs identical to Python's `mmh3.hash()`.
- Verified vector: `mmh3("test") == -1508913719`.
- Shodan favicon hashing support: Base64-encoded favicon stream with 76-character line breaks hashed with MMH3 seed 0.

---

## 8. Build & Run Instructions

### Debug Build
```bash
./gradlew assembleDebug
```

### Release Build
```bash
./gradlew assembleRelease
```
Expected output:
```
app/build/outputs/apk/release/app-release.apk
```

If ABI splitting is enabled:
```bash
./gradlew assembleRelease -Pandroid.splitApk=true
```

### Run Unit Tests
```bash
./gradlew testDebugUnitTest
```

---

## 9. Permissions

- `android.permission.INTERNET`: For dispatching HTTP/HTTPS probes and DNS resolution.
- `android.permission.ACCESS_NETWORK_STATE`: For observing connection state.
- `android.permission.FOREGROUND_SERVICE`: For long-running scan execution.
- `android.permission.FOREGROUND_SERVICE_DATA_SYNC`: Android 14 requirement for background scanning jobs.
- `android.permission.POST_NOTIFICATIONS`: Android 13+ (API 33+) runtime notification permission for live foreground notifications.

---

## 10. Known Android Limitations

1. **Cleartext Traffic**: Probing plain HTTP hosts (`http://`) requires `android:usesCleartextTraffic="true"` in the `AndroidManifest.xml`, which is enabled for security scanning utility compatibility.
2. **Background Battery Optimizations**: OEM-specific battery managers (e.g. Samsung OneUI, Xiaomi MIUI) may throttle background CPU if battery optimization is strictly enforced; keeping the foreground notification active prevents premature termination.
3. **Android 14+ Foreground Service Types**: Requires explicit `android:foregroundServiceType="dataSync"` declaration, which is configured in the manifest.

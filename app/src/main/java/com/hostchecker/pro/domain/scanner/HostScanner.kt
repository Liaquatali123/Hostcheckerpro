package com.hostchecker.pro.domain.scanner

import com.hostchecker.pro.data.repo.ResultRepository
import com.hostchecker.pro.data.repo.SessionRepository
import com.hostchecker.pro.domain.model.ScanConfig
import com.hostchecker.pro.domain.model.ScanResult
import java.net.InetAddress
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.random.Random
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class HostScanner(
    private val baseOkHttpClient: OkHttpClient,
    private val resultRepository: ResultRepository,
    private val sessionRepository: SessionRepository,
    private val asnLookup: AsnLookup,
    private val cloudflareProbe: CloudflareProbe
) {
    private val scannerJob = SupervisorJob()
    private val scannerScope = CoroutineScope(Dispatchers.IO + scannerJob)

    private var activeJob: Job? = null
    private val isPaused = AtomicBoolean(false)
    private val isScanning = AtomicBoolean(false)

    private val _resultStream = MutableSharedFlow<ScanResult>(replay = 100, extraBufferCapacity = 1000)
    val resultStream: SharedFlow<ScanResult> = _resultStream.asSharedFlow()

    private val _scanEvents = MutableSharedFlow<ScanEvent>(replay = 1, extraBufferCapacity = 100)
    val scanEvents: SharedFlow<ScanEvent> = _scanEvents.asSharedFlow()

    sealed interface ScanEvent {
        data class Progress(
            val scanned: Int,
            val responded: Int,
            val total: Int,
            val currentHost: String,
            val hostsPerSecond: Double
        ) : ScanEvent

        data class Complete(val scanned: Int, val responded: Int, val total: Int) : ScanEvent
        data class NetworkWarning(val consecutiveFails: Int) : ScanEvent
        data object Paused : ScanEvent
        data object Resumed : ScanEvent
        data object Stopped : ScanEvent
    }

    private val userAgents = listOf(
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:130.0) Gecko/20100101 Firefox/130.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14.6; rv:130.0) Gecko/20100101 Firefox/130.0",
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_6_1) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Safari/605.1.15",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36 Edg/128.0.0.0",
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36",
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.6613.88 Mobile Safari/537.36",
        "Mozilla/5.0 (iPhone; CPU iPhone OS 17_6_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/127.0.0.0 Safari/537.36 OPR/113.0.0.0",
        "Mozilla/5.0 (X11; Ubuntu; Linux x86_64; rv:129.0) Gecko/20100101 Firefox/129.0",
        "Mozilla/5.0 (iPad; CPU OS 17_6_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.5 Mobile/15E148 Safari/604.1"
    )

    private val titleRegex = Regex("<title[^>]*>([^<]+)</title>", RegexOption.IGNORE_CASE)
    private val faviconRegex = Regex("""<link[^>]+rel=["']?(?:shortcut\s+)?icon["']?[^>]*href=["']?([^"'>\s]+)""", RegexOption.IGNORE_CASE)

    fun isScanningNow(): Boolean = isScanning.get()
    fun isPausedNow(): Boolean = isPaused.get()

    /**
     * Starts or resumes a scan session across a list of hosts.
     */
    fun startScan(
        sessionId: Long,
        hosts: List<String>,
        config: ScanConfig,
        startIndex: Int = 0
    ) {
        stopScan()
        isScanning.set(true)
        isPaused.set(false)

        activeJob = scannerScope.launch {
            val total = hosts.size
            val currentIndex = AtomicInteger(startIndex)
            val scannedCount = AtomicInteger(startIndex)
            val respondedCount = AtomicInteger(0)
            val consecutiveFails = AtomicInteger(0)
            val startTime = System.currentTimeMillis()

            // Pre-count responded from previous runs if resuming
            val existingResponded = resultRepository.getLiveResultsOnce(sessionId).size
            respondedCount.set(existingResponded)

            val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            })
            val sslContext = SSLContext.getInstance("TLS").apply {
                init(null, trustAllCerts, SecureRandom())
            }

            val client = baseOkHttpClient.newBuilder()
                .connectTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .readTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .writeTimeout(config.timeoutSeconds.toLong(), TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
                .hostnameVerifier { _, _ -> true }
                .build()

            val workers = (0 until config.threads.coerceIn(1, 64)).map {
                launch {
                    while (isActive) {
                        while (isPaused.get() && isActive) {
                            delay(200)
                        }

                        val idx = currentIndex.getAndIncrement()
                        if (idx >= total) break

                        val rawHost = hosts[idx].trim()
                        if (rawHost.isBlank() || rawHost.startsWith("#")) {
                            scannedCount.incrementAndGet()
                            continue
                        }

                        if (config.jitterEnabled) {
                            delay(Random.nextLong(0, 500))
                        }

                        val scanResult = scanSingleHost(
                            sessionId = sessionId,
                            rawHost = rawHost,
                            client = client,
                            config = config
                        )

                        // Immediately insert to Room
                        resultRepository.insertResult(scanResult)
                        _resultStream.emit(scanResult)

                        val currentScanned = scannedCount.incrementAndGet()
                        if (!scanResult.failed) {
                            respondedCount.incrementAndGet()
                            consecutiveFails.set(0)
                        } else {
                            val fails = consecutiveFails.incrementAndGet()
                            if (fails == 10) {
                                _scanEvents.emit(ScanEvent.NetworkWarning(fails))
                            }
                        }

                        // Periodic progress save every 25 hosts
                        if (currentScanned % 25 == 0 || currentScanned >= total) {
                            sessionRepository.updateProgress(
                                id = sessionId,
                                scanned = currentScanned,
                                responded = respondedCount.get(),
                                lastIndex = idx,
                                status = if (currentScanned >= total) "COMPLETED" else "RUNNING"
                            )
                        }

                        // Emit progress event
                        val elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000.0
                        val rate = if (elapsedSeconds > 0) (currentScanned - startIndex) / elapsedSeconds else 0.0
                        _scanEvents.emit(
                            ScanEvent.Progress(
                                scanned = currentScanned,
                                responded = respondedCount.get(),
                                total = total,
                                currentHost = scanResult.host,
                                hostsPerSecond = rate
                            )
                        )
                    }
                }
            }

            try {
                workers.joinAll()

                // Final save
                val finalScanned = scannedCount.get().coerceAtMost(total)
                val finalResponded = respondedCount.get()
                sessionRepository.updateProgress(
                    id = sessionId,
                    scanned = finalScanned,
                    responded = finalResponded,
                    lastIndex = total,
                    status = "COMPLETED"
                )
                sessionRepository.finishSession(sessionId, "COMPLETED")
                _scanEvents.emit(ScanEvent.Complete(finalScanned, finalResponded, total))
            } catch (e: CancellationException) {
                val lastIdx = currentIndex.get().coerceAtMost(total)
                sessionRepository.updateProgress(
                    id = sessionId,
                    scanned = scannedCount.get(),
                    responded = respondedCount.get(),
                    lastIndex = lastIdx,
                    status = if (isPaused.get()) "PAUSED" else "STOPPED"
                )
                if (isPaused.get()) {
                    _scanEvents.emit(ScanEvent.Paused)
                } else {
                    _scanEvents.emit(ScanEvent.Stopped)
                }
            } finally {
                isScanning.set(false)
            }
        }
    }

    private suspend fun scanSingleHost(
        sessionId: Long,
        rawHost: String,
        client: OkHttpClient,
        config: ScanConfig
    ): ScanResult {
        val cleanHost = rawHost.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .split("/").first()
            .split(":").first()

        var resolvedIp = ""
        try {
            resolvedIp = InetAddress.getByName(cleanHost).hostAddress ?: ""
        } catch (e: Exception) {
            // DNS resolution failed
        }

        // Try HTTPS first, then fallback HTTP
        var result: ScanResult? = null
        val schemes = listOf("https", "http")

        for (scheme in schemes) {
            val url = "$scheme://$cleanHost"
            val scanAttempt = executeHttpProbe(
                sessionId = sessionId,
                host = cleanHost,
                url = url,
                ip = resolvedIp,
                client = client,
                config = config
            )

            if (!scanAttempt.failed) {
                result = scanAttempt
                break
            } else if (result == null) {
                result = scanAttempt // Keep first failure error
            }
        }

        val finalResult = result ?: ScanResult(
            sessionId = sessionId,
            host = cleanHost,
            ip = resolvedIp,
            failed = true,
            errorMessage = "Connection refused or unreachable"
        )

        // If alive, enrich with ASN, SAN, and CF origin leak probe
        if (!finalResult.failed) {
            var asn = ""
            var org = ""
            if (finalResult.ip.isNotBlank()) {
                val asnInfo = asnLookup.lookupAsn(finalResult.ip)
                asn = asnInfo.asn
                org = asnInfo.org
            }

            val sans = CertParser.extractSans(cleanHost)

            var isCf = finalResult.server.contains("cloudflare", ignoreCase = true)
            var cfOrigin = ""
            if (isCf) {
                val cfProbe = cloudflareProbe.probeOrigin(cleanHost)
                isCf = cfProbe.isFronted
                cfOrigin = cfProbe.originInfo
            }

            return finalResult.copy(
                asn = asn,
                org = org,
                san = sans,
                cloudflareFronted = isCf,
                cloudflareOrigin = cfOrigin
            )
        }

        return finalResult
    }

    private suspend fun executeHttpProbe(
        sessionId: Long,
        host: String,
        url: String,
        ip: String,
        client: OkHttpClient,
        config: ScanConfig
    ): ScanResult = withContext(Dispatchers.IO) {
        val userAgent = if (config.stealthMode) {
            userAgents.random()
        } else {
            "HostCheckerPro/1.0 (+https://github.com/hostchecker/pro)"
        }

        val maxAttempts = if (config.retryFailed) 3 else 1
        var lastError = ""

        for (attempt in 1..maxAttempts) {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val startTime = System.currentTimeMillis()
            var response: Response? = null
            try {
                response = client.newCall(request).execute()
                val responseTimeMs = System.currentTimeMillis() - startTime
                val code = response.code
                val server = response.header("Server") ?: ""

                val headersMap = mutableMapOf<String, String>()
                for (name in response.headers.names()) {
                    headersMap[name] = response.headers[name] ?: ""
                }

                // Peek up to 64KB body for <title> and favicon
                var title = ""
                var faviconHash = ""
                try {
                    val peekedBody = response.peekBody(65536).string()
                    val titleMatch = titleRegex.find(peekedBody)
                    if (titleMatch != null) {
                        title = titleMatch.groupValues[1].trim()
                    }

                    // Favicon detection
                    val iconMatch = faviconRegex.find(peekedBody)
                    val faviconUrl = if (iconMatch != null) {
                        val rawHref = iconMatch.groupValues[1].trim()
                        val parsed = url.toHttpUrlOrNull()?.resolve(rawHref)
                        parsed?.toString() ?: "$url/favicon.ico"
                    } else {
                        "$url/favicon.ico"
                    }

                    faviconHash = fetchFaviconHash(client, faviconUrl, userAgent)
                } catch (e: Exception) {
                    // Body peek error ignored
                } finally {
                    // Prompt mandate: Cancel body via response.close() after reading headers — never download full page
                    response.close()
                }

                return@withContext ScanResult(
                    sessionId = sessionId,
                    host = host,
                    ip = ip,
                    server = server,
                    code = code,
                    ms = responseTimeMs,
                    title = title,
                    faviconHash = faviconHash,
                    headers = headersMap,
                    failed = false
                )
            } catch (e: Exception) {
                lastError = e.message ?: e.javaClass.simpleName
                response?.close()
                if (attempt < maxAttempts) {
                    delay(100L * (1 shl (attempt - 1))) // exponential backoff
                }
            }
        }

        ScanResult(
            sessionId = sessionId,
            host = host,
            ip = ip,
            code = 0,
            failed = true,
            errorMessage = lastError
        )
    }

    private fun fetchFaviconHash(client: OkHttpClient, iconUrl: String, userAgent: String): String {
        return try {
            val req = Request.Builder()
                .url(iconUrl)
                .header("User-Agent", userAgent)
                .build()
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    val stream = res.body?.byteStream()
                    if (stream != null) {
                        val bytes = readBytesUpTo(stream, 65536)
                        if (bytes.isNotEmpty()) {
                            val hash = FaviconHasher.hash(bytes, seed = 0)
                            return hash.toString()
                        }
                    }
                }
                ""
            }
        } catch (e: Exception) {
            ""
        }
    }

    private fun readBytesUpTo(stream: java.io.InputStream, maxBytes: Int): ByteArray {
        val buffer = ByteArray(minOf(maxBytes, 8192))
        val baos = java.io.ByteArrayOutputStream()
        var total = 0
        while (total < maxBytes) {
            val toRead = minOf(buffer.size, maxBytes - total)
            val read = stream.read(buffer, 0, toRead)
            if (read == -1) break
            baos.write(buffer, 0, read)
            total += read
        }
        return baos.toByteArray()
    }

    fun pauseScan() {
        if (isScanning.get()) {
            isPaused.set(true)
        }
    }

    fun resumeScan() {
        if (isScanning.get()) {
            isPaused.set(false)
        }
    }

    fun stopScan() {
        isPaused.set(false)
        isScanning.set(false)
        activeJob?.cancel()
        activeJob = null
    }
}

package com.hostchecker.pro.domain.scanner

import android.content.Context
import com.hostchecker.pro.data.repo.ResultRepository
import com.hostchecker.pro.data.repo.SessionRepository
import com.hostchecker.pro.domain.model.ScanConfig
import com.hostchecker.pro.domain.model.ScanResult
import com.hostchecker.pro.util.AutoSaveManager
import com.hostchecker.pro.util.NetworkUtil
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
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
import okhttp3.Call
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response

class HostScanner(
    private val baseOkHttpClient: OkHttpClient,
    private val resultRepository: ResultRepository,
    private val sessionRepository: SessionRepository,
    private val asnLookup: AsnLookup,
    private val cloudflareProbe: CloudflareProbe,
    val context: Context? = null
) {
    private val scannerJob = SupervisorJob()
    private val scannerScope = CoroutineScope(Dispatchers.IO + scannerJob)

    private var activeJob: Job? = null
    private val isPaused = AtomicBoolean(false)
    private val isScanning = AtomicBoolean(false)

    private val dnsCache = ConcurrentHashMap<String, List<InetAddress>>()

    private val customDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val cached = dnsCache[hostname]
            if (!cached.isNullOrEmpty()) {
                return cached
            }
            return Dns.SYSTEM.lookup(hostname)
        }
    }

    private class ConnectedIpTag {
        var ip: String = ""
    }

    private val ipCapturingEventListener = object : EventListener() {
        override fun connectEnd(
            call: Call,
            inetSocketAddress: InetSocketAddress,
            proxy: Proxy,
            protocol: Protocol?
        ) {
            val tag = call.request().tag(ConnectedIpTag::class.java)
            if (tag != null) {
                tag.ip = inetSocketAddress.address?.hostAddress ?: ""
            }
        }
    }

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
    private val faviconRegex = Regex(
        """<link\s+[^>]*rel=["'](?:shortcut\s+)?icon["'][^>]*href=["']([^"'>\s]+)["']|<link\s+[^>]*href=["']([^"'>\s]+)["'][^>]*rel=["'](?:shortcut\s+)?icon["']""",
        RegexOption.IGNORE_CASE
    )

    fun isScanningNow(): Boolean = isScanning.get()
    fun isPausedNow(): Boolean = isPaused.get()

    /**
     * Starts or resumes a scan session across a list of hosts.
     * Guaranteed duplicate protection and atomic work claiming.
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
            // Deduplicate input hosts while preserving order
            val normalizedHostList = mutableListOf<String>()
            val seenInInput = HashSet<String>()
            for (raw in hosts) {
                val clean = raw.trim()
                if (clean.isBlank() || clean.startsWith("#")) continue
                val normalized = NetworkUtil.cleanHostInput(clean)
                if (normalized.isNotBlank() && seenInInput.add(normalized)) {
                    normalizedHostList.add(normalized)
                }
            }

            val total = normalizedHostList.size
            val currentIndex = AtomicInteger(startIndex)
            val scannedCount = AtomicInteger(startIndex)
            val respondedCount = AtomicInteger(0)
            val consecutiveFails = AtomicInteger(0)
            val startTime = System.currentTimeMillis()

            // Atomic work claiming tracker so workers never process the same host simultaneously
            val claimedHosts = ConcurrentHashMap.newKeySet<String>()

            // Initialize auto-save in main storage HostCheckerPro/<outname>/
            val outFolderName = if (config.outName.isNotBlank()) config.outName else "scan_$sessionId"
            AutoSaveManager.initSession(context, sessionId, outFolderName)

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
                .dns(customDns)
                .eventListener(ipCapturingEventListener)
                .connectTimeout(config.timeoutSeconds.toLong().coerceIn(2, 60), TimeUnit.SECONDS)
                .readTimeout(config.timeoutSeconds.toLong().coerceIn(2, 60), TimeUnit.SECONDS)
                .callTimeout((config.timeoutSeconds * 2).toLong().coerceIn(4, 120), TimeUnit.SECONDS)
                .followRedirects(config.followRedirects)
                .followSslRedirects(config.followRedirects)
                .retryOnConnectionFailure(false) // Handle retries explicitly at scanner level
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

                        val cleanHost = normalizedHostList[idx]
                        if (!claimedHosts.add(cleanHost)) {
                            // Already claimed by another worker
                            continue
                        }

                        if (config.jitterEnabled) {
                            delay(Random.nextLong(0, 500))
                        }

                        val scanResult = scanSingleHost(
                            sessionId = sessionId,
                            cleanHost = cleanHost,
                            client = client,
                            config = config
                        )

                        // Immediately insert to Room with deduplication and overwrite safety
                        resultRepository.insertResult(scanResult)
                        _resultStream.emit(scanResult)

                        val currentScanned = scannedCount.incrementAndGet()
                        if (!scanResult.failed && scanResult.code in 100..599) {
                            respondedCount.incrementAndGet()
                            consecutiveFails.set(0)
                            // Auto-save live host in real-time to HostCheckerPro/<outname>/
                            AutoSaveManager.saveLiveHost(context, sessionId, scanResult)
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
                AutoSaveManager.closeSession(context, sessionId)
                isScanning.set(false)
            }
        }
    }

    /**
     * Scans a single host strictly adhering to:
     * 1. DNS resolution.
     * 2. HTTPS First: if HTTPS produces any HTTP response (100..599), STOP. Never probe HTTP.
     * 3. HTTP Fallback: ONLY if HTTPS fails at the connection/TLS/network level.
     * 4. OkHttp response.code is the single source of truth.
     * 5. Preserves original response, final response, and full redirect chain.
     */
    suspend fun scanSingleHost(
        sessionId: Long,
        cleanHost: String,
        client: OkHttpClient,
        config: ScanConfig,
        attemptNumber: Int = 1
    ): ScanResult {
        if (cleanHost.isBlank()) {
            return ScanResult(
                sessionId = sessionId,
                host = cleanHost,
                scheme = "HTTPS",
                failed = true,
                errorMessage = "Invalid host format"
            )
        }

        // 1. DNS RESOLUTION
        val resolvedAddresses = try {
            InetAddress.getAllByName(cleanHost)
        } catch (e: UnknownHostException) {
            return ScanResult(
                sessionId = sessionId,
                host = cleanHost,
                scheme = "HTTPS",
                requestedUrl = "https://$cleanHost",
                ip = "",
                failed = true,
                errorMessage = "DNS resolution failed (host does not exist)"
            )
        } catch (e: Exception) {
            return ScanResult(
                sessionId = sessionId,
                host = cleanHost,
                scheme = "HTTPS",
                requestedUrl = "https://$cleanHost",
                ip = "",
                failed = true,
                errorMessage = "DNS error: ${e.message ?: e.javaClass.simpleName}"
            )
        }

        if (resolvedAddresses.isEmpty()) {
            return ScanResult(
                sessionId = sessionId,
                host = cleanHost,
                scheme = "HTTPS",
                requestedUrl = "https://$cleanHost",
                ip = "",
                failed = true,
                errorMessage = "DNS returned no records"
            )
        }

        val defaultIp = resolvedAddresses.firstOrNull()?.hostAddress ?: ""
        dnsCache[cleanHost] = resolvedAddresses.toList()

        val userAgent = if (config.stealthMode) {
            userAgents.random()
        } else {
            "HostCheckerPro/1.0 (+https://github.com/hostchecker/pro)"
        }

        // 2. HTTPS FIRST
        val httpsResult = executeSingleProbe(
            cleanHost = cleanHost,
            scheme = "HTTPS",
            client = client,
            userAgent = userAgent,
            config = config,
            fallbackIp = defaultIp
        )

        val finalProbe: ProbeSuccess? = when (httpsResult) {
            is ProbeOutcome.Success -> {
                // HTTPS returned a valid HTTP response (100..599).
                // MANDATORY: STOP HERE! Never probe HTTP or overwrite HTTPS result.
                httpsResult.data
            }
            is ProbeOutcome.NetworkFailure -> {
                // HTTPS failed at connection/TLS/network level.
                // Fallback to HTTP probe.
                val httpResult = executeSingleProbe(
                    cleanHost = cleanHost,
                    scheme = "HTTP",
                    client = client,
                    userAgent = userAgent,
                    config = config,
                    fallbackIp = defaultIp
                )
                when (httpResult) {
                    is ProbeOutcome.Success -> httpResult.data
                    is ProbeOutcome.NetworkFailure -> null
                }
            }
        }

        // Check if we got an HTTP response
        if (finalProbe == null || finalProbe.originalCode !in 100..599) {
            // Both HTTPS and HTTP failed at connection/network level
            // Check retry policy
            if (config.retryFailed && attemptNumber == 1) {
                delay(300)
                return scanSingleHost(
                    sessionId = sessionId,
                    cleanHost = cleanHost,
                    client = client,
                    config = config,
                    attemptNumber = 2
                )
            }

            val errorMsg = when (httpsResult) {
                is ProbeOutcome.NetworkFailure -> httpsResult.errorDescription
                else -> "Connection failed on both HTTPS and HTTP"
            }

            return ScanResult(
                sessionId = sessionId,
                host = cleanHost,
                scheme = "HTTPS",
                requestedUrl = "https://$cleanHost",
                ip = defaultIp,
                code = 0,
                originalCode = 0,
                finalCode = 0,
                failed = true,
                errorMessage = errorMsg
            )
        }

        // Favicon fetch (metadata only - never alters HTTP response code)
        val faviconHash = fetchFaviconHash(
            client = client,
            baseUrl = finalProbe.requestedUrl,
            cleanHost = cleanHost,
            iconHref = finalProbe.iconHref,
            userAgent = userAgent
        )

        // Enrich with ASN, SAN, and CF detection (metadata only - NEVER modifies HTTP status code)
        val ipToLookup = finalProbe.connectedIp.ifBlank { defaultIp }
        var asn = ""
        var org = ""
        if (ipToLookup.isNotBlank()) {
            try {
                val asnInfo = asnLookup.lookupAsn(ipToLookup)
                asn = if (asnInfo.asn.isBlank() || asnInfo.asn.equals("UNKNOWN", ignoreCase = true) || asnInfo.asn == "no-asn") "" else asnInfo.asn
                org = if (asnInfo.org.isBlank() || asnInfo.org.equals("Unknown", ignoreCase = true)) "" else asnInfo.org
            } catch (e: Exception) {
                // ASN failure does not fail the host
            }
        }

        val sans = if (finalProbe.scheme == "HTTPS") {
            try {
                CertParser.extractSans(cleanHost)
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }

        var isCf = finalProbe.server.contains("cloudflare", ignoreCase = true)
        var cfOrigin = ""
        if (isCf) {
            try {
                val cfProbe = cloudflareProbe.probeOrigin(cleanHost)
                isCf = cfProbe.isFronted
                cfOrigin = cfProbe.originInfo
            } catch (e: Exception) {
                // CF probe failure does not affect HTTP response
            }
        }

        return ScanResult(
            sessionId = sessionId,
            host = cleanHost,
            scheme = finalProbe.scheme,
            requestedUrl = finalProbe.requestedUrl,
            finalUrl = finalProbe.finalUrl,
            httpMethod = finalProbe.httpMethod,
            originalCode = finalProbe.originalCode,
            finalCode = finalProbe.finalCode,
            redirectCount = finalProbe.redirectCount,
            redirectChain = finalProbe.redirectChain,
            ip = ipToLookup,
            asn = asn,
            org = org,
            server = finalProbe.server,
            code = finalProbe.originalCode, // Primary code is the original response code (e.g. 302)
            ms = finalProbe.ms,
            title = finalProbe.title,
            faviconHash = faviconHash,
            san = sans,
            headers = finalProbe.headers,
            cloudflareFronted = isCf,
            cloudflareOrigin = cfOrigin,
            failed = false,
            errorMessage = ""
        )
    }

    private data class RedirectStep(
        val url: String,
        val code: Int
    )

    data class ProbeSuccess(
        val scheme: String,
        val requestedUrl: String,
        val finalUrl: String,
        val httpMethod: String,
        val originalCode: Int,
        val finalCode: Int,
        val redirectCount: Int,
        val redirectChain: List<String>,
        val connectedIp: String,
        val server: String,
        val ms: Long,
        val title: String,
        val iconHref: String?,
        val headers: Map<String, String>
    )

    sealed interface ProbeOutcome {
        data class Success(val data: ProbeSuccess) : ProbeOutcome
        data class NetworkFailure(val errorDescription: String, val cause: Throwable?) : ProbeOutcome
    }

    private fun executeSingleProbe(
        cleanHost: String,
        scheme: String,
        client: OkHttpClient,
        userAgent: String,
        config: ScanConfig,
        fallbackIp: String
    ): ProbeOutcome {
        val targetUrl = "${scheme.lowercase()}://$cleanHost"
        val primaryMethod = config.httpMethod.ifBlank { "GET" }

        val outcome = executeHttpCall(
            url = targetUrl,
            method = primaryMethod,
            scheme = scheme,
            client = client,
            userAgent = userAgent,
            config = config,
            fallbackIp = fallbackIp
        )

        // If GET resulted in a connection error, try HEAD fallback for this scheme
        if (outcome is ProbeOutcome.NetworkFailure && primaryMethod == "GET") {
            val headOutcome = executeHttpCall(
                url = targetUrl,
                method = "HEAD",
                scheme = scheme,
                client = client,
                userAgent = userAgent,
                config = config,
                fallbackIp = fallbackIp
            )
            if (headOutcome is ProbeOutcome.Success) {
                return headOutcome
            }
        }

        return outcome
    }

    private fun executeHttpCall(
        url: String,
        method: String,
        scheme: String,
        client: OkHttpClient,
        userAgent: String,
        config: ScanConfig,
        fallbackIp: String
    ): ProbeOutcome {
        val ipTag = ConnectedIpTag()
        val request = Request.Builder()
            .url(url)
            .method(method, null)
            .tag(ConnectedIpTag::class.java, ipTag)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        val callClient = client.newBuilder()
            .followRedirects(config.followRedirects)
            .followSslRedirects(config.followRedirects)
            .build()

        val startTime = System.currentTimeMillis()
        var response: Response? = null
        return try {
            response = callClient.newCall(request).execute()
            val responseTimeMs = System.currentTimeMillis() - startTime

            // OkHttp's response.code is the single source of truth!
            val finalCode = response.code
            val finalUrl = response.request.url.toString()
            val server = response.header("Server") ?: ""

            // Read headers before closing response
            val headersMap = mutableMapOf<String, String>()
            for (name in response.headers.names()) {
                headersMap[name] = response.headers[name] ?: ""
            }

            // Extract redirect chain from response.priorResponse
            val chain = mutableListOf<RedirectStep>()
            var curr: Response? = response
            while (curr != null) {
                chain.add(0, RedirectStep(url = curr.request.url.toString(), code = curr.code))
                curr = curr.priorResponse
            }

            val originalCode = chain.first().code
            val requestedUrl = chain.first().url
            val redirectCount = (chain.size - 1).coerceAtLeast(0)
            val redirectChainList = if (chain.size > 1) {
                chain.map { "${it.url} [${it.code}]" }
            } else {
                emptyList()
            }

            val connectedIp = ipTag.ip.ifBlank { fallbackIp }

            var title = ""
            var iconHref: String? = null
            if (method == "GET") {
                try {
                    val peekedBody = response.peekBody(65536).string()
                    val titleMatch = titleRegex.find(peekedBody)
                    if (titleMatch != null) {
                        title = titleMatch.groupValues[1].trim()
                    }
                    val iconMatch = faviconRegex.find(peekedBody)
                    if (iconMatch != null) {
                        iconHref = (iconMatch.groups[1] ?: iconMatch.groups[2])?.value?.trim()
                    }
                } catch (e: Exception) {
                    // Body peek error ignored
                }
            }

            ProbeOutcome.Success(
                ProbeSuccess(
                    scheme = scheme,
                    requestedUrl = requestedUrl,
                    finalUrl = finalUrl,
                    httpMethod = method,
                    originalCode = originalCode,
                    finalCode = finalCode,
                    redirectCount = redirectCount,
                    redirectChain = redirectChainList,
                    connectedIp = connectedIp,
                    server = server,
                    ms = responseTimeMs,
                    title = title,
                    iconHref = iconHref,
                    headers = headersMap
                )
            )
        } catch (e: SSLException) {
            ProbeOutcome.NetworkFailure("TLS/SSL handshake failed: ${e.message}", e)
        } catch (e: SocketTimeoutException) {
            ProbeOutcome.NetworkFailure("Connection timed out", e)
        } catch (e: UnknownHostException) {
            ProbeOutcome.NetworkFailure("Unknown host / DNS failure", e)
        } catch (e: Exception) {
            ProbeOutcome.NetworkFailure("Network error: ${e.message ?: e.javaClass.simpleName}", e)
        } finally {
            response?.close()
        }
    }

    private fun fetchFaviconHash(
        client: OkHttpClient,
        baseUrl: String,
        cleanHost: String,
        iconHref: String?,
        userAgent: String
    ): String {
        val faviconUrl = if (!iconHref.isNullOrBlank()) {
            baseUrl.toHttpUrlOrNull()?.resolve(iconHref)?.toString()
                ?: "${baseUrl.trimEnd('/')}/favicon.ico"
        } else {
            val protocol = if (baseUrl.startsWith("https", ignoreCase = true)) "https" else "http"
            "$protocol://$cleanHost/favicon.ico"
        }

        try {
            val req = Request.Builder()
                .url(faviconUrl)
                .header("User-Agent", userAgent)
                .header("Accept", "image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8")
                .build()

            client.newCall(req).execute().use { res ->
                if (res.code in 200..299) {
                    val stream = res.body?.byteStream()
                    if (stream != null) {
                        val bytes = readBytesUpTo(stream, 524288) // max 512KB
                        if (bytes.isNotEmpty()) {
                            val hash = MMH3.hash(bytes, 0)
                            return hash.toString()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Fetch failed
        }

        return "-1"
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

    suspend fun rescanHostDirect(
        sessionId: Long,
        host: String,
        config: ScanConfig = ScanConfig(threads = 1, timeoutSeconds = 10)
    ): ScanResult {
        val cleanHost = NetworkUtil.cleanHostInput(host)
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        })
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }

        val client = baseOkHttpClient.newBuilder()
            .dns(customDns)
            .eventListener(ipCapturingEventListener)
            .connectTimeout(config.timeoutSeconds.toLong().coerceIn(2, 60), TimeUnit.SECONDS)
            .readTimeout(config.timeoutSeconds.toLong().coerceIn(2, 60), TimeUnit.SECONDS)
            .callTimeout((config.timeoutSeconds * 2).toLong().coerceIn(4, 120), TimeUnit.SECONDS)
            .followRedirects(config.followRedirects)
            .followSslRedirects(config.followRedirects)
            .retryOnConnectionFailure(false)
            .sslSocketFactory(sslContext.socketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .build()

        val scanResult = scanSingleHost(
            sessionId = sessionId,
            cleanHost = cleanHost,
            client = client,
            config = config
        )
        resultRepository.insertResult(scanResult)
        _resultStream.emit(scanResult)
        return scanResult
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

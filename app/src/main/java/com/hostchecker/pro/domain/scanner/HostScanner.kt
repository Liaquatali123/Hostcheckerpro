package com.hostchecker.pro.domain.scanner

import com.hostchecker.pro.data.repo.ResultRepository
import com.hostchecker.pro.data.repo.SessionRepository
import com.hostchecker.pro.domain.model.ScanConfig
import com.hostchecker.pro.domain.model.ScanResult
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
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
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Dns
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

    private val dnsCache = ConcurrentHashMap<String, List<InetAddress>>()

    private val customDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val cached = dnsCache[hostname]
            if (cached != null && cached.isNotEmpty()) {
                return cached
            }
            return Dns.SYSTEM.lookup(hostname)
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
                .dns(customDns)
                .connectTimeout(6, TimeUnit.SECONDS)
                .readTimeout(8, TimeUnit.SECONDS)
                .callTimeout(12, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .retryOnConnectionFailure(true)
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

        if (cleanHost.isBlank()) {
            return ScanResult(
                sessionId = sessionId,
                host = rawHost,
                failed = true,
                errorMessage = "Invalid host format"
            )
        }

        // a) DNS RESOLVE — InetAddress.getAllByName(host). If this throws UnknownHostException, mark failed (host does not exist).
        val resolvedAddresses = try {
            InetAddress.getAllByName(cleanHost)
        } catch (e: UnknownHostException) {
            return ScanResult(
                sessionId = sessionId,
                host = cleanHost,
                ip = "",
                failed = true,
                errorMessage = "DNS resolution failed (host does not exist)"
            )
        } catch (e: Exception) {
            return ScanResult(
                sessionId = sessionId,
                host = cleanHost,
                ip = "",
                failed = true,
                errorMessage = "DNS error: ${e.message ?: e.javaClass.simpleName}"
            )
        }

        if (resolvedAddresses.isEmpty()) {
            return ScanResult(
                sessionId = sessionId,
                host = cleanHost,
                ip = "",
                failed = true,
                errorMessage = "DNS returned no records"
            )
        }

        val ip = resolvedAddresses.firstOrNull()?.hostAddress ?: ""
        dnsCache[cleanHost] = resolvedAddresses.toList()

        // b) TCP PROBE on 80 and 443 — Socket connect with 4s timeout each, in parallel via coroutines.
        val (port80Open, port443Open) = checkTcpPorts(if (ip.isNotBlank()) ip else cleanHost, timeoutMs = 4000)
        if (!port80Open && !port443Open) {
            return ScanResult(
                sessionId = sessionId,
                host = cleanHost,
                ip = ip,
                failed = true,
                errorMessage = "TCP ports 80 & 443 closed or unreachable"
            )
        }

        // c, d, e, f, h) Parallel HTTPS & HTTP probes, HEAD fallback, first success wins
        val userAgent = if (config.stealthMode) {
            userAgents.random()
        } else {
            "HostCheckerPro/1.0 (+https://github.com/hostchecker/pro)"
        }

        val probeResult = probeHostProtocols(
            cleanHost = cleanHost,
            port80Open = port80Open,
            port443Open = port443Open,
            client = client,
            userAgent = userAgent
        )

        if (probeResult == null || probeResult.code !in 100..599) {
            return ScanResult(
                sessionId = sessionId,
                host = cleanHost,
                ip = ip,
                code = probeResult?.code ?: 0,
                failed = true,
                errorMessage = "No response on HTTP/HTTPS"
            )
        }

        // Successful response! (100-599 is RESPONDED)
        // FIX 3: ALWAYS attempt favicon fetch
        val faviconHash = fetchFaviconHash(
            client = client,
            baseUrl = probeResult.url,
            cleanHost = cleanHost,
            iconHref = probeResult.iconHref,
            userAgent = userAgent
        )

        val liveResult = ScanResult(
            sessionId = sessionId,
            host = cleanHost,
            ip = ip,
            server = probeResult.server,
            code = probeResult.code,
            ms = probeResult.ms,
            title = probeResult.title,
            faviconHash = faviconHash,
            headers = probeResult.headers,
            failed = false
        )

        // Enrich with ASN, SAN, and CF origin leak probe
        var asn = ""
        var org = ""
        if (liveResult.ip.isNotBlank()) {
            val asnInfo = asnLookup.lookupAsn(liveResult.ip)
            asn = if (asnInfo.asn.isBlank() || asnInfo.asn.equals("UNKNOWN", ignoreCase = true) || asnInfo.asn == "no-asn") "" else asnInfo.asn
            org = if (asnInfo.org.isBlank() || asnInfo.org.equals("Unknown", ignoreCase = true)) "" else asnInfo.org
        }

        val sans = CertParser.extractSans(cleanHost)

        var isCf = liveResult.server.contains("cloudflare", ignoreCase = true)
        var cfOrigin = ""
        if (isCf) {
            val cfProbe = cloudflareProbe.probeOrigin(cleanHost)
            isCf = cfProbe.isFronted
            cfOrigin = cfProbe.originInfo
        }

        return liveResult.copy(
            asn = asn,
            org = org,
            san = sans,
            cloudflareFronted = isCf,
            cloudflareOrigin = cfOrigin
        )
    }

    private suspend fun checkTcpPorts(target: String, timeoutMs: Int): Pair<Boolean, Boolean> = coroutineScope {
        val port443 = async(Dispatchers.IO) { isPortOpen(target, 443, timeoutMs) }
        val port80 = async(Dispatchers.IO) { isPortOpen(target, 80, timeoutMs) }
        Pair(port80.await(), port443.await())
    }

    private fun isPortOpen(target: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(target, port), timeoutMs)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    private data class ProbeAttempt(
        val url: String,
        val code: Int,
        val server: String,
        val ms: Long,
        val title: String,
        val iconHref: String?,
        val headers: Map<String, String>
    )

    private fun trySingleHttpCall(
        url: String,
        method: String,
        client: OkHttpClient,
        userAgent: String
    ): ProbeAttempt? {
        val request = Request.Builder()
            .url(url)
            .method(method, null)
            .header("User-Agent", userAgent)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        val startTime = System.currentTimeMillis()
        var response: Response? = null
        return try {
            response = client.newCall(request).execute()
            val responseTimeMs = System.currentTimeMillis() - startTime
            val code = response.code
            val server = response.header("Server") ?: ""

            val headersMap = mutableMapOf<String, String>()
            for (name in response.headers.names()) {
                headersMap[name] = response.headers[name] ?: ""
            }

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
                    // Peek body error ignored
                }
            }

            ProbeAttempt(
                url = url,
                code = code,
                server = server,
                ms = responseTimeMs,
                title = title,
                iconHref = iconHref,
                headers = headersMap
            )
        } catch (e: Exception) {
            null
        } finally {
            response?.close()
        }
    }

    private fun executeProtocolProbe(
        url: String,
        client: OkHttpClient,
        userAgent: String
    ): ProbeAttempt? {
        var attempt = trySingleHttpCall(url, "GET", client, userAgent)
        if (attempt == null) {
            // e) HEAD FALLBACK — if GET returns no response or times out, try HEAD request
            attempt = trySingleHttpCall(url, "HEAD", client, userAgent)
        }
        return attempt
    }

    private suspend fun probeHostProtocols(
        cleanHost: String,
        port80Open: Boolean,
        port443Open: Boolean,
        client: OkHttpClient,
        userAgent: String
    ): ProbeAttempt? = coroutineScope {
        val channel = Channel<ProbeAttempt?>(Channel.BUFFERED)
        val jobs = mutableListOf<Job>()
        var probeCount = 0

        val launchHttps = port443Open || !port80Open
        val launchHttp = port80Open || !port443Open

        if (launchHttps) {
            probeCount++
            jobs += launch(Dispatchers.IO) {
                val res = executeProtocolProbe("https://$cleanHost", client, userAgent)
                channel.send(res)
            }
        }

        if (launchHttp) {
            probeCount++
            jobs += launch(Dispatchers.IO) {
                val res = executeProtocolProbe("http://$cleanHost", client, userAgent)
                channel.send(res)
            }
        }

        var bestAttempt: ProbeAttempt? = null
        var fallbackAttempt: ProbeAttempt? = null

        for (i in 0 until probeCount) {
            val attempt = channel.receive()
            if (attempt != null && attempt.code in 100..599) {
                bestAttempt = attempt
                jobs.forEach { it.cancel() }
                break
            } else if (attempt != null && fallbackAttempt == null) {
                fallbackAttempt = attempt
            }
        }

        if (bestAttempt == null) {
            if (!launchHttps) {
                val fallbackHttps = executeProtocolProbe("https://$cleanHost", client, userAgent)
                if (fallbackHttps != null && fallbackHttps.code in 100..599) {
                    bestAttempt = fallbackHttps
                }
            } else if (!launchHttp) {
                val fallbackHttp = executeProtocolProbe("http://$cleanHost", client, userAgent)
                if (fallbackHttp != null && fallbackHttp.code in 100..599) {
                    bestAttempt = fallbackHttp
                }
            }
        }

        bestAttempt ?: fallbackAttempt
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
            val protocol = if (baseUrl.startsWith("https")) "https" else "http"
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

        // If icon fetch fails, hash the string "NONE" and store -1 to indicate attempted-but-missing. Display "—".
        MMH3.hash("NONE".toByteArray(Charsets.UTF_8), 0)
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

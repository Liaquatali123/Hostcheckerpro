package com.hostchecker.pro.domain.scanner

import java.net.InetAddress
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class CfProbeResult(
    val isFronted: Boolean,
    val originInfo: String = ""
)

class CloudflareProbe(
    private val baseOkHttpClient: OkHttpClient
) {
    private val probeClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .connectTimeout(3, TimeUnit.SECONDS)
            .readTimeout(3, TimeUnit.SECONDS)
            .followRedirects(false)
            .build()
    }

    private val probeSubdomains = listOf(
        "direct", "origin", "ftp", "mail", "cpanel", "dev", "staging"
    )

    /**
     * Probes for origin leak when Cloudflare CDN is detected.
     */
    suspend fun probeOrigin(host: String): CfProbeResult = withContext(Dispatchers.IO) {
        val cleanHost = host.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .split("/").first()
            .split(":").first()

        for (sub in probeSubdomains) {
            val candidate = "$sub.$cleanHost"
            val ip = try {
                val address = InetAddress.getByName(candidate)
                address.hostAddress
            } catch (e: Exception) {
                null
            }

            if (ip != null) {
                // Resolved! Now send fast probe to check if Server header is NOT cloudflare
                val server = checkServerHeader(candidate)
                if (server != null && !server.contains("cloudflare", ignoreCase = true)) {
                    return@withContext CfProbeResult(
                        isFronted = true,
                        originInfo = "Origin leaked: $candidate ($ip) [Server: $server]"
                    )
                } else if (server == null && !isCloudflareIp(ip)) {
                    // DNS leaked to non-Cloudflare IP
                    return@withContext CfProbeResult(
                        isFronted = true,
                        originInfo = "Potential DNS leak: $candidate ($ip)"
                    )
                }
            }
        }

        CfProbeResult(isFronted = true, originInfo = "")
    }

    private fun checkServerHeader(candidate: String): String? {
        val protocols = listOf("https://$candidate", "http://$candidate")
        for (url in protocols) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .head()
                    .build()
                probeClient.newCall(request).execute().use { response ->
                    return response.header("Server") ?: "Unknown non-CF"
                }
            } catch (e: Exception) {
                // try next protocol
            }
        }
        return null
    }

    private fun isCloudflareIp(ip: String): Boolean {
        // Common Cloudflare IPv4 ranges: 173.245.48.0/20, 103.21.244.0/22, 103.22.200.0/22, 103.31.4.0/22,
        // 141.101.64.0/18, 108.162.192.0/18, 190.93.240.0/20, 188.114.96.0/20, 197.234.240.0/22,
        // 198.41.128.0/17, 162.158.0.0/15, 104.16.0.0/13, 104.24.0.0/14, 172.64.0.0/13, 131.0.72.0/22
        return ip.startsWith("104.") || ip.startsWith("172.6") || ip.startsWith("162.15") ||
                ip.startsWith("198.41.") || ip.startsWith("108.162.")
    }
}

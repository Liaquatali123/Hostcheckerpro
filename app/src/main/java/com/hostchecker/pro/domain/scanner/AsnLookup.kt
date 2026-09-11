package com.hostchecker.pro.domain.scanner

import com.hostchecker.pro.data.repo.ResultRepository
import com.hostchecker.pro.domain.model.AsnInfo
import java.net.InetAddress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

class AsnLookup(
    private val okHttpClient: OkHttpClient,
    private val resultRepository: ResultRepository
) {
    /**
     * Resolves host to IP address.
     */
    suspend fun resolveIp(host: String): String? = withContext(Dispatchers.IO) {
        val cleanHost = host.trim()
            .removePrefix("https://")
            .removePrefix("http://")
            .split("/").first()
            .split(":").first()
        try {
            val address = InetAddress.getByName(cleanHost)
            address.hostAddress
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Looks up ASN and Organization for the given IP address, with 30-day Room caching.
     */
    suspend fun lookupAsn(ip: String): AsnInfo = withContext(Dispatchers.IO) {
        if (ip.isBlank()) return@withContext AsnInfo(ip = "", asn = "", org = "")

        // Check cache first
        val cached = resultRepository.getCachedAsn(ip)
        if (cached != null) {
            return@withContext cached
        }

        // Fetch from ip-api.com
        var info = fetchFromIpApi(ip)
        if (info == null || (info.asn.isBlank() && info.org.isBlank())) {
            // Fallback to ipapi.co
            info = fetchFromIpApiCo(ip)
        }

        val result = info ?: AsnInfo(ip = ip, asn = "UNKNOWN", org = "Unknown")
        if (result.asn.isNotBlank() || result.org.isNotBlank()) {
            resultRepository.cacheAsn(result)
        }
        result
    }

    private fun fetchFromIpApi(ip: String): AsnInfo? {
        val url = "http://ip-api.com/json/$ip?fields=status,as,org,query"
        val request = Request.Builder().url(url).build()
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                if (json.optString("status") == "success") {
                    val rawAs = json.optString("as")
                    val org = json.optString("org")
                    val asn = rawAs.split(" ").firstOrNull() ?: rawAs
                    AsnInfo(ip = ip, asn = asn, org = org)
                } else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun fetchFromIpApiCo(ip: String): AsnInfo? {
        val url = "https://ipapi.co/$ip/json/"
        val request = Request.Builder().url(url).build()
        return try {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val json = JSONObject(body)
                val asn = json.optString("asn")
                val org = json.optString("org")
                AsnInfo(ip = ip, asn = asn, org = org)
            }
        } catch (e: Exception) {
            null
        }
    }
}

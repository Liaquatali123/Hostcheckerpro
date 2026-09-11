package com.hostchecker.pro.domain.scanner

import java.net.InetSocketAddress
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CertParser {

    private val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
    })

    private val sslContext: SSLContext by lazy {
        SSLContext.getInstance("TLS").apply {
            init(null, trustAllCerts, SecureRandom())
        }
    }

    /**
     * Extracts Subject Alternative Names (SAN) from the host's SSL certificate.
     */
    suspend fun extractSans(host: String, port: Int = 443, timeoutMs: Int = 5000): List<String> =
        withContext(Dispatchers.IO) {
            val cleanHost = host.trim().removePrefix("https://").removePrefix("http://").split("/").first().split(":").first()
            var rawSocket: Socket? = null
            var sslSocket: SSLSocket? = null
            try {
                rawSocket = Socket()
                rawSocket.connect(InetSocketAddress(cleanHost, port), timeoutMs)
                rawSocket.soTimeout = timeoutMs

                sslSocket = sslContext.socketFactory.createSocket(
                    rawSocket,
                    cleanHost,
                    port,
                    true
                ) as SSLSocket
                sslSocket.soTimeout = timeoutMs
                sslSocket.startHandshake()

                val certs = sslSocket.session.peerCertificates
                if (certs.isNotEmpty()) {
                    val cert = certs[0] as? X509Certificate ?: return@withContext emptyList()
                    val sanCollection = cert.subjectAlternativeNames ?: return@withContext emptyList()
                    val sans = mutableListOf<String>()
                    for (item in sanCollection) {
                        if (item != null && item.size >= 2) {
                            val type = item[0] as? Int
                            val value = item[1]?.toString()
                            // Type 2 is DNSName, Type 7 is IPAddress
                            if ((type == 2 || type == 7) && !value.isNullOrBlank()) {
                                sans.add(value)
                            }
                        }
                    }
                    return@withContext sans.distinct()
                }
                emptyList()
            } catch (e: Exception) {
                emptyList()
            } finally {
                runCatching { sslSocket?.close() }
                runCatching { rawSocket?.close() }
            }
        }
}

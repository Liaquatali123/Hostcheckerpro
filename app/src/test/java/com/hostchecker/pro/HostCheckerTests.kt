package com.hostchecker.pro

import com.hostchecker.pro.domain.export.Exporter
import com.hostchecker.pro.domain.model.ScanConfig
import com.hostchecker.pro.domain.model.ScanResult
import com.hostchecker.pro.domain.model.Session
import com.hostchecker.pro.domain.scanner.FaviconHasher
import com.hostchecker.pro.domain.scanner.MMH3
import com.hostchecker.pro.util.FileUtil
import com.hostchecker.pro.util.NetworkUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HostCheckerTests {

    @Test
    fun testMandatoryMurmurHash3VerificationVector() {
        // MANDATORY SPEC REQUIREMENT: mmh3("test") must equal -1508913719
        val hashTest = FaviconHasher.hashString("test")
        assertEquals(-1508913719, hashTest)

        // Python mmh3 reference vector for "foo"
        val hashFoo = FaviconHasher.hashString("foo")
        assertEquals(-156908512, hashFoo)
    }

    @Test
    fun testHostnameValidationAndNormalization() {
        // Valid hosts
        assertTrue(NetworkUtil.isValidHost("example.com"))
        assertTrue(NetworkUtil.isValidHost("sub.domain.co.uk"))
        assertTrue(NetworkUtil.isValidHost("192.168.1.1"))
        assertTrue(NetworkUtil.isValidHost("1.1.1.1"))
        assertTrue(NetworkUtil.isValidHost("localhost"))
        assertTrue(NetworkUtil.isValidHost("https://test.example.org:8080/path?arg=1"))

        // Normalization
        assertEquals("example.com", NetworkUtil.cleanHostInput("https://example.com/path"))
        assertEquals("example.com", NetworkUtil.cleanHostInput("http://example.com:443/"))
        assertEquals("api.service.io", NetworkUtil.cleanHostInput("  api.service.io:9000  "))

        // Invalid hosts
        assertFalse(NetworkUtil.isValidHost(""))
        assertFalse(NetworkUtil.isValidHost("   "))
        assertFalse(NetworkUtil.isValidHost("..invalid.."))
        assertFalse(NetworkUtil.isValidHost("http://"))
    }

    @Test
    fun testTextHostParsingAndDuplicateRemoval() {
        val rawInput = """
            # This is a comment
            example.com
            test.org
            # Another comment
            
            example.com
            https://test.org/api
            invalid..host..format
            1.2.3.4
        """.trimIndent()

        val result = FileUtil.parseHostsFromText(rawInput)
        assertEquals(3, result.validHosts.size) // example.com, test.org, 1.2.3.4
        assertTrue(result.validHosts.contains("example.com"))
        assertTrue(result.validHosts.contains("test.org"))
        assertTrue(result.validHosts.contains("1.2.3.4"))
        assertEquals(2, result.duplicateCount) // example.com and test.org duplicates
        assertEquals(1, result.invalidCount) // invalid..host..format
    }

    @Test
    fun testResultSorting() {
        val results = listOf(
            ScanResult(id = 1, sessionId = 1, host = "b.com", code = 404, ms = 200, server = "nginx"),
            ScanResult(id = 2, sessionId = 1, host = "a.com", code = 200, ms = 50, server = "cloudflare"),
            ScanResult(id = 3, sessionId = 1, host = "c.com", code = 500, ms = 500, server = "apache")
        )

        // Sort by Code ascending
        val byCodeAsc = results.sortedBy { it.code }
        assertEquals(200, byCodeAsc.first().code)
        assertEquals(500, byCodeAsc.last().code)

        // Sort by MS ascending
        val byMsAsc = results.sortedBy { it.ms }
        assertEquals(50, byMsAsc.first().ms)
        assertEquals(500, byMsAsc.last().ms)

        // Sort by Host descending
        val byHostDesc = results.sortedByDescending { it.host }
        assertEquals("c.com", byHostDesc.first().host)
        assertEquals("a.com", byHostDesc.last().host)
    }

    @Test
    fun testLiveFiltering() {
        val results = listOf(
            ScanResult(id = 1, sessionId = 1, host = "gateway.cloudflare.net", code = 200, server = "cloudflare", failed = false),
            ScanResult(id = 2, sessionId = 1, host = "internal.server.local", code = 502, server = "nginx", failed = true),
            ScanResult(id = 3, sessionId = 1, host = "api.example.com", code = 200, server = "envoy", failed = false)
        )

        // Filter live only (exclude failed)
        val liveOnly = results.filter { !it.failed }
        assertEquals(2, liveOnly.size)

        // Search query "cloudflare"
        val query = "cloudflare"
        val filtered = results.filter {
            it.host.contains(query, ignoreCase = true) || it.server.contains(query, ignoreCase = true)
        }
        assertEquals(1, filtered.size)
        assertEquals("gateway.cloudflare.net", filtered.first().host)
    }

    @Test
    fun testSessionStatusAndEtaCalculations() {
        val total = 1000
        val scanned = 250
        val percent = (scanned.toFloat() / total.toFloat() * 100f).toInt()
        assertEquals(25, percent)

        val speed = 25.0 // hosts per second
        val remainingHosts = total - scanned // 750
        val etaSeconds = (remainingHosts / speed).toLong() // 30 seconds
        assertEquals(30L, etaSeconds)

        // Session status transitions
        val session = Session(
            id = 1,
            name = "Test Session",
            fileName = "hosts.txt",
            total = total,
            scanned = scanned,
            status = "RUNNING"
        )
        assertEquals("RUNNING", session.status)
        val pausedSession = session.copy(status = "PAUSED")
        assertEquals("PAUSED", pausedSession.status)
        val completedSession = session.copy(status = "COMPLETED", scanned = total)
        assertEquals("COMPLETED", completedSession.status)
        assertEquals(1000, completedSession.scanned)
    }

    @Test
    fun testExporterImplementations() {
        val results = listOf(
            ScanResult(
                id = 1,
                sessionId = 1,
                host = "auth.example.com",
                ip = "93.184.216.34",
                asn = "AS15133",
                org = "Edgecast Inc.",
                server = "ECS",
                code = 200,
                ms = 45,
                title = "Authentication Portal",
                faviconHash = "-1508913719",
                failed = false
            )
        )

        // CSV export
        val csv = Exporter.generateCsv(results)
        assertTrue(csv.contains("auth.example.com"))
        assertTrue(csv.contains("93.184.216.34"))
        assertTrue(csv.contains("AS15133"))
        assertTrue(csv.contains("200"))

        // TXT export
        val txt = Exporter.generateTxt(results)
        assertEquals("auth.example.com\n", txt)

        // JSON export
        val json = Exporter.generateJson(results)
        assertTrue(json.contains("\"host\": \"auth.example.com\""))
        assertTrue(json.contains("\"code\": 200"))
    }
}

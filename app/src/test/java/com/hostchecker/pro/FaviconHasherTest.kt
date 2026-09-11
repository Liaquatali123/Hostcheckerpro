package com.hostchecker.pro

import com.hostchecker.pro.domain.export.Exporter
import com.hostchecker.pro.domain.model.ScanResult
import com.hostchecker.pro.domain.scanner.FaviconHasher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FaviconHasherTest {

    @Test
    fun verifyMurmurHash3TestString() {
        // Austin Appleby reference MurmurHash3 x86 32-bit (signed 32-bit)
        val hashTest = FaviconHasher.hashString("test")
        assertEquals(-1167338989, hashTest)

        val hashFoo = FaviconHasher.hashString("foo")
        assertEquals(-156908512, hashFoo)
    }

    @Test
    fun verifyExporterFormats() {
        val results = listOf(
            ScanResult(
                id = 1,
                sessionId = 10,
                host = "test.cloudfront.net",
                ip = "1.2.3.4",
                server = "cloudflare",
                code = 200,
                ms = 50,
                title = "Test Page",
                faviconHash = "-1508913719"
            )
        )

        val csv = Exporter.generateCsv(results)
        assertTrue(csv.contains("test.cloudfront.net"))
        assertTrue(csv.contains("cloudflare"))
        assertTrue(csv.contains("-1508913719"))

        val txt = Exporter.generateTxt(results)
        assertEquals("test.cloudfront.net\n", txt)

        val json = Exporter.generateJson(results)
        assertTrue(json.contains("test.cloudfront.net"))
    }
}


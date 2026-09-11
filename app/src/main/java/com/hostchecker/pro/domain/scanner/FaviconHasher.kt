package com.hostchecker.pro.domain.scanner

import android.util.Base64

object FaviconHasher {

    /**
     * MurmurHash3 x86 32-bit implementation in Kotlin.
     * Matches Python `mmh3.hash(data, seed=0)`.
     * Example: mmh3("test") == -1508913719
     */
    fun hash(data: ByteArray, seed: Int = 0): Int {
        val c1 = 0xcc9e2d51.toInt()
        val c2 = 0x1b873593.toInt()
        var h = seed
        val length = data.size
        val roundedEnd = length and 3.inv()

        var i = 0
        while (i < roundedEnd) {
            var k = (data[i].toInt() and 0xFF) or
                    ((data[i + 1].toInt() and 0xFF) shl 8) or
                    ((data[i + 2].toInt() and 0xFF) shl 16) or
                    ((data[i + 3].toInt() and 0xFF) shl 24)

            k *= c1
            k = (k shl 15) or (k ushr 17)
            k *= c2

            h = h xor k
            h = (h shl 13) or (h ushr 19)
            h = h * 5 + 0xe6546b64.toInt()
            i += 4
        }

        var k1 = 0
        val remainder = length and 3
        if (remainder == 3) {
            k1 = (data[roundedEnd + 2].toInt() and 0xFF) shl 16
        }
        if (remainder >= 2) {
            k1 = k1 or ((data[roundedEnd + 1].toInt() and 0xFF) shl 8)
        }
        if (remainder >= 1) {
            k1 = k1 or (data[roundedEnd].toInt() and 0xFF)
            k1 *= c1
            k1 = (k1 shl 15) or (k1 ushr 17)
            k1 *= c2
            h = h xor k1
        }

        h = h xor length
        h = h xor (h ushr 16)
        h *= 0x85ebca6b.toInt()
        h = h xor (h ushr 13)
        h *= 0xc2b2ae35.toInt()
        h = h xor (h ushr 16)

        return h
    }

    fun hashString(text: String, seed: Int = 0): Int {
        return hash(text.toByteArray(Charsets.UTF_8), seed)
    }

    /**
     * Compute Shodan-style favicon hash:
     * Base64 encode the favicon bytes with line breaks every 76 chars, then MMH3 hash.
     */
    fun hashFaviconShodan(bytes: ByteArray): Int {
        val base64 = Base64.encodeToString(bytes, Base64.DEFAULT)
        return hash(base64.toByteArray(Charsets.ISO_8859_1), 0)
    }
}

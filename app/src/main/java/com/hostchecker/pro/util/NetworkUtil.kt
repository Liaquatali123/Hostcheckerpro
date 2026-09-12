package com.hostchecker.pro.util

import java.util.regex.Pattern

object NetworkUtil {

    private val HOSTNAME_PATTERN = Pattern.compile(
        "^([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}$"
    )

    private val IPV4_PATTERN = Pattern.compile(
        "^(([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])\\.){3}([0-9]|[1-9][0-9]|1[0-9]{2}|2[0-4][0-9]|25[0-5])$"
    )

    /**
     * Validates whether an input string is a syntactically valid hostname or IP address.
     */
    fun isValidHost(host: String): Boolean {
        val clean = cleanHostInput(host)
        if (clean.isEmpty() || clean.length > 253) return false
        if (clean == "localhost") return true
        if (IPV4_PATTERN.matcher(clean).matches()) return true
        if (HOSTNAME_PATTERN.matcher(clean).matches()) return true
        return clean.matches(Regex("^[a-zA-Z0-9]([a-zA-Z0-9\\-_]{0,62}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9\\-_]{0,62}[a-zA-Z0-9])?)*$"))
    }

    /**
     * Normalizes a host string by stripping http/https schemes, ports, and trailing paths.
     */
    fun cleanHostInput(raw: String): String {
        var host = raw.trim()
        if (host.startsWith("https://", ignoreCase = true)) {
            host = host.substring(8)
        } else if (host.startsWith("http://", ignoreCase = true)) {
            host = host.substring(7)
        }
        val slashIndex = host.indexOf('/')
        if (slashIndex != -1) {
            host = host.substring(0, slashIndex)
        }
        val colonIndex = host.indexOf(':')
        if (colonIndex != -1) {
            host = host.substring(0, colonIndex)
        }
        return host.trim().lowercase()
    }
}

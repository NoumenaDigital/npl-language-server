package com.noumenadigital.npl.lang.server.util

import java.net.URI

object UriFixtures {
    fun normalizeUri(uriString: String): String {
        val normalized = uriString.replace('\\', '/')

        val uri = URI.create(normalized)
        if (uri.scheme == "file") {
            val isWindows = System.getProperty("os.name").lowercase().contains("windows")
            if (isWindows) {
                val testIndex = normalized.indexOf("/test")
                if (testIndex >= 0) {
                    return "file://" + normalized.substring(testIndex)
                }
            }
            val path = uri.path ?: uri.schemeSpecificPart
            val cleanPath = path.dropWhile { it == '/' }
            return "file:///$cleanPath"
        }
        return normalized
    }
}

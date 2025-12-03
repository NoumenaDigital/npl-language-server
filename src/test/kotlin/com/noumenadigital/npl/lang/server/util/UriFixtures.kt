package com.noumenadigital.npl.lang.server.util

import java.net.URI

object UriFixtures {
    fun normalizeUri(uriString: String): String {
        // Convert backslashes to forward slashes
        val normalized = uriString.replace('\\', '/')
        
        val uri = URI.create(normalized)
        if (uri.scheme == "file") {
            // Get the full URI string and find /test
            val testIndex = normalized.indexOf("/test")
            if (testIndex >= 0) {
                // Remove everything before /test and rebuild as file:///test/...
                return "file://" + normalized.substring(testIndex)
            }
            
            // Fallback: use path component
            val path = uri.path ?: uri.schemeSpecificPart
            val cleanPath = path.dropWhile { it == '/' }
            return "file:///$cleanPath"
        }
        return normalized
    }
}

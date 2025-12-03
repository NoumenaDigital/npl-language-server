package com.noumenadigital.npl.lang.server.util

import java.io.File
import java.net.URI

object UriFixtures {
    /**
     * Normalizes URIs to a consistent format for comparison.
     * Handles:
     * - Windows paths with backslashes
     * - file:/ vs file:/// formats
     * - Relative vs absolute paths
     * - Drive letters on Windows
     */
    fun normalizeUri(uriString: String): String {
        // Convert backslashes to forward slashes for URI compatibility
        val withForwardSlashes = uriString.replace('\\', '/')

        // Try to parse as URI
        val uri =
            try {
                URI.create(withForwardSlashes)
            } catch (e: Exception) {
                // If parsing fails, treat as a file path and convert to URI
                return File(uriString).toURI().toString()
            }

        // Handle file URIs
        if (uri.scheme == "file") {
            // Get the path component - use schemeSpecificPart for file:/D:/ format
            // and path for file:///D:/ format
            val rawPath = uri.path ?: uri.schemeSpecificPart
            
            // For file:/D:/path format, the path might be null and schemeSpecificPart is /D:/path
            // For file:///D:/path format, the path is /D:/path
            // We need to handle both cases
            val pathToNormalize = when {
                // If path is null or empty, use schemeSpecificPart
                rawPath.isNullOrEmpty() -> uri.schemeSpecificPart
                // If path doesn't start with / but schemeSpecificPart does, use schemeSpecificPart
                !rawPath.startsWith("/") && uri.schemeSpecificPart.startsWith("/") -> uri.schemeSpecificPart
                else -> rawPath
            }

            // Normalize the path: remove leading slashes
            val cleanPath = pathToNormalize.trimStart('/')

            // Check if this is a Windows absolute path (starts with drive letter)
            val isWindowsAbsolute = cleanPath.matches(Regex("^[A-Za-z]:.*"))

            // Return normalized URI - always use file:/// format for consistency
            return if (isWindowsAbsolute || cleanPath.isNotEmpty()) {
                "file:///$cleanPath"
            } else {
                "file:///"
            }
        }

        // Handle paths without scheme - treat as file paths
        if (uri.scheme == null) {
            return File(uriString).toURI().toString()
        }

        // For other schemes, return with forward slashes
        return withForwardSlashes
    }
}

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
        val uri = try {
            URI.create(withForwardSlashes)
        } catch (e: Exception) {
            // If parsing fails, treat as a file path and convert to URI
            return File(uriString).toURI().toString()
        }
        
        // Handle file URIs
        if (uri.scheme == "file") {
            // Get the path component
            val path = uri.path ?: uri.schemeSpecificPart
            
            // Normalize the path:
            // - Remove leading slashes
            // - Ensure consistent format
            val cleanPath = path.trimStart('/')
            
            // Check if this is a Windows absolute path (starts with drive letter)
            val isWindowsAbsolute = cleanPath.matches(Regex("^[A-Za-z]:.*"))
            
            // Return normalized URI
            return if (isWindowsAbsolute) {
                // Windows absolute path: file:///C:/path
                "file:///$cleanPath"
            } else if (cleanPath.isEmpty()) {
                // Empty path
                "file:///"
            } else {
                // Relative or Unix absolute path: file:///path
                "file:///$cleanPath"
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

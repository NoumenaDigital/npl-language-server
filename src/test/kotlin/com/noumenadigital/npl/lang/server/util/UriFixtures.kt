package com.noumenadigital.npl.lang.server.util

import java.io.File
import java.net.URI

object UriFixtures {
    fun normalizeUri(uriString: String): String {
        // Convert backslashes to forward slashes for URI compatibility
        val withForwardSlashes = uriString.replace('\\', '/')
        
        val uri = try {
            URI.create(withForwardSlashes)
        } catch (e: Exception) {
            // If URI creation fails, treat as a file path
            return File(uriString).toURI().toString()
        }
        
        return if (uri.scheme == "file") {
            val path = uri.path
            val cleanPath = path.dropWhile { it == '/' }
            "file:///$cleanPath"
        } else if (uri.scheme == null) {
            // No scheme means it's a file path, convert it
            File(uriString).toURI().toString()
        } else {
            withForwardSlashes
        }
    }

    fun normalizeWindowsPath(uri: String): String {
        if (File.separatorChar != '\\') {
            return uri
        }

        return uri
            .replace(Regex("file:///([A-Za-z]):")) { match ->
                "${match.groupValues[1]}:\\"
            }.replace(Regex("^file:///"), "")
            .replace('/', '\\')
    }
}

package com.noumenadigital.npl.lang.server.util

import java.io.File
import java.net.URI

object UriFixtures {
    fun normalizeUri(uriString: String): String {
        val withForwardSlashes = uriString.replace('\\', '/')

        val uri =
            try {
                URI.create(withForwardSlashes)
            } catch (e: Exception) {
                return File(uriString).toURI().toString()
            }

        return when (uri.scheme) {
            uri.scheme -> {
                val path = uri.path ?: uri.schemeSpecificPart
                val cleanPath = path.dropWhile { it == '/' }
                "file:///$cleanPath"
            }
            uri.scheme -> {
                File(uriString).toURI().toString()
            }
            else -> {
                withForwardSlashes
            }
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

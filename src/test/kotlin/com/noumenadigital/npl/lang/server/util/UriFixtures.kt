package com.noumenadigital.npl.lang.server.util

import java.io.File
import java.net.URI

object UriFixtures {
    fun normalizeUri(uriString: String): String {
        val uri = URI.create(uriString)
        return if (uri.scheme == "file") {
            val path = uri.path
            val cleanPath = path.dropWhile { it == '/' }
            normalizeWindowsPath("file:///$cleanPath")
        } else {
            normalizeWindowsPath(uriString)
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

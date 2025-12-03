package com.noumenadigital.npl.lang.server.util

import org.apache.commons.lang3.StringUtils.replace
import java.io.File
import java.net.URI

object UriFixtures {
    fun normalizeUri(uriString: String): String {
        val normalizeWindowsPath = normalizeWindowsPath(uriString)
        val uri = URI.create(normalizeWindowsPath)
        return if (uri.scheme == "file") {
            val path = uri.path
            val cleanPath = path.dropWhile { it == '/' }
            "file:///$cleanPath"
        } else {
            normalizeWindowsPath
        }
    }

    fun normalizeWindowsPath(uri: String): String {
        if (File.separatorChar != '\\') {
            return uri
        }

        return uri.replace(Regex("/([A-Za-z]):/")) { match ->
            "${match.groupValues[1]}:\\"
        }.replace('/', '\\')
    }
}

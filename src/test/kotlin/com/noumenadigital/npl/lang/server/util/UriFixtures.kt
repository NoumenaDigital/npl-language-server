package com.noumenadigital.npl.lang.server.util

import java.net.URI

object UriFixtures {
    fun normalizeUri(uriString: String): String {
        val uri = URI.create(uriString)
        return if (uri.scheme == "file") {
            val path = uri.path
            val cleanPath = path.dropWhile { it == '/' }
            "file:///$cleanPath".replaceAll(".+/test", "/");
        } else {
            uriString.replaceAll(".+/test", "/");
        }
    }
}

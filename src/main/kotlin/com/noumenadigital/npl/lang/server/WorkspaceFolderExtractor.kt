package com.noumenadigital.npl.lang.server

import com.google.gson.JsonObject
import mu.KotlinLogging

private val logger = KotlinLogging.logger { }

object WorkspaceFolderExtractor {
    fun extractWorkspaceFolderUris(
        initializationOptions: Any?,
        standardWorkspaceFolderUris: List<String>?,
    ): List<String> {
        val effectiveUris = extractUrisFromEffectiveWorkspaceFolders(initializationOptions)
        if (effectiveUris.isNotEmpty()) {
            logger.info("Using ${effectiveUris.size} URIs from effectiveWorkspaceFolders")
            return effectiveUris
        }

        logger.info("Using standard workspaceFolders")
        val standardUris = standardWorkspaceFolderUris ?: emptyList()

        if (standardUris.isEmpty()) {
            logger.warn("No workspace folders found in either effectiveWorkspaceFolders or standard workspaceFolders")
        } else {
            logger.info("Found ${standardUris.size} standard workspace folders")
        }

        return standardUris
    }

    private fun extractUrisFromEffectiveWorkspaceFolders(options: Any?): List<String> {
        if (options !is JsonObject || !options.has("effectiveWorkspaceFolders")) {
            return emptyList()
        }

        val folderArray =
            try {
                options.getAsJsonArray("effectiveWorkspaceFolders")
            } catch (e: Exception) {
                logger.warn(e) { "Error parsing effectiveWorkspaceFolders" }
                return emptyList()
            }

        val uris = mutableListOf<String>()
        folderArray.forEach { element ->
            if (element is JsonObject && element.has("uri")) {
                try {
                    val uri = element.get("uri").asString
                    uris.add(uri)
                } catch (e: Exception) {
                    logger.warn(e) { "Failed to extract URI from workspace folder" }
                }
            }
        }

        return uris
    }
}

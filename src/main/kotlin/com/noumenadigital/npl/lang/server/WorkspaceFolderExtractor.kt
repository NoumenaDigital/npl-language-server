package com.noumenadigital.npl.lang.server

import com.google.gson.JsonObject
import mu.KotlinLogging
import org.eclipse.lsp4j.InitializeParams

private val logger = KotlinLogging.logger {}

/**
 * Utility to extract workspace folder URIs from LSP initialization parameters.
 */
object WorkspaceFolderExtractor {
    /**
     * Extracts workspace folder URIs from initialization parameters.
     * First attempts to get URIs from initializationOptions.effectiveWorkspaceFolders,
     * then falls back to standard workspaceFolders if needed.
     */
    fun extractWorkspaceFolderUris(params: InitializeParams): List<String> {
        val effectiveUris = extractUrisFromEffectiveWorkspaceFolders(params.initializationOptions)
        if (effectiveUris.isNotEmpty()) {
            return effectiveUris
        }

        logger.info("Using standard workspaceFolders")
        return params.workspaceFolders
            ?.filterNotNull()
            ?.mapNotNull { it.uri }
            ?: emptyList()
    }

    /**
     * Extracts URIs from the effectiveWorkspaceFolders field in initializationOptions.
     * Returns an empty list if the field is missing or malformed.
     */
    private fun extractUrisFromEffectiveWorkspaceFolders(options: Any?): List<String> {
        if (options !is JsonObject || !options.has("effectiveWorkspaceFolders")) {
            return emptyList()
        }

        val folderArray =
            try {
                options.getAsJsonArray("effectiveWorkspaceFolders")
            } catch (e: Exception) {
                logger.warn("Error parsing effectiveWorkspaceFolders: ${e.message}")
                return emptyList()
            }

        val uris = mutableListOf<String>()
        folderArray.forEach { element ->
            if (element is JsonObject && element.has("uri")) {
                try {
                    val uri = element.get("uri").asString
                    uris.add(uri)
                } catch (e: Exception) {
                    logger.warn("Failed to extract URI from workspace folder: ${e.message}")
                }
            }
        }

        if (uris.isNotEmpty()) {
            logger.info("Using URIs from effectiveWorkspaceFolders")
        }

        return uris
    }
}

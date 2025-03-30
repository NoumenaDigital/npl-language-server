package com.noumenadigital.npl.lang.server

import com.google.gson.JsonObject
import com.noumenadigital.npl.lang.server.logging.LSPLogger
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.services.LanguageClient

/**
 * Utility to extract workspace folder URIs from LSP initialization parameters.
 */
object WorkspaceFolderExtractor {
    /**
     * Extracts workspace folder URIs from initialization parameters.
     * First attempts to get URIs from initializationOptions.effectiveWorkspaceFolders,
     * then falls back to standard workspaceFolders if needed.
     */
    fun extractWorkspaceFolderUris(
        params: InitializeParams,
        client: LanguageClient? = null,
    ): List<String> {
        val logger = LSPLogger("com.noumenadigital.npl.lang.server.WorkspaceFolderExtractor") { client }

        val effectiveUris = extractUrisFromEffectiveWorkspaceFolders(params.initializationOptions, logger)
        if (effectiveUris.isNotEmpty()) {
            logger.info("Using ${effectiveUris.size} URIs from effectiveWorkspaceFolders")
            return effectiveUris
        }

        logger.info("Using standard workspaceFolders")
        val standardUris =
            params.workspaceFolders
                ?.filterNotNull()
                ?.mapNotNull { it.uri }
                ?: emptyList()

        if (standardUris.isEmpty()) {
            logger.warn("No workspace folders found in either effectiveWorkspaceFolders or standard workspaceFolders")
        } else {
            logger.info("Found ${standardUris.size} standard workspace folders")
        }

        return standardUris
    }

    /**
     * Extracts URIs from the effectiveWorkspaceFolders field in initializationOptions.
     * Returns an empty list if the field is missing or malformed.
     */
    private fun extractUrisFromEffectiveWorkspaceFolders(
        options: Any?,
        logger: LSPLogger,
    ): List<String> {
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

        return uris
    }
}

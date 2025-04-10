package com.noumenadigital.npl.lang.server

import com.google.gson.Gson
import com.google.gson.JsonObject
import mu.KotlinLogging

private val logger = KotlinLogging.logger { }

data class InitializationOptions(
    val effectiveWorkspaceFolders: List<EffectiveWorkspaceFolder>?,
)

data class EffectiveWorkspaceFolder(
    val uri: String,
    val name: String,
)

object WorkspaceFolderExtractor {
    private val gson = Gson()

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
        if (options == null) {
            return emptyList()
        }

        try {
            val initOptions = gson.fromJson(options as JsonObject, InitializationOptions::class.java)

            return initOptions.effectiveWorkspaceFolders
                ?.mapNotNull { it.uri.takeIf(String::isNotBlank) }
                ?: emptyList()
        } catch (e: Exception) {
            logger.warn(e) { "Error parsing effectiveWorkspaceFolders" }
            return emptyList()
        }
    }
}

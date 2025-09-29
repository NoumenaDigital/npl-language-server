package com.noumenadigital.npl.lang.server

import com.google.gson.Gson
import mu.KotlinLogging

private val logger = KotlinLogging.logger { }

data class InitializationOptions(
    val effectiveWorkspaceFolders: List<EffectiveWorkspaceFolder>? = emptyList(),
    val nplServerDebouncingTimeMs: Int = 300,
)

data class EffectiveWorkspaceFolder(
    val uri: String?,
    val name: String?,
)

object WorkspaceFolderExtractor {
    private val gson = Gson()

    fun extractWorkspaceFolderUris(
        effectiveWorkspaceFolders: List<EffectiveWorkspaceFolder>?,
        standardWorkspaceFolderUris: List<String>?,
    ): List<String> {
        val effectiveUris =
            effectiveWorkspaceFolders
                ?.mapNotNull { wsf -> wsf.uri.takeIf { !it.isNullOrBlank() } }
                ?: emptyList()

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

    fun extractUrisFromInitializationOptions(initParams: InitializationOptions): List<String> {
        try {
            return extractWorkspaceFolderUris(initParams.effectiveWorkspaceFolders, null)
        } catch (e: Exception) {
            logger.warn(e) { "Error parsing effectiveWorkspaceFolders" }
            return emptyList()
        }
    }
}

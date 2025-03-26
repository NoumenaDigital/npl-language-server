package com.noumenadigital.npl.lang.server.compilation

import com.noumenadigital.npl.lang.CompileException
import com.noumenadigital.npl.lang.CompileFailure
import com.noumenadigital.npl.lang.CompileResult
import com.noumenadigital.npl.lang.CompileWarningException
import com.noumenadigital.npl.lang.CompilerConfiguration
import com.noumenadigital.npl.lang.Loader
import com.noumenadigital.npl.lang.Source
import com.noumenadigital.npl.lang.server.LanguageClientProvider
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.intellij.lang.annotations.Language
import org.slf4j.LoggerFactory
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.extension

private const val NPL_FILE_EXTENSION = "npl"
private const val TARGET_DIR_PATTERN = "/target/"

interface CompilerService {
    fun updateSource(
        uri: String,
        @Language("NPL") content: String,
    )

    fun preloadSources(nplRootUris: List<String>)
}

class DefaultCompilerService(
    private val clientProvider: LanguageClientProvider,
) : CompilerService {
    private val sources = mutableMapOf<String, Source>()
    private val modifiedSources = mutableSetOf<String>()
    private var lastCompileResult: CompileResult? = null
    private var workspacePaths: List<Path> = emptyList()
    private val logger = LoggerFactory.getLogger(DefaultCompilerService::class.java)

    private fun compileIfNeeded() {
        if (modifiedSources.isEmpty() && lastCompileResult != null) return

        // Only compile sources within the workspace
        val sourceList = sources.values.filter { isInWorkspace(Path.of(it.location.toURI())) }.toList()
        if (sourceList.isEmpty()) return

        try {
            // TODO ST-4481: Improve error handling - the compiler should not throw exceptions for normal compilation errors.
            // Instead, errors should be part of the CompileResult and we should avoid try-catch for expected errors.
            val compileResult =
                Loader(CompilerConfiguration(tag = null, quirksMode = true))
                    .loadPackages(sources = sourceList)
            lastCompileResult = compileResult
            modifiedSources.clear()
            publishDiagnostics(sourceList, compileResult)
        } catch (e: CompileException) {
            // This catch block should only handle unexpected errors, not normal compilation errors
            publishCaughtExceptionDiagnostic(e)
        }
    }

    private fun publishDiagnostics(
        sources: List<Source>,
        compileResult: CompileResult,
    ) {
        val compilerExceptions = compileResult.warnings + (compileResult as? CompileFailure)?.errors.orEmpty()
        val exceptionsByUri = compilerExceptions.groupBy { it.sourceInfo.location.toURI() }

        sources.forEach { source ->
            val uri = source.location.toURI().toString()
            val diagnostics =
                exceptionsByUri[source.location.toURI()]
                    ?.map { createDiagnostic(it) }
                    .orEmpty()

            clientProvider.client?.publishDiagnostics(PublishDiagnosticsParams(uri, diagnostics))
        }
    }

    private fun publishCaughtExceptionDiagnostic(compileException: CompileException) {
        val uri =
            compileException.sourceInfo.location
                .toURI()
                .toString()
        if (!isInWorkspace(createPathFromUri(uri))) return

        val diagnostic = createDiagnostic(compileException)
        clientProvider.client?.publishDiagnostics(PublishDiagnosticsParams(uri, listOf(diagnostic)))
    }

    private fun createPathFromUri(uri: String) = Paths.get(URI.create(uri))

    override fun updateSource(
        uri: String,
        content: String,
    ) {
        val path = createPathFromUri(uri)
        if (path.extension != NPL_FILE_EXTENSION) return

        if (isInWorkspace(path)) {
            sources[uri] = Source(path, content)
            modifiedSources.add(uri)
            compileIfNeeded()
        } else if (sources.containsKey(uri)) {
            // Clear diagnostics and remove file if outside workspace
            sources.remove(uri)
            clientProvider.client?.publishDiagnostics(PublishDiagnosticsParams(uri, emptyList()))
        }
    }

    override fun preloadSources(nplRootUris: List<String>) {
        // When workspace changes, we need to clear diagnostics for files that are no longer in workspace
        val oldSources = sources.keys.toSet()

        workspacePaths = nplRootUris.map { createPathFromUri(it) }
        logger.info("Setting workspaces: ${workspacePaths.map { it.toAbsolutePath() }}")

        sources.clear()
        modifiedSources.clear()

        try {
            workspacePaths.forEach { workspacePath ->
                Files.walk(workspacePath).use { pathStream ->
                    pathStream
                        .filter { Files.isRegularFile(it) && it.extension == NPL_FILE_EXTENSION }
                        .filter { !it.toString().contains(TARGET_DIR_PATTERN) }
                        .forEach { path ->
                            val uri = path.toUri().toString()
                            sources[uri] = Source(path, Files.readString(path))
                            modifiedSources.add(uri)
                        }
                }
            }

            // Clear diagnostics for files that were in the previous workspace but not in the new one
            val newSources = sources.keys.toSet()
            val removedSources = oldSources - newSources

            removedSources.forEach { uri ->
                clientProvider.client?.publishDiagnostics(PublishDiagnosticsParams(uri, emptyList()))
            }

            compileIfNeeded()
        } catch (e: Exception) {
            logger.error("Error preloading sources: ${e.message}")
        }
    }

    private fun isInWorkspace(path: Path): Boolean =
        if (workspacePaths.isEmpty()) {
            true // Accept all files if workspace not set
        } else {
            workspacePaths.any { workspace ->
                try {
                    path.toRealPath().startsWith(workspace.toRealPath())
                } catch (e: Exception) {
                    false
                }
            }
        }

    private fun createDiagnostic(compileException: CompileException): Diagnostic {
        val snippetLines = compileException.sourceInfo.snippet.split("\n")
        val startPosition =
            Position(
                compileException.sourceInfo.line - 1,
                compileException.sourceInfo.column - 1,
            )
        val endPosition =
            Position(
                compileException.sourceInfo.line + snippetLines.size - 1,
                compileException.sourceInfo.column + snippetLines.last().length - 1,
            )
        val range = Range(startPosition, endPosition)

        val severity =
            when (compileException) {
                is CompileWarningException -> DiagnosticSeverity.Warning
                else -> DiagnosticSeverity.Error
            }

        return Diagnostic(range, compileException.messageText, severity, "NPL compiler", compileException.code)
    }
}

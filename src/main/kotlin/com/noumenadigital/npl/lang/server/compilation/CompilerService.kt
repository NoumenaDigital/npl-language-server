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
import java.net.URI
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.extension

private const val NPL_FILE_EXTENSION = "npl"
private const val TARGET_DIR_PATTERN = "/target/"

interface CompilerService {
    fun updateSource(uri: String, @Language("NPL") content: String)
    fun preloadSources(nplRootUri: String)
}

class DefaultCompilerService(
    private val clientProvider: LanguageClientProvider,
) : CompilerService {
    private val sources = mutableMapOf<String, Source>()
    private val modifiedSources = mutableSetOf<String>()
    private var lastCompileResult: CompileResult? = null

    private fun compileIfNeeded() {
        if (modifiedSources.isEmpty() && lastCompileResult != null) return

        val sourceList = sources.values.toList()
        try {
            // TODO ST-4481: Improve error handling - the compiler should not throw exceptions for normal compilation errors.
            // Instead, errors should be part of the CompileResult and we should avoid try-catch for expected errors.
            val compileResult = Loader(CompilerConfiguration(tag = null, quirksMode = true))
                .loadPackages(sources = sourceList)
            lastCompileResult = compileResult
            modifiedSources.clear()
            publishDiagnostics(sourceList, compileResult)
        } catch (e: CompileException) {
            // This catch block should only handle unexpected errors, not normal compilation errors
            publishCaughtExceptionDiagnostic(e)
        }
    }

    private fun publishDiagnostics(sources: List<Source>, compileResult: CompileResult) {
        val compilerExceptions = compileResult.warnings + (compileResult as? CompileFailure)?.errors.orEmpty()
        val exceptionsByUri = compilerExceptions.groupBy { it.sourceInfo.location.toURI() }

        sources.forEach { source ->
            val uri = source.location.toURI().toString()
            val diagnostics = exceptionsByUri[source.location.toURI()]
                ?.map { createDiagnostic(it) }
                .orEmpty()

            clientProvider.client?.publishDiagnostics(PublishDiagnosticsParams(uri, diagnostics))
        }
    }

    private fun publishCaughtExceptionDiagnostic(compileException: CompileException) {
        val uri = compileException.sourceInfo.location.toURI().toString()
        val diagnostic = createDiagnostic(compileException)
        clientProvider.client?.publishDiagnostics(PublishDiagnosticsParams(uri, listOf(diagnostic)))
    }

    private fun createPathFromUri(uri: String) = Paths.get(URI.create(uri))

    override fun updateSource(uri: String, content: String) {
        val path = createPathFromUri(uri)
        if (path.extension == NPL_FILE_EXTENSION) {
            sources[uri] = Source(path, content)
            modifiedSources.add(uri)
            compileIfNeeded()
        }
    }

    override fun preloadSources(nplRootUri: String) {
        val rootPath = createPathFromUri(nplRootUri)
        Files.walk(rootPath).use { pathStream ->
            pathStream
                .filter { Files.isRegularFile(it) && it.extension == NPL_FILE_EXTENSION }
                .filter { !it.toRealPath().toString().contains(TARGET_DIR_PATTERN) }
                .forEach { path ->
                    val uri = path.toUri().toString()
                    sources[uri] = Source(path, Files.readString(path))
                    modifiedSources.add(uri)
                }
        }

        compileIfNeeded()
    }

    private fun createDiagnostic(compileException: CompileException): Diagnostic {
        val snippetLines = compileException.sourceInfo.snippet.split("\n")
        val startPosition = Position(
            compileException.sourceInfo.line - 1,
            compileException.sourceInfo.column - 1,
        )
        val endPosition = Position(
            compileException.sourceInfo.line + snippetLines.size - 1,
            compileException.sourceInfo.column + snippetLines.last().length - 1,
        )
        val range = Range(startPosition, endPosition)

        val severity = when (compileException) {
            is CompileWarningException -> DiagnosticSeverity.Warning
            else -> DiagnosticSeverity.Error
        }

        return Diagnostic(range, compileException.messageText, severity, "NPL compiler", compileException.code)
    }
}

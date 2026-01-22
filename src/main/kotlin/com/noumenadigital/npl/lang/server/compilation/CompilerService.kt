package com.noumenadigital.npl.lang.server.compilation

import com.noumenadigital.npl.contrib.DefaultNplContribLoader
import com.noumenadigital.npl.contrib.NplContribConfiguration
import com.noumenadigital.npl.lang.CompileException
import com.noumenadigital.npl.lang.CompileFailure
import com.noumenadigital.npl.lang.CompileResult
import com.noumenadigital.npl.lang.CompileWarningException
import com.noumenadigital.npl.lang.CompilerConfiguration
import com.noumenadigital.npl.lang.Loader
import com.noumenadigital.npl.lang.Source
import com.noumenadigital.npl.lang.server.LanguageClientProvider
import org.apache.commons.vfs2.FileObject
import org.apache.commons.vfs2.VFS
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.intellij.lang.annotations.Language
import java.io.File
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.extension

private const val NPL_FILE_EXTENSION = "npl"
private const val TARGET_DIR_PATTERN = "/target/"

interface CompilerService {
    fun updateSource(
        uri: String,
        @Language("NPL") content: String,
    )

    fun removeSource(uri: String)

    fun preloadSources(
        nplRootUris: List<String>,
        nplContribLibs: List<String> = emptyList(),
    )
}

class DefaultCompilerService(
    private val clientProvider: LanguageClientProvider,
) : CompilerService {
    private val sources = mutableMapOf<String, Source>()
    private val modifiedSources = mutableSetOf<String>()
    private var lastCompileResult: CompileResult? = null
    private var workspacePaths: List<Path> = emptyList()
    private var contribLibSources: List<Source> = emptyList()
    private var nplContribConfiguration: NplContribConfiguration = NplContribConfiguration()

    private fun compileIfNeeded() {
        if (modifiedSources.isEmpty() && lastCompileResult != null) return

        // Only compile sources within the workspace
        val sourceList = sources.values.filter { isInWorkspace(Path.of(it.location.toURI())) }.toList()
        if (sourceList.isEmpty()) return
        try {
            // TODO ST-4481: Improve error handling - the compiler should not throw exceptions for normal compilation errors.
            // Instead, errors should be part of the CompileResult and we should avoid try-catch for expected errors.
            val compileResult =
                Loader(CompilerConfiguration(tag = null, quirksMode = true, nplContribConfiguration = nplContribConfiguration))
                    .loadPackages(sources = sourceList + contribLibSources)
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
            modifiedSources.remove(uri)

            clientProvider.client?.publishDiagnostics(PublishDiagnosticsParams(uri, emptyList()))
        }
    }

    override fun removeSource(uri: String) {
        // Remove from sources if it exists
        sources.remove(uri)

        // Always mark as modified to force recompilation
        // This is necessary to ensure that references to the deleted file in other files
        // are properly marked as errors, even if the deleted file wasn't in our cache
        modifiedSources.add(uri)
        compileIfNeeded()

        // Always send empty diagnostics to clear any existing diagnostics for this URI
        clientProvider.client?.publishDiagnostics(PublishDiagnosticsParams(uri, emptyList()))
    }

    override fun preloadSources(
        nplRootUris: List<String>,
        nplContribLibs: List<String>,
    ) {
        // When workspace changes, we need to clear diagnostics for files that are no longer in workspace
        val oldSources = sources.keys.toSet()
        sources.clear()
        modifiedSources.clear()

        workspacePaths = nplRootUris.map { createPathFromUri(it) }
        if (nplContribLibs.isNotEmpty()) {
            val archive = zipWorkspaceSources(nplRootUris)
            contribLibSources = DefaultNplContribLoader.extractNplContribLibSources(nplContribLibs, archive)
        }

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
    }

    private fun zipWorkspaceSources(nplRootUris: List<String>): FileObject {
        val zipPath = Files.createTempFile("archive", ".zip")
        zipPath.toFile().deleteOnExit()

        ZipOutputStream(Files.newOutputStream(zipPath)).use { zos ->
            nplRootUris.forEach { uriString ->
                val rootPath = Paths.get(URI(uriString))

                Files.walk(rootPath).forEach { path ->
                    if (Files.isDirectory(path)) return@forEach

                    val entryName =
                        rootPath
                            .relativize(path)
                            .toString()
                            .replace(File.separatorChar, '/')

                    zos.putNextEntry(ZipEntry(entryName))
                    Files.copy(path, zos)
                    zos.closeEntry()
                }
            }
        }

        val archiveUri = "zip:${zipPath.toUri()}!/"
        return VFS.getManager().resolveFile(archiveUri)
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

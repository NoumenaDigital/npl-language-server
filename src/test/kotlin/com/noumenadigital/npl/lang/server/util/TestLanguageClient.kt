package com.noumenadigital.npl.lang.server.util

import com.noumenadigital.npl.lang.server.util.UriFixtures.normalizeUri
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.ClientInfo
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FileEvent
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.MessageActionItem
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.ShowMessageRequestParams
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageServer
import org.intellij.lang.annotations.Language
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class TestLanguageClient(
    private val expectedDiagnosticsCount: Int = 1,
) : LanguageClient {
    private lateinit var server: LanguageServer
    private val receivedMessages = mutableListOf<MessageParams>()
    private val allDiagnostics = mutableListOf<PublishDiagnosticsParams>()
    private var diagnosticsLatch = CountDownLatch(0)
    private val defaultTimeoutSeconds = 5L

    val diagnostics: List<PublishDiagnosticsParams>
        get() = synchronized(allDiagnostics) { allDiagnostics.toList() }

    fun connect(server: LanguageServer) {
        this.server = server
    }

    fun initialize(params: InitializeParams = createDefaultParams()): CompletableFuture<InitializeResult> {
        val future = server.initialize(params)
        future.get(defaultTimeoutSeconds, TimeUnit.SECONDS)
        server.initialized(null)
        return future
    }

    private fun createDefaultParams(): InitializeParams =
        InitializeParams().apply {
            clientInfo = ClientInfo("Test Client", "1.0.0")
            capabilities =
                ClientCapabilities().apply {
                    textDocument = TextDocumentClientCapabilities()
                }
        }

    fun openDocument(
        uri: String,
        @Language("NPL") text: String,
    ) {
        val item = TextDocumentItem(uri, "npl", 1, text)
        server.textDocumentService.didOpen(DidOpenTextDocumentParams(item))
    }

    fun changeDocument(
        uri: String,
        newText: String,
    ) {
        val identifier = VersionedTextDocumentIdentifier(uri, 2)
        val contentChange = TextDocumentContentChangeEvent(newText)
        server.textDocumentService.didChange(
            DidChangeTextDocumentParams(identifier, listOf(contentChange)),
        )
    }

    fun deleteDocument(uri: String) {
        val identifier = VersionedTextDocumentIdentifier(uri, 3)
        server.textDocumentService.didClose(DidCloseTextDocumentParams(identifier))
    }

    fun deleteFile(uri: String) {
        val change = FileEvent(uri, FileChangeType.Deleted)
        server.workspaceService.didChangeWatchedFiles(DidChangeWatchedFilesParams(listOf(change)))
    }

    fun expectDiagnostics() = synchronized(allDiagnostics) { diagnosticsLatch = CountDownLatch(1) }

    fun clearDiagnostics() = synchronized(allDiagnostics) { allDiagnostics.clear() }

    fun waitForDiagnostics(
        timeout: Long = defaultTimeoutSeconds,
        unit: TimeUnit = TimeUnit.SECONDS,
    ): Boolean = diagnosticsLatch.await(timeout, unit)

    fun hasDiagnosticsForUri(normalizedUri: String): Boolean =
        synchronized(allDiagnostics) {
            println("mmozhzhe" + normalizedUri)
            return allDiagnostics.any {
                println("mmozhzhe1" + it.uri)
                println("mmozhzhe1_normal" + normalizeUri(it.uri))
                println("mmozhzhe2" + normalizedUri)
                println("mmozhzhe2_normal" + normalizeUri(normalizedUri))
                return normalizeUri(it.uri) == normalizeUri(normalizedUri)
            }
        }

    fun verifyDiagnosticsContain(
        uri: String,
        @Language("NPL") sourceCode: String,
        expectedDiagnostics: List<DiagnosticTestUtils.ExpectedDiagnostic>,
    ) {
        if (expectedDiagnostics.isEmpty()) return

        val diagnostics = getDiagnosticsForUri(uri)
        DiagnosticTestUtils.verifyDiagnostics(
            diagnostics = diagnostics,
            sourceCode = sourceCode,
            expectedDiagnostics = expectedDiagnostics,
        )
    }

    private fun getDiagnosticsForUri(normalizedUri: String): List<Diagnostic> =
        synchronized(allDiagnostics) {
            return allDiagnostics
                .filter { normalizeUri(it.uri) == normalizeUri(normalizedUri) }
                .flatMap { it.diagnostics }
        }

    fun getDiagnostics(normalizedUri: String): PublishDiagnosticsParams? =
        synchronized(allDiagnostics) {
            return allDiagnostics.lastOrNull { normalizeUri(it.uri) == normalizeUri(normalizedUri) }
        }

    fun shutdown(): CompletableFuture<Any> = server.shutdown()

    fun exit() = server.exit()

    override fun telemetryEvent(`object`: Any?) {}

    override fun publishDiagnostics(diagnostics: PublishDiagnosticsParams?) {
        diagnostics?.let { params ->
            synchronized(allDiagnostics) {
                allDiagnostics.add(params)
                val totalDiagnostics = allDiagnostics.flatMap { it.diagnostics }.size

                if (diagnosticsLatch.count > 0 && totalDiagnostics >= expectedDiagnosticsCount) {
                    diagnosticsLatch.countDown()
                }
            }
        }
    }

    override fun showMessage(messageParams: MessageParams?) {
        messageParams?.let { receivedMessages.add(it) }
    }

    override fun showMessageRequest(requestParams: ShowMessageRequestParams?): CompletableFuture<MessageActionItem> =
        CompletableFuture.completedFuture(MessageActionItem())

    override fun logMessage(message: MessageParams?) {
        message?.let { receivedMessages.add(it) }
    }
}

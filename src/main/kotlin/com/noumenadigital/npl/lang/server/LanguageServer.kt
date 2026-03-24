package com.noumenadigital.npl.lang.server

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.noumenadigital.npl.lang.server.compilation.CompilerService
import com.noumenadigital.npl.lang.server.compilation.DefaultCompilerService
import mu.KotlinLogging
import org.eclipse.lsp4j.CallHierarchyIncomingCall
import org.eclipse.lsp4j.CallHierarchyIncomingCallsParams
import org.eclipse.lsp4j.CallHierarchyItem
import org.eclipse.lsp4j.CallHierarchyOutgoingCall
import org.eclipse.lsp4j.CallHierarchyOutgoingCallsParams
import org.eclipse.lsp4j.CallHierarchyPrepareParams
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.CodeLensParams
import org.eclipse.lsp4j.ColorInformation
import org.eclipse.lsp4j.ColorPresentation
import org.eclipse.lsp4j.ColorPresentationParams
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionList
import org.eclipse.lsp4j.CompletionParams
import org.eclipse.lsp4j.CreateFilesParams
import org.eclipse.lsp4j.DeclarationParams
import org.eclipse.lsp4j.DefinitionParams
import org.eclipse.lsp4j.DeleteFilesParams
import org.eclipse.lsp4j.DidChangeConfigurationParams
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidChangeWatchedFilesParams
import org.eclipse.lsp4j.DidChangeWorkspaceFoldersParams
import org.eclipse.lsp4j.DidCloseTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.DidSaveTextDocumentParams
import org.eclipse.lsp4j.DocumentColorParams
import org.eclipse.lsp4j.DocumentDiagnosticParams
import org.eclipse.lsp4j.DocumentDiagnosticReport
import org.eclipse.lsp4j.DocumentFormattingParams
import org.eclipse.lsp4j.DocumentHighlight
import org.eclipse.lsp4j.DocumentHighlightParams
import org.eclipse.lsp4j.DocumentLink
import org.eclipse.lsp4j.DocumentLinkParams
import org.eclipse.lsp4j.DocumentOnTypeFormattingParams
import org.eclipse.lsp4j.DocumentRangeFormattingParams
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.DocumentSymbolParams
import org.eclipse.lsp4j.ExecuteCommandParams
import org.eclipse.lsp4j.FileChangeType
import org.eclipse.lsp4j.FoldingRange
import org.eclipse.lsp4j.FoldingRangeRequestParams
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.HoverParams
import org.eclipse.lsp4j.ImplementationParams
import org.eclipse.lsp4j.InlayHint
import org.eclipse.lsp4j.InlayHintParams
import org.eclipse.lsp4j.InlineValue
import org.eclipse.lsp4j.InlineValueParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.InitializeResult
import org.eclipse.lsp4j.LinkedEditingRangeParams
import org.eclipse.lsp4j.LinkedEditingRanges
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.LocationLink
import org.eclipse.lsp4j.Moniker
import org.eclipse.lsp4j.MonikerParams
import org.eclipse.lsp4j.PrepareRenameDefaultBehavior
import org.eclipse.lsp4j.PrepareRenameParams
import org.eclipse.lsp4j.PrepareRenameResult
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.ReferenceParams
import org.eclipse.lsp4j.RenameFilesParams
import org.eclipse.lsp4j.RenameParams
import org.eclipse.lsp4j.SelectionRange
import org.eclipse.lsp4j.SelectionRangeParams
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensDelta
import org.eclipse.lsp4j.SemanticTokensDeltaParams
import org.eclipse.lsp4j.SemanticTokensParams
import org.eclipse.lsp4j.SemanticTokensRangeParams
import org.eclipse.lsp4j.ServerCapabilities
import org.eclipse.lsp4j.SetTraceParams
import org.eclipse.lsp4j.SignatureHelp
import org.eclipse.lsp4j.SignatureHelpParams
import org.eclipse.lsp4j.SymbolInformation
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.TypeDefinitionParams
import org.eclipse.lsp4j.TypeHierarchyItem
import org.eclipse.lsp4j.TypeHierarchyPrepareParams
import org.eclipse.lsp4j.TypeHierarchySubtypesParams
import org.eclipse.lsp4j.TypeHierarchySupertypesParams
import org.eclipse.lsp4j.WillSaveTextDocumentParams
import org.eclipse.lsp4j.WorkspaceDiagnosticParams
import org.eclipse.lsp4j.WorkspaceDiagnosticReport
import org.eclipse.lsp4j.WorkspaceEdit
import org.eclipse.lsp4j.WorkspaceSymbol
import org.eclipse.lsp4j.WorkspaceSymbolParams
import org.eclipse.lsp4j.jsonrpc.messages.Either
import org.eclipse.lsp4j.jsonrpc.messages.Either3
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware
import org.eclipse.lsp4j.services.LanguageServer
import org.eclipse.lsp4j.services.TextDocumentService
import org.eclipse.lsp4j.services.WorkspaceService
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletableFuture.completedFuture
import kotlin.system.exitProcess

interface SystemExitHandler {
    fun exit(status: Int)
}

class DefaultSystemExitHandler : SystemExitHandler {
    override fun exit(status: Int) = exitProcess(status)
}

private val logger = KotlinLogging.logger { }

class LanguageServer(
    private val clientProvider: LanguageClientProvider = LanguageClientProvider(),
    private val compilerServiceFactory: (LanguageClientProvider) -> CompilerService = ::DefaultCompilerService,
    private val systemExitHandler: SystemExitHandler = DefaultSystemExitHandler(),
    private val gson: Gson = Gson(),
) : LanguageServer,
    LanguageClientAware {
    private val compilerService by lazy { compilerServiceFactory(clientProvider) }
    private lateinit var scheduler: LspScheduler

    private val textDocumentService = TextDocumentHandler()
    private val workspaceService = WorkspaceHandler()

    override fun initialize(params: InitializeParams): CompletableFuture<InitializeResult> {
        val capabilities =
            ServerCapabilities().apply {
                textDocumentSync = Either.forLeft(TextDocumentSyncKind.Full)
            }

        val standardWorkspaceFolderUris =
            params.workspaceFolders
                ?.filterNotNull()
                ?.mapNotNull { it.uri }

        val initParams: InitializationOptions = extractInitializeOptions(params.initializationOptions)
        scheduler = LspScheduler(initParams.nplServerDebouncingTimeMs.toLong())

        val nplRootUris =
            WorkspaceFolderExtractor
                .extractUrisFromInitializationOptions(
                    initParams,
                ).takeIf { it.isNotEmpty() } ?: standardWorkspaceFolderUris ?: emptyList()

        if (nplRootUris.isNotEmpty()) {
            logger.info("Preloading sources for workspace folders: $nplRootUris")
            val nplContribLibs = initParams.nplContribLibraries ?: emptyList()
            preloadSources(nplRootUris, nplContribLibs)
        } else {
            logger.warn("No workspace folders found to preload.")
        }

        return completedFuture(InitializeResult(capabilities))
    }

    private fun extractInitializeOptions(options: Any?): InitializationOptions {
        val defaultOptions = InitializationOptions(null)
        if (options == null || options !is JsonObject) {
            return defaultOptions
        }

        try {
            val initOptions = gson.fromJson(options, InitializationOptions::class.java)
            return initOptions
        } catch (e: Exception) {
            logger.warn(e) { "Error parsing init params" }
            return defaultOptions
        }
    }

    private fun preloadSources(
        nplRootUris: List<String>,
        nplContribLibs: List<String> = emptyList(),
    ) {
        compilerService.preloadSources(nplRootUris, nplContribLibs)
    }

    override fun shutdown(): CompletableFuture<Any> {
        scheduler.shutdown()
        return completedFuture(null)
    }

    override fun exit() {
        systemExitHandler.exit(0)
    }

    override fun setTrace(params: SetTraceParams) {
        // no-op for now (we get an annoying exception if we don't implement this)
    }

    override fun getTextDocumentService(): TextDocumentService = textDocumentService

    override fun getWorkspaceService(): WorkspaceService = workspaceService

    override fun connect(client: LanguageClient?) {
        clientProvider.client = client
    }

    inner class TextDocumentHandler : TextDocumentService {
        // ─── Already implemented ───────────────────────────────────────────────

        /** [LSP: textDocument/didOpen](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_didOpen) */
        override fun didOpen(params: DidOpenTextDocumentParams) {
            compilerService.updateSource(params.textDocument.uri, params.textDocument.text)
        }

        /** [LSP: textDocument/didChange](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_didChange) */
        override fun didChange(params: DidChangeTextDocumentParams) {
            params.contentChanges.forEach { change ->
                scheduler.submit(params.textDocument.uri) {
                    compilerService.updateSource(params.textDocument.uri, change.text)
                }
            }
        }

        /** [LSP: textDocument/didClose](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_didClose) */
        override fun didClose(params: DidCloseTextDocumentParams?) {
            params?.let {
                val uri = it.textDocument.uri
                // Only need to take action if the file no longer exists on disk
                val path =
                    java.nio.file.Paths
                        .get(java.net.URI.create(uri))
                if (!java.nio.file.Files
                        .exists(path)
                ) {
                    compilerService.removeSource(uri)
                }
            }
        }

        /** [LSP: textDocument/didSave](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_didSave) */
        override fun didSave(params: DidSaveTextDocumentParams?) { // no-op -- compilation occurs on change
        }

        /** [LSP: textDocument/willSave](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_willSave) */
        override fun willSave(params: WillSaveTextDocumentParams) {
            // TODO: not yet implemented
        }

        // ─── Intelligence ─────────────────────────────────────────────────────

        /** [LSP: textDocument/completion](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_completion) */
        override fun completion(params: CompletionParams): CompletableFuture<Either<MutableList<CompletionItem>, CompletionList>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: completionItem/resolve](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#completionItem_resolve) */
        override fun resolveCompletionItem(unresolved: CompletionItem): CompletableFuture<CompletionItem> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: textDocument/hover](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_hover) */
        override fun hover(params: HoverParams): CompletableFuture<Hover> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: textDocument/signatureHelp](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_signatureHelp) */
        override fun signatureHelp(params: SignatureHelpParams): CompletableFuture<SignatureHelp> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: textDocument/willSaveWaitUntil](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_willSaveWaitUntil) */
        override fun willSaveWaitUntil(params: WillSaveTextDocumentParams): CompletableFuture<MutableList<TextEdit>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: textDocument/inlayHint](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_inlayHint) */
        override fun inlayHint(params: InlayHintParams): CompletableFuture<MutableList<InlayHint>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: inlayHint/resolve](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#inlayHint_resolve) */
        override fun resolveInlayHint(unresolved: InlayHint): CompletableFuture<InlayHint> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: textDocument/inlineValue](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_inlineValue) */
        override fun inlineValue(params: InlineValueParams): CompletableFuture<MutableList<InlineValue>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: textDocument/moniker](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_moniker) */
        override fun moniker(params: MonikerParams): CompletableFuture<MutableList<Moniker>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        // ─── Navigation ───────────────────────────────────────────────────────

        /** [LSP: textDocument/declaration](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_declaration) */
        override fun declaration(params: DeclarationParams): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: textDocument/definition](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_definition) */
        override fun definition(params: DefinitionParams): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: textDocument/typeDefinition](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_typeDefinition) */
        override fun typeDefinition(params: TypeDefinitionParams): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: textDocument/implementation](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_implementation) */
        override fun implementation(params: ImplementationParams): CompletableFuture<Either<MutableList<out Location>, MutableList<out LocationLink>>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: textDocument/references](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_references) */
        override fun references(params: ReferenceParams): CompletableFuture<MutableList<out Location>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        // ─── Symbols & Highlights ─────────────────────────────────────────────

        /** [LSP: textDocument/documentHighlight](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentHighlight) */
        override fun documentHighlight(params: DocumentHighlightParams): CompletableFuture<MutableList<out DocumentHighlight>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: textDocument/documentSymbol](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentSymbol) */
        override fun documentSymbol(params: DocumentSymbolParams): CompletableFuture<MutableList<Either<SymbolInformation, DocumentSymbol>>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        // ─── Code Actions & Lens ──────────────────────────────────────────────

        /** [LSP: textDocument/codeAction](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_codeAction) */
        override fun codeAction(params: CodeActionParams): CompletableFuture<MutableList<Either<Command, CodeAction>>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: codeAction/resolve](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#codeAction_resolve) */
        override fun resolveCodeAction(unresolved: CodeAction): CompletableFuture<CodeAction> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: textDocument/codeLens](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_codeLens) */
        override fun codeLens(params: CodeLensParams): CompletableFuture<MutableList<out CodeLens>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: codeLens/resolve](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#codeLens_resolve) */
        override fun resolveCodeLens(unresolved: CodeLens): CompletableFuture<CodeLens> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        // ─── Formatting ───────────────────────────────────────────────────────

        /** [LSP: textDocument/formatting](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_formatting) */
        override fun formatting(params: DocumentFormattingParams): CompletableFuture<MutableList<out TextEdit>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: textDocument/rangeFormatting](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_rangeFormatting) */
        override fun rangeFormatting(params: DocumentRangeFormattingParams): CompletableFuture<MutableList<out TextEdit>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: textDocument/onTypeFormatting](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_onTypeFormatting) */
        override fun onTypeFormatting(params: DocumentOnTypeFormattingParams): CompletableFuture<MutableList<out TextEdit>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        // ─── Rename ───────────────────────────────────────────────────────────

        /** [LSP: textDocument/rename](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_rename) */
        override fun rename(params: RenameParams): CompletableFuture<WorkspaceEdit> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: textDocument/prepareRename](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_prepareRename) */
        override fun prepareRename(params: PrepareRenameParams): CompletableFuture<Either3<Range, PrepareRenameResult, PrepareRenameDefaultBehavior>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        // ─── Document Extras ──────────────────────────────────────────────────

        /** [LSP: textDocument/linkedEditingRange](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_linkedEditingRange) */
        override fun linkedEditingRange(params: LinkedEditingRangeParams): CompletableFuture<LinkedEditingRanges> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: textDocument/documentLink](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentLink) */
        override fun documentLink(params: DocumentLinkParams): CompletableFuture<MutableList<DocumentLink>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: documentLink/resolve](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#documentLink_resolve) */
        override fun documentLinkResolve(params: DocumentLink): CompletableFuture<DocumentLink> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: textDocument/documentColor](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_documentColor) */
        override fun documentColor(params: DocumentColorParams): CompletableFuture<MutableList<ColorInformation>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: textDocument/colorPresentation](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_colorPresentation) */
        override fun colorPresentation(params: ColorPresentationParams): CompletableFuture<MutableList<ColorPresentation>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: textDocument/foldingRange](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_foldingRange) */
        override fun foldingRange(params: FoldingRangeRequestParams): CompletableFuture<MutableList<FoldingRange>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: textDocument/selectionRange](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_selectionRange) */
        override fun selectionRange(params: SelectionRangeParams): CompletableFuture<MutableList<SelectionRange>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        // ─── Semantic Tokens ──────────────────────────────────────────────────

        /** [LSP: textDocument/semanticTokens/full](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#semanticTokens_fullRequest) */
        override fun semanticTokensFull(params: SemanticTokensParams): CompletableFuture<SemanticTokens> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: textDocument/semanticTokens/full/delta](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#semanticTokens_deltaRequest) */
        override fun semanticTokensFullDelta(params: SemanticTokensDeltaParams): CompletableFuture<Either<SemanticTokens, SemanticTokensDelta>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: textDocument/semanticTokens/range](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#semanticTokens_rangeRequest) */
        override fun semanticTokensRange(params: SemanticTokensRangeParams): CompletableFuture<SemanticTokens> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        // ─── Hierarchies ──────────────────────────────────────────────────────

        /** [LSP: textDocument/prepareCallHierarchy](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_prepareCallHierarchy) */
        override fun prepareCallHierarchy(params: CallHierarchyPrepareParams): CompletableFuture<MutableList<CallHierarchyItem>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: callHierarchy/incomingCalls](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#callHierarchy_incomingCalls) */
        override fun callHierarchyIncomingCalls(params: CallHierarchyIncomingCallsParams): CompletableFuture<MutableList<CallHierarchyIncomingCall>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: callHierarchy/outgoingCalls](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#callHierarchy_outgoingCalls) */
        override fun callHierarchyOutgoingCalls(params: CallHierarchyOutgoingCallsParams): CompletableFuture<MutableList<CallHierarchyOutgoingCall>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: textDocument/prepareTypeHierarchy](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_prepareTypeHierarchy) */
        override fun prepareTypeHierarchy(params: TypeHierarchyPrepareParams): CompletableFuture<MutableList<TypeHierarchyItem>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: typeHierarchy/supertypes](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#typeHierarchy_supertypes) */
        override fun typeHierarchySupertypes(params: TypeHierarchySupertypesParams): CompletableFuture<MutableList<TypeHierarchyItem>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: typeHierarchy/subtypes](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#typeHierarchy_subtypes) */
        override fun typeHierarchySubtypes(params: TypeHierarchySubtypesParams): CompletableFuture<MutableList<TypeHierarchyItem>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        // ─── Diagnostics (pull model) ──────────────────────────────────────────

        /** [LSP: textDocument/diagnostic](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_pullDiagnostics) */
        override fun diagnostic(params: DocumentDiagnosticParams): CompletableFuture<DocumentDiagnosticReport> {
            // TODO: not yet implemented
            return completedFuture(null)
        }
    }

    inner class WorkspaceHandler : WorkspaceService {
        // ─── Already implemented ───────────────────────────────────────────────

        /** [LSP: workspace/didChangeConfiguration](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_didChangeConfiguration) */
        override fun didChangeConfiguration(params: DidChangeConfigurationParams?) {}

        /** [LSP: workspace/didChangeWatchedFiles](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_didChangeWatchedFiles) */
        override fun didChangeWatchedFiles(params: DidChangeWatchedFilesParams?) {
            params?.changes?.forEach { change ->
                if (change.type == FileChangeType.Deleted) {
                    compilerService.removeSource(change.uri)
                }
            }
        }

        /** [LSP: workspace/didDeleteFiles](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_willDeleteFiles) */
        override fun didDeleteFiles(params: DeleteFilesParams?) {
            params?.files?.forEach { file ->
                compilerService.removeSource(file.uri)
            }
        }

        // ─── Symbols ──────────────────────────────────────────────────────────

        /** [LSP: workspace/symbol](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_symbol) */
        override fun symbol(params: WorkspaceSymbolParams): CompletableFuture<Either<MutableList<out SymbolInformation>, MutableList<out WorkspaceSymbol>>> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: workspaceSymbol/resolve](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_symbolResolve) */
        override fun resolveWorkspaceSymbol(workspaceSymbol: WorkspaceSymbol): CompletableFuture<WorkspaceSymbol> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        // ─── Commands ─────────────────────────────────────────────────────────

        /** [LSP: workspace/executeCommand](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_executeCommand) */
        override fun executeCommand(params: ExecuteCommandParams): CompletableFuture<Any> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        // ─── Workspace folder changes ──────────────────────────────────────────

        /** [LSP: workspace/didChangeWorkspaceFolders](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_didChangeWorkspaceFolders) */
        override fun didChangeWorkspaceFolders(params: DidChangeWorkspaceFoldersParams) {
            // TODO: not yet implemented
        }

        // ─── File operations ──────────────────────────────────────────────────

        /** [LSP: workspace/willCreateFiles](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_willCreateFiles) */
        override fun willCreateFiles(params: CreateFilesParams): CompletableFuture<WorkspaceEdit> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: workspace/didCreateFiles](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_didCreateFiles) */
        override fun didCreateFiles(params: CreateFilesParams) {
            // TODO: not yet implemented
        }

        /** [LSP: workspace/willRenameFiles](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_willRenameFiles) */
        override fun willRenameFiles(params: RenameFilesParams): CompletableFuture<WorkspaceEdit> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        /** [LSP: workspace/didRenameFiles](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_didRenameFiles) */
        override fun didRenameFiles(params: RenameFilesParams) {
            // TODO: not yet implemented
        }

        /** [LSP: workspace/willDeleteFiles](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_willDeleteFiles) */
        override fun willDeleteFiles(params: DeleteFilesParams): CompletableFuture<WorkspaceEdit> {
            // TODO: not yet implemented
            return completedFuture(null)
        }

        // ─── Diagnostics (pull model) ──────────────────────────────────────────

        /** [LSP: workspace/diagnostic](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspace_diagnostic) */
        override fun diagnostic(params: WorkspaceDiagnosticParams): CompletableFuture<WorkspaceDiagnosticReport> {
            // TODO: not yet implemented
            return completedFuture(null)
        }
    }
}

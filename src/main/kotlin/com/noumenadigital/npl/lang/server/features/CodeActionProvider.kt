package com.noumenadigital.npl.lang.server.features

import com.noumenadigital.npl.grammar.NplParser
import com.noumenadigital.npl.lang.server.ast.ParsedFile
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.eclipse.lsp4j.CodeAction
import org.eclipse.lsp4j.CodeActionKind
import org.eclipse.lsp4j.CodeActionParams
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit

/**
 * Provides code actions (quick fixes and refactorings) for NPL files.
 *
 * Supported actions:
 * - Quick fixes for diagnostics (unused imports, missing types, etc.)
 * - Source actions (organize imports, generate functions)
 * - Refactoring actions (extract variable, extract function)
 */
object CodeActionProvider {

    /**
     * Get code actions for the given context (range, diagnostics).
     */
    fun getCodeActions(
        parsedFile: ParsedFile,
        params: CodeActionParams,
        allParsedFiles: Map<String, ParsedFile>,
    ): List<CodeAction> {
        val actions = mutableListOf<CodeAction>()

        // Add quick fixes based on diagnostics
        params.context.diagnostics.forEach { diagnostic ->
            actions.addAll(getQuickFixesForDiagnostic(parsedFile, diagnostic, allParsedFiles))
        }

        // Add source actions (available regardless of diagnostics)
        actions.addAll(getSourceActions(parsedFile, params.range, allParsedFiles))

        // Add refactoring actions based on selection
        actions.addAll(getRefactoringActions(parsedFile, params.range, allParsedFiles))

        return actions
    }

    /**
     * Generate quick fixes for a specific diagnostic.
     */
    private fun getQuickFixesForDiagnostic(
        parsedFile: ParsedFile,
        diagnostic: Diagnostic,
        allParsedFiles: Map<String, ParsedFile>,
    ): List<CodeAction> {
        val actions = mutableListOf<CodeAction>()

        // Handle different error codes
        when (diagnostic.code?.left) {
            // Error code for type resolution issues
            "89" -> {
                // Suggest adding explicit type annotation
                actions.add(createAddExplicitTypeAction(parsedFile, diagnostic))
            }
        }

        // Check for common patterns in diagnostic messages
        val message = diagnostic.message

        // Handle "Unknown identifier" errors - suggest importing
        if (message.contains("Unknown identifier") || message.contains("cannot be resolved") || message.contains("not found")) {
            val identifier = extractIdentifierFromRange(parsedFile, diagnostic.range)
            if (identifier != null) {
                val importActions = suggestImportActions(identifier, parsedFile, allParsedFiles)
                actions.addAll(importActions)
            }
        }

        // Handle "unused" warnings - suggest removal
        if (message.contains("unused", ignoreCase = true) || message.contains("never used", ignoreCase = true)) {
            actions.add(createRemoveUnusedAction(parsedFile, diagnostic))
        }

        return actions.filter { it.edit != null || it.command != null }
    }

    /**
     * Generate source actions (organize imports, generate code, etc.)
     */
    private fun getSourceActions(
        parsedFile: ParsedFile,
        range: Range,
        allParsedFiles: Map<String, ParsedFile>,
    ): List<CodeAction> {
        val actions = mutableListOf<CodeAction>()

        // Organize imports action
        actions.add(createOrganizeImportsAction(parsedFile))

        // Generate constructor/init block for protocol
        val node = parsedFile.findNodeAt(range.start)
        if (node != null) {
            val protocolDecl = findParentOfType<NplParser.ProtocolDeclContext>(node)
            if (protocolDecl != null && !hasInitBlock(protocolDecl)) {
                actions.add(createGenerateInitAction(parsedFile, protocolDecl))
            }

            // Generate toString for struct
            val structDecl = findParentOfType<NplParser.StructDeclContext>(node)
            if (structDecl != null) {
                actions.add(createGenerateToStringAction(parsedFile, structDecl))
            }
        }

        return actions.filter { it.edit != null || it.command != null }
    }

    /**
     * Generate refactoring actions based on selection.
     */
    private fun getRefactoringActions(
        parsedFile: ParsedFile,
        range: Range,
        allParsedFiles: Map<String, ParsedFile>,
    ): List<CodeAction> {
        val actions = mutableListOf<CodeAction>()

        val node = parsedFile.findNodeAt(range.start)
        if (node != null) {
            // Extract variable refactoring
            val exprContext = findParentOfType<NplParser.ExprContext>(node)
            if (exprContext != null && isNonTrivialExpression(exprContext)) {
                actions.add(createExtractVariableAction(parsedFile, exprContext))
            }

            // Convert permission to obligation and vice versa
            val permissionDecl = findParentOfType<NplParser.PermissionDeclContext>(node)
            if (permissionDecl != null) {
                actions.add(createConvertToObligationAction(parsedFile, permissionDecl))
            }

            val obligationDecl = findParentOfType<NplParser.ObligationDeclContext>(node)
            if (obligationDecl != null) {
                actions.add(createConvertToPermissionAction(parsedFile, obligationDecl))
            }
        }

        return actions.filter { it.edit != null || it.command != null }
    }

    /**
     * Create an action to add explicit type annotation.
     */
    private fun createAddExplicitTypeAction(
        parsedFile: ParsedFile,
        diagnostic: Diagnostic,
    ): CodeAction {
        return CodeAction().apply {
            title = "Add explicit type annotation"
            kind = CodeActionKind.QuickFix
            diagnostics = listOf(diagnostic)
            isPreferred = true
            // Note: Full implementation would analyze the expression to determine the type
            // For now, this creates a placeholder that the user can fill in
        }
    }

    /**
     * Create action to remove unused declaration.
     */
    private fun createRemoveUnusedAction(
        parsedFile: ParsedFile,
        diagnostic: Diagnostic,
    ): CodeAction {
        val range = diagnostic.range

        // Extend range to include the full line if it's on a single line
        val startLine = range.start.line
        val endLine = range.end.line

        val fullLineRange = if (startLine == endLine) {
            Range(
                Position(startLine, 0),
                Position(startLine + 1, 0)
            )
        } else {
            Range(
                Position(startLine, 0),
                Position(endLine + 1, 0)
            )
        }

        return CodeAction().apply {
            title = "Remove unused declaration"
            kind = CodeActionKind.QuickFix
            diagnostics = listOf(diagnostic)
            edit = WorkspaceEdit(mapOf(
                parsedFile.uri to listOf(TextEdit(fullLineRange, ""))
            ))
        }
    }

    /**
     * Suggest import actions for an unknown identifier.
     */
    private fun suggestImportActions(
        identifier: String,
        parsedFile: ParsedFile,
        allParsedFiles: Map<String, ParsedFile>,
    ): List<CodeAction> {
        val actions = mutableListOf<CodeAction>()

        // Search for matching symbols in other files
        for ((uri, file) in allParsedFiles) {
            if (uri == parsedFile.uri) continue

            val symbols = findExportedSymbols(file)
            val matchingSymbol = symbols.find { it.name == identifier }

            if (matchingSymbol != null) {
                val packageName = file.getPackageName() ?: continue
                val importStatement = "use $packageName.$identifier"

                val insertPosition = findImportInsertPosition(parsedFile)

                actions.add(CodeAction().apply {
                    title = "Import '$identifier' from $packageName"
                    kind = CodeActionKind.QuickFix
                    isPreferred = actions.isEmpty() // First suggestion is preferred
                    edit = WorkspaceEdit(mapOf(
                        parsedFile.uri to listOf(TextEdit(
                            Range(insertPosition, insertPosition),
                            "$importStatement\n"
                        ))
                    ))
                })
            }
        }

        return actions
    }

    /**
     * Create action to organize imports.
     */
    private fun createOrganizeImportsAction(parsedFile: ParsedFile): CodeAction {
        val tree = parsedFile.tree ?: return CodeAction().apply { title = "Organize imports" }

        // Collect all import statements from root
        val imports = mutableListOf<Pair<Range, String>>()
        tree.useStmt().forEach { useStmt ->
            val range = parsedFile.tokenToRange(useStmt)
            val text = useStmt.qualifiedName()?.text ?: return@forEach
            imports.add(range to text)
        }

        if (imports.isEmpty()) {
            return CodeAction().apply {
                title = "Organize imports"
                kind = CodeActionKind.SourceOrganizeImports
            }
        }

        // Sort imports alphabetically
        val sortedImports = imports.sortedBy { it.second }

        // Check if already organized
        if (imports.map { it.second } == sortedImports.map { it.second }) {
            return CodeAction().apply {
                title = "Organize imports"
                kind = CodeActionKind.SourceOrganizeImports
            }
        }

        // Create edits to reorganize
        val firstImportStart = imports.first().first.start
        val lastImportEnd = imports.last().first.end

        val newImportText = sortedImports.joinToString("\n") { "use ${it.second}" }

        return CodeAction().apply {
            title = "Organize imports"
            kind = CodeActionKind.SourceOrganizeImports
            edit = WorkspaceEdit(mapOf(
                parsedFile.uri to listOf(TextEdit(
                    Range(firstImportStart, lastImportEnd),
                    newImportText
                ))
            ))
        }
    }

    /**
     * Create action to generate init block for protocol.
     */
    private fun createGenerateInitAction(
        parsedFile: ParsedFile,
        protocolDecl: NplParser.ProtocolDeclContext,
    ): CodeAction {
        // Find the position to insert the init block (before the closing brace)
        val range = parsedFile.tokenToRange(protocolDecl)
        val insertLine = range.end.line
        val indent = "    "

        val initBlock = "\n${indent}init {\n$indent}\n"

        // Find insert position (just before closing brace)
        val insertPosition = Position(insertLine, 0)

        return CodeAction().apply {
            title = "Generate init block"
            kind = CodeActionKind.Source
            edit = WorkspaceEdit(mapOf(
                parsedFile.uri to listOf(TextEdit(
                    Range(insertPosition, insertPosition),
                    initBlock
                ))
            ))
        }
    }

    /**
     * Create action to generate toString function for struct.
     */
    private fun createGenerateToStringAction(
        parsedFile: ParsedFile,
        structDecl: NplParser.StructDeclContext,
    ): CodeAction {
        val structName = structDecl.structIdentifier()?.text ?: return CodeAction().apply {
            title = "Generate toString"
        }

        // Collect fields
        val fields = structDecl.typedIdentifiers()?.typedIdentifier()?.mapNotNull {
            it.variableIdentifier()?.text
        } ?: emptyList()

        // Generate toString function
        val fieldFormatting = fields.joinToString(" + \", \" + ") { "\"$it=\" + this.$it" }
        val toStringBody = if (fields.isEmpty()) {
            "\"$structName{}\""
        } else {
            "\"$structName{\" + $fieldFormatting + \"}\""
        }

        // Position after the struct declaration
        val range = parsedFile.tokenToRange(structDecl)
        val insertPosition = Position(range.end.line + 1, 0)

        val toStringFunction = "\nfunction $structName.toString() returns Text -> $toStringBody\n"

        return CodeAction().apply {
            title = "Generate toString function"
            kind = CodeActionKind.Source
            edit = WorkspaceEdit(mapOf(
                parsedFile.uri to listOf(TextEdit(
                    Range(insertPosition, insertPosition),
                    toStringFunction
                ))
            ))
        }
    }

    /**
     * Create action to extract an expression into a variable.
     */
    private fun createExtractVariableAction(
        parsedFile: ParsedFile,
        exprContext: NplParser.ExprContext,
    ): CodeAction {
        val exprRange = parsedFile.tokenToRange(exprContext)
        val exprText = extractTextFromRange(parsedFile, exprRange)

        // Generate a variable name based on context
        val varName = "extracted"  // Could be smarter about naming

        // Calculate indentation of the current line
        val lineText = parsedFile.lines.getOrNull(exprRange.start.line) ?: ""
        val indent = lineText.takeWhile { it.isWhitespace() }

        // Create the variable declaration
        val varDecl = "${indent}var $varName = $exprText;\n"

        // Insert position: start of the current line
        val insertPosition = Position(exprRange.start.line, 0)

        return CodeAction().apply {
            title = "Extract to variable"
            kind = CodeActionKind.RefactorExtract
            edit = WorkspaceEdit(mapOf(
                parsedFile.uri to listOf(
                    // Insert variable declaration
                    TextEdit(Range(insertPosition, insertPosition), varDecl),
                    // Replace expression with variable reference
                    TextEdit(exprRange, varName)
                )
            ))
        }
    }

    /**
     * Create action to convert permission to obligation.
     */
    private fun createConvertToObligationAction(
        parsedFile: ParsedFile,
        permissionDecl: NplParser.PermissionDeclContext,
    ): CodeAction {
        val range = parsedFile.tokenToRange(permissionDecl)
        val text = extractTextFromRange(parsedFile, range)
        val newText = text.replaceFirst("permission", "obligation")

        return CodeAction().apply {
            title = "Convert to obligation"
            kind = CodeActionKind.RefactorRewrite
            edit = WorkspaceEdit(mapOf(
                parsedFile.uri to listOf(TextEdit(range, newText))
            ))
        }
    }

    /**
     * Create action to convert obligation to permission.
     */
    private fun createConvertToPermissionAction(
        parsedFile: ParsedFile,
        obligationDecl: NplParser.ObligationDeclContext,
    ): CodeAction {
        val range = parsedFile.tokenToRange(obligationDecl)
        val text = extractTextFromRange(parsedFile, range)
        val newText = text.replaceFirst("obligation", "permission")

        return CodeAction().apply {
            title = "Convert to permission"
            kind = CodeActionKind.RefactorRewrite
            edit = WorkspaceEdit(mapOf(
                parsedFile.uri to listOf(TextEdit(range, newText))
            ))
        }
    }

    // ─── Helper functions ─────────────────────────────────────────────────────

    private inline fun <reified T : ParserRuleContext> findParentOfType(node: ParseTree): T? {
        var current: ParseTree? = node
        while (current != null) {
            if (current is T) return current
            current = (current as? ParserRuleContext)?.parent
        }
        return null
    }

    private fun hasInitBlock(protocolDecl: NplParser.ProtocolDeclContext): Boolean {
        return protocolDecl.initDecl() != null
    }

    private fun isNonTrivialExpression(expr: NplParser.ExprContext): Boolean {
        // Consider an expression non-trivial if it's not just a simple identifier or literal
        // Check for multiple children which indicates a complex expression
        return expr.childCount > 1
    }

    private fun extractIdentifierFromRange(parsedFile: ParsedFile, range: Range): String? {
        return extractTextFromRange(parsedFile, range).takeIf { it.isNotBlank() && it.all { c -> c.isLetterOrDigit() || c == '_' } }
    }

    private fun extractTextFromRange(parsedFile: ParsedFile, range: Range): String {
        val lines = parsedFile.lines
        return if (range.start.line == range.end.line) {
            lines.getOrNull(range.start.line)
                ?.substring(range.start.character, minOf(range.end.character, lines[range.start.line].length))
                ?: ""
        } else {
            val result = StringBuilder()
            for (line in range.start.line..range.end.line) {
                val lineText = lines.getOrNull(line) ?: continue
                when (line) {
                    range.start.line -> result.append(lineText.substring(range.start.character))
                    range.end.line -> result.append(lineText.substring(0, minOf(range.end.character, lineText.length)))
                    else -> result.append(lineText)
                }
                if (line < range.end.line) result.append("\n")
            }
            result.toString()
        }
    }

    /**
     * Find symbols that can be exported from a file.
     */
    private fun findExportedSymbols(parsedFile: ParsedFile): List<SymbolInfo> {
        val tree = parsedFile.tree ?: return emptyList()
        val symbols = mutableListOf<SymbolInfo>()

        tree.statement().forEach { stmt ->
            stmt.functionDecl()?.let { func ->
                func.functionIdentifier()?.text?.let { name ->
                    symbols.add(SymbolInfo(name, SymbolKind.FUNCTION))
                }
            }
            stmt.structDecl()?.let { struct ->
                struct.structIdentifier()?.text?.let { name ->
                    symbols.add(SymbolInfo(name, SymbolKind.STRUCT))
                }
            }
            stmt.unionDecl()?.let { union ->
                union.unionIdentifier()?.text?.let { name ->
                    symbols.add(SymbolInfo(name, SymbolKind.UNION))
                }
            }
            stmt.enumDecl()?.let { enum ->
                enum.enumIdentifier()?.text?.let { name ->
                    symbols.add(SymbolInfo(name, SymbolKind.ENUM))
                }
            }
            stmt.protocolDecl()?.let { protocol ->
                protocol.protocolIdentifier()?.text?.let { name ->
                    symbols.add(SymbolInfo(name, SymbolKind.PROTOCOL))
                }
            }
            stmt.notificationDecl()?.let { notification ->
                notification.notificationIdentifier()?.text?.let { name ->
                    symbols.add(SymbolInfo(name, SymbolKind.NOTIFICATION))
                }
            }
        }

        return symbols
    }

    /**
     * Find the position to insert new import statements.
     */
    private fun findImportInsertPosition(parsedFile: ParsedFile): Position {
        val tree = parsedFile.tree ?: return Position(1, 0)

        // Find the last import statement or package statement
        var lastImportLine = -1
        var packageLine = -1

        tree.packageStmt()?.let {
            packageLine = parsedFile.tokenToRange(it).end.line
        }

        tree.useStmt().forEach { useStmt ->
            val line = parsedFile.tokenToRange(useStmt).end.line
            if (line > lastImportLine) {
                lastImportLine = line
            }
        }

        return when {
            lastImportLine >= 0 -> Position(lastImportLine + 1, 0)
            packageLine >= 0 -> Position(packageLine + 1, 0)
            else -> Position(0, 0)
        }
    }

    private data class SymbolInfo(
        val name: String,
        val kind: SymbolKind,
    )

    private enum class SymbolKind {
        FUNCTION,
        STRUCT,
        UNION,
        ENUM,
        PROTOCOL,
        NOTIFICATION,
    }
}






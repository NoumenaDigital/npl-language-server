package com.noumenadigital.npl.lang.server.features

import com.noumenadigital.npl.grammar.NplParser
import com.noumenadigital.npl.lang.server.ast.ParsedFile
import org.eclipse.lsp4j.CodeLens
import org.eclipse.lsp4j.Command
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range

/**
 * Provides code lenses for NPL files.
 *
 * Code lenses show additional information above declarations, such as:
 * - Number of references to a symbol
 * - Number of implementations for protocols/unions
 * - Test status indicators
 *
 * Clicking a code lens can trigger actions like "Show References" or "Run Test".
 */
object CodeLensProvider {

    // Command identifiers for code lens actions
    const val CMD_SHOW_REFERENCES = "npl.showReferences"
    const val CMD_SHOW_IMPLEMENTATIONS = "npl.showImplementations"
    const val CMD_GO_TO_TEST = "npl.goToTest"
    const val CMD_RUN_TEST = "npl.runTest"

    /**
     * Get all code lenses for a file.
     */
    fun getCodeLenses(
        parsedFile: ParsedFile,
        allParsedFiles: Map<String, ParsedFile>,
    ): List<CodeLens> {
        val tree = parsedFile.tree ?: return emptyList()
        val lenses = mutableListOf<CodeLens>()

        tree.statement().forEach { statement ->
            // Functions
            statement.functionDecl()?.let { funcDecl ->
                lenses.addAll(getFunctionCodeLenses(parsedFile, funcDecl, allParsedFiles))
            }

            // Protocols
            statement.protocolDecl()?.let { protocolDecl ->
                lenses.addAll(getProtocolCodeLenses(parsedFile, protocolDecl, allParsedFiles))
            }

            // Structs
            statement.structDecl()?.let { structDecl ->
                lenses.addAll(getStructCodeLenses(parsedFile, structDecl, allParsedFiles))
            }

            // Unions
            statement.unionDecl()?.let { unionDecl ->
                lenses.addAll(getUnionCodeLenses(parsedFile, unionDecl, allParsedFiles))
            }

            // Enums
            statement.enumDecl()?.let { enumDecl ->
                lenses.addAll(getEnumCodeLenses(parsedFile, enumDecl, allParsedFiles))
            }

            // Notifications
            statement.notificationDecl()?.let { notificationDecl ->
                lenses.addAll(getNotificationCodeLenses(parsedFile, notificationDecl, allParsedFiles))
            }

            // Constants
            statement.constDecl()?.let { constDecl ->
                lenses.addAll(getConstCodeLenses(parsedFile, constDecl, allParsedFiles))
            }
        }

        return lenses
    }

    /**
     * Resolve a code lens by computing its command lazily.
     * This is called when the code lens becomes visible in the editor.
     */
    fun resolveCodeLens(
        codeLens: CodeLens,
        parsedFile: ParsedFile,
        allParsedFiles: Map<String, ParsedFile>,
    ): CodeLens {
        // If the code lens already has a command, it's fully resolved
        if (codeLens.command != null) return codeLens

        // Resolve based on data stored in the code lens
        val data = codeLens.data
        if (data is Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            val dataMap = data as Map<String, Any>
            val type = dataMap["type"] as? String
            val symbolName = dataMap["symbolName"] as? String

            when (type) {
                "references" -> {
                    if (symbolName != null) {
                        val count = countReferences(symbolName, allParsedFiles)
                        codeLens.command = Command(
                            "$count reference${if (count != 1) "s" else ""}",
                            CMD_SHOW_REFERENCES,
                            listOf(parsedFile.uri, codeLens.range.start.line, codeLens.range.start.character)
                        )
                    }
                }
                "implementations" -> {
                    if (symbolName != null) {
                        val count = countImplementations(symbolName, allParsedFiles)
                        codeLens.command = Command(
                            "$count implementation${if (count != 1) "s" else ""}",
                            CMD_SHOW_IMPLEMENTATIONS,
                            listOf(parsedFile.uri, codeLens.range.start.line, codeLens.range.start.character)
                        )
                    }
                }
            }
        }

        return codeLens
    }

    // ─── Function code lenses ─────────────────────────────────────────────────

    private fun getFunctionCodeLenses(
        parsedFile: ParsedFile,
        funcDecl: NplParser.FunctionDeclContext,
        allParsedFiles: Map<String, ParsedFile>,
    ): List<CodeLens> {
        val lenses = mutableListOf<CodeLens>()
        val funcName = funcDecl.functionIdentifier()?.text ?: return lenses
        val range = parsedFile.tokenToRange(funcDecl.functionIdentifier()!!)

        // References lens
        val refCount = countReferences(funcName, allParsedFiles) - 1 // Exclude declaration
        if (refCount >= 0) {
            lenses.add(CodeLens(
                Range(Position(range.start.line, 0), Position(range.start.line, 0)),
                Command(
                    "$refCount reference${if (refCount != 1) "s" else ""}",
                    CMD_SHOW_REFERENCES,
                    listOf(parsedFile.uri, range.start.line, range.start.character)
                ),
                null
            ))
        }

        return lenses
    }

    // ─── Protocol code lenses ─────────────────────────────────────────────────

    private fun getProtocolCodeLenses(
        parsedFile: ParsedFile,
        protocolDecl: NplParser.ProtocolDeclContext,
        allParsedFiles: Map<String, ParsedFile>,
    ): List<CodeLens> {
        val lenses = mutableListOf<CodeLens>()
        val protocolName = protocolDecl.protocolIdentifier()?.text ?: return lenses
        val range = parsedFile.tokenToRange(protocolDecl.protocolIdentifier()!!)
        val lensLine = range.start.line

        // References lens
        val refCount = countReferences(protocolName, allParsedFiles) - 1
        if (refCount >= 0) {
            lenses.add(CodeLens(
                Range(Position(lensLine, 0), Position(lensLine, 0)),
                Command(
                    "$refCount reference${if (refCount != 1) "s" else ""}",
                    CMD_SHOW_REFERENCES,
                    listOf(parsedFile.uri, range.start.line, range.start.character)
                ),
                null
            ))
        }

        // States count lens
        val stateCount = protocolDecl.stateDecl().size
        if (stateCount > 0) {
            lenses.add(CodeLens(
                Range(Position(lensLine, 0), Position(lensLine, 0)),
                Command(
                    "$stateCount state${if (stateCount != 1) "s" else ""}",
                    CMD_SHOW_IMPLEMENTATIONS,
                    listOf(parsedFile.uri, range.start.line, range.start.character)
                ),
                null
            ))
        }

        // Actions count lens
        val actionCount = protocolDecl.actionDecl().size
        if (actionCount > 0) {
            lenses.add(CodeLens(
                Range(Position(lensLine, 0), Position(lensLine, 0)),
                Command(
                    "$actionCount action${if (actionCount != 1) "s" else ""}",
                    CMD_SHOW_IMPLEMENTATIONS,
                    listOf(parsedFile.uri, range.start.line, range.start.character)
                ),
                null
            ))
        }

        // Code lenses for permissions and obligations within the protocol
        protocolDecl.actionDecl().forEach { actionDecl ->
            actionDecl.permissionDecl()?.let { permDecl ->
                val actionName = permDecl.actionHead()?.actionIdentifier()?.text ?: return@let
                val actionRange = parsedFile.tokenToRange(permDecl.actionHead()!!.actionIdentifier()!!)
                val actionRefCount = countReferences(actionName, allParsedFiles) - 1
                if (actionRefCount >= 0) {
                    lenses.add(CodeLens(
                        Range(Position(actionRange.start.line, 0), Position(actionRange.start.line, 0)),
                        Command(
                            "$actionRefCount reference${if (actionRefCount != 1) "s" else ""}",
                            CMD_SHOW_REFERENCES,
                            listOf(parsedFile.uri, actionRange.start.line, actionRange.start.character)
                        ),
                        null
                    ))
                }
            }
            actionDecl.obligationDecl()?.let { oblDecl ->
                val actionName = oblDecl.actionHead()?.actionIdentifier()?.text ?: return@let
                val actionRange = parsedFile.tokenToRange(oblDecl.actionHead()!!.actionIdentifier()!!)
                val actionRefCount = countReferences(actionName, allParsedFiles) - 1
                if (actionRefCount >= 0) {
                    lenses.add(CodeLens(
                        Range(Position(actionRange.start.line, 0), Position(actionRange.start.line, 0)),
                        Command(
                            "$actionRefCount reference${if (actionRefCount != 1) "s" else ""}",
                            CMD_SHOW_REFERENCES,
                            listOf(parsedFile.uri, actionRange.start.line, actionRange.start.character)
                        ),
                        null
                    ))
                }
            }
        }

        return lenses
    }

    // ─── Struct code lenses ───────────────────────────────────────────────────

    private fun getStructCodeLenses(
        parsedFile: ParsedFile,
        structDecl: NplParser.StructDeclContext,
        allParsedFiles: Map<String, ParsedFile>,
    ): List<CodeLens> {
        val lenses = mutableListOf<CodeLens>()
        val structName = structDecl.structIdentifier()?.text ?: return lenses
        val range = parsedFile.tokenToRange(structDecl.structIdentifier()!!)

        // References lens
        val refCount = countReferences(structName, allParsedFiles) - 1
        if (refCount >= 0) {
            lenses.add(CodeLens(
                Range(Position(range.start.line, 0), Position(range.start.line, 0)),
                Command(
                    "$refCount reference${if (refCount != 1) "s" else ""}",
                    CMD_SHOW_REFERENCES,
                    listOf(parsedFile.uri, range.start.line, range.start.character)
                ),
                null
            ))
        }

        // Field count lens
        val fieldCount = structDecl.typedIdentifiers()?.typedIdentifier()?.size ?: 0
        if (fieldCount > 0) {
            lenses.add(CodeLens(
                Range(Position(range.start.line, 0), Position(range.start.line, 0)),
                Command(
                    "$fieldCount field${if (fieldCount != 1) "s" else ""}",
                    null,  // No command, just informational
                    null
                ),
                null
            ))
        }

        return lenses
    }

    // ─── Union code lenses ────────────────────────────────────────────────────

    private fun getUnionCodeLenses(
        parsedFile: ParsedFile,
        unionDecl: NplParser.UnionDeclContext,
        allParsedFiles: Map<String, ParsedFile>,
    ): List<CodeLens> {
        val lenses = mutableListOf<CodeLens>()
        val unionName = unionDecl.unionIdentifier()?.text ?: return lenses
        val range = parsedFile.tokenToRange(unionDecl.unionIdentifier()!!)

        // References lens
        val refCount = countReferences(unionName, allParsedFiles) - 1
        if (refCount >= 0) {
            lenses.add(CodeLens(
                Range(Position(range.start.line, 0), Position(range.start.line, 0)),
                Command(
                    "$refCount reference${if (refCount != 1) "s" else ""}",
                    CMD_SHOW_REFERENCES,
                    listOf(parsedFile.uri, range.start.line, range.start.character)
                ),
                null
            ))
        }

        // Variant count lens (using typeExprList for union variants)
        val variantCount = unionDecl.typeExprList()?.typeExpr()?.size ?: 0
        if (variantCount > 0) {
            lenses.add(CodeLens(
                Range(Position(range.start.line, 0), Position(range.start.line, 0)),
                Command(
                    "$variantCount variant${if (variantCount != 1) "s" else ""}",
                    CMD_SHOW_IMPLEMENTATIONS,
                    listOf(parsedFile.uri, range.start.line, range.start.character)
                ),
                null
            ))
        }

        return lenses
    }

    // ─── Enum code lenses ─────────────────────────────────────────────────────

    private fun getEnumCodeLenses(
        parsedFile: ParsedFile,
        enumDecl: NplParser.EnumDeclContext,
        allParsedFiles: Map<String, ParsedFile>,
    ): List<CodeLens> {
        val lenses = mutableListOf<CodeLens>()
        val enumName = enumDecl.enumIdentifier()?.text ?: return lenses
        val range = parsedFile.tokenToRange(enumDecl.enumIdentifier()!!)

        // References lens
        val refCount = countReferences(enumName, allParsedFiles) - 1
        if (refCount >= 0) {
            lenses.add(CodeLens(
                Range(Position(range.start.line, 0), Position(range.start.line, 0)),
                Command(
                    "$refCount reference${if (refCount != 1) "s" else ""}",
                    CMD_SHOW_REFERENCES,
                    listOf(parsedFile.uri, range.start.line, range.start.character)
                ),
                null
            ))
        }

        // Variants count lens
        val variantCount = enumDecl.enumVariantIdentifiers()?.enumVariantIdentifier()?.size ?: 0
        if (variantCount > 0) {
            lenses.add(CodeLens(
                Range(Position(range.start.line, 0), Position(range.start.line, 0)),
                Command(
                    "$variantCount value${if (variantCount != 1) "s" else ""}",
                    null,
                    null
                ),
                null
            ))
        }

        return lenses
    }

    // ─── Notification code lenses ─────────────────────────────────────────────

    private fun getNotificationCodeLenses(
        parsedFile: ParsedFile,
        notificationDecl: NplParser.NotificationDeclContext,
        allParsedFiles: Map<String, ParsedFile>,
    ): List<CodeLens> {
        val lenses = mutableListOf<CodeLens>()
        val notificationName = notificationDecl.notificationIdentifier()?.text ?: return lenses
        val range = parsedFile.tokenToRange(notificationDecl.notificationIdentifier()!!)

        // References lens
        val refCount = countReferences(notificationName, allParsedFiles) - 1
        if (refCount >= 0) {
            lenses.add(CodeLens(
                Range(Position(range.start.line, 0), Position(range.start.line, 0)),
                Command(
                    "$refCount reference${if (refCount != 1) "s" else ""}",
                    CMD_SHOW_REFERENCES,
                    listOf(parsedFile.uri, range.start.line, range.start.character)
                ),
                null
            ))
        }

        return lenses
    }

    // ─── Constant code lenses ─────────────────────────────────────────────────

    private fun getConstCodeLenses(
        parsedFile: ParsedFile,
        constDecl: NplParser.ConstDeclContext,
        allParsedFiles: Map<String, ParsedFile>,
    ): List<CodeLens> {
        val lenses = mutableListOf<CodeLens>()
        val constName = constDecl.optionallyTypedIdentifier()?.variableIdentifier()?.text ?: return lenses
        val range = parsedFile.tokenToRange(constDecl.optionallyTypedIdentifier()!!.variableIdentifier()!!)

        // References lens
        val refCount = countReferences(constName, allParsedFiles) - 1
        if (refCount >= 0) {
            lenses.add(CodeLens(
                Range(Position(range.start.line, 0), Position(range.start.line, 0)),
                Command(
                    "$refCount reference${if (refCount != 1) "s" else ""}",
                    CMD_SHOW_REFERENCES,
                    listOf(parsedFile.uri, range.start.line, range.start.character)
                ),
                null
            ))
        }

        return lenses
    }

    // ─── Helper functions ─────────────────────────────────────────────────────

    /**
     * Count references to a symbol across all files.
     */
    private fun countReferences(symbolName: String, allParsedFiles: Map<String, ParsedFile>): Int {
        var count = 0
        for ((_, file) in allParsedFiles) {
            count += countReferencesInFile(symbolName, file)
        }
        return count
    }

    /**
     * Count references to a symbol in a single file.
     */
    private fun countReferencesInFile(symbolName: String, parsedFile: ParsedFile): Int {
        val tree = parsedFile.tree ?: return 0
        return countReferencesInNode(symbolName, tree)
    }

    /**
     * Recursively count references in a parse tree node.
     */
    private fun countReferencesInNode(symbolName: String, node: org.antlr.v4.runtime.tree.ParseTree): Int {
        var count = 0

        when (node) {
            is NplParser.FunctionIdentifierContext -> if (node.text == symbolName) count++
            is NplParser.ProtocolIdentifierContext -> if (node.text == symbolName) count++
            is NplParser.StructIdentifierContext -> if (node.text == symbolName) count++
            is NplParser.UnionIdentifierContext -> if (node.text == symbolName) count++
            is NplParser.EnumIdentifierContext -> if (node.text == symbolName) count++
            is NplParser.NotificationIdentifierContext -> if (node.text == symbolName) count++
            is NplParser.StateIdentifierContext -> if (node.text == symbolName) count++
            is NplParser.VariableIdentifierContext -> if (node.text == symbolName) count++
            is NplParser.ActionIdentifierContext -> if (node.text == symbolName) count++
            is NplParser.IdentifierContext -> if (node.text == symbolName) count++
            is NplParser.SimpleTypeExprContext -> {
                if (node.typeIdentifier()?.text == symbolName) count++
            }
        }

        // Recurse into children
        if (node is org.antlr.v4.runtime.ParserRuleContext) {
            for (i in 0 until node.childCount) {
                count += countReferencesInNode(symbolName, node.getChild(i))
            }
        }

        return count
    }

    /**
     * Count implementations (variants for unions, states for protocols, etc.)
     */
    private fun countImplementations(symbolName: String, allParsedFiles: Map<String, ParsedFile>): Int {
        for ((_, file) in allParsedFiles) {
            val tree = file.tree ?: continue

            tree.statement().forEach { stmt ->
                // Check unions (using typeExprList for variants)
                stmt.unionDecl()?.let { unionDecl ->
                    if (unionDecl.unionIdentifier()?.text == symbolName) {
                        return unionDecl.typeExprList()?.typeExpr()?.size ?: 0
                    }
                }

                // Check protocols
                stmt.protocolDecl()?.let { protocolDecl ->
                    if (protocolDecl.protocolIdentifier()?.text == symbolName) {
                        val stateCount = protocolDecl.stateDecl().size
                        val actionCount = protocolDecl.actionDecl().size
                        return stateCount + actionCount
                    }
                }
            }
        }

        return 0
    }
}




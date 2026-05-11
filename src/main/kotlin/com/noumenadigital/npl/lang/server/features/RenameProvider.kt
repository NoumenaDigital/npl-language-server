package com.noumenadigital.npl.lang.server.features

import com.noumenadigital.npl.grammar.NplParser
import com.noumenadigital.npl.lang.server.ast.ParsedFile
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.PrepareRenameResult
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.TextEdit
import org.eclipse.lsp4j.WorkspaceEdit

/**
 * Provides rename functionality for NPL files.
 *
 * Supports renaming of:
 * - Functions
 * - Protocols
 * - Structs
 * - Unions
 * - Enums
 * - Notifications
 * - States
 * - Variables/Parameters
 * - Actions (permissions/obligations)
 *
 * The rename operation finds all references to the symbol across all loaded files
 * and generates text edits to replace the old name with the new name.
 */
object RenameProvider {

    /**
     * Prepare a rename operation by validating that the symbol at the given position
     * can be renamed. Returns the range and placeholder text for the rename.
     *
     * @return PrepareRenameResult with the symbol range and current name, or null if not renameable
     */
    fun prepareRename(
        parsedFile: ParsedFile,
        position: Position,
    ): PrepareRenameResult? {
        val node = parsedFile.findNodeAt(position) ?: return null

        // Find the renameable symbol at this position
        val symbolInfo = findRenameableSymbol(node, parsedFile) ?: return null

        return PrepareRenameResult(symbolInfo.range, symbolInfo.name)
    }

    /**
     * Perform a rename operation, returning all text edits needed across all files.
     *
     * @return WorkspaceEdit with all necessary text changes, or null if rename is not possible
     */
    fun rename(
        parsedFile: ParsedFile,
        position: Position,
        newName: String,
        allParsedFiles: Map<String, ParsedFile>,
    ): WorkspaceEdit? {
        val node = parsedFile.findNodeAt(position) ?: return null

        // Find the symbol to rename
        val symbolInfo = findRenameableSymbol(node, parsedFile) ?: return null

        // Validate the new name
        if (!isValidIdentifier(newName)) {
            return null
        }

        // Find all references (including the declaration)
        val references = ReferencesProvider.getReferences(
            parsedFile,
            position,
            includeDeclaration = true,
            allParsedFiles
        )

        if (references.isEmpty()) {
            return null
        }

        // Group edits by file URI
        val editsByUri = references.groupBy { it.uri }
            .mapValues { (_, locations) ->
                locations.map { location ->
                    TextEdit(location.range, newName)
                }
            }

        return WorkspaceEdit(editsByUri)
    }

    /**
     * Information about a renameable symbol.
     */
    private data class SymbolInfo(
        val name: String,
        val range: Range,
        val kind: SymbolKind,
    )

    private enum class SymbolKind {
        FUNCTION,
        PROTOCOL,
        STRUCT,
        UNION,
        ENUM,
        NOTIFICATION,
        STATE,
        VARIABLE,
        ACTION,
        TYPE_PARAMETER,
    }

    /**
     * Find the renameable symbol at the given node position.
     * Returns null if the symbol cannot be renamed (e.g., keywords, built-in types).
     */
    private fun findRenameableSymbol(node: ParseTree, parsedFile: ParsedFile): SymbolInfo? {
        var current: ParseTree? = node

        while (current != null) {
            val info = when (current) {
                is NplParser.FunctionIdentifierContext -> {
                    SymbolInfo(
                        current.text,
                        parsedFile.tokenToRange(current),
                        SymbolKind.FUNCTION
                    )
                }

                is NplParser.ProtocolIdentifierContext -> {
                    SymbolInfo(
                        current.text,
                        parsedFile.tokenToRange(current),
                        SymbolKind.PROTOCOL
                    )
                }

                is NplParser.StructIdentifierContext -> {
                    SymbolInfo(
                        current.text,
                        parsedFile.tokenToRange(current),
                        SymbolKind.STRUCT
                    )
                }

                is NplParser.UnionIdentifierContext -> {
                    SymbolInfo(
                        current.text,
                        parsedFile.tokenToRange(current),
                        SymbolKind.UNION
                    )
                }

                is NplParser.EnumIdentifierContext -> {
                    SymbolInfo(
                        current.text,
                        parsedFile.tokenToRange(current),
                        SymbolKind.ENUM
                    )
                }

                is NplParser.NotificationIdentifierContext -> {
                    SymbolInfo(
                        current.text,
                        parsedFile.tokenToRange(current),
                        SymbolKind.NOTIFICATION
                    )
                }

                is NplParser.StateIdentifierContext -> {
                    SymbolInfo(
                        current.text,
                        parsedFile.tokenToRange(current),
                        SymbolKind.STATE
                    )
                }

                is NplParser.VariableIdentifierContext -> {
                    SymbolInfo(
                        current.text,
                        parsedFile.tokenToRange(current),
                        SymbolKind.VARIABLE
                    )
                }

                is NplParser.ActionIdentifierContext -> {
                    SymbolInfo(
                        current.text,
                        parsedFile.tokenToRange(current),
                        SymbolKind.ACTION
                    )
                }

                is NplParser.TypeIdentifierContext -> {
                    // Check if this is a built-in type (not renameable)
                    val typeName = current.text
                    if (isBuiltInType(typeName)) {
                        null
                    } else {
                        SymbolInfo(
                            typeName,
                            parsedFile.tokenToRange(current),
                            SymbolKind.TYPE_PARAMETER
                        )
                    }
                }

                is NplParser.IdentifierContext -> {
                    // Generic identifier - check if it's a built-in
                    val name = current.text
                    if (isBuiltInType(name) || isKeyword(name)) {
                        null
                    } else {
                        SymbolInfo(
                            name,
                            parsedFile.tokenToRange(current),
                            SymbolKind.VARIABLE
                        )
                    }
                }

                else -> null
            }

            if (info != null) return info
            current = (current as? ParserRuleContext)?.parent
        }

        return null
    }

    /**
     * Check if the given name is a valid NPL identifier.
     */
    private fun isValidIdentifier(name: String): Boolean {
        if (name.isEmpty()) return false

        // Must start with a letter or underscore
        if (!name[0].isLetter() && name[0] != '_') return false

        // Rest can be letters, digits, or underscores
        return name.all { it.isLetterOrDigit() || it == '_' }
    }

    /**
     * Built-in types that cannot be renamed.
     */
    private val builtInTypes = setOf(
        "Number", "Text", "Boolean", "DateTime", "Duration", "Period",
        "Party", "Unit", "Any", "Nothing",
        "List", "Set", "Map", "Optional"
    )

    private fun isBuiltInType(name: String): Boolean = name in builtInTypes

    /**
     * Keywords that cannot be used as identifiers.
     */
    private val keywords = setOf(
        "package", "use", "struct", "union", "enum", "protocol", "function",
        "notification", "const", "var", "if", "else", "match", "return",
        "become", "this", "true", "false", "null", "native", "permission",
        "obligation", "state", "init", "returns", "before", "after", "between",
        "otherwise", "with", "vararg", "optional", "private", "identifier", "symbol"
    )

    private fun isKeyword(name: String): Boolean = name in keywords
}


package com.noumenadigital.npl.lang.server.features

import com.noumenadigital.npl.grammar.NplParser
import com.noumenadigital.npl.lang.server.ast.ParsedFile
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position

/**
 * Provides find-references functionality for NPL files.
 * Finds all usages of a symbol across loaded files.
 */
object ReferencesProvider {

    /**
     * Find all references to the symbol at the given position.
     */
    fun getReferences(
        parsedFile: ParsedFile,
        position: Position,
        includeDeclaration: Boolean,
        allParsedFiles: Map<String, ParsedFile>,
    ): List<Location> {
        val node = parsedFile.findNodeAt(position) ?: return emptyList()

        // Find what symbol we're looking for
        val symbolName = findSymbolName(node) ?: return emptyList()

        // Find all references across all files
        val locations = mutableListOf<Location>()

        for ((_, file) in allParsedFiles) {
            val refs = findReferencesInFile(symbolName, file, includeDeclaration)
            locations.addAll(refs)
        }

        return locations
    }

    private fun findSymbolName(node: ParseTree): String? {
        var current: ParseTree? = node
        while (current != null) {
            val name = when (current) {
                is NplParser.FunctionIdentifierContext -> current.text
                is NplParser.ProtocolIdentifierContext -> current.text
                is NplParser.StructIdentifierContext -> current.text
                is NplParser.UnionIdentifierContext -> current.text
                is NplParser.EnumIdentifierContext -> current.text
                is NplParser.NotificationIdentifierContext -> current.text
                is NplParser.StateIdentifierContext -> current.text
                is NplParser.VariableIdentifierContext -> current.text
                is NplParser.ActionIdentifierContext -> current.text
                is NplParser.IdentifierContext -> current.text
                else -> null
            }
            if (name != null) return name
            current = (current as? ParserRuleContext)?.parent
        }
        return null
    }

    private fun findReferencesInFile(
        symbolName: String,
        parsedFile: ParsedFile,
        includeDeclaration: Boolean,
    ): List<Location> {
        val tree = parsedFile.tree ?: return emptyList()
        val locations = mutableListOf<Location>()

        collectReferences(tree, symbolName, parsedFile, locations, includeDeclaration)

        return locations
    }

    private fun collectReferences(
        node: ParseTree,
        symbolName: String,
        parsedFile: ParsedFile,
        locations: MutableList<Location>,
        includeDeclaration: Boolean,
    ) {
        when (node) {
            // Function references
            is NplParser.FunctionIdentifierContext -> {
                if (node.text == symbolName) {
                    val isDeclaration = node.parent is NplParser.FunctionDeclContext
                    if (includeDeclaration || !isDeclaration) {
                        locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(node)))
                    }
                }
            }

            // Protocol references
            is NplParser.ProtocolIdentifierContext -> {
                if (node.text == symbolName) {
                    val isDeclaration = node.parent is NplParser.ProtocolDeclContext
                    if (includeDeclaration || !isDeclaration) {
                        locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(node)))
                    }
                }
            }

            // Struct references
            is NplParser.StructIdentifierContext -> {
                if (node.text == symbolName) {
                    val isDeclaration = node.parent is NplParser.StructDeclContext
                    if (includeDeclaration || !isDeclaration) {
                        locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(node)))
                    }
                }
            }

            // Union references
            is NplParser.UnionIdentifierContext -> {
                if (node.text == symbolName) {
                    val isDeclaration = node.parent is NplParser.UnionDeclContext
                    if (includeDeclaration || !isDeclaration) {
                        locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(node)))
                    }
                }
            }

            // Enum references
            is NplParser.EnumIdentifierContext -> {
                if (node.text == symbolName) {
                    val isDeclaration = node.parent is NplParser.EnumDeclContext
                    if (includeDeclaration || !isDeclaration) {
                        locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(node)))
                    }
                }
            }

            // Notification references
            is NplParser.NotificationIdentifierContext -> {
                if (node.text == symbolName) {
                    val isDeclaration = node.parent is NplParser.NotificationDeclContext
                    if (includeDeclaration || !isDeclaration) {
                        locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(node)))
                    }
                }
            }

            // State references
            is NplParser.StateIdentifierContext -> {
                if (node.text == symbolName) {
                    val isDeclaration = node.parent is NplParser.StateDeclContext
                    if (includeDeclaration || !isDeclaration) {
                        locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(node)))
                    }
                }
            }

            // Variable references
            is NplParser.VariableIdentifierContext -> {
                if (node.text == symbolName) {
                    locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(node)))
                }
            }

            // Action references
            is NplParser.ActionIdentifierContext -> {
                if (node.text == symbolName) {
                    locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(node)))
                }
            }

            // Generic identifiers (may be function calls, type references, etc.)
            is NplParser.IdentifierContext -> {
                if (node.text == symbolName) {
                    locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(node)))
                }
            }

            // Type expressions may contain type names
            is NplParser.SimpleTypeExprContext -> {
                val typeId = node.typeIdentifier()
                if (typeId?.text == symbolName) {
                    locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(typeId)))
                }
            }
        }

        // Recurse into children
        if (node is ParserRuleContext) {
            for (i in 0 until node.childCount) {
                collectReferences(node.getChild(i), symbolName, parsedFile, locations, includeDeclaration)
            }
        }
    }
}

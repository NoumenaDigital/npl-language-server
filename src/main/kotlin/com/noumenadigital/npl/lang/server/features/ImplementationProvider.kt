package com.noumenadigital.npl.lang.server.features

import com.noumenadigital.npl.grammar.NplParser
import com.noumenadigital.npl.lang.server.ast.ParsedFile
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position

/**
 * Provides go-to-implementation functionality for NPL files.
 *
 * In traditional OOP languages, this finds implementations of interfaces or abstract types.
 * NPL doesn't have interfaces/inheritance in the traditional sense, but this provider
 * supports the following use cases:
 *
 * - On a union type: finds all variant definitions
 * - On a protocol: finds all state definitions within that protocol
 *
 * For types without implementations (structs, enums, functions), this returns
 * the definition location as a fallback, similar to go-to-definition.
 */
object ImplementationProvider {

    /**
     * Get implementation locations for the symbol at the given position.
     */
    fun getImplementation(
        parsedFile: ParsedFile,
        position: Position,
        allParsedFiles: Map<String, ParsedFile>,
    ): List<Location> {
        val node = parsedFile.findNodeAt(position) ?: return emptyList()

        // Determine what kind of symbol we're looking at
        val symbolInfo = extractSymbolInfo(node) ?: return emptyList()

        return when (symbolInfo.kind) {
            SymbolKind.UNION -> findUnionVariants(symbolInfo.name, allParsedFiles)
            SymbolKind.PROTOCOL -> findProtocolStates(symbolInfo.name, allParsedFiles)
            // For other types, fall back to definition
            else -> DefinitionProvider.getDefinition(parsedFile, position, allParsedFiles)
        }
    }

    private enum class SymbolKind {
        UNION,
        PROTOCOL,
        OTHER
    }

    private data class SymbolInfo(val name: String, val kind: SymbolKind)

    /**
     * Extract symbol information from the parse tree node.
     */
    private fun extractSymbolInfo(node: ParseTree): SymbolInfo? {
        var current: ParseTree? = node

        while (current != null) {
            when (current) {
                is NplParser.UnionIdentifierContext -> {
                    return SymbolInfo(current.text, SymbolKind.UNION)
                }

                is NplParser.ProtocolIdentifierContext -> {
                    return SymbolInfo(current.text, SymbolKind.PROTOCOL)
                }

                is NplParser.StructIdentifierContext,
                is NplParser.EnumIdentifierContext,
                is NplParser.FunctionIdentifierContext,
                is NplParser.NotificationIdentifierContext -> {
                    return SymbolInfo(current.text, SymbolKind.OTHER)
                }

                is NplParser.IdentifierContext -> {
                    // Generic identifier - return as OTHER, will fall back to definition
                    return SymbolInfo(current.text, SymbolKind.OTHER)
                }

                is NplParser.VariableIdentifierContext -> {
                    return SymbolInfo(current.text, SymbolKind.OTHER)
                }
            }

            current = (current as? ParserRuleContext)?.parent
        }

        return null
    }

    /**
     * Find all variants of a union type.
     * In NPL, union variants are defined as type expressions in the union declaration.
     */
    private fun findUnionVariants(
        unionName: String,
        allParsedFiles: Map<String, ParsedFile>,
    ): List<Location> {
        val locations = mutableListOf<Location>()

        for ((_, parsedFile) in allParsedFiles) {
            val tree = parsedFile.tree ?: continue

            for (statement in tree.statement()) {
                statement.unionDecl()?.let { union ->
                    if (union.unionIdentifier()?.text == unionName) {
                        // Add the union declaration itself
                        union.unionIdentifier()?.let { id ->
                            locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(id)))
                        }

                        // Add all variant type definitions
                        // Union variants in NPL are expressed as a type expression list
                        union.typeExprList()?.typeExpr()?.forEach { typeExpr ->
                            locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(typeExpr)))
                        }
                    }
                }
            }
        }

        return locations.distinctBy { "${it.uri}:${it.range.start.line}:${it.range.start.character}" }
    }

    /**
     * Find all state definitions within a protocol.
     */
    private fun findProtocolStates(
        protocolName: String,
        allParsedFiles: Map<String, ParsedFile>,
    ): List<Location> {
        val locations = mutableListOf<Location>()

        for ((_, parsedFile) in allParsedFiles) {
            val tree = parsedFile.tree ?: continue

            for (statement in tree.statement()) {
                statement.protocolDecl()?.let { protocol ->
                    if (protocol.protocolIdentifier()?.text == protocolName) {
                        // Add the protocol declaration itself
                        protocol.protocolIdentifier()?.let { id ->
                            locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(id)))
                        }

                        // Add all state definitions
                        protocol.stateDecl().forEach { state ->
                            state.stateIdentifier()?.let { stateId ->
                                locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(stateId)))
                            }
                        }

                        // Add all permission definitions
                        protocol.actionDecl().forEach { action ->
                            action.permissionDecl()?.actionHead()?.actionIdentifier()?.let { permId ->
                                locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(permId)))
                            }
                            action.obligationDecl()?.actionHead()?.actionIdentifier()?.let { oblId ->
                                locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(oblId)))
                            }
                        }
                    }
                }
            }
        }

        return locations.distinctBy { "${it.uri}:${it.range.start.line}:${it.range.start.character}" }
    }
}



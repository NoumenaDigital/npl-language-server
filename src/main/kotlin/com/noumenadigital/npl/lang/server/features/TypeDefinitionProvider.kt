package com.noumenadigital.npl.lang.server.features

import com.noumenadigital.npl.grammar.NplParser
import com.noumenadigital.npl.lang.server.ast.ParsedFile
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position

/**
 * Provides go-to-type-definition functionality for NPL files.
 *
 * When invoked on a symbol (variable, parameter, field), this navigates
 * to the definition of that symbol's type rather than the symbol's own definition.
 *
 * For example:
 * - On `customer` in `var customer: Customer` → navigates to `Customer` struct definition
 * - On `name` in `function greet(name: Text)` → returns empty (built-in type)
 */
object TypeDefinitionProvider {

    /**
     * Get the type definition location for the symbol at the given position.
     */
    fun getTypeDefinition(
        parsedFile: ParsedFile,
        position: Position,
        allParsedFiles: Map<String, ParsedFile>,
    ): List<Location> {
        val node = parsedFile.findNodeAt(position) ?: return emptyList()

        // Extract the type name of the symbol at this position
        val typeName = extractTypeName(node, parsedFile) ?: return emptyList()

        // Skip built-in types (they have no source location)
        if (isBuiltInType(typeName)) {
            return emptyList()
        }

        // Find the type definition across all files
        return findTypeDefinition(typeName, allParsedFiles)
    }

    /**
     * Built-in types that have no source definition.
     */
    private val builtInTypes = setOf(
        "Number", "Text", "Boolean", "DateTime", "Duration", "Period",
        "Party", "Unit", "Any", "Nothing",
        "List", "Set", "Map", "Optional"
    )

    private fun isBuiltInType(typeName: String): Boolean {
        // Strip generic parameters for comparison
        val baseType = typeName.takeWhile { it != '<' && it != '[' }
        return baseType in builtInTypes
    }

    /**
     * Extract the type name of the symbol at the given node position.
     */
    private fun extractTypeName(node: ParseTree, parsedFile: ParsedFile): String? {
        var current: ParseTree? = node

        while (current != null) {
            when (current) {
                // If we're on a type expression, return that type directly
                is NplParser.TypeExprContext -> {
                    return current.text?.takeWhile { it != '<' && it != '[' }
                }

                is NplParser.SimpleTypeExprContext -> {
                    return current.typeIdentifier()?.text
                }

                // Variable identifier - look for its type declaration
                is NplParser.VariableIdentifierContext -> {
                    return findVariableTypeName(current, parsedFile)
                }

                // Type identifiers are already a type reference
                is NplParser.StructIdentifierContext,
                is NplParser.UnionIdentifierContext,
                is NplParser.EnumIdentifierContext,
                is NplParser.ProtocolIdentifierContext,
                is NplParser.NotificationIdentifierContext -> {
                    return current.text
                }

                // Generic identifier - try to find its type
                is NplParser.IdentifierContext -> {
                    val varType = findVariableTypeName(current, parsedFile)
                    if (varType != null) return varType
                }
            }

            current = (current as? ParserRuleContext)?.parent
        }

        return null
    }

    /**
     * Find the declared type of a variable, parameter, or field.
     */
    private fun findVariableTypeName(identifier: ParseTree, parsedFile: ParsedFile): String? {
        val varName = identifier.text ?: return null
        var current: ParserRuleContext? = when (identifier) {
            is ParserRuleContext -> identifier
            else -> return null
        }

        while (current != null) {
            when (current) {
                // Check typed identifier (e.g., in function params, struct fields)
                is NplParser.TypedIdentifierContext -> {
                    if (current.variableIdentifier()?.text == varName) {
                        return current.typeExpr()?.text?.takeWhile { it != '<' && it != '[' }
                    }
                }

                // Check function parameters
                is NplParser.FunctionDeclContext -> {
                    current.paramList()?.typedIdentifiers()?.typedIdentifier()?.forEach { param ->
                        if (param.variableIdentifier()?.text == varName) {
                            return param.typeExpr()?.text?.takeWhile { it != '<' && it != '[' }
                        }
                    }
                    // Check return type if we're on the function name
                    if (current.functionIdentifier()?.text == varName) {
                        return current.returnTypeExpr?.text?.takeWhile { it != '<' && it != '[' }
                    }
                }

                // Check protocol constructor parameters
                is NplParser.ProtocolDeclContext -> {
                    current.memberTypedIdentifiers()?.memberTypedIdentifier()?.forEach { member ->
                        val typedId = member.typedIdentifier()
                        if (typedId?.variableIdentifier()?.text == varName) {
                            return typedId.typeExpr()?.text?.takeWhile { it != '<' && it != '[' }
                        }
                    }
                }

                // Check struct fields
                is NplParser.StructDeclContext -> {
                    current.typedIdentifiers()?.typedIdentifier()?.forEach { field ->
                        if (field.variableIdentifier()?.text == varName) {
                            return field.typeExpr()?.text?.takeWhile { it != '<' && it != '[' }
                        }
                    }
                }

                // Check permission/obligation parameters
                is NplParser.PermissionDeclContext -> {
                    current.actionHead()?.actionParams()?.typedIdentifiers()?.typedIdentifier()?.forEach { param ->
                        if (param.variableIdentifier()?.text == varName) {
                            return param.typeExpr()?.text?.takeWhile { it != '<' && it != '[' }
                        }
                    }
                }

                is NplParser.ObligationDeclContext -> {
                    current.actionHead()?.actionParams()?.typedIdentifiers()?.typedIdentifier()?.forEach { param ->
                        if (param.variableIdentifier()?.text == varName) {
                            return param.typeExpr()?.text?.takeWhile { it != '<' && it != '[' }
                        }
                    }
                }

                // Check const declarations
                is NplParser.ConstDeclContext -> {
                    val optTypedId = current.optionallyTypedIdentifier()
                    if (optTypedId?.variableIdentifier()?.text == varName) {
                        return optTypedId.typeExpr()?.text?.takeWhile { it != '<' && it != '[' }
                    }
                }
            }

            current = current.parent as? ParserRuleContext
        }

        return null
    }

    /**
     * Find the definition of a type across all parsed files.
     */
    private fun findTypeDefinition(
        typeName: String,
        allParsedFiles: Map<String, ParsedFile>,
    ): List<Location> {
        val locations = mutableListOf<Location>()

        for ((_, parsedFile) in allParsedFiles) {
            val tree = parsedFile.tree ?: continue

            for (statement in tree.statement()) {
                statement.structDecl()?.let { struct ->
                    if (struct.structIdentifier()?.text == typeName) {
                        locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(struct.structIdentifier()!!)))
                    }
                }

                statement.unionDecl()?.let { union ->
                    if (union.unionIdentifier()?.text == typeName) {
                        locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(union.unionIdentifier()!!)))
                    }
                }

                statement.enumDecl()?.let { enum ->
                    if (enum.enumIdentifier()?.text == typeName) {
                        locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(enum.enumIdentifier()!!)))
                    }
                }

                statement.protocolDecl()?.let { protocol ->
                    if (protocol.protocolIdentifier()?.text == typeName) {
                        locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(protocol.protocolIdentifier()!!)))
                    }
                }

                statement.notificationDecl()?.let { notification ->
                    if (notification.notificationIdentifier()?.text == typeName) {
                        locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(notification.notificationIdentifier()!!)))
                    }
                }
            }
        }

        return locations.distinctBy { "${it.uri}:${it.range.start.line}:${it.range.start.character}" }
    }
}



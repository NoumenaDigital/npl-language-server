package com.noumenadigital.npl.lang.server.features

import com.noumenadigital.npl.grammar.NplParser
import com.noumenadigital.npl.lang.server.ast.ParsedFile
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position

/**
 * Provides go-to-definition functionality for NPL files.
 * Finds the declaration of a symbol from its usage.
 *
 * Implementation mirrors the IntelliJ plugin's reference resolution,
 * including receiver type inference for method calls.
 */
object DefinitionProvider {

    /**
     * Get the definition location for the symbol at the given position.
     */
    fun getDefinition(
        parsedFile: ParsedFile,
        position: Position,
        allParsedFiles: Map<String, ParsedFile>,
    ): List<Location> {
        val node = parsedFile.findNodeAt(position) ?: return emptyList()

        // Extract identifier information including receiver type context
        val identifierInfo = extractIdentifierInfo(node, parsedFile) ?: return emptyList()

        // Find the definition based on the identifier info
        return findDefinition(identifierInfo, parsedFile, allParsedFiles)
    }

    /**
     * Information about an identifier and its context.
     */
    private data class IdentifierInfo(
        val name: String,
        val kind: IdentifierKind,
        val receiverTypeName: String? = null,
        val isInTypePosition: Boolean = false,
    )

    private enum class IdentifierKind {
        FUNCTION,
        TYPE,       // struct, union, enum, protocol
        VARIABLE,
        FIELD,      // member access like receiver.field
        UNKNOWN
    }

    /**
     * Extract identifier information from the parse tree node.
     * This includes determining the kind of identifier and any receiver type for method calls.
     */
    private fun extractIdentifierInfo(node: ParseTree, parsedFile: ParsedFile): IdentifierInfo? {
        var current: ParseTree? = node
        val receiverTypeName: String? = null
        var isInTypePosition = false

        while (current != null) {
            when (current) {
                // Function identifier
                is NplParser.FunctionIdentifierContext -> {
                    return IdentifierInfo(current.text, IdentifierKind.FUNCTION, receiverTypeName)
                }

                // Type identifiers
                is NplParser.ProtocolIdentifierContext -> {
                    return IdentifierInfo(current.text, IdentifierKind.TYPE)
                }
                is NplParser.StructIdentifierContext -> {
                    return IdentifierInfo(current.text, IdentifierKind.TYPE)
                }
                is NplParser.UnionIdentifierContext -> {
                    return IdentifierInfo(current.text, IdentifierKind.TYPE)
                }
                is NplParser.EnumIdentifierContext -> {
                    return IdentifierInfo(current.text, IdentifierKind.TYPE)
                }
                is NplParser.NotificationIdentifierContext -> {
                    return IdentifierInfo(current.text, IdentifierKind.TYPE)
                }
                is NplParser.StateIdentifierContext -> {
                    return IdentifierInfo(current.text, IdentifierKind.TYPE)
                }

                // Simple type expression - has a direct type identifier (check before TypeExprContext)
                is NplParser.SimpleTypeExprContext -> {
                    isInTypePosition = true
                    val typeId = current.typeIdentifier()
                    if (typeId != null) {
                        return IdentifierInfo(typeId.text, IdentifierKind.TYPE, null, true)
                    }
                }

                // Type expression - reference to a type
                is NplParser.TypeExprContext -> {
                    isInTypePosition = true
                    // Get the base type name from the type expression text
                    val typeName = current.text?.takeWhile { it != '<' && it != '[' }
                    if (typeName != null && typeName.isNotEmpty()) {
                        return IdentifierInfo(typeName, IdentifierKind.TYPE, null, true)
                    }
                }

                // Variable and action identifiers
                is NplParser.VariableIdentifierContext -> {
                    // Check if this is a field access
                    val maybeField = findReceiverType(current, parsedFile)
                    if (maybeField != null) {
                        return IdentifierInfo(current.text, IdentifierKind.FIELD, maybeField)
                    }
                    return IdentifierInfo(current.text, IdentifierKind.VARIABLE, receiverTypeName)
                }
                is NplParser.ActionIdentifierContext -> {
                    return IdentifierInfo(current.text, IdentifierKind.FUNCTION, receiverTypeName)
                }

                // Generic identifier - could be function call, type reference, or variable
                is NplParser.IdentifierContext -> {
                    // Try to infer the receiver type from the expression context
                    val maybeReceiverType = findReceiverType(current, parsedFile)
                    val kind = if (maybeReceiverType != null) {
                        IdentifierKind.FIELD
                    } else if (isInTypePosition) {
                        IdentifierKind.TYPE
                    } else {
                        IdentifierKind.UNKNOWN
                    }
                    return IdentifierInfo(current.text, kind, maybeReceiverType ?: receiverTypeName, isInTypePosition)
                }
            }

            current = (current as? ParserRuleContext)?.parent
        }

        return null
    }

    /**
     * Find the receiver type for a member expression.
     * For expressions like 'receiver.member()' or 'receiver.field', this returns the type name of the receiver.
     */
    private fun findReceiverType(identifier: ParseTree, parsedFile: ParsedFile): String? {
        // Walk up to find expression context
        var current: ParserRuleContext? = when (identifier) {
            is ParserRuleContext -> identifier.parent as? ParserRuleContext
            else -> null
        }

        while (current != null) {
            when (current) {
                is NplParser.ExprContext -> {
                    // Check if this is a dot expression (receiver.member)
                    val children = current.children ?: return null

                    // Look for DOT pattern: expr DOT tailExpr
                    if (children.size >= 3) {
                        val dotIndex = children.indexOfFirst { it.text == "." }
                        if (dotIndex > 0) {
                            val receiver = children[dotIndex - 1]
                            // Infer the type name from the receiver
                            return inferTypeFromExpr(receiver, parsedFile)
                        }
                    }
                    return null
                }
            }
            current = current.parent as? ParserRuleContext
        }

        return null
    }

    /**
     * Infer the type name from an expression.
     * This is a simplified version - full type inference would require the compiler.
     */
    private fun inferTypeFromExpr(expr: ParseTree, parsedFile: ParsedFile): String? {
        return when (expr) {
            is NplParser.ExprContext -> {
                val children = expr.children ?: return null
                if (children.size == 1) {
                    val term = children[0]
                    return inferTypeFromExpr(term, parsedFile)
                }
                // For more complex expressions (e.g., chained calls), we would need full type inference
                null
            }
            is NplParser.TermContext -> {
                val children = expr.children
                if (children != null && children.isNotEmpty()) {
                    return inferTypeFromExpr(children[0], parsedFile)
                }
                null
            }
            is ParserRuleContext -> {
                // Check for 'this' keyword
                if (expr.text == "this") {
                    return findEnclosingProtocolName(expr)
                }
                // Check for an identifier that might be a variable
                val text = expr.text
                if (text != null && text.isNotEmpty() && !text.contains(".") && !text.contains("(")) {
                    // Try to find the variable's declared type
                    return findVariableType(text, expr, parsedFile)
                }
                null
            }
            else -> null
        }
    }

    /**
     * Find the enclosing protocol name for 'this' expressions.
     */
    private fun findEnclosingProtocolName(node: ParseTree): String? {
        var current: ParserRuleContext? = when (node) {
            is ParserRuleContext -> node
            else -> null
        }

        while (current != null) {
            if (current is NplParser.ProtocolDeclContext) {
                return current.protocolIdentifier()?.text
            }
            current = current.parent as? ParserRuleContext
        }
        return null
    }

    /**
     * Find the declared type of a variable by looking at variable declarations in scope.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun findVariableType(varName: String, context: ParseTree, parsedFile: ParsedFile): String? {
        // Look for variable declarations in function params, protocol params, etc.
        var current: ParserRuleContext? = when (context) {
            is ParserRuleContext -> context
            else -> null
        }

        while (current != null) {
            when (current) {
                is NplParser.FunctionDeclContext -> {
                    // Check function parameters
                    current.paramList()?.typedIdentifiers()?.typedIdentifier()?.forEach { param ->
                        if (param.variableIdentifier()?.text == varName) {
                            return param.typeExpr()?.text?.takeWhile { it != '<' && it != '[' }
                        }
                    }
                }
                is NplParser.ProtocolDeclContext -> {
                    // Check protocol params (constructor parameters) using memberTypedIdentifiers
                    current.memberTypedIdentifiers()?.memberTypedIdentifier()?.forEach { member ->
                        val typedId = member.typedIdentifier()
                        if (typedId?.variableIdentifier()?.text == varName) {
                            return typedId.typeExpr()?.text?.takeWhile { it != '<' && it != '[' }
                        }
                    }
                }
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
            }
            current = current.parent as? ParserRuleContext
        }

        return null
    }

    /**
     * Extract the type name from a type expression.
     */
    private fun extractTypeName(typeExpr: NplParser.TypeExprContext?): String? {
        if (typeExpr == null) return null

        // For more complex types, return the text with generics stripped
        return typeExpr.text?.let { typeName ->
            // Strip generics like List<X> -> List
            typeName.takeWhile { it != '<' && it != '[' }
        }
    }

    /**
     * Find the definition location for the given identifier.
     */
    private fun findDefinition(
        info: IdentifierInfo,
        currentFile: ParsedFile,
        allParsedFiles: Map<String, ParsedFile>,
    ): List<Location> {
        val locations = mutableListOf<Location>()

        // If we have a receiver type, first look in that type's definition
        if (info.receiverTypeName != null && info.kind == IdentifierKind.FIELD) {
            findMemberDefinition(info.name, info.receiverTypeName, allParsedFiles, locations)
        }

        // Look in all files for the definition
        for ((uri, parsedFile) in allParsedFiles) {
            val tree = parsedFile.tree ?: continue

            when (info.kind) {
                IdentifierKind.TYPE -> {
                    findTypeDefinition(info.name, tree, parsedFile, locations)
                }
                IdentifierKind.FUNCTION -> {
                    // For functions with receiver, only search in receiver type or top-level
                    if (info.receiverTypeName != null) {
                        findMethodInType(info.name, info.receiverTypeName, tree, parsedFile, locations)
                    } else {
                        findFunctionDefinition(info.name, tree, parsedFile, locations)
                    }
                }
                IdentifierKind.VARIABLE -> {
                    // Variables are typically local, search in current file first
                    if (uri == currentFile.uri) {
                        findVariableDefinition(info.name, tree, parsedFile, locations)
                    }
                }
                IdentifierKind.FIELD -> {
                    // Already handled above for receiver types
                    if (info.receiverTypeName == null) {
                        // Search as regular variable in current scope
                        findVariableDefinition(info.name, tree, parsedFile, locations)
                    }
                }
                IdentifierKind.UNKNOWN -> {
                    // Try all definition types
                    findTypeDefinition(info.name, tree, parsedFile, locations)
                    findFunctionDefinition(info.name, tree, parsedFile, locations)
                    if (uri == currentFile.uri) {
                        findVariableDefinition(info.name, tree, parsedFile, locations)
                    }
                }
            }
        }

        // Check imports for cross-file references
        findDefinitionThroughImports(info, currentFile, allParsedFiles, locations)

        return locations.distinctBy { "${it.uri}:${it.range.start.line}:${it.range.start.character}" }
    }

    /**
     * Find a member (field or method) definition in a type.
     */
    private fun findMemberDefinition(
        memberName: String,
        typeName: String,
        allParsedFiles: Map<String, ParsedFile>,
        locations: MutableList<Location>,
    ) {
        for ((_, parsedFile) in allParsedFiles) {
            val tree = parsedFile.tree ?: continue

            for (statement in tree.statement()) {
                // Check protocols
                statement.protocolDecl()?.let { protocol ->
                    if (protocol.protocolIdentifier()?.text == typeName) {
                        // Search for member in protocol
                        findMemberInProtocol(memberName, protocol, parsedFile, locations)
                        // Also check protocol params (fields) using memberTypedIdentifiers
                        protocol.memberTypedIdentifiers()?.memberTypedIdentifier()?.forEach { member ->
                            val typedId = member.typedIdentifier()
                            if (typedId?.variableIdentifier()?.text == memberName) {
                                locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(typedId)))
                            }
                        }
                    }
                }

                // Check structs
                statement.structDecl()?.let { struct ->
                    if (struct.structIdentifier()?.text == typeName) {
                        findFieldInStruct(memberName, struct, parsedFile, locations)
                    }
                }
            }
        }
    }

    /**
     * Find a member (permission, obligation, function, state) in a protocol.
     */
    private fun findMemberInProtocol(
        memberName: String,
        protocol: NplParser.ProtocolDeclContext,
        parsedFile: ParsedFile,
        locations: MutableList<Location>,
    ) {
        // Actions (which contain permissions and obligations)
        protocol.actionDecl().forEach { actionCtx ->
            // Permissions
            actionCtx.permissionDecl()?.let { permission ->
                val actionId = permission.actionHead()?.actionIdentifier()
                if (actionId?.text == memberName) {
                    locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(actionId)))
                }
            }

            // Obligations
            actionCtx.obligationDecl()?.let { obligation ->
                val actionId = obligation.actionHead()?.actionIdentifier()
                if (actionId?.text == memberName) {
                    locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(actionId)))
                }
            }
        }

        // Functions in protocol
        protocol.functionDecl().forEach { func ->
            val funcId = func.functionIdentifier()
            if (funcId?.text == memberName) {
                locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(funcId)))
            }
        }

        // States
        protocol.stateDecl().forEach { state ->
            val stateId = state.stateIdentifier()
            if (stateId?.text == memberName) {
                locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(stateId)))
            }
        }
    }

    /**
     * Find a field in a struct.
     */
    private fun findFieldInStruct(
        fieldName: String,
        struct: NplParser.StructDeclContext,
        parsedFile: ParsedFile,
        locations: MutableList<Location>,
    ) {
        struct.typedIdentifiers()?.typedIdentifier()?.forEach { field ->
            if (field.variableIdentifier()?.text == fieldName) {
                locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(field)))
            }
        }
    }

    /**
     * Find type definitions (struct, union, enum, protocol).
     */
    private fun findTypeDefinition(
        typeName: String,
        tree: NplParser.RootContext,
        parsedFile: ParsedFile,
        locations: MutableList<Location>,
    ) {
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

    /**
     * Find function definitions.
     */
    private fun findFunctionDefinition(
        functionName: String,
        tree: NplParser.RootContext,
        parsedFile: ParsedFile,
        locations: MutableList<Location>,
    ) {
        for (statement in tree.statement()) {
            statement.functionDecl()?.let { func ->
                if (func.functionIdentifier()?.text == functionName) {
                    locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(func.functionIdentifier()!!)))
                }
            }

            // Also check functions inside protocols
            statement.protocolDecl()?.let { protocol ->
                protocol.functionDecl().forEach { func ->
                    if (func.functionIdentifier()?.text == functionName) {
                        locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(func.functionIdentifier()!!)))
                    }
                }
            }
        }
    }

    /**
     * Find a method definition within a specific type.
     */
    private fun findMethodInType(
        methodName: String,
        typeName: String,
        tree: NplParser.RootContext,
        parsedFile: ParsedFile,
        locations: MutableList<Location>,
    ) {
        for (statement in tree.statement()) {
            // Check protocols
            statement.protocolDecl()?.let { protocol ->
                if (protocol.protocolIdentifier()?.text == typeName) {
                    findMemberInProtocol(methodName, protocol, parsedFile, locations)
                }
            }

            // Check functions with receiver type annotation (native functions)
            statement.functionDecl()?.let { func ->
                if (func.functionIdentifier()?.text == methodName) {
                    // Check if this function has a receiver type matching typeName
                    val receiverType = func.receiverTypeExpr
                    if (receiverType != null && extractTypeName(receiverType) == typeName) {
                        locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(func.functionIdentifier()!!)))
                    }
                }
            }
        }
    }

    /**
     * Find variable definitions (in protocol fields, function params, etc.).
     */
    private fun findVariableDefinition(
        varName: String,
        tree: NplParser.RootContext,
        parsedFile: ParsedFile,
        locations: MutableList<Location>,
    ) {
        for (statement in tree.statement()) {
            // Check protocol constructor parameters using memberTypedIdentifiers
            statement.protocolDecl()?.let { protocol ->
                protocol.memberTypedIdentifiers()?.memberTypedIdentifier()?.forEach { member ->
                    val typedId = member.typedIdentifier()
                    if (typedId?.variableIdentifier()?.text == varName) {
                        locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(typedId)))
                    }
                }
            }

            // Check function parameters
            statement.functionDecl()?.let { func ->
                func.paramList()?.typedIdentifiers()?.typedIdentifier()?.forEach { param ->
                    if (param.variableIdentifier()?.text == varName) {
                        locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(param)))
                    }
                }
            }

            // Check constants
            statement.constDecl()?.let { constDecl ->
                if (constDecl.optionallyTypedIdentifier()?.variableIdentifier()?.text == varName) {
                    locations.add(Location(parsedFile.uri, parsedFile.tokenToRange(constDecl)))
                }
            }
        }
    }

    /**
     * Find definitions by following import statements.
     */
    private fun findDefinitionThroughImports(
        info: IdentifierInfo,
        currentFile: ParsedFile,
        allParsedFiles: Map<String, ParsedFile>,
        locations: MutableList<Location>,
    ) {
        val tree = currentFile.tree ?: return

        // Get all use statements from root (not from statement)
        for (useStmt in tree.useStmt()) {
            val qualifiedName = useStmt.qualifiedName()?.text ?: continue

            // Check if this import matches the name we're looking for
            val importedName = qualifiedName.substringAfterLast('.')
            if (importedName == info.name || info.name.startsWith("$importedName.")) {
                // Find the file that provides this symbol
                val packageName = qualifiedName.substringBeforeLast('.')
                for ((_, parsedFile) in allParsedFiles) {
                    if (parsedFile.getPackageName() == packageName) {
                        val fileTree = parsedFile.tree ?: continue

                        when (info.kind) {
                            IdentifierKind.TYPE, IdentifierKind.UNKNOWN -> {
                                findTypeDefinition(info.name, fileTree, parsedFile, locations)
                            }
                            IdentifierKind.FUNCTION -> {
                                findFunctionDefinition(info.name, fileTree, parsedFile, locations)
                            }
                            else -> {}
                        }
                    }
                }
            }
        }
    }
}







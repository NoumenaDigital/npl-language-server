package com.noumenadigital.npl.lang.server.features

import com.noumenadigital.npl.grammar.NplParser
import com.noumenadigital.npl.lang.server.ast.ParsedFile
import org.eclipse.lsp4j.DocumentSymbol
import org.eclipse.lsp4j.SymbolKind

/**
 * Extracts document symbols (outline) from a parsed NPL file.
 * Based on the IntelliJ plugin's Structure.kt implementation.
 */
object DocumentSymbolProvider {

    fun getDocumentSymbols(parsedFile: ParsedFile): List<DocumentSymbol> {
        val tree = parsedFile.tree ?: return emptyList()
        return extractSymbols(parsedFile, tree)
    }

    private fun extractSymbols(parsedFile: ParsedFile, root: NplParser.RootContext): List<DocumentSymbol> {
        val symbols = mutableListOf<DocumentSymbol>()

        root.statement().forEach { statement ->
            extractSymbolFromStatement(parsedFile, statement)?.let { symbols.add(it) }
        }

        return symbols
    }

    private fun extractSymbolFromStatement(parsedFile: ParsedFile, statement: NplParser.StatementContext): DocumentSymbol? {
        // Check each possible declaration type
        statement.protocolDecl()?.let { return extractProtocolSymbol(parsedFile, it) }
        statement.functionDecl()?.let { return extractFunctionSymbol(parsedFile, it) }
        statement.structDecl()?.let { return extractStructSymbol(parsedFile, it) }
        statement.unionDecl()?.let { return extractUnionSymbol(parsedFile, it) }
        statement.enumDecl()?.let { return extractEnumSymbol(parsedFile, it) }
        statement.notificationDecl()?.let { return extractNotificationSymbol(parsedFile, it) }
        statement.constDecl()?.let { return extractConstSymbol(parsedFile, it) }

        return null
    }

    private fun extractProtocolSymbol(parsedFile: ParsedFile, ctx: NplParser.ProtocolDeclContext): DocumentSymbol {
        val name = ctx.protocolIdentifier()?.text ?: "<anonymous>"
        val range = parsedFile.tokenToRange(ctx)
        val selectionRange = ctx.protocolIdentifier()?.let { parsedFile.tokenToRange(it) } ?: range

        val symbol = DocumentSymbol(name, SymbolKind.Class, range, selectionRange)

        // Add children: permissions, obligations, actions, states
        val children = mutableListOf<DocumentSymbol>()

        // States
        ctx.stateDecl().forEach { stateCtx ->
            val stateName = stateCtx.stateIdentifier()?.text ?: "<anonymous>"
            val stateRange = parsedFile.tokenToRange(stateCtx)
            val stateSelectionRange = stateCtx.stateIdentifier()?.let { parsedFile.tokenToRange(it) } ?: stateRange
            children.add(DocumentSymbol(stateName, SymbolKind.Enum, stateRange, stateSelectionRange))
        }

        // Actions (which contain permissions and obligations)
        ctx.actionDecl().forEach { actionCtx ->
            // Permission
            actionCtx.permissionDecl()?.let { permCtx ->
                val permName = permCtx.actionHead()?.actionIdentifier()?.text ?: "<anonymous>"
                val permRange = parsedFile.tokenToRange(permCtx)
                val permSelectionRange = permCtx.actionHead()?.actionIdentifier()?.let { parsedFile.tokenToRange(it) } ?: permRange
                children.add(DocumentSymbol(permName, SymbolKind.Method, permRange, permSelectionRange))
            }

            // Obligation
            actionCtx.obligationDecl()?.let { oblCtx ->
                val oblName = oblCtx.actionHead()?.actionIdentifier()?.text ?: "<anonymous>"
                val oblRange = parsedFile.tokenToRange(oblCtx)
                val oblSelectionRange = oblCtx.actionHead()?.actionIdentifier()?.let { parsedFile.tokenToRange(it) } ?: oblRange
                children.add(DocumentSymbol(oblName, SymbolKind.Method, oblRange, oblSelectionRange))
            }
        }

        // Functions within protocol
        ctx.functionDecl().forEach { funcCtx ->
            val funcName = funcCtx.functionIdentifier()?.text ?: "<anonymous>"
            val funcRange = parsedFile.tokenToRange(funcCtx)
            val funcSelectionRange = funcCtx.functionIdentifier()?.let { parsedFile.tokenToRange(it) } ?: funcRange
            children.add(DocumentSymbol(funcName, SymbolKind.Method, funcRange, funcSelectionRange))
        }

        if (children.isNotEmpty()) {
            symbol.children = children
        }

        return symbol
    }

    private fun extractFunctionSymbol(parsedFile: ParsedFile, ctx: NplParser.FunctionDeclContext): DocumentSymbol {
        val name = ctx.functionIdentifier()?.text ?: "<anonymous>"
        val range = parsedFile.tokenToRange(ctx)
        val selectionRange = ctx.functionIdentifier()?.let { parsedFile.tokenToRange(it) } ?: range

        // Determine if it's a method (has receiver type) or a function
        // receiverTypeExpr is set when there's a "Type." prefix
        val kind = if (ctx.receiverTypeExpr != null) SymbolKind.Method else SymbolKind.Function

        return DocumentSymbol(name, kind, range, selectionRange)
    }

    private fun extractStructSymbol(parsedFile: ParsedFile, ctx: NplParser.StructDeclContext): DocumentSymbol {
        val name = ctx.structIdentifier()?.text ?: "<anonymous>"
        val range = parsedFile.tokenToRange(ctx)
        val selectionRange = ctx.structIdentifier()?.let { parsedFile.tokenToRange(it) } ?: range

        val symbol = DocumentSymbol(name, SymbolKind.Struct, range, selectionRange)

        // Add fields as children
        val children = mutableListOf<DocumentSymbol>()
        ctx.typedIdentifiers()?.typedIdentifier()?.forEach { field ->
            val fieldName = field.variableIdentifier()?.text ?: return@forEach
            val fieldRange = parsedFile.tokenToRange(field)
            val fieldSelectionRange = field.variableIdentifier()?.let { parsedFile.tokenToRange(it) } ?: fieldRange
            children.add(DocumentSymbol(fieldName, SymbolKind.Field, fieldRange, fieldSelectionRange))
        }

        if (children.isNotEmpty()) {
            symbol.children = children
        }

        return symbol
    }

    private fun extractUnionSymbol(parsedFile: ParsedFile, ctx: NplParser.UnionDeclContext): DocumentSymbol {
        val name = ctx.unionIdentifier()?.text ?: "<anonymous>"
        val range = parsedFile.tokenToRange(ctx)
        val selectionRange = ctx.unionIdentifier()?.let { parsedFile.tokenToRange(it) } ?: range

        return DocumentSymbol(name, SymbolKind.Enum, range, selectionRange)
    }

    private fun extractEnumSymbol(parsedFile: ParsedFile, ctx: NplParser.EnumDeclContext): DocumentSymbol {
        val name = ctx.enumIdentifier()?.text ?: "<anonymous>"
        val range = parsedFile.tokenToRange(ctx)
        val selectionRange = ctx.enumIdentifier()?.let { parsedFile.tokenToRange(it) } ?: range

        val symbol = DocumentSymbol(name, SymbolKind.Enum, range, selectionRange)

        // Add enum variants as children
        val children = mutableListOf<DocumentSymbol>()
        ctx.enumVariantIdentifiers()?.enumVariantIdentifier()?.forEach { variant ->
            val variantName = variant.text
            val variantRange = parsedFile.tokenToRange(variant)
            children.add(DocumentSymbol(variantName, SymbolKind.EnumMember, variantRange, variantRange))
        }

        if (children.isNotEmpty()) {
            symbol.children = children
        }

        return symbol
    }

    private fun extractNotificationSymbol(parsedFile: ParsedFile, ctx: NplParser.NotificationDeclContext): DocumentSymbol {
        val name = ctx.notificationIdentifier()?.text ?: "<anonymous>"
        val range = parsedFile.tokenToRange(ctx)
        val selectionRange = ctx.notificationIdentifier()?.let { parsedFile.tokenToRange(it) } ?: range

        return DocumentSymbol(name, SymbolKind.Event, range, selectionRange)
    }

    private fun extractConstSymbol(parsedFile: ParsedFile, ctx: NplParser.ConstDeclContext): DocumentSymbol {
        val name = ctx.optionallyTypedIdentifier()?.variableIdentifier()?.text ?: "<anonymous>"
        val range = parsedFile.tokenToRange(ctx)
        val selectionRange = ctx.optionallyTypedIdentifier()?.variableIdentifier()?.let { parsedFile.tokenToRange(it) } ?: range

        return DocumentSymbol(name, SymbolKind.Constant, range, selectionRange)
    }
}

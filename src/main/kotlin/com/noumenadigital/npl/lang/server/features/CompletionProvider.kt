package com.noumenadigital.npl.lang.server.features

import com.noumenadigital.npl.grammar.NplParser
import com.noumenadigital.npl.lang.server.ast.ParsedFile
import org.antlr.v4.runtime.ParserRuleContext
import org.eclipse.lsp4j.CompletionItem
import org.eclipse.lsp4j.CompletionItemKind
import org.eclipse.lsp4j.InsertTextFormat
import org.eclipse.lsp4j.Position

/**
 * Provides code completion for NPL files.
 * Based on the IntelliJ plugin's completion contributors.
 */
object CompletionProvider {

    // NPL keywords
    private val KEYWORDS = listOf(
        "package", "use",
        "struct", "union", "enum",
        "protocol", "function", "native", "notification",
        "permission", "obligation",
        "state", "init", "observers",
        "var", "const",
        "if", "else", "match", "for", "in",
        "return", "become", "notify",
        "this", "select",
        "before", "after", "between", "and",
        "require", "returns", "states",
        "private", "identifier", "symbol",
        "true", "false",
    )

    // Built-in types
    private val BUILTIN_TYPES = listOf(
        "Number", "Text", "Boolean", "Party",
        "DateTime", "LocalDate", "Duration", "Period",
        "List", "Set", "Map", "Optional",
        "Unit", "Any", "Nothing",
        "Blob",
    )

    fun getCompletions(
        parsedFile: ParsedFile,
        position: Position,
        allParsedFiles: Map<String, ParsedFile>,
    ): List<CompletionItem> {
        val items = mutableListOf<CompletionItem>()

        // Determine context for completion
        val context = determineCompletionContext(parsedFile, position)

        when (context) {
            CompletionContext.TOP_LEVEL -> {
                // Suggest top-level declarations
                items.addAll(topLevelKeywords())
            }
            CompletionContext.TYPE_POSITION -> {
                // Suggest types
                items.addAll(builtinTypes())
                items.addAll(userDefinedTypes(allParsedFiles))
            }
            CompletionContext.EXPRESSION -> {
                // Suggest keywords, functions, variables
                items.addAll(expressionKeywords())
                items.addAll(functionsAndVariables(allParsedFiles))
            }
            CompletionContext.PROTOCOL_BODY -> {
                // Suggest protocol-specific keywords
                items.addAll(protocolBodyKeywords())
            }
            CompletionContext.UNKNOWN -> {
                // Suggest everything
                items.addAll(allKeywords())
                items.addAll(builtinTypes())
                items.addAll(userDefinedTypes(allParsedFiles))
                items.addAll(functionsAndVariables(allParsedFiles))
            }
        }

        return items
    }

    private enum class CompletionContext {
        TOP_LEVEL,
        TYPE_POSITION,
        EXPRESSION,
        PROTOCOL_BODY,
        UNKNOWN
    }

    private fun determineCompletionContext(parsedFile: ParsedFile, position: Position): CompletionContext {
        val node = parsedFile.findNodeAt(position)

        // Walk up to find context
        var current = node as? ParserRuleContext
        while (current != null) {
            when (current) {
                is NplParser.RootContext -> return CompletionContext.TOP_LEVEL
                is NplParser.TypeExprContext -> return CompletionContext.TYPE_POSITION
                is NplParser.ExprContext -> return CompletionContext.EXPRESSION
                is NplParser.ProtocolDeclContext -> return CompletionContext.PROTOCOL_BODY
                is NplParser.BlockContext -> return CompletionContext.EXPRESSION
            }
            current = current.parent as? ParserRuleContext
        }

        return CompletionContext.UNKNOWN
    }

    private fun topLevelKeywords(): List<CompletionItem> {
        return listOf(
            createKeyword("package", "package \$1", "Package declaration"),
            createKeyword("use", "use \$1", "Import statement"),
            createKeyword("struct", "struct \$1 {\n\t\$2\n}", "Struct declaration"),
            createKeyword("union", "union \$1: \$2", "Union declaration"),
            createKeyword("enum", "enum \$1: \$2", "Enum declaration"),
            createKeyword("protocol", "protocol [\$1](\$2) {\n\t\$3\n}", "Protocol declaration"),
            createKeyword("function", "function \$1(\$2): \$3 -> \$4", "Function declaration"),
            createKeyword("notification", "notification \$1(\$2)", "Notification declaration"),
            createKeyword("const", "const \$1 = \$2", "Constant declaration"),
        )
    }

    private fun protocolBodyKeywords(): List<CompletionItem> {
        return listOf(
            createKeyword("init", "init {\n\t\$1\n}", "Initialization block"),
            createKeyword("state", "state \$1", "State declaration"),
            createKeyword("permission", "permission [\$1](\$2): \$3 {\n\t\$4\n}", "Permission action"),
            createKeyword("obligation", "obligation [\$1](\$2): \$3 {\n\t\$4\n}", "Obligation action"),
            createKeyword("function", "function \$1(\$2): \$3 -> \$4", "Function declaration"),
            createKeyword("var", "var \$1: \$2", "Variable declaration"),
            createKeyword("private", "private ", "Private modifier"),
        )
    }

    private fun expressionKeywords(): List<CompletionItem> {
        return listOf(
            createKeyword("if", "if (\$1) {\n\t\$2\n} else {\n\t\$3\n}", "If expression"),
            createKeyword("match", "match (\$1) {\n\t\$2 -> \$3\n}", "Match expression"),
            createKeyword("for", "for (\$1 in \$2) {\n\t\$3\n}", "For loop"),
            createKeyword("return", "return \$1", "Return statement"),
            createKeyword("var", "var \$1 = \$2", "Variable declaration"),
            createKeyword("this", "this", "Current protocol instance"),
            createKeyword("true", "true", "Boolean true"),
            createKeyword("false", "false", "Boolean false"),
        )
    }

    private fun allKeywords(): List<CompletionItem> {
        return KEYWORDS.map { keyword ->
            CompletionItem(keyword).apply {
                kind = CompletionItemKind.Keyword
            }
        }
    }

    private fun builtinTypes(): List<CompletionItem> {
        return BUILTIN_TYPES.map { type ->
            CompletionItem(type).apply {
                kind = CompletionItemKind.Class
                detail = "Built-in type"
            }
        }
    }

    private fun userDefinedTypes(allParsedFiles: Map<String, ParsedFile>): List<CompletionItem> {
        val items = mutableListOf<CompletionItem>()

        for ((_, file) in allParsedFiles) {
            val tree = file.tree ?: continue

            for (statement in tree.statement()) {
                // Structs
                statement.structDecl()?.let { structDecl ->
                    val name = structDecl.structIdentifier()?.text ?: return@let
                    items.add(CompletionItem(name).apply {
                        kind = CompletionItemKind.Struct
                        detail = "struct"
                    })
                }

                // Unions
                statement.unionDecl()?.let { unionDecl ->
                    val name = unionDecl.unionIdentifier()?.text ?: return@let
                    items.add(CompletionItem(name).apply {
                        kind = CompletionItemKind.Enum
                        detail = "union"
                    })
                }

                // Enums
                statement.enumDecl()?.let { enumDecl ->
                    val name = enumDecl.enumIdentifier()?.text ?: return@let
                    items.add(CompletionItem(name).apply {
                        kind = CompletionItemKind.Enum
                        detail = "enum"
                    })
                }

                // Protocols
                statement.protocolDecl()?.let { protoDecl ->
                    val name = protoDecl.protocolIdentifier()?.text ?: return@let
                    items.add(CompletionItem(name).apply {
                        kind = CompletionItemKind.Class
                        detail = "protocol"
                    })
                }
            }
        }

        return items.distinctBy { it.label }
    }

    private fun functionsAndVariables(allParsedFiles: Map<String, ParsedFile>): List<CompletionItem> {
        val items = mutableListOf<CompletionItem>()

        for ((_, file) in allParsedFiles) {
            val tree = file.tree ?: continue

            for (statement in tree.statement()) {
                // Functions
                statement.functionDecl()?.let { funcDecl ->
                    val name = funcDecl.functionIdentifier()?.text ?: return@let
                    val params = funcDecl.paramList()?.typedIdentifiers()?.typedIdentifier()?.joinToString(", ") { param ->
                        "${param.variableIdentifier()?.text ?: "?"}: ${param.typeExpr()?.text ?: "?"}"
                    } ?: ""
                    val returnType = funcDecl.returnTypeExpr?.text ?: "Unit"

                    items.add(CompletionItem(name).apply {
                        kind = CompletionItemKind.Function
                        detail = "($params): $returnType"
                        insertText = "$name(\$1)"
                        insertTextFormat = InsertTextFormat.Snippet
                    })
                }

                // Notifications
                statement.notificationDecl()?.let { notifDecl ->
                    val name = notifDecl.notificationIdentifier()?.text ?: return@let
                    items.add(CompletionItem(name).apply {
                        kind = CompletionItemKind.Event
                        detail = "notification"
                    })
                }

                // Constants
                statement.constDecl()?.let { constDecl ->
                    val name = constDecl.optionallyTypedIdentifier()?.variableIdentifier()?.text ?: return@let
                    items.add(CompletionItem(name).apply {
                        kind = CompletionItemKind.Constant
                        detail = "const"
                    })
                }
            }
        }

        return items.distinctBy { it.label }
    }

    private fun createKeyword(label: String, snippet: String, documentation: String): CompletionItem {
        return CompletionItem(label).apply {
            kind = CompletionItemKind.Keyword
            insertText = snippet
            insertTextFormat = InsertTextFormat.Snippet
            detail = documentation
        }
    }
}

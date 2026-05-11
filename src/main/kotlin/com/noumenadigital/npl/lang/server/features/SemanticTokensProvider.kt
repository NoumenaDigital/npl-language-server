package com.noumenadigital.npl.lang.server.features

import com.noumenadigital.npl.grammar.NplLexer
import com.noumenadigital.npl.grammar.NplParser
import com.noumenadigital.npl.lang.server.ast.ParsedFile
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ParseTree
import org.eclipse.lsp4j.SemanticTokens
import org.eclipse.lsp4j.SemanticTokensLegend

/**
 * Provides semantic tokens for rich syntax highlighting in NPL files.
 * Based on the IntelliJ plugin's SyntaxHighlighter.kt.
 */
object SemanticTokensProvider {

    // Token types (must match the legend)
    const val TYPE_KEYWORD = 0
    const val TYPE_TYPE = 1
    const val TYPE_CLASS = 2
    const val TYPE_ENUM = 3
    const val TYPE_FUNCTION = 4
    const val TYPE_METHOD = 5
    const val TYPE_PROPERTY = 6
    const val TYPE_VARIABLE = 7
    const val TYPE_PARAMETER = 8
    const val TYPE_STRING = 9
    const val TYPE_NUMBER = 10
    const val TYPE_COMMENT = 11
    const val TYPE_OPERATOR = 12
    const val TYPE_ENUM_MEMBER = 13
    const val TYPE_NAMESPACE = 14
    const val TYPE_EVENT = 15

    // Token modifiers
    const val MOD_DECLARATION = 0
    const val MOD_DEFINITION = 1
    const val MOD_READONLY = 2
    const val MOD_STATIC = 3

    val legend = SemanticTokensLegend(
        listOf(
            "keyword",      // 0
            "type",         // 1
            "class",        // 2
            "enum",         // 3
            "function",     // 4
            "method",       // 5
            "property",     // 6
            "variable",     // 7
            "parameter",    // 8
            "string",       // 9
            "number",       // 10
            "comment",      // 11
            "operator",     // 12
            "enumMember",   // 13
            "namespace",    // 14
            "event",        // 15
        ),
        listOf(
            "declaration",  // 0
            "definition",   // 1
            "readonly",     // 2
            "static",       // 3
        )
    )

    fun getSemanticTokens(parsedFile: ParsedFile): SemanticTokens {
        val tokens = parsedFile.tokens ?: return SemanticTokens(emptyList())
        val tree = parsedFile.tree

        val data = mutableListOf<Int>()
        var prevLine = 0
        var prevChar = 0

        // First pass: lexer-based tokens (keywords, literals, comments)
        val lexerTokens = collectLexerTokens(tokens)

        // Second pass: AST-based tokens (declarations, types, etc.)
        val astTokens = tree?.let { collectAstTokens(it) } ?: emptyList()

        // Merge and sort all tokens by position
        val allTokens = (lexerTokens + astTokens).sortedWith(compareBy({ it.line }, { it.char }))

        // Encode tokens in LSP format (relative positions)
        for (token in allTokens) {
            val deltaLine = token.line - prevLine
            val deltaChar = if (deltaLine == 0) token.char - prevChar else token.char

            data.add(deltaLine)
            data.add(deltaChar)
            data.add(token.length)
            data.add(token.type)
            data.add(token.modifiers)

            prevLine = token.line
            prevChar = token.char
        }

        return SemanticTokens(data)
    }

    private data class SemanticToken(
        val line: Int,
        val char: Int,
        val length: Int,
        val type: Int,
        val modifiers: Int = 0,
    )

    private fun collectLexerTokens(tokens: org.antlr.v4.runtime.CommonTokenStream): List<SemanticToken> {
        val result = mutableListOf<SemanticToken>()

        tokens.fill()
        for (token in tokens.tokens) {
            if (token.channel == Token.HIDDEN_CHANNEL) {
                // Handle comments
                when (token.type) {
                    NplLexer.LINE_COMMENT, NplLexer.BLOCK_COMMENT, NplLexer.DOC_COMMENT -> {
                        result.add(SemanticToken(
                            line = token.line - 1,
                            char = token.charPositionInLine,
                            length = token.text.length,
                            type = TYPE_COMMENT
                        ))
                    }
                }
                continue
            }

            val tokenType = when (token.type) {
                // Keywords
                NplLexer.PACKAGE, NplLexer.USE,
                NplLexer.STRUCT, NplLexer.UNION, NplLexer.ENUM,
                NplLexer.PROTOCOL, NplLexer.FUNCTION, NplLexer.NATIVE,
                NplLexer.PERMISSION, NplLexer.OBLIGATION,
                NplLexer.VAR, NplLexer.CONST,
                NplLexer.IF, NplLexer.ELSE, NplLexer.MATCH, NplLexer.FOR, NplLexer.IN,
                NplLexer.RETURN, NplLexer.BECOME, NplLexer.NOTIFY,
                NplLexer.STATE, NplLexer.INIT,
                NplLexer.THIS,
                NplLexer.BEFORE, NplLexer.AFTER, NplLexer.BETWEEN, NplLexer.AND,
                NplLexer.REQUIRE, NplLexer.RETURNS,
                NplLexer.PRIVATE, NplLexer.NOTIFICATION,
                NplLexer.SYMBOL -> TYPE_KEYWORD

                // Literals
                NplLexer.TEXT_LITERAL -> TYPE_STRING
                NplLexer.NUMBER_LITERAL, NplLexer.TIME_LITERAL -> TYPE_NUMBER
                NplLexer.PARTY_LITERAL -> TYPE_STRING  // Treat party literals as strings

                else -> null
            }

            if (tokenType != null) {
                result.add(SemanticToken(
                    line = token.line - 1,
                    char = token.charPositionInLine,
                    length = token.text.length,
                    type = tokenType
                ))
            }
        }

        return result
    }

    private fun collectAstTokens(tree: NplParser.RootContext): List<SemanticToken> {
        val result = mutableListOf<SemanticToken>()

        // Walk the AST to find declarations and references
        walkTree(tree, result)

        return result
    }

    private fun walkTree(node: ParseTree, result: MutableList<SemanticToken>) {
        when (node) {
            // Package name
            is NplParser.PackageStmtContext -> {
                node.qualifiedName()?.let { qn ->
                    result.add(SemanticToken(
                        line = qn.start.line - 1,
                        char = qn.start.charPositionInLine,
                        length = qn.text.length,
                        type = TYPE_NAMESPACE
                    ))
                }
            }

            // Protocol declaration
            is NplParser.ProtocolIdentifierContext -> {
                val isDeclaration = node.parent is NplParser.ProtocolDeclContext
                result.add(SemanticToken(
                    line = node.start.line - 1,
                    char = node.start.charPositionInLine,
                    length = node.text.length,
                    type = TYPE_CLASS,
                    modifiers = if (isDeclaration) 1 shl MOD_DECLARATION else 0
                ))
            }

            // Function declaration
            is NplParser.FunctionIdentifierContext -> {
                val isDeclaration = node.parent is NplParser.FunctionDeclContext
                result.add(SemanticToken(
                    line = node.start.line - 1,
                    char = node.start.charPositionInLine,
                    length = node.text.length,
                    type = TYPE_FUNCTION,
                    modifiers = if (isDeclaration) 1 shl MOD_DECLARATION else 0
                ))
            }

            // Struct declaration
            is NplParser.StructIdentifierContext -> {
                val isDeclaration = node.parent is NplParser.StructDeclContext
                result.add(SemanticToken(
                    line = node.start.line - 1,
                    char = node.start.charPositionInLine,
                    length = node.text.length,
                    type = TYPE_TYPE,
                    modifiers = if (isDeclaration) 1 shl MOD_DECLARATION else 0
                ))
            }

            // Union declaration
            is NplParser.UnionIdentifierContext -> {
                val isDeclaration = node.parent is NplParser.UnionDeclContext
                result.add(SemanticToken(
                    line = node.start.line - 1,
                    char = node.start.charPositionInLine,
                    length = node.text.length,
                    type = TYPE_TYPE,
                    modifiers = if (isDeclaration) 1 shl MOD_DECLARATION else 0
                ))
            }

            // Enum declaration
            is NplParser.EnumIdentifierContext -> {
                val isDeclaration = node.parent is NplParser.EnumDeclContext
                result.add(SemanticToken(
                    line = node.start.line - 1,
                    char = node.start.charPositionInLine,
                    length = node.text.length,
                    type = TYPE_ENUM,
                    modifiers = if (isDeclaration) 1 shl MOD_DECLARATION else 0
                ))
            }

            // Enum variant
            is NplParser.EnumVariantIdentifierContext -> {
                result.add(SemanticToken(
                    line = node.start.line - 1,
                    char = node.start.charPositionInLine,
                    length = node.text.length,
                    type = TYPE_ENUM_MEMBER,
                    modifiers = 1 shl MOD_READONLY
                ))
            }

            // Notification declaration
            is NplParser.NotificationIdentifierContext -> {
                result.add(SemanticToken(
                    line = node.start.line - 1,
                    char = node.start.charPositionInLine,
                    length = node.text.length,
                    type = TYPE_EVENT,
                    modifiers = 1 shl MOD_DECLARATION
                ))
            }

            // Action identifier (permission/obligation)
            is NplParser.ActionIdentifierContext -> {
                result.add(SemanticToken(
                    line = node.start.line - 1,
                    char = node.start.charPositionInLine,
                    length = node.text.length,
                    type = TYPE_METHOD,
                    modifiers = 1 shl MOD_DECLARATION
                ))
            }

            // State declaration
            is NplParser.StateIdentifierContext -> {
                val isDeclaration = node.parent is NplParser.StateDeclContext
                result.add(SemanticToken(
                    line = node.start.line - 1,
                    char = node.start.charPositionInLine,
                    length = node.text.length,
                    type = TYPE_ENUM_MEMBER,
                    modifiers = if (isDeclaration) 1 shl MOD_DECLARATION else 0
                ))
            }

            // Variable/parameter in typed identifier
            is NplParser.VariableIdentifierContext -> {
                // Determine if this is a parameter or a variable
                val parent = node.parent
                val grandparent = (parent as? ParserRuleContext)?.parent
                val type = when {
                    parent is NplParser.TypedIdentifierContext &&
                        grandparent is NplParser.TypedIdentifiersContext &&
                        grandparent.parent is NplParser.ParamListContext -> TYPE_PARAMETER
                    parent is NplParser.TypedIdentifierContext &&
                        grandparent is NplParser.TypedIdentifiersContext -> TYPE_PROPERTY
                    else -> TYPE_VARIABLE
                }
                result.add(SemanticToken(
                    line = node.start.line - 1,
                    char = node.start.charPositionInLine,
                    length = node.text.length,
                    type = type
                ))
            }

            // Type identifier (generic parameters)
            is NplParser.TypeIdentifierContext -> {
                result.add(SemanticToken(
                    line = node.start.line - 1,
                    char = node.start.charPositionInLine,
                    length = node.text.length,
                    type = TYPE_TYPE,
                    modifiers = 1 shl MOD_DECLARATION
                ))
            }
        }

        // Recurse into children
        if (node is ParserRuleContext) {
            for (i in 0 until node.childCount) {
                walkTree(node.getChild(i), result)
            }
        }
    }
}


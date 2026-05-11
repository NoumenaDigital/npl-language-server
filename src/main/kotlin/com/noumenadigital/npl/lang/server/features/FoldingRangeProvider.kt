package com.noumenadigital.npl.lang.server.features

import com.noumenadigital.npl.grammar.NplLexer
import com.noumenadigital.npl.grammar.NplParser
import com.noumenadigital.npl.lang.server.ast.ParsedFile
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.Token
import org.antlr.v4.runtime.tree.ParseTree
import org.eclipse.lsp4j.FoldingRange
import org.eclipse.lsp4j.FoldingRangeKind

/**
 * Provides folding ranges for NPL files.
 */
object FoldingRangeProvider {

    fun getFoldingRanges(parsedFile: ParsedFile): List<FoldingRange> {
        val tree = parsedFile.tree ?: return emptyList()
        val ranges = mutableListOf<FoldingRange>()

        // Fold use statements (imports)
        foldUseStatements(tree, ranges)

        // Fold comments
        foldComments(parsedFile, ranges)

        // Fold braced blocks
        collectFoldableBlocks(tree, ranges)

        return ranges
    }

    private fun foldUseStatements(root: NplParser.RootContext, ranges: MutableList<FoldingRange>) {
        val useStatements = root.useStmt()
        if (useStatements.size > 1) {
            val first = useStatements.first()
            val last = useStatements.last()

            val startLine = first.start.line - 1
            val endLine = last.stop?.line?.minus(1) ?: startLine

            if (endLine > startLine) {
                ranges.add(FoldingRange(startLine, endLine).apply {
                    kind = FoldingRangeKind.Imports
                })
            }
        }
    }

    /**
     * Folds comments:
     * - Multi-line block comments (/* ... */) and doc comments (/** ... */)
     * - Consecutive line comments (// ...)
     */
    private fun foldComments(parsedFile: ParsedFile, ranges: MutableList<FoldingRange>) {
        val tokens = parsedFile.tokens ?: return
        tokens.fill()

        // Collect all comment tokens
        // Note: Line/block comments are on HIDDEN_CHANNEL, but doc comments are on default channel
        val commentTokens = tokens.tokens.filter { token ->
            when (token.type) {
                NplLexer.LINE_COMMENT, NplLexer.BLOCK_COMMENT -> token.channel == Token.HIDDEN_CHANNEL
                NplLexer.DOC_COMMENT -> true  // Doc comments can be on any channel
                else -> false
            }
        }

        if (commentTokens.isEmpty()) return

        // Process multi-line block and doc comments
        for (token in commentTokens) {
            if (token.type == NplLexer.BLOCK_COMMENT || token.type == NplLexer.DOC_COMMENT) {
                val startLine = token.line - 1
                val text = token.text ?: continue
                val lineCount = text.count { it == '\n' }
                val endLine = startLine + lineCount

                if (endLine > startLine) {
                    ranges.add(FoldingRange(startLine, endLine).apply {
                        kind = FoldingRangeKind.Comment
                    })
                }
            }
        }

        // Group consecutive line comments
        val lineComments = commentTokens.filter { it.type == NplLexer.LINE_COMMENT }
        if (lineComments.isEmpty()) return

        var groupStart: Token? = null
        var groupEnd: Token? = null
        var lastLine = -2

        for (token in lineComments) {
            val currentLine = token.line

            if (currentLine == lastLine + 1) {
                // Continue the group
                groupEnd = token
            } else {
                // End previous group if it spans multiple lines
                if (groupStart != null && groupEnd != null) {
                    val startLine = groupStart.line - 1
                    val endLine = groupEnd.line - 1
                    if (endLine > startLine) {
                        ranges.add(FoldingRange(startLine, endLine).apply {
                            kind = FoldingRangeKind.Comment
                        })
                    }
                }
                // Start new group
                groupStart = token
                groupEnd = token
            }

            lastLine = currentLine
        }

        // Don't forget the last group
        if (groupStart != null && groupEnd != null) {
            val startLine = groupStart.line - 1
            val endLine = groupEnd.line - 1
            if (endLine > startLine) {
                ranges.add(FoldingRange(startLine, endLine).apply {
                    kind = FoldingRangeKind.Comment
                })
            }
        }
    }

    private fun collectFoldableBlocks(node: ParseTree, ranges: MutableList<FoldingRange>) {
        if (node is ParserRuleContext) {
            // Check if this is a foldable construct
            when (node) {
                is NplParser.ProtocolDeclContext -> addBraceFoldingRange(node, ranges)
                is NplParser.FunctionDeclContext -> {
                    // Function may have expression body or block body via expr
                    node.expr()?.let { addBraceFoldingRange(node, ranges) }
                }
                is NplParser.StructDeclContext -> addBraceFoldingRange(node, ranges)
                is NplParser.UnionDeclContext -> addBraceFoldingRange(node, ranges)
                is NplParser.EnumDeclContext -> addBraceFoldingRange(node, ranges)
                is NplParser.ActionBodyContext -> addBraceFoldingRange(node, ranges)
                is NplParser.PermissionDeclContext -> {
                    node.actionBody()?.let { addBraceFoldingRange(it, ranges) }
                }
                is NplParser.ObligationDeclContext -> {
                    node.actionBody()?.let { addBraceFoldingRange(it, ranges) }
                }
                is NplParser.IfExprContext -> {
                    // If expressions may have block-style expressions
                    addBraceFoldingRange(node, ranges)
                }
                is NplParser.MatchExprContext -> addBraceFoldingRange(node, ranges)
                is NplParser.ForStmtContext -> addBraceFoldingRange(node, ranges)
                is NplParser.BlockContext -> addBraceFoldingRange(node, ranges)
            }

            // Recurse into children
            for (i in 0 until node.childCount) {
                collectFoldableBlocks(node.getChild(i), ranges)
            }
        }
    }

    private fun addBraceFoldingRange(ctx: ParserRuleContext, ranges: MutableList<FoldingRange>) {
        val startLine = ctx.start.line - 1
        val endLine = ctx.stop?.line?.minus(1) ?: return

        if (endLine > startLine) {
            ranges.add(FoldingRange(startLine, endLine).apply {
                kind = FoldingRangeKind.Region
            })
        }
    }
}

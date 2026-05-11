package com.noumenadigital.npl.lang.server.ast

import com.noumenadigital.npl.grammar.NplLexer
import com.noumenadigital.npl.grammar.NplParser
import mu.KotlinLogging
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.tree.ParseTree
import org.antlr.v4.runtime.tree.TerminalNode
import org.eclipse.lsp4j.Position
import org.eclipse.lsp4j.Range
import java.net.URI

private val logger = KotlinLogging.logger { }

/**
 * Service for parsing NPL source files and providing AST access for LSP features.
 * This uses the ANTLR grammar directly to provide IDE features independently of the compiler.
 */
class NplAstService {
    private val parsedFiles = mutableMapOf<String, ParsedFile>()

    /**
     * Parse or retrieve cached parse result for a file.
     */
    fun getOrParse(uri: String, content: String): ParsedFile {
        // For now, always re-parse (we can add caching based on content hash later)
        val parsed = parse(uri, content)
        parsedFiles[uri] = parsed
        return parsed
    }

    fun getParsed(uri: String): ParsedFile? = parsedFiles[uri]

    fun remove(uri: String) {
        parsedFiles.remove(uri)
    }

    private fun parse(uri: String, content: String): ParsedFile {
        return try {
            val lexer = NplLexer(CharStreams.fromString(content))
            val tokens = CommonTokenStream(lexer)
            val parser = NplParser(tokens)
            parser.quirksMode = true // Match compiler behavior

            val tree = parser.root()
            ParsedFile(uri, content, tree, tokens)
        } catch (e: Exception) {
            logger.warn(e) { "Failed to parse $uri" }
            ParsedFile(uri, content, null, null)
        }
    }
}

/**
 * Represents a parsed NPL file with its AST.
 */
data class ParsedFile(
    val uri: String,
    val content: String,
    val tree: NplParser.RootContext?,
    val tokens: CommonTokenStream?,
) {
    val lines: List<String> by lazy { content.lines() }

    fun getPackageName(): String? {
        return tree?.packageStmt()?.qualifiedName()?.text
    }

    /**
     * Convert a token position to LSP Range.
     */
    fun tokenToRange(ctx: org.antlr.v4.runtime.ParserRuleContext): Range {
        val startToken = ctx.start
        val stopToken = ctx.stop ?: startToken

        return Range(
            Position(startToken.line - 1, startToken.charPositionInLine),
            Position(stopToken.line - 1, stopToken.charPositionInLine + (stopToken.text?.length ?: 0))
        )
    }

    /**
     * Find the parse tree node at a given position.
     */
    fun findNodeAt(position: Position): ParseTree? {
        if (tree == null || tokens == null) return null

        val line = position.line + 1 // ANTLR is 1-based
        val column = position.character

        return findNodeAtPosition(tree, line, column)
    }

    private fun findNodeAtPosition(node: ParseTree, line: Int, column: Int): ParseTree? {
        if (node is org.antlr.v4.runtime.ParserRuleContext) {
            val start = node.start
            val stop = node.stop ?: return null

            // Check if position is within this node
            val inRange = when {
                line < start.line -> false
                line > stop.line -> false
                line == start.line && column < start.charPositionInLine -> false
                line == stop.line && column > stop.charPositionInLine + (stop.text?.length ?: 0) -> false
                else -> true
            }

            if (!inRange) return null

            // Try to find a more specific child
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                
                // Check if child is a TerminalNode and contains the position
                if (child is TerminalNode) {
                    val token = child.symbol
                    if (token != null) {
                        val tokenLine = token.line
                        val tokenStart = token.charPositionInLine
                        val tokenEnd = tokenStart + (token.text?.length ?: 0)
                        
                        if (line == tokenLine && column >= tokenStart && column <= tokenEnd) {
                            // Return the parent context (node) since DefinitionProvider needs a context
                            return node
                        }
                    }
                } else {
                    val found = findNodeAtPosition(child, line, column)
                    if (found != null) return found
                }
            }

            return node
        }

        return null
    }
}


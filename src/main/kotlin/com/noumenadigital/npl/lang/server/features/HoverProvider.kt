package com.noumenadigital.npl.lang.server.features

import com.noumenadigital.npl.grammar.NplParser
import com.noumenadigital.npl.lang.server.ast.ParsedFile
import org.antlr.v4.runtime.ParserRuleContext
import org.antlr.v4.runtime.tree.ParseTree
import org.eclipse.lsp4j.Hover
import org.eclipse.lsp4j.MarkupContent
import org.eclipse.lsp4j.MarkupKind
import org.eclipse.lsp4j.Position

/**
 * Provides hover information for NPL files.
 * Based on the IntelliJ plugin's DocumentationProvider.kt and SignatureGenerator.kt.
 */
object HoverProvider {

    fun getHover(parsedFile: ParsedFile, position: Position): Hover? {
        val node = parsedFile.findNodeAt(position) ?: return null

        // Find the declaration that contains or defines the hovered element
        val content = generateHoverContent(node, parsedFile) ?: return null

        return Hover(MarkupContent(MarkupKind.MARKDOWN, content))
    }

    private fun generateHoverContent(node: ParseTree, parsedFile: ParsedFile): String? {
        // Walk up to find the most relevant context for hover
        var current: ParseTree? = node
        while (current != null) {
            val content = when (current) {
                is NplParser.FunctionDeclContext -> generateFunctionHover(current)
                is NplParser.ProtocolDeclContext -> generateProtocolHover(current)
                is NplParser.StructDeclContext -> generateStructHover(current)
                is NplParser.UnionDeclContext -> generateUnionHover(current)
                is NplParser.EnumDeclContext -> generateEnumHover(current)
                is NplParser.NotificationDeclContext -> generateNotificationHover(current)
                is NplParser.PermissionDeclContext -> generatePermissionHover(current)
                is NplParser.ObligationDeclContext -> generateObligationHover(current)
                is NplParser.StateDeclContext -> generateStateHover(current)
                is NplParser.ConstDeclContext -> generateConstHover(current)
                is NplParser.TypedIdentifierContext -> generateVariableHover(current)
                is NplParser.OptionallyTypedIdentifierContext -> generateOptionalVariableHover(current)
                // Identifiers - try to find what they reference
                is NplParser.FunctionIdentifierContext -> generateIdentifierHover("function", current.text)
                is NplParser.ProtocolIdentifierContext -> generateIdentifierHover("protocol", current.text)
                is NplParser.StructIdentifierContext -> generateIdentifierHover("struct", current.text)
                is NplParser.UnionIdentifierContext -> generateIdentifierHover("union", current.text)
                is NplParser.EnumIdentifierContext -> generateIdentifierHover("enum", current.text)
                is NplParser.VariableIdentifierContext -> {
                    // Try to get type from parent context
                    val parent = current.parent
                    when (parent) {
                        is NplParser.TypedIdentifierContext -> generateVariableHover(parent)
                        is NplParser.OptionallyTypedIdentifierContext -> generateOptionalVariableHover(parent)
                        else -> null
                    }
                }
                else -> null
            }
            if (content != null) return content
            current = (current as? ParserRuleContext)?.parent
        }
        return null
    }

    private fun generateFunctionHover(ctx: NplParser.FunctionDeclContext): String {
        val name = ctx.functionIdentifier()?.text ?: "?"
        val receiver = ctx.receiverTypeExpr?.text?.let { "$it." } ?: ""
        val params = ctx.paramList()?.typedIdentifiers()?.typedIdentifier()?.joinToString(", ") { param ->
            "${param.variableIdentifier()?.text ?: "?"}: ${param.typeExpr()?.text ?: "?"}"
        } ?: ""
        val returnType = ctx.returnTypeExpr?.text ?: "Unit"
        val isNative = ctx.NATIVE() != null

        val signature = buildString {
            if (isNative) append("native ")
            append("function $receiver**$name**($params): $returnType")
        }

        val docComment = ctx.docComment()?.text?.let { extractDocComment(it) }

        return buildHoverContent(signature, docComment)
    }

    private fun generateProtocolHover(ctx: NplParser.ProtocolDeclContext): String {
        val name = ctx.protocolIdentifier()?.text ?: "?"
        val parties = ctx.partyList()?.party()?.joinToString(", ") { it.text } ?: ""
        val params = ctx.memberTypedIdentifiers()?.memberTypedIdentifier()?.joinToString(", ") { member ->
            val typedId = member.typedIdentifier()
            "${typedId?.variableIdentifier()?.text ?: "?"}: ${typedId?.typeExpr()?.text ?: "?"}"
        } ?: ""

        val signature = "protocol **$name**[$parties]($params)"
        val docComment = ctx.docComment()?.text?.let { extractDocComment(it) }

        return buildHoverContent(signature, docComment)
    }

    private fun generateStructHover(ctx: NplParser.StructDeclContext): String {
        val name = ctx.structIdentifier()?.text ?: "?"
        val typeParams = ctx.templateParameters()?.typeIdentifier()?.joinToString(", ") { it.text }
            ?.let { "<$it>" } ?: ""
        val fields = ctx.typedIdentifiers()?.typedIdentifier()?.joinToString(",\n  ") { field ->
            "${field.variableIdentifier()?.text ?: "?"}: ${field.typeExpr()?.text ?: "?"}"
        }?.let { "\n  $it\n" } ?: ""

        val signature = "struct **$name$typeParams** {$fields}"
        val docComment = ctx.docComment()?.text?.let { extractDocComment(it) }

        return buildHoverContent(signature, docComment)
    }

    private fun generateUnionHover(ctx: NplParser.UnionDeclContext): String {
        val name = ctx.unionIdentifier()?.text ?: "?"
        val typeParams = ctx.templateParameters()?.typeIdentifier()?.joinToString(", ") { it.text }
            ?.let { "<$it>" } ?: ""
        val types = ctx.typeExprList()?.typeExpr()?.joinToString(" | ") { it.text } ?: ""

        val signature = "union **$name$typeParams**: $types"
        val docComment = ctx.docComment()?.text?.let { extractDocComment(it) }

        return buildHoverContent(signature, docComment)
    }

    private fun generateEnumHover(ctx: NplParser.EnumDeclContext): String {
        val name = ctx.enumIdentifier()?.text ?: "?"
        val variants = ctx.enumVariantIdentifiers()?.enumVariantIdentifier()?.joinToString(" | ") { it.text } ?: ""

        val signature = "enum **$name**: $variants"
        val docComment = ctx.docComment()?.text?.let { extractDocComment(it) }

        return buildHoverContent(signature, docComment)
    }

    private fun generateNotificationHover(ctx: NplParser.NotificationDeclContext): String {
        val name = ctx.notificationIdentifier()?.text ?: "?"
        val params = ctx.paramList()?.typedIdentifiers()?.typedIdentifier()?.joinToString(", ") { param ->
            "${param.variableIdentifier()?.text ?: "?"}: ${param.typeExpr()?.text ?: "?"}"
        } ?: ""

        val signature = "notification **$name**($params)"
        val docComment = ctx.docComment()?.text?.let { extractDocComment(it) }

        return buildHoverContent(signature, docComment)
    }

    private fun generatePermissionHover(ctx: NplParser.PermissionDeclContext): String {
        val head = ctx.actionHead()
        val name = head?.actionIdentifier()?.text ?: "?"
        val parties = head?.actionPartySpecifier()?.party()?.joinToString(", ") { it.text } ?: ""
        val params = head?.actionParams()?.typedIdentifiers()?.typedIdentifier()?.joinToString(", ") { param ->
            "${param.variableIdentifier()?.text ?: "?"}: ${param.typeExpr()?.text ?: "?"}"
        } ?: ""
        val returnType = ctx.actionReturns()?.typeExpr()?.text ?: "Unit"

        val signature = "permission **$name**[$parties]($params): $returnType"
        val docComment = ctx.docComment()?.text?.let { extractDocComment(it) }

        return buildHoverContent(signature, docComment)
    }

    private fun generateObligationHover(ctx: NplParser.ObligationDeclContext): String {
        val head = ctx.actionHead()
        val name = head?.actionIdentifier()?.text ?: "?"
        val parties = head?.actionPartySpecifier()?.party()?.joinToString(", ") { it.text } ?: ""
        val params = head?.actionParams()?.typedIdentifiers()?.typedIdentifier()?.joinToString(", ") { param ->
            "${param.variableIdentifier()?.text ?: "?"}: ${param.typeExpr()?.text ?: "?"}"
        } ?: ""
        val returnType = ctx.actionReturns()?.typeExpr()?.text ?: "Unit"

        val signature = "obligation **$name**[$parties]($params): $returnType"
        val docComment = ctx.docComment()?.text?.let { extractDocComment(it) }

        return buildHoverContent(signature, docComment)
    }

    private fun generateStateHover(ctx: NplParser.StateDeclContext): String {
        val name = ctx.stateIdentifier()?.text ?: "?"
        return "state **$name**"
    }

    private fun generateConstHover(ctx: NplParser.ConstDeclContext): String {
        val ident = ctx.optionallyTypedIdentifier()
        val name = ident?.variableIdentifier()?.text ?: "?"
        val type = ident?.typeExpr()?.text ?: "inferred"
        val value = ctx.constantLiteral()?.text ?: "?"

        return "const **$name**: $type = $value"
    }

    private fun generateVariableHover(ctx: NplParser.TypedIdentifierContext): String {
        val name = ctx.variableIdentifier()?.text ?: "?"
        val type = ctx.typeExpr()?.text ?: "?"
        return "*$type* **$name**"
    }

    private fun generateOptionalVariableHover(ctx: NplParser.OptionallyTypedIdentifierContext): String {
        val name = ctx.variableIdentifier()?.text ?: "?"
        val type = ctx.typeExpr()?.text ?: "inferred"
        val varOrConst = if (ctx.isConst) "const" else "var"
        return "$varOrConst **$name**: $type"
    }

    private fun generateIdentifierHover(kind: String, name: String): String {
        return "$kind **$name**"
    }

    private fun extractDocComment(text: String): String? {
        if (text.isBlank()) return null

        // Remove /** and */ markers and clean up
        val cleaned = text
            .removePrefix("/**")
            .removeSuffix("*/")
            .lines()
            .map { it.trim().removePrefix("*").trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")

        return cleaned.ifBlank { null }
    }

    private fun buildHoverContent(signature: String, docComment: String?): String {
        return buildString {
            append("```npl\n")
            append(signature.replace("**", "")) // Clean markdown for code block
            append("\n```")
            if (docComment != null) {
                append("\n\n---\n\n")
                append(docComment)
            }
        }
    }
}



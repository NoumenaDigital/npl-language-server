package com.noumenadigital.npl.lang.server.util

import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.eclipse.lsp4j.Diagnostic
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.PublishDiagnosticsParams
import org.eclipse.lsp4j.Range
import org.intellij.lang.annotations.Language

object DiagnosticTestUtils {
    data class ExpectedDiagnostic(
        val snippet: String,
        val code: Int,
        val message: String,
        val severity: DiagnosticSeverity,
    )

    fun verifyPublishedDiagnostics(
        diagnostics: List<PublishDiagnosticsParams>,
        @Language("NPL") sourceCode: String,
        expectedDiagnostics: List<ExpectedDiagnostic> = emptyList(),
    ) {
        val actualDiagnostics = diagnostics.flatMap { it.diagnostics }
        verifyDiagnostics(actualDiagnostics, sourceCode, expectedDiagnostics)
    }

    fun verifyDiagnostics(
        diagnostics: List<Diagnostic>,
        @Language("NPL") sourceCode: String,
        expectedDiagnostics: List<ExpectedDiagnostic> = emptyList(),
    ) {
        if (expectedDiagnostics.isEmpty()) {
            diagnostics.shouldBeEmpty()
            return
        }

        val actualDiagnosticDetails =
            diagnostics
                .map { diagnostic ->
                    val snippet = normalizeSnippet(extractSnippetFromRange(sourceCode, diagnostic.range))
                    val code = if (diagnostic.code.isLeft) diagnostic.code.left.toInt() else diagnostic.code.right
                    ExpectedDiagnostic(
                        snippet = snippet,
                        code = code,
                        message = diagnostic.message,
                        severity = diagnostic.severity,
                    )
                }.toSet()

        val expectedDiagnosticSet =
            expectedDiagnostics
                .map { expected ->
                    ExpectedDiagnostic(
                        snippet = normalizeSnippet(expected.snippet),
                        code = expected.code,
                        message = expected.message,
                        severity = expected.severity,
                    )
                }.toSet()

        actualDiagnosticDetails shouldBe expectedDiagnosticSet
    }

    private fun normalizeSnippet(snippet: String): String {
        val lines = snippet.lines().filter { it.trim().isNotEmpty() }
        return if (lines.size > 1) {
            lines.first().trim()
        } else {
            snippet.trim()
        }
    }

    private fun extractSnippetFromRange(
        sourceCode: String,
        range: Range,
    ): String {
        val lines = sourceCode.lines()

        val startLine = range.start.line.coerceIn(0, lines.size - 1)
        val endLine = range.end.line.coerceIn(0, lines.size - 1)

        if (startLine == endLine) {
            val line = lines[startLine]
            val startChar = range.start.character.coerceIn(0, line.length)
            val endChar = range.end.character.coerceIn(startChar, line.length)
            return line.substring(startChar, endChar)
        }

        val result = StringBuilder()

        val firstLine = lines[startLine]
        val startChar = range.start.character.coerceIn(0, firstLine.length)
        result.append(firstLine.substring(startChar))
        result.append("\n")

        for (i in startLine + 1 until endLine) {
            result.append(lines[i])
            result.append("\n")
        }

        val lastLine = lines[endLine]
        val endChar = range.end.character.coerceIn(0, lastLine.length)
        result.append(lastLine.substring(0, endChar))

        return result.toString()
    }
}

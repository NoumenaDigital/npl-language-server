package com.noumenadigital.npl.lang.server.compilation

import com.noumenadigital.npl.lang.server.LanguageClientProvider
import com.noumenadigital.npl.lang.server.util.DiagnosticTestUtils
import com.noumenadigital.npl.lang.server.util.DiagnosticTestUtils.ExpectedDiagnostic
import com.noumenadigital.npl.lang.server.util.NplFileFixtures.withNplTestFile
import com.noumenadigital.npl.lang.server.util.TestLanguageClient
import com.noumenadigital.npl.lang.server.util.UriFixtures.withNplTestFile
import io.kotest.core.spec.style.FunSpec
import org.eclipse.lsp4j.DiagnosticSeverity
import org.intellij.lang.annotations.Language

class CompilerServiceTest :
    FunSpec({
        val waitTimeMs = 500L

        data class TestScenario(
            @Language("NPL") val code: String,
            val expectedDiagnostics: List<ExpectedDiagnostic> = emptyList(),
        )

        fun runDiagnosticTest(scenario: TestScenario) {
            val expectedDiagnosticsCount = scenario.expectedDiagnostics.size

            withTestService(expectedDiagnosticsCount.coerceAtLeast(1)) { service, testClient ->
                withNplTestFile(scenario.code, service, testClient, waitTimeMs)

                DiagnosticTestUtils.verifyPublishedDiagnostics(
                    diagnostics = testClient.diagnostics,
                    sourceCode = scenario.code,
                    expectedDiagnostics = scenario.expectedDiagnostics,
                )
            }
        }

        context("Syntax error diagnostics") {
            test("invalid package name") {
                val scenario =
                    TestScenario(
                        code =
                            """
                            package 123test
                            """.trimIndent(),
                        expectedDiagnostics =
                            listOf(
                                ExpectedDiagnostic(
                                    snippet = "123",
                                    code = 1,
                                    message = "Syntax error: extraneous input '123' expecting IDENTIFIER",
                                    severity = DiagnosticSeverity.Error,
                                ),
                            ),
                    )
                runDiagnosticTest(scenario)
            }

            test("something where an exception is thrown rather than collected") {
                val scenario =
                    TestScenario(
                        code =
                            """
                            package test

                            function doIt() -> getNumber();
                            function getNumber() -> doIt();
                            """.trimIndent(),
                        expectedDiagnostics =
                            listOf(
                                ExpectedDiagnostic(
                                    snippet = "function doIt() -> getNumber();",
                                    code = 89,
                                    message =
                                        "The compiler cannot (currently) automatically resolve the type " +
                                            "for '/test/doIt'. Please add explicit types.",
                                    severity = DiagnosticSeverity.Error,
                                ),
                            ),
                    )
                runDiagnosticTest(scenario)
            }

            test("no diagnostics for valid code") {
                val scenario =
                    TestScenario(
                        code =
                            """
                            package test

                            struct TimestampedAmount {
                                amount: Number,
                                timestamp: DateTime
                            }

                            function total(entries: List<TimestampedAmount>) returns Number -> {
                                return entries.map(function(p: TimestampedAmount) returns Number -> p.amount).sum();
                            }
                            """.trimIndent(),
                    )

                withTestService { service, testClient ->
                    withNplTestFile(scenario.code, service, testClient, waitTimeMs)

                    DiagnosticTestUtils.verifyPublishedDiagnostics(
                        diagnostics = testClient.diagnostics,
                        sourceCode = scenario.code,
                    )
                }
            }

            test("multiple errors in one file") {
                val scenario =
                    TestScenario(
                        code =
                            """
                            package test

                            struct MyStruct {
                                foo: InvalidType
                            }

                            function foo() returns Number -> {}

                            """.trimIndent(),
                        expectedDiagnostics =
                            listOf(
                                ExpectedDiagnostic(
                                    snippet = "InvalidType\n}",
                                    code = 2,
                                    message = "Unknown 'InvalidType'",
                                    severity = DiagnosticSeverity.Error,
                                ),
                                ExpectedDiagnostic(
                                    snippet = "function foo() returns Number -> {}",
                                    code = 15,
                                    message = "Missing return statement",
                                    severity = DiagnosticSeverity.Error,
                                ),
                            ),
                    )
                runDiagnosticTest(scenario)
            }

            test("multiple warnings in one file") {
                val scenario =
                    TestScenario(
                        code =
                            """
                            package test

                            function foo() returns Number -> {
                                var unused1 = 42;
                                var unused2 = 42;

                                return 42;
                            }

                            """.trimIndent(),
                        expectedDiagnostics =
                            listOf(
                                ExpectedDiagnostic(
                                    snippet = "var unused1 = 42;",
                                    code = 16,
                                    message = "Declared variable `unused1` unused",
                                    severity = DiagnosticSeverity.Warning,
                                ),
                                ExpectedDiagnostic(
                                    snippet = "var unused2 = 42;",
                                    code = 16,
                                    message = "Declared variable `unused2` unused",
                                    severity = DiagnosticSeverity.Warning,
                                ),
                            ),
                    )
                runDiagnosticTest(scenario)
            }

            test("both errors and warnings in same file") {
                val scenario =
                    TestScenario(
                        code =
                            """
                            package test

                            protocol[p] Foo() {
                                var x = 5;

                                permission[p] foo(y: Invalid) {};
                            };
                            """.trimIndent(),
                        expectedDiagnostics =
                            listOf(
                                ExpectedDiagnostic(
                                    snippet = "Invalid) {};",
                                    code = 2,
                                    message = "Unknown 'Invalid'",
                                    severity = DiagnosticSeverity.Error,
                                ),
                                ExpectedDiagnostic(
                                    snippet = "var x = 5;",
                                    code = 19,
                                    message = "Public property `x` should be explicitly typed.",
                                    severity = DiagnosticSeverity.Warning,
                                ),
                            ),
                    )
                runDiagnosticTest(scenario)
            }
        }
    })

private fun withTestService(
    expectedDiagnosticsCount: Int = 1,
    test: (CompilerService, TestLanguageClient) -> Unit,
) {
    val testClient = TestLanguageClient(expectedDiagnosticsCount)
    val clientProvider = LanguageClientProvider().apply { client = testClient }
    val service = DefaultCompilerService(clientProvider)

    test(service, testClient)
}

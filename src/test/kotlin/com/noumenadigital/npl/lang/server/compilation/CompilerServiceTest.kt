package com.noumenadigital.npl.lang.server.compilation

import com.noumenadigital.npl.lang.server.LanguageClientProvider
import com.noumenadigital.npl.lang.server.util.DiagnosticTestUtils
import com.noumenadigital.npl.lang.server.util.DiagnosticTestUtils.ExpectedDiagnostic
import com.noumenadigital.npl.lang.server.util.NplFileFixtures.createNplFile
import com.noumenadigital.npl.lang.server.util.NplFileFixtures.simpleValidCode
import com.noumenadigital.npl.lang.server.util.NplFileFixtures.validCodeWithError
import com.noumenadigital.npl.lang.server.util.NplFileFixtures.withNplTestFile
import com.noumenadigital.npl.lang.server.util.NplFileFixtures.withTempDirectory
import com.noumenadigital.npl.lang.server.util.TestLanguageClient
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.eclipse.lsp4j.DiagnosticSeverity
import org.intellij.lang.annotations.Language
import java.nio.file.Files
import java.util.concurrent.TimeUnit

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

        context("Workspace folder validation") {
            test("Files inside workspace should be compiled") {
                withTempDirectory("workspace-test") { workspaceDir ->
                    val testClient = TestLanguageClient(expectedDiagnosticsCount = 1)
                    val clientProvider = LanguageClientProvider().apply { client = testClient }
                    val service = DefaultCompilerService(clientProvider)

                    val workspaceUri = workspaceDir.toUri().toString()
                    service.preloadSources(listOf(workspaceUri))

                    val fileUri = createNplFile(workspaceDir, "Valid.npl", validCodeWithError()).toUri().toString()

                    testClient.expectDiagnostics()
                    service.updateSource(fileUri, validCodeWithError())

                    testClient.waitForDiagnostics(5, TimeUnit.SECONDS) shouldBe true
                    testClient.diagnostics.flatMap { it.diagnostics }.isNotEmpty() shouldBe true
                }
            }

            test("Files outside workspace should be ignored") {
                withTempDirectory("workspace-test") { workspaceDir ->
                    withTempDirectory("outside-dir") { outsideDir ->
                        val testClient = TestLanguageClient(expectedDiagnosticsCount = 1)
                        val clientProvider = LanguageClientProvider().apply { client = testClient }
                        val service = DefaultCompilerService(clientProvider)

                        service.preloadSources(listOf(workspaceDir.toUri().toString()))

                        val outsideFileUri =
                            createNplFile(outsideDir, "Outside.npl", validCodeWithError())
                                .toUri()
                                .toString()

                        service.updateSource(outsideFileUri, validCodeWithError())

                        Thread.sleep(200)
                        testClient.diagnostics.isEmpty() shouldBe true
                    }
                }
            }

            test("Files that move outside workspace should have diagnostics cleared") {
                withTempDirectory("workspace-test") { workspaceDir ->
                    withTempDirectory("outside-dir") { outsideDir ->
                        val testClient = TestLanguageClient(expectedDiagnosticsCount = 0)
                        val clientProvider = LanguageClientProvider().apply { client = testClient }
                        val service = DefaultCompilerService(clientProvider)

                        // Initial setup with file in workspace
                        service.preloadSources(listOf(workspaceDir.toUri().toString()))
                        val fileUri =
                            createNplFile(workspaceDir, "Moving.npl", validCodeWithError())
                                .toUri()
                                .toString()
                        service.updateSource(fileUri, validCodeWithError())

                        // Change workspace, making the file effectively outside
                        service.preloadSources(listOf(outsideDir.toUri().toString()))

                        // Verify empty diagnostics were published
                        Thread.sleep(200)
                        testClient.diagnostics.any { it.uri == fileUri && it.diagnostics.isEmpty() } shouldBe true
                    }
                }
            }

            test("Only files in workspace should be preloaded") {
                withTempDirectory("workspace-test") { workspaceDir ->
                    withTempDirectory("outside-dir") { outsideDir ->
                        // Setup test files
                        createNplFile(workspaceDir, "Inside1.npl", simpleValidCode())
                        createNplFile(workspaceDir, "Inside2.npl", simpleValidCode())
                        createNplFile(
                            workspaceDir.resolve("nested").also { Files.createDirectories(it) },
                            "Nested.npl",
                            simpleValidCode(),
                        )

                        createNplFile(outsideDir, "Outside1.npl", validCodeWithError())
                        createNplFile(outsideDir, "Outside2.npl", validCodeWithError())

                        // Track preloaded sources count
                        var preloadedSources = 0
                        val service = DefaultCompilerService(LanguageClientProvider())
                        val field = DefaultCompilerService::class.java.getDeclaredField("sources")
                        field.isAccessible = true

                        service.preloadSources(listOf(workspaceDir.toUri().toString()))
                        preloadedSources = (field.get(service) as Map<*, *>).size

                        // Should only load the 3 files inside workspace
                        preloadedSources shouldBe 3
                    }
                }
            }

            test("Files from multiple workspaces should be loaded") {
                withTempDirectory("workspace-test-1") { workspaceDir1 ->
                    withTempDirectory("workspace-test-2") { workspaceDir2 ->
                        withTempDirectory("outside-dir") { outsideDir ->
                            // Setup test files
                            createNplFile(workspaceDir1, "Inside1.npl", simpleValidCode())
                            createNplFile(workspaceDir2, "Inside2.npl", simpleValidCode())
                            createNplFile(outsideDir, "Outside.npl", simpleValidCode())

                            // Track preloaded sources count
                            val service = DefaultCompilerService(LanguageClientProvider())
                            val sourcesField = DefaultCompilerService::class.java.getDeclaredField("sources")
                            sourcesField.isAccessible = true

                            // Configure service with multiple workspaces
                            service.preloadSources(
                                listOf(
                                    workspaceDir1.toUri().toString(),
                                    workspaceDir2.toUri().toString(),
                                ),
                            )

                            // Get loaded sources
                            val sources = sourcesField.get(service) as Map<*, *>

                            // Should load files from both workspaces but not outside
                            sources.size shouldBe 2

                            // Verify the sources contain files from both workspaces
                            val sourceUris = sources.keys.map { it.toString() }
                            sourceUris.any { it.contains(workspaceDir1.fileName.toString()) } shouldBe true
                            sourceUris.any { it.contains(workspaceDir2.fileName.toString()) } shouldBe true
                            sourceUris.any { it.contains(outsideDir.fileName.toString()) } shouldBe false
                        }
                    }
                }
            }

            test("Test sources from initialization options should be loaded") {
                withTempDirectory("workspace-test") { workspaceDir ->
                    withTempDirectory("test-sources") { testDir ->
                        // Setup test files
                        createNplFile(workspaceDir, "Main.npl", simpleValidCode())
                        createNplFile(testDir, "Test.npl", validCodeWithError())

                        // Track preloaded sources count
                        val service = DefaultCompilerService(LanguageClientProvider())
                        val sourcesField = DefaultCompilerService::class.java.getDeclaredField("sources")
                        sourcesField.isAccessible = true

                        // Configure service with workspace and test sources
                        service.preloadSources(
                            listOf(
                                workspaceDir.toUri().toString(),
                                testDir.toUri().toString(),
                            ),
                        )

                        // Get loaded sources
                        val sources = sourcesField.get(service) as Map<*, *>

                        // Should load files from both directories
                        sources.size shouldBe 2

                        // Verify the sources contain files from both directories
                        val sourceUris = sources.keys.map { it.toString() }
                        sourceUris.any { it.contains(workspaceDir.fileName.toString()) } shouldBe true
                        sourceUris.any { it.contains(testDir.fileName.toString()) } shouldBe true
                    }
                }
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

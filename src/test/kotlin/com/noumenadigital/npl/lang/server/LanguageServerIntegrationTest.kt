package com.noumenadigital.npl.lang.server

import com.noumenadigital.npl.lang.server.compilation.CompilerService
import com.noumenadigital.npl.lang.server.compilation.DefaultCompilerService
import com.noumenadigital.npl.lang.server.util.DiagnosticTestUtils
import com.noumenadigital.npl.lang.server.util.LanguageServerFixtures.createTestServer
import com.noumenadigital.npl.lang.server.util.LanguageServerFixtures.withLanguageServer
import com.noumenadigital.npl.lang.server.util.SafeSystemExitHandler
import com.noumenadigital.npl.lang.server.util.TestLanguageClient
import com.noumenadigital.npl.lang.server.util.UriFixtures.normalizeUri
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.eclipse.lsp4j.ClientCapabilities
import org.eclipse.lsp4j.ClientInfo
import org.eclipse.lsp4j.DiagnosticSeverity
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.TextDocumentClientCapabilities
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.services.LanguageClientAware
import org.intellij.lang.annotations.Language
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

class LanguageServerIntegrationTest : FunSpec() {
    private val timeoutSeconds = 10L

    init {
        context("Server lifecycle") {
            test("initializes with correct capabilities") {
                withLanguageServer { client ->
                    val result = client.initialize().get(timeoutSeconds, TimeUnit.SECONDS)
                    result.capabilities.textDocumentSync.left shouldNotBe null
                }
            }

            test("preloads NPL sources from workspace") {
                withTempDirectory("npl-test") { tempDir ->
                    createNplFile(tempDir, "Test1.npl", "package test")
                    createNplFile(tempDir, "Test2.npl", "package test")

                    val nestedDir = tempDir.resolve("nested")
                    Files.createDirectory(nestedDir)
                    createNplFile(nestedDir, "Test3.npl", "package testx")

                    val clientProvider = LanguageClientProvider()
                    val client = TestLanguageClient()
                    clientProvider.client = client

                    val compilerServiceSpy = CompilerServiceSpy(
                        DefaultCompilerService(clientProvider),
                    )

                    val exitHandler = SafeSystemExitHandler()
                    val server = createTestServer(
                        systemExitHandler = exitHandler,
                        clientProvider = clientProvider,
                        compilerServiceFactory = { compilerServiceSpy },
                    )

                    (server as LanguageClientAware).connect(client)
                    client.connect(server)

                    val workspaceUri = tempDir.toUri().toString()
                    client.initialize(createParams(workspaceUri)).get(timeoutSeconds, TimeUnit.SECONDS)

                    compilerServiceSpy.preloadSourcesCalled shouldBe true
                    compilerServiceSpy.preloadedUri shouldBe workspaceUri

                    client.shutdown().get(5, TimeUnit.SECONDS)
                    client.exit()
                    exitHandler.exitCalled shouldBe true
                    exitHandler.statusCode shouldBe 0
                }
            }
        }

        context("Document diagnostics") {
            test("reports errors for invalid code on document open") {
                withLanguageServer { client ->
                    val uri = "file:///test/Test.npl"
                    val normalizedUri = normalizeUri(uri)
                    val invalidCode = "package 123test"

                    client.expectDiagnostics()
                    client.openDocument(uri, invalidCode)

                    client.waitForDiagnostics(timeoutSeconds, TimeUnit.SECONDS) shouldBe true
                    client.hasDiagnosticsForUri(normalizedUri) shouldBe true

                    client.verifyDiagnosticsContain(
                        normalizedUri,
                        invalidCode,
                        listOf(
                            DiagnosticTestUtils.ExpectedDiagnostic(
                                snippet = "123",
                                code = 1,
                                message = "Syntax error: extraneous input '123' expecting IDENTIFIER",
                                severity = DiagnosticSeverity.Error,
                            ),
                        ),
                    )
                }
            }

            test("reports errors when changing valid code to invalid") {
                withLanguageServer { client ->
                    val uri = "file:///test/ChangeTest.npl"
                    val normalizedUri = normalizeUri(uri)
                    val validCode = "package test"
                    val invalidCode = "package 123test"

                    client.openDocument(uri, validCode)

                    client.clearDiagnostics()
                    client.expectDiagnostics()
                    client.changeDocument(uri, invalidCode)

                    client.waitForDiagnostics(timeoutSeconds, TimeUnit.SECONDS) shouldBe true
                    client.hasDiagnosticsForUri(normalizedUri) shouldBe true

                    client.verifyDiagnosticsContain(
                        normalizedUri,
                        invalidCode,
                        listOf(
                            DiagnosticTestUtils.ExpectedDiagnostic(
                                snippet = "123",
                                code = 1,
                                message = "Syntax error: extraneous input '123' expecting IDENTIFIER",
                                severity = DiagnosticSeverity.Error,
                            ),
                        ),
                    )
                }
            }

            test("reports errors when a referenced type is renamed in another file") {
                withLanguageServer { client ->
                    val structFileUri = "file:///test/bar/Bar.npl"
                    val structCode = """
                        package bar

                        struct Bar {
                            amount: Number
                        }
                    """.trimIndent()

                    val usageFileUri = "file:///test/foo/Foo.npl"
                    val normalizedUsageUri = normalizeUri(usageFileUri)
                    val usageCode = """
                        package foo

                        use bar.Bar

                        function foo() -> Bar(1000);
                    """.trimIndent()

                    client.openDocument(structFileUri, structCode)
                    client.openDocument(usageFileUri, usageCode)

                    client.clearDiagnostics()

                    val updatedStructCode = """
                        package bar

                        struct BarX {
                            amount: Number
                        }
                    """.trimIndent()

                    client.expectDiagnostics()
                    client.changeDocument(structFileUri, updatedStructCode)

                    client.waitForDiagnostics(timeoutSeconds, TimeUnit.SECONDS) shouldBe true
                    client.hasDiagnosticsForUri(normalizedUsageUri) shouldBe true

                    client.verifyDiagnosticsContain(
                        normalizedUsageUri,
                        usageCode,
                        listOf(
                            DiagnosticTestUtils.ExpectedDiagnostic(
                                snippet = "use bar.Bar",
                                code = 62,
                                message = "Unresolved import 'bar.Bar'",
                                severity = DiagnosticSeverity.Error,
                            ),
                        ),
                    )
                }
            }
        }

        test("custom compiler service can be provided") {
            val mockService = object : CompilerService {
                override fun updateSource(uri: String, content: String) {}
                override fun preloadSources(nplRootUri: String) {}
            }

            val server = createTestServer(
                compilerServiceFactory = { mockService },
            )

            server shouldNotBe null
        }
    }

    private fun <T> withTempDirectory(prefix: String, block: (Path) -> T): T {
        val tempDir = Files.createTempDirectory(prefix)
        try {
            return block(tempDir)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    private fun createNplFile(directory: Path, name: String, @Language("NPL") content: String): Path {
        val file = directory.resolve(name)
        return Files.writeString(file, content)
    }

    private fun createParams(workspaceUri: String? = null) = InitializeParams().apply {
        clientInfo = ClientInfo("Test Client", "1.0.0")
        capabilities = ClientCapabilities().apply {
            textDocument = TextDocumentClientCapabilities()
        }

        if (workspaceUri != null) {
            workspaceFolders = listOf(WorkspaceFolder(workspaceUri, "NPL Test Workspace"))
        }
    }
}

class CompilerServiceSpy(private val delegate: CompilerService) : CompilerService {
    var preloadSourcesCalled = false
    var preloadedUri: String? = null

    override fun updateSource(uri: String, content: String) =
        delegate.updateSource(uri, content)

    override fun preloadSources(nplRootUri: String) {
        preloadSourcesCalled = true
        preloadedUri = nplRootUri
        delegate.preloadSources(nplRootUri)
    }
}

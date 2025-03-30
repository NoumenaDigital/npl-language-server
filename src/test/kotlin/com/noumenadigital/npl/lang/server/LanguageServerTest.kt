package com.noumenadigital.npl.lang.server

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.noumenadigital.npl.lang.server.compilation.CompilerService
import com.noumenadigital.npl.lang.server.logging.TraceManager
import com.noumenadigital.npl.lang.server.util.LanguageServerFixtures.createTestServer
import com.noumenadigital.npl.lang.server.util.SafeSystemExitHandler
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.SetTraceParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.TextDocumentSyncKind
import org.eclipse.lsp4j.TraceValue
import org.eclipse.lsp4j.VersionedTextDocumentIdentifier
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.services.LanguageClient
import org.eclipse.lsp4j.services.LanguageClientAware

class LanguageServerTest :
    FunSpec({
        context("Server lifecycle") {
            test("initialization") {
                val compilerMock = mockk<CompilerService>(relaxed = true)
                val server =
                    createTestServer(
                        compilerServiceFactory = { compilerMock },
                    )

                val workspaceFolder = WorkspaceFolder("file:///test/workspace", "Test")
                val params =
                    InitializeParams().apply {
                        workspaceFolders = listOf(workspaceFolder)
                    }

                val result = server.initialize(params).get()

                result.capabilities.textDocumentSync.left shouldBe TextDocumentSyncKind.Full
                verify { compilerMock.preloadSources(listOf("file:///test/workspace")) }
            }

            test("initialization with effectiveWorkspaceFolders") {
                val compilerMock = mockk<CompilerService>(relaxed = true)
                val server =
                    createTestServer(
                        compilerServiceFactory = { compilerMock },
                    )

                // Create effectiveWorkspaceFolders in initializationOptions
                val effectiveFolder1 =
                    JsonObject().apply {
                        addProperty("uri", "file:///effective/workspace1")
                        addProperty("name", "Effective1")
                    }
                val effectiveFolder2 =
                    JsonObject().apply {
                        addProperty("uri", "file:///effective/workspace2")
                        addProperty("name", "Effective2")
                    }
                val effectiveFolders =
                    JsonArray().apply {
                        add(effectiveFolder1)
                        add(effectiveFolder2)
                    }
                val options =
                    JsonObject().apply {
                        add("effectiveWorkspaceFolders", effectiveFolders)
                    }

                // Create standard workspace folders (which should be ignored)
                val standardFolder = WorkspaceFolder("file:///standard/workspace", "Standard")

                val params =
                    InitializeParams().apply {
                        workspaceFolders = listOf(standardFolder)
                        initializationOptions = options
                    }

                val result = server.initialize(params).get()

                result.capabilities.textDocumentSync.left shouldBe TextDocumentSyncKind.Full
                verify {
                    compilerMock.preloadSources(
                        match<List<String>> { uris ->
                            uris.size == 2 &&
                                uris.contains("file:///effective/workspace1") &&
                                uris.contains("file:///effective/workspace2")
                        },
                    )
                }
            }

            test("initialization with test sources") {
                val compilerMock = mockk<CompilerService>(relaxed = true)
                val server =
                    createTestServer(
                        compilerServiceFactory = { compilerMock },
                    )

                val workspaceFolder = WorkspaceFolder("file:///test/workspace", "Test")
                val testSourcesUri = "file:///test/sources"

                val options =
                    JsonObject().apply {
                        addProperty("testSourcesUri", testSourcesUri)
                    }

                val params =
                    InitializeParams().apply {
                        workspaceFolders = listOf(workspaceFolder)
                        initializationOptions = options
                    }

                val result = server.initialize(params).get()

                result.capabilities.textDocumentSync.left shouldBe TextDocumentSyncKind.Full
                verify {
                    compilerMock.preloadSources(listOf(workspaceFolder.uri))
                }
            }

            test("initialization with both effectiveWorkspaceFolders and testSourcesUri") {
                val compilerMock = mockk<CompilerService>(relaxed = true)
                val server =
                    createTestServer(
                        compilerServiceFactory = { compilerMock },
                    )

                val effectiveFolder1 =
                    JsonObject().apply {
                        addProperty("uri", "file:///effective/workspace1")
                        addProperty("name", "Effective1")
                    }
                val effectiveFolder2 =
                    JsonObject().apply {
                        addProperty("uri", "file:///effective/workspace2")
                        addProperty("name", "Effective2")
                    }
                val effectiveFolders =
                    JsonArray().apply {
                        add(effectiveFolder1)
                        add(effectiveFolder2)
                    }

                val options =
                    JsonObject().apply {
                        add("effectiveWorkspaceFolders", effectiveFolders)
                        addProperty("testSourcesUri", "file:///test/sources")
                    }

                val standardFolder = WorkspaceFolder("file:///standard/workspace", "Standard")

                val params =
                    InitializeParams().apply {
                        workspaceFolders = listOf(standardFolder)
                        initializationOptions = options
                    }

                val result = server.initialize(params).get()

                result.capabilities.textDocumentSync.left shouldBe TextDocumentSyncKind.Full
                verify {
                    compilerMock.preloadSources(
                        match<List<String>> { uris ->
                            uris.size == 2 &&
                                uris.contains("file:///effective/workspace1") &&
                                uris.contains("file:///effective/workspace2")
                        },
                    )
                }
            }

            test("exit") {
                val systemExitHandler = SafeSystemExitHandler()
                val server = createTestServer(systemExitHandler = systemExitHandler)

                server.exit()

                systemExitHandler.exitCalled shouldBe true
                systemExitHandler.statusCode shouldBe 0
            }

            test("setTrace updates trace value") {
                val server = createTestServer()

                // This should be the default value
                TraceManager.isTracingEnabled(TraceValue.Messages) shouldBe false

                // Set to Messages
                val params = SetTraceParams(TraceValue.Messages)
                server.setTrace(params)

                // Now Messages should be enabled
                TraceManager.isTracingEnabled(TraceValue.Messages) shouldBe true
                TraceManager.isTracingEnabled(TraceValue.Verbose) shouldBe false

                // Set to Verbose
                val verboseParams = SetTraceParams(TraceValue.Verbose)
                server.setTrace(verboseParams)

                // Now both should be enabled
                TraceManager.isTracingEnabled(TraceValue.Messages) shouldBe true
                TraceManager.isTracingEnabled(TraceValue.Verbose) shouldBe true
            }
        }

        context("Document handling") {
            test("didOpen updates compiler service") {
                val compilerMock = mockk<CompilerService>(relaxed = true)
                val server =
                    createTestServer(
                        compilerServiceFactory = { compilerMock },
                    )

                server.initialize(InitializeParams()).get()

                val document = TextDocumentItem("file:///test.npl", "npl", 1, "test content")
                server.textDocumentService.didOpen(DidOpenTextDocumentParams(document))

                verify { compilerMock.updateSource("file:///test.npl", "test content") }
            }

            test("didChange updates compiler service") {
                val compilerMock = mockk<CompilerService>(relaxed = true)
                val server =
                    createTestServer(
                        compilerServiceFactory = { compilerMock },
                    )

                server.initialize(InitializeParams()).get()

                val docIdentifier = VersionedTextDocumentIdentifier("file:///test.npl", 2)
                val changeEvent = TextDocumentContentChangeEvent("updated content")
                server.textDocumentService.didChange(
                    DidChangeTextDocumentParams(docIdentifier, listOf(changeEvent)),
                )

                verify { compilerMock.updateSource("file:///test.npl", "updated content") }
            }
        }

        context("Client interaction") {
            test("connect sets client") {
                val clientProvider = LanguageClientProvider()
                val server = createTestServer(clientProvider = clientProvider)
                val clientMock = mockk<LanguageClient>()

                (server as LanguageClientAware).connect(clientMock)

                clientProvider.client shouldBe clientMock
            }
        }
    })

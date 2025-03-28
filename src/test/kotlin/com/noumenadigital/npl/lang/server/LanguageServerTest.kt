package com.noumenadigital.npl.lang.server

import com.noumenadigital.npl.lang.server.compilation.CompilerService
import com.noumenadigital.npl.lang.server.util.LanguageServerFixtures.createTestServer
import com.noumenadigital.npl.lang.server.util.SafeSystemExitHandler
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import io.mockk.verify
import org.eclipse.lsp4j.DidChangeTextDocumentParams
import org.eclipse.lsp4j.DidOpenTextDocumentParams
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.TextDocumentContentChangeEvent
import org.eclipse.lsp4j.TextDocumentItem
import org.eclipse.lsp4j.TextDocumentSyncKind
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

            test("initialization with test sources") {
                val compilerMock = mockk<CompilerService>(relaxed = true)
                val server =
                    createTestServer(
                        compilerServiceFactory = { compilerMock },
                    )

                val workspaceFolder = WorkspaceFolder("file:///test/workspace", "Test")
                val testSourcesFolder = WorkspaceFolder("file:///test/sources", "Test Sources")
                val params =
                    InitializeParams().apply {
                        workspaceFolders = listOf(workspaceFolder)
                        initializationOptions =
                            mapOf(
                                "testSources" to
                                    mapOf(
                                        "uri" to testSourcesFolder.uri,
                                        "name" to testSourcesFolder.name,
                                    ),
                            )
                    }

                val result = server.initialize(params).get()

                result.capabilities.textDocumentSync.left shouldBe TextDocumentSyncKind.Full
                verify {
                    compilerMock.preloadSources(
                        listOf(
                            workspaceFolder.uri,
                            testSourcesFolder.uri,
                        ),
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

package com.noumenadigital.npl.lang.server

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.eclipse.lsp4j.InitializeParams
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.WorkspaceFolder
import org.eclipse.lsp4j.services.LanguageClient

class WorkspaceFolderExtractorTest :
    FunSpec({

        context("extractWorkspaceFolderUris") {
            test("should extract URIs from effectiveWorkspaceFolders") {
                // Setup
                val uri1 = "file:///test/uri1"
                val uri2 = "file:///test/uri2"

                val effectiveWorkspaceFolders =
                    JsonArray().apply {
                        add(createWorkspaceFolderJson(uri1, "folder1"))
                        add(createWorkspaceFolderJson(uri2, "folder2"))
                    }

                val options =
                    JsonObject().apply {
                        add("effectiveWorkspaceFolders", effectiveWorkspaceFolders)
                    }

                val params =
                    InitializeParams().apply {
                        initializationOptions = options
                    }

                // Execute
                val result = WorkspaceFolderExtractor.extractWorkspaceFolderUris(params)

                // Verify
                result.shouldContainExactlyInAnyOrder(uri1, uri2)
            }

            test("should fall back to standard workspaceFolders when effectiveWorkspaceFolders is missing") {
                // Setup
                val uri1 = "file:///test/uri1"
                val uri2 = "file:///test/uri2"

                val params =
                    InitializeParams().apply {
                        workspaceFolders =
                            listOf(
                                WorkspaceFolder(uri1, "folder1"),
                                WorkspaceFolder(uri2, "folder2"),
                            )
                    }

                // Execute
                val result = WorkspaceFolderExtractor.extractWorkspaceFolderUris(params)

                // Verify
                result.shouldContainExactlyInAnyOrder(uri1, uri2)
            }

            test("should fall back to standard workspaceFolders when effectiveWorkspaceFolders is empty") {
                // Setup
                val uri1 = "file:///test/uri1"

                val options =
                    JsonObject().apply {
                        add("effectiveWorkspaceFolders", JsonArray())
                    }

                val params =
                    InitializeParams().apply {
                        initializationOptions = options
                        workspaceFolders =
                            listOf(
                                WorkspaceFolder(uri1, "folder1"),
                            )
                    }

                // Execute
                val result = WorkspaceFolderExtractor.extractWorkspaceFolderUris(params)

                // Verify
                result.shouldContainExactly(uri1)
            }

            test("should handle invalid JSON in effectiveWorkspaceFolders") {
                // Setup
                val uri1 = "file:///test/uri1"

                val effectiveWorkspaceFolders =
                    JsonArray().apply {
                        add(JsonObject()) // Missing uri field
                        add(createWorkspaceFolderJson(uri1, "folder1"))
                    }

                val options =
                    JsonObject().apply {
                        add("effectiveWorkspaceFolders", effectiveWorkspaceFolders)
                    }

                val params =
                    InitializeParams().apply {
                        initializationOptions = options
                    }

                // Execute
                val result = WorkspaceFolderExtractor.extractWorkspaceFolderUris(params)

                // Verify
                result.shouldContainExactly(uri1)
            }

            test("should return empty list when both effectiveWorkspaceFolders and workspaceFolders are missing") {
                // Setup
                val params = InitializeParams()

                // Execute
                val result = WorkspaceFolderExtractor.extractWorkspaceFolderUris(params)

                // Verify
                result.shouldBeEmpty()
            }

            test("should handle non-JsonObject initializationOptions") {
                // Setup
                val uri1 = "file:///test/uri1"

                val params =
                    InitializeParams().apply {
                        initializationOptions = "Not a JsonObject"
                        workspaceFolders =
                            listOf(
                                WorkspaceFolder(uri1, "folder1"),
                            )
                    }

                // Execute
                val result = WorkspaceFolderExtractor.extractWorkspaceFolderUris(params)

                // Verify
                result.shouldContainExactly(uri1)
            }

            test("should log warnings for errors in effectiveWorkspaceFolders") {
                // Setup
                val client = mockk<LanguageClient>(relaxed = true)
                val messageSlot = slot<MessageParams>()

                every { client.logMessage(capture(messageSlot)) } returns Unit

                val badJson =
                    JsonObject().apply {
                        add("effectiveWorkspaceFolders", JsonObject()) // Not an array
                    }

                val params =
                    InitializeParams().apply {
                        initializationOptions = badJson
                    }

                // Execute
                WorkspaceFolderExtractor.extractWorkspaceFolderUris(params, client)

                // Verify client received a warning message
                verify { client.logMessage(match { it.type == MessageType.Warning }) }
            }

            test("should log info messages for successful extraction") {
                // Setup
                val client = mockk<LanguageClient>(relaxed = true)
                val messageSlot = slot<MessageParams>()

                every { client.logMessage(capture(messageSlot)) } returns Unit

                val uri1 = "file:///test/uri1"
                val uri2 = "file:///test/uri2"

                val effectiveWorkspaceFolders =
                    JsonArray().apply {
                        add(createWorkspaceFolderJson(uri1, "folder1"))
                        add(createWorkspaceFolderJson(uri2, "folder2"))
                    }

                val options =
                    JsonObject().apply {
                        add("effectiveWorkspaceFolders", effectiveWorkspaceFolders)
                    }

                val params =
                    InitializeParams().apply {
                        initializationOptions = options
                    }

                // Execute
                WorkspaceFolderExtractor.extractWorkspaceFolderUris(params, client)

                // Verify
                verify { client.logMessage(match { it.type == MessageType.Info }) }
            }
        }
    })

/**
 * Helper function to create a JsonObject representing a workspace folder
 */
private fun createWorkspaceFolderJson(
    uri: String,
    name: String,
): JsonObject =
    JsonObject().apply {
        addProperty("uri", uri)
        addProperty("name", name)
    }

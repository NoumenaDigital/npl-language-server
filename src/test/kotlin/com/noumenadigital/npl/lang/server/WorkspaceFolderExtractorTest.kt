package com.noumenadigital.npl.lang.server

import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder

class WorkspaceFolderExtractorTest :
    FunSpec({

        context("extractUrisFromInitializationOptions") {
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

                // Execute
                val result = WorkspaceFolderExtractor.extractUrisFromInitializationOptions(Gson().fromJson(options, InitializationOptions::class.java))

                // Verify
                result.shouldContainExactlyInAnyOrder(uri1, uri2)
            }

            test("should return empty list when effectiveWorkspaceFolders is missing") {
                // Setup
                val options = JsonObject()

                // Execute
                val result = WorkspaceFolderExtractor.extractUrisFromInitializationOptions(Gson().fromJson(options, InitializationOptions::class.java))

                // Verify
                result.shouldBeEmpty()
            }

            test("should return empty list when effectiveWorkspaceFolders is empty") {
                // Setup
                val options =
                    JsonObject().apply {
                        add("effectiveWorkspaceFolders", JsonArray())
                    }

                // Execute
                val result = WorkspaceFolderExtractor.extractUrisFromInitializationOptions(Gson().fromJson(options, InitializationOptions::class.java))

                // Verify
                result.shouldBeEmpty()
            }

            test("should handle invalid JSON in effectiveWorkspaceFolders") {
                // Setup
                val uri1 = "file:///test/uri1"

                // Create one invalid JSON object (missing uri) and one valid
                val effectiveWorkspaceFolders =
                    JsonArray().apply {
                        add(
                            JsonObject().apply {
                                addProperty("name", "missingUri")
                                // No URI property
                            },
                        )
                        add(createWorkspaceFolderJson(uri1, "folder1"))
                    }

                val options =
                    JsonObject().apply {
                        add("effectiveWorkspaceFolders", effectiveWorkspaceFolders)
                    }

                // Execute
                val result = WorkspaceFolderExtractor.extractUrisFromInitializationOptions(Gson().fromJson(options, InitializationOptions::class.java))

                // Verify - With the fixed implementation, only valid URIs should be included
                result.shouldContainExactly(uri1)
            }

            test("should return empty list when options are defaults") {
                // Execute
                val result = WorkspaceFolderExtractor.extractUrisFromInitializationOptions(InitializationOptions())

                // Verify
                result.shouldBeEmpty()
            }

            test("should handle null effectiveWorkspaceFolders") {
                // Setup
                val options =
                    JsonObject().apply {
                        addProperty("effectiveWorkspaceFolders", null as String?)
                    }

                // Execute
                val result = WorkspaceFolderExtractor.extractUrisFromInitializationOptions(Gson().fromJson(options, InitializationOptions::class.java))

                // Verify
                result.shouldBeEmpty()
            }
        }

        context("extractWorkspaceFolderUris") {
            test("should use effective folders when available") {
                // Setup
                val uri1 = "file:///test/uri1"
                val uri2 = "file:///test/uri2"

                val effectiveWorkspaceFolders =
                    listOf(
                        EffectiveWorkspaceFolder(uri1, "folder1"),
                        EffectiveWorkspaceFolder(uri2, "folder2"),
                    )

                // Execute
                val result = WorkspaceFolderExtractor.extractWorkspaceFolderUris(effectiveWorkspaceFolders, null)

                // Verify
                result.shouldContainExactlyInAnyOrder(uri1, uri2)
            }

            test("should fallback to standard folders when effective folders are empty") {
                // Setup
                val uri1 = "file:///test/uri1"
                val standardFolders = listOf(uri1)

                // Execute
                val result = WorkspaceFolderExtractor.extractWorkspaceFolderUris(emptyList(), standardFolders)

                // Verify
                result.shouldContainExactly(uri1)
            }

            test("should filter out null or blank URIs") {
                // Setup
                val uri1 = "file:///test/uri1"
                val effectiveWorkspaceFolders =
                    listOf(
                        EffectiveWorkspaceFolder(uri1, "folder1"),
                        EffectiveWorkspaceFolder(null, "folder2"),
                        EffectiveWorkspaceFolder("", "folder3"),
                    )

                // Execute
                val result = WorkspaceFolderExtractor.extractWorkspaceFolderUris(effectiveWorkspaceFolders, null)

                // Verify
                result.shouldContainExactly(uri1)
            }
        }
    })

private fun createWorkspaceFolderJson(
    uri: String,
    name: String,
): JsonObject =
    JsonObject().apply {
        addProperty("uri", uri)
        addProperty("name", name)
    }

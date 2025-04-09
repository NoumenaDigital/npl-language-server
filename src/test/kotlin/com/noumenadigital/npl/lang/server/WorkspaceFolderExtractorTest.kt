package com.noumenadigital.npl.lang.server

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder

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

                // Execute
                val result = WorkspaceFolderExtractor.extractWorkspaceFolderUris(options, null)

                // Verify
                result.shouldContainExactlyInAnyOrder(uri1, uri2)
            }

            test("should fall back to standard workspaceFolders when effectiveWorkspaceFolders is missing") {
                // Setup
                val uri1 = "file:///test/uri1"
                val uri2 = "file:///test/uri2"
                val standardFolders = listOf(uri1, uri2)

                // Execute
                val result = WorkspaceFolderExtractor.extractWorkspaceFolderUris(null, standardFolders)

                // Verify
                result.shouldContainExactlyInAnyOrder(uri1, uri2)
            }

            test("should fall back to standard workspaceFolders when effectiveWorkspaceFolders is empty") {
                // Setup
                val uri1 = "file:///test/uri1"
                val standardFolders = listOf(uri1)

                val options =
                    JsonObject().apply {
                        add("effectiveWorkspaceFolders", JsonArray())
                    }

                // Execute
                val result = WorkspaceFolderExtractor.extractWorkspaceFolderUris(options, standardFolders)

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

                // Execute
                val result = WorkspaceFolderExtractor.extractWorkspaceFolderUris(options, null)

                // Verify
                result.shouldContainExactly(uri1)
            }

            test("should return empty list when both effectiveWorkspaceFolders and workspaceFolders are missing") {
                // Execute
                val result = WorkspaceFolderExtractor.extractWorkspaceFolderUris(null, null)

                // Verify
                result.shouldBeEmpty()
            }

            test("should handle non-JsonObject initializationOptions") {
                // Setup
                val uri1 = "file:///test/uri1"
                val standardFolders = listOf(uri1)

                // Execute
                val result = WorkspaceFolderExtractor.extractWorkspaceFolderUris("Not a JsonObject", standardFolders)

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

package com.noumenadigital.npl.lang.server.logging

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.services.LanguageClient

class LSPLoggerTest :
    FunSpec({

        context("Server and client logging") {
            val client = mockk<LanguageClient>(relaxed = true)
            val messageSlot = slot<MessageParams>()

            every { client.logMessage(capture(messageSlot)) } returns Unit

            // Create a logger that always returns our mock client
            val logger = LSPLogger("test.logger") { client }

            test("info messages are sent to client") {
                val message = "This is an info message"
                logger.info(message)

                verify { client.logMessage(any()) }
                messageSlot.captured.type shouldBe MessageType.Info
                messageSlot.captured.message shouldBe "NPL Language Server: $message"
            }

            test("warning messages are sent to client") {
                val message = "This is a warning message"
                logger.warn(message)

                verify { client.logMessage(any()) }
                messageSlot.captured.type shouldBe MessageType.Warning
                messageSlot.captured.message shouldBe "NPL Language Server: $message"
            }

            test("error messages are sent to client") {
                val message = "This is an error message"
                logger.error(message)

                verify { client.logMessage(any()) }
                messageSlot.captured.type shouldBe MessageType.Error
                messageSlot.captured.message shouldBe "NPL Language Server: $message"
            }

            test("error messages with exceptions include exception details") {
                val message = "Error occurred"
                val exception = RuntimeException("Something went wrong")
                logger.error(message, exception)

                verify { client.logMessage(any()) }
                messageSlot.captured.type shouldBe MessageType.Error
                messageSlot.captured.message shouldBe "NPL Language Server: $message - RuntimeException: Something went wrong"
            }
        }

        context("Null client handling") {
            // Create a logger that returns null for the client
            val logger = LSPLogger("test.logger") { null }

            test("logging with null client should not throw exceptions") {
                logger.info("Info with null client")
                logger.warn("Warning with null client")
                logger.error("Error with null client")
                logger.error("Error with exception and null client", RuntimeException("Test exception"))

                // No assertions needed - test passes if no exceptions are thrown
            }
        }

        context("Logger factory") {
            test("forClass creates logger with correct class name") {
                val client = mockk<LanguageClient>(relaxed = true)
                val logger = LSPLogger.forClass<LSPLoggerTest> { client }

                // Using reflection to check the private name field
                val nameField = LSPLogger::class.java.getDeclaredField("name")
                nameField.isAccessible = true
                val name = nameField.get(logger) as String

                name shouldBe LSPLoggerTest::class.java.name
            }
        }

        context("Debug and trace messages") {
            val client = mockk<LanguageClient>(relaxed = true)
            val logger = LSPLogger("test.logger") { client }

            test("debug messages are not sent to client") {
                logger.debug("Debug message")

                verify(exactly = 0) { client.logMessage(any()) }
            }

            test("trace messages are not sent to client") {
                logger.trace("Trace message")

                verify(exactly = 0) { client.logMessage(any()) }
            }
        }
    })

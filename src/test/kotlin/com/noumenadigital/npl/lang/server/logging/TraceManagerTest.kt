package com.noumenadigital.npl.lang.server.logging

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import org.eclipse.lsp4j.TraceValue
import org.eclipse.lsp4j.services.LanguageClient

class TraceManagerTest :
    FunSpec({

        beforeEach {
            // Reset trace value to Off before each test
            TraceManager.setTraceValue(TraceValue.Off)
        }

        context("Trace level settings") {
            test("default trace level should be 'off'") {
                // Default is Off, so messages shouldn't be traced
                TraceManager.isTracingEnabled(TraceValue.Messages) shouldBe false
                TraceManager.isTracingEnabled(TraceValue.Verbose) shouldBe false
            }

            test("should enable appropriate levels when set to 'messages'") {
                TraceManager.setTraceValue(TraceValue.Messages)

                TraceManager.isTracingEnabled(TraceValue.Messages) shouldBe true
                TraceManager.isTracingEnabled(TraceValue.Verbose) shouldBe false
            }

            test("should enable all levels when set to 'verbose'") {
                TraceManager.setTraceValue(TraceValue.Verbose)

                TraceManager.isTracingEnabled(TraceValue.Messages) shouldBe true
                TraceManager.isTracingEnabled(TraceValue.Verbose) shouldBe true
            }
        }

        context("Trace logging") {
            val testTracingClient = TestTracingClient()

            beforeTest {
                testTracingClient.reset()
            }

            test("should not send trace when tracing is disabled") {
                TraceManager.setTraceValue(TraceValue.Off)
                TraceManager.logTraceMessage("This should not be sent", testTracingClient)

                testTracingClient.traceMessages.size shouldBe 0
            }

            test("should send basic trace messages when level is 'messages'") {
                TraceManager.setTraceValue(TraceValue.Messages)

                val message = "This is a basic trace message"
                TraceManager.logTraceMessage(message, testTracingClient)

                testTracingClient.traceMessages.size shouldBe 1
                testTracingClient.traceMessages[0] shouldBe message
            }

            test("should not send verbose trace messages when level is 'messages'") {
                TraceManager.setTraceValue(TraceValue.Messages)

                val message = "This is a verbose trace message"
                TraceManager.logTraceVerbose(message, testTracingClient)

                testTracingClient.traceMessages.size shouldBe 0
            }

            test("should send all trace messages when level is 'verbose'") {
                TraceManager.setTraceValue(TraceValue.Verbose)

                val message1 = "This is a basic trace message"
                val message2 = "This is a verbose trace message"

                TraceManager.logTraceMessage(message1, testTracingClient)
                TraceManager.logTraceVerbose(message2, testTracingClient)

                testTracingClient.traceMessages.size shouldBe 2
                testTracingClient.traceMessages[0] shouldBe message1
                testTracingClient.traceMessages[1] shouldBe message2
            }

            test("should handle null client") {
                // Set trace to enabled
                TraceManager.setTraceValue(TraceValue.Verbose)

                // Should not throw exception with null client
                TraceManager.logTraceMessage("This should be silently ignored", null)
                TraceManager.logTraceVerbose("This should also be ignored", null)
            }
        }
    })

/**
 * A simple TracingClient implementation for testing that captures trace messages.
 */
class TestTracingClient :
    TracingClient,
    LanguageClient {
    val traceMessages = mutableListOf<String>()

    fun reset() {
        traceMessages.clear()
    }

    override fun logTrace(message: String) {
        traceMessages.add(message)
    }

    // Implement required methods from LanguageClient
    // Since we're not testing these, we can leave them as empty implementations
    override fun telemetryEvent(any: Any?) {}

    override fun publishDiagnostics(diagnostics: org.eclipse.lsp4j.PublishDiagnosticsParams?) {}

    override fun showMessage(messageParams: org.eclipse.lsp4j.MessageParams?) {}

    override fun showMessageRequest(requestParams: org.eclipse.lsp4j.ShowMessageRequestParams?) = null

    override fun logMessage(message: org.eclipse.lsp4j.MessageParams?) {}
}

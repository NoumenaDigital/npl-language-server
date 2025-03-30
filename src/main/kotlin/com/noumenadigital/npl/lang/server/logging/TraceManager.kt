package com.noumenadigital.npl.lang.server.logging

import org.eclipse.lsp4j.TraceValue
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import org.eclipse.lsp4j.services.LanguageClient
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages trace settings and provides trace logging capabilities for the language server.
 */
object TraceManager {
    // Default trace level is off
    private val currentTraceValue = AtomicReference(TraceValue.Off)

    /**
     * Sets the trace level for the language server.
     * @param value The trace level to set, one of: "off", "messages", "verbose"
     */
    fun setTraceValue(value: String) {
        currentTraceValue.set(value)
    }

    /**
     * Checks if the specified trace level is enabled.
     * @param level The trace level to check
     * @return true if the current trace level is at or above the specified level
     */
    fun isTracingEnabled(level: String): Boolean {
        val current = currentTraceValue.get()
        return when (level) {
            TraceValue.Off -> false // "off" is never enabled
            TraceValue.Messages -> current == TraceValue.Messages || current == TraceValue.Verbose
            TraceValue.Verbose -> current == TraceValue.Verbose
            else -> false
        }
    }

    /**
     * Logs a trace message to the client if tracing is enabled at the specified level.
     * @param message The message to log
     * @param level The trace level for this message (Messages or Verbose)
     * @param client The language client to send the trace to
     */
    fun logTrace(
        message: String,
        level: String = TraceValue.Messages,
        client: LanguageClient?,
    ) {
        if (isTracingEnabled(level) && client != null) {
            // Check if client implements the LSP extension interface
            if (client is TracingClient) {
                client.logTrace(message)
            } else {
                // Use reflection as a fallback if the client doesn't implement our custom interface
                try {
                    val method = client.javaClass.getMethod("logTrace", String::class.java)
                    method.invoke(client, message)
                } catch (e: Exception) {
                    // Couldn't send trace - client doesn't support it
                }
            }
        }
    }

    /**
     * Logs a basic trace message (level = "messages").
     * @param message The message to log
     * @param client The language client to send the trace to
     */
    fun logTraceMessage(
        message: String,
        client: LanguageClient?,
    ) {
        logTrace(message, TraceValue.Messages, client)
    }

    /**
     * Logs a verbose trace message (level = "verbose").
     * @param message The message to log
     * @param client The language client to send the trace to
     */
    fun logTraceVerbose(
        message: String,
        client: LanguageClient?,
    ) {
        logTrace(message, TraceValue.Verbose, client)
    }
}

/**
 * Interface for LSP clients that support trace logging.
 */
interface TracingClient {
    /**
     * Logs a trace message to the client.
     * @param message The message to log
     */
    @JsonNotification("$/logTrace")
    fun logTrace(message: String)
}

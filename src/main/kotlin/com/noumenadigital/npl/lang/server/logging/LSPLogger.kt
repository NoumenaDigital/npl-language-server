package com.noumenadigital.npl.lang.server.logging

import mu.KotlinLogging
import org.eclipse.lsp4j.MessageParams
import org.eclipse.lsp4j.MessageType
import org.eclipse.lsp4j.services.LanguageClient

/**
 * Logger for language servers that logs both to the server log and to the LSP client.
 *
 * This helps ensure that important information is visible both in the server logs
 * (for developers) and in the client/editor (for users).
 */
class LSPLogger(
    private val name: String,
    private val clientProvider: () -> LanguageClient?,
) {
    private val serverLogger = KotlinLogging.logger(name)
    private val prefix = "NPL Language Server"

    fun trace(message: String) {
        serverLogger.trace(message)
        // For trace level messages, use the TraceManager to respect trace settings
        TraceManager.logTraceVerbose("$name: $message", clientProvider())
    }

    fun debug(message: String) {
        serverLogger.debug(message)
        // Debug messages use basic trace level (messages)
        TraceManager.logTraceMessage("$name: $message", clientProvider())
    }

    fun info(message: String) {
        serverLogger.info(message)
        clientProvider()?.logMessage(MessageParams(MessageType.Info, "$prefix: $message"))
    }

    fun warn(message: String) {
        serverLogger.warn(message)
        clientProvider()?.logMessage(MessageParams(MessageType.Warning, "$prefix: $message"))
    }

    fun error(message: String) {
        serverLogger.error(message)
        clientProvider()?.logMessage(MessageParams(MessageType.Error, "$prefix: $message"))
    }

    fun error(
        message: String,
        e: Throwable,
    ) {
        serverLogger.error(e) { message }
        clientProvider()?.logMessage(
            MessageParams(
                MessageType.Error,
                "$prefix: $message - ${e.javaClass.simpleName}: ${e.message}",
            ),
        )
    }

    companion object {
        /**
         * Creates a logger for the specified class that uses the provided client provider.
         */
        inline fun <reified T : Any> forClass(noinline clientProvider: () -> LanguageClient?): LSPLogger =
            LSPLogger(T::class.java.name, clientProvider)
    }
}

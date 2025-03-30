package com.noumenadigital.npl.lang.server

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.encoder.PatternLayoutEncoder
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.ConsoleAppender
import mu.KotlinLogging
import org.eclipse.lsp4j.launch.LSPLauncher
import org.eclipse.lsp4j.services.LanguageClientAware
import org.slf4j.LoggerFactory
import java.io.InputStream
import java.io.OutputStream
import java.io.PrintWriter
import java.net.ServerSocket

private val logger = KotlinLogging.logger { }

enum class ServerMode {
    TCP,
    STDIO,
}

data class ServerConfig(
    val mode: ServerMode,
    val tcpPort: Int? = null,
)

interface ServerLauncher {
    fun launchTcpServer(port: Int)

    fun launchStdioServer()

    fun startServer(
        languageServer: LanguageServer,
        input: InputStream,
        output: OutputStream,
    )

    fun createLanguageServer(): LanguageServer = LanguageServer()
}

class DefaultServerLauncher : ServerLauncher {
    override fun launchTcpServer(port: Int) {
        val languageServer = createLanguageServer()
        logger.info("Language TCP server started on port $port")

        ServerSocket(port).use { serverSocket ->
            val socket = serverSocket.accept()
            logger.info("Client connected: ${socket.inetAddress}")
            startServer(languageServer, socket.getInputStream(), socket.getOutputStream())
        }
    }

    override fun launchStdioServer() {
        // Configure logging to stderr but leave stdout for protocol messages
        configureLoggingForStdioMode()

        val languageServer = createLanguageServer()

        // Use stderr only for logging, keep stdout for protocol messages
        val launcher =
            LSPLauncher.createServerLauncher(
                languageServer,
                System.`in`,
                System.out,
                true,
                PrintWriter(System.err),
            )

        val client = launcher.remoteProxy
        (languageServer as LanguageClientAware).connect(client)

        launcher.startListening()

        logger.info("Language STDIO server started")
    }

    override fun startServer(
        languageServer: LanguageServer,
        input: InputStream,
        output: OutputStream,
    ) {
        val launcher = LSPLauncher.createServerLauncher(languageServer, input, output)
        launcher.startListening()
        languageServer.connect(launcher.remoteProxy)
    }

    private fun configureLoggingForStdioMode() {
        try {
            // DO NOT redirect System.out to System.err - that causes protocol messages to go to stderr
            // System.setOut(PrintStream(System.err)) <- This was the problem!

            // Configure logback to use stderr for all logging
            redirectLogbackToStderr()
        } catch (e: Exception) {
            // If redirection fails, log a warning and continue
            System.err.println("Warning: Could not redirect logging: ${e.message}")
        }
    }

    private fun redirectLogbackToStderr() {
        // Get the logback logger context
        val loggerContext = LoggerFactory.getILoggerFactory() as? LoggerContext ?: return

        // Get the root logger
        val rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME)

        // Create and configure a stderr appender
        val stderrAppender =
            ConsoleAppender<ILoggingEvent>().apply {
                context = loggerContext
                name = "STDERR"
                target = "System.err"

                // Create and set encoder with pattern
                encoder =
                    PatternLayoutEncoder().apply {
                        context = loggerContext
                        pattern = "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
                        start()
                    }

                start()
            }

        // Remove all existing appenders and add the stderr appender
        rootLogger.detachAndStopAllAppenders()
        rootLogger.addAppender(stderrAppender)
    }
}

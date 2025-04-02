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
        configureLoggingForStdioMode()

        val languageServer = createLanguageServer()

        // Use stderr only for logging, keep stdout for protocol messages
        val launcher =
            LSPLauncher.createServerLauncher(
                // server =
                languageServer,
                // in =
                System.`in`,
                // out =
                System.out,
                // validate =
                true,
                // trace =
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

    // logging before this method is called in stdio mode will pollute stdout
    private fun configureLoggingForStdioMode() {
        try {
            redirectLogbackToStderr()
        } catch (e: Exception) {
            System.err.println("Warning: Could not redirect logging: ${e.message}")
        }
    }

    private fun redirectLogbackToStderr() {
        val loggerContext = LoggerFactory.getILoggerFactory() as? LoggerContext ?: return

        val rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME)

        val stderrAppender =
            ConsoleAppender<ILoggingEvent>().apply {
                context = loggerContext
                name = "STDERR"
                target = "System.err"

                encoder =
                    PatternLayoutEncoder().apply {
                        context = loggerContext
                        pattern = "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
                        start()
                    }

                start()
            }

        rootLogger.detachAndStopAllAppenders()
        rootLogger.addAppender(stderrAppender)
    }
}

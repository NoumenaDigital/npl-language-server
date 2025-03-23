package com.noumenadigital.npl.lang.server

import mu.KotlinLogging
import org.eclipse.lsp4j.launch.LSPLauncher
import java.io.InputStream
import java.io.OutputStream
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
        val languageServer = createLanguageServer()
        logger.info("Language STDIO server started")

        startServer(languageServer, System.`in`, System.out)
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
}

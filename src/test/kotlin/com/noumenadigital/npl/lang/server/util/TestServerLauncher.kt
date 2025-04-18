package com.noumenadigital.npl.lang.server.util

import com.noumenadigital.npl.lang.server.LanguageServer
import com.noumenadigital.npl.lang.server.ServerLauncher
import java.io.InputStream
import java.io.OutputStream

class TestServerLauncher : ServerLauncher {
    var tcpServerLaunched = false
    var stdioServerLaunched = false
    var tcpPort: Int? = null
    var inputStream: InputStream? = null
    var outputStream: OutputStream? = null
    var useSystemStreams = false

    override fun launchTcpServer(port: Int) {
        tcpServerLaunched = true
        tcpPort = port
        // TCP mode uses its own streams (simulated here with mocks)
        val server = createLanguageServer()
        val input = createMockInputStream()
        val output = createMockOutputStream()
        startServer(server, input, output)
    }

    override fun launchStdioServer() {
        stdioServerLaunched = true
        // Stdio mode selects streams based on useSystemStreams
        val server = createLanguageServer()
        val input = if (useSystemStreams) System.`in` else createMockInputStream()
        val output = if (useSystemStreams) System.out else createMockOutputStream()
        startServer(server, input, output)
    }

    override fun startServer(
        languageServer: LanguageServer,
        input: InputStream,
        output: OutputStream,
    ) {
        inputStream = input
        outputStream = output
    }

    private fun createMockInputStream() =
        object : InputStream() {
            override fun read(): Int = -1
        }

    private fun createMockOutputStream() =
        object : OutputStream() {
            override fun write(b: Int) {}
        }
}

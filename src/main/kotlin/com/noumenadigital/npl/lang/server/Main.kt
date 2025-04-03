package com.noumenadigital.npl.lang.server

import mu.KotlinLogging

private val logger = KotlinLogging.logger { }

fun main(args: Array<String>) {
    val config = parseArgs(args)
    launchServer(config)
}

const val DEFAULT_TCP_PORT = 5007

fun parseArgs(args: Array<String>): ServerConfig {
    val stdioMode = args.contains("--stdio")
    val port = args.find { it.startsWith("--port=") }?.substringAfter("=")?.toInt() ?: DEFAULT_TCP_PORT

    val mode =
        when {
            stdioMode -> ServerMode.STDIO
            else -> ServerMode.TCP
        }

    return ServerConfig(
        mode = mode,
        tcpPort = if (mode == ServerMode.TCP) port else null,
    )
}

fun launchServer(
    config: ServerConfig,
    launcher: ServerLauncher = DefaultServerLauncher(),
) {
    when (config.mode) {
        ServerMode.TCP -> {
            val port = config.tcpPort ?: DEFAULT_TCP_PORT
            launcher.launchTcpServer(port)
            logger.info("Started language server in TCP mode on port $port")
        }

        ServerMode.STDIO -> {
            launcher.launchStdioServer()
            logger.info("Started language server in stdio mode")
        }
    }
}

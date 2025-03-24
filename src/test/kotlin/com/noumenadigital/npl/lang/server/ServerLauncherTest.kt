package com.noumenadigital.npl.lang.server

import com.noumenadigital.npl.lang.server.util.TestServerLauncher
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class ServerLauncherTest :
    FunSpec({

        context("Command line parsing") {
            test("tcp default") {
                val config = parseArgs(arrayOf("--tcp"))
                config.mode shouldBe ServerMode.TCP
                config.tcpPort shouldBe DEFAULT_TCP_PORT
            }

            test("tcp with custom port") {
                val config = parseArgs(arrayOf("--tcp", "--port=8080"))
                config.mode shouldBe ServerMode.TCP
                config.tcpPort shouldBe 8080
            }

            test("stdio mode") {
                val config = parseArgs(arrayOf("--stdio"))
                config.mode shouldBe ServerMode.STDIO
                config.tcpPort shouldBe null
            }

            test("empty args") {
                val config = parseArgs(arrayOf())
                config.mode shouldBe ServerMode.TCP
                config.tcpPort shouldBe DEFAULT_TCP_PORT
            }

            test("combined options") {
                val config = parseArgs(arrayOf("--stdio", "--port=8080"))
                config.mode shouldBe ServerMode.STDIO
                config.tcpPort shouldBe null
            }
        }

        context("Server startup") {
            test("tcp server") {
                val config = ServerConfig(ServerMode.TCP, 8080)
                val mockLauncher = TestServerLauncher()

                launchServer(config, mockLauncher)

                mockLauncher.tcpServerLaunched shouldBe true
                mockLauncher.stdioServerLaunched shouldBe false
                mockLauncher.tcpPort shouldBe 8080
            }

            test("stdio server") {
                val config = ServerConfig(ServerMode.STDIO)
                val mockLauncher = TestServerLauncher()

                launchServer(config, mockLauncher)

                mockLauncher.tcpServerLaunched shouldBe false
                mockLauncher.stdioServerLaunched shouldBe true
            }

            test("tcp with default port") {
                val config = ServerConfig(ServerMode.TCP, null)
                val mockLauncher = TestServerLauncher()

                launchServer(config, mockLauncher)

                mockLauncher.tcpServerLaunched shouldBe true
                mockLauncher.tcpPort shouldBe DEFAULT_TCP_PORT
            }
        }

        context("Stream handling") {
            test("tcp uses socket streams") {
                val testLauncher = TestServerLauncher()
                val config = ServerConfig(ServerMode.TCP, 8080)

                launchServer(config, testLauncher)

                testLauncher.tcpServerLaunched shouldBe true
            }

            test("stdio uses System streams") {
                val testLauncher = TestServerLauncher()
                val config = ServerConfig(ServerMode.STDIO)

                testLauncher.useSystemStreams = true
                launchServer(config, testLauncher)

                testLauncher.stdioServerLaunched shouldBe true
                testLauncher.inputStream shouldBe System.`in`
                testLauncher.outputStream shouldBe System.out
            }
        }
    })

package com.noumenadigital.npl.lang.server.util

import com.noumenadigital.npl.lang.server.LanguageClientProvider
import com.noumenadigital.npl.lang.server.LanguageServer
import com.noumenadigital.npl.lang.server.SystemExitHandler
import com.noumenadigital.npl.lang.server.compilation.CompilerService
import com.noumenadigital.npl.lang.server.compilation.DefaultCompilerService
import org.eclipse.lsp4j.services.LanguageClientAware
import java.util.concurrent.TimeUnit

object LanguageServerFixtures {
    fun createTestServer(
        systemExitHandler: SystemExitHandler = SafeSystemExitHandler(),
        clientProvider: LanguageClientProvider = LanguageClientProvider(),
        compilerServiceFactory: (LanguageClientProvider) -> CompilerService = { provider ->
            DefaultCompilerService(provider)
        },
    ) = LanguageServer(
        systemExitHandler = systemExitHandler,
        clientProvider = clientProvider,
        compilerServiceFactory = compilerServiceFactory,
    )

    fun withLanguageServer(
        test: (TestLanguageClient) -> Unit,
    ) {
        val client = TestLanguageClient()
        val exitHandler = SafeSystemExitHandler()
        val server = createTestServer(systemExitHandler = exitHandler)

        try {
            (server as LanguageClientAware).connect(client)
            client.connect(server)
            client.initialize().get(10, TimeUnit.SECONDS)

            test(client)
        } finally {
            client.shutdown().get(5, TimeUnit.SECONDS)
            client.exit()
        }
    }
}

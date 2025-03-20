package com.noumenadigital.npl.lang.server.util

import com.noumenadigital.npl.lang.server.compilation.CompilerService
import org.intellij.lang.annotations.Language
import java.net.URI
import java.nio.file.Files
import java.util.concurrent.TimeUnit

object UriFixtures {
    fun normalizeUri(uriString: String): String {
        val uri = URI.create(uriString)
        return if (uri.scheme == "file") {
            val path = uri.path
            val cleanPath = path.dropWhile { it == '/' }
            "file:///$cleanPath"
        } else {
            uriString
        }
    }

    private fun createTestFile(@Language("NPL") code: String): String {
        val tempFile = Files.createTempFile("test", ".npl")
        Files.writeString(tempFile, code)
        tempFile.toFile().deleteOnExit()
        val uri = tempFile.toUri().toString()
        return normalizeUri(uri)
    }

    fun withNplTestFile(
        @Language("NPL") code: String,
        service: CompilerService,
        client: TestLanguageClient,
        timeoutMs: Long = 5000,
    ): String {
        val fileUri = createTestFile(code)
        client.expectDiagnostics()
        service.updateSource(fileUri, code)
        client.waitForDiagnostics(timeoutMs, TimeUnit.MILLISECONDS)
        return fileUri
    }
}

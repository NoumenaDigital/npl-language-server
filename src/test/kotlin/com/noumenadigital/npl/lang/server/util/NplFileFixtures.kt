package com.noumenadigital.npl.lang.server.util

import com.noumenadigital.npl.lang.server.compilation.CompilerService
import com.noumenadigital.npl.lang.server.util.UriFixtures.normalizeUri
import org.intellij.lang.annotations.Language
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

object NplFileFixtures {
    fun <T> withTempDirectory(
        prefix: String,
        block: (Path) -> T,
    ): T {
        val tempDir = Files.createTempDirectory(prefix)
        try {
            return block(tempDir)
        } finally {
            tempDir.toFile().deleteRecursively()
        }
    }

    fun createNplFile(
        directory: Path,
        name: String,
        @Language("NPL") content: String,
    ): Path {
        val file = directory.resolve(name)
        Files.createDirectories(directory)
        return Files.writeString(file, content)
    }

    fun moveZipArchive(
        sourceFile: Path,
        targetDirectory: Path,
        newFileName: String? = null
    ): Path {
        Files.createDirectories(targetDirectory)
        val destination = targetDirectory.resolve(newFileName ?: sourceFile.fileName.toString())
        return Files.copy(sourceFile, destination, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun createTempNplFile(
        @Language("NPL") code: String,
    ): String {
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
        val fileUri = createTempNplFile(code)
        client.expectDiagnostics()
        service.updateSource(fileUri, code)
        client.waitForDiagnostics(timeoutMs, TimeUnit.MILLISECONDS)
        return fileUri
    }

    fun simpleValidCode(): String =
        // language=NPL
        """
        package test
        use stdlib.v1.collection.range

        function f() -> {
            range(1, 4, 1);
        };

        struct Simple {
            value: Number
        }
        """.trimIndent()

    fun validCodeWithError(): String =
        """
        package test

        struct MyStruct {
            badField: NonExistentType
        }
        """.trimIndent()

    fun codeValidOnlyWithNplContribLib(): String =
        """
        package test
        use mynpl.MyFoo2;

        struct MyStruct {
            goodField: MyFoo2
        }
        """.trimIndent()
}

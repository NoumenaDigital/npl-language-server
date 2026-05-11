package com.noumenadigital.npl.lang.server.features

import com.noumenadigital.npl.lang.server.ast.ParsedFile
import org.eclipse.lsp4j.Location
import org.eclipse.lsp4j.Position

/**
 * Provides go-to-declaration functionality for NPL files.
 *
 * In NPL, there is no distinction between declaration and definition
 * (unlike C/C++ with forward declarations). This provider delegates
 * directly to [DefinitionProvider].
 */
object DeclarationProvider {

    /**
     * Get the declaration location for the symbol at the given position.
     * Delegates to [DefinitionProvider.getDefinition] since NPL doesn't
     * distinguish between declaration and definition.
     */
    fun getDeclaration(
        parsedFile: ParsedFile,
        position: Position,
        allParsedFiles: Map<String, ParsedFile>,
    ): List<Location> {
        return DefinitionProvider.getDefinition(parsedFile, position, allParsedFiles)
    }
}


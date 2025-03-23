package com.noumenadigital.npl.lang.server

import org.eclipse.lsp4j.services.LanguageClient

// This is just a trivial implementation for now, but this abstraction
// will help with extensibility (e.g. multiple clients) and testability in the future.
class LanguageClientProvider {
    var client: LanguageClient? = null
}

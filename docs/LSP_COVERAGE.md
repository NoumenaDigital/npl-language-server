# LSP Feature Coverage

This document tracks which [Language Server Protocol](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/) features are implemented in the NPL language server.

Legend:
- ✅ **Implemented** – fully working
- 🔲 **Stub** – method exists, returns `null` / no-op; not yet implemented
- ➖ **N/A** – intentionally omitted (no semantic value for NPL)

---

## Lifecycle

| LSP Feature | Status | Notes |
|---|---|---|
| `initialize` | ✅ | Returns `TextDocumentSyncKind.Full`; supports custom `InitializationOptions` |
| `initialized` | ✅ | Handled by LSP4J framework |
| `shutdown` | ✅ | Shuts down the debounce scheduler |
| `exit` | ✅ | Calls `exitProcess(0)` |
| `$/setTrace` | ✅ | No-op stub (avoids client error) |
| `$/cancelRequest` | ✅ | Handled by LSP4J framework |

---

## Text Document Synchronisation

| LSP Feature | Status | Notes |
|---|---|---|
| `textDocument/didOpen` | ✅ | Triggers immediate compile + diagnostics |
| `textDocument/didChange` | ✅ | Full-content sync, debounced (default 300 ms) |
| `textDocument/didClose` | ✅ | Removes source if file no longer exists on disk |
| `textDocument/didSave` | ✅ | No-op — compilation happens on change |
| `textDocument/willSave` | 🔲 | Stub |
| `textDocument/willSaveWaitUntil` | 🔲 | Stub |

---

## Diagnostics

| LSP Feature | Status | Notes |
|---|---|---|
| `textDocument/publishDiagnostics` (push) | ✅ | Compiler errors & warnings with range, severity, code, message |
| `textDocument/diagnostic` (pull) | 🔲 | Stub |
| `workspace/diagnostic` (pull) | 🔲 | Stub |

---

## Intelligence

| LSP Feature | Status | Notes |
|---|---|---|
| `textDocument/completion` | 🔲 | Stub — requires compiler symbol table |
| `completionItem/resolve` | 🔲 | Stub |
| `textDocument/hover` | 🔲 | Stub — requires compiler type info |
| `textDocument/signatureHelp` | 🔲 | Stub — requires compiler AST |
| `textDocument/inlayHint` | 🔲 | Stub |
| `inlayHint/resolve` | 🔲 | Stub |
| `textDocument/inlineValue` | 🔲 | Stub |

---

## Navigation

| LSP Feature | Status | Notes |
|---|---|---|
| `textDocument/definition` | 🔲 | Stub — requires compiler symbol resolution |
| `textDocument/declaration` | 🔲 | Stub |
| `textDocument/typeDefinition` | 🔲 | Stub |
| `textDocument/implementation` | 🔲 | Stub |
| `textDocument/references` | 🔲 | Stub — requires compiler reference tracking |

---

## Symbols

| LSP Feature | Status | Notes |
|---|---|---|
| `textDocument/documentSymbol` | 🔲 | Stub — outline view (structs, functions, protocols…) |
| `textDocument/documentHighlight` | 🔲 | Stub — highlight other occurrences |
| `workspace/symbol` | 🔲 | Stub — cross-file symbol search |
| `workspaceSymbol/resolve` | 🔲 | Stub |

---

## Code Actions & Lens

| LSP Feature | Status | Notes |
|---|---|---|
| `textDocument/codeAction` | 🔲 | Stub — quick fixes, refactors |
| `codeAction/resolve` | 🔲 | Stub |
| `textDocument/codeLens` | 🔲 | Stub |
| `codeLens/resolve` | 🔲 | Stub |

---

## Formatting

| LSP Feature | Status | Notes |
|---|---|---|
| `textDocument/formatting` | 🔲 | Stub |
| `textDocument/rangeFormatting` | 🔲 | Stub |
| `textDocument/onTypeFormatting` | 🔲 | Stub |

---

## Rename

| LSP Feature | Status | Notes |
|---|---|---|
| `textDocument/rename` | 🔲 | Stub — requires compiler reference tracking |
| `textDocument/prepareRename` | 🔲 | Stub |

---

## Semantic Tokens

| LSP Feature | Status | Notes |
|---|---|---|
| `textDocument/semanticTokens/full` | 🔲 | Stub — requires compiler AST walk |
| `textDocument/semanticTokens/full/delta` | 🔲 | Stub |
| `textDocument/semanticTokens/range` | 🔲 | Stub |

---

## Hierarchies

| LSP Feature | Status | Notes |
|---|---|---|
| `textDocument/prepareCallHierarchy` | 🔲 | Stub |
| `callHierarchy/incomingCalls` | 🔲 | Stub |
| `callHierarchy/outgoingCalls` | 🔲 | Stub |
| `textDocument/prepareTypeHierarchy` | 🔲 | Stub |
| `typeHierarchy/supertypes` | 🔲 | Stub |
| `typeHierarchy/subtypes` | 🔲 | Stub |

---

## Document Extras

| LSP Feature | Status | Notes |
|---|---|---|
| `textDocument/foldingRange` | 🔲 | Stub |
| `textDocument/selectionRange` | 🔲 | Stub |
| `textDocument/linkedEditingRange` | 🔲 | Stub |
| `textDocument/documentLink` | 🔲 | Stub |
| `documentLink/resolve` | 🔲 | Stub |
| `textDocument/documentColor` | ➖ | Not applicable to NPL |
| `textDocument/colorPresentation` | ➖ | Not applicable to NPL |
| `textDocument/moniker` | 🔲 | Stub |

---

## Workspace

| LSP Feature | Status | Notes |
|---|---|---|
| `workspace/didChangeConfiguration` | ✅ | No-op (no server-side configuration yet) |
| `workspace/didChangeWatchedFiles` | ✅ | Handles `Deleted` events; clears diagnostics |
| `workspace/didChangeWorkspaceFolders` | 🔲 | Stub |
| `workspace/executeCommand` | 🔲 | Stub |
| `workspace/willCreateFiles` | 🔲 | Stub |
| `workspace/didCreateFiles` | 🔲 | Stub |
| `workspace/willRenameFiles` | 🔲 | Stub |
| `workspace/didRenameFiles` | 🔲 | Stub |
| `workspace/willDeleteFiles` | 🔲 | Stub |
| `workspace/didDeleteFiles` | ✅ | Removes source + clears diagnostics |

---

## Suggested Implementation Order

Priority order based on editor-experience value and compiler API availability:

1. `textDocument/hover` — type info & docs on hover
2. `textDocument/definition` — go to definition
3. `textDocument/documentSymbol` — outline / breadcrumb navigation
4. `textDocument/references` — find all references
5. `textDocument/rename` / `textDocument/prepareRename` — safe rename
6. `textDocument/completion` — auto-complete
7. `textDocument/semanticTokens/full` — rich syntax highlighting
8. `textDocument/foldingRange` — code folding
9. `textDocument/signatureHelp` — function signature hints
10. `textDocument/codeAction` — quick fixes


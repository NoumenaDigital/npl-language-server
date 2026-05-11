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
| `textDocument/diagnostic` (pull) | ✅ | Returns diagnostics for a single document on demand |
| `workspace/diagnostic` (pull) | ✅ | Returns diagnostics for all workspace documents |

---

## Intelligence

| LSP Feature | Status | Notes |
|---|---|---|
| `textDocument/completion` | ✅ | Context-aware completions for keywords, types, functions, and user-defined symbols |
| `completionItem/resolve` | 🔲 | Stub |
| `textDocument/hover` | ✅ | Type info & documentation on hover for all declarations |
| `textDocument/signatureHelp` | ✅ | Shows function signature with parameter info while typing inside function calls |
| `textDocument/inlayHint` | ✅ | Type hints for constants without explicit type annotations |
| `inlayHint/resolve` | 🔲 | Stub |
| `textDocument/inlineValue` | 🔲 | Stub |

---

## Navigation

| LSP Feature | Status | Notes |
|---|---|---|
| `textDocument/definition` | ✅ | Go to definition for functions, protocols, structs, unions, enums, notifications, states |
| `textDocument/declaration` | ✅ | Delegates to definition (NPL has no separate declaration/definition distinction) |
| `textDocument/typeDefinition` | ✅ | Navigate to type definition of variables, parameters, fields; returns empty for built-in types |
| `textDocument/implementation` | ✅ | For unions: finds all variants; for protocols: finds states and actions; others: falls back to definition |
| `textDocument/references` | ✅ | Find all references across loaded files |

---

## Symbols

| LSP Feature | Status | Notes |
|---|---|---|
| `textDocument/documentSymbol` | ✅ | Outline view: protocols, functions, structs, unions, enums, notifications, constants |
| `textDocument/documentHighlight` | ✅ | Highlights all occurrences of a symbol in the current document |
| `workspace/symbol` | ✅ | Cross-file symbol search with fuzzy matching; finds functions, protocols, structs, unions, enums, notifications, constants, states, actions |
| `workspaceSymbol/resolve` | 🔲 | Stub |

---

## Code Actions & Lens

| LSP Feature | Status | Notes |
|---|---|---|
| `textDocument/codeAction` | ✅ | Quick fixes (imports, remove unused), source actions (organize imports, generate init/toString), refactoring (extract variable, convert permission/obligation) |
| `codeAction/resolve` | ✅ | Actions are fully resolved when returned |
| `textDocument/codeLens` | ✅ | Reference counts for functions, structs, protocols, unions, enums, notifications; state/action counts for protocols; variant counts for unions/enums |
| `codeLens/resolve` | ✅ | Lenses are fully resolved when returned |

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
| `textDocument/rename` | ✅ | Rename symbols across all loaded files; validates new name is a valid identifier |
| `textDocument/prepareRename` | ✅ | Returns symbol range and placeholder; rejects built-in types and keywords |

---

## Semantic Tokens

| LSP Feature | Status | Notes |
|---|---|---|
| `textDocument/semanticTokens/full` | ✅ | Rich highlighting for keywords, types, functions, variables, parameters, comments, and more |
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

| LSP Feature | Status | Notes                                                       |
|---|---|-------------------------------------------------------------|
| `textDocument/foldingRange` | ✅ | Import blocks, protocols, functions, structs, comments etc. |
| `textDocument/selectionRange` | 🔲 | Stub                                                        |
| `textDocument/linkedEditingRange` | 🔲 | Stub                                                        |
| `textDocument/documentLink` | 🔲 | Stub                                                        |
| `documentLink/resolve` | 🔲 | Stub                                                        |
| `textDocument/documentColor` | ➖ | Not applicable to NPL                                       |
| `textDocument/colorPresentation` | ➖ | Not applicable to NPL                                       |
| `textDocument/moniker` | 🔲 | Stub                                                        |

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

1. ✅ `textDocument/hover` — type info & docs on hover
2. ✅ `textDocument/definition` — go to definition
3. ✅ `textDocument/documentSymbol` — outline / breadcrumb navigation
4. ✅ `textDocument/references` — find all references
5. ✅ `textDocument/rename` / `textDocument/prepareRename` — safe rename
6. ✅ `textDocument/completion` — auto-complete
7. ✅ `textDocument/semanticTokens/full` — rich syntax highlighting
8. ✅ `textDocument/foldingRange` — code folding
9. ✅ `textDocument/signatureHelp` — function signature hints
10. ✅ `textDocument/codeAction` — quick fixes
11. ✅ `workspace/symbol` — cross-file symbol search
12. ✅ `textDocument/documentHighlight` — highlight other occurrences
13. ✅ `textDocument/inlayHint` — type hints

### Next priorities:

1. `textDocument/formatting` — auto-format code
2. `textDocument/prepareCallHierarchy` — call hierarchy navigation
3. `textDocument/linkedEditingRange` — edit multiple related ranges


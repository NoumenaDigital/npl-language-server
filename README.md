# NPL Language Server

This module implements a Language Server Protocol (LSP) server for NPL.

## Overview

The language server can operate in two modes:

- TCP mode: Listens on a specified port (default: 5007)
- STDIO mode: Communicates through standard input/output

## Development

### Setup

You'll need Maven and Java 21 (graalvm if you want to build binaries) or later installed on your system.

### Build project and run tests

```bash
mvn clean verify
```

## Testing with VS Code

To test the language server with VS Code:

1. Run the `Main::main` function in TCP mode within IntelliJ:

   - Navigate to `com.noumenadigital.npl.lang.server.Main`
   - Edit the run configuration to include `--tcp` as a program argument
   - Run the main function

2. Set up the VS Code extension:

   - Clone the extension repository: `git clone git@github.com:NoumenaDigital/npl-vscode-extension.git`
     - (Check that you're using an appropriate branch for your purposes)
   - Open the project in VS Code
   - Install dependencies with `npm install`

3. Run the extension:

   - Navigate to `extension.ts`
   - Press F5 to start running the extension in instrumented mode

4. Test functionality:
   - Open an NPL project (such as npl-starter)
   - Make changes to NPL files and interact with your codebase

## Native Image Build

The language server can be compiled to a native executable using GraalVM's native-image. This creates a standalone
binary that users can run without installing a JRE or any other dependencies.

### Building the Native Image

1. Install GraalVM JDK 21 or later
2. Build the native executable:
   ```bash
   mvn -Pnative package
   ```

### Running the Native Image

```bash
./target/language-server
```

### Native Image Configuration

The language server uses GraalVM's native-image for creating standalone executables. To generate the necessary
configuration files:

1. Run the configuration generator script:

   ```bash
   ./generate-native-configs.sh
   ```

   This script will:

   - Build a fat jar with all dependencies
   - Run the language server with the native-image-agent
   - Generate configuration files in `src/main/resources/META-INF/native-image`

2. While the server is running, interact with it through your IDE or client to exercise different code paths. The agent
   will automatically collect metadata about classes, methods, and resources used at runtime.

3. Once you've exercised the desired functionality, stop the server (Ctrl+C). The generated configurations will be saved
   in the `src/main/resources/META-INF/native-image` directory.

4. After generating configurations, you can build the native image using:
   ```bash
   mvn -Pnative package
   ```

If you encounter issues with the native image, you may need to manually modify the generated configuration files. Refer
to the
[GraalVM documentation](https://www.graalvm.org/latest/reference-manual/native-image/metadata/AutomaticMetadataCollection/)
for more information on native image configuration.

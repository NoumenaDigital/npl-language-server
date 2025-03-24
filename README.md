# NPL Language Server

This module implements a Language Server Protocol (LSP) server for NPL.

## Overview

The language server can operate in two modes:

- TCP mode: Listens on a specified port (default: 5007)
- STDIO mode: Communicates through standard input/output

## Setup

You'll need Maven and Java 21 (graalvm if you want to build binaries) or later installed on your system.

## Build project and run tests

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

## Automated checks and formatting

You can use `pre-commit` to run some automated checks as well as Kotlin (ktlint) and Markdown formatting whenever you
commit something.

```shell
brew install pre-commit
pre-commit install
```

The checks can be bypassed by running `git commit -n` or `git commit --no-verify`

### detect-secrets

Enforces accidental secret commits with a pre-commit hook using Yelp's detect-secrets tool. The pre-commit hook will
scan staged changes for potential secrets before allowing a commit. If any secrets are detected above baseline, the
commit will be aborted, ensuring that no sensitive information is pushed to the repository.

No need to install locally, just make sure you have the pre-commit hook installed.

To check locally:

```shell
pre-commit run detect-secrets --all-files
```

To generate a new baseline, install the version of `detect-secrets` that's configured in `.pre-commit-config.yaml`
locally (using `pip` or `brew` -- just double check that you have the right version) and run:

```shell
detect-secrets scan > .secrets.baseline
```

### ktlint

This project enforces a standard code formatting style using [ktlint](https://github.com/pinterest/ktlint) via the
automatic `pretty-format-kotlin` [pre-commit hook](https://github.com/macisamuele/language-formatters-pre-commit-hooks).

The `pretty-format-kotlin` hook automatically formats Kotlin code with ktlint rules before committing it.

You can run ktlint for the entire project using the `pre-commit` like so:

```shell
pre-commit run pretty-format-kotlin --all-files
```

### prettier

We use [prettier](https://prettier.io) to format our Markdown. The configuration is found in
[.prettierrc.yml](.prettierrc.yml).

To format all Markdown files in the project, run (needed if e.g. the corresponding job is failing):

```shell
pre-commit run prettier --all-files
```

Note that `prettier` formats tables differently than IntelliJ, so you might want to disable IntelliJ's
`Incorrect table formatting` Markdown inspection.

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

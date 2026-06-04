# Development

## Prerequisites

- JDK 21
- IntelliJ IDEA (or PhpStorm) with the "Plugin DevKit" plugin

## Build & run

```bash
./gradlew buildPlugin                    # Build the plugin ZIP
./gradlew runIde                         # Run IDE with the plugin
./gradlew runIdeForUiTests               # Run IDE with robot-server for UI tests
./gradlew test                           # Run tests
./gradlew check                          # Run tests + code coverage
./gradlew verifyPlugin                   # Verify plugin structure + IDE compatibility
```

The built ZIP is at `build/distributions/`.

## Update mechanism

The plugin uses a **custom IntelliJ plugin repository** hosted on GitHub Pages:

1. `docs/updatePlugins.xml` — the repository manifest, generated from `CHANGELOG.md`
2. `src/main/resources/META-INF/plugin.xml` — contains `updateUrl` pointing to the XML
3. IDE checks this URL for updates through the standard `Settings → Plugins` UI

### Regenerate the manifest

After updating `CHANGELOG.md`:

```bash
./gradlew generateUpdatePluginsXml
```

This parses all `## [X.Y.Z]` sections from `CHANGELOG.md`, converts Markdown to HTML, and writes `docs/updatePlugins.xml` with entries for every version.

## Release process

The release is fully automated via GitHub Actions.

### Step-by-step

1. Make your changes and commit them to `main`.

2. Update `gradle.properties`:
   ```properties
   pluginVersion = X.Y.Z
   ```

3. Add an entry to `CHANGELOG.md`:
   ```markdown
   ## [X.Y.Z]
   - Your change here
   ```

4. Regenerate the plugin repository manifest:
   ```bash
   ./gradlew generateUpdatePluginsXml
   ```

5. Commit everything and push to `main`:
   ```bash
   git add -A
   git commit -m "Release X.Y.Z"
   git push origin main
   ```

6. **CI (build.yml)** automatically:
   - Builds the plugin
   - Runs tests and code quality checks
   - Creates a **draft** GitHub Release

7. Go to the repository's Releases page on GitHub, review the draft, and publish it.

8. **CI (release.yml)** automatically:
   - Rebuilds the plugin from the tag
   - Uploads the ZIP to the release
   - Runs `./gradlew generateUpdatePluginsXml`
   - Commits and pushes `docs/updatePlugins.xml` to `main`
   - GitHub Pages immediately serves the updated manifest

9. Users get the update notification in their IDE within 24 hours (or immediately on "Check for Updates").

### CI pipelines

| Workflow | File | Trigger |
|----------|------|---------|
| **Build** | `.github/workflows/build.yml` | Push to `main`, pull requests |
| **Release** | `.github/workflows/release.yml` | GitHub Release published |
| **UI Tests** | `.github/workflows/run-ui-tests.yml` | Manual (`workflow_dispatch`) |

## Project structure

```
.
├── .github/workflows/        # CI/CD
├── build.gradle.kts          # Gradle build (IntelliJ Platform Plugin)
├── CHANGELOG.md              # All versions and change notes
├── docs/
│   └── updatePlugins.xml     # Custom plugin repository manifest (auto-generated)
├── gradle.properties         # Plugin version, SDK version, etc.
├── src/
│   └── main/
│       ├── kotlin/           # Plugin source code
│       └── resources/META-INF/plugin.xml
└── development.md            # This file
```

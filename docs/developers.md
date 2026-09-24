![Logisim Revolution wordmark](../artwork/logisim-revolution-wordmark.svg)

# Developing Logisim Revolution

[Back to the project overview](../README.md).

## Prerequisites

Use **JDK 21 or newer**. The repository includes Gradle wrapper scripts, so no separate Gradle installation is required. Set `JAVA_HOME` to your JDK when your environment does not pick it automatically.

## Build and run

From the repository root on Linux or macOS:

```bash
./gradlew run
./gradlew build
```

On Windows, use `gradlew.bat` in place of `./gradlew`. Useful focused tasks:

| Command | Result |
| --- | --- |
| `./gradlew test` | Run the JUnit suite. |
| `./gradlew checkstyleMain checkstyleTest` | Check Java source style. |
| `./gradlew shadowJar` | Build `build/libs/logisim-revolution-<version>-all.jar`. |
| `./gradlew genFiles` | Generate build metadata before an Eclipse import. |
| `./gradlew createAll` | Build installers for the current host platform. |

The application mark, document icon, and wordmark have editable SVG masters in [artwork](../artwork). To regenerate their runtime and installer assets, run `./artwork/update_assets.sh` with Python 3, `rsvg-convert`, and ImageMagick installed.

The current development JAR is `build/libs/logisim-revolution-5.1.0dev-all.jar`. Run it with `java -jar`; the filename changes when [gradle.properties](../gradle.properties) changes.

Gradle generates `com.cburch.logisim.generated.BuildInfo` under `build/generated/logisim/java`. Edit the version and project URL in `gradle.properties`, not the generated class. Platform packages use the JDK's `jpackage` and may require host-specific packaging tools.

## Working on the project

The simulator and circuit format still use the original `com.cburch.logisim` Java packages and serialized component/library IDs. Keep those IDs stable so existing `.circ` projects continue to open. Application UI code lives under `gui/`; the component libraries and simulation engine remain separate.

Tests are JUnit 5 under `src/test/java`. Run a focused class with:

```bash
./gradlew test --tests com.cburch.logisim.std.ttl.Ttl7493Test
```

Use the existing style and localization mechanisms. Start with the [style guide](style.md), [localization guide](localization.md), and [TTL component checklist](implementing_ttl_components.md) where relevant. User-visible strings belong in the English base bundle and should be localized through the package `Strings.S`.

Before proposing a change, run tests and style checks relevant to it and inspect rendered UI changes at more than one scale. Record user-facing changes in the top `@dev` section of [CHANGES.md](../CHANGES.md), crediting the author. The repository's CI workflows are the source of truth for contribution gates.

## Reporting and attribution

Use the [Logisim Revolution repository](https://git.briggen.dev/NilsBriggen/Logisim-Revolution) for this fork's source and issue reporting. For the history and people behind the original Logisim and Logisim-evolution projects, see the [credits](credits.md).

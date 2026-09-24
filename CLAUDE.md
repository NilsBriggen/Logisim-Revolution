# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Logisim-evolution is a Java 21 / Swing digital logic designer and simulator, built with Gradle.

## Commands

```bash
./gradlew run                 # build and launch the app
./gradlew build               # full build + tests + checkstyle
./gradlew test                # run tests (JUnit 5 / junit-jupiter)
./gradlew test --tests com.cburch.logisim.std.ttl.Ttl7493Test   # single test class
./gradlew checkstyleMain      # lint main sources (checkstyleTest for tests)
./gradlew shadowJar           # fat jar -> build/libs/logisim-revolution-<ver>-all.jar
./gradlew createAll           # platform installer(s) into build/dist (jpackage; host platform only)
./gradlew genFiles            # run code generation only (needed before importing into Eclipse)
```

Gitea CI (`.gitea/workflows/ci.yml`) runs `./gradlew build` on JDK 21, including tests and
Checkstyle. A manual Gitea workflow builds Linux packages and dispatches Windows and macOS
packages on the public GitHub mirror; it publishes a Gitea release only after all targets
succeed. See `.gitea/README.md`.

## Build-time code generation

`compileJava`/`compileTestJava` depend on `genFiles`, which runs `genBuildInfo`. That task writes
`com.cburch.logisim.generated.BuildInfo` (version, git branch/hash, build timestamp, URL) into
`build/generated/logisim/java` — a generated source root. Never edit it by hand; change
`gradle.properties` (`version`, `url`) instead.

`processResources` depends on `generateHelpSets`, which runs the separate `src/docgen/java` source set
(`com.cburch.logisim.docs.DocumentationGenerator`) over `src/main/doc/help-sets.xml` +
`src/main/resources/doc` to emit JavaHelp descriptors into `build/generated/documentation-resources`.

## Architecture

**Component model.** A component type is a `ComponentFactory`, in practice a subclass of
`InstanceFactory` (`com/cburch/logisim/instance/`), which supplies `paintInstance(InstancePainter)`,
`propagate(InstanceState)`, port/attribute declarations, and an optional `HdlGeneratorFactory` passed
to its constructor. Factories are stateless singletons (conventionally a `public static final FACTORY`
field); **all per-instance state lives in an `InstanceData` retrieved via
`InstanceState.getData()`/`setData()`** — never in factory fields. Values are read and written through
`InstanceState.getPortValue(port)` / `setPort(port, value, delay)` using `Value` and `BitWidth` from
`com/cburch/logisim/data/`.

**Libraries and registration.** Components are exposed as `Tool`s (usually `new AddTool(X.FACTORY)`)
grouped into a `Library` (e.g. `std/gates/GatesLibrary.java`); larger libraries (`ttl`, `io`, `soc`)
instead list `FactoryDescription` entries so tools are constructed lazily. All built-in libraries are
enumerated in `src/main/java/com/cburch/logisim/std/Builtin.java`. Every library, tool, and component
declares a `public static final String _ID`; **these IDs are serialized into `.circ` project files and must never
change** once released, or existing projects stop loading.

**Simulation.** `circuit/` holds the netlist and runtime: `Circuit`, `CircuitState` (per-circuit value
and component-data store, nested for subcircuits) and `Propagator`, which drives value propagation with
delays. `proj/Project` ties a loaded file to its simulator and GUI; `file/` handles `.circ`
load/save (`XmlWriter`, `Loader`); `gui/` is the Swing UI; `tools/` the toolbox and editing tools;
`comp/` the lower-level component/wire abstractions (`Component`, `ComponentFactory`, `EndData`).
`CircuitState` is itself an `InstanceData`, which is how subcircuit state nests.

**HDL / FPGA.** `fpga/` contains board models, design-rule checks, and the download/synthesis flow.
Components generate HDL by supplying an `HdlGeneratorFactory` (usually extending
`AbstractHdlGeneratorFactory` in `fpga/hdlgenerator/`), which declares ports, wires, parameters and
emits VHDL/Verilog; `InlinedHdlGeneratorFactory` is for components rendered inline. Passing `null` as
the generator means "no HDL support". `vhdl/` supports user-supplied VHDL components; `soc/` implements
the SoC library (RV32IM and Nios2 soft cores, SocBus, memory/PIO/VGA/DMA/JtagUart peripherals, plus
ELF loading and an assembler/disassembler).

**Localization.** Each package has a `Strings` class holding a
`LocaleManager("resources/logisim", "<bundle>")`, used as a static import `S`, with `S.get("key")` /
`S.getter("key")` (a `StringGetter` for lazily-localized labels). Bundles live in
`src/main/resources/resources/logisim/strings/<bundle>/<bundle>.properties` (English, the fallback) with
`<bundle>_<lang>.properties` siblings. A new language must also be added to
`resources/logisim/settings.properties`. Translation files are maintained with `trans-tool`; lines
prefixed `# ==> key =` mark untranslated keys.

## Conventions

- Code style is **Google Java Style** via Checkstyle (`google_checks.xml` shipped with the tool),
  relaxed by `checkstyle-suppressions.xml` in the repo root — 2-space indent, and the codebase widely
  uses the `final var` idiom. The full Checkstyle task runs as part of Gitea CI.
- Every source file carries the project's GPLv3 header comment block.
- Files are UTF-8, LF line endings, no trailing whitespace, and end with a newline
  (`.pre-commit-config.yaml.dist` enforces this; copy it to `.pre-commit-config.yaml` to use).
- **CI enforces two PR requirements** (`scripts/ci/pr_checks.py`): an entry in the topmost `@dev`
  section of `CHANGES.md` crediting the author (`* Fixed the frobnicator (@nick).`), and a closing
  reference to an existing open issue (`Closes #1234`) in the PR description. Opt out with
  `NO_CHANGELOG_ENTRY` / `NO_CHANGELOG_AUTHOR_CREDIT` / `NO_TICKET` in the description.
- All work targets the `main` branch.

## Tests

Tests are JUnit 5 under `src/test/java`, mirroring the main package layout, many extending the shared
`com.cburch.logisim.TestBase`. Test components through their public `InstanceState`/port API rather
than private helpers. Logisim also has a runtime circuit-verification feature (Test Vectors) documented
in `docs/test_vector.md` — unrelated to the JUnit suite.

## Adding a TTL component

`docs/implementing_ttl_components.md` is the authoritative checklist. In short: extend
`AbstractTtlGate` in `std/ttl/` with data-sheet-accurate one-based physical pin numbers (logical ports
are zero-based after unused/power pins are dropped), implement `propagateTtl()`, then complete all
integration points in the same change — `FactoryDescription` in `TtlLibrary`, the `TTL<n>` keys in
`std.properties`, the entry in `resources/doc/en/html/libs/ttl/index.html`, and the `CHANGES.md` line.

## Further docs

`docs/developers.md` (build & contribute), `docs/style.md` (style setup), `docs/localization.md`,
`docs/implementing_ttl_components.md`, `docs/test_vector.md`, `docs/automatic_library_import.md`.

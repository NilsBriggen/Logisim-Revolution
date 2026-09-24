# Isolated visual QA

`visual-qa.mjs` runs the real Swing application in a private KWin/Xwayland display.
It does not attach to the user's desktop. Each run has its own Java preferences,
home, XDG directories, copied circuit and immutable copy of the application jar.
Later builds cannot replace a running JVM's lazily loaded classes.

Requirements: Linux, Java 21, Node.js, KWin/Xwayland, `xdotool` and ImageMagick.
Build with `./gradlew shadowJar`, then:

```sh
node scripts/visual-qa.mjs start --theme dark --scale 1.6 --size 2560x1600 --file scripts/fixtures/ui-smoke.circ
```

The command prints an owned `/tmp/logisim-visual-qa-*` directory. Use that exact
directory for later commands. `windows` lists real window IDs and geometry.

```sh
node scripts/visual-qa.mjs windows /tmp/logisim-visual-qa-EXAMPLE
node scripts/visual-qa.mjs input /tmp/logisim-visual-qa-EXAMPLE windowactivate --sync WINDOW_ID key --clearmodifiers ctrl+comma
node scripts/visual-qa.mjs capture /tmp/logisim-visual-qa-EXAMPLE WINDOW_ID preferences.png
node scripts/visual-qa.mjs stop /tmp/logisim-visual-qa-EXAMPLE
```

Replace both placeholders with returned values. Dialogs and heavyweight popups
have their own IDs; a main-window capture does not prove a popup was inspected.
Allow asynchronous UI actions to settle and inspect each image.

Options: `--theme light|dark|system`, `--scale auto|NUMBER`, `--size WIDTHxHEIGHT`,
`--file PATH`, `--jar PATH`. Auto omits the stored Scale preference. The manifest
records jar SHA256, revision, dirty-worktree inventory and PID start times. An
inventory is not proof every edit was compiled; coordinate builds and record the
binary used for acceptance.

`stop` checks ownership and PID start times before stopping only that session's
processes. Evidence remains on disk. Always stop owned sessions when finished.

## Smoke fixture

`fixtures/ui-smoke.circ` contains a clocked example, RAM/ROM, a subcircuit, an
inverter, an oscillator and a width mismatch. It is a UI fixture, not a verified
FPGA design. Its `inv` circuit can run `inverter-vectors.txt`: the intentional
expected result is **two passes and two failures**, not a simulator regression.

Check both themes at 1.0, 1.6 and 2.0; include constrained widths, divider drags,
cancellation, keyboard paths and reopened panels. Unit tests and rendered checks
are separate acceptance gates. Virtual displays do not establish physical
4K/mixed-monitor scaling, printer output or FPGA hardware behavior.

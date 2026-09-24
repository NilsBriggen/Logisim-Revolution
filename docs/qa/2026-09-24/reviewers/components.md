# Components: bounded QA review and fix plan

Reviewed 2026-09-24 at HEAD `0a62266649937f3c2cbcb1ef495b6d9be6ba2d5a` in `/home/nilsb/Documents/projects/logisim-revolution`.

Scope: the 32 findings in [inspect-components.json](/tmp/logisim-qa-recovery-20260924/reports/inspect-components.json), plus [verify-components-partial.md](/tmp/logisim-qa-recovery-20260924/reports/verify-components-partial.md). [components.json](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/reviewers/components.json) supplies all 32 dispositions, exact evidence/source paths, reproductions, fixes and acceptance checks. No other inspection reports were reviewed.

## Result and provenance

**One P1 workflow issue: Reptar propagation. No P0 data loss demonstrated.** Most confirmed issues are P2 rendering/layout/export problems. Dispositions: 19 confirmed-visual, one confirmed-source, one confirmed-runtime using a recovered trace, ten design recommendations, and one duplicate. These are claim dispositions, not independent bug counts.

Actual recovered UI images were displayed and inspected. `view_image` failed with the sandbox mountinfo error; the authorized read-only base64 fallback emitted the image data. Enlarged crops were not treated as physical-DPI measurements. Original screenshots and partial verifier records are prior-agent evidence, not newly executed workflows.

[22-gates.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components/22-gates.png) displays `main/2532c84d` in the title. That embedded metadata is stale and is not treated as the actual binary revision. Source and binary identities are recorded independently:

- Reviewed source HEAD: `0a62266649937f3c2cbcb1ef495b6d9be6ba2d5a`.
- Current QA binary: `/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/logisim.jar`.
- Matching current build artifact: `/home/nilsb/Documents/projects/logisim-revolution/build/libs/logisim-evolution-5.1.0dev-all.jar`.
- Both JARs have SHA-256 `c854edbbea34d6cfc0e53ac9cf70ef5b2a5dcf550a3959ff0ed99d852950d433`, freshly checked during finalization.

The source comparison from 2532c84d to HEAD is only a source-history check; it does not date or identify the code in the JAR. Current hash equality establishes that the two files contain identical bytes, not a per-screenshot capture digest. This reviewer did not recover per-capture binary hashes. Recovered UI observations and present source checks remain valid evidence with those separate identities; no fresh runtime or physical-DPI acceptance is claimed.

Read AGENTS.md and the full qa.sh before considering runtime. No new QA session was started; no processes needed stopping. No production code, real user files, preferences or autosaves were written or cleared; no commit/push or child agents. Initial git status showed untracked AGENTS.md. The final check also showed untracked docs/qa/ created during concurrent work; this reviewer did not create, inspect or modify it.

## Root-cause work and acceptance

### Reptar unsupported simulation — P1, components-22

The recovered verifier trace reports `UnsupportedOperationException: Reptar Local Bus simulation not implemented`; [ReptarLocalBus.java](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/std/io/ReptarLocalBus.java:171) still throws unconditionally. [Simulator.java](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/circuit/Simulator.java:551) catches the failed propagation attempt. This blocks meaningful use of that component and interrupts propagation in a containing circuit; it does not prove that every circuit stops or the process dies.

Reproduce with a disposable circuit containing Reptar and an unrelated pin path. Plan an explicit FPGA-only/unsupported simulation contract, visible capability notice, and correctly sized UNKNOWN outputs instead of throwing. A bare no-op does not itself guarantee output initialization. Acceptance: repeated reset/poke/tick yields no exception, unrelated circuitry settles, and Reptar exposes the specified unsupported state through its public port API.

### Default contrast — P2, components-03/05/08/09/21; design tuning in 07

Direct evidence: [POR](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components-verify/07-por-200.png), [Constant](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components-verify/07-databus-const-200-x2.png), [ROM](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components-verify/09-rom-100-x2.png), [RTC and I/O labels](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components-verify/11-io-100-a-x2.png), [bus trace placeholder](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components/21-soc-bus-100.png), [DMA captions](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components/21-soc-dma-100.png), [annotation/editor](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components/24-texttool-typing-z.png), [high input pin](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components/23-pin1-x6.png).

Shared cause: fixed light fills with light theme-selected ink, or literal black/blue/white ink surviving elsewhere. [MemState.java](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/std/memory/MemState.java:227), [Constant.java](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/std/wiring/Constant.java:252), [TextAttributes.java](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/std/base/TextAttributes.java:39), [Pin.java](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/std/wiring/Pin.java:875) and per-ID JSON pointers establish the paths.

Plan paired built-in surface/ink tokens and contrast-aware value glyphs. Introduce explicit auto/default colour semantics for new content where needed. **Preserve stored custom colours, including black, white and blue; never infer auto by RGB equality.** Bright off segments warrant emphasis tuning, not a claim of wrong simulated digits. Ground's value colour is intentional; TTL edges remain visible against the package, so the report overstated their invisibility.

Acceptance: isolated fixtures in dark/light and printer view, scales 1.0/1.6/2.0 and zooms 100/200%; choose a 4.5:1 text-contrast target. Custom colour attributes round-trip unchanged. Unknown/error states remain distinct. Shared MemState supports RAM breadth in source, but this review visually checked ROM rather than every memory variant.

### Caption and gate-label geometry — P2, components-02/11/12

[TTL captions](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components/20-ttl-100-7400.png), [splitter](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components/30-export-splitter.png), [plexers](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components/15-plexers-200-row1.png), [arithmetic](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components/16-arith-200-row1.png), [bit extender](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components-verify/07-bitext-200-x3.png), [Telnet](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components/18-io-100-pio.png), and [gate labels](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components/09-gates-200-labels.png) show local crowding. [AbstractGate.computeLabel](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/std/gates/AbstractGate.java:87) centres labels inside symbols. DMA puts three long strings into three 100-unit panels.

Plan measured caption interiors, reserved port gutters, explicit circuit-unit fonts, concise status text with full tooltip, and an external gate-label option. Avoid moving existing ports or shrinking critical bit numbers below readability. Acceptance: separated fixtures, all facings, both themes and export, short/long/localized labels, no text crossing ports or state glyphs, identical existing connectivity.

Not accepted: every small caption is unreadable at 200%; separately overlapping SoC fixture components prove intrinsic Nios/PIO title bugs. Those claims exceed the inspected evidence.

### LED-bar dot placement — P2, components-17

[Detached export ellipses](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components/31-export-io-mid.png) match [DotMatrixBase.drawCircle](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/std/io/DotMatrixBase.java:353) multiplying already absolute coordinates. [LedBar](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/std/io/LedBar.java:119) sets scaleY=3; default DotMatrix scale is 1.

Plan `fillOval(x + scaleX, y + scaleY, 8 * scaleX, 8 * scaleY)`. Reproduce via printer-view export of a bar away from origin. Acceptance: multiple nonzero positions, print view on/off and 1x/2x zoom, every ellipse in its cell; use default DotMatrix as control. Live circular bars use the same faulty primitive but were not freshly exercised.

### Export fidelity — P2, components-15/18

[Aliased edge](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components/33-export-aliasing.png) is visible. Recounted original PNGs: `export-gates.png` 840×649 and `export-all/gates.png` 1680×1298, each exactly three colours. [ExportImage.java](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/main/ExportImage.java:238) omits screen AA hints. [VGA printer-view status](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components/32-export-vga.png) is black on dark; [pin badges](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components/28-export-pins-z.png) are cramped.

Plan raster AA/text hints, paired print status ink/surface, and fitted badges. Preserve actual framebuffer colours; ink-saving outlines should be optional. Acceptance: smooth curves/text with correct image dimensions, readable status and fitted x1/x8 badges. Inspect PNG/GIF/JPEG individually and check vector regressions. Physical print/PDF was not inspected; probe colour and black screen fills alone are design concerns. The original SocVgaShape pointer did not explain the whole normal VGA body.

### Placement preview — P2, components-19

[Ghost screenshot](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components/36-ghost-z.png) matches [AbstractGate.paintBase](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/std/gates/AbstractGate.java:383) overriding ghost colour.

Plan a ghost-aware shared painter or composite scoped to copied Graphics2D, preserving user ghost preferences. Acceptance: clearly distinct preview in both themes/zooms, readable ports, no alpha leakage into placed components. No collision-prevention defect was demonstrated.

### Label migration and startup warning — P2, components-13/14; 29 duplicates 14

**Scope correction:** only VHDL-invalid labels are candidates; random suffixes are added when normalization changes the trimmed label. Reopening the original invalid input can differ. Already valid/sanitized labels do not randomly change every load.

**Version audit:** [XmlReader.readLibrary](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/file/XmlReader.java:1149) calls ensureLogisimCompatibility at 1152, then considerRepairs at 1154. Version guards at 1024/1046/1076/1078 belong to subsequent repairs and do not guard the sanitizer. Normal project loading reaches this through [LogisimFile.java](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/file/LogisimFile.java:303).

Plan preserve display text and derive deterministic collision-safe HDL identifiers during generation, or make required legacy conversion explicit/stable. Preserve reference remapping. Acceptance: modern/legacy/absent-source disposable fixtures with valid/invalid/colliding names, repeat reads and save/reopen; stable display labels and HDL IDs. No save-based data-loss test occurred here.

[The splash](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components/47-splash-dialog.png) covers warning text but OK remains visible. [Circuit.removeWrongLabels](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/circuit/Circuit.java:939) clears matching labels and opens a null-parent placement-worded modal. Plan deferred, owned load diagnostics after splash dismissal. Acceptance: visible keyboard-reachable actions, accurate load wording and checked open/cancel/save semantics on copies. New artwork is optional; components-29 is not another startup bug.

### Shell handoff — P2 symptoms, components-16/24/25/26/28/31

Historical evidence: [small form controls](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components/27-export-dialog-win.png), [header tokens](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components/34-print-params.png), [arrow under zoom overlay](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components/16-arrow-under-pill.png), [clipped palette labels](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components/45-palette-wiring-x2.png), [inspector spacing](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components/25-inspector-z.png), [small accelerators](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components/26-filemenu-z.png), [tab/list mismatch](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/components/22-gates.png). Coordinate these with shell/palette/inspector owners; do not count them again as component-painter bugs.

Plan reserve overlay bounds, scalable form controls and header-token help/preview, measured caption layouts, and single-application font scaling. Tab/list source hypothesis needs correction: [CircuitListView](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/CircuitListView.java:312) already listens to ACTION_SET_CURRENT and calls syncSelection. Trace event ordering/current target before modifying synchronization.

Acceptance: current HEAD at default/minimum widths and scales 1.0/1.6/2.0; tab/list/canvas/title agree after mouse/keyboard/list/appearance/HDL navigation; accelerators remain legible without double scaling. The screenshot symptom is confirmed historically; exact sequence and present recurrence are unverified.

## Design decisions, not established simulation defects

Components-01/04/06/10/20/23/27/30/32 are recommendations. Define physical DPI/canvas-unit behavior before changing canvas 100%. Preserve custom appearance colours and actual display states. Retain TTL orientation, bus-width meaning and existing TCL/HDL identifiers. Build metadata is intentionally limited to non-stable versions. Recovery choices should clearly express open recovered work/save copy/discard, but the screenshot does not show broken recovery.

Reject mass recolouring of custom black/white shapes, removing matrix error colours merely because they look bright, and treating every black framebuffer as decorative UI. Prefer explicit new defaults and optional display controls.

## QA harness correction and untested coverage

The original qa.sh isolates `java.util.prefs.userRoot` but not `user.home`. [Loader.java](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/file/Loader.java:179) writes unnamed autosaves there; [Startup.java](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/start/Startup.java:999) scans there. Recovered autosave-dialog content cannot be assumed to be private test data.

Before any future launch, patch a scratch harness to create `$D/home` and pass `-Duser.home=$D/home`, or create a unique temporary home and pass it through scoped JAVA_TOOL_OPTIONS. Keep preferences/config private, copy fixtures, never clear real autosaves, and validate owned PIDs before stopping only the assigned session. `shot` hardcodes 2560×1600; derive actual dimensions or use `win` for nondefault sizes.

Harness acceptance: disposable unnamed autosave create/recover/discard stays under the QA home; no user-location writes; captures match actual window dimensions; ownership checked at start/stop.

Not tested: fresh UI launch, physical DPI, exhaustive components, new round-trip/data-loss experiment, live circular LedBar, physical print/PDF, fresh GIF/JPEG/vector exports, current-HEAD tab/list sequence, or autosave lifecycle. No new production findings beyond the assigned scope.

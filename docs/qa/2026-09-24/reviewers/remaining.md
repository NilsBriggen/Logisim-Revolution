# Remaining areas: bounded visual QA and fix plan

Reviewed simulation, projects, HDL/FPGA/SoC and keyboard at source HEAD `0a6226664`. **The four original agents were interrupted; this is a new recovery review, not their completed output.** No completed finding IDs were assigned. The JSON gives dispositions for every registry row below.

**Result:** four new confirmed product root causes (one P1, two P2, one P3), one unresolved candidate, and seven evidence groups merged into existing findings. No new P0/data-loss finding. The shared drawer defect is also P1 because it obstructs normal timing/test-result inspection; it is counted under existing `shell-03`, not again here.

## Evidence and isolation

Live work used private session `cx-remaining`, dark theme, interface scale 1.6 and a 2560×1600 display. The circuit was a uniquely named copy of the prior synthetic `simtest.circ`; test vectors were copied too. No production code, real user project, commit or push was changed by this reviewer. Parallel review work created an untracked `docs/qa/` directory during this pass; it was left untouched.

The supplied jar is `/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/logisim.jar`, SHA-256 `c854edbbea34d6cfc0e53ac9cf70ef5b2a5dcf550a3959ff0ed99d852950d433`. It displays build ID `main/2532c84d`, time `2026-09-24T16:07:16+0200`, while checkout HEAD is `0a6226664`. Exact jar-to-HEAD equivalence is not proven. The principal implicated source files are unchanged between those commits; the whole HEAD build is not visually accepted by this review.

The original harness omitted `user.home` isolation, and the first launch reached an existing autosave prompt. I did **not** discard that autosave. I relaunched with `JAVA_TOOL_OPTIONS=-Duser.home=/tmp/logisim-qa-recovery-20260924/remaining-evidence/java-home`, verified it and the private preferences root using the live JVM's properties, then performed the workflow checks. Fresh work remained under /tmp. Java was 21.0.12.

`view_image` and normal exec both failed with the sandbox mountinfo error. Scoped escalated exec succeeded. Images were actually displayed through the Node image helper and forwarded as images; native crops were inspected for small text. Earlier screenshot provenance is explicitly identified per item.

## New findings

### remaining-hdl-01 — P1 — HDL-only Execute throws an icon ClassCastException and strands the commander

confirmed-runtime. Fresh live reproduction plus current source and recovered log.

Generate HDL only > Execute immediately disables the clock, annotation, board and action controls. Progress remains Idle; no visible error is shown. The log records FlatSVGIcon cannot be cast to ProjectAddIcon at FpgaCommander.java:404, before Download is constructed.

- Reproduce: Open a disposable project. FPGA > Synthesize & Download; leave Generate HDL only selected. Click Execute; inspect disabled controls and app log.
- Cause: StopButton receives an SVG icon, but start, stop and completion still cast it to the removed ProjectAddIcon type. Execute disables controls before the failing cast.
- Source: `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/fpga/gui/FpgaCommander.java:208`; `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/fpga/gui/FpgaCommander.java:397`; `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/fpga/gui/FpgaCommander.java:404`; `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/fpga/gui/FpgaCommander.java:431`.
- Plan: Remove all three obsolete casts; express stop state with button enabled/state and the SVG icon API. Restore controls and show an actionable error on every failed start or worker exit.
- Acceptance: With no vendor software installed, Generate HDL only starts the internal generation/DRC path and either produces files in a temporary destination or presents a useful design error. Repeat start, cancel, and retry without a ClassCastException or stranded controls. Run external download checks separately when a board/toolchain exists.
- Limit: External synthesis/programming was not attempted; this failure occurs before any toolchain is invoked.
- Evidence: [17-fpga-before.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/remaining-evidence/17-fpga-before.png), [18-fpga-after.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/remaining-evidence/18-fpga-after.png), [app.log](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/remaining-evidence/app.log), [app-run1.log](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/hdl/app-run1.log).

### remaining-keyboard-01 — P2 — Ctrl+Tab and Ctrl+Shift+Tab do not cycle circuit tabs from the canvas

confirmed-runtime. Fresh live forward/reverse checks; Ctrl+W positive control.

With main and inv open, canvas focus followed by Ctrl+Tab left main selected. With three tabs open, Ctrl+Shift+Tab from inv left inv selected. Ctrl+W from the same canvas successfully closed inv.

- Reproduce: Open at least two circuits from the Circuits list. Click empty canvas to give it focus. Press Ctrl+Tab and Ctrl+Shift+Tab; compare active editor tabs. Press Ctrl+W as a positive control.
- Cause: The shortcuts are installed in the tab strip's WHEN_IN_FOCUSED_WINDOW map. No corresponding focus-traversal override was found on Canvas/EditorTabs. Traversal interception is the likely cause, not yet instrumented; live failure is established.
- Source: `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/EditorTabs.java:90`; `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/EditorTabs.java:94`; `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/EditorTabs.java:98`; `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/main/Canvas.java`.
- Plan: Trace the focus manager's consumed key events and route next/previous-editor actions at the owning root pane/focus boundary, excluding those chords from traversal where necessary. Keep ordinary Tab and text-editor behavior intact.
- Acceptance: At least three circuit/HDL tabs wrap forward and backward from canvas, circuit list, filter, and code editor focus. Ctrl+W still closes one tab; normal Tab traverses controls with visible focus.
- Limit: Window-manager and platform portability beyond the private Linux KWin/Xwayland session is untested.
- Evidence: [13-tab-after-gc-crop.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/remaining-evidence/13-tab-after-gc-crop.png), [14-ctrl-tab-crop.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/remaining-evidence/14-ctrl-tab-crop.png), [19-reverse-tab.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/remaining-evidence/19-reverse-tab.png), [20-close-tab-crop.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/remaining-evidence/20-close-tab-crop.png).

### remaining-simulation-01 — P2 — Oscillation stops propagation without synchronizing the run controls

confirmed-visual. Recovered images actually viewed; current source confirms missing state notification. Not freshly reproduced.

Recovered osc2 captures show Oscillation apparent and a paused status, while the toolbar still offers Pause and the Simulate menu still checks Auto-Propagate.

- Reproduce: Use the recovered simtest2.circ oscillator fixture in a fresh disposable copy. Open osc2 and poke en to trigger sustained oscillation. Compare the canvas/status error with the toolbar and Auto-Propagate menu state.
- Cause: The oscillation branch clears autoPropagating and autoPropagatingUnsynchronized, but the later fireSimulatorStateChanged call is gated only by clockDied. A propagation-completed notification does not update all run-state controls.
- Source: `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/circuit/Simulator.java:585`; `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/circuit/Simulator.java:588`; `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/circuit/Simulator.java:610`; `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/main/SimulationToolbarModel.java:94`.
- Plan: Emit a simulator state-change event when oscillation changes automatic propagation, with UI updates dispatched consistently to the EDT. Keep the error visible and make reset/recovery state explicit.
- Acceptance: After a deliberately triggered oscillation, Auto-Propagate is unchecked and the toolbar shows Resume/Run everywhere it appears. Reset clears the error and a subsequent start updates all controls.
- Limit: This is misleading state feedback, not evidence of data loss or incorrect saved circuit data.
- Evidence: [95z-combo.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/simulation/95z-combo.png), [94z-menu-after-osc.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/simulation/94z-menu-after-osc.png), [inspect-simulation-partial.md](/tmp/logisim-qa-recovery-20260924/reports/inspect-simulation-partial.md).

### remaining-hdl-02 — P3 — SoC transaction dialog identifies the target bus as null

confirmed-visual. Recovered native window capture viewed; exact current source cause identified.

The dialog title reads Insert a transaction to bus: null, although the memory-map window can identify the bus.

- Reproduce: In a disposable SoC fixture, right-click a bus. Choose Insert a bus transaction. Inspect the dialog title.
- Cause: actionPerformed passes info.getName(), the inherited JMenuItem component name. The constructors call super(label) but never set that name. The resulting null is concatenated into the window title.
- Source: `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/soc/bus/SocBusMenuProvider.java:71`; `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/soc/bus/SocBusMenuProvider.java:97`; `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/soc/bus/SocBusMenuProvider.java:174`; `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/soc/bus/SocBusMenuProvider.java:262`.
- Plan: Carry an explicit bus display name or derive it from the instance label with the existing type@coordinates fallback. Do not use the Swing component name as domain identity.
- Acceptance: Transaction windows for two differently named/unnamed buses show distinct correct names, including a stable fallback for an unlabeled bus. No title contains null.
- Limit: Recovered SoC visuals only; bus execution was not rerun in this review.
- Evidence: [83-insert-transaction.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/hdl/83-insert-transaction.png), [82-memory-map.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/hdl/82-memory-map.png).

### remaining-hdl-03 — unrated — Recovered TCL/HDL editor open logs X11 Window must not be zero

unverified. Recovered stack inspected; not reproduced on this session or a normal desktop.

app-run3.log contains IllegalArgumentException: Window must not be zero while HdlContentEditor.setVisible calls Dialog.setVisible through an attribute editor.

- Reproduce: Place a TCL generic component in a disposable project. Open its HDL interface/content attribute editor. Check modal ownership and whether opening succeeds repeatedly.
- Cause: Undetermined; stack implicates native X11 transient/modal ownership. A private compositor or disposed owner may contribute. Do not label it a universal production crash.
- Source: `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/std/hdl/HdlContentEditor.java:322`; `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/generic/AttrTable.java:484`.
- Plan: First reproduce with a visible normal desktop and a fresh owner window; then fix owner lifecycle/modal creation only if reproduced. Record JDK/window-manager versions.
- Acceptance: Repeated open/close from a live project works without an X11 exception on supported desktops; a private-rig-only failure is tracked against the harness.
- Limit: No severity assigned until the effect and reproduction boundary are established.
- Evidence: [app-run3.log](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/hdl/app-run3.log), [inspect-hdl-partial.md](/tmp/logisim-qa-recovery-20260924/reports/inspect-hdl-partial.md).

## Existing findings strengthened by this review

These are deduplication/evidence additions, not seven new bugs. The stale-list entries in `components-28`, `light-19` and the stale-list part of `shell-08` should join `dialogs-17`; other unrelated symptoms in those composite reports should remain separate.

### remaining-shared-01 — P1 — Drawer and panel resizing snaps back; default timing/test-vector content is unusably short

duplicate; merge into **shell-03**. Fresh live divider drags and vector run, backed by source.

Both tested divider positions remain unchanged after dragging. Timing shows only the ruler; loaded test vectors report 2 passed/2 failed but have no visible result rows at the default drawer height.

- Reproduce: Open Timing diagram and select clk. Drag the drawer divider upward and the Circuits divider rightward. Open inv > Test Vector and load the copied inv_test.txt.
- Cause: Continuous split layout reapplies the remembered size before drag release can persist the new size. Drawer migration/minimum is in unscaled pixels, so the existing height is inadequate at 1.6.
- Source: `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/ShellLayout.java:120`; `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/ShellLayout.java:170`; `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/ShellLayout.java:202`; `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/LayoutPrefs.java:26`; `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/LayoutPrefs.java:79`.
- Plan: Apply restored sizes during initialization/explicit relayout only, permit live dragging, and persist the released size. Derive the drawer's initial/minimum useful height from its controls and font metrics; keep body scrolling available.
- Acceptance: At 1.0, 1.6 and 2.0 scales, drag every splitter smoothly, reopen with sizes retained, and inspect a visible waveform plus at least four test rows. A 2-pass/2-fail vector exposes each failed cell and Show/Set actions.
- Grouping: Same shared splitter defect as shell-03. Hidden timing/test rows are downstream effects, not two extra bugs.
- Evidence: [06-drawer-crop.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/remaining-evidence/06-drawer-crop.png), [07-drawer-after-crop.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/remaining-evidence/07-drawer-after-crop.png), [08-timing-crop.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/remaining-evidence/08-timing-crop.png), [09-both-dividers.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/remaining-evidence/09-both-dividers.png), [12-vector-crop.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/remaining-evidence/12-vector-crop.png).

### remaining-shared-02 — P2 — Circuit list loses its listener to garbage collection and becomes stale

duplicate; merge into **dialogs-17**. Fresh live GC, tab-switch and F2 rename; source establishes lifetime defect.

After jcmd GC.run on the private JVM, main is active while inv remains selected in the list. Renaming osc to osc_qa updates the tab and title while the list still displays osc.

- Reproduce: Open main and inv from the list. Allow GC or request GC only on a disposable QA JVM. Click main tab and compare list highlight. Select osc, press F2, rename to osc_qa; compare row and tab.
- Cause: MyListener is a constructor-local variable registered with weak project/library event sources. CircuitListView does not retain it strongly.
- Source: `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/CircuitListView.java:113`; `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/CircuitListView.java:311`; `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/proj/Project.java:151`; `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/file/LogisimFile.java:118`.
- Plan: Retain the listener in a field for the view lifetime, unregister on disposal, and keep current-circuit selection and library mutations synchronized. Guard actions against targets no longer in the project.
- Acceptance: After forced GC, add/rename/delete/reorder/set-main and tab switching update the list immediately. Undo/redo does the same. A deleted row cannot be reopened as a ghost.
- Grouping: Unifies add/rename, stale highlight, and recovered deleted-row symptoms. No data-loss claim: ghost editing/saving was not retested.
- Evidence: [13-tab-after-gc-crop.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/remaining-evidence/13-tab-after-gc-crop.png), [16-fpga-menu.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/remaining-evidence/16-fpga-menu.png), [20-close-tab-crop.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/remaining-evidence/20-close-tab-crop.png), [15z-tab-list-desync.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/projects/15z-tab-list-desync.png).

### remaining-shared-03 — P2 — Chronogram ruler baseline clips timestamp and cursor text

duplicate; merge into **codeaudit-08**. Fresh ruler crop and recovered populated waveform crop actually viewed; current code checked.

The tops of timestamps and the highlighted cursor label disappear above the ruler. This remains visible in recovered images with a taller drawer.

- Reproduce: Open Timing diagram at 1.6. Generate ticks and inspect the ruler at native pixels; enlarge drawer after fixing the splitter.
- Cause: Fixed 20-pixel header and h/2 baseline are incompatible with the scaled monospaced font.
- Source: `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/chrono/ChronoPanel.java:58`; `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/chrono/RightPanel.java:812`; `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/chrono/RightPanel.java:822`.
- Plan: Compute header/row dimensions and baselines from font ascent/descent and scaled padding. Size signal-name/value columns for useful content.
- Acceptance: Ruler and cursor labels are entirely inside their bounds at all supported scales, with no overlap at minimum and enlarged drawer sizes.
- Grouping: Visual corroboration of the existing codeaudit-08 cause; not another drawer-resize bug.
- Evidence: [08-timing-crop.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/remaining-evidence/08-timing-crop.png), [66z-rows-zoom.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/simulation/66z-rows-zoom.png).

### remaining-shared-04 — P2 — Code editor theme resets code to a smaller font than its gutter and surrounding UI

duplicate; merge into **codeaudit-07**. Recovered VHDL pixels viewed and EditorTheme code checked; not freshly opened.

The VHDL screenshot shows much larger gutter numbers than code, with the surrounding shell using larger scaled text.

- Reproduce: Create/open VHDL content at 1.6 interface scale. Compare code, line numbers, and shell labels; switch editor theme.
- Cause: Theme.load(input).apply(editor) has no explicit scaled base font; the editor/gutter font contract is inconsistent.
- Source: `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/generic/EditorTheme.java:69`; `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/generic/EditorTheme.java:77`; `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/vhdl/gui/HdlContentView.java:170`.
- Plan: Apply the shared scaled mono font to syntax styles and gutter together, retaining it across theme changes. Review assembler and breakpoint editors using the same helper.
- Acceptance: Code and gutter use aligned line metrics at 1.0, 1.6 and 2.0, including after a theme change. Verify actual rendered text, not only font property values.
- Grouping: Same EditorTheme root cause as codeaudit-07; no per-editor inflation.
- Evidence: [29-vhdl-editor-zoom.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/hdl/29-vhdl-editor-zoom.png).

### remaining-shared-05 — P2 — SoC memory-map table uses unreadable blue headers and inconsistent row colors

duplicate; merge into **codeaudit-11**. Recovered native memory-map screenshot viewed; current renderer code checked.

Header text is saturated blue on dark gray; allocated/empty rows use bright accent and light gray backgrounds with black text. The component-name header and values are truncated.

- Reproduce: Open Show memory map on a SoC bus in dark theme. Inspect header contrast and complete component names at 1.6.
- Cause: Hard-coded primary colors and fixed table presentation bypass the shared dark/light table styling.
- Source: `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/soc/data/SocMemMapModel.java:41`; `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/soc/data/SocMemMapModel.java:139`; `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/soc/data/SocMemMapModel.java:146`.
- Plan: Use semantic themed table foreground/background and selection colors; reserve warning styling for overlaps and size/scroll columns to retain addresses and component identity.
- Acceptance: Native captures of empty, mapped and overlapping regions remain readable in dark/light themes at 1.0 and 1.6; color is not the only overlap indicator.
- Grouping: Corroborates the existing shared FPGA/SoC renderer finding.
- Evidence: [82-memory-map.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/hdl/82-memory-map.png).

### remaining-shared-06 — P2 — Welcome actions are mouse-only panels

duplicate; merge into **codeaudit-19**. Source confirmed only in this review; original keyboard notes used as leads.

WelcomePanel.action creates JPanel/JLabel rows and only adds a mouseClicked handler; no focusable action/button or keyboard activation is installed.

- Reproduce: Fresh launch with isolated home and preferences. Attempt Tab/Shift+Tab, Enter and Space on New/Open/recent-file rows.
- Cause: Actions are represented as passive panels rather than keyboard-accessible controls.
- Source: `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/WelcomePanel.java:145`; `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/WelcomePanel.java:169`.
- Plan: Use shared Swing Actions with styled buttons or implement focusability, visible focus, Enter/Space activation and accessible names on each row.
- Acceptance: A keyboard-only user can reach and activate New, Open and recent files with clear focus, with global shortcuts still working.
- Grouping: Source-only support for a subcase of codeaudit-19, not a new visual verification.
- Evidence: source only; no screenshot claim.

### remaining-shared-07 — P2 — Hotkey synchronization races the preferences dispatcher

duplicate; merge into **shell-31**. Recovered HDL log inspected; not triggered in the current fresh session.

The prior HDL session recorded ConcurrentModificationException in AppPreferences.hotkeySync on java.util.prefs' Event Dispatch Thread.

- Reproduce: Open Preferences and create/close project windows while preferences change. Check hotkey updates and app log.
- Cause: The preferences callback iterates a mutable GUI synchronization list off the Swing event thread.
- Source: `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/prefs/AppPreferences.java:1712`; `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/prefs/HotkeyOptions.java:195`.
- Plan: Filter preference keys, marshal hotkey changes to the EDT, and manage synchronization listener lifetime safely.
- Acceptance: Window creation/destruction concurrent with relevant preference updates produces no exceptions and updates accelerators exactly once.
- Grouping: Same captured exception as shell-31. User-facing failure in this run was not measured, so no P1 escalation.
- Evidence: [app-run1.log](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/hdl/app-run1.log).

## QA harness plan

**remaining-harness-01 — infrastructure, excluded from product count.** The original `/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/qa.sh` isolates Java preferences but not `user.home`. `/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/file/Loader.java:179` writes unnamed autosaves under Java home. Pass a unique per-session home before any launch and assert both paths in the JVM. Never delete or import real autosaves. Also derive whole-display capture dimensions instead of hardcoding 2560×1600, or use native window captures. Keep launcher/session ownership alive and record the jar hash plus source/build identity.

Acceptance: two fresh sessions cannot see each other's or the user's autosaves; all created preferences/recovery files stay under their own /tmp roots; screenshots have correct bounds; cleanup affects only validated owned PIDs. The first recovery prompt is recorded in [01-initial.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/remaining-evidence/01-initial.png), and the corrected Java-home option appears in [app.log](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/remaining-evidence/app.log).

## Execution order and acceptance gates

1. Correct harness isolation and provenance checks.
2. Fix FPGA Execute and shared split-pane dragging/restoration. Visually prove HDL-only start/retry and readable waveform/test results at 1.6.
3. Retain circuit listeners and fix tab shortcut routing. Exercise add/rename/delete/undo and navigation after GC with each relevant focus owner.
4. Synchronize oscillation feedback and apply font-metric/theme fixes to timing, code and SoC tables. Check native rendered captures in light/dark and scales 1.0/1.6/2.0.
5. Correct bus display naming; investigate the native modal exception on a normal desktop before implementation.

Passing an internal check or getting correct vector totals does not satisfy visual acceptance when result rows are hidden. Keep workflow correctness and readable rendering as separate acceptance checks.

## Coverage and limits

Freshly tested: opening/selection, clock choice, half-tick shortcuts, drawer/side-divider drags, inverter vector load (2 pass/2 fail), tab selection, post-GC list synchronization, F2 rename/Enter, forward/reverse tab shortcuts, Ctrl+W, and HDL-only Execute. Original recovered images were used for populated timing rows, oscillation feedback, VHDL font mismatch and SoC dialogs; original command logs alone were not treated as visual proof.

Not rerun: the full half-adder tour, persistence/save-reopen, ghost-circuit save loss, RAM/ROM edits, full Test Vector Show/Set interactions, broad focus traversal, full SoC execution, board programming, external HDL simulation, all scales/themes/platforms. Vivado, Quartus, Questa/ModelSim, GHDL and Yosys are absent from PATH; only tclsh was found. Hardware/toolchain behavior is unverified. The JSON lists the detailed tested/notTested coverage.

The private session is stopped. App PID 555082, wrapper 551000, session 551076 and compositor 551003 were validated before shutdown and confirmed absent afterward. No other agent's session was stopped. The temporary synthetic rename was not saved into a real project.

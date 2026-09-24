# Visual QA recovery and repair plan

Implementation follow-up: [repair pass and verification](fixes/README.md). The
audit below is the preserved before-state, not the current repair status.

The interface needs a coordinated repair pass. The parts picker cannot be read at the tested 1.6 scale; the same mismatch between fonts, layout and control metrics affects much of the application. Separate workflow defects can lose edits or make panels and commands unavailable. Those take priority over cosmetic refinements.

This is a QA report and implementation plan, not a claim that the application is fixed. No production code was changed for this audit.

## Findings that determine the repair order

| Finding | Evidence | Required outcome |
| --- | --- | --- |
| **P0: canceling project close discards unsaved edits** | Fresh File Close → Escape and Save → cancel chooser runs; [editing review](reviewers/editing.md) | Every dismissal or failed save leaves the project and recovery state intact. |
| **P1: component names are clipped or absent** | Native captures at [1.6](evidence/foundations-evidence/02-palette-1.6-crop.png) and [2.0](evidence/foundations-evidence/08-palette-2.0-crop.png) scale | Control geometry and font metrics agree; component names are distinguishable without hovering. |
| **P1: Cancel changes selected property values** | Fresh [before/cancel/undo sequence](evidence/editing-evidence/14-cancel-sequence.png) | Cancel is mutation-free and a committed multi-edit is one undo transaction. |
| **P1: splitters snap back and hide usable results** | Fresh [test-vector drawer](evidence/remaining-evidence/12-vector-crop.png); two passes/two failures computed, but rows not visible | Panels resize normally and results can actually be inspected. |
| **P1: FPGA Execute fails before useful work** | Fresh action/log evidence in the [workflow review](reviewers/remaining.md) | Controller state must not cast the replacement SVG icon to the previous icon class. |
| **P1/P2: scaling, theme and secondary-window regressions** | [Foundations](reviewers/foundations.md), [dialogs](reviewers/dialogs.md), [components](reviewers/components.md) | Repair shared metrics first, then verify each affected surface and workflow at native size. |

The complete register has 381 tracked observations: 20 runtime-confirmed, 65 visually confirmed, 58 source-confirmed, 87 design recommendations, 56 unverified, 94 duplicates and one rejected claim. These are evidence dispositions, **not 381 independent bugs**. Some confirmed observations describe different consequences of one shared defect. Archived `originalClaim` suggestions are historical input, not approved fixes; follow each reviewed qualification and this plan.

## Evidence and recovery

- Revision inspected: `0a6226664` on `main`. The pre-existing untracked `AGENTS.md` was preserved.
- Runtime: Java 21.0.12, FlatLaf/flatlaf-extras 3.7.2, Linux/KWin/Xwayland.
- Copied QA jar and current `build/libs` jar both have SHA256 `c854edbbea34d6cfc0e53ac9cf70ef5b2a5dcf550a3959ff0ed99d852950d433`.
- Runtime checks apply to that binary; source checks apply to the recorded checkout. The replacement pass did not rebuild it. Hash equality does not establish a reproducible source build, and embedded BuildInfo names the older `2532c84d`. The implicated close/edit/splitter paths were checked against current source; W0 closes the provenance gap for future acceptance runs.
- Recovered original workflow `wf_2db45d7c-b0c`: 26 launched assignments, 11 completed inspection reports containing 352 observations, and one completed source-verification report adding 15 more observations. Four inspectors and ten verifiers had no final result. There was no completed clustering report.
- The exact recovered workflow matches the original on disk: SHA256 `2ea21a9b403651d86378887fe2ff13b39e985692b166ebf2b8588aa91cb0ae43`.
- Five replacement reviewers cover foundations/picker; editing/shell/attributes; dialogs/Analyzer/light theme; unfinished simulation/projects/HDL/keyboard; and component rendering. The lead reviews the overall design, evidence integrity and theme lifecycle.
- [Finding register](findings.json) preserves every original observation, its disposition, evidence, and assigned workstream. [Readable checklist](checklist.md) provides the same traceability without the full original prose. Neither count is a count of independent bugs.
- [Recovery index](recovery/index.json), [original workflow](recovery/original-workflow.js), [original harness](recovery/original-qa.sh.txt), and [reviewer reports](reviewers/) preserve the recovered work. The historical workflow requires its original agent host; its tasks were transferred to the current agent tools.

Native-resolution screenshot crops were inspected, including unreadable picker labels, ROM values, black text on the dark canvas, appearance toolbar icons, narrow Hex Editor, and About credits. Fresh reviewer evidence is distinguished from reinspection of historical captures in the register. A screenshot path or source hit alone does not establish visual acceptance.

Fresh evidence linked by the replacement reports is archived under `evidence/`; the register's `evidenceMap` maps original scratch paths to archived copies. Historical captures still referenced under `/tmp/claude-1000/` are explicitly recovered evidence, not fresh reruns. Keep that distinction when implementing or rechecking a finding.

The virtual display reproduces 2560×1600 application coordinates and app scale 1.6, not every aspect of the user's physical KDE compositor, fractional scaling, or mixed-monitor behavior. Real monitor moves remain an acceptance gate. The old harness also composites screenshots onto a fixed-size image and does not isolate `user.home`; edge-tooltip, splash and recovery findings require direct window captures and isolated reruns.

## Recommended sequence

| Workstream | Priority | Deliverable | Depends on |
| --- | --- | --- | --- |
| W0 | First | Reproducible, isolated QA and trustworthy build identity | — |
| W1 | Urgent | Safe close/save/cancel, non-destructive defaults, actionable failures | W0 |
| W2 | Urgent | One scale contract and reliable theme/lifecycle handling | W0 |
| W3 | High | Readable parts picker and usable, resizable shell | W2 |
| W4 | High | Clear, reliable property editing | W1, W2 |
| W5 | High | Legible components/canvas and correct export | W2 |
| W6 | High | Functional simulation, navigation and tool drawers | W1, W2, W3 |
| W7 | High | Responsive secondary editors, analysis and settings | W1, W2, W4 |
| W8 | Completion gate | Keyboard access, coherent terminology and visual finish | W3–W7 |

Implement W1 and W2 as independently reviewable changes. Then prove a complete editor specimen—picker, canvas, selected-component properties and simulation drawer—before sweeping the remaining windows. Do not count a package complete while its screenshot or interaction acceptance is visibly broken.

## W0 — Repair the QA process

Preserve the recovered scripts as evidence; use a corrected harness for future runs. Each run must have its own `user.home`, Java preferences, autosaves, temporary files and copied circuit fixtures. Validate IDs and verify process identity before cleanup. An interrupted test must never leave a process that continues writing to the user's home.

Capture windows at their actual dimensions, including popup geometry and stacking order. Keep a full image for context and native-resolution crops for text. Record revision, dirty status, jar hash, JVM, OS, physical/logical screen geometry, Java device transform, FlatLaf scale, app zoom, theme, locale, and fixture. Fix `genBuildInfo` task inputs so the generated revision changes when HEAD changes; the current jar title still names an older revision.

Add a small repeatable interaction harness around real Swing windows, separate from headless unit checks. Use existing snapshot tooling only after isolating its settings and removing its implicit 1.0-scale assumption. Compare preference/autosave snapshots before and after the run. Do not delete existing user recents or recovery files to obtain a clean screenshot.

Acceptance: two consecutive runs produce the same baseline; user settings remain unchanged; 1280×800 and 2560×1600 images are correctly sized; exact revision and jar identity accompany all evidence. A build/test result is recorded separately from visual and workflow results.

## W1 — Protect work and make failure recoverable

- Unify File Close, window Close and Quit around one save/discard/cancel contract. Escape, title-bar close, failed save and cancelled Save As must leave the edited document open. Only an explicit discard may abandon changes. Inspect autosave cleanup and undo preservation in the same flow.
- This is urgent: fresh scratch runs lost unsaved circuit edits both on File Close → Escape and on File Close → Save → cancel the chooser. Also repair `Frame.confirmClose`'s autosave lifetime on a failed/cancelled save; routing menu close there without inspecting that branch is insufficient.
- Make Analyzer Build Circuit start with a unique new-circuit name and focused, selected text. Require an explicit named Replace action for a collision; retain undo. Recheck keyboard-only behavior so typing cannot accidentally activate the default button.
- Ensure canceling property editors, color/font pickers and multi-selection choices performs no mutation. Delayed wheel edits must remain attached to the original edit target or be cancelled when selection changes.
- Put shortcut/menu preference updates on the Swing event thread, with lifecycle-aware subscriptions. The recovered log contains a real `ConcurrentModificationException` in `hotkeySync`; changing themes/settings and opening tool windows must not kill that update path.
- Expose unsupported simulation components with an actionable status and bounded error reporting. A component whose propagation deliberately throws must not repeatedly fail while appearing fully supported. Preserve its serialized ID and any legitimate hardware-only use.
- Remove behavior that depends on concrete icon classes. A fresh FPGA UI run records a `FlatSVGIcon` to `ProjectAddIcon` cast failure in `FpgaCommander.actionPerformed`; changing a button's artwork must not break its action. Keep action state in the controller and render the appropriate icon from that state.
- Review conditional legacy label normalization separately from ordinary loading. Keep display labels stable where compatibility permits; use deterministic HDL identifiers or an explicit migration notice. Do not describe the conditional sanitizer as renaming every valid label.

Acceptance: scripted close/save/cancel permutations preserve the circuit model and on-disk file; Escape never means discard. Property cancel leaves the model and undo history unchanged. Repeated preferences/window cycles log no concurrency exception. Normal file open/save retains circuit IDs and user content.

## W2 — Unify scaling, themes and lifetime

Define three distinct concepts: operating-system device scale, application UI zoom, and document/canvas zoom. Store panel geometry in logical units. Preserve explicitly chosen existing zoom settings; do not silently reset users to 1.0 or infer physical DPI solely from screen height.

Use FlatLaf as the owner of widget metrics. The installed 3.7.2 API includes `UIScale.setZoomFactor`, `getZoomFactor`, and supported zoom factors; verify its behavior in a small prototype before selecting the live-zoom path. Remove the global AWT font rewrite once native metrics own scaling. `UiFonts` must derive roles from an already-scaled base font without multiplying again, and `AppIcons` must not pre-scale SVG dimensions that FlatLaf scales again. Keep body, secondary, heading and monospace roles intact across adding components, changing theme and changing scale.

Update every geometry cache and custom control from the same scale notification. This includes row heights, editors, captions, splitters, focus rings, hit targets, tree renderers, keyboard-shortcut fields, dialog minima, toolbar icons, diagram rows and gutters. Size text-bearing elements from actual font metrics and content. Scale must affect the control and its text together.

Use one ordered theme transaction for startup, explicit choice and OS notifications. Refresh component/value palettes, UI defaults, code-editor themes, caches and repaint listeners together. Move OS discovery off the event thread and bound the entire child-process lifetime; the diagnostic probe shows the current two-second timeout taking three seconds because it reads output before applying the timeout.

Cache regular/accent/disabled icons separately: a runtime probe proves requesting a disabled icon currently mutates the shared regular instance. Pair subscription registration with disposal; palette rebuilds currently retain old tiles through strong theme listeners. Preserve user-defined colors, and do not run palette migrations that erase overrides.

Acceptance: 1.0 → 1.25 → 1.5 → 1.6 → 2.0 → 1.0 produces coherent widget/text sizes without restart artifacts or cumulative scaling. Repeat light → dark → light with project, Preferences, Analyzer and a code editor open. A silent OS-probe process cannot block typing. Listener counts stabilize after repeated rebuilds and window closure. Document zoom and exported geometry remain stable.

## W3 — Make the picker and shell usable

The parts picker is the first visual benchmark. Replace the fixed 68×62-pixel tile with a layout measured from icon area, scaled padding and two caption lines. Use readable foreground text, wrap words, and provide a full-name list presentation for long labels such as TTL and floating-point operations. Full names must be discoverable without hovering every tile. Scale the favorite marker and its hit target. Do not rename serialized component IDs to make captions fit.

Search must commit the current query before Enter chooses a result. Ensure keyboard traversal, visible focus, no-results guidance, full-name tooltips, favorite/recents persistence, unambiguous categories, disabled recursion choices and correct placement tools. Avoid rebuilding the entire palette on every incidental event; ensure disposed results release listeners.

Let the user actually drag all shell splitters. Reconcile stored sizes with layout only on initial restore or explicit programmatic reset; do not snap a divider back during an active drag. Scale default widths and enforce content-aware minima while leaving useful canvas space on small screens.

Expose discoverable toggles to reopen Properties, the navigator and the drawer. Reset Layout is recovery, not the only route to a closed panel. Keep command state synchronized with activity-bar actions. Define last-tab behavior, active-tab overflow, selected circuit synchronization and clear circuit-versus-appearance labels. Fix stale zoom status by binding to the active editor model. Give welcome and no-selection states useful guidance; avoid invisible startup actions in the user's undo history.

Retain the circuit-list listener strongly for the view's lifetime, since its event sources use weak references. A fresh GC/rename/tab-switch check left stale rows and selection. Unregister on disposal and guard operations against a circuit that no longer belongs to the project. Include add/delete/reorder/set-main and undo/redo in that regression check.

Acceptance: a user can identify NOT, Controlled Buffer, Pull Resistor and representative long TTL names at native size; type a query and immediately Enter the intended tool; place it; open Properties; resize/hide/reopen panels; switch/close overflowing tabs; and reopen the project without layout drift. Capture the whole sequence at the user's 1.6 scale in both themes and at a constrained window width.

## W4 — Rebuild property editing around the task

Use a selection summary, meaningful groups and optional explanations: Identity, Behavior, Connections, Appearance and hardware details where applicable. Keep uncommon options behind a clearly labeled advanced section. Display compatible multi-selection values consistently, with an explicit mixed value rather than misleading blanks or a false supported state.

Size labels and editors independently; permit column resizing or a responsive stacked layout. Avoid an invisible fixed 50/50 split. Full labels, selected values, validation text and font/color previews must fit. Use native boolean toggles, bounded numeric editors, readable choices and dedicated font/color controls. Keep component descriptions available through keyboard focus as well as hover. Use semantic status colors with text, not neon-filled cells.

Centralize edit validation and undo transactions. Enter commits once, Escape cancels, selection changes finish or cancel according to a clear rule, and multi-edit applies only compatible attributes. Scrolling the panel must not unexpectedly change values. A wheel-nudge edit requires deliberate focus/modifier semantics and a bounded undo group.

Acceptance: edit an AND gate, a splitter, ROM, a labeled component, two compatible gates and mixed incompatible selections. Test invalid input, minimum/maximum, cancel, undo/redo and selection changes during pending edits. All changed models and undo entries must match what the inspector shows. Verify an expanded locale or long labels as well as English.

## W5 — Make circuit rendering legible and export correct

Separate automatic interface/default colors from explicit document colors. Fix foreground and background together for ROM/RAM contents, constants, reset/clock components, bus traces and value badges. New text must be readable in the active theme. Existing explicitly colored text and custom appearances must remain intact; a theme-adapted display option must be reversible and must not rewrite saved data or print output.

Audit small pin values, radix labels, gate labels, splitter indices, internal arithmetic labels, off segments, TTL and SoC painters at actual size. Use contrast-tested pairs and reserve signal colors for signal meaning. Preserve IEEE/IEC shapes and port geometry. Remove accidental overlaps and displaced drawing, including `DotMatrixBase.drawCircle` multiplying already-absolute positions by the display's local scale.

Define a readable initial viewport and fit/center behavior separately from circuit coordinates. Any new display transform must be shared by painting, mouse hit testing, grid, scrolling, ghost placement, selection and saved view state. Do not blindly multiply document coordinates or stored zoom by application UI zoom.

Export needs its own proof: inspect antialiasing, pixel bounds, white-background print palettes, transparent backgrounds and custom appearance colors. Do not judge an image only by a reduced preview or use a count of colors as sufficient proof of antialiasing.

Acceptance: render a fixture covering gates, labels, wiring, memory, displays, TTL, custom appearances and SoC at 50/100/200% in both themes and print view. Exercise selection/wiring at each zoom. Inspect native-resolution output images and ensure LED dots stay inside their component. Round-trip the fixture and confirm component IDs, coordinates, connectivity and explicit colors are unchanged.

## W6 — Complete simulation and navigation workflows

Make run/pause, propagation step, half/full tick, clock-enabled state and frequency legible and synchronized wherever shown. Distinguish simulation state from document editing state. Empty clock selection and unsupported components need useful guidance. Surface errors in a persistent, inspectable place with deduplication and a route to the affected component.

Treat timing diagrams, signal selection, data tables and test vectors as functional editor content. Give the drawer a usable initial/minimum height, resizable regions and a path to expand or detach when needed. Closing one tool tab must not unexpectedly remove unrelated tools. Audit the old hidden JFrame controllers for focus, menu bindings, simulator listeners and visible-state assumptions after reparenting.

Keep signal names aligned with waveform rows at every scale, make time axes and bus labels readable, and test horizontal scrolling and zoom on long captures. Make test-vector load/run/results navigable with the keyboard. Preserve circuit/subcircuit instance navigation and state when switching tabs; the circuit being edited and the simulation instance being inspected must be distinguishable.

Acceptance: build or open a small clocked circuit, run/stop/step/tick it, select signals, scroll a long timing capture, inspect a failing vector, navigate into and out of an instance, hide/reopen the drawer and change theme/scale. Verify model state and labels, not merely that a panel opens. Run a sustained capture to check memory behavior after correctness is established.

## W7 — Finish secondary windows and settings

Work in separate slices after shared metrics are stable:

1. **Preferences and Project Options:** viewport-width-aware, top-aligned forms; sensible line wrapping; reachable Browse/Apply actions; search by individual setting and synonym; consistent reset scope and shortcut editing.
2. **Analyzer:** measured signal/table rows and combo popups; predictable editing/focus; clear primary Build action; grouped import/export; readable expressions and Karnaugh annotations; safe build defaults from W1. Fix expression overbar placement using the same final text metrics used to paint, and test digits on overlapping K-map covers. Move optimization to a cancellable worker with an explicit progress/cancel path and EDT-only UI updates.
3. **Hex and HDL editors:** adequate initial geometry, consistent toolbar and search, scale-aware code and gutter fonts, validation/error navigation, clear file ownership. Shell-hosting requires explicit menu/action routing and lifecycle work; merely moving a component out of a JFrame is insufficient.
4. **FPGA and SoC tools:** responsive form sections, readable reports and CPU/trace views, clear missing-toolchain state, proper action enablement. Preserve hardware-only features and report external tool failures. Actual synthesis/programming requires available software and hardware.
5. **Dialogs, chooser, About and Help:** owner-relative placement, bounded screen size, correct focus/default/cancel behavior, coherent labels, readable credits and documentation navigation. Preserve upstream authorship and license acknowledgments during branding changes.

Acceptance: every form's primary action is reachable at minimum supported window size and 2.0 scale; no clipped combo choices or horizontal scrolling just to reach a normal action. Repeat a normal, invalid, canceled and keyboard-only path for each slice. Record FPGA programming and printer output as untested until actual dependencies are available.

## W8 — Keyboard access and visual completion

Use actual actions and accessible controls for clickable labels, section headers, breadcrumbs, palette results and zoom controls. Give each a name, role, visible focus and keyboard activation. Resolve reserved/configurable shortcut conflicts and distinguish closing an editor tab from closing a project. Escape cancels the current interaction first; it must not discard documents.

Apply a coherent typographic hierarchy and icon grammar. Interface icons should share stroke, weight and contrast; miniature circuit symbols may retain domain-specific information. Refresh old drawing-tool icons and raster assets where they visibly fail. Normalize terminology across menus, toolbar tips, Properties, search and dialogs. Clarify disabled actions and empty states, reduce duplicate status and remove debug build metadata from everyday titles while retaining it in About/Copy Details.

Inspect real theme tokens and contrast rather than recoloring by intuition. Primary/secondary text must remain readable on its actual surface; state and selection need more than color alone. Compare the complete editor and secondary windows at native size, including focus, hover, selected, disabled and error states. Treat tooltip clipping as provisional until the real popup capture confirms it.

Acceptance: a keyboard-only user can start a project, find/place components, edit attributes, wire, run/inspect simulation, save, reopen and close safely. Repeat with long names and locale expansion. A final reviewer unfamiliar with the implementation must complete the same task using only what the interface explains.

## Release gate

| Dimension | Required coverage |
| --- | --- |
| UI zoom | 1.0, 1.25, 1.5, the reported 1.6, 2.0; one larger stress setting |
| Theme | light, dark, live light/dark cycle, simulated and physical OS-following change |
| Window | full 2560×1600, 3840×2160/4K, 1280×800 or documented minimum, resized/narrow panels |
| Physical display | user's fractional KDE setup; a genuine HiDPI transform; mixed-DPI monitor move |
| Data | fresh isolated settings; migrated copied settings; simple fixture; large real circuit; custom appearance |
| Interaction | mouse, keyboard, search, cancel, undo/redo, invalid input, persistence, reopen |
| Output | screen, PNG export, print-view rendering; real printer only if available |

Use pairwise coverage for broad window sweeps, and the full relevant matrix for scale/theme infrastructure and the picker. Record both whole-window captures and native crops. Human review must verify text and affordances at normal viewing size, not only enlarged crops. Unit tests should cover real invariants—cancel safety, model changes, coordinate mappings, cache ownership and bounds—not implementation trivia. Run the existing appropriate Gradle checks and report failures explicitly; the previously known `SoftwaresTest` failure is not a blanket exemption for new failures.

Done means every confirmed finding has a fix and passing acceptance evidence, every duplicate resolves to that fix, every rejected claim has a reason, and every unverified claim has a named reproduction task. Source review, a successful build, or a low-resolution screenshot cannot substitute for this gate.

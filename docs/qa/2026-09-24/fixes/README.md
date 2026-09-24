# Repair pass and verification

Implementation follow-up to the [original audit](../README.md). The original 381
observations remain historical evidence, not 381 distinct bugs. This report does
not mark every recommendation or unverified claim resolved. Changes are currently
uncommitted on `main` over `0a6226664`.

## Implemented repair families

| Area | Repair |
| --- | --- |
| Project safety | Close routes share explicit Save/Discard/Cancel policy. Escape, dismissal and failed/canceled Save do not close the project. Quit defers cleanup until all confirmations succeed. Replacing a file retires its old autosave writer before the new writer can run. Saves stage complete serialization before replacing existing files, and serializer errors preserve the last valid project/recovery instead of reporting success. |
| Properties | Cancel discards drafts without writes. Compatible multi-edit commits one undo action. Valid model/focus transfers keep the original target; invalid values remain editable. Ordinary wheel scrolls; Alt+wheel deliberately nudges in one transaction. Type-to-edit, mixed HDL status, padding and empty guidance are repaired. |
| Scaling and themes | FlatLaf owns font/control metrics; custom geometry uses the same scale once. Explicit scale is preserved; Auto consults advertised desktop DPI without a resolution heuristic. Theme discovery is off the EDT and bounded. Theme/scale tree refresh is deferred and coalesced so active combo and slider handlers retain their delegates. Explicitly styled help/slider fonts refresh on live scale changes. Icon variants have separate caches; transient theme subscriptions follow component lifetime. |
| Parts picker | Captions use measured two-line layouts, switching to compact rows in narrow panels. Immediate Enter uses the current query; aliases, keyboard traversal, no-results guidance and full-name access work. Rebuilding results no longer retains disposed tiles. Library-tree refresh events now carry tree-node paths, preventing inconsistent expansion state after UI reinstall. |
| Shell | Divider drags persist; logical dimensions survive scale changes. Narrow windows reserve editor space, temporarily compressing side panels without overwriting requested widths. Closed panels have direct reopen actions. Circuit-list listeners, tab overflow/selection and last-tab behavior are repaired. Starting work dismisses the welcome screen. Zoom status follows the active editor. |
| Circuit rendering | Document fonts are independent of UI zoom, fixing RAM/ROM overlaps; screen, export bounds and print use the same logical font. Automatic data displays and subcircuit outlines use paired theme/print colors; signal fills and explicit document colors remain intact. Printing default subcircuit shapes does not mutate the cached screen artwork. Ghost ink, matrix-dot placement, grid hierarchy and raster export antialiasing are corrected. |
| Simulation/tools | FPGA actions no longer cast SVG icons to obsolete classes; completion/error UI updates run on the EDT. Reptar is identified as hardware-only and drives unknown outputs instead of crashing propagation. Optimizer work is cancellable and applies complete results on the EDT. |
| Secondary UI | Settings search indexes controls and synonyms; empty results are explicit. Forms, code/gutter metrics, timing rows and Hex sizing are repaired. Clock selection reserves complete selectable rows, and timing Options reflows to stacked sections. Timing/Test drawers suspend safely while an HDL editor is active. Analyzer builds default to a unique name, with Cancel as the replacement default. About typography and keyboard dismissal are repaired. |
| QA infrastructure | Tests/probes use private preferences. The native runner freezes its jar, records provenance, copies circuits and owns its display/processes. No real user circuit or autosave is used. |

## Rendered and interactive evidence

Actual window captures and actual exports, not recreated mockups:

- [Readable picker and vector results at dark/2.0](evidence/picker-and-results-dark-2.png).
- [Compact picker after a real divider drag and immediate search](evidence/picker-narrow-search.png), dark/1.6.
- [Canceled Save leaves the same dirty circuit open](evidence/save-cancel-kept-dirty.png), dark/1.6. File Close → Escape was also checked; Ctrl+W closes an editor tab, Ctrl+Shift+W the project.
- [Independent property-edit native checks](attributes-native.md) passed: two-Escape cancellation retains mixed No/Yes; one Undo restores both committed rows; ordinary wheel leaves Facing unchanged; a label draft survives focus transfer and belongs to the original gate in saved scratch XML.
- [RAM/ROM document text at UI scale 2.0](evidence/document-font-dark-2.png): UI enlargement no longer enlarges fixed-coordinate internal text.
- [Two passing and two intentionally failing vector rows after resizing](evidence/test-results-resized-dark-2.png). The drawer followed the pointer and retained its height.
- Native File → Export Image produced [screen-palette PNG](evidence/main-screen-export.png) and [print-view PNG](evidence/main-print-export.png). Both were inspected at native 843×630 size. This is not physical-printer acceptance.
- [Constrained HDL editor at 1280×800 / 2.0](evidence/constrained-hdl-light-2.png): approximately 640px of editor width remains. [Native responsive-shell checks](responsive-shell-native.md) also verified real divider dragging, restoration on widening, and persistence of desired rather than compressed widths.
- [Timing history after HDL round trip](evidence/timing-history-return-dark-2.png): waveform time 54,999 ns and counter 5 survive switching to HDL and back. [Bounded timing report](timing-native.md) separates this passed check from the then-unverified completed-vector-results case.
- [Light picker after live scale/theme round trips](evidence/picker-light-1.6.png) and [dark picker](evidence/picker-dark-1.6.png). These captures establish readability, not clean-log live-switch acceptance: the same session exposed a UI-delegate reentrancy error, assigned for correction and another run.

[Save-safety details](save-safety.md) document failure injection and its limits.
Successful atomic replacement is not a power-loss/fsync guarantee; failed exports
may still leave a partial export artifact, unlike the guarded project/recovery paths.

[Core provenance](evidence/core-session.json) records the private jar hash and
worktree inventory. The [earlier editing session](evidence/editing-session.json)
predates immutable-jar freezing; only observations before replacement are retained.
Reviewers explicitly rejected contaminated captures after shared-jar replacement
rather than counting resulting class-loading errors as product regressions.

## Validation status

The integrated run on 2026-09-24 passed **1,030 tests with zero failures, errors or
skips**, production/test Checkstyle and the fat-jar build. The full `./gradlew build`
also passed, including distribution assembly. `git diff --check` and syntax checks
for both native-QA runner scripts passed.

Commands: `./gradlew test checkstyleMain checkstyleTest shadowJar`, then
`./gradlew build`. After native QA exposed a remaining test-result invalidation
defect, the narrow correction and four regressions were followed by another full
`./gradlew build`: tests, production/test Checkstyle and distribution assembly all
ran successfully. The earlier 1,026-test checkpoint is not the final count.

Verified candidate: `build/libs/logisim-evolution-5.1.0dev-all.jar`, SHA256
`50316d4a92230497b1c9486373333f3411580b0d9fec63c73f510b2c83e86908`.
It includes timing-to-HDL transitions, clock-selector chrome reservation, responsive
settings and shell allocation, automatic subcircuit screen/print colors, the library
tree event fix, and deferred theme/scale refresh. Late native rechecks use private,
immutable copies with hashes in each report. Forms/tree/theme rechecks below used
the preceding `f582284f…` jar; the only subsequent production edit is the narrow
test-result invalidation correction in `gui/test/Model.java`. Its native rerun uses
the final `50316d4a…` jar. A green unit run is not substituted for native checks.

Earlier regression failures were investigated, not excluded: two contaminated font
fixtures were isolated, the clock test was strengthened to measure actual viewport
space and prompted an additional chrome-reservation fix, and a genuine recursive
appearance-port matcher was corrected with dedicated comparisons. No tests are skipped.

The final [test-result repair](test-result-retention.md) ignores view-only
`ACTION_DISPLAY_CHANGE` notifications, preserving completed reports, counts and row
ordering through real Project HDL/circuit transitions. Actual component invalidation,
structure edits and vector replacement still clear results. The [native pre-fix failure](test-result-retention-before.md)
is preserved rather than hidden by the earlier successful build.

### Final-candidate native rechecks

- **PASS — responsive settings and live fonts:** the [focused forms report](forms-native-final.md)
  verifies the entire Software validation caption at 1200px/2.0, reachable Browse actions,
  new scale help, and Window/Software fonts updating in the same open window at 2.0→1.0.
  [Wrapped caption](evidence/software-wrapped-dark-2.png) · [live scale 1.0](evidence/window-live-scale-1.png).
  The application log was empty and all owned session processes were stopped.
- **PASS — event ordering and Welcome navigation:** [native UI-refresh report](ui-refresh-native.md)
  confirms real keyboard Dark/Light choices and mouse slider releases at 2.0→1.0→1.6 with
  a zero-byte application log. [Final light/1.6](evidence/theme-scale-final-light-1.6.png).
  Creating HDL from Welcome opens [the editor directly](evidence/welcome-direct-hdl.png).
  Theme changes may move focus; this check explicitly refocused the combo before the next key.
  This supersedes the pre-fix exception log, rather than treating the older screenshots as acceptance.
- **PASS — library-tree fallback:** [native tree report](tree-native-final.md) verifies children
  remain visible through live UI changes, actual Gates/Multiplexers collapse/expand, and Buffer/
  Multiplexer selection with matching properties. [Initial light/1.6](evidence/tree-light-1.6.png)
  · [returned light/1.59](evidence/tree-roundtrip-light-1.59.png). The mouse return landed at 1.59,
  not exactly 1.6; this is tree-state acceptance, not an exact numeric slider-roundtrip claim.
  Application log empty; owned session stopped.
- **PASS — completed test results survive HDL:** [final acceptance report](results-native-accepted.md)
  uses the final `50316d4a…` jar. A real vector run produced 2 passes and 2 intentional failures;
  switching to an already-existing HDL editor and back retained counts, statuses, ordering and
  highlights without rerunning. [Returned results](evidence/test-results-retained-dark-2.png).
  Application log empty; owned session stopped. The latest clock chooser also passed an actual
  resize to [840×500 at scale 2.0](evidence/clock-chooser-resized-dark-2.png), retaining three
  complete selectable rows and reachable Cancel/OK controls.

## Coverage limits

Physical 4K/mixed-monitor behavior, FPGA synthesis/programming, physical printing,
every library/localization and a complete keyboard-only end-to-end run are not
established. Explicit saved colors are not silently rewritten. Historical XML
label sanitization, document-specific caption geometry and remaining design
recommendations need their own compatibility or product decisions; a green test
count does not close them.

Known lower-priority visual scope still open includes white-backed legacy component
bitmap icons and intrinsic long-label/caption collisions in some component symbols.
The repaired UI-font inheritance is not a claim to have redesigned every symbol's
document geometry. Those cases, remaining product-design recommendations and unverified
audit claims are not silently marked closed by this repair pass.

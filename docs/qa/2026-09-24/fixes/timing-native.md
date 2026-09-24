# Bounded timing/native follow-up — 2026-09-24

## Frozen artifact and isolation

- Session: `/tmp/logisim-visual-qa-wjyKvD`; runner `scripts/visual-qa.mjs`.
- Frozen jar: `work/application.jar`; SHA256 `de2e9f49d6f6551e2f293ec2d127afbf18ba3d21369c50e4cb5a23e71716bec2`.
- Dark theme, explicit Scale 2.0; private 2560x1600 display, app 2304x1440, window ID 4194355. Clock dialog was 840x628.
- Only copied `scripts/fixtures/ui-smoke.circ` and private inverter vectors used. Added unsaved `qa_hdl` only to the disposable project.
- All eight captures below viewed. Owned session stopped via runner. Final app.log: zero bytes.
- Real preferences SHA256 remained `450a87ed346c80b0570b06f27d7c19ea6bfaf1a66fccac45d858fdd640de020f`.
- No Gradle, JUnit execution, real circuit/preferences writes, commits or pushes.

## Actual native results

Evidence directory: `/tmp/logisim-visual-qa-wjyKvD/evidence/`.

| Capture ID | Observed result |
| --- | --- |
| 01-clock-chooser-2.png | Five visible entries, readable explanatory text, full Cancel/OK. Selected `clk`, confirmed with OK; chooser closed successfully. |
| 02-timing-options-2.png | Responsive stacked sections: full Signals/Formats controls and Logging Mode labels, rather than squeezed columns. |
| 03-timing-options-history-2.png | Scrolling exposes full history controls, 400-value spinner and wrapped explanation; no observed horizontal caption clipping. |
| 04-timing-history-before-hdl.png | Clock history established by ten half ticks: cursor 54,999 ns, counter 5. |
| 05-hdl-timing-inactive.png | HDL editor opens; code/gutter visible, timing shows inactive circuit-selection hint, no exception. |
| 06-timing-history-return.png | Returning to main restores waveform at 54,999 ns and counter 5, without resetting history. |
| 07-test-drawer.png | Inverter test drawer opens with readable load/run controls. |
| 08-test-results-return.png | After loading private vectors and switching test drawer -> qa_hdl -> inv, all four loaded rows remain. No exception. Counts show 0/0, so completed-result retention is NOT established by this capture. |

The test transition was confirmed by native window title changes and an empty log. A Run click did not establish the expected 2-pass/2-fail counts in the saved evidence; do not claim that part passed. No extra captures or broader matrix were taken. Timing lifecycle and chooser usability passed this bounded dark-2.0 run, not the complete scaling/theme/physical-device matrix.

## ClockSourceTest failure and bounded follow-up (NOT in frozen jar)

XML `build/test-results/test/TEST-com.cburch.logisim.gui.log.ClockSourceTest.xml` reports `long descriptions scroll rather than consuming the signal list` at the former line54, not the row-count assertion.

Standalone reproduction with the frozen jar and a pre-existing developer `Label.font=Dialog 12` override: FlatLightLaf installation preserves that override; the 20-repeat explanation measures 178 px and its viewport is also 178 px, so the strict scrolling assertion is false. This reproduces the pollution mechanism; the precise preceding test responsible was not identified.

The test now clears/restores only that developer override, installs the intended look and feel, and lays out child viewports before asserting. Its row check is strengthened to measure actual viewport height, not scroll-pane outer height. This exposed a real chrome-reservation defect: at clean double scale, the old viewport was 140 px for three 48 px rows (144 px required).

Source fix reserves scroll-pane border, potential horizontal scrollbar and column header height in both preferred sizing and constrained explanation sizing. Standalone updated-source measurement: font26, rows48, signal viewport164 px (>=144), description viewport168 px versus preferred836 px, so long description scrolling and three complete visible rows are both established without weakening assertions.

Changed follow-up files:

- `src/main/java/com/cburch/logisim/gui/log/ClockSource.java`
- `src/test/java/com/cburch/logisim/gui/log/ClockSourceTest.java`

Both compile with standalone javac21; scoped `git diff --check` passes. No API/new string changes. Parent should rerun `com.cburch.logisim.gui.log.ClockSourceTest` in the integrated build. No JUnit pass is claimed by this worker. Probe source/classes remain under `/tmp/logisim-timing-fix-azH8II/`.

Source frozen at handoff. No Theme.apply/UiScale.refresh edits; those belong to Godel. Earlier lifecycle implementation/test details are in `/tmp/logisim-fix-timing-lifecycle.md`.

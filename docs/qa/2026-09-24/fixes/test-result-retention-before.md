# Final bounded timing native verification

2026-09-24. Production frozen; no source edits or Gradle/JUnit execution by this worker.

## Artifact and isolation

- Session: `/tmp/logisim-visual-qa-jNr7tH`, created with `scripts/visual-qa.mjs`.
- Frozen jar: `/tmp/logisim-visual-qa-jNr7tH/work/application.jar`.
- SHA256: `f582284fa310aa5863c8861d3da4673b24dad91f143bb46fc4e755a6c12be8bb`, matching the parent-supplied final artifact.
- Dark theme, explicit 2.0 scale, private 2560x1600 display; main window 4194355 at 2304x1440.
- Only disposable copies of `scripts/fixtures/ui-smoke.circ` and `scripts/fixtures/inverter-vectors.txt` used. `qa_hdl` created unsaved in the private project.
- Owned session stopped through the runner. Final application log: zero bytes. All seven captures viewed.
- Real preferences checksum unchanged before/after: `450a87ed346c80b0570b06f27d7c19ea6bfaf1a66fccac45d858fdd640de020f`.

## Completed test results -> HDL -> inverter: FAIL

Native title confirmed `inv of ui-smoke` before loading vectors. The observed Run button center was approximately (1129,1356) in native coordinates; fresh captures grounded its position.

1. Loaded the copied inverter vector file and clicked Run. Capture02 visibly shows **Passed: 2 Failed: 2**, two fail rows and two pass rows.
2. Created `qa_hdl`; HDL editor opens safely with disabled test controls (capture03). Returning to inv shows loaded vector rows but **0/0** and blank statuses (capture04).
3. To exclude project mutation during HDL creation, reran vectors with `qa_hdl` already present. Capture05 establishes a second **2/2** baseline.
4. Switched via navigator to the existing `qa_hdl`; native window title changed to the project-only HDL title. Switched back to inv without editing, loading, resetting or rerunning. Capture06 again shows **0/0** and blank statuses, with the four loaded vector rows retained.

Thus null-state safety works, but completed results do not survive a pure existing-editor round trip. This is a confirmed remaining defect, not an unverified Run click. No exception accompanies it.

Read-only source lead, not a fully traced diagnosis: `gui/test/Model.java:circuitChanged` clears results for every CircuitEvent except ACTION_SET_NAME. ModelHistory reuses the per-circuit model, but caching alone cannot protect it from non-semantic circuit events. The precise triggering event was not instrumented in this frozen pass. Production remains untouched; correction requires a separately authorized bounded source/test follow-up.

## Latest clock chooser sizing: PASS at tested constraint

Opened timing for main. Actual Clock Source Selection window4194433 initially measured **840x660**. Resized it to **840x500** at2.0, confirmed by native geometry.

Capture07 shows three complete selectable rows (`a`, `clk`, `clk_out`), a scrollable remainder, wrapped/scrolled explanation, and fully visible Cancel/OK buttons. Selected `clk` at approximately (860,660) and clicked OK at (1495,839); the chooser closed successfully. This exercises the latest scroll-pane chrome allowance in the frozen final jar.

## Evidence inventory

All captures under `/tmp/logisim-visual-qa-jNr7tH/evidence/`:

- `01-test-controls.png`: initial drawer controls used to ground coordinates; initial title still main, corrected and confirmed inv before loading.
- `02-vectors-run.png`: first completed 2/2 baseline.
- `03-test-hdl-inactive.png`: HDL code editor and disabled test controls.
- `04-completed-results-return.png`: 0/0 after first return.
- `05-results-existing-hdl-baseline.png`: completed 2/2 with HDL entity already present.
- `06-results-existing-hdl-return.png`: decisive pure-switch regression, 0/0 after return.
- `07-clock-chooser-resized.png`: native840x500 dialog with three complete rows and usable buttons.

Parent reports1026 tests and both Checkstyle/shadowJar passing. That does not override the observed completed-result retention failure. No broader scale/theme or physical-hardware acceptance is claimed.

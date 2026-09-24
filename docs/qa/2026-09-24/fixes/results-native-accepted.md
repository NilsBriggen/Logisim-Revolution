# Completed-result retention — native accepted

2026-09-24. **PASS for the requested bounded case.** Production remained frozen; no source edits, Gradle or extra acceptance matrix.

## Artifact and private session

- Frozen jar: `/tmp/logisim-visual-qa-T8dFKl/work/application.jar`.
- SHA256: `50316d4a92230497b1c9486373333f3411580b0d9fec63c73f510b2c83e86908`, verified against the parent-supplied artifact before launch and again after the check.
- Runner: `scripts/visual-qa.mjs`; session `/tmp/logisim-visual-qa-T8dFKl`.
- Dark theme, explicit scale2.0; private2560x1600 display, app2304x1440, native window4194355.
- Copied `scripts/fixtures/ui-smoke.circ` and `scripts/fixtures/inverter-vectors.txt`; isolated home/preferences. Created unsaved `qa_hdl` BEFORE loading/running the vectors.

## Observed steps and result

1. Selected `inv`; native window title confirmed `inv of ui-smoke`.
2. Loaded the four-row copied inverter vector file and clicked Run at the established native control position (1129,1356).
3. Viewed before capture: **Passed:2 Failed:2**, two failed rows followed by two passed rows, with expected failure highlighting.
4. Switched to the already-existing `qa_hdl` through the navigator. Native title changed from the inverter title to the project-only HDL title. No project edit or entity creation occurred in this measured round trip.
5. Returned to the same `inv` without loading, resetting or rerunning. Native title confirmed the inverter again.
6. Viewed after capture: **Passed:2 Failed:2**, identical row statuses/order and failure highlighting. Completed results retained.

## Evidence

Both captures were viewed:

- Before: `/tmp/logisim-visual-qa-T8dFKl/evidence/01-completed-before-hdl.png`
- After: `/tmp/logisim-visual-qa-T8dFKl/evidence/02-completed-after-hdl.png`

Application log `/tmp/logisim-visual-qa-T8dFKl/app.log`: **0 bytes**, no logged exceptions.

Owned session stopped using the runner; evidence retained. Real user preferences checksum unchanged before/after: `450a87ed346c80b0570b06f27d7c19ea6bfaf1a66fccac45d858fdd640de020f`.

This closes the previously observed completed-result-reset native regression on the stated jar. No clock chooser repeat, broad theme/scale matrix, or physical-device acceptance is claimed.

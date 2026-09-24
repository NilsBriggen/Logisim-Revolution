# Final focused forms native acceptance — 2026-09-24

## Artifact / isolation

- Jar SHA-256: `f582284fa310aa5863c8861d3da4673b24dad91f143bb46fc4e755a6c12be8bb`, verified against parent-provided build before launch and the private copy before shutdown.
- Owned independent session: `/tmp/logisim-visual-qa-9yY5Yz`, started `2026-09-24T17:46:24.492Z`.
- Frozen artifact: `/tmp/logisim-visual-qa-9yY5Yz/work/application.jar`.
- Recorded HEAD: `0a62266649937f3c2cbcb1ef495b6d9be6ba2d5a`, with shared worktree status preserved in session.json. Binary identity is the hash above, not clean HEAD.
- Private KWin/Xwayland display 1920x1200, private home/preferences, dark theme, English. No other agent's session used. Preferences window set to 1200x900.
- No production edits, Gradle, tests, toolchain execution, real-display access, or real preferences/circuit/autosave changes. The sole preference change was the requested native scale transition inside this disposable session.

## Assigned cases and results

| Case | Result | Actual evidence |
| --- | --- | --- |
| Complete Software caption at 1200px / 2.0 | PASS | Full `Use Questa Advanced Simulator to validate HDL entities` appears on two complete lines. Final word `entities` is fully visible and separate from the VHDL row. Browse buttons remain inside width, no horizontal scrollbar. Capture 01. |
| Latest Window scale help | PASS | `Interface scale changes controls and text immediately. Circuit zoom is separate.` is visible at both 2.0 and 1.0. Old restart warning text is absent from this help area. Captures 03/04. |
| Live Window explicit fonts 2.0 -> 1.0 | PASS | Without closing/reopening Preferences, focused the 2.0 slider thumb and pressed Home. Italic help and all three slider labels (`1.0x`, `2.0x`, `3.0x`) shrink consistently with ordinary controls at 1.0. No oversized stale 2.0 text remains in the sampled area. Captures 03/04. |
| Live Software explicit font 2.0 -> 1.0 | PASS | Returned to the already-created Software page in the same window/session. Validation caption uses the smaller control-font scale and displays its complete text on one line. All five Browse actions fit horizontally and are visible in this 1.0 view. Capture 05, compared with 01. |

The previously confirmed missing-final-word defect is visibly resolved on this artifact. No new defect observed within these focused cases. This does not establish all-locale/all-theme/all-scale matrix completion, pixel-exact font measurements, or acceptance of unrelated workflows.

## Viewed evidence

All FIVE actual window captures were emitted and visually inspected using the base64 image fallback for the known sandbox mountinfo failure. No extra captures were taken.

Evidence directory: `/tmp/logisim-visual-qa-9yY5Yz/evidence/`.

1. `01-software-1200-scale2.png` — full wrapped caption and initial Browse controls, 1200x900/2.0.
2. `02-window-scale2-help.png` — transitional duplicate Software image captured before the page switch completed; despite filename, NOT used as Window evidence.
3. `03-window-scale2-help.png` — settled Window page, new help text, 2.0 label fonts.
4. `04-window-live-scale1.png` — same open Preferences window after native slider Home sets isolated scale to 1.0.
5. `05-software-live-scale1.png` — same Software page after live transition, complete caption and all five Browse actions.

Metadata, frozen jar, logs and screenshots retained. Runtime app.log contained no output/errors during this bounded run.

## Cleanup / verification boundary

Runner stop completed. Verified absence under /proc of app PID 683589, compositor PID 683505, and helper PID 683569. No owned processes remain.

Parent reports 1026 tests passing with zero failures/errors/skips, both Checkstyle tasks and shadowJar passing. This worker did not rerun those commands. The native results above are independently observed and limited to the assigned cases. Production remains frozen; no pending edits from this worker.

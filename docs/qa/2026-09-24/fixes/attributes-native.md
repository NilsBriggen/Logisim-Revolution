# Bounded native attribute QA — 2026-09-24

Result: all four requested workflows PASS on the frozen runner-copied jar. No production edits,
Gradle, builds or unit-test execution. Source-review findings were delivered before this native pass.

## Provenance and isolation

- Private runner session: `/tmp/logisim-visual-qa-tz4cdx`; started 2026-09-24 17:10:06 UTC.
- Jar: `/tmp/logisim-visual-qa-tz4cdx/work/application.jar`, copied by the runner from the existing
  `build/libs/logisim-evolution-5.1.0dev-all.jar` (19:00 local file timestamp).
- SHA256: `214cc5b2ed429a6a6c5eac0166ede2187e46964fda4b18d2017dae8eaf87a9ea`.
- Repository HEAD recorded by runner: `0a62266649937f3c2cbcb1ef495b6d9be6ba2d5a`; dirty workspace
  listing is retained in session.json. The jar hash, not HEAD alone, identifies this runtime.
- Private KWin/Xwayland display 2560x1600, dark theme, UI scale 1.6, circuit zoom 100%; application
  window 2304x1440. This is native Swing/window-manager QA on a virtual display, not physical HiDPI QA.
- Runner redirected `user.home`, user/system Java preferences and XDG state into this session.
  The JVM's system properties were checked before stopping it. No real preferences/circuits/autosaves
  were edited. The runner's initial ui-smoke fixture was a copy; it was left unchanged.
- Tested a scratch `work/attributes.circ`: one two-input, east-facing AND gate at (300,200), initially
  Negate 1=No, Negate 2=Yes. Only this disposable fixture was saved during checks.
- Session stopped through the runner's identity-checked stop operation; evidence retained.

## Results

| Workflow | Native action and observed result |
| --- | --- |
| inspector-19 cancellation | Selected both negate label rows using Shift, opened Negate 1's combo (a popup window was observed), pressed Escape twice. Both rows remained selected and values remained No/Yes; gate retained only its lower inversion bubble and project remained clean. PASS. |
| Atomic multi-row commit / Undo | Changed the second row to No and saved the No/No baseline. Selected both labels, opened the first value, used Home/Enter to commit Yes. Both became Yes with both bubbles. Exactly one Ctrl+Z restored No/No and the saved clean state. PASS. |
| Ordinary hover wheel | After Undo, clicked the gate to leave canvas focus, hovered Facing without clicking it, and sent two ordinary wheel-up events. Facing remained East, geometry unchanged and project remained clean. PASS. |
| Valid label draft on focus/model transfer | Typed ScratchLabel into Label without Enter, clicked empty canvas, then reselected the original gate. Its inspector title/value and rendered label showed ScratchLabel. Saved scratch XML contains `<a name="label" val="ScratchLabel"/>` on the AND gate; negate defaults remain No/No. PASS. |

## Captures — all six independently viewed

All paths below are under `/tmp/logisim-visual-qa-tz4cdx/evidence/`:

1. `01-fixture.png`: loaded scratch AND and empty inspector before selection.
2. `02-mixed-baseline.png`: selected AND, East, No/Yes baseline.
3. `03-cancel-undo-menu.png`: after two Escapes, both selected negate rows still No/Yes, clean title.
   Despite the filename, the heavyweight Edit menu is not included in the direct window image;
   do not treat this as evidence of menu-enabled state or a direct measurement of undo depth.
4. `04-multi-commit.png`: both values Yes, both inversion bubbles and unsaved state.
5. `05-one-undo-and-ordinary-wheel.png`: after one Undo and two ordinary wheel-up events, No/No,
   Facing East, no bubbles and clean saved state.
6. `06-label-focus-loss.png`: after empty-canvas click and reselection, ScratchLabel belongs to the
   original gate and is shown both in the inspector and on canvas.

One root-window capture attempt failed without producing an image; it was retried as a direct
application-window capture. Six images total were produced and viewed. The sandbox image reader
failed with mountinfo; scoped base64 reads of these exact PNGs were used to view them instead.
The private application log was empty at completion.

## Limits / remaining scope

This pass does not retest high-resolution wheel input, Alt+wheel nudges, splitter scrolling/dragging,
invalid labels, cross-circuit/mixed-component edits, Redo, multiple UI scales, or physical monitors.
Cancellation preserves visible model and clean state; internal undo depth was not instrumented.
No new native regression was found in the four requested paths. Parent's separate native failures
are not cleared by these results. Prior source-review findings remain a separate report, not native
failures from this session.

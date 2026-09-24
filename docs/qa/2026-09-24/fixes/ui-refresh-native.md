# Bounded native UI refresh acceptance — 2026-09-24

Result: PASS for the assigned fresh Welcome → HDL, keyboard theme selection, and released UI-scale slider sequence. This is not a full visual/workflow matrix.

## Artifact and isolation

- Built jar: `/home/nilsb/Documents/projects/logisim-revolution/build/libs/logisim-evolution-5.1.0dev-all.jar`.
- Built jar and runner-frozen `work/application.jar` both verified SHA256: `f582284fa310aa5863c8861d3da4673b24dad91f143bb46fc4e755a6c12be8bb`.
- Session: `/tmp/logisim-visual-qa-DU1Ky3`; private KWin/Xwayland display 2560×1600, fresh isolated preferences, initial Light/2.0. Main window 4194355 (2304×1440); Preferences window 4194392 (1640×1120).
- No production edits, Gradle commands, commits, or real circuit/autosave changes in this pass. Parent reported 1026 tests passing and Checkstyle/shadowJar passing; these were not rerun here.

## Native results

1. Fresh Welcome → create HDL `qa_direct_hdl`: PASS. Used the create-HDL control and name dialog directly; settled editor displays VHDL and Welcome is dismissed. Never selected main or used main → HDL as a workaround.
2. Theme combo keyboard Dark/Light: PASS. Focused combo, dismissed popup, then Down selected Dark. After refresh, the combo lost focus, so an initial standalone Up did not change it; refocusing the combo and Escape/Up selected Light. Both keyboard selection handlers completed without the former delegate-null exception.
3. UI scale slider 2.0 → 1.0 → 1.6: PASS. Both changes used native mouse-down, drag, mouse-up on the thumb, not preference injection or keyboard substitution. Rendered controls resized at each release. Final private XML confirms `Scale=1.6` and `theme=light`.
4. Application log: PASS. `/tmp/logisim-visual-qa-DU1Ky3/app.log` is zero bytes both before and after shutdown; no combo/slider delegate exceptions or other application log output.

## Captures — all eight visually inspected

Directory: `/tmp/logisim-visual-qa-DU1Ky3/evidence/`.

- `01-fresh-welcome.png`: fresh initial Welcome.
- `02-direct-hdl.png`: taken too early, before creation action settled; still Welcome. Retained transparently, not used as acceptance evidence.
- `03-direct-hdl-settled.png`: direct HDL editor acceptance, no fallback navigation.
- `04-preferences-controls.png`: setup/navigation capture on initial International preferences page.
- `05-window-settings.png`: Window preferences, initial Light/2.0.
- `06-keyboard-dark-scale2.png`: keyboard-selected Dark, scale 2.0.
- `07-keyboard-light-slider1.png`: filename reflects intended Light transition, but actual capture is Dark/1.0 because focus had moved during refresh; validates the first slider release only.
- `08-keyboard-light-slider16.png`: final keyboard-selected Light, slider at 1.6.

Eight rather than approximately five captures were retained because of early-action timing and preferences navigation; no broader matrix was attempted.

## Cleanup and remaining scope

- Runner stop completed; app PID 683807, compositor PID 683751, helper PID 683787 all independently checked absent from `/proc` afterward. Evidence retained.
- Real `/home/nilsb/.java/.userPrefs/com/cburch/logisim/prefs.xml` hash remains the pre-session baseline: `450a87ed346c80b0570b06f27d7c19ea6bfaf1a66fccac45d858fdd640de020f`.
- No remaining work for this bounded acceptance request. Other platforms, themes, scales, and workflows were not certified by this pass. Production remains frozen.

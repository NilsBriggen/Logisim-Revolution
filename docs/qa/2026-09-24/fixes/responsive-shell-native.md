# Responsive shell — bounded native acceptance, 2026-09-24

## Result

**PASS for the assigned light-theme / 2.0 responsive-shell checks**, with the limits below.
This is rendered native evidence, not inferred from layout calculations. No Gradle/build ran.
No production source was edited during this pass. One test fixture was corrected afterward in
response to the parent's two integration failures; parent still owns its rerun.

## Provenance and isolation

- Session: `/tmp/logisim-visual-qa-sWUQ60`; private KWin/Xwayland display `:4`, 2560x1600.
- Main window ID: `4194355`. Close dialog ID: `4194416`.
- Frozen runtime jar: `/tmp/logisim-visual-qa-sWUQ60/work/application.jar`.
- Verified SHA256 before and after:
  `de2e9f49d6f6551e2f293ec2d127afbf18ba3d21369c50e4cb5a23e71716bec2`.
- Window sizes tested: 2560x1400 and 1280x800; UI scale2.0, light theme. This is a constrained
  window on a virtual native display, not a physical1280 monitor/mixed-DPI acceptance run.
- Scratch project/entity only (`qa_responsive`); discarded via the explicit Discard button on close.
  No real circuits/autosaves opened, changed or discarded.
- Private user.home/userRoot/systemRoot supplied by the runner. Real prefs SHA256 before/after:
  `450a87ed346c80b0570b06f27d7c19ea6bfaf1a66fccac45d858fdd640de020f`.
- App log: 0 bytes, no exceptions logged.
- Owned app663069, compositor663015 and helper663061 were stopped through runner ownership checks;
  all three /proc entries confirmed absent. Evidence retained.
- Exactly10 captures; every capture was viewed.

## Observed acceptance

1. **Usable constrained HDL center — PASS.** Capture04 at1280x800 shows the actual HDL editor
   approximately x354-994 (~640px), versus the earlier ~200px strip. Code begins aroundx420 and
   displays the entire `USE ieee.std_logic_1164.all;` line. Both side panes remain present and are
   visibly narrower; Properties labels elide and the navigator can scroll horizontally, a deliberate
   center-first tradeoff rather than a claim that the side panes retain full roomy layouts.
2. **Widen restores widths — PASS.** Capture03 provides the real HDL wide baseline. Capture05 after
   narrowing/widening restores the same navigator (~520px) and inspector (~560px) widths/visibility.
3. **Real divider drag — PASS.** Wide left splitter was dragged with native mouse down/move from
   x620 to740. Capture06 was taken while held; capture07 after release. Navigator grew by120px
   (~520->640px), the divider stayed put, and the private prefs recorded logical sideWidth320.
   No snapback observed during or after this drag.
4. **Visibility intent survives resize — PASS.** Inspector closed by its visible close control;
   window narrowed then widened. Capture08 shows inspector still absent and navigator still at the
   requested640px wide size. Existing Properties activity action explicitly reopens it.
5. **Save uses desired, not compressed widths — PASS.** After reopening and narrowing again,
   capture09 shows a still-usable ~640px HDL center, compressed navigator (~270px) and inspector
   (~236px). The app was closed at1280x800 through its window close control, then the disposable
   project was discarded. Private persisted prefs after actual close contain:
   - `shell.sideWidth=320` logical (640 physical at2.0, matching the explicit drag).
   - `shell.inspectorWidth=280` logical (560 physical, not ~236px compressed).
   - `shell.inspectorVisible=true`.
   - `shell.activeSideView=explorer`; `windowHeight=800`.
   This verifies the real close/save path, not only a mocked setter.

## Capture manifest

All under `/tmp/logisim-visual-qa-sWUQ60/evidence/`:

|ID|File|Actual content|
|---|---|---|
|01|01-startup.png|Fresh welcome surface,2304x1440|
|02|02-wide-hdl.png|Entity created but welcome surface still displayed; NOT accepted HDL evidence|
|03|03-wide-hdl-open.png|Real HDL editor after main->HDL navigator activation,2560x1400|
|04|04-constrained-hdl.png|1280x800 usable HDL center with both panes compressed|
|05|05-wide-restored.png|2560x1400, original pane widths restored|
|06|06-divider-held.png|Native left-divider drag held at new position|
|07|07-divider-released.png|Same new position after mouse release|
|08|08-hidden-inspector-restored-wide.png|Explicitly hidden inspector remains hidden after resize cycle|
|09|09-constrained-after-drag.png|1280x800 after drag and inspector reopen; desired widths temporarily compressed|
|10|10-disposable-close.png|Confirm Close for the owned scratch project; Discard subsequently clicked|

## Integrated test failures and bounded correction

Read parent's1017-run ShellResponsiveLayoutTest XML:
- wide restoration test expected520 but measured597 at line48;
- initially constrained test reached savePreferences but no width setter fired at line104.

The native jar passes the corresponding center, wide restoration and persistence checks. The old
fixture mixed a synthetic Label.font override with whatever LaF/zoom the preceding tests left,
and restored a resolved, already-scaled font as a permanent UIManager developer override. It did
not assert that its requested2.0 remained the effective scale during layout.

Changed **only** `src/test/java/com/cburch/logisim/gui/shell/ShellResponsiveLayoutTest.java` after
the native pass:
- clear/preserve the actual Label.font developer override (not its resolved scaled LaF value);
- install native FlatLightLaf, then apply scale2.0;
- restore original LaF, raw UIScale zoom and original developer override in cleanup;
- assert effective scale2.0 at every layout call.

All existing640px minimum-center, exact520/560 restoration, proportionality, no-write and
persistence assertions are retained. The correction follows the newer NativeFonts fixture in
UiScaleTestSupport. **Fixture-state diagnosis remains subject to parent rerun**; no claim these
tests now pass, and no production allocation workaround was added. Scoped git diff --check passes.

Parent selector: `--tests com.cburch.logisim.gui.shell.ShellResponsiveLayoutTest`.

## Limits / incidental observation

- Not full matrix acceptance: no dark-theme responsive pass, other scales, physical mixed-DPI,
  timing/HDL transitions, running simulation or compressed-state divider-drag acceptance.
- This native check used Circuits as the left-side view, not a fresh palette-label/grid sweep;
  allocation/persistence belongs to the same shared side pane.
- Initial entity creation from welcome left the welcome surface displayed despite the HDL tab/title
  (capture02). Selecting main then the HDL entity dismissed it (capture03), with no logged exception.
  Recorded for the parent's welcome integration; no expanded investigation or source edit.

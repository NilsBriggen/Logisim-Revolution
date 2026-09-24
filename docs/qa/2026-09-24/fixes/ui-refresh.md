# Deferred UI refresh — narrow P1 freeze checkpoint

Source/tests frozen. No Gradle/build/test process run here. No preferences, circuits, Frame,
resources or other production files modified. Native verification awaits the parent's final jar.

## Confirmed cause

Read `/tmp/logisim-visual-qa-PcImXB/app.log`: BasicComboBoxUI.selectNextPossibleValue resumes with
comboBox=null (line1 trace); BasicSliderUI.TrackListener.mouseReleased resumes with slider=null
(line41 and74 traces). Both handlers notify model/action listeners before subsequently repainting
through their UI delegate's component reference. Theme.applyResolved and UiScale.refresh previously
called FlatLaf.updateUI and Theme.fireChanged on that same EDT stack. Uninstalling the active
delegate clears the reference before the original handler reaches its repaint. Java21 bytecode
inspection confirmed these exact handler tails; FlatLaf UIScale.setZoomFactor itself changes metrics
and publishes property changes, rather than synchronously calling updateUI.

## Changed files

- `src/main/java/com/cburch/logisim/gui/theme/Theme.java`: one shared atomic pending flag and
  refreshUiLater callback. Requests coalesce into an invokeLater callback, where native component
  trees refresh first and custom listeners are notified second. The pending flag clears before
  processing so a genuinely new request during notification can schedule a subsequent refresh.
  applyResolved now schedules this work instead of replacing delegates inline. Theme/palette
  state remains immediate, preserving existing callers that inspect the selected state.
- `src/main/java/com/cburch/logisim/util/UiScale.java`: retains EDT scaling and existing preference
  semantics, but uses the same deferred callback instead of a second synchronous updateUI path.
- `src/test/java/com/cburch/logisim/gui/theme/ThemeTest.java`: listener/palette-order test now
  drains the queued EDT refresh and explicitly rejects an inline listener callback.
- NEW `src/test/java/com/cburch/logisim/gui/theme/ThemeRefreshTest.java`: two focused headless
  delegate-lifetime regressions, described below.

## Tests added

- Combo: actual BasicComboBoxUI.selectNextPossibleValue, Light->Dark->Light with a scale change
  in the same initiating event. The old delegate remains installed/attached through each handler;
  no custom refresh occurs inline; exactly one later refresh observes the final1.6 scale.
- Slider: actual BasicSliderUI.TrackListener.mouseReleased, adjusting->released changes2->1->1.6.
  Each release returns with its original delegate still attached; exactly one later refresh installs
  the new delegate, at the latest requested scale.

Headless components are not members of Window.getWindows, so a Theme listener refreshes the local
test tree explicitly. This reproduces delegate uninstall when the notification is inline, without
requiring a display. AppPreferences/Projects/DesktopScale are mocked on the EDT: no actual prefs
writes, real project mutation or external desktop probe. Fixtures drain EDT work deterministically
without sleeps, remove listeners and restore the prior LaF/zoom. No assertion was weakened.

Suggested parent selectors:

```
--tests com.cburch.logisim.gui.theme.ThemeRefreshTest
--tests com.cburch.logisim.gui.theme.ThemeTest
--tests com.cburch.logisim.util.UiScaleTest
```

Scoped git diff --check passed. Compilation/test success is NOT claimed; parent owns execution.
No Rawls coordination needed for these four files; no other scope touched.

## Final-jar native follow-up, not yet run

One private session: exercise theme combo via keyboard Dark/Light and scale slider by real release
2->1->1.6. Inspect every capture and the fresh app log; verify no delegate-null exceptions, then
stop owned session processes. Await explicit final-jar ready/hash before launching. This is not a
claim of complete theme/scale/native matrix acceptance.

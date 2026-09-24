# Completed test-result retention fix — frozen for rebuild

2026-09-24. No Gradle/JUnit execution, Theme changes, real preferences/circuit writes, commits or pushes.

## Exact cause and minimal fix

`Project.setCurrentHdlModel` calls `oldCircuit.displayChanged()` after suspending the active state. Returning through `Project.setCircuitState` calls `newCircuit.displayChanged()`. Both dispatch `CircuitEvent.ACTION_DISPLAY_CHANGE` (9). The test Model's lifetime circuit listener treated this view/halo notification as a circuit edit and called `clearResults()` even though ModelHistory correctly cached the same model.

Changed only `src/main/java/com/cburch/logisim/gui/test/Model.java`: alongside the existing ACTION_SET_NAME exemption, ignore ACTION_DISPLAY_CHANGE. Every other event retains existing invalidation behavior. Vector replacement and explicit reset are unchanged.

## Regression coverage

New `src/test/java/com/cburch/logisim/gui/test/ModelResultLifecycleTest.java` has four tests:

- A real wired input/output circuit is evaluated synchronously through TestVectorEvaluator, producing exactly two passes and two failures. Actual Circuit.displayChanged and real Project HDL/circuit transitions preserve counts, result reports, sorted row order, cached model identity and selection suspension. The trace explicitly asserts the two received event9 notifications.
- An actual CircuitMutation wire addition invalidates completed results while preserving the loaded vector.
- Actual input-component fireInvalidated dispatch through Circuit still clears results while the model is deselected in HDL; return must not resurrect the stale results.
- Replacing the vector clears completed results.

Tests run their lifecycle operations on the EDT, use JUnit temporary vector files, and shut down the Project simulator. No user settings are written by the fixture.

## Verification

Standalone javac21 compilation of both scoped files passes against the frozen jar and local JUnit/Mockito compile dependencies. Scoped git diff --check passes. JUnit and Checkstyle execution belong to the parent.

Disposable actual-event probe: `/tmp/logisim-result-retention-GMivLI/ResultEventProbe.java`, with isolated user.home and Java preference roots. Real evaluator baseline2/2, actual Project transitions:

```
Frozen f582284f jar:
baseline=2/2
event=9 counts=0/0
HDL=0/0
event=9 counts=0/0
return=0/0

Compiled scoped Model overlay:
baseline=2/2
event=9 counts=2/2
HDL=2/2
event=9 counts=2/2
return=2/2
```

The trace uses a programmatic real wired-pin circuit, not the native inverter fixture; both yield2/2. Probe exited. Prior native session jNr7tH already stopped. Production source is frozen now.

Suggested parent selector: `com.cburch.logisim.gui.test.ModelResultLifecycleTest`, plus existing `com.cburch.logisim.gui.test.TestFrameLifecycleTest`. Await parent rebuilt jar-ready before repeating native completed-vector2/2 -> existing HDL -> same inverter preservation. Earlier final-jar failure and passing constrained chooser evidence remain in `/tmp/logisim-fix-timing-native-final.md`.

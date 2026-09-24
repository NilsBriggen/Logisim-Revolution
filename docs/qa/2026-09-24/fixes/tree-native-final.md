# Final bounded native tree acceptance — 2026-09-24

## Build and isolation

Verified source jar and private frozen copy SHA256:
f582284fa310aa5863c8861d3da4673b24dad91f143bb46fc4e755a6c12be8bb

Owned session: /tmp/logisim-visual-qa-wx07gq. Native KWin/Xwayland, 2560x1600 virtual display,
2304x1440 app window, disposable copy of scripts/fixtures/ui-smoke.circ, separate user.home,
preferences, configuration and autosave locations. No real user documents/preferences touched.
Parent session PcImXB was not reused. No source edits, Gradle or test execution.
The parent-reported 1026 passing tests and clean lint/build remain separate integration evidence.

## Native result

PASS for the previously observed root-only tree failure in this bounded live roundtrip:

1. Started Light at exactly 1.6 (harness setting). Opened Library then its tree fallback.
   Project tools main/counter_sub/inv/osc/mismatch and built-in libraries were immediately visible.
   Clicked the Gates disclosure; NOT Gate, Buffer, AND/OR/NAND/NOR/XOR/XNOR, parity gates,
   Controlled Buffer, Controlled Inverter and PLA were readable.
2. Switched theme through the actual Preferences combo (including a transient Follow system
   selection), then explicitly Dark. Moved the native interface slider to its 2.0 tick.
   Existing Gates children survived. Collapsed/re-expanded Gates and selected Buffer;
   the selected row and Buffer property heading were visible. No component was placed.
3. Returned toward 1.6 and explicitly Light. Gates children and selected Buffer survived.
   Collapsed Gates and expanded Multiplexers after the roundtrip. Multiplexer, Demultiplexer,
   Decoder, Priority Encoder and Bit Selector were readable. Selected Multiplexer and verified
   its highlighted row and matching property heading.

PRECISION LIMIT: this is not proof of the exact numeric 1.6 -> 2.0 -> 1.6 sequence. The mouse
slider's first return readback was 1.61; one bounded fine adjustment ended at 1.59, confirmed
in the isolated prefs XML after shutdown. The dark intermediate capture shows the 2.0 tick
but its exact hundredth was not separately recorded. Do not label the final capture as exact
1.6 solely from its nominal filename. The root/child retention and actual selection checks
passed across the live UI reinstalls, including the nearby 1.61 and final 1.59 endpoint.

## Three primary captures (all inspected)

- /tmp/logisim-visual-qa-wx07gq/evidence/tree-light-1.6.png
  Initial exact light 1.6; Gates expanded with names visible.
- /tmp/logisim-visual-qa-wx07gq/evidence/tree-dark-2.png
  Dark near the 2.0 tick; Gates expanded and Buffer selected, matching inspector heading.
- /tmp/logisim-visual-qa-wx07gq/evidence/tree-roundtrip-light-1.6.png
  Nominal filename; actual final scale 1.59. Multiplexers expanded, Multiplexer selected.

Additional navigation/settings captures are retained as scratch evidence, not extra acceptance
claims. navigation.png also shows readable two-column palette examples, including Controlled
Buffer and two-line Controlled Inverter at initial light 1.6. No full long-FP-name, narrow-pane,
keyboard-only or all-library picker acceptance is claimed by this bounded tree check.

## Log and cleanup

/tmp/logisim-visual-qa-wx07gq/app.log is 0 bytes after the run and owned-session shutdown:
no app exceptions, errors or warnings recorded. This is an app-log claim, not a compositor-log
cleanliness assertion. One attempted capture of the X root window failed in the harness;
capturing the actual Preferences popup succeeded and did not affect application state.

Stopped the owned app/compositor/helper through scripts/visual-qa.mjs stop, which checks PID
start-time and ownership. Evidence retained. No newly observed tree functional failure and
no production follow-up edits. Exact slider-endpoint acceptance remains qualified above.

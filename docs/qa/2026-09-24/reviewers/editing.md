# Editing QA review — 24 September 2026

**Priority result: P0 confirmed. File > Close > Escape discards unsaved edits. Choosing Save and then cancelling the Save chooser also discards them.** Both were independently reproduced on new scratch projects, and the faulty handler is present at HEAD `0a62266649937f3c2cbcb1ef495b6d9be6ba2d5a`.

This review owns only inspect-shell, inspect-inspector and inspect-canvas. It is a review and fix plan; no production changes, build, commit or push were made.

## Evidence and limits

The runtime jar was `/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/logisim.jar`. Its title reports `2532c84d`, built at 16:07:16, while recovered verifier output shows it contains ColorOptions from the later working tree. Its exact commit provenance is therefore uncertain. Source was checked at the requested HEAD; MenuFile, AttrTable and ShellLayout are unchanged between HEAD and its parent. This is runtime evidence for the supplied jar plus current-source confirmation, not a claim to have run a newly built HEAD.

Session `cx-editing` used fresh dark preferences at scale 1.6, private KWin/Xwayland and only new disposable circuits. `user.home` was explicitly redirected to `/tmp/logisim-qa-recovery-20260924/editing-home`, alongside isolated user/system preference roots, and checked through the JVM. The original harness does **not** isolate autosaves by itself.

The default sandbox and image viewer failed with the mountinfo error. Scoped escalated commands and the supplied base64-to-image fallback worked. A kept-open terminal was needed to prevent the tool lifetime from ending background GUI launches. Main-window/dialog captures used `win`; the composed `shot` capture was used only where a heavyweight popup had to be included. The original shot helper hard-codes 2560×1600 and is not reliable evidence of nondefault window geometry.

Nine specific archived images were independently viewed, listed in [editing.json](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/reviewers/editing.json). They support tab overflow, drawer layout, annotation/pin contrast, grid zoom behavior, fit placement, stale Appearance title and gate-ghost styling. Other archived filenames are not treated as visual proof. Recovered partial verifier commands were inspected without operating their sessions.

## Findings and concrete fix plan

### P0 — Make project close cancellation safe

**Candidates:** shell-01.

Both routes dispose the edited project and leave a blank replacement. Only disposable unsaved in-memory content was lost; recoverability from autosave was not tested. MenuFile only excludes result 2, so CLOSED_OPTION (-1) proceeds; it also ignores doSave's false result. Frame.confirmClose is safer for dismissal but stops autosave even after a cancelled/failed Save, so blindly routing to it is insufficient.

**Reproduce:** New scratch project; place an AND gate; File > Close; Escape. Repeat with Save, then cancel the Save chooser.

**Plan:** Use one close-decision routine for menu/window/quit entry points. Proceed only on explicit discard or successful save; keep project, undo history and autosave running on Escape, dialog X, Cancel and failed/cancelled Save. Dispose and clean autosave only after an accepted decision. Localize the prompt.

**Acceptance:** Run scratch tests for Escape, dialog X, Cancel, Save success, Save chooser Cancel, unwritable target, Discard, one-window and multiple-window cases. Cancel/failure preserves the exact component/attribute model, dirty state, frame and running autosave; success closes exactly the intended project.

**Evidence/source:** [02-before-close.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/editing-evidence/02-before-close.png); [03-confirm-close.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/editing-evidence/03-confirm-close.png); [04-after-escape.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/editing-evidence/04-after-escape.png); [05-save-dialog.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/editing-evidence/05-save-dialog.png); [06-after-save-cancel.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/editing-evidence/06-after-save-cancel.png); [MenuFile.java:137](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/menu/MenuFile.java:137); [Frame.java:683](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/main/Frame.java:683).

### P1 — Remove writes from attribute cancellation; batch multi-row commits

**Candidates:** inspector-19.

Cancel makes both No; one Undo restores the changed Yes. Multi-commit sequence is Yes/Yes -> Yes/No -> Yes/No -> No/No over three Undos. AttrTable.fireEditingCanceled notifies listeners, then writes the editor value to every selected row. JTable has already cleared editing-row state, defeating the skip. Each setValueAt generates a separate project action.

**Reproduce:** AND gate: Negate 1=No, Negate 2=Yes. Select both label rows, open Negate 1 value, Escape to dismiss popup then Escape to cancel editing. For undo test start both No, multi-set Yes, undo three times.

**Plan:** Cancellation must only discard the draft. Capture target rows and pre-edit values before editor teardown. Commit compatible targets through one atomic SetAttributeAction; suppress no-op mutations and reentrant commits.

**Acceptance:** Cancel with mixed values leaves all values and undo depth unchanged. One Enter commit creates one undo item; one Undo restores all original values and one Redo reapplies them. Cover popup Escape, editor Escape, focus loss, model switch and mixed component selections.

**Evidence/source:** [14-cancel-sequence.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/editing-evidence/14-cancel-sequence.png); [18-multi-undo-sequence.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/editing-evidence/18-multi-undo-sequence.png); [AttrTable.java:378](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/generic/AttrTable.java:378); [AttrTableSelectionModel.java:157](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/main/AttrTableSelectionModel.java:157).

### P1 — Make property scrolling safe

**Candidates:** inspector-05, inspector-06.

East changes to North and the gate rotates. A normal wheel gesture can edit the circuit unexpectedly. AttrTable installs a wheel listener and applies values under the pointer; non-value paths return without forwarding to the enclosing scrollpane. Combo changes bypass numeric-gesture batching.

**Reproduce:** Select the gate, leave canvas focus, hover Facing value without clicking and scroll up twice.

**Plan:** Default wheel input scrolls the table. Require an explicitly active editor or documented modifier for value nudging; forward unused events. Use one direction convention and one gesture transaction across editor types.

**Acceptance:** A 64-bit splitter property list scrolls over both columns without changing circuit attributes or undo depth. Explicit numeric/enum nudge has predictable direction and one Undo per gesture; rapid/high-resolution wheel events are handled deterministically.

**Evidence/source:** [21-divider-after.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/editing-evidence/21-divider-after.png); [22-wheel-facing-panel.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/editing-evidence/22-wheel-facing-panel.png); [AttrTable.java:135](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/generic/AttrTable.java:135).

### P2 — Preserve drafts across selection/focus changes

**Candidates:** inspector-07, inspector-08.

The visible draft disappears and the label remains empty. setAttrTableModel cancels the editor before focusLost can commit; validation messages separately expose raw conversion text.

**Reproduce:** Type ScratchLabel in the selected gate's Label field, click empty canvas, then reselect the gate.

**Plan:** Resolve the active editor before changing models. Commit valid drafts once; retain invalid text with an inline error and a defined focus policy. Escape alone discards the draft. Avoid raw Java exception copy.

**Acceptance:** Enter, Tab and valid canvas-focus transfer persist the same value exactly once; Escape does not. Invalid text remains editable and associated with its field; changing selection never applies a draft to the wrong object.

**Evidence/source:** [20-label-sequence.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/editing-evidence/20-label-sequence.png); [AttrTable.java:217](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/generic/AttrTable.java:217); [AttrTable.java:415](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/generic/AttrTable.java:415); [AttributeSetTableModel.java:260](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/generic/AttributeSetTableModel.java:260).

### P1 — Stop restoring divider preferences during user drags

**Candidates:** shell-03, inspector-02.

Requested sizes do not persist; only a small incidental divider change is possible. SizedSplit.doLayout repeatedly reapplies storedSize while continuousLayout runs, before mouseReleased stores the result.

**Reproduce:** Drag inspector left about 300 px and side panel right about 230 px, slowly then release.

**Plan:** Apply stored dimensions on initialization or explicit restore, not every layout. Track active drag if needed; persist the actual final child size. Scale divider hit targets.

**Acceptance:** All three dividers follow the pointer, retain the final size, survive window resize and isolated restart, and keep both panes usable at 1.0/1.6/2.0 UI scale. Drawer drag remains a required acceptance test, not a live test completed here.

**Evidence/source:** [20-label-reselected.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/editing-evidence/20-label-reselected.png); [21-divider-after.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/editing-evidence/21-divider-after.png); [ShellLayout.java:166](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/ShellLayout.java:166); [ShellLayout.java:201](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/ShellLayout.java:201).

### P2 — Use one scale policy and readable panel minimums

**Candidates:** shell-02, shell-05, shell-21, inspector-01.

Inspector names/values elide; menu shortcuts and chooser icons are much smaller than labels. Original drawer crop shows the same metric mismatch. Late font-only resizing coexists with device-pixel LayoutPrefs and custom metrics. Do not assume simply multiplying everything by 1.6 is safe.

**Reproduce:** Open a fresh dark window at UI scale 1.6; inspect gate properties, Edit menu and Save chooser.

**Plan:** Define logical UI dimensions, integrate font and Look-and-Feel metrics once, remove double scaling, migrate stored sizes and add content-aware column widths/padding. Use accessible elision disclosure where finite width remains necessary.

**Acceptance:** Rendered 1.0/1.6/2.0 dark/light matrix includes long circuit names, a 64-row splitter, menus, chooser and drawer at narrow/default/maximized widths. Labels and accelerator regions must not overlap; major controls and complete values remain reachable.

**Evidence/source:** [08-after-multicancel-menu-crop.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/editing-evidence/08-after-multicancel-menu-crop.png); [05-save-dialog.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/editing-evidence/05-save-dialog.png); [48z-drawer-left.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/shell/48z-drawer-left.png); [Startup.java:1161](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/start/Startup.java:1161); [LayoutPrefs.java:26](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/LayoutPrefs.java:26); [07-multiedit-panel.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/editing-evidence/07-multiedit-panel.png); [14-cancel-sequence.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/editing-evidence/14-cancel-sequence.png); [HdlColorRenderer.java:58](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/fpga/gui/HdlColorRenderer.java:58); [HdlColorRenderer.java:83](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/fpga/gui/HdlColorRenderer.java:83).

### P1 — Give hidden panels a direct restore path

**Candidates:** shell-04, inspector-11, shell-16, inspector-27, shell-17.

Source shows Show Properties only changes its model, while drawer tab X hides the whole drawer. State list lacks guidance when nothing is opted in. Visibility state is separate from the actions that target those panels. BottomPanel deliberately maps tab close to drawer hide.

**Reproduce:** Close Properties; invoke a component's Show Properties. For drawer, close one timing tab.

**Plan:** Add synchronized Window menu toggles and make Show Properties reveal Inspector. Separate hide-drawer from close-tab affordances, retaining existing reopen commands. Explain empty STATE and how to enable a register.

**Acceptance:** Hide and reopen every panel without resetting unrelated sizes, before/after restart; menu checks agree with visibility. Closing one tab has the documented scope. Empty and opted-in-register state views both render useful content.

**Evidence/source:** [Frame.java:372](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/main/Frame.java:372); [Frame.java:1182](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/main/Frame.java:1182); [ShellLayout.java:253](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/ShellLayout.java:253); [ShellLayout.java:332](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/ShellLayout.java:332); [48z-drawer-left.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/shell/48z-drawer-left.png); [BottomPanel.java:43](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/BottomPanel.java:43); [RegTabContent.java:178](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/generic/RegTabContent.java:178).

### P2 — Keep active tabs reachable and final-tab state coherent

**Candidates:** shell-07, shell-08.

Archived overflow has no usable scroll controls; live last-tab close leaves the editor active without a tab. Scroll-button properties do not switch JTabbedPane to scroll layout. Empty EditorTabModel clears only selection; the canvas keeps current circuit.

**Reproduce:** Open enough circuits to overflow the strip (archived evidence), and close the sole tab (independently reproduced).

**Plan:** Use scroll layout with active-tab reveal and overflow picker; avoid rebuilding the entire strip unnecessarily. Define last-tab policy: retain the current tab or show an empty-editor view. Synchronize circuit list and tabs.

**Acceptance:** Open at least 20 long-named layout/appearance/HDL tabs, navigate by mouse and keyboard, close active/other/all tabs, and rename/reorder circuits. The active editor is always identifiable and reachable.

**Evidence/source:** [41z-tabs-full.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/shell/41z-tabs-full.png); [EditorTabs.java:53](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/EditorTabs.java:53); [EditorTabs.java:174](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/EditorTabs.java:174); [23-last-tab.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/editing-evidence/23-last-tab.png); [EditorTabModel.java:151](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/EditorTabModel.java:151); [EditorTabs.java:184](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/EditorTabs.java:184).

### P2 — Keep inspector identity and aggregate values truthful

**Candidates:** inspector-04, inspector-15, inspector-16, inspector-18.

Source uses one factory for aggregate HDL support and uppercases identifiers. Archived Appearance screenshot shows a stale LED title over circuit attributes. Mixed-value editor defaults are ambiguous. Header updates are owned by Frame while another manager swaps AttrTable directly; aggregation/display policy is embedded in generic rendering.

**Reproduce:** View a mixed selection; switch from an instance to Appearance; compare a mixed-case circuit/label with the panel title.

**Plan:** Make model/title/scope updates atomic and preserve user-entered casing. Aggregate HDL capability across applicable objects or show Mixed/Not applicable. Represent mixed values as a typed state and require an explicit change before applying defaults.

**Acceptance:** Titles match actual tool/instance/shape/circuit scope after every switch. Mixed supported/unsupported selections never show uniformly Supported. Opening and cancelling a mixed field changes nothing; bulk label numbering is previewed/documented.

**Evidence/source:** [AttributeSetTableModel.java:295](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/generic/AttributeSetTableModel.java:295); [AttrTableSelectionModel.java:65](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/main/AttrTableSelectionModel.java:65); [103-appearance-insp-z.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/inspector/103-appearance-insp-z.png); [Frame.java:947](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/main/Frame.java:947); [Frame.java:1085](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/main/Frame.java:1085); [PanelHeader.java:68](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/PanelHeader.java:68); [SelectionAttributes.java:110](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/main/SelectionAttributes.java:110).

### P2 — Improve property readability and keyboard access

**Candidates:** inspector-03, inspector-10, inspector-13, inspector-25.

Blank Properties has no instruction; saturated status fill dominates; labels run into values. Typed KeyEvents except Space are rejected by the editor. Generic HdlColorRenderer removes insets and overloads background colors; Null model has no empty state; CellEditor blocks ordinary typed starts.

**Reproduce:** Inspect fresh Poke state, an AND gate and keyboard entry into the property table.

**Plan:** Use padded cells, small status/color swatches, an explanatory empty state and persistent editable/read-only affordances. Provide visible keyboard focus, type-to-edit and a shortcut to reach Inspector.

**Acceptance:** Keyboard-only user can enter, edit, commit and cancel supported values with visible focus. Long values are accessible. Dark/light views preserve readable contrast without relying on saturated fills.

**Evidence/source:** [07-multiedit-panel.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/editing-evidence/07-multiedit-panel.png); [14-cancel-sequence.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/editing-evidence/14-cancel-sequence.png); [HdlColorRenderer.java:58](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/fpga/gui/HdlColorRenderer.java:58); [HdlColorRenderer.java:83](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/fpga/gui/HdlColorRenderer.java:83); [01-fresh.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/editing-evidence/01-fresh.png); [Frame.java:298](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/main/Frame.java:298); [Startup.java:1125](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/start/Startup.java:1125); [AttrTable.java:528](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/generic/AttrTable.java:528).

### P2 — Separate themed defaults from explicit schematic colors

**Candidates:** canvas-02, canvas-04, inspector-28.

Default annotation is black on dark; radix letter is pure blue and the digit has weak contrast over green. TextTool stores black as an explicit color; Pin uses literal blue and white independent of theme/fill.

**Reproduce:** Review dark-canvas text annotation and a binary input with value 1; archived images were inspected.

**Plan:** Introduce a semantic automatic text default with a compatible serialization/migration policy, while preserving intentional custom colors. Choose pin text/radix colors from background-aware theme tokens.

**Acceptance:** New and legacy fixtures with automatic and explicit colors remain readable in dark/light modes and after save/load/export. Explicit user colors are not silently rewritten. Validate small binary/hex pins at common zooms.

**Evidence/source:** [31-text-committed-z.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/canvas/31-text-committed-z.png); [TextTool.java:155](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/tools/TextTool.java:155); [AppPreferences.java:690](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/prefs/AppPreferences.java:690); [15-pin1-z10.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/canvas/15-pin1-z10.png); [Pin.java:875](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/std/wiring/Pin.java:875).

### P2 — Keep grid density and placement preview stable

**Candidates:** canvas-10, canvas-18.

Grid hierarchy exists only at exactly 100%; low zoom becomes a dense mesh. Gate ghost overrides the requested preview color. GridPainter branches on f==1.0 and draws every point; AbstractGate.paintBase replaces the ghost painter's color.

**Reproduce:** Compare archived 100-percent, intermediate, large and very small zoom grids; compare gate ghost with placed outline.

**Plan:** Choose grid detail from screen-pixel spacing, retaining major/minor hierarchy across zooms. Pass explicit normal/ghost/selection paint style to gate painters instead of overriding color.

**Acceptance:** Rendered 5/25/50/100/110/160/200/1000-percent views avoid mesh aliasing and excessive squares. A placement preview is distinguishable from a committed part in both themes without hiding ports.

**Evidence/source:** [47-grid-compare.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/canvas/47-grid-compare.png); [GridPainter.java:138](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/generic/GridPainter.java:138); [07-and-ghost-z.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/canvas/07-and-ghost-z.png); [08-and-placed-z.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/canvas/08-and-placed-z.png); [AbstractGate.java:383](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/std/gates/AbstractGate.java:383); [AbstractGate.java:436](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/std/gates/AbstractGate.java:436).

### P2 — Validate fit near the circuit origin before changing zoom policy

**Candidates:** canvas-01, canvas-11, canvas-12, shell-37.

Asymmetry is visible, but the original 'never centers' source explanation is false. Center calls exist. Negative scroll offsets near origin and skipping center when zoom changes by less than 0.01 are hypotheses requiring a fixture reproduction.

**Reproduce:** Archived MUX4WAY16 Fit image shows a left-hugging circuit and unused right space. No new live fit test.

**Plan:** First reproduce at origin and at translated positive coordinates. If confirmed, add viewport padding/origin translation and center even at unchanged zoom. Rename Auto to Fit to contents. Decide initial zoom and multiplicative steps as separate design choices.

**Acceptance:** Fit keeps all labels within padded viewport and centers near-origin/translated circuits at unchanged and changed factors. Panning, hit testing and serialized coordinates remain correct; empty circuit has a defined harmless result.

**Evidence/source:** [74-mux-fit.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/canvas/74-mux-fit.png); [ZoomControl.java:420](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/generic/ZoomControl.java:420); [BasicZoomModel.java:114](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/generic/BasicZoomModel.java:114); [Canvas.java:213](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/main/Canvas.java:213).

### P2 — Make Welcome own a consistent project lifecycle

**Candidates:** shell-09, shell-10, canvas-25.

Welcome sits over an existing Untitled project; callbacks use different ownership policies. Default library loading is recorded as an action. Startup constructs a project before Welcome, and New always allocates another frame; bootstrap work uses user-action history.

**Reproduce:** Start without a file using fresh isolated preferences; choose New or a recent file.

**Plan:** Either make Welcome a projectless view or reuse its known blank project consistently. Do not place bootstrap library loading on the user undo stack. Circuit selection must enter an editor.

**Acceptance:** Fresh New produces one intended editable project/window; open/recent/new follow the documented policy. The pristine project has no user-visible undo action and does not produce spurious recovery work.

**Evidence/source:** [01-fresh.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/editing-evidence/01-fresh.png); [Frame.java:298](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/main/Frame.java:298); [Startup.java:1125](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/start/Startup.java:1125).

### P2 — Wire the status fields that are actually intended

**Candidates:** shell-18.

Coordinates/messages have no callers; tick/simulation setters do exist. Partially integrated StatusBar API, not proof all simulator status is broken.

**Reproduce:** Move around a circuit and inspect status source callers.

**Plan:** Connect pointer coordinates and actionable editor messages if they are part of the design; otherwise remove unused API. Preserve and test existing simulator paths.

**Acceptance:** Coordinates update in circuit units and clear when appropriate; messages reflect current operation. Auto-tick/propagation state transitions are independently exercised without duplicating stale information.

**Evidence/source:** [21-divider-after.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/editing-evidence/21-divider-after.png); [StatusBar.java:146](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/StatusBar.java:146); [Frame.java:803](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/main/Frame.java:803).

### P2 — Confine hotkey UI updates to the EDT

**Candidates:** shell-31.

Recorded ConcurrentModificationException; live HEAD source permits the same cross-thread mutation pattern. Unfiltered preference callback invokes hotkeySync over a mutable menu registry outside Swing EDT.

**Reproduce:** Recovered log from Preferences/New/close sequence; no new stress reproduction.

**Plan:** Filter relevant preference keys, marshal UI updates to EDT, and manage menu/listener registration lifetime safely. Avoid a global flush/update for unrelated layout writes.

**Acceptance:** Stress preferences changes while creating/closing scratch windows; no CME, stale menu callbacks or off-EDT Swing access. Closing preference windows releases listeners.

**Evidence/source:** [log-cme-hotkeysync.txt](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/shell/log-cme-hotkeysync.txt); [HotkeyOptions.java:194](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/prefs/HotkeyOptions.java:194); [AppPreferences.java:1704](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/prefs/AppPreferences.java:1704).

## Corrections to the original claims

- File Close's absent question mark comes from its hard-coded message string. It is not, by itself, proof that the dialog clipped punctuation.
- The safe-close plan cannot blindly reuse Frame.confirmClose: its Save branch currently stops autosave even when doSave returns false.
- The live multi-edit Undo sequence was Yes/Yes → Yes/No → Yes/No → No/No. The original report placed the no-op undo at a different position; three actions and one redundant action were reproduced.
- Mixed-selection attributes become null on disagreement; the source does not simply supply the first component's value. Editor defaults and silent label numbering remain concerns.
- Zoom-to-Fit already calls a centering path. Origin constraints or the unchanged-zoom shortcut require a focused follow-up; the archived asymmetry is real, but the proposed original cause is wrong.
- StatusBar has simulation/tick setters and Frame callers. Missing coordinates/messages does not prove simulation status is never connected.
- Fresh CIRCUITS header was readable in this run. Width truncation depends on layout/preferences; inspector clipping was directly reproduced.
- Transparent schematic symbols, an undefined pin value U, a separate canvas zoom, dirty-dot placement and alternative shortcut conventions are not automatically bugs. They remain design proposals.
- Old recovery-dialog frequency is confounded by parallel rigs sharing real user.home. This review never cleared those files.
- Catch-all inspector-31 is not one independent bug: its autosave/style symptoms are duplicates, and preserving splitter bit mappings after changing fan-out is not demonstrated incorrect.

## Harness plan and handoff

In a scratch harness set user.home=$D/home plus private preference/config roots; validate effective JVM properties. Capture actual display/window geometry, handle heavyweight popups explicitly, keep the launch terminal alive and record jar hash/build provenance. Never clear real autosaves.

**Acceptance:** Creating/closing/recovering disposable projects touches only the session tree. Nondefault-size captures include correct bounds. Session stop validates PIDs and leaves every other compositor/app untouched.

The report preserves **all 106 assigned IDs**: 6 runtime-confirmed, 13 visual-confirmed, 16 source-confirmed, 19 duplicate, 29 design recommendations and 23 unverified. These are disposition totals, **not independent bug totals**. Compound claims are narrowed explicitly in the JSON. Unverified IDs carry no accepted severity.

Unverified candidates: shell-15, shell-23, shell-24, shell-25, shell-27, shell-28, shell-32, shell-34, shell-35, shell-36, inspector-22, inspector-23, inspector-24, canvas-03, canvas-13, canvas-14, canvas-15, canvas-16, canvas-17, canvas-22, canvas-24, canvas-28, canvas-31. This bounded review did not retest light theme, other scale factors, live overflow/Appearance, long-list scrolling, dialog X, save I/O failure, recovery, race stress, tooltip bounds or a broad workflow tour.

Session `cx-editing` was stopped after checking the exact app/wrapper/session PIDs and private environment. A subsequent process check showed all three gone; the terminal session was closed. Only this review's session was stopped. Production worktree files were not edited. The pre-existing untracked AGENTS.md and concurrently appearing docs/qa were left untouched.

Detailed per-ID dispositions, exact evidence paths, tested/not-tested lists and acceptance checks are in [editing.json](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/reviewers/editing.json).


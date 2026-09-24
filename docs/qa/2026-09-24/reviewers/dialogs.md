# Dialogs / Analyzer / Light QA review

Reviewed checkout **0a62266649937f3c2cbcb1ef495b6d9be6ba2d5a**. Bounded independent review of **inspect-analyzer (29), inspect-dialogs (27), inspect-light (30)** only. No production changes, commits, pushes or child agents.

All **86 inherited IDs** have one disposition in [dialogs.json](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/reviewers/dialogs.json): **4 confirmed-runtime, 18 confirmed-visual, 10 confirmed-source, 18 duplicates, 20 design recommendations, 16 unverified**. These are claim dispositions, not independent bug counts. The twelve workstreams below group proposed work rather than inflate symptom counts.

## Main result

**No P0 established.** Build Circuit has an unsafe keyboard default but replacement was recoverable with Undo. P1 is reserved for unreadable palette captions and the source-confirmed absence of a normal cancellation path during a long optimization. Reversible interactions and resize/scroll workarounds are P2; optional visual/editorial work is P3.

**Blank dropdown rows were not reproduced.** Theme and font lists, plus all 20 Analyzer bit-width rows, remained readable at 1.6x, including after theme changes. This does not clear every custom renderer. Enclosing form clipping is separately confirmed.

The highest-value live evidence is [08-build-after-Q.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/dialogs-evidence/08-build-after-Q.png), [09-confirm-after-space.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/dialogs-evidence/09-confirm-after-space.png), [10-replaced-edit-menu.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/dialogs-evidence/10-replaced-edit-menu.png), [11-undo-restored.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/dialogs-evidence/11-undo-restored.png), [13-window-dark.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/dialogs-evidence/13-window-dark.png), [15-window-light.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/dialogs-evidence/15-window-light.png), [17-window-dark-again.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/dialogs-evidence/17-window-dark-again.png), and [22-bitwidth-popup.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/dialogs-evidence/22-bitwidth-popup.png).

## Provenance and limits

Real recovered jar in private KWin/Xwayland, 2560×1600, fresh preferences, application scale 1.6, session `cx-dialogs`. Only a disposable copy of the recovered PC fixture was edited; Undo restored it, and no Save occurred.

- Jar: `/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/logisim.jar`.
- SHA-256: `c854edbbea34d6cfc0e53ac9cf70ef5b2a5dcf550a3959ff0ed99d852950d433`.
- BuildInfo: `main/2532c84d, 2026-09-24T16:07:16+0200`.
- HEAD differs from BuildInfo. The artifact includes the Colors page present at HEAD, so stale metadata alone does not prove all classes are older. Runtime conclusions apply to this binary; source checks apply to HEAD. Clean build/source equivalence was not established.
- Inherited screenshots under `/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/` were personally inspected where cited as visual proof. New images are under `/tmp/logisim-qa-recovery-20260924/dialogs-evidence/`. `view_image` failed with mountinfo; actual PNG bytes were rendered with the local Node image reader and functions image helper.
- Corrected partial verifiers are interrupted tool histories, not completed verdicts. They were used for selected context, not accepted as proof.

## Root-cause workstreams and acceptance
### D1. Build Circuit: safe destination and keyboard focus — P2

BuildCircuitButton selects the analyzed project/name, shows an OK-default generic confirm, then a Yes-default replacement confirm. name.selectAll() does not transfer focus. The replacement is an undoable project action.

Reproduction/evidence: Analyze FULLADDER in the copied PC fixture, click Build Circuit, type Q, press Space, then Enter. Observe changed gate layout; main Edit > Undo Replace Circuit restores it. [08-build-after-Q.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/dialogs-evidence/08-build-after-Q.png), [09-confirm-after-space.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/dialogs-evidence/09-confirm-after-space.png), [10-replaced-edit-menu.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/dialogs-evidence/10-replaced-edit-menu.png), [11-undo-restored.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/dialogs-evidence/11-undo-restored.png).

Source: [BuildCircuitButton.java:69](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/analyze/gui/BuildCircuitButton.java:69), [BuildCircuitButton.java:142](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/analyze/gui/BuildCircuitButton.java:142), [BuildCircuitButton.java:193](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/analyze/gui/BuildCircuitButton.java:193), [BuildCircuitButton.java:259](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/analyze/gui/BuildCircuitButton.java:259)

Plan: Propose a unique <source>_built name, focus/select its field on show, label the primary action Build, and use explicit Replace/Cancel with Cancel the collision default. Keep replacement undoable and disclose that.

Acceptance: Keyboard-only opening and typing changes the name; Space while editing cannot submit; Enter on an untouched collision prompt cancels. Explicit replacement works and Undo restores topology, pin attributes and wiring. No disk write before Save.

Limit: P2 under requested rubric: unsafe interaction reproduced but recoverable; no demonstrated irreversible loss or saved-file overwrite.

### D2. Analyzer window and action-row geometry — P2

Packed initial geometry leaves little content height, while a nonwrapping six-button BoxLayout is wider than the permitted minimum. Fixed wait-dialog dimensions and locally assigned metrics compound scaling problems.

Reproduction/evidence: Open Analyzer at 1.6x: 1473x492. Select Simplified: expression lies below viewport. Recovered 720px window omits Build and exports. [05-analysis-main.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/dialogs-evidence/05-analysis-main.png), [23-kmap.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/dialogs-evidence/23-kmap.png), [80-analyzer-narrow.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/analyzer/80-analyzer-narrow.png).

Source: [Analyzer.java:192](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/analyze/gui/Analyzer.java:192), [Analyzer.java:198](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/analyze/gui/Analyzer.java:198), [Analyzer.java:321](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/analyze/gui/Analyzer.java:321)

Plan: Choose a work-area-bounded initial content size, retain user size, and wrap or overflow secondary actions while keeping Build reachable. Size wait dialogs from contents. Give table controls padding and descriptive tooltips; make optional table/map zoom explicit.

Acceptance: At 1.0/1.6/2.0x and minimum/default/maximized widths, every primary action remains reachable, map and expression can be accessed, and no half-row is the only representation. Verify wait-dialog rendering with a controlled slow fixture.

Limit: Narrow-window evidence is inherited but personally inspected. User can currently resize/scroll; not P1. Map expansion, zebra rows and hex display remain optional design work.

### D3. One scale policy across startup and theme changes — P2

Startup rescales selected components on COMPONENT_ADDED. Theme.apply reinstalls UI delegates through FlatLaf.updateUI and updateComponentTreeUI; regenerated title fonts lose application scaling. Font-only adjustment leaves widget metrics inconsistent.

Reproduction/evidence: Fresh 1.6x dark session, Preferences > Window > Light > Dark. Compare title text before and after while content labels remain large. [13-window-dark.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/dialogs-evidence/13-window-dark.png), [15-window-light.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/dialogs-evidence/15-window-light.png), [17-window-dark-again.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/dialogs-evidence/17-window-dark-again.png), [08-build-after-Q.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/dialogs-evidence/08-build-after-Q.png).

Source: [Startup.java:1161](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/start/Startup.java:1161), [Theme.java:104](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/theme/Theme.java:104)

Plan: Establish one supported scaling path for fonts, UI defaults, icons, insets, renderer measurements and title panes. Remove or adapt the container-add workaround only after a small representative prototype proves no double scaling. Reapply theme-dependent custom colors during UI updates.

Acceptance: Repeat Light/Dark/System transitions with Preferences, Analyzer and a main window open at 1.0/1.6/2.0x. Same label retains font size/bounds, controls retain usable hit areas, and nothing double-scales. Test newly opened and already-open windows.

Limit: Title shrink verified live; stale hotkey background and every claimed widget class not rechecked.

### D4. Fixed shell metrics obscure component identification — P1

ComponentTile fixes dimensions at 68x62 but paints scaled text beneath a fixed preview area. LayoutPrefs uses device-pixel defaults. SizedSplit reapplies stored dimensions during every layout and records on release, supporting divider snapback.

Reproduction/evidence: At 1.6x light, open the library; recovered captions are cut vertically. Independently inspect default panel values and divider layout path. [07z-palette-top.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/light/07z-palette-top.png).

Source: [ComponentTile.java:45](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/palette/ComponentTile.java:45), [ComponentTile.java:188](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/palette/ComponentTile.java:188), [LayoutPrefs.java:40](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/LayoutPrefs.java:40), [ShellLayout.java:166](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/ShellLayout.java:166), [ShellLayout.java:201](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/ShellLayout.java:201)

Plan: Derive tile height and caption bounds from final metrics; allow readable wrapping or a labeled list mode. Scale default/minimum panel dimensions once and migrate stored dimensions carefully. Track drag state so layout does not restore stale positions while dragging.

Acceptance: Identify a gate, TTL part and user subcircuit by visible name at each supported scale without hovering. Drag all three dividers and verify the new position persists across relayout/reopen. Render timing content after resizing.

Limit: P1 applies to unreadable component captions; divider and default-size concerns are P2/source-confirmed, not independently live-retested.

### D5. Expression and K-map rendering must communicate the function — P2

ExpressionRenderData scales notSep twice and measures spans separately from the final attributed drawing. K-map covers alpha-composite light fills under light text. Horizontal metric mismatch is a candidate cause, not fully isolated.

Reproduction/evidence: Analyze FULLADDER > Simplified at 1.6x; inspect mathematical overbars and digits inside tan/olive cover groups. [35-overline-z5.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/analyzer/35-overline-z5.png), [34-kmap-z4.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/analyzer/34-kmap-z4.png), [23-kmap.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/dialogs-evidence/23-kmap.png).

Source: [ExpressionRenderData.java:66](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/analyze/data/ExpressionRenderData.java:66), [ExpressionRenderData.java:214](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/analyze/data/ExpressionRenderData.java:214), [ExpressionRenderData.java:345](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/analyze/data/ExpressionRenderData.java:345), [ExpressionRenderData.java:368](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/analyze/data/ExpressionRenderData.java:368), [KarnaughMapGroups.java:134](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/analyze/data/KarnaughMapGroups.java:134)

Plan: Use one TextLayout/attributed geometry for both glyphs and negation spans, with one scaling conversion for gap/stroke. Select cover/text pairs based on final composited contrast; keep grouping intelligible without color alone.

Acceptance: Render nested NOT, SOP/POS, XOR and subscripts at 1.0/1.6/2.0x in both themes. Each bar covers exactly its operand. Measure at least 4.5:1 for normal text on every cover/overlap and visually inspect real captures.

Limit: Original numerical contrast ratios not independently measured; functional truth-table computation was not challenged.

### D6. Analyzer editing history and optimization lifecycle — P1

Map clicks directly mutate TruthTable; fill-down selection is intentional; analysis lacks an evident edit-history path. Expression visibility is gated on input count. Optimizer hides its only completion button during work, refuses close and updates Swing from raw threads.

Reproduction/evidence: Source-confirmed: start a sufficiently expensive >6-input optimization to expose uncancellable progress. Click a K-map cell or table output region to exercise direct editing/fill-down. Long run not performed here. [05-analysis-main.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/dialogs-evidence/05-analysis-main.png), [20-signals.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/dialogs-evidence/20-signals.png), [22-bitwidth-popup.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/dialogs-evidence/22-bitwidth-popup.png).

Source: [KarnaughMapPanel.java:1039](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/analyze/gui/KarnaughMapPanel.java:1039), [TableTabCaret.java:304](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/analyze/gui/TableTabCaret.java:304), [Analyzer.java:114](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/analyze/gui/Analyzer.java:114), [MinimizeButton.java:41](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/analyze/gui/MinimizeButton.java:41)

Plan: Add transactional Analyzer undo/redo and an explicit fill-down affordance. Label the source project/circuit and snapshot state. Run optimization in a cancellable worker with cooperative cancellation, EDT-only presentation, structured progress, and an accessible result view independent of automatic-minimization limits.

Acceptance: Cancel a deliberately bounded slow optimization promptly without losing prior results. Undo/redo table, variable, expression and map edits. Manually optimize an 8-input table and inspect/export the result. Popup rows remain readable after theme transitions.

Limit: P1 applies to inability to exit long optimization normally, confirmed in source; no hours-long run or proven scratch-data loss. Other usability concerns P2. No blank bit-width rows reproduced.

### D7. Preferences forms, scrolling and settings discovery — P2

Plain JScrollPane views honor large text-field preferred widths, producing horizontal overflow; unit increments are not configured. Hotkeys use fixed nested panes. SettingsNav matches only page titles and retains the old page on no results.

Reproduction/evidence: At 1.6x inspect Template/Software and Hotkey pages. Search 'theme': source match function excludes Window. Compare separate swatch grids. [03-prefs-template.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/dialogs/03-prefs-template.png), [19-software.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/dialogs-evidence/19-software.png), [12-prefs-hotkeys.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/dialogs/12-prefs-hotkeys.png).

Source: [PreferencesFrame.java:79](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/prefs/PreferencesFrame.java:79), [HotkeyOptions.java:82](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/prefs/HotkeyOptions.java:82), [JHotkeyInput.java:113](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/util/JHotkeyInput.java:113), [SettingsNav.java:116](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/SettingsNav.java:116), [ColorOptions.java:101](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/prefs/ColorOptions.java:101)

Plan: Create viewport-tracking forms with flexible path fields, visible browse controls and a scaled scroll increment. Use one main scroll surface for key bindings. Index setting labels/keywords and show an explicit no-result state. Align swatch columns and distinguish UI scale from canvas zoom.

Acceptance: At 1.0/1.6/2.0x, no required form action needs horizontal scrolling. Wheel movement advances a useful row. Queries theme/font/zoom reach Window; nonsense query shows no results and clear recovery. Long localized labels wrap without truncating controls.

Limit: Overflow visual confirmed; exact wheel distance and filter keyboard handoff were not live-tested. Page hierarchy and swatch enhancements are design choices.

### D8. Tree measurement and active-selection contrast — P2

ProjectExplorer sets scaled renderer fonts after tree sizing, and active-circuit foreground overrides selected foreground. Renderer and layout must share metrics. Toolbar actions and duplicate Pin labels add discoverability problems.

Reproduction/evidence: Open Project Options > Toolbar at 1.6x; inspect recovered truncated library rows. Select the active circuit as a follow-up acceptance case. [22-projopts-toolbar.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/dialogs/22-projopts-toolbar.png).

Source: [ProjectExplorer.java:289](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/generic/ProjectExplorer.java:289), [ProjectExplorer.java:321](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/generic/ProjectExplorer.java:321)

Plan: Set tree font/row/icon metrics before size caching and invalidate measurements after scale/theme changes. Preserve contrasting selection foreground, using a separate current-circuit marker. Add distinguishable Input/Output pin labels and accessible action names.

Acceptance: Collapsed and newly expanded nodes have the same readable layout; descenders fit; selected active circuit name remains legible in both themes. Keyboard navigation reaches all editing actions.

Limit: Tree clipping visually confirmed; active selected invisibility is source-supported but not live clicked. Mouse repaint debris unverified.

### D9. Secondary dialog layout and contrast — P2

Independent local defects: CSV centers before pack and omits title/padding; Help CSS hardcodes navy headings; statistics uses compact unscaled caps/mixed renderers; recovered color chooser thumb disagrees with numeric brightness. Chooser layout also reflects shared scaling problems.

Reproduction/evidence: Inspect recovered CSV, Help, file chooser, Statistics and Color chooser captures. Source-check CSV positioning, Help colors and Statistics caps. [55-csv-dialog.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/analyzer/55-csv-dialog.png), [28-help-guide.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/dialogs/28-help-guide.png), [61-lookin-popup-z.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/dialogs/61-lookin-popup-z.png), [35-statistics.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/dialogs/35-statistics.png), [08-colorchooser.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/dialogs/08-colorchooser.png).

Source: [CsvReadParameterDialog.java:52](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/analyze/gui/CsvReadParameterDialog.java:52), [style.css:89](/home/nilsb/Documents/projects/logisim-revolution/src/main/resources/doc/en/html/style.css:89), [StatisticsDialog.java:59](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/main/StatisticsDialog.java:59)

Plan: Give CSV import a titled padded form, scrollable data preview, documented input/output split and explicit Cancel; pack before centering. Supply theme-aware Help CSS. Size Statistics columns from final fonts. Trace color-picker thumb initialization after layout before selecting a library patch/replacement. Apply shared chooser sizing improvements.

Acceptance: CSV import can be cancelled with button/Escape without model mutation and supported exported CSV round-trips. Help headings/links meet contrast targets. All statistics headers/totals fit or expose full accessible text. Opening #FF00B4 shows the brightness thumb at 100% before interaction.

Limit: Legacy aesthetics alone are not bugs. CSV includes header in its four previewed rows, so 'only three data rows' is not inherently wrong. Missing Cancel button is not missing Escape cancellation. Screen positions cannot be inferred from cropped windows.

### D10. Color tokens must cover component subpanels and presets — P2

MemState fills LIGHT_GRAY then draws theme-aware light foreground. Colorblind preset writes a near-black bus color independent of theme and immediately overwrites palette choices. Muted light captions need composited contrast measurement.

Reproduction/evidence: Inspect recovered dark ROM values; source-check MemState and colorblind preset. Review muted light palette captions. [73z-rom.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/light/73z-rom.png), [07z-palette-top.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/light/07z-palette-top.png).

Source: [MemState.java:227](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/std/memory/MemState.java:227), [SimOptions.java:142](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/prefs/SimOptions.java:142), [ColorOptions.java:169](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/prefs/ColorOptions.java:169)

Plan: Define paired semantic fill/text/highlight colors for memory data regions and use theme-aware preset variants. Preview/revert preset application or give an explicit confirmation consistent with other resets. Measure muted text before adjusting tokens.

Acceptance: Read ROM/RAM bytes in both themes before/after transitions; maintain contrast across selection/highlight states. Apply and revert the accessibility preset without losing a saved custom scheme. Verify visibility rather than equating low saturation with accessibility.

Limit: No visual proof here for all black text annotations, pin radix variants or every preset color.

### D11. Hotkey Escape should cancel capture — P2

JHotkeyInput.keyPressed validates Escape as a candidate key instead of handling edit cancellation first, and creates null-parent validation dialogs. Persistent blank display is inherited, not independently reproduced.

Reproduction/evidence: Follow-up: Preferences > Hotkeys > edit Auto-Propagate > Escape, with and without a prior conflict.

Source: [JHotkeyInput.java:289](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/util/JHotkeyInput.java:289), [JHotkeyInput.java:314](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/util/JHotkeyInput.java:314)

Plan: Handle and consume Escape before modifier validation, restore prior displayed value and focus, and parent validation feedback to the editor. Keep temporary capture text separate from committed bindings.

Acceptance: Escape cancels capture once without closing Preferences or changing the stored binding; conflicts preserve the old shortcut, and reopening shows it correctly.

Limit: Source-confirmed escape-validation problem; not proven persisted binding loss.

### D12. Copy, workflow affordances and optional visual modernization — P3

Mixed terminology and generic dialog/menu reuse reduce clarity. Recovered Find Action 'zoom' output visibly misses relevant commands, but provider cause is unaudited. Independent canvas zoom and dev build metadata can be intentional.

Reproduction/evidence: Inspect the live table-fallback notice, menu/title chrome and recovered 'zoom' action search. [04-analyzer.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/dialogs-evidence/04-analyzer.png), [10-replaced-edit-menu.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/dialogs-evidence/10-replaced-edit-menu.png), [87z-findaction.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/light/87z-findaction.png).

Source: No fully isolated source cause asserted; inspect the relevant provider/dialog implementation before coding.

Plan: Create a concise UI glossary, use action verbs and contextual menus, add guidance for empty panels, and audit search providers/ranking before changing them. Keep physical canvas zoom independent unless product requirements explicitly change it. Modernize artwork and print dialogs after functional/legibility fixes.

Acceptance: Common search terms reach their actions before irrelevant subsequences; control names match menus/tooltips; empty content explains next steps. Preserve keyboard focus visibility and semantic schematic glyphs. Verify release and dev-title requirements separately.

Limit: This is mostly recommendation work, not a pile of independently confirmed defects. Do not spend redesign effort ahead of caption clipping or inaccessible optimization cancellation.
## Remaining verification and corrections

Sixteen IDs remain unverified: analyzer-17, analyzer-20, analyzer-24, dialogs-11, dialogs-13, dialogs-16, dialogs-17, dialogs-21, dialogs-22, light-04, light-13, light-14, light-17, light-19, light-24, light-27. Exact reasons and qualified clauses are in JSON. Do not implement speculative list-synchronization, zoom-fit, hex-editing, About, FPGA or print/export fixes without the responsible owner's reproduction.

Corrections to inherited assertions:

- Build replacement is undoable; no saved-file or irreversible loss was established.
- Controls outside a scrollable form are difficult to reach, not categorically unreachable.
- Analyzer's action strip is BoxLayout at HEAD, not FlowLayout.
- CSV already installs Escape-to-close; four preview rows including the header is not inherently wrong.
- The wait dialog explicitly centers relative to its parent component after packing. Its fixed size is real, but runtime clipping was not observed.
- Fill-down selection is intentional in source. Independent canvas zoom, visible focus rings and conditional development-build metadata are not automatic defects.
- Exact original contrast ratios, wheel distances and blanket “every widget” statements were not independently remeasured.

Not tested: clean rebuild; other scales/OS/locales; hours-long optimizer; physical printing; CSV/LaTeX round-trips; saved overwrite; persisted hotkey loss; all custom dropdown renderers; every screenshot or compound clause.

## Harness correction and cleanup

The original qa.sh isolates Java preferences but not user.home. It exposed an existing real-home unnamed-autosave prompt before fixture loading; I took no action on that prompt. Evidence: [02-autosave.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/dialogs-evidence/02-autosave.png). Loader.java:175 derives unnamed autosave paths from user.home.

The scratch-only [dialogs-qa.sh](/tmp/logisim-qa-recovery-20260924/dialogs-qa.sh) creates a session home and supplies `-Duser.home=$D/home` before workflow mutations. Parent independently identified the same harness defect; count it once globally (dialogs-new-01).

Harness fix plan: enforce both private home and preferences, copy named fixtures, verify PID ownership before termination, correct shot's hardcoded 2560×1600 dimensions, and record artifact identity/hash with every run. The first launcher exited with its tool process; a held terminal kept the final owned session alive.

After Undo, source and disposable fixture SHA-256 matched:
`6eb2351cd56ddb6402ddb191f7ff000e15e0ed89131225bd3c511f3f92295dcf`.
The app log was empty. Owned app 557259, wrapper 557194, compositor 557197 and session 557246 were validated, stopped, and absent in the final ps check. Terminal keepalives exited. No other sessions were stopped. Initial and immediate post-cleanup repository status showed only pre-existing untracked AGENTS.md; final validation also showed untracked docs/qa/, which this reviewer did not create or modify. This reviewer wrote only /tmp artifacts.

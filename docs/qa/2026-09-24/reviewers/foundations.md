# Foundations visual QA — 2026-09-24

The palette's normal identification workflow fails at interface scale 1.6 and 2.0. Native live crops show captions cut to glyph tops at 1.6 and absent at 2.0; the tree fallback overlaps at 2.0. **P1**, with no P0 data loss demonstrated. Fixing one shared tile/row/scale contract addresses many repeated symptoms.

Reviewed the three assigned inspection areas plus completed code-audit verification. The JSON contains dispositions for **all 105 unique assigned IDs**: 22 palette, 32 scaling, 36 original code-audit and 15 codeaudit-v-* additions. The verifier's 36 repeated verdicts do not add 36 bugs. Statuses: confirmed-visual: 11; confirmed-source: 26; confirmed-runtime: 6; design-recommendation: 20; duplicate: 27; unverified: 14; rejected: 1. The nine groups below are planning workstreams, not a claim of nine independent defects or 105 confirmed bugs.

## Evidence and limits

- Source HEAD: `0a62266649937f3c2cbcb1ef495b6d9be6ba2d5a`; repository unchanged, with only the preexisting untracked AGENTS.md.
- Real supplied jar: `/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/logisim.jar`. SHA-256: `c854edbbea34d6cfc0e53ac9cf70ef5b2a5dcf550a3959ff0ed99d852950d433`.
- Its window embeds `main/2532c84d` / `2026-09-24T16:07:16+0200`. Bytecode contains HEAD's immediate PrefMonitorDouble cache/writeback fix, and reviewed tile/font/scale sources are unchanged across that revision gap. Full build equivalence is not established.
- Session `cx-foundations`: fresh preferences, private Java home `/tmp/logisim-qa-recovery-20260924/foundations-home` on **every** launch, 2560x1600 private KWin/Xwayland, dark theme. Only a harness copy of scratch sample.circ was opened.
- Live screenshots are in [foundations-evidence](/tmp/logisim-qa-recovery-20260924/foundations-evidence). Recovered screenshots below were actually viewed; their original paths remain intact. `view_image` failed with the mountinfo sandbox error, so the authorized scoped base64-to-image fallback was used.
- This is not proof of exact physical KDE, actual desktop DPI, GPU rendering, mixed-monitor behavior, Windows or macOS acceptance.
- The first settings query capture `11-settings-filter-scale.png` is invalid (xdotool typed trailing arguments); it is superseded by `17-settings-scale-corrected.png`. The 35-Tab capture still shows visible focus and does **not** prove an offscreen-focus failure.

## Root-cause groups and fix plan

### 1. Palette labels and tree rows do not fit scaled content — P1

1.6 captions show only glyph tops; at 2.0 captions vanish. Tree fallback overlaps at 2.0. At 1.0 Floating... and Controlle... remove distinguishing words. This is not one defect per tile.

**Reproduce:** Start cx-foundations with private home/prefs at 1.6, open Library; repeat at 2.0 and switch to tree. For 1.0 naming compare the recovered Arithmetic crop.

**Fix:** Measure tile preview and caption lines from effective icon size and FontMetrics; scale padding once and anchor star badges within bounds. Allow two lines or a width-adaptive row mode. Add localized short names such as FP Add / FP Multiply while retaining full tooltip and accessible name; distinguish PLA from PLA ROM without changing serialized factory IDs. Set tree row height to max(font line height, actual icon height)+padding. Reset reused renderer font from tree.getFont(), explicitly plain/bold per row.

**Acceptance:** At app zoom 1.0/1.25/1.6/2.0/2.5, dark and light, every caption baseline+descent stays within its tile and labels distinguish integer/FP variants. Tree icons and descenders do not overlap; switching current circuit does not make following rows bold. Test default and narrow panel widths plus a long localized name; inspect native crops.

**Screenshots:** [02-palette-1.6-crop.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/foundations-evidence/02-palette-1.6-crop.png), [08-palette-2.0-crop.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/foundations-evidence/08-palette-2.0-crop.png), [05-tree-1.6-crop.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/foundations-evidence/05-tree-1.6-crop.png), [07-scale2-start-crop.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/foundations-evidence/07-scale2-start-crop.png), [42z-scale1-float-2x.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/palette/42z-scale1-float-2x.png)

**Source:** [ComponentTile.java:45](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/palette/ComponentTile.java:45), [ComponentTile.java:184](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/palette/ComponentTile.java:184), [ProjectExplorer.java:288](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/generic/ProjectExplorer.java:288), [ProjectExplorer.java:521](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/generic/ProjectExplorer.java:521), [FlatLaf.properties:38](/home/nilsb/Documents/projects/logisim-revolution/src/main/resources/com/cburch/logisim/gui/theme/FlatLaf.properties:38).

Canonical IDs: palette-01, palette-02, palette-03, codeaudit-v-02.

### 2. One coordinated UI scale and font contract is missing — P1

Accelerators and native checkbox glyphs stay small beside enlarged labels. After live scale change captions shrink, menu and panel headers remain large, and preview icons retain their old raster size. Recovered Statistics has a large first header cell and small remaining cells.

**Reproduce:** At 1.6 open File menu; at 2.0 open Preferences > Window. Change slider from 2.0 to 1.0 and close Preferences. Compare chrome, captions and cached preview symbols; inspect recovered Statistics header.

**Fix:** Prototype FlatLaf 3.7.2 UIScale.setZoomFactor(float), getZoomFactor(), setSupportedZoomFactors(float[]) and PROP_ZOOM_FACTOR as the application zoom authority; local javap confirms these public APIs. Preserve Java2D device transform separately. Measure LaF base/user scaling before mapping stored explicit Scale values; preserve explicit settings and support required fractional values, checking setZoomFactor's boolean result. Remove the global COMPONENT_ADDED font rewrite and app multiplication layered on already-scaled LaF fonts/SVGs. Give custom widgets one logical-to-UI conversion and update geometry/fonts/caches together on the EDT. Key preview rasters by effective scale, device transform and theme or paint vector previews directly. Use role fonts, including explicit TableHeader/editor/gutter roles. Keep circuit document fonts/coordinates and canvas zoom independent.

**Acceptance:** Existing explicit Scale=1.6 survives migration/restart with intended effective size. Auto mode and explicit mode remain distinct. Native menu labels/accelerators, checkbox targets, row metrics and custom icons scale together at 1.0/1.25/1.6/2.0/2.5. A live 2->1->1.6 change matches a fresh launch at each setting; renderer order/repaint does not change font size. Repeat on actual KDE/Wayland-Xwayland, Windows scaling and a mixed-DPI monitor move before declaring cross-platform acceptance.

**Screenshots:** [06-menu-1.6-crop.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/foundations-evidence/06-menu-1.6-crop.png), [12-window-settings-2.0.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/foundations-evidence/12-window-settings-2.0.png), [14-live-scale1-main-crop.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/foundations-evidence/14-live-scale1-main-crop.png), [50-s1.6-statistics.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/scaling/50-s1.6-statistics.png)

**Source:** [Startup.java:1161](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/start/Startup.java:1161), [AppPreferences.java:446](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/prefs/AppPreferences.java:446), [AppPreferences.java:1003](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/prefs/AppPreferences.java:1003), [UiFonts.java:89](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/util/UiFonts.java:89), [AppIcons.java:156](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/theme/AppIcons.java:156), [WindowOptions.java:275](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/prefs/WindowOptions.java:275), [ToolPreviewIcon.java:44](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/palette/ToolPreviewIcon.java:44).

Canonical IDs: scaling-01, scaling-06, scaling-08, scaling-22, codeaudit-03, codeaudit-25, codeaudit-v-11.

### 3. Raw panel persistence and continuous-layout resets prevent a useful sizing workaround — P2

Initial window occupies the left half. Fresh migration stores 320px side width. A tested drag did not widen it; doLayout restores the saved size while release later persists that restored position.

**Reproduce:** Fresh 2560x1600 private session opens 1280x1600. At 2.0 drag the left divider from approximately x410 to x660; compare before and after.

**Fix:** Restore persisted divider size only during explicit initialization/restore, not every layout while dragging. Persist user changes after drag. Store logical panel sizes with versioned conversion of legacy device-pixel values. Migrate old fractions only when legacy settings actually exist; use content-aware defaults for fresh users and usable screen bounds. Let headers reserve a minimum title width and overflow secondary actions.

**Acceptance:** Dragging side/inspector/bottom dividers changes their size and survives repaint, resize, hide/show and restart. Fresh 1366x768 and 2560x1600 starts provide usable canvas/palette proportions; existing customized widths are preserved. Header actions do not erase the title at high zoom.

**Screenshots:** [01-fresh-1.6.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/foundations-evidence/01-fresh-1.6.png), [08-palette-2.0.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/foundations-evidence/08-palette-2.0.png), [09-after-divider-drag-2.0.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/foundations-evidence/09-after-divider-drag-2.0.png)

**Source:** [LayoutPrefs.java:63](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/LayoutPrefs.java:63), [ShellLayout.java:166](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/ShellLayout.java:166), [ShellLayout.java:202](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/ShellLayout.java:202), [PanelHeader.java:37](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/PanelHeader.java:37), [AppPreferences.java:1309](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/prefs/AppPreferences.java:1309).

Canonical IDs: palette-06, scaling-03, scaling-04, scaling-05, scaling-23.

### 4. Search contracts fail common vocabulary and provide no empty-result feedback — P2

Palette mux is blank while Find Action finds Multiplexer. Settings scale empties navigation while retaining the old page. The two PLA factories have the same label. Project components are appended after built-ins by construction.

**Reproduce:** Enter mux in Library; compare recovered Find Action mux results. In Preferences type scale. Inspect ComponentPalette.matches, category order and SettingsNav.matches.

**Fix:** Share component search metadata/ranking between palette and Find Action: exact label first, then aliases (mux, demux, flip-flop, output pin), then other matches. Keep localized labels and stable IDs separate. Show query-aware No components found with a clear action. Index settings labels/keywords and reveal the target page/control; explain no results. Put a clearly named project group before long built-in catalogs. Make search popup sizing content/zoom aware and remove provider counts.

**Acceptance:** mux finds Multiplexer; exact AND ranks before unrelated matches; output pin is discoverable and distinguishes its configuration; PLA and PLA ROM are unambiguous. Nonsense query yields a readable empty state. scale/zoom/theme locate appropriate settings. Enter selects the intended result; clearing preserves focus.

**Screenshots:** [03-search-mux-crop.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/foundations-evidence/03-search-mux-crop.png), [17-settings-scale-corrected.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/foundations-evidence/17-settings-scale-corrected.png), [49b-omni-mux-win.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/palette/49b-omni-mux-win.png)

**Source:** [ComponentPalette.java:162](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/palette/ComponentPalette.java:162), [ComponentPalette.java:190](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/palette/ComponentPalette.java:190), [SettingsNav.java:115](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/SettingsNav.java:115), [std.properties:385](/home/nilsb/Documents/projects/logisim-revolution/src/main/resources/resources/logisim/strings/std/std.properties:385), [std.properties:507](/home/nilsb/Documents/projects/logisim-revolution/src/main/resources/resources/logisim/strings/std/std.properties:507).

Canonical IDs: palette-04, palette-05, palette-11, scaling-12, scaling-20.

### 5. Palette keyboard, recents and Escape behaviors need explicit state handling — P2

Right does not move tile focus; tile listeners support Enter/Space only. choose() persists recents but does not refresh the group. Section fold state is memory-only. Empty filter still swallows Escape. Activity hover/selected fill and tree halo have paint branches that draw nothing.

**Reproduce:** From palette search press Down then Right. Use Tab across tiles. Select a new tool and inspect recents source. In Preferences filter type text and press Escape twice.

**Fix:** Implement a single grid traversal policy with arrows/Home/End, scrollRectToVisible on focus, Enter/Space activation and keyboard context menu. Use focusable actions for section headers/view toggle and visible focus state. Refresh recents immediately without dropping focus/scroll position; document whether pinned items also appear in recents. Persist collapse by stable section key if that is the intended contract. Escape clears a nonempty query, then delegates to the dialog/root action when empty. Paint the intended activity and halo state indicators.

**Acceptance:** Keyboard-only user can find, select, pin and place a component then leave the palette without hundreds of Tab stops. Focus remains visible across all sections. Recents reorder immediately. Empty-filter Escape closes applicable dialogs; nonempty-filter Escape only clears. Selection/focus/hover is visible in both themes. This run did not establish offscreen-focus behavior through its 35-Tab screenshot.

**Screenshots:** [15-keyboard-right.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/foundations-evidence/15-keyboard-right.png), [18-after-double-escape.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/foundations-evidence/18-after-double-escape.png)

**Source:** [ComponentTile.java:98](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/palette/ComponentTile.java:98), [ComponentPalette.java:272](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/palette/ComponentPalette.java:272), [SectionPanel.java:61](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/SectionPanel.java:61), [FilterField.java:72](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/FilterField.java:72), [ActivityBar.java:151](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/ActivityBar.java:151), [ProjectExplorer.java:537](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/generic/ProjectExplorer.java:537).

Canonical IDs: palette-07, palette-09, palette-13, scaling-29, codeaudit-18, codeaudit-19, codeaudit-v-12.

### 6. Text and symbol visibility need semantic theme colors and usable names — P2

White-backed legacy bitmap glyphs coexist with monochrome icons. Purple credits are nearly unreadable. Reported muted color pairs calculate to 3.976:1 dark and 3.152:1 light. Inspector renderer uses raw GREEN/RED/ORANGE; chrono error fills use static pastels (both source-only here).

**Reproduce:** Inspect live palette icons, recovered About credits and theme/source color declarations.

**Fix:** Choose semantic foreground/status colors against the actual surface and target at least 4.5:1 for normal readable text. Theme About credits. Replace white-backed raster palette assets with scale-aware transparent/vector previews, retaining meaningful signal colors. Use subtle status fills plus readable labels/icons, not color alone. Verify light/dark waveform X/E contrast before changing chrono fills.

**Acceptance:** Native palette names and About credits remain legible in light/dark at 1.0 and 1.6. Required/status cells meet contrast and remain distinguishable without color. All supported preview backgrounds are transparent. Measure real resolved theme colors, not fallback literals; inspect selected/disabled states.

**Screenshots:** [02-palette-1.6-crop.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/foundations-evidence/02-palette-1.6-crop.png), [08-palette-2.0-crop.png](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/evidence/foundations-evidence/08-palette-2.0-crop.png), [28z-s1.25-about-credits.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/scaling/28z-s1.25-about-credits.png)

**Source:** [LogisimDarkLaf.properties:27](/home/nilsb/Documents/projects/logisim-revolution/src/main/resources/com/cburch/logisim/gui/theme/LogisimDarkLaf.properties:27), [LogisimLightLaf.properties:27](/home/nilsb/Documents/projects/logisim-revolution/src/main/resources/com/cburch/logisim/gui/theme/LogisimLightLaf.properties:27), [AboutCredits.java:190](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/start/AboutCredits.java:190), [HdlColorRenderer.java:61](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/fpga/gui/HdlColorRenderer.java:61), [ChronoPanel.java:283](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/chrono/ChronoPanel.java:283).

Canonical IDs: palette-08, codeaudit-16, scaling-14, codeaudit-v-05, codeaudit-v-14.

### 7. Secondary forms need measured row/layout sizing rather than a universal fixed rectangle — P2

Statistics has truncated cells and inconsistent headers. Hotkey instructions and navigation truncate, and scroll panes reveal only a small part of the content. EditorTheme uses Theme.load(input) without a role/base font; other fixed metrics remain source leads.

**Reproduce:** Inspect recovered native Statistics and Hotkey settings captures; review fixed metrics in editor themes, hotkey input and chrono. Editor/chrono runtime not retested.

**Fix:** After the scale contract is fixed, wrap page help text and compute nav width/row height from content. Do not merely hide horizontal scrollbars. Center Statistics over its owner and size columns/rows from metrics within usable screen bounds. Set code/gutter fonts together after theme application. Size hotkey button boxes from icons and content; derive chrono row/header metrics from actual text. Defer secondary panels without rendered evidence to their owners.

**Acceptance:** Full Hotkey instructions are readable without horizontal scrolling; long nav titles remain identifiable. All Statistics headers have equal font role on first and later paints. For editor/chrono follow-up, inspect multiline code gutters, X/E timing rows and editing action buttons at 1.0/1.6/2.0 before accepting.

**Screenshots:** [50-s1.6-statistics.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/scaling/50-s1.6-statistics.png), [12-prefs-hotkeys.png](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/out/dialogs/12-prefs-hotkeys.png)

**Source:** [StatisticsDialog.java:59](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/main/StatisticsDialog.java:59), [PanelHeader.java:35](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/PanelHeader.java:35), [SettingsNav.java:153](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/shell/SettingsNav.java:153), [EditorTheme.java:77](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/generic/EditorTheme.java:77), [ChronoPanel.java:58](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/chrono/ChronoPanel.java:58), [JHotkeyInput.java:151](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/util/JHotkeyInput.java:151).

Canonical IDs: scaling-10, scaling-11, codeaudit-07, codeaudit-08, codeaudit-09.

### 8. Autosave ownership must be isolated in QA and recovery must identify the right session — P2

Original rig isolates java.util.prefs.userRoot but not user.home. Unnamed autosaves are discovered globally within that home; Discard deletes the selected file. No real autosaves were cleared by this review and no P0 data loss was reproduced.

**Reproduce:** Read qa.sh launch arguments and Loader.determineAutosaveName/Startup autosave scan. This run set JAVA_TOOL_OPTIONS=-Duser.home=/tmp/logisim-qa-recovery-20260924/foundations-home on every launch.

**Fix:** Harness: create D/home before launch, pass both private user.home and preference root every time including relaunch, keep process ownership/PID validation, and derive shot dimensions from the display or use win for nondefault sizes. Record Java2D transform, configured zoom, LaF scale, size, theme and jar hash. Application: use session-owned recovery records and an explicit recovery list with source/date instead of ambiguous per-file prompts; never conflate another active instance's recovery with the current project.

**Acceptance:** Two disposable sessions produce and recover only their own unnamed autosaves; stopping either cannot remove the other's records. Harness writes stay under assigned temp directories. Startup with no pending recovery has no dialog. Recovery tests use synthetic files only. Physical KDE and mixed-DPI equivalence require separate measurement.

**Screenshots:** Source-only; no visual reproduction claimed.

**Source:** [qa.sh](/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa/qa.sh), [Loader.java:175](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/file/Loader.java:175), [Startup.java:997](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/start/Startup.java:997).

Canonical IDs: palette-19.

### 9. Two source-supported risks should remain separate from visual acceptance — P2

Theme polling can block EDT while reading a process before checking its timeout. Accent/disabled helpers mutate a shared cached icon; the latter is latent P3.

**Reproduce:** Inspect Theme update timer, SystemTheme.run read order, and cached AppIcons variants. No hang was injected; no accented/disabled call sites currently found.

**Fix:** Probe system theme off EDT with bounded stdout consumption/process timeout, then update UI only on changed result. Cache separate icon variants/copies rather than mutate a base cache entry.

**Acceptance:** A stub probe that stalls cannot block an EDT ping; timed-out processes terminate. Requesting an accented/disabled glyph leaves an existing base glyph unchanged. Do not claim a measured performance problem or user-visible cache regression before those checks.

**Screenshots:** Source-only; no visual reproduction claimed.

**Source:** [Theme.java:182](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/theme/Theme.java:182), [SystemTheme.java:163](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/theme/SystemTheme.java:163), [AppIcons.java:147](/home/nilsb/Documents/projects/logisim-revolution/src/main/java/com/cburch/logisim/gui/theme/AppIcons.java:147).

Canonical IDs: codeaudit-v-09, codeaudit-v-10.

## Corrections to carry into the parent plan

- Do not set P0 because the old report used “blocker.” This review reproduced blocked identification, not data loss.
- Do not add one bug per clipped component, icon, row or secondary report. Keep the per-ID evidence registry while planning shared fixes.
- Platform scale is **already** checked through Java2D's device transform. The remaining height fallback and LaF integration need work; “ignores all OS scale” is false.
- Our fresh migrated panel is 320px and has three columns at 2.0. The 260px/two-column report is a particular saved layout, not universal.
- A new logical sizing policy must preserve explicit Scale preferences, monitor device transform and saved circuit document fonts. Merely setting `flatlaf.uiScale` on top of existing UiFonts/AppIcons multiplication risks double scaling. FlatLaf 3.7.2's public zoom API deserves the bounded prototype described above.
- The global font listener can mutate a shared table-header renderer on its first insertion. Source font literals alone cannot establish runtime font size; some are overridden.
- HdlColorRenderer affects the inspector too; its status colors are conditional. The claim “every component always has the same colored cells” is too broad.
- Canvas “100%” remaining distinct from interface zoom is a product/document contract decision, not automatically a bug.
- English **was selected** in the inspected Preferences run; the no-selection claim does not reproduce. First-click swallowing remains unverified and may be rig focus handling.
- A native file picker, drag-to-place, new branding, menu wording, icon role sizes and pin terminology are design choices, not automatically P1 defects.
- Source-only risks (EDT theme polling, shared icon variants, OS-scaled raster blur) need their specified runtime tests. No screen-reader, physical mixed-DPI or complete FPGA/HDL/SoC sweep was performed.
- The interrupted palette verifier's circular-placement/error-message lead remains provisional; no new independently confirmed finding is added.

## Completion and cleanup

Live coverage: palette/tree at 1.6 and 2.0, mux search, menu typography, one splitter drag, Preferences filtering/Escape, live 2.0→1.0 change and basic keyboard traversal. Reused native evidence: 1.0 FP names, Statistics, About credits, Hotkey form, checkbox crop and Find Action mux. Full tested/notTested lists and every disposition are in [foundations.json](/home/nilsb/Documents/projects/logisim-revolution/docs/qa/2026-09-24/reviewers/foundations.json).

The exact app, wrapper and session PIDs were validated before `qa.sh stop cx-foundations`; a subsequent process check found them and the compositor exited. Current app log contained no exception. Production code, commits, pushes, real user files and real preferences were untouched. Evidence and private temporary state remain available for the parent.


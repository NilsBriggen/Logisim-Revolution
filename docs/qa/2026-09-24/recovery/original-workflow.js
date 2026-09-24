export const meta = {
  name: 'visual-qa-sweep',
  description: 'Real-app visual inspection and QA of every surface at the user\'s 2560x1600 / 1.6 scale, verified, then clustered by root cause',
  phases: [
    { title: 'Inspect', detail: '15 inspectors, one area each, each on a private headless display' },
    { title: 'Verify', detail: 'independent verifier per area re-checks findings and hunts for misses' },
    { title: 'Cluster', detail: 'group verified findings into fix workstreams by root cause' },
  ],
}

const Q = '/tmp/claude-1000/-home-nilsb/aa97432b-04cd-4681-802b-fbefae8bfa3e/scratchpad/qa'
const REPO = '/home/nilsb/Documents/projects/logisim-revolution'
const SRC = REPO + '/src/main/java/com/cburch/logisim'

const RIG = `
## The QA rig (read carefully)
You test the REAL application jar ($Q/logisim.jar, built from the current HEAD) running inside a private, invisible
KWin compositor with its own Xwayland display. Nothing appears on the user's screen. Default configuration matches the
user's laptop exactly: a 2560x1600 display, interface scale auto-detected (1.6), dark theme, and a copy of the user's
own preferences. Drive it with xdotool and capture it with the rig script:

  Q=${Q}
  $Q/qa.sh start ID [--scale S] [--theme dark|light] [--fresh] [--file PATH.circ] [--size WxH]
       starts compositor + app (maximized). --fresh = empty preferences (first-launch). --file copies the circuit into
       the rig's work dir first, so the original is never modified.
  $Q/qa.sh relaunch ID [--scale S] [--theme T] [--file F]   restart only the app (preferences persist within the rig)
  $Q/qa.sh shot ID OUT.png            capture the whole 2560x1600 display incl. popups, menus, tooltips, dialogs
  $Q/qa.sh win ID 'TitleRegex' OUT.png   capture one window (e.g. a dialog) at native size
  $Q/qa.sh zoom IN.png X Y W H OUT.png [FACTOR]   crop a region (native pixel coords) and enlarge (default x2)
  $Q/qa.sh windows ID                 list visible windows with titles and geometry (dialogs, popups)
  $Q/qa.sh x ID <xdotool args>        input, e.g.:
        x ID mousemove 120 340 click 1            (left click; click 3 = right, --repeat 2 = double)
        x ID mousemove 100 100 mousedown 1 sleep 0.2 mousemove 400 300 sleep 0.2 mouseup 1   (drag)
        x ID keydown ctrl click 4 keyup ctrl      (ctrl+wheel up; 4=up 5=down)
        x ID key ctrl+z    /   x ID type --delay 40 'hello'   /   x ID key Escape
  $Q/qa.sh log ID                     app stdout/stderr tail — check it for exceptions after every flow
  $Q/qa.sh stop ID                    ALWAYS stop your rig when done (also before starting another one)

Swing needs a moment: sleep 0.5-1s between an action and a shot. Tooltips need ~1.2s of hover.
Circuits you may open (always via --file so they get copied): the user's real projects
  "/home/nilsb/Downloads/PC.circ", "/home/nilsb/Documents/Uni/SIN.01022 (Computer architecture)/assignement_2_nb.circ",
  and scratch circuits ${Q}/../painters.circ, ${Q}/../sample.circ, ${Q}/../soc.circ. You can also build circuits by hand.

## How to look (this is why earlier reviews failed)
Earlier reviews rendered the window at 1440x900 and scale 1.0 and looked at downsampled images. They missed that NOT ONE
component name in the parts picker is readable at the user's real scale: the palette tiles are a fixed 68x62 px while
the font scales by 1.6, so every caption is cut in half. Do not repeat that failure:
- The Read tool downsamples a 2560x1600 shot to 2000x1250 (it tells you "multiply by 1.28"). Small text is NOT reliably
  readable there. Before judging any region — and before reporting anything about text — crop it with qa.sh zoom at
  native resolution (factor 1 or 2) and look at the crop. Never report clipping/blur from a downsampled view alone,
  and never miss it because the downsampled view looked fine.
- Look at EVERYTHING in the frame, not only the thing you were asked about. Report every defect you see.
- Be a harsh, specific critic — senior product designer + QA engineer. The user's acceptance bar: "If I can still
  recognize parts of the old software, we didn't work thoroughly enough", and the result must feel like a modern,
  polished app (think VS Code, JetBrains IDEs, Figma). They were very disappointed by the last round.
- Things to catch: clipped/truncated/overlapping/cut-off text; elements that did not scale (tiny icons next to scaled
  text, fixed-pixel boxes, hairline strokes, tiny hit targets); misalignment and inconsistent spacing; low contrast;
  dark-theme mistakes (white/grey boxes, black text on dark, light-theme colours leaking); anything that still looks
  like 2005 Logisim (titled/etched borders, grey Metal-era dialogs, old icons, raw JTables, FlowLayout button strips,
  old wording); dead or misbehaving controls; exceptions in the log; focus/keyboard problems; confusing workflows,
  too many steps, missing affordances or feedback; inconsistent terminology; stale/placeholder/debug text.
- Actually exercise things: click, type, drag, right-click, hover, resize, undo. QA means testing behaviour, not only
  looking.

## Hard rules
- NEVER modify anything under ${REPO} (read and grep the source freely to find root causes). Never touch ~/.java,
  ~/.config or the user's files. Never git anything. Write only inside your own output directory (given below).
- Use only the rig IDs assigned to you. Run at most one rig at a time. Never use pkill/killall — use qa.sh stop.
- Save every piece of evidence (full shots and zoomed crops) in your output directory with descriptive names; findings
  must cite them by absolute path.
- For each finding, find the responsible code (file:line under ${SRC}) when you can — grep for the string, class or
  constant — and say whether the same root cause probably affects other places (systemic).
`

const FINDING = {
  type: 'object',
  properties: {
    id: { type: 'string', description: 'area-prefixed id, e.g. palette-07' },
    title: { type: 'string' },
    severity: { type: 'string', enum: ['blocker', 'major', 'minor', 'polish'] },
    category: { type: 'string', enum: ['scaling-layout', 'legibility-contrast', 'visual-consistency', 'old-look', 'bug-interaction', 'workflow-ux', 'exception', 'copy-wording', 'performance'] },
    surface: { type: 'string', description: 'where in the app' },
    repro: { type: 'string' },
    observed: { type: 'string' },
    expected: { type: 'string' },
    evidence: { type: 'array', items: { type: 'string' }, description: 'absolute paths of screenshots/crops' },
    rootCause: { type: 'string', description: 'file:line and explanation, or "unknown"' },
    systemic: { type: 'boolean' },
    fixIdea: { type: 'string' },
  },
  required: ['id', 'title', 'severity', 'category', 'surface', 'observed', 'expected', 'evidence', 'rootCause', 'systemic', 'fixIdea'],
}

const FINDER_SCHEMA = {
  type: 'object',
  properties: {
    area: { type: 'string' },
    overallImpression: { type: 'string', description: '4-8 sentences: how this area feels to a demanding user, honestly' },
    coverage: { type: 'string', description: 'what you actually exercised, and anything you could not get to' },
    findings: { type: 'array', items: FINDING },
  },
  required: ['area', 'overallImpression', 'coverage', 'findings'],
}

const VERIFY_SCHEMA = {
  type: 'object',
  properties: {
    area: { type: 'string' },
    verdicts: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          id: { type: 'string' },
          verdict: { type: 'string', enum: ['CONFIRMED', 'REFUTED', 'CANNOT_REPRODUCE'] },
          severity: { type: 'string', enum: ['blocker', 'major', 'minor', 'polish'] },
          note: { type: 'string' },
        },
        required: ['id', 'verdict', 'severity', 'note'],
      },
    },
    newFindings: { type: 'array', items: FINDING },
    missedSummary: { type: 'string', description: 'what the first inspector overlooked, in 2-4 sentences' },
  },
  required: ['area', 'verdicts', 'newFindings', 'missedSummary'],
}

const AREAS = [
  { key: 'shell', title: 'Main window chrome and shell', brief: `
The whole main window as a user first meets it and while working: title bar (is the long build-ID title acceptable?),
menu bar and EVERY menu and submenu opened one by one (File, Edit, Project, Simulate, FPGA, Window, Help), the main
toolbar (hover each button for its tooltip), the activity bar (click every icon: circuits, components, simulation,
search, settings), each side-panel view and its header, the side panel splitter (drag it wider/narrower), the Properties
inspector (empty, with a component selected, closed via its x and brought back), the STATE section, the bottom drawer
(open/close), the status bar, the floating zoom control, editor tabs (open several circuits, close, overflow), and the
welcome screen (what it shows, whether it makes sense with a project already open). Also create a new project from the
welcome screen and check what the empty editor looks like.` },
  { key: 'palette', title: 'Component palette (parts picker)', brief: `
The component palette exhaustively. The known defect: tile captions are clipped. Go beyond it: expand and collapse
every category and scroll the whole palette; check every tile's icon and caption; hover for tooltips; search/filter
with several queries (and, gate, "mux", "ram", nonsense, a German word); favourites and recents (right-click a tile,
add/remove favourite, see sections update); keyboard navigation (Tab, arrows, Enter); the "group by library" toggle if
present and whatever the folder icon in the header does; select a tool and place the component on the canvas; widen
the side panel and see whether the grid reflows; categories that make no sense or duplicate each other (e.g. many
identical-looking Float tiles), inconsistent icon styles (some coloured old Logisim icons next to line icons).
Repeat the key checks at --scale 1.0 and --scale 2.0 via relaunch.` },
  { key: 'canvas', title: 'Canvas editing', brief: `
Core editing on the canvas at the user's scale: new project; place AND/OR/NOT gates, input and output pins, wire them
by dragging; select, rubber-band select, move, rotate (arrow keys and the Facing attribute), copy/paste, duplicate,
delete, undo/redo; add labels and a text annotation; a splitter, a tunnel and a multi-bit bus; zoom from the smallest to
the largest level (ctrl+wheel and the zoom control), grid appearance at each level; selection outline, handles, halo,
wire and bus colours, junction dots, pin markers; right-click on empty canvas, on a component, on a wire. Judge
whether the canvas still looks like old Logisim, whether strokes and text inside components look right at 1.6, and
whether anything feels clumsy compared to a modern editor.` },
  { key: 'components', title: 'How components are drawn', brief: `
Rendering quality of the component symbols themselves. Open ${Q}/../painters.circ (and build more if needed) so that
components from EVERY library appear on the canvas: gates, wiring (splitter, tunnel, pull resistor, clock, constant,
probe, transistors, power/ground), plexers, arithmetic, memory (flip-flops, registers, counters, RAM, ROM), I/O
(button, LED, 7-segment, hex digit, DIP switch, LED matrix, keyboard, TTY, joystick), TTL chips, BFH, SoC. Inspect at
100% and 200% zoom in dark theme: text inside components (legible? right font? tiny?), colours that clash with the
dark canvas, leftover white fills, pure-black strokes invisible on dark, inconsistent stroke weights, symbols that look
exactly like old Logisim. Also File > Export Image (export one and open the PNG) and the print preview if reachable:
the exported figure must be light and high-contrast.` },
  { key: 'inspector', title: 'Properties inspector and attribute editing', brief: `
Attribute editing in the Properties inspector. Select different components and edit every kind of attribute: text
(label), bit-width dropdown, facing, boolean, numbers (spinner or text), colours (colour picker popup), font (font
dialog/popup), enum dropdowns; mouse-wheel nudging on numeric rows and whether one undo reverts the whole gesture;
multi-select several gates and change a shared attribute; multi-select different kinds of components and see how
disagreeing values show; invalid input (bad label, out-of-range number) and how the error is shown; circuit
attributes (click empty canvas); any group headers, descriptions, filter field, collapse state. Check how the table
looks: row height vs scaled font, column widths, clipping, editors that overflow, dropdown popups.` },
  { key: 'simulation', title: 'Simulation workflow', brief: `
Simulation end to end. Build or open a circuit with inputs, a clock and outputs (the user's PC.circ copy is good);
poke inputs with the interact tool; toolbar simulation controls (run/pause, step, tick, tick frequency); Simulate menu
items (reset, tick once, enable ticks, frequency list); the Simulation side view (circuit state tree); descend into a
subcircuit's state; create an oscillation (NOT gate feeding itself) and see how the error is reported; a width-mismatch
error; the timing diagram / logging (Simulate > Logging / timing) which now opens in the bottom drawer — its layout,
row heights, signal selection, zoom, resizing the drawer; the test-vector panel (load a vector file if you can make
one); RAM/ROM contents via the hex editor (right-click a RAM > Edit contents): its layout, font, address go-to, load/
save buttons.` },
  { key: 'dialogs', title: 'Preferences, project options and dialogs', brief: `
Every dialog and settings surface. Preferences (File > Preferences): every page, every control, the filter box, the
Colors page (all groups), reset buttons; change the theme live from dark to light and back and see what fails to
update. Project > Options: every page (simulation, toolbar editor, mouse mappings, library). Also: About, Export Image
dialog, Print dialog, Project > Statistics (or equivalent), Load Library (built-in / JAR / Logisim) dialogs, the file
chooser (File > Open — is it a themed, usable chooser or an old Metal JFileChooser?), the unsaved-changes confirmation
on close, the add/rename circuit prompts, Help menu content (user guide / library reference browser). Look for old-look
dialogs, button strips, titled borders, clipped labels, unscaled controls, inconsistent button order.` },
  { key: 'analyzer', title: 'Combinational analysis (Analyzer)', brief: `
Project > Analyze Circuit (combinational analysis) on a small real circuit you build (2-3 inputs, 1-2 outputs) and on a
circuit from the user's projects: every page (inputs/outputs lists, truth table, expression, minimized form, Karnaugh
map), editing the truth table, entering an expression, building a circuit from the analysis, export/print options if
any. Also any window it opens and how it relates to the main window. Judge layout, clipping, dark-theme colours
(K-map, truth table), fonts, and whether it looks like the old program.` },
  { key: 'projects', title: 'Projects, subcircuits and navigation', brief: `
Working with a real multi-circuit project: open copies of the user's PC.circ and assignement_2_nb.circ. The circuits
list (side view): open circuits in tabs, rename with F2 and via context menu, add/delete/reorder circuits, set main
circuit; editor tabs (Ctrl+Tab, closing, many tabs); descend into a subcircuit instance and back (breadcrumb in the
status bar), the circuit appearance editor (Project > Edit Circuit Appearance or similar) with its own tools; the
global search (activity bar magnifier — find components/circuits by label); save, save-as, close with unsaved changes,
reopen via recent files. Note anything a student with a real assignment would stumble over.` },
  { key: 'hdl', title: 'FPGA, HDL, SoC and I/O extras', brief: `
The less common windows, which the last round rebuilt only partly: FPGA menu (synthesize/download commander, board
editor), placing a VHDL entity and a Verilog/HDL component and editing its content (HDL editor window), TCL console
components, SoC components (a CPU such as the RISC-V/Nios, the assembler window, bus/memory windows) — ${Q}/../soc.circ
has some, the I/O extras that open their own windows or have interactive surfaces (TTY, keyboard, LED matrix, RGB
video, joystick). Check each window's look against the rest of the app, dark theme correctness, layout at the user's
scale, and whether they work.` },
  { key: 'light', title: 'Light theme tour', brief: `
The whole app in the LIGHT theme (start with --theme light): main window, palette, a real circuit on the canvas (open
the user's PC.circ copy), menus, inspector with a selection, the drawer with the timing diagram, Preferences (all
pages), Project Options, the hex editor, the analyzer. Look for dark-theme colours leaking, low contrast (light grey on
white), inconsistent surfaces, icons that were drawn for dark backgrounds, and anything that looks unfinished. Also
switch from light to dark live in Preferences and back, and check whether every open surface updates.` },
  { key: 'scaling', title: 'Scaling, first launch and small screens', brief: `
Scale robustness and first launch. (1) --fresh: a brand-new user with no preferences — what scale and theme are
chosen, what the first window shows, whether it looks right. (2) Relaunch at --scale 1.0, 1.25, 2.0 and 2.5: main
window, palette, a menu, the inspector with a selection, Preferences and one other dialog at each — list everything
that breaks at some scales (clipping, overlap, unscaled icons, tiny strokes, wrong row heights, cut-off headers).
(3) Smaller displays: start new rigs with --size 1920x1080 and --size 1366x768 (scale as auto-detected, then 1.0):
does the window fit, are panels usable, do dialogs fit on screen. Also check how the scale can be changed in
Preferences and whether the change applies live or needs a restart (and whether that is explained).` },
  { key: 'codeaudit', title: 'Static audit: unscaled pixel sizes and hard-coded styling', noRig: true, brief: `
A code audit, no rig needed (you may start one to confirm a suspicion). The palette defect comes from fixed pixel sizes
(ComponentTile WIDTH=68/HEIGHT=62/PREVIEW_HEIGHT=40) while fonts scale with the interface. First establish exactly how
interface scaling works: read ${SRC}/util/UiScale.java, util/Spacing.java, util/UiFonts.java, the Scale preference in
prefs/AppPreferences.java, gui/theme/* (does FlatLaf's own uiScale / flatlaf.uiScale get set, or only font sizes?), and
AppIcons sizing. Then sweep ALL of ${SRC} (focus on gui/, and anything the redesign touched: gui/shell, gui/theme,
gui/prefs, gui/opts, gui/generic, gui/main, gui/log, gui/hex, gui/chrono, analyze/gui, fpga/gui, soc/gui, std/*
painting helpers) for sizes that do not pass through the scale: new Dimension(<literal>), setPreferredSize/Minimum/
Maximum with literals, static final int WIDTH/HEIGHT/SIZE/GAP/PAD/ROW constants used as pixels, literal icon sizes
(AppIcons.get(..., 14)), setRowHeight(literal), EmptyBorder/insets literals, deriveFont(literal), new Font(..., literal),
drawString at literal offsets, fillRoundRect radii, BasicStroke literals in chrome. For each: file:line, what it sizes,
whether it is visibly wrong at 1.6 (text inside a fixed box = clipping risk; icon = tiny), severity. Also find other
hard-coded styling: literal Color(...) in UI code (not canvas), Color.WHITE/BLACK in UI, fonts by name. Group results
by file; every result is a finding (you may batch closely related lines of one file into one finding).` },
  { key: 'keyboard', title: 'Keyboard, focus and the first-ten-minutes workflow', brief: `
(1) Keyboard and focus: Tab/Shift+Tab traversal through the main window and a dialog — is focus always visible?
shortcuts (Ctrl+N/O/S, Ctrl+Z/Y, Ctrl+C/V/D, Delete, Ctrl+A, Ctrl+Tab, F2, Ctrl+F or the search, Escape in dialogs,
Enter as default button, arrow keys to move a selection, number keys/shortcuts for tools if any); menu mnemonics.
(2) The first ten minutes of a new student: with --fresh, build a half adder (2 inputs, XOR + AND, 2 outputs) from
scratch, label everything, test it by poking inputs, save it to your output directory, close and reopen it. Count every
friction point: unclear icon, missing hint, wrong default, too many clicks, confusing wording, anything that required
guessing. Report each as a finding.` },
  { key: 'critic', title: 'Holistic design critique', brief: `
A senior product designer's review of the whole app at the user's configuration, judged against modern tools (VS Code,
JetBrains, Figma, Digital, CircuitVerse) and against the user's bar ("if I can still recognise the old software, it's
not thorough enough"). Take your own shots of: the main window empty and with a real circuit (user's PC.circ copy),
menus, the palette, the inspector, the drawer, Preferences, Project Options, one old-style window (hex editor or
analyzer). Evaluate: visual hierarchy, typography scale and consistency (sizes, weights, too many sizes?), density and
spacing rhythm, alignment grid, icon family consistency (line icons vs old coloured Logisim icons), colour usage and
accent discipline, empty states, how "designed" vs "assembled" it feels, and every place that still reads as old
Logisim. Findings should be concrete (what, where, what it should be instead), not vague taste.` },
]

function finderPrompt(a) {
  const rigId = a.key
  const out = `${Q}/out/${a.key}`
  return `You are a visual-inspection and QA tester for a heavily redesigned version of Logisim-evolution (a Java/Swing
digital-logic simulator, FlatLaf-based, repo ${REPO}). Your area: **${a.title}**.
${RIG}
## Your assignment
Rig ID: ${a.noRig ? `${rigId} (only if you need one)` : rigId}   Output directory: ${out}  (mkdir -p it first)
${a.brief}

Report every defect you find in this area AND anything else you notice along the way. Aim for completeness: a typical
thorough pass finds 15-40 issues in an area. Merge duplicates (same root cause on the same surface) into one finding
that lists every place. Stop your rig at the end (${Q}/qa.sh stop ${rigId}).
Return the structured result.`
}

function verifierPrompt(a, found) {
  const rigId = a.key + '-v'
  const out = `${Q}/out/${a.key}-verify`
  return `You are the second, independent QA reviewer for a heavily redesigned version of Logisim-evolution (a Java/Swing
digital-logic simulator, FlatLaf-based, repo ${REPO}). Area: **${a.title}**.
${RIG}
## Your assignment
Rig ID: ${rigId}${a.noRig ? ' (only if you need one)' : ''}   Output directory: ${out}  (mkdir -p it first)
The area's brief was:
${a.brief}

The first inspector reported the findings below. Your two jobs:
1. VERIFY each one skeptically. Look at its evidence crops (Read them) and, where the evidence is not conclusive,
   reproduce it on your own rig and crop at native resolution. Mark CONFIRMED, REFUTED (explain — e.g. an artifact of a
   downsampled view, a misreading, intended behaviour that is actually fine) or CANNOT_REPRODUCE. Re-grade severity if
   it is off. For code-audit claims, check the cited code.
2. HUNT FOR MISSES. The user's core complaint is that previous QA missed obvious things. Independently go through the
   area again, especially what the first inspector did not exercise (see their coverage note), and report every new
   defect as a newFinding (id prefix "${a.key}-v"). Do not re-report things already listed.

First inspector's overall impression: ${found.overallImpression}
First inspector's coverage: ${found.coverage}
Findings (JSON):
${JSON.stringify(found.findings, null, 1)}

Stop your rig at the end (${Q}/qa.sh stop ${rigId}). Return the structured result.`
}

phase('Inspect')
const results = await pipeline(
  AREAS,
  a => agent(finderPrompt(a), { label: `inspect:${a.key}`, phase: 'Inspect', schema: FINDER_SCHEMA }),
  (found, a) => {
    if (!found) return null
    return agent(verifierPrompt(a, found), { label: `verify:${a.key}`, phase: 'Verify', schema: VERIFY_SCHEMA })
      .then(v => ({ area: a.key, title: a.title, found, verify: v }))
  },
)

const merged = []
const areaNotes = []
for (const r of results.filter(Boolean)) {
  const verdicts = new Map(((r.verify && r.verify.verdicts) || []).map(v => [v.id, v]))
  for (const f of r.found.findings) {
    const v = verdicts.get(f.id)
    const verdict = v ? v.verdict : 'UNVERIFIED'
    if (verdict === 'REFUTED') continue
    merged.push({ ...f, area: r.area, verdict, severity: v ? v.severity : f.severity, verifyNote: v ? v.note : '' })
  }
  for (const f of ((r.verify && r.verify.newFindings) || [])) {
    merged.push({ ...f, area: r.area, verdict: 'FOUND_BY_VERIFIER' })
  }
  areaNotes.push({
    area: r.area,
    impression: r.found.overallImpression,
    coverage: r.found.coverage,
    missed: r.verify ? r.verify.missedSummary : '(verifier failed)',
    refuted: ((r.verify && r.verify.verdicts) || []).filter(v => v.verdict === 'REFUTED').map(v => `${v.id}: ${v.note}`),
  })
}
const missingAreas = AREAS.map(a => a.key).filter(k => !results.filter(Boolean).some(r => r.area === k))
if (missingAreas.length) log(`areas with no result: ${missingAreas.join(', ')}`)
log(`${merged.length} findings survived verification`)

phase('Cluster')
const CLUSTER_SCHEMA = {
  type: 'object',
  properties: {
    workstreams: {
      type: 'array',
      items: {
        type: 'object',
        properties: {
          name: { type: 'string' },
          rootCause: { type: 'string' },
          findingIds: { type: 'array', items: { type: 'string' } },
          files: { type: 'array', items: { type: 'string' } },
          approach: { type: 'string', description: 'how to fix it properly at the root, reusing existing utilities' },
          size: { type: 'string', enum: ['S', 'M', 'L', 'XL'] },
          priority: { type: 'integer', description: '1 = do first' },
        },
        required: ['name', 'rootCause', 'findingIds', 'files', 'approach', 'size', 'priority'],
      },
    },
    unclustered: { type: 'array', items: { type: 'string' } },
  },
  required: ['workstreams', 'unclustered'],
}
const clusters = await agent(`You are planning the fix work for a QA sweep of a redesigned Logisim-evolution
(Java/Swing/FlatLaf, repo ${REPO}; read source freely, modify nothing). Below are ${merged.length} verified findings from
15 areas. Group them into fix WORKSTREAMS by shared root cause (e.g. "fixed pixel sizes that ignore the interface
scale", "menus/popups", a specific window), so that fixing one root cause fixes many findings. For each workstream give
the root cause, every finding id it covers, the files involved, a concrete fix approach that addresses the root rather
than each symptom (check the code: name the existing utilities to reuse — e.g. util/UiScale, util/Spacing,
util/UiFonts, gui/theme/Tokens, gui/theme/AppIcons, gui/shell/* — and verify they do what you claim), a size and a
priority (blockers and the most-seen surfaces first). Every finding id must appear in exactly one workstream or in
unclustered. Also save a readable markdown report of all findings grouped by workstream to ${Q}/out/REPORT.md
(include severity, surface, observed/expected and evidence paths).

Area notes: ${JSON.stringify(areaNotes, null, 1)}

Findings: ${JSON.stringify(merged, null, 1)}`, { label: 'cluster', phase: 'Cluster', schema: CLUSTER_SCHEMA })

return { areaNotes, findings: merged, clusters, missingAreas }

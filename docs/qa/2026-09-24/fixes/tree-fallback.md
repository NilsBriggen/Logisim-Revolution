# Bounded tree fallback repair

Viewed the supplied native final-tree-expanded-light-1.6.png in
/tmp/logisim-visual-qa-PcImXB/evidence: only the ui-smoke root is visible. The parent's
Right/click/double-click failures are reported native evidence, not interactions I repeated.
Session 4194355 was not used or closed. Known theme/scale NPE log events were left to Godel.

## Confirmed cause and bounded correction

ProjectExplorerModel.fireStructureChanged sent root.getUserObjectPath(), whose root element
is the LogisimFile, instead of root.getPath(), whose element is the actual model node.
Frame invokes toolbox.updateStructure during startup; locale refresh uses the same path.
Toolbox merely switches existing cards, while ProjectExplorer.updateUI reinstalls tree metrics.

An isolated headless diagnostic against the parent's frozen application.jar, with temporary
preferences and autosave explicitly disabled, reproduced the sequence:

- Initial: 3 rows, 2 model children, root expanded.
- Queued updateStructure: still 3 rows and 2 children, but root expansion state becomes false.
- Dark/light updateUI: 1 visible row, still 2 model children, not a leaf.
- Explicit expandPath with the real root restores 3 rows.

Thus this is stale/inconsistent Swing expansion state, not deletion of the built-in libraries
or project tools. The two emitted event paths now use root.getPath(). No lifecycle, filtering,
selection policy, toolbox cards, theme/scaling code or application preferences were changed.
A plain Swing diagnostic with correctly typed event paths supports the correction, but the
new production patch itself still awaits parent compilation/test/native validation.

Changed source: src/main/java/com/cburch/logisim/gui/generic/ProjectExplorerModel.java (2 lines).
Added ProjectExplorerRefreshTest (2 methods): verify both event paths contain the actual model
root; repeatedly drain queued refreshes then reinstall the UI, checking root expansion, child
counts, visible rows and subsequent collapse/expand behavior. Mock project/file/tool fixtures
avoid real preferences, autosave threads and circuit documents.

No Gradle run. Frozen for parent integration. Selector:
--tests com.cburch.logisim.gui.generic.ProjectExplorerRefreshTest

Native acceptance remains pending, particularly mouse/keyboard expansion in the rebuilt tree.
The selection model rejects library-node selection and root handles default to false; these
explain why keyboard/disclosure recovery is limited, but were not expanded into further edits.

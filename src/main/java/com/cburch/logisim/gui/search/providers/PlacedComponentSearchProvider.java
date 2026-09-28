/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.search.providers;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.gui.icons.ComponentIcons;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.gui.main.SelectionActions;
import com.cburch.logisim.gui.search.FuzzyMatcher;
import com.cburch.logisim.gui.search.IndexedSearchProvider;
import com.cburch.logisim.gui.search.SearchCandidate;
import com.cburch.logisim.gui.search.SearchContext;
import com.cburch.logisim.gui.search.SearchProvider;
import com.cburch.logisim.gui.search.SearchQuery;
import com.cburch.logisim.gui.search.SearchResult;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.base.BaseLibrary;
import com.cburch.logisim.tools.EditTool;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Toolkit;
import java.util.ArrayList;
import java.util.List;
import javax.swing.Icon;
import javax.swing.SwingUtilities;

/**
 * Finds the components placed in the project's circuits, by label and by type: typing a pin's
 * label finds that pin, typing "RAM" finds every RAM. Choosing one shows its circuit, selects it
 * and scrolls it into view.
 *
 * <p>Nothing is offered before a query is typed; a project may hold thousands of components.
 */
public class PlacedComponentSearchProvider implements SearchProvider {

  /**
   * Taken off a labelled component found by its type rather than its label, so that a component
   * named for what was typed comes before one that merely is of that kind.
   */
  static final int TYPE_MATCH_PENALTY = 30;

  /** Margin around a revealed component, in circuit units, so its wires show too. */
  private static final int REVEAL_MARGIN = 30;

  private final PlacedComponentIndex index = new PlacedComponentIndex();
  private Project project;

  @Override
  public String getDisplayName() {
    return S.get("searchProviderPlaced");
  }

  @Override
  public boolean isAvailable(SearchContext context) {
    return context.project() != null;
  }

  @Override
  public void prepare(SearchContext context) {
    project = context.project();
  }

  @Override
  public List<SearchResult> search(SearchQuery query) {
    if (query.isEmpty() || project == null || project.getLogisimFile() == null) return List.of();
    final var circuits = project.getLogisimFile().getCircuits();
    index.retainOnly(circuits);
    final var text = query.text();
    final var results = new ArrayList<SearchResult>();
    for (final var circuit : circuits) {
      for (final var entry : index.entries(circuit)) {
        final var result = match(query, text, circuit, entry);
        if (result != null) results.add(result);
      }
    }
    return results;
  }

  /** How many times a circuit has been read into the index, for tests. */
  int indexReads() {
    return index.reads();
  }

  private SearchResult match(
      SearchQuery query, String text, Circuit circuit, PlacedComponentIndex.Entry entry) {
    final var label = entry.label();
    final var type = entry.type();
    final var byLabel = !label.isEmpty() && FuzzyMatcher.isSubsequence(text, label);
    final var byType = FuzzyMatcher.isSubsequence(text, type);
    if (!byLabel && !byType) return null;

    final var candidate = candidate(circuit, entry, label, type);
    // Only the title is matched: a word in the circuit name would otherwise find every
    // component of that circuit.
    final var result = IndexedSearchProvider.score(query, candidate, candidate.titleOffset());
    if (result != null) return result;
    if (label.isEmpty() || !byType) return null;
    final var typeMatch = FuzzyMatcher.match(text, type);
    return typeMatch == null
        ? null
        : SearchResult.of(candidate, typeMatch.score() - TYPE_MATCH_PENALTY);
  }

  private SearchCandidate candidate(
      Circuit circuit, PlacedComponentIndex.Entry entry, String label, String type) {
    final var component = entry.component();
    final var labelled = !label.isEmpty();
    return new SearchCandidate(
        labelled ? label : type,
        circuit.getName(),
        new ComponentIcon(component),
        labelled ? type : S.get("searchPlacedAtHint", component.getLocation().toString()),
        true,
        () -> reveal(project, circuit, component));
  }

  /**
   * Shows {@code circuit} in the layout editor with {@code component} selected and in view.
   */
  static void reveal(Project project, Circuit circuit, Component component) {
    if (project == null || !circuit.contains(component)) {
      // Deleted since the search was typed.
      Toolkit.getDefaultToolkit().beep();
      return;
    }
    if (project.getCurrentCircuit() != circuit) project.setCurrentCircuit(circuit);
    final var frame = project.getFrame();
    if (frame == null) return;
    if (!Frame.EDIT_LAYOUT.equals(frame.getEditorView())) frame.setEditorView(Frame.EDIT_LAYOUT);
    // The selection only shows under the Edit tool.
    if (!(project.getTool() instanceof EditTool)) selectEditTool(project);
    final var selection = project.getSelection();
    if (selection == null) return;
    final var drop = SelectionActions.dropAll(selection);
    if (drop != null) project.doAction(drop);
    selection.add(component);
    final var canvas = frame.getCanvas();
    // After the canvas has taken the size of the circuit just switched to.
    SwingUtilities.invokeLater(
        () -> {
          canvas.revealBounds(component.getBounds().expand(REVEAL_MARGIN));
          canvas.repaint();
        });
  }

  private static void selectEditTool(Project project) {
    for (final var library : project.getLogisimFile().getLibraries()) {
      if (library instanceof BaseLibrary base) {
        final var tool = base.getTool(EditTool._ID);
        if (tool != null) {
          project.setTool(tool);
          return;
        }
      }
    }
  }

  /** The component's own icon, as the toolbox shows its type. */
  private static final class ComponentIcon implements Icon {
    private final Component component;

    ComponentIcon(Component component) {
      this.component = component;
    }

    @Override
    public int getIconHeight() {
      return AppPreferences.getScaled(AppPreferences.BOX_SIZE);
    }

    @Override
    public int getIconWidth() {
      return AppPreferences.getScaled(AppPreferences.BOX_SIZE);
    }

    @Override
    public void paintIcon(java.awt.Component target, Graphics graphics, int x, int y) {
      final var factory = component.getFactory();
      final var border = AppPreferences.getScaled(AppPreferences.ICON_BORDER);
      final var icon = ComponentIcons.forFactory(factory.getClass());
      if (icon != null) {
        icon.paintIcon(target, graphics, x + border, y + border);
        return;
      }
      final var baseGraphics = graphics.create();
      baseGraphics.setColor(new Color(AppPreferences.COMPONENT_ICON_COLOR.get()));
      final var iconGraphics = baseGraphics.create();
      try {
        final var context =
            new ComponentDrawContext(target, null, null, baseGraphics, iconGraphics);
        factory.paintIcon(context, x + border, y + border, component.getAttributeSet());
      } finally {
        iconGraphics.dispose();
        baseGraphics.dispose();
      }
    }
  }
}

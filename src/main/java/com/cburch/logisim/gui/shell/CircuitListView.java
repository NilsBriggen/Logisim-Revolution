/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.file.LibraryEvent;
import com.cburch.logisim.file.LibraryListener;
import com.cburch.logisim.file.LogisimFileActions;
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.gui.main.StatisticsDialog;
import com.cburch.logisim.gui.menu.ProjectCircuitActions;
import com.cburch.logisim.gui.theme.AppIcons;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.UiFonts;
import com.cburch.logisim.vhdl.base.VhdlContent;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;

/**
 * The list of circuits and VHDL entities in the project.
 *
 * <p>Moving between circuits is the commonest thing anyone does in a project of any size, and it
 * used to mean finding the right entry in a tree that also held every component library, several
 * hundred entries deep. This panel holds only the things the user has made.
 */
public class CircuitListView extends JPanel {

  private static final long serialVersionUID = 1L;

  /** One row: either a circuit or a VHDL entity. */
  private record Entry(Object target, String name, boolean main) {}

  private final Project project;
  // Both event sources keep weak references; the view owns this listener's lifetime.
  private final MyListener listener = new MyListener();
  private final Set<Circuit> observedCircuits =
      Collections.newSetFromMap(new IdentityHashMap<>());
  private boolean listening;
  private final DefaultListModel<Entry> model = new DefaultListModel<>();
  private final JList<Entry> list = new JList<>(model);
  private final Runnable themeListener = list::repaint;
  private final FilterField filter;

  private String filterText = "";
  private Runnable onOpen = () -> {};

  /** Set while this panel is the one changing the project, so the change is not sent back. */
  private boolean selecting;

  public CircuitListView(Project project) {
    super(new BorderLayout());
    this.project = project;

    filter = new FilterField(S.get("explorerFilterHint"), text -> {
      filterText = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
      rebuild();
    });
    final var filterHolder = new JPanel(new BorderLayout());
    filterHolder.setBorder(
        BorderFactory.createEmptyBorder(0, Spacing.sm(), Spacing.xs(), Spacing.sm()));
    filterHolder.add(filter, BorderLayout.CENTER);
    add(filterHolder, BorderLayout.NORTH);

    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(new EntryRenderer());
    list.addListSelectionListener(
        event -> {
          if (event.getValueIsAdjusting() || selecting) return;
          open(list.getSelectedValue());
        });
    list.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent event) {
            showPopup(event);
          }

          @Override
          public void mouseReleased(MouseEvent event) {
            showPopup(event);
          }

          @Override
          public void mouseClicked(MouseEvent event) {
            if (!javax.swing.SwingUtilities.isLeftMouseButton(event)) return;
            final var index = list.locationToIndex(event.getPoint());
            if (index >= 0 && list.getCellBounds(index, index).contains(event.getPoint())) {
              open(model.get(index));
            }
          }
        });
    installKeyBindings();
    filter.setKeyboardHandoff(this::openFirstMatch, direction -> moveIntoList(direction));

    final var scroll = new JScrollPane(list);
    scroll.setBorder(null);
    add(scroll, BorderLayout.CENTER);

    attachListeners();
    rebuild();
  }

  private void attachListeners() {
    if (listening) return;
    project.addProjectListener(listener);
    project.addLibraryListener(listener);
    Theme.addListener(themeListener);
    listening = true;
  }

  @Override
  public void addNotify() {
    super.addNotify();
    attachListeners();
    rebuild();
  }

  @Override
  public void removeNotify() {
    project.removeProjectListener(listener);
    project.removeLibraryListener(listener);
    for (final var circuit : observedCircuits) circuit.removeCircuitListener(listener);
    observedCircuits.clear();
    Theme.removeListener(themeListener);
    listening = false;
    super.removeNotify();
  }

  /** Rebuilds the rows from the project, keeping whatever the filter allows. */
  private void rebuild() {
    final var file = project.getLogisimFile();
    if (listening) {
      final var current = Collections.newSetFromMap(new IdentityHashMap<Circuit, Boolean>());
      if (file != null) current.addAll(file.getCircuits());
      for (final var circuit : observedCircuits) {
        if (!current.contains(circuit)) circuit.removeCircuitListener(listener);
      }
      for (final var circuit : current) {
        if (!observedCircuits.contains(circuit)) circuit.addCircuitListener(listener);
      }
      observedCircuits.clear();
      observedCircuits.addAll(current);
    }
    final var entries = new ArrayList<Entry>();
    if (file != null) {
      final var main = file.getMainCircuit();
      for (final var circuit : file.getCircuits()) {
        entries.add(new Entry(circuit, circuit.getName(), circuit == main));
      }
      for (final var vhdl : file.getVhdlContents()) {
        entries.add(new Entry(vhdl, vhdl.getName(), false));
      }
    }
    selecting = true;
    try {
      model.clear();
      for (final var entry : entries) {
        if (matches(entry)) model.addElement(entry);
      }
    } finally {
      selecting = false;
    }
    syncSelection();
  }

  private boolean matches(Entry entry) {
    return filterText.isEmpty() || entry.name().toLowerCase(Locale.ROOT).contains(filterText);
  }

  /** Marks the row for whatever the project is showing, without asking for it to be shown again. */
  private void syncSelection() {
    final var current = currentTarget();
    selecting = true;
    try {
      for (var index = 0; index < model.size(); index++) {
        if (model.get(index).target() == current) {
          list.setSelectedIndex(index);
          list.ensureIndexIsVisible(index);
          return;
        }
      }
      list.clearSelection();
    } finally {
      selecting = false;
    }
  }

  /**
   * The keys a list is expected to answer.
   *
   * <p>The panel used to ignore every one of them: renaming meant finding the name row in the
   * properties panel, and removing meant the context menu or the small button in the heading.
   */
  private void installKeyBindings() {
    final var input = list.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
    final var actions = list.getActionMap();
    input.put(KeyStroke.getKeyStroke(KeyEvent.VK_F2, 0), "renameCircuit");
    input.put(KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "removeCircuit");
    actions.put("renameCircuit", action(this::renameSelected));
    actions.put("removeCircuit", action(this::removeSelected));
  }

  private static javax.swing.Action action(Runnable body) {
    return new javax.swing.AbstractAction() {
      private static final long serialVersionUID = 1L;

      @Override
      public void actionPerformed(java.awt.event.ActionEvent event) {
        body.run();
      }
    };
  }

  /** The circuit the list is pointing at, or {@code null} when it is pointing at something else. */
  private Circuit selectedCircuit() {
    final var entry = list.getSelectedValue();
    return (entry != null && containsTarget(entry.target())
        && entry.target() instanceof Circuit circuit) ? circuit : null;
  }

  private void renameSelected() {
    final var circuit = selectedCircuit();
    if (circuit == null) return;
    final var name =
        OptionPane.showInputDialog(
            javax.swing.SwingUtilities.getWindowAncestor(this),
            S.get("circuitRenamePrompt"),
            S.get("circuitRenameTitle"),
            OptionPane.QUESTION_MESSAGE,
            null,
            null,
            circuit.getName());
    if (name == null || !containsTarget(circuit)) return;
    final var trimmed = name.toString().trim();
    if (trimmed.isEmpty() || trimmed.equals(circuit.getName())) return;
    project.doAction(LogisimFileActions.renameCircuit(circuit, trimmed));
  }

  private void removeSelected() {
    final var circuit = selectedCircuit();
    if (circuit != null) ProjectCircuitActions.doRemoveCircuit(project, circuit);
  }

  /** Opens the only thing the filter left, which is what pressing Enter in a filter means. */
  private void openFirstMatch() {
    if (model.isEmpty()) return;
    list.setSelectedIndex(0);
    open(model.get(0));
  }

  private void moveIntoList(int direction) {
    if (model.isEmpty()) return;
    final var index = direction > 0 ? 0 : model.size() - 1;
    list.setSelectedIndex(index);
    list.requestFocusInWindow();
  }

  private Object currentTarget() {
    final var hdl = project.getCurrentHdl();
    return hdl != null ? hdl : project.getCurrentCircuit();
  }

  /** Reopens an editor after its last tab closed, including when opening the current circuit. */
  public void setOnOpen(Runnable onOpen) {
    this.onOpen = onOpen == null ? () -> {} : onOpen;
  }

  /** Clears only the visible selection; the project's circuit instance remains unchanged. */
  public void clearEditorSelection() {
    selecting = true;
    try {
      list.clearSelection();
    } finally {
      selecting = false;
    }
  }

  private void open(Entry entry) {
    if (entry == null || !containsTarget(entry.target())) return;
    if (entry.target() instanceof Circuit circuit) {
      if (project.getCurrentCircuit() != circuit) project.setCurrentCircuit(circuit);
    } else if (entry.target() instanceof VhdlContent vhdl) {
      project.setCurrentHdlModel(vhdl);
    }
    onOpen.run();
  }

  private boolean containsTarget(Object target) {
    final var file = project.getLogisimFile();
    if (file == null) return false;
    return file.getCircuits().stream().anyMatch(circuit -> circuit == target)
        || file.getVhdlContents().stream().anyMatch(hdl -> hdl == target);
  }

  private void showPopup(MouseEvent event) {
    if (!event.isPopupTrigger()) return;
    final var index = list.locationToIndex(event.getPoint());
    if (index < 0) return;
    list.setSelectedIndex(index);
    final var entry = model.get(index);
    if (!(entry.target() instanceof Circuit circuit) || !containsTarget(circuit)) return;

    final var menu = new JPopupMenu();
    menu.add(item(S.get("circuitRenameItem"), this::renameSelected));
    menu.add(item(S.get("projectSetAsMainItem"), () ->
        ProjectCircuitActions.doSetAsMainCircuit(project, circuit)));
    menu.addSeparator();
    menu.add(item(S.get("projectAnalyzeCircuitItem"), () ->
        ProjectCircuitActions.doAnalyze(project, circuit)));
    menu.add(item(S.get("projectGetCircuitStatisticsItem"), () ->
        StatisticsDialog.show(
            project.getFrame(),
            project.getLogisimFile(),
            circuit)));
    // Exporting a single circuit used to be reachable only from the library tree, which the
    // component palette replaced as the default view.
    menu.add(item(S.get("projectExportCircuitItem"), () ->
        ProjectCircuitActions.doExportCircuit(project, circuit)));
    menu.addSeparator();
    menu.add(item(S.get("projectRemoveCircuitItem"), this::removeSelected));
    menu.show(list, event.getX(), event.getY());
  }

  private javax.swing.JMenuItem item(String text, Runnable action) {
    final var menuItem = new javax.swing.JMenuItem(text);
    final var target = selectedCircuit();
    menuItem.addActionListener(event -> {
      if (target != null && containsTarget(target)) action.run();
    });
    return menuItem;
  }

  /** Re-reads the labels, after a change of language. */
  public void localeChanged() {
    filter.setPlaceholder(S.get("explorerFilterHint"));
    rebuild();
  }

  /** Draws a row: an icon, the name, and a note for the circuit the project starts in. */
  private final class EntryRenderer extends DefaultListCellRenderer {
    private static final long serialVersionUID = 1L;

    @Override
    public Component getListCellRendererComponent(
        JList<?> source, Object value, int index, boolean selected, boolean focused) {
      super.getListCellRendererComponent(source, value, index, selected, focused);
      final var entry = (Entry) value;
      final var isCurrent = entry.target() == currentTarget();
      setText(entry.main() ? entry.name() + "  " + S.get("explorerMainSuffix") : entry.name());
      setIcon(
          AppIcons.colored(
              entry.target() instanceof Circuit ? AppIcons.Id.CIRCUIT : AppIcons.Id.HDL,
              AppIcons.SIZE,
              isCurrent && !selected ? Tokens.accent() : Tokens.iconForeground()));
      setFont(isCurrent ? UiFonts.bodyBold() : UiFonts.body());
      if (isCurrent && !selected) setForeground(Tokens.accent());
      setBorder(
          BorderFactory.createEmptyBorder(Spacing.xs(), Spacing.sm(), Spacing.xs(), Spacing.sm()));
      return this;
    }
  }

  /** Keeps the list in step with the project. */
  private final class MyListener implements ProjectListener, LibraryListener, CircuitListener {
    @Override
    public void circuitChanged(CircuitEvent event) {
      if (event.getAction() == CircuitEvent.ACTION_SET_NAME) rebuild();
    }

    @Override
    public void projectChanged(ProjectEvent event) {
      final var action = event.getAction();
      if (action == ProjectEvent.ACTION_SET_CURRENT) {
        syncSelection();
        list.repaint();
      } else if (action == ProjectEvent.ACTION_SET_FILE) {
        rebuild();
      }
    }

    @Override
    public void libraryChanged(LibraryEvent event) {
      final var action = event.getAction();
      if (action == LibraryEvent.ADD_TOOL
          || action == LibraryEvent.REMOVE_TOOL
          || action == LibraryEvent.MOVE_TOOL
          || action == LibraryEvent.SET_MAIN
          || action == LibraryEvent.SET_NAME) {
        rebuild();
      }
    }
  }
}

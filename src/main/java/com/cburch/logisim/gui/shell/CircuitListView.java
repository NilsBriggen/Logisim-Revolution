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
import com.cburch.logisim.file.LibraryEvent;
import com.cburch.logisim.file.LibraryListener;
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
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
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
  private final DefaultListModel<Entry> model = new DefaultListModel<>();
  private final JList<Entry> list = new JList<>(model);
  private final FilterField filter;

  private String filterText = "";

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
        });
    final var scroll = new JScrollPane(list);
    scroll.setBorder(null);
    add(scroll, BorderLayout.CENTER);

    final var listener = new MyListener();
    project.addProjectListener(listener);
    project.addLibraryListener(listener);
    Theme.addListener(list::repaint);
    rebuild();
  }

  /** Rebuilds the rows from the project, keeping whatever the filter allows. */
  private void rebuild() {
    final var file = project.getLogisimFile();
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
    model.clear();
    for (final var entry : entries) {
      if (matches(entry)) model.addElement(entry);
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

  private Object currentTarget() {
    final var hdl = project.getCurrentHdl();
    return hdl != null ? hdl : project.getCurrentCircuit();
  }

  private void open(Entry entry) {
    if (entry == null) return;
    if (entry.target() instanceof Circuit circuit) {
      if (project.getCurrentCircuit() != circuit) project.setCurrentCircuit(circuit);
    } else if (entry.target() instanceof VhdlContent vhdl) {
      project.setCurrentHdlModel(vhdl);
    }
  }

  private void showPopup(MouseEvent event) {
    if (!event.isPopupTrigger()) return;
    final var index = list.locationToIndex(event.getPoint());
    if (index < 0) return;
    list.setSelectedIndex(index);
    final var entry = model.get(index);
    if (!(entry.target() instanceof Circuit circuit)) return;

    final var menu = new JPopupMenu();
    menu.add(item(S.get("projectAnalyzeCircuitItem"), () ->
        ProjectCircuitActions.doAnalyze(project, circuit)));
    menu.add(item(S.get("projectSetAsMainItem"), () ->
        ProjectCircuitActions.doSetAsMainCircuit(project, circuit)));
    menu.add(item(S.get("projectRemoveCircuitItem"), () ->
        ProjectCircuitActions.doRemoveCircuit(project, circuit)));
    menu.show(list, event.getX(), event.getY());
  }

  private static javax.swing.JMenuItem item(String text, Runnable action) {
    final var menuItem = new javax.swing.JMenuItem(text);
    menuItem.addActionListener(event -> action.run());
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
  private final class MyListener implements ProjectListener, LibraryListener {
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

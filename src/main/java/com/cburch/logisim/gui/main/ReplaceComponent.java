/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.main;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Replaces one placed component with another kind in the same place, keeping every attribute the
 * new kind also has (label, facing, width and so on), so the wires stay attached where the
 * connections coincide. One replacement is one undoable action.
 */
public final class ReplaceComponent {

  private ReplaceComponent() {}

  /** A component kind the user can pick, with the library path it is listed under. */
  record Candidate(AddTool tool, String path) {
    @Override
    public String toString() {
      return tool.getDisplayName() + "  —  " + path;
    }
  }

  /** Every component kind of the project's libraries that may replace {@code old}. */
  static List<Candidate> candidates(Project project, Component old) {
    final var ret = new ArrayList<Candidate>();
    collect(project.getLogisimFile(), "", project, old, ret, new HashSet<>());
    return ret;
  }

  private static void collect(
      Library library,
      String path,
      Project project,
      Component old,
      List<Candidate> out,
      Set<Library> seen) {
    if (!seen.add(library)) return;
    final var here = path.isEmpty() ? library.getDisplayName() : path + " › " + library.getDisplayName();
    for (final var tool : library.getTools()) {
      if (!(tool instanceof AddTool add)) continue;
      final var factory = add.getFactory();
      if (factory == null || isSameKind(factory, old.getFactory())) continue;
      // A circuit cannot contain itself.
      if (factory instanceof SubcircuitFactory sub
          && sub.getSubcircuit() == project.getCurrentCircuit()) {
        continue;
      }
      out.add(new Candidate(add, here));
    }
    for (final var child : library.getLibraries()) {
      if (!child.isHidden()) collect(child, here, project, old, out, seen);
    }
  }

  /** Whether two factories make the same kind of component (libraries may hold separate copies). */
  private static boolean isSameKind(ComponentFactory a, ComponentFactory b) {
    return a == b || (a.getClass() == b.getClass() && a.getName().equals(b.getName()));
  }

  /** Creates the replacement: {@code target} at the same place, with the attributes it shares. */
  static Component build(Component old, ComponentFactory target) {
    final var attrs = target.createAttributeSet();
    final var from = old.getAttributeSet();
    for (final var attr : from.getAttributes()) {
      Attribute<?> same = attrs.containsAttribute(attr) ? attr : attrs.getAttribute(attr.getName());
      if (same == null) continue;
      try {
        @SuppressWarnings("unchecked")
        final var typed = (Attribute<Object>) same;
        final var value = from.getValue(attr);
        if (attrs.isReadOnly(typed)) continue;
        attrs.setValue(typed, value);
      } catch (RuntimeException e) {
        // The value does not fit the new kind (another type, out of range): keep its default.
      }
    }
    return target.createComponent(old.getLocation(), attrs);
  }

  /**
   * The connected ends of {@code old} that {@code replacement} has nothing at, so whatever is
   * attached there would be cut off.
   */
  static List<Location> lostConnections(Circuit circuit, Component old, Component replacement) {
    final var kept = new HashSet<Location>();
    for (final var end : replacement.getEnds()) kept.add(end.getLocation());
    final var lost = new ArrayList<Location>();
    for (final var end : old.getEnds()) {
      final var loc = end.getLocation();
      if (kept.contains(loc)) continue;
      for (final var other : circuit.getComponents(loc)) {
        if (other != old) {
          lost.add(loc);
          break;
        }
      }
    }
    return lost;
  }

  static Action action(Circuit circuit, Component old, Component replacement) {
    final var xn = new CircuitMutation(circuit);
    xn.replace(old, replacement);
    return xn.toAction(
        S.getter("replaceComponentAction", replacement.getFactory().getDisplayGetter()));
  }

  /** Asks which kind to use and replaces {@code old} with it. */
  public static void ask(Project project, Circuit circuit, Component old) {
    final var frame = project.getFrame();
    final var dialog =
        new JDialog(frame, S.get("replaceComponentTitle", old.getFactory().getDisplayName()), true);
    final var all = candidates(project, old);
    final var model = new DefaultListModel<Candidate>();
    final var list = new JList<>(model);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    final var filter = new JTextField(24);
    final Runnable refilter =
        () -> {
          final var text = filter.getText().trim().toLowerCase(Locale.ROOT);
          model.clear();
          for (final var c : all) {
            if (text.isEmpty() || c.toString().toLowerCase(Locale.ROOT).contains(text)) {
              model.addElement(c);
            }
          }
          if (!model.isEmpty()) list.setSelectedIndex(0);
        };
    filter
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent e) {
                refilter.run();
              }

              @Override
              public void removeUpdate(DocumentEvent e) {
                refilter.run();
              }

              @Override
              public void changedUpdate(DocumentEvent e) {
                refilter.run();
              }
            });
    refilter.run();
    final var ok = new JButton(S.get("replaceComponentButton"));
    final var cancel = new JButton(S.get("labelReplaceClose"));
    final Candidate[] chosen = {null};
    ok.addActionListener(
        e -> {
          chosen[0] = list.getSelectedValue();
          dialog.dispose();
        });
    cancel.addActionListener(e -> dialog.dispose());
    list.addMouseListener(
        new java.awt.event.MouseAdapter() {
          @Override
          public void mouseClicked(java.awt.event.MouseEvent e) {
            if (e.getClickCount() == 2) ok.doClick();
          }
        });
    final var buttons = new JPanel();
    buttons.add(ok);
    buttons.add(cancel);
    final var content = new JPanel(new BorderLayout(4, 4));
    content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    content.add(filter, BorderLayout.NORTH);
    content.add(new JScrollPane(list), BorderLayout.CENTER);
    content.add(buttons, BorderLayout.SOUTH);
    dialog.setContentPane(content);
    dialog.getRootPane().setDefaultButton(ok);
    dialog.setSize(460, 420);
    dialog.setLocationRelativeTo(frame);
    dialog.setVisible(true);

    if (chosen[0] == null) return;
    final var replacement = build(old, chosen[0].tool().getFactory());
    final var lost = lostConnections(circuit, old, replacement);
    if (!lost.isEmpty()) {
      final var answer =
          OptionPane.showConfirmDialog(
              frame,
              S.get("replaceComponentLost", lost.size()),
              S.get("replaceComponentTitle", old.getFactory().getDisplayName()),
              OptionPane.YES_NO_OPTION);
      if (answer != OptionPane.YES_OPTION) return;
    }
    project.doAction(action(circuit, old, replacement));
  }
}

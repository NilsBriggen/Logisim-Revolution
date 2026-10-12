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
import com.cburch.logisim.circuit.LabelReplace;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.JoinedAction;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.SetAttributeAction;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.PatternSyntaxException;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;

/**
 * Edit &gt; Replace in Labels: renames component labels in the current circuit or the whole
 * project, previewing every change and why any is refused. All accepted changes are one undoable
 * action.
 */
public final class FindReplaceLabelsDialog extends JDialog {
  private static final long serialVersionUID = 1L;

  private final Project project;
  private final JTextField findField = new JTextField(20);
  private final JTextField replaceField = new JTextField(20);
  private final JCheckBox regexBox = new JCheckBox(S.get("labelReplaceRegex"));
  private final JCheckBox caseBox = new JCheckBox(S.get("labelReplaceMatchCase"));
  private final JRadioButton circuitScope = new JRadioButton(S.get("labelReplaceScopeCircuit"));
  private final JRadioButton projectScope = new JRadioButton(S.get("labelReplaceScopeProject"));
  private final JLabel status = new JLabel(" ");
  private final JButton replaceButton = new JButton(S.get("labelReplaceButton"));
  private final PreviewModel preview = new PreviewModel();
  private List<LabelReplace.Match> matches = List.of();

  /** Opens the dialog for {@code project}'s window. */
  public static void show(Project project) {
    final var dialog = new FindReplaceLabelsDialog(project);
    dialog.setLocationRelativeTo(project.getFrame());
    dialog.setVisible(true);
  }

  private FindReplaceLabelsDialog(Project project) {
    super(project.getFrame(), S.get("labelReplaceTitle"), false);
    this.project = project;

    final var form = new JPanel(new GridBagLayout());
    form.setBorder(BorderFactory.createEmptyBorder(8, 8, 4, 8));
    final var gbc = new GridBagConstraints();
    gbc.insets = new Insets(2, 2, 2, 2);
    gbc.anchor = GridBagConstraints.LINE_START;
    gbc.gridy = 0;
    form.add(new JLabel(S.get("labelReplaceFind")), gbc);
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1;
    form.add(findField, gbc);
    gbc.gridy = 1;
    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = 0;
    form.add(new JLabel(S.get("labelReplaceWith")), gbc);
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = 1;
    form.add(replaceField, gbc);
    gbc.gridy = 2;
    gbc.gridx = 1;
    final var options = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
    options.add(regexBox);
    options.add(caseBox);
    options.add(circuitScope);
    options.add(projectScope);
    form.add(options, gbc);
    final var scope = new ButtonGroup();
    scope.add(circuitScope);
    scope.add(projectScope);
    circuitScope.setSelected(true);

    final var table = new JTable(preview);
    table.setFillsViewportHeight(true);
    final var buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING));
    final var close = new JButton(S.get("labelReplaceClose"));
    buttons.add(replaceButton);
    buttons.add(close);
    final var bottom = new JPanel(new BorderLayout());
    bottom.setBorder(BorderFactory.createEmptyBorder(0, 8, 4, 8));
    bottom.add(status, BorderLayout.CENTER);
    bottom.add(buttons, BorderLayout.EAST);

    getContentPane().add(form, BorderLayout.NORTH);
    getContentPane().add(new JScrollPane(table), BorderLayout.CENTER);
    getContentPane().add(bottom, BorderLayout.SOUTH);

    final DocumentListener update =
        new DocumentListener() {
          @Override
          public void insertUpdate(DocumentEvent e) {
            refresh();
          }

          @Override
          public void removeUpdate(DocumentEvent e) {
            refresh();
          }

          @Override
          public void changedUpdate(DocumentEvent e) {
            refresh();
          }
        };
    findField.getDocument().addDocumentListener(update);
    replaceField.getDocument().addDocumentListener(update);
    regexBox.addActionListener(e -> refresh());
    caseBox.addActionListener(e -> refresh());
    circuitScope.addActionListener(e -> refresh());
    projectScope.addActionListener(e -> refresh());
    replaceButton.addActionListener(e -> apply());
    close.addActionListener(e -> dispose());
    getRootPane().setDefaultButton(replaceButton);

    refresh();
    pack();
    setSize(Math.max(getWidth(), 640), 420);
  }

  private List<Circuit> scopeCircuits() {
    if (projectScope.isSelected()) return new ArrayList<>(project.getLogisimFile().getCircuits());
    final var current = project.getCurrentCircuit();
    return current == null ? List.of() : List.of(current);
  }

  private void refresh() {
    final var find = findField.getText();
    matches = List.of();
    if (!find.isEmpty()) {
      try {
        final var pattern =
            LabelReplace.pattern(find, regexBox.isSelected(), caseBox.isSelected());
        matches =
            LabelReplace.find(
                scopeCircuits(), pattern, replaceField.getText(), regexBox.isSelected());
        status.setText(" ");
      } catch (PatternSyntaxException e) {
        status.setText(S.get("labelReplaceBadPattern", e.getDescription()));
      }
    } else {
      status.setText(" ");
    }
    final var valid = matches.stream().filter(LabelReplace.Match::isValid).count();
    if (!find.isEmpty() && status.getText().isBlank()) {
      status.setText(S.get("labelReplaceSummary", valid, matches.size() - valid));
    }
    replaceButton.setEnabled(valid > 0);
    preview.fireTableDataChanged();
  }

  private void apply() {
    final var action = toAction(matches);
    if (action != null) project.doAction(action);
    refresh();
  }

  /**
   * One undoable action setting every valid replacement, one step per circuit; null if none is
   * valid.
   */
  static Action toAction(List<LabelReplace.Match> matches) {
    final var perCircuit = new LinkedHashMap<Circuit, SetAttributeAction>();
    for (final var match : matches) {
      if (!match.isValid()) continue;
      perCircuit
          .computeIfAbsent(
              match.circuit(),
              c -> new SetAttributeAction(c, S.getter("labelReplaceAction")))
          .set(match.component(), StdAttr.LABEL, match.newLabel());
    }
    if (perCircuit.isEmpty()) return null;
    final var actions = perCircuit.values().toArray(new Action[0]);
    return actions.length == 1 ? actions[0] : new JoinedAction(actions);
  }

  private class PreviewModel extends AbstractTableModel {
    private static final long serialVersionUID = 1L;

    @Override
    public int getRowCount() {
      return matches.size();
    }

    @Override
    public int getColumnCount() {
      return 5;
    }

    @Override
    public String getColumnName(int column) {
      return switch (column) {
        case 0 -> S.get("labelReplaceCircuitColumn");
        case 1 -> S.get("labelReplaceComponentColumn");
        case 2 -> S.get("labelReplaceOldColumn");
        case 3 -> S.get("labelReplaceNewColumn");
        default -> S.get("labelReplaceStatusColumn");
      };
    }

    @Override
    public Object getValueAt(int row, int column) {
      final var match = matches.get(row);
      return switch (column) {
        case 0 -> match.circuit().getName();
        case 1 -> match.component().getFactory().getDisplayName();
        case 2 -> match.oldLabel();
        case 3 -> match.newLabel();
        default -> match.isValid() ? S.get("labelReplaceOk") : match.problem();
      };
    }
  }
}

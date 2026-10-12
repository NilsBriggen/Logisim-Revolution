/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.log;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.util.UiScale;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;
import javax.swing.table.AbstractTableModel;

/**
 * Lists the watchpoints of the shown circuit and adds new ones. A watchpoint watches a signal of
 * the timing diagram, so the signals offered are the ones being logged.
 */
class WatchpointsPanel extends LogPanel {
  private static final long serialVersionUID = 1L;

  private final TableModel tableModel = new TableModel();
  private final JTable table = new JTable(tableModel);
  private final JComboBox<SignalChoice> signalBox = new JComboBox<>();
  private final JComboBox<Watchpoint.Condition> conditionBox =
      new JComboBox<>(Watchpoint.Condition.values());
  private final JTextField valueField = new JTextField("0", 8);
  private final JButton addButton = new JButton();
  private final JButton removeButton = new JButton();
  private final JLabel signalLabel = new JLabel();
  private final JLabel valueLabel = new JLabel();
  private final Runnable setListener = () -> SwingUtilities.invokeLater(tableModel::refresh);
  private WatchpointSet shown;

  /** A logged signal as a combo-box entry, shown by its display name. */
  private record SignalChoice(SignalInfo info) {
    @Override
    public String toString() {
      return info.getDisplayName();
    }
  }

  WatchpointsPanel(LogFrame frame) {
    super(frame, new BorderLayout());
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setFillsViewportHeight(true);
    add(new JScrollPane(table), BorderLayout.CENTER);

    final var controls = new JPanel(new FlowLayout(FlowLayout.LEADING));
    controls.add(signalLabel);
    controls.add(signalBox);
    controls.add(conditionBox);
    controls.add(valueLabel);
    controls.add(valueField);
    controls.add(addButton);
    controls.add(removeButton);
    add(controls, BorderLayout.SOUTH);

    // The logged signals change from the Options tab; offer the current ones whenever opened.
    signalBox.addPopupMenuListener(
        new PopupMenuListener() {
          @Override
          public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            refreshSignals();
          }

          @Override
          public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {}

          @Override
          public void popupMenuCanceled(PopupMenuEvent e) {}
        });
    conditionBox.addActionListener(e -> updateValueField());
    addButton.addActionListener(e -> addWatchpoint());
    removeButton.addActionListener(e -> removeWatchpoint());
    table.getSelectionModel().addListSelectionListener(e -> updateButtons());

    localeChanged();
    modelChanged(null, getModel());
  }

  private void refreshSignals() {
    final var model = getModel();
    final var selected = (SignalChoice) signalBox.getSelectedItem();
    final var choices = new DefaultComboBoxModel<SignalChoice>();
    if (model != null) {
      for (final var signal : model.getSignals()) choices.addElement(new SignalChoice(signal.info));
    }
    signalBox.setModel(choices);
    if (selected != null) {
      for (var i = 0; i < choices.getSize(); i++) {
        if (choices.getElementAt(i).info() == selected.info()) signalBox.setSelectedIndex(i);
      }
    }
    updateButtons();
  }

  private void updateValueField() {
    final var condition = (Watchpoint.Condition) conditionBox.getSelectedItem();
    valueField.setEnabled(condition != null && condition.usesOperand());
  }

  private void updateButtons() {
    addButton.setEnabled(shown != null && signalBox.getItemCount() > 0);
    removeButton.setEnabled(shown != null && table.getSelectedRow() >= 0);
  }

  private void addWatchpoint() {
    final var choice = (SignalChoice) signalBox.getSelectedItem();
    final var condition = (Watchpoint.Condition) conditionBox.getSelectedItem();
    if (shown == null || choice == null || condition == null) return;
    var operand = 0L;
    if (condition.usesOperand()) {
      final var parsed = parseOperand(valueField.getText());
      if (parsed == null) {
        OptionPane.showMessageDialog(
            this,
            S.get("watchBadValue", valueField.getText()),
            S.get("watchpointsTab"),
            OptionPane.ERROR_MESSAGE);
        return;
      }
      operand = parsed;
    }
    shown.add(new Watchpoint(choice.info(), condition, operand));
  }

  /** Parses decimal, {@code 0x} hexadecimal or {@code 0b} binary; null if it is none of them. */
  static Long parseOperand(String text) {
    final var t = text.trim().replace("_", "").toLowerCase();
    try {
      if (t.startsWith("0x")) return Long.parseUnsignedLong(t.substring(2), 16);
      if (t.startsWith("0b")) return Long.parseUnsignedLong(t.substring(2), 2);
      return Long.parseLong(t);
    } catch (NumberFormatException e) {
      return null;
    }
  }

  private void removeWatchpoint() {
    final var row = table.getSelectedRow();
    if (shown == null || row < 0) return;
    final var all = shown.getAll();
    if (row < all.size()) shown.remove(all.get(row));
  }

  @Override
  public String getHelpText() {
    return S.get("watchpointsHelp");
  }

  @Override
  public String getTitle() {
    return S.get("watchpointsTab");
  }

  @Override
  public void localeChanged() {
    signalLabel.setText(S.get("watchSignalLabel"));
    valueLabel.setText(S.get("watchValueLabel"));
    addButton.setText(S.get("watchAddButton"));
    removeButton.setText(S.get("watchRemoveButton"));
    valueField.setToolTipText(S.get("watchValueTip"));
    conditionBox.repaint();
    tableModel.fireTableStructureChanged();
    sizeColumns();
  }

  /** The check box and the hit count need little room; the condition takes the rest. */
  private void sizeColumns() {
    final var columns = table.getColumnModel();
    if (columns.getColumnCount() < 3) return;
    columns.getColumn(0).setMaxWidth(UiScale.scaled(48));
    columns.getColumn(2).setMaxWidth(UiScale.scaled(72));
  }

  @Override
  public void modelChanged(Model oldModel, Model newModel) {
    if (shown != null) shown.removeListener(setListener);
    shown = newModel == null ? null : newModel.getWatchpoints();
    if (shown != null) shown.addListener(setListener);
    tableModel.refresh();
    refreshSignals();
    updateValueField();
  }

  private class TableModel extends AbstractTableModel {
    private static final long serialVersionUID = 1L;
    private List<Watchpoint> rows = List.of();

    void refresh() {
      rows = shown == null ? List.of() : shown.getAll();
      fireTableDataChanged();
      updateButtons();
    }

    @Override
    public int getRowCount() {
      return rows.size();
    }

    @Override
    public int getColumnCount() {
      return 3;
    }

    @Override
    public String getColumnName(int column) {
      return switch (column) {
        case 0 -> S.get("watchEnabledColumn");
        case 1 -> S.get("watchConditionColumn");
        default -> S.get("watchHitsColumn");
      };
    }

    @Override
    public Class<?> getColumnClass(int column) {
      return switch (column) {
        case 0 -> Boolean.class;
        case 2 -> Integer.class;
        default -> String.class;
      };
    }

    @Override
    public boolean isCellEditable(int row, int column) {
      return column == 0;
    }

    @Override
    public Object getValueAt(int row, int column) {
      final var watchpoint = rows.get(row);
      return switch (column) {
        case 0 -> watchpoint.isEnabled();
        case 1 -> watchpoint.describe();
        default -> watchpoint.getHits();
      };
    }

    @Override
    public void setValueAt(Object value, int row, int column) {
      if (column == 0 && value instanceof Boolean enabled) rows.get(row).setEnabled(enabled);
    }
  }
}

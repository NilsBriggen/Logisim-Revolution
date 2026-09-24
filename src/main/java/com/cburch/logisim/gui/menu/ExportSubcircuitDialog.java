/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.menu;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.gui.generic.Dialogs;
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.gui.generic.ScrollableForm;
import com.cburch.logisim.util.UiScale;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableModel;

public class ExportSubcircuitDialog extends JDialog implements ActionListener {
  private static final long serialVersionUID = 1L;

  private final Circuit mainCircuit;
  private final List<Circuit> dependentCircuits;
  private final String[] currentExportNames;
  private final JCheckBox autoPrefixCheckBox;
  private final JTable table;
  private final DependentsTableModel tableModel;
  private boolean isApproved = false;

  public ExportSubcircuitDialog(Window parent, Circuit mainCircuit, List<Circuit> dependentCircuits) {
    super(parent, S.get("exportSubcircuitTitle", mainCircuit.getName()), ModalityType.APPLICATION_MODAL);
    this.mainCircuit = mainCircuit;
    this.dependentCircuits = dependentCircuits;
    this.currentExportNames = new String[dependentCircuits.size()];

    for (int i = 0; i < dependentCircuits.size(); i++) {
      currentExportNames[i] = mainCircuit.getName() + "_" + dependentCircuits.get(i).getName();
    }

    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    final var gap = UiScale.scaled(12);
    setLayout(new BorderLayout(gap, gap));

    final var topPanel = new JPanel(new BorderLayout());
    final var headerLabel = new JLabel(S.get("exportSubcircuitDependenciesHeader"));
    topPanel.add(headerLabel);

    tableModel = new DependentsTableModel();
    table = new DependencyTable(tableModel);
    headerLabel.setLabelFor(table);
    table.getAccessibleContext().setAccessibleName(S.get("exportSubcircuitDependenciesHeader"));

    final var scrollPane = new JScrollPane(table);

    autoPrefixCheckBox = new JCheckBox(S.get("exportSubcircuitAutoPrefix"), true);
    autoPrefixCheckBox.addActionListener(this);

    final var okButton = new JButton(S.get("exportSubcircuitFileSelect"));
    okButton.setActionCommand("OK");
    okButton.addActionListener(this);
    getRootPane().setDefaultButton(okButton);
    Dialogs.installEscapeToClose(this);

    final var cancelButton = new JButton(S.get("showStateDialogCancelButton"));
    cancelButton.setActionCommand("CANCEL");
    cancelButton.addActionListener(this);

    final var buttonPanel = new JPanel(new FlowLayout(FlowLayout.TRAILING, gap, 0));
    buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, gap, gap, gap));
    buttonPanel.add(cancelButton);
    buttonPanel.add(okButton);

    final var form = new JPanel(new BorderLayout(gap, gap));
    form.setBorder(BorderFactory.createEmptyBorder(0, gap, gap, gap));
    form.add(topPanel, BorderLayout.NORTH);
    form.add(scrollPane, BorderLayout.CENTER);
    form.add(autoPrefixCheckBox, BorderLayout.SOUTH);
    final var viewport = new ScrollableForm(form);
    viewport.setHelpText(S.get("exportSubcircuitExplanation"));
    add(viewport.createScrollPane(), BorderLayout.CENTER);
    add(buttonPanel, BorderLayout.SOUTH);

    ScrollableForm.sizeWindow(this, new Dimension(620, 440), new Dimension(420, 280), true);
    setLocationRelativeTo(parent);
  }

  public boolean isApproved() {
    return isApproved;
  }

  public Map<Circuit, String> getRenamedCircuits() {
    if (!isApproved) return null;
    final var map = new HashMap<Circuit, String>();
    for (int i = 0; i < dependentCircuits.size(); i++) {
      map.put(dependentCircuits.get(i), currentExportNames[i].trim());
    }
    return map;
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    if (e.getSource() == autoPrefixCheckBox) {
      final var isAuto = autoPrefixCheckBox.isSelected();
      for (int i = 0; i < dependentCircuits.size(); i++) {
        if (isAuto) {
          currentExportNames[i] = mainCircuit.getName() + "_" + dependentCircuits.get(i).getName();
        } else {
          currentExportNames[i] = dependentCircuits.get(i).getName();
        }
      }
      tableModel.fireTableDataChanged();
    } else if ("OK".equals(e.getActionCommand())) {
      if (table.isEditing() && !table.getCellEditor().stopCellEditing()) return;
      if (validateNames()) {
        isApproved = true;
        dispose();
      }
    } else if ("CANCEL".equals(e.getActionCommand())) {
      isApproved = false;
      dispose();
    }
  }

  private boolean validateNames() {
    final var usedNames = new HashSet<String>();
    usedNames.add(mainCircuit.getName().trim().toLowerCase());

    for (int i = 0; i < currentExportNames.length; i++) {
      final var name = currentExportNames[i] == null ? "" : currentExportNames[i].trim();
      if (name.isEmpty()) {
        showError(S.get("circuitNameMissingError"));
        return false;
      }
      final var lowerName = name.toLowerCase();
      if (usedNames.contains(lowerName)) {
        showError(name + ": " + S.get("circuitNameExists"));
        return false;
      }
      usedNames.add(lowerName);
    }
    return true;
  }

  private void showError(String msg) {
    OptionPane.showMessageDialog(this, msg, S.get("exportSubcircuitTitle", mainCircuit.getName()), OptionPane.ERROR_MESSAGE);
  }

  static class DependencyTable extends JTable {
    private static final long serialVersionUID = 1L;

    DependencyTable(TableModel model) {
      super(model);
      getTableHeader().setReorderingAllowed(false);
      setFillsViewportHeight(true);
      updateMetrics();
    }

    @Override
    public void updateUI() {
      super.updateUI();
      updateMetrics();
    }

    private void updateMetrics() {
      if (getFont() == null || getTableHeader() == null) return;
      setRowHeight(getFontMetrics(getFont()).getHeight() + UiScale.scaled(10));
      getTableHeader().setFont(getFont());
      for (var i = 0; i < getColumnCount(); i++) {
        final var column = getColumnModel().getColumn(i);
        final var labelWidth = getFontMetrics(getFont()).stringWidth(getColumnName(i));
        column.setMinWidth(labelWidth + UiScale.scaled(24));
        column.setPreferredWidth(Math.max(column.getMinWidth(), UiScale.scaled(250)));
      }
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
      return new Dimension(UiScale.scaled(520), Math.min(Math.max(getRowCount(), 2), 8)
          * getRowHeight());
    }
  }

  private class DependentsTableModel extends AbstractTableModel {
    private static final long serialVersionUID = 1L;
    private final String[] columnNames = {
      S.get("exportSubcircuitOriginalName"),
      S.get("exportSubcircuitExportedName")
    };

    @Override
    public int getRowCount() {
      return dependentCircuits.size();
    }

    @Override
    public int getColumnCount() {
      return columnNames.length;
    }

    @Override
    public String getColumnName(int column) {
      return columnNames[column];
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex == 1;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      if (columnIndex == 0) {
        return dependentCircuits.get(rowIndex).getName();
      } else {
        return currentExportNames[rowIndex];
      }
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
      if (columnIndex == 1 && aValue != null) {
        currentExportNames[rowIndex] = aValue.toString();
        fireTableCellUpdated(rowIndex, columnIndex);
      }
    }
  }
}

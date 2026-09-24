/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.generic;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.fpga.gui.HdlColorRenderer;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.util.JDialogOk;
import com.cburch.logisim.util.JInputComponent;
import com.cburch.logisim.util.JInputDialog;
import com.cburch.logisim.util.LocaleListener;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.UiFonts;
import com.cburch.logisim.util.UiScale;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.CellEditorListener;
import javax.swing.event.ChangeEvent;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableModel;

@SuppressWarnings("serial")

/*
 * Attribute table panel
 * Shows detailed attributes of the selected element.
 */
public class AttrTable extends JPanel implements LocaleListener {
  private static final AttrTableModel NULL_ATTR_MODEL = new NullAttrModel();
  private final Window parent;
  private final JLabel title;
  private final JLabel editError = new JLabel();
  private final JTextArea emptyProperties = new JTextArea();
  private final JScrollPane tableScroll;
  private final JTable table;
  private final TableModelAdapter tableModel;
  private final CellEditor editor = new CellEditor();
  private boolean titleEnabled;

  public AttrTable(Window parent) {
    super(new BorderLayout());
    this.parent = parent;

    titleEnabled = true;
    title = new TitleLabel();
    title.setHorizontalAlignment(SwingConstants.CENTER);
    title.setVerticalAlignment(SwingConstants.CENTER);
    tableModel = new TableModelAdapter(parent, NULL_ATTR_MODEL);
    table = new JTable(tableModel) {
      private static final long serialVersionUID = 1L;

      @Override
      public String getToolTipText(MouseEvent event) {
        return elidedCellText(this, event.getPoint());
      }

      @Override
      public void editingStopped(ChangeEvent event) {
        // CellEditor commits its captured transaction before notifying JTable.
        removeEditor();
      }
    };
    table.setDefaultEditor(Object.class, editor);
    table.setTableHeader(null);
    table.setDefaultRenderer(String.class, new HdlColorRenderer());
    table.addMouseWheelListener(this::handleMouseWheel);

    final var propPanel = new JPanel(new BorderLayout(0, 0));
    tableScroll = new JScrollPane(table);
    emptyProperties.setEditable(false);
    emptyProperties.setFocusable(false);
    emptyProperties.setOpaque(false);
    emptyProperties.setLineWrap(true);
    emptyProperties.setWrapStyleWord(true);

    propPanel.add(title, BorderLayout.PAGE_START);
    propPanel.add(tableScroll, BorderLayout.CENTER);
    editError.setVisible(false);
    propPanel.add(editError, BorderLayout.SOUTH);

    this.add(propPanel, BorderLayout.CENTER);

    LocaleManager.addLocaleListener(this);
    localeChanged();
    refreshUiMetrics();
    Theme.addListener(this, this::refreshUiMetrics);
  }

  @Override
  public void updateUI() {
    super.updateUI();
    refreshUiMetrics();
  }

  private void refreshUiMetrics() {
    // JPanel invokes updateUI before this class has constructed its child components.
    if (title == null || table == null || editError == null) return;
    final var font = UiFonts.body();
    title.setFont(UiFonts.heading());
    table.setFont(font);
    editError.setFont(UiFonts.small());
    emptyProperties.setFont(font);
    emptyProperties.setBorder(Spacing.border(Spacing.SM));
    if (table.getDefaultRenderer(String.class) instanceof Component renderer) {
      renderer.setFont(font);
    }
    table.setRowHeight(Math.max(UiScale.iconSize(),
        table.getFontMetrics(font).getHeight() + 2 * Spacing.xs()));
    revalidate();
    repaint();
  }

  /**
   * The row the wheel is currently being turned over, and the value it will be given.
   *
   * <p>A wheel gesture used to apply every intermediate value, so spinning a bit width from 1 to
   * 16 left fifteen separate entries in the undo history and asked the circuit to re-fit its wires
   * fifteen times. The value now moves in the table as the wheel turns but is written to the
   * circuit once, when the wheel stops.
   */
  private AttrTableModelRow pendingRow = null;

  private Object pendingValue = null;
  private String pendingDisplay = null;
  private AttrTableModelRow.Edit pendingEdit = null;
  private Timer pendingTimer = null;
  private AttrTableModelRow wheelRow = null;
  private double wheelRemainder = 0;

  /** How long the wheel must be still before the value is written to the circuit. */
  private static final int WHEEL_SETTLE_MS = 350;

  /** The value to paint for a row: the one being spun, if this is that row. */
  String displayValueOf(AttrTableModelRow row) {
    return (row == pendingRow && pendingDisplay != null) ? pendingDisplay : row.getValue();
  }

  private void handleMouseWheel(MouseWheelEvent e) {
    // Alt + wheel is an explicit edit gesture. Ordinary scrolling never edits a property.
    if (!e.isAltDown()) {
      forwardWheel(e);
      return;
    }
    final var rowIdx = table.rowAtPoint(e.getPoint());
    final var colIdx = table.columnAtPoint(e.getPoint());
    if (colIdx != 1 || rowIdx < 0) {
      forwardWheel(e);
      return;
    }

    final var attrModel = tableModel.attrModel;
    if (attrModel == null) return;
    final var row = attrModel.getRow(rowIdx);
    if (row == null || !row.isValueEditable()) {
      forwardWheel(e);
      return;
    }
    if (table.isEditing() && !editor.stopCellEditing()) return;
    if (wheelRow != row) {
      commitPendingNudge();
      wheelRow = row;
      wheelRemainder = 0;
    }
    wheelRemainder += e.getPreciseWheelRotation();
    final var rotation = (int) wheelRemainder;
    wheelRemainder -= rotation;
    e.consume();
    if (rotation == 0) return;

    // A list of choices has no arithmetic to do: step through the options instead.
    final var editorComp = row.getEditor(parent);
    if (editorComp instanceof JComboBox<?> box && !box.isEditable()) {
      final var count = box.getItemCount();
      if (pendingRow == row) box.setSelectedItem(pendingValue);
      final var currentIdx = box.getSelectedIndex();
      if (count <= 1 || currentIdx < 0) return;
      final var newIdx = (int) Math.max(0, Math.min(count - 1L, (long) currentIdx - rotation));
      if (newIdx == currentIdx) return;
      pendingValue = box.getItemAt(newIdx);
      pendingDisplay = row.displayValue(pendingValue);
    } else {
      final var nudged = AttrWheelNudge.nudge(displayValueOf(row), rotation, row.getAttribute());
      if (nudged.isEmpty()) return;
      pendingValue = nudged.get();
      pendingDisplay = nudged.get();
    }
    if (pendingEdit == null) pendingEdit = row.captureEdit(parent, List.of(row));
    pendingRow = row;

    if (pendingTimer == null) {
      pendingTimer = new Timer(WHEEL_SETTLE_MS, evt -> commitPendingNudge());
      pendingTimer.setRepeats(false);
    }
    pendingTimer.restart();
    table.repaint();
  }

  private void forwardWheel(MouseWheelEvent event) {
    final var scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, table);
    if (scroll == null) return;
    final var point = SwingUtilities.convertPoint(table, event.getPoint(), scroll);
    scroll.dispatchEvent(new MouseWheelEvent(scroll, event.getID(), event.getWhen(),
        event.getModifiersEx(), point.x, point.y, event.getXOnScreen(), event.getYOnScreen(),
        event.getClickCount(), event.isPopupTrigger(), event.getScrollType(),
        event.getScrollAmount(), event.getWheelRotation(), event.getPreciseWheelRotation()));
    event.consume();
  }

  /** Writes the value the wheel arrived at, as a single change. */
  private void commitPendingNudge() {
    if (pendingTimer != null) pendingTimer.stop();
    final var edit = pendingEdit;
    final var value = pendingValue;
    pendingRow = null;
    pendingValue = null;
    pendingDisplay = null;
    pendingEdit = null;
    wheelRow = null;
    wheelRemainder = 0;
    if (edit == null || value == null) return;
    applyValue(edit, value);
    table.repaint();
  }

  /**
   * Sets a value and says so when it is refused.
   *
   * <p>Both wheel paths used to discard the exception, so a value the component would not accept
   * simply snapped back with no explanation.
   */
  private void applyValue(AttrTableModelRow.Edit edit, Object value) {
    try {
      edit.commit(value);
    } catch (AttrTableSetException refused) {
      OptionPane.showMessageDialog(parent, refused.getMessage(),
          S.get("attributeChangeInvalidTitle"), OptionPane.WARNING_MESSAGE);
    }
  }

  public AttrTableModel getAttrTableModel() {
    return tableModel.attrModel;
  }

  public void setAttrTableModel(AttrTableModel value) {
    // Whatever the wheel was spinning belongs to the thing being shown now, not the next one.
    commitPendingNudge();
    final var editor = table.getCellEditor();
    if (editor != null && !editor.stopCellEditing()) return;
    tableModel.setAttrTableModel(value == null ? NULL_ATTR_MODEL : value);
    updateTitle();
  }

  public boolean isTitleEnabled() {
    return titleEnabled;
  }

  public void setTitleEnabled(boolean value) {
    titleEnabled = value;
    updateTitle();
  }

  @Override
  public void localeChanged() {
    emptyProperties.setText(S.get("propertiesEmptyHint"));
    emptyProperties.getAccessibleContext().setAccessibleName(S.get("propertiesTab"));
    updateTitle();
    tableModel.fireTableChanged();
  }

  private void updateTitle() {
    if (titleEnabled) {
      final var text = tableModel.attrModel.getTitle();
      if (text == null) {
        title.setVisible(false);
      } else {
        title.setText(text);
        title.setVisible(true);
      }
    } else {
      title.setVisible(false);
    }
  }

  private static class MyDialog extends JDialogOk {
    JInputComponent input;
    Object value;

    public MyDialog(JInputComponent input) {
      super(S.get("attributeDialogTitle"));
      configure(input);
    }

    private void configure(JInputComponent input) {
      this.input = input;
      this.value = null;

      // Thanks to Christophe Jacquet, who contributed a fix to this
      // so that when the dialog is resized, the component within it
      // is resized as well. (Tracker #2024479)
      final var p = new JPanel(new BorderLayout());
      p.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
      // Hide the JFileChooser buttons, since we already have the
      // MyDialog ones
      if (input instanceof JFileChooser chooser) chooser.setControlButtonsAreShown(false);
      p.add((JComponent) input, BorderLayout.CENTER);
      getContentPane().add(p, BorderLayout.CENTER);

      pack();
    }

    public Object getValue() {
      return value;
    }

    @Override
    public void okClicked() {
      value = input.getValue();
    }
  }

  /**
   * Returns the full text of the cell at {@code point} when the column is too narrow to show all
   * of it, so it can serve as a tooltip; {@code null} when the cell is fully visible or absent.
   */
  static String elidedCellText(JTable table, Point point) {
    final var row = table.rowAtPoint(point);
    final var column = table.columnAtPoint(point);
    if (row < 0 || column < 0) return null;
    final var value = table.getValueAt(row, column);
    if (value == null) return null;
    final var text = value.toString();
    if (text.isEmpty()) return null;
    final var renderer = table.prepareRenderer(table.getCellRenderer(row, column), row, column);
    final var available = table.getCellRect(row, column, false).width;
    return renderer.getPreferredSize().width > available ? text : null;
  }

  private static class NullAttrModel implements AttrTableModel {
    @Override
    public void addAttrTableModelListener(AttrTableModelListener listener) {
      // Do nothing.
    }

    @Override
    public AttrTableModelRow getRow(int rowIndex) {
      return null;
    }

    @Override
    public int getRowCount() {
      return 0;
    }

    @Override
    public String getTitle() {
      return null;
    }

    @Override
    public void removeAttrTableModelListener(AttrTableModelListener listener) {
      // Do nothing.
    }
  }

  private static class TitleLabel extends JLabel {
    @Override
    public Dimension getMinimumSize() {
      Dimension ret = super.getMinimumSize();
      return new Dimension(1, ret.height);
    }
  }

  private class CellEditor implements TableCellEditor, FocusListener, ActionListener {
    final LinkedList<CellEditorListener> listeners = new LinkedList<>();
    AttrTableModelRow currentRow;
    AttrTableModelRow[] currentRows;
    AttrTableModelRow.Edit currentEdit;
    Component currentEditor;
    Object initialValue;
    boolean choiceChanged;
    boolean multiEditActive = false;
    boolean stoppedCellEditing = false;

    //
    // ActionListener methods
    //
    @Override
    public void actionPerformed(ActionEvent e) {
      if (e.getSource() != currentEditor) return;
      choiceChanged = true;
      stopCellEditing();
    }

    //
    // TableCellListener management
    //
    @Override
    public void addCellEditorListener(CellEditorListener l) {
      // Adds a listener to the list that's notified when the
      // editor stops, or cancels editing.
      listeners.add(l);
    }

    //
    // other TableCellEditor methods
    //
    @Override
    public void cancelCellEditing() {
      // Tells the editor to cancel editing and not accept any
      // partially edited value.
      fireEditingCanceled();
    }

    public void fireEditingCanceled() {
      if (stoppedCellEditing) return;
      clearEdit();
      final var e = new ChangeEvent(AttrTable.this);
      for (final var l : new ArrayList<>(listeners)) {
        l.editingCanceled(e);
      }
    }

    private void clearEdit() {
      if (currentEditor != null) currentEditor.removeFocusListener(this);
      if (currentEditor instanceof JComboBox<?> box) box.removeActionListener(this);
      if (currentEditor instanceof JTextField field) field.removeActionListener(this);
      editError.setVisible(false);
      editError.setText("");
      currentRow = null;
      currentRows = null;
      currentEdit = null;
      currentEditor = null;
      initialValue = null;
      multiEditActive = false;
      choiceChanged = false;
    }

    public void fireEditingStopped() {
      final var e = new ChangeEvent(AttrTable.this);
      for (final var l : new ArrayList<>(listeners)) {
        l.editingStopped(e);
      }
    }

    @Override
    public void focusGained(FocusEvent e) {
      // Do nothing
    }

    //
    // FocusListener methods
    //
    @Override
    public void focusLost(FocusEvent e) {
      if (e.getSource() != currentEditor) return;
      final var dst = e.getOppositeComponent();
      if (dst != null) {
        var p = dst;
        while (p != null && !(p instanceof Window)) {
          if (p == AttrTable.this) {
            // switch to another place in this table,
            // no problem
            return;
          }
          p = p.getParent();
        }
        // focus transferred outside table; stop editing
        editor.stopCellEditing();
      }
    }

    @Override
    public Object getCellEditorValue() {
      // Returns the value contained in the editor.
      final var comp = currentEditor;
      if (comp instanceof JTextField field) {
        return field.getText();
      } else if (comp instanceof JComboBox box) {
        return box.getSelectedItem();
      } else {
        return null;
      }
    }

    @Override
    public Component getTableCellEditorComponent(
        JTable table, Object value, boolean isSelected, int rowIndex, int columnIndex) {
      final var attrModel = tableModel.attrModel;
      commitPendingNudge();
      final var row = attrModel.getRow(rowIndex);
      AttrTableModelRow[] rows = null;
      int[] rowIndexes = null;
      multiEditActive = false;

      if ((columnIndex == 0) || (rowIndex == 0)) {
        return new JLabel(row.getLabel());
      }

      var editor = row.getEditor(parent);
      currentEdit = row.captureEdit(parent, List.of(row));
      if (editor instanceof JComboBox box) {
        box.addActionListener(this);
        editor.addFocusListener(this);
        rowIndexes = table.getSelectedRows();
        if (isSelected && row.supportsMultiEdit() && rowIndexes.length > 1) {
          multiEditActive = true;
          rows = new AttrTableModelRow[rowIndexes.length];
          for (var i = 0; i < rowIndexes.length; i++) {
            rows[i] = attrModel.getRow(rowIndexes[i]);
            // The model gets first say, then the general rule: same choices, same edit.
            if (!rows[i].isValueEditable()
                || (!row.multiEditCompatible(rows[i])
                    && !AttrMultiEdit.sameChoices(box, rows[i].getEditor(parent)))) {
              multiEditActive = false;
              rowIndexes = null;
              rows = null;
              break;
            }
          }
        } else {
          rowIndexes = null;
        }
      } else if (editor instanceof JInputDialog dlog) {
        dlog.setVisible(true);
        final var retVal = dlog.getValue();
        try {
          currentEdit.commit(retVal);
        } catch (AttrTableSetException e) {
          OptionPane.showMessageDialog(parent, e.getMessage(), S.get("attributeChangeInvalidTitle"),
              OptionPane.WARNING_MESSAGE);
        }
        editor = null;
      } else if (editor instanceof JInputComponent input) {
        final var dialog = new MyDialog(input);
        dialog.setVisible(true);
        final var retVal = dialog.getValue();
        try {
          currentEdit.commit(retVal);
        } catch (AttrTableSetException e) {
          OptionPane.showMessageDialog(parent, e.getMessage(), S.get("attributeChangeInvalidTitle"),
              OptionPane.WARNING_MESSAGE);
        }
        editor = null;
      } else {
        editor.addFocusListener(this);
        if (editor instanceof JTextField field) field.addActionListener(this);
      }

      currentRow = row;
      currentRows = rows;
      if (rows != null) currentEdit = row.captureEdit(parent, List.of(rows));
      currentEditor = editor;
      initialValue = getCellEditorValue();
      choiceChanged = false;
      return editor;
    }

    public boolean isEditing(AttrTableModelRow row) {
      if (currentRow == row) return true;
      if (currentRows == null) return false;
      for (AttrTableModelRow r : currentRows)
        if (r == row) return true;
      return false;
    }

    @Override
    public boolean isCellEditable(EventObject anEvent) {
      if (anEvent instanceof KeyEvent key) {
        if (key.isMetaDown() || ((key.isControlDown() || key.isAltDown())
            && !key.isAltGraphDown())) return false;
        final var printable = key.getKeyChar() != KeyEvent.CHAR_UNDEFINED
            && !Character.isISOControl(key.getKeyChar());
        if (key.getID() == KeyEvent.KEY_TYPED) return printable;
        // JTable starts automatic editing on KEY_PRESSED, then forwards KEY_TYPED to the editor.
        return key.getID() == KeyEvent.KEY_PRESSED
            && (printable || key.getKeyCode() == KeyEvent.VK_SPACE
                || key.getKeyCode() == KeyEvent.VK_F2);
      }
      if (anEvent instanceof MouseEvent) {
        return ((MouseEvent) anEvent).getClickCount() >= 1;
      }
      return true;
    }

    @Override
    public void removeCellEditorListener(CellEditorListener l) {
      // Removes a listener from the list that's notified
      listeners.remove(l);
    }

    @Override
    public boolean shouldSelectCell(EventObject anEvent) {
      // Returns true if the editing cell should be selected,
      // false otherwise.
      return !multiEditActive;
    }

    @Override
    public boolean stopCellEditing() {
      // Tells the editor to stop editing and accept any partially
      // edited value as the value of the editor.
      if (stoppedCellEditing) return false;
      if (currentEdit == null || currentEditor == null) return true;
      stoppedCellEditing = true;
      try {
        final var value = getCellEditorValue();
        if (choiceChanged || !Objects.equals(initialValue, value)) currentEdit.commit(value);
        fireEditingStopped();
        clearEdit();
        tableModel.fireTableChanged();
        return true;
      } catch (AttrTableSetException ex) {
        // Keep the invalid draft and its captured target available for correction or Escape.
        editError.setText(currentRow.getLabel() + ": " + ex.getMessage());
        editError.setVisible(true);
        if (currentEditor instanceof JComponent component) {
          component.setToolTipText(ex.getMessage());
          component.getAccessibleContext().setAccessibleDescription(ex.getMessage());
          component.requestFocusInWindow();
        }
        return false;
      } finally {
        stoppedCellEditing = false;
      }
    }
  }

  private class TableModelAdapter implements TableModel, AttrTableModelListener {
    final Window parent;
    final LinkedList<TableModelListener> listeners;
    AttrTableModel attrModel;

    TableModelAdapter(Window parent, AttrTableModel attrModel) {
      this.parent = parent;
      this.listeners = new LinkedList<>();
      this.attrModel = attrModel;
    }

    @Override
    public void addTableModelListener(TableModelListener l) {
      listeners.add(l);
    }

    @Override
    public void attrStructureChanged(AttrTableModelEvent e) {
      if (e.getSource() != attrModel) {
        attrModel.removeAttrTableModelListener(this);
        return;
      }
      final var ed = table.getCellEditor();
      if (editor.stoppedCellEditing) return;
      if (ed != null && !ed.stopCellEditing()) return;
      commitPendingNudge();
      fireTableChanged();
    }

    //
    // AttrTableModelListener methods
    //
    @Override
    public void attrTitleChanged(AttrTableModelEvent e) {
      if (e.getSource() != attrModel) {
        attrModel.removeAttrTableModelListener(this);
        return;
      }
      updateTitle();
    }

    @Override
    public void attrValueChanged(AttrTableModelEvent e) {
      if (e.getSource() != attrModel) {
        attrModel.removeAttrTableModelListener(this);
        return;
      }
      // A selection model can change underneath the editor before Frame switches it. Keep
      // the captured edit alive until focus/model transfer resolves it.
      if (table.isEditing() || editor.stoppedCellEditing) {
        table.repaint();
        return;
      }
      fireTableChanged();
    }

    void fireTableChanged() {
      final var e = new TableModelEvent(this);
      for (final var l : new ArrayList<>(listeners)) {
        l.tableChanged(e);
      }
      final Component view = getRowCount() == 0 ? emptyProperties : table;
      if (tableScroll.getViewport().getView() != view) tableScroll.setViewportView(view);
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return String.class;
    }

    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    public String getColumnName(int columnIndex) {
      return (columnIndex == 0) ? "Attribute" : "Value";
    }

    @Override
    public int getRowCount() {
      return attrModel.getRowCount();
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      final var row = attrModel.getRow(rowIndex);
      return (columnIndex == 0) ? row.getLabel() : displayValueOf(row);
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return columnIndex > 0 && attrModel.getRow(rowIndex).isValueEditable() && rowIndex > 0;
    }

    @Override
    public void removeTableModelListener(TableModelListener l) {
      listeners.remove(l);
    }

    void setAttrTableModel(AttrTableModel value) {
      if (attrModel != value) {
        attrModel.removeAttrTableModelListener(this);
        attrModel = value;
        attrModel.addAttrTableModelListener(this);
        fireTableChanged();
      }
    }

    @Override
    public void setValueAt(Object value, int rowIndex, int columnIndex) {
      if (columnIndex > 0) {
        try {
          attrModel.getRow(rowIndex).setValue(parent, value);
        } catch (AttrTableSetException e) {
          OptionPane.showMessageDialog(parent, e.getMessage(), S.get("attributeChangeInvalidTitle"),
              OptionPane.WARNING_MESSAGE);
        }
      }
    }
  }
}

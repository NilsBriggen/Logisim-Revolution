/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.generic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSets;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.util.LocaleManager;
import java.awt.Component;
import java.awt.Container;
import java.awt.DefaultKeyboardFocusManager;
import java.awt.KeyboardFocusManager;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseWheelEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComboBox;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class AttrTableEditingTest {
  private static final Attribute<Boolean> FIRST = Attributes.forBoolean("first");
  private static final Attribute<Boolean> SECOND = Attributes.forBoolean("second");
  private static final Attribute<String> LABEL = Attributes.forString("label");
  private static final Attribute<Integer> NUMBER = Attributes.forIntegerRange("number", 0, 100);

  @Test
  void printableTypingStartsTextEditingAndPreservesTheFirstCharacter() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try (final var fixture = new Fixture()) {
        final var originalFocusManager = KeyboardFocusManager.getCurrentKeyboardFocusManager();
        KeyboardFocusManager.setCurrentKeyboardFocusManager(new DefaultKeyboardFocusManager() {
          @Override
          public Component getFocusOwner() {
            return fixture.table;
          }
        });
        try {
          for (final var character : new char[] {'a', '7', ' ', 'ä'}) {
            fixture.table.changeSelection(3, 1, false, false);
            // Space is a native table selection binding; use F2 before typing a literal space.
            final var keyCode = character == ' ' ? KeyEvent.VK_F2
                : KeyEvent.getExtendedKeyCodeForChar(character);
            SwingUtilities.processKeyBindings(new KeyEvent(fixture.table, KeyEvent.KEY_PRESSED,
                1, 0, keyCode, character == ' ' ? KeyEvent.CHAR_UNDEFINED : character));
            assertTrue(fixture.table.isEditing(), "key press must start editing");
            final var event = new KeyEvent(fixture.table, KeyEvent.KEY_TYPED, 2, 0,
                KeyEvent.VK_UNDEFINED, character);
            assertTrue(SwingUtilities.processKeyBindings(event));
            assertEquals(String.valueOf(character),
                ((JTextField) fixture.table.getEditorComponent()).getText());
            fixture.table.getCellEditor().cancelCellEditing();
          }
          assertTrue(fixture.model.commits.isEmpty());
        } finally {
          KeyboardFocusManager.setCurrentKeyboardFocusManager(originalFocusManager);
        }
      }
    });
  }

  @Test
  void shortcutsAndNavigationDoNotStartEditingButSpaceAndF2StillDo() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try (final var fixture = new Fixture()) {
        for (final var modifier : new int[] {InputEvent.CTRL_DOWN_MASK,
            InputEvent.ALT_DOWN_MASK, InputEvent.META_DOWN_MASK}) {
          assertFalse(fixture.table.editCellAt(3, 1, new KeyEvent(fixture.table,
              KeyEvent.KEY_PRESSED, 1, modifier, KeyEvent.VK_A, 'a')));
          assertFalse(fixture.table.editCellAt(3, 1, new KeyEvent(fixture.table,
              KeyEvent.KEY_TYPED, 1, modifier, KeyEvent.VK_UNDEFINED, 'a')));
        }
        assertFalse(fixture.table.editCellAt(3, 1, new KeyEvent(fixture.table,
            KeyEvent.KEY_PRESSED, 1, 0, KeyEvent.VK_DOWN, KeyEvent.CHAR_UNDEFINED)));
        for (final var key : new int[] {KeyEvent.VK_SPACE, KeyEvent.VK_F2}) {
          assertTrue(fixture.table.editCellAt(3, 1, new KeyEvent(fixture.table,
              KeyEvent.KEY_PRESSED, 1, 0, key, KeyEvent.CHAR_UNDEFINED)));
          fixture.table.getCellEditor().cancelCellEditing();
        }
        assertTrue(fixture.model.commits.isEmpty());
      }
    });
  }

  @Test
  void emptyPropertiesShowsGuidanceWithoutInventingAnEditableRow() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try (final var fixture = new Fixture()) {
        final var scroll = (JScrollPane) SwingUtilities.getAncestorOfClass(
            JScrollPane.class, fixture.table);
        fixture.panel.setAttrTableModel(null);
        assertEquals(0, fixture.panel.getAttrTableModel().getRowCount());
        final var guidance = (JTextArea) scroll.getViewport().getView();
        assertFalse(guidance.getText().isBlank());
        assertFalse(guidance.isEditable());
        assertTrue(guidance.getLineWrap());
        fixture.panel.setAttrTableModel(fixture.model);
        assertSame(fixture.table, scroll.getViewport().getView());
        fixture.panel.setAttrTableModel(null);
        assertSame(guidance, scroll.getViewport().getView());
        assertTrue(fixture.model.commits.isEmpty());
      }
    });
  }

  @Test
  void cancelMixedRowsDoesNotWriteAndLateFocusLossDoesNotCommit() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try (final var fixture = new Fixture()) {
        final var table = fixture.table;
        table.setRowSelectionInterval(1, 2);
        assertTrue(table.editCellAt(1, 1));
        final var component = table.getEditorComponent();
        table.getCellEditor().cancelCellEditing();
        for (final var listener : component.getFocusListeners()) {
          listener.focusLost(
              new FocusEvent(component, FocusEvent.FOCUS_LOST, false, new JTextField()));
        }
        assertEquals(false, fixture.model.getAttributeSet().getValue(FIRST));
        assertEquals(true, fixture.model.getAttributeSet().getValue(SECOND));
        assertTrue(fixture.model.commits.isEmpty());
      }
    });
  }

  @Test
  void compatibleRowsCommitOnceDespiteSynchronousModelNotifications() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try (final var fixture = new Fixture()) {
        fixture.table.setRowSelectionInterval(1, 2);
        assertTrue(fixture.table.editCellAt(1, 1));
        ((JComboBox<?>) fixture.table.getEditorComponent()).setSelectedItem(true);
        assertEquals(1, fixture.model.commits.size());
        assertEquals(Map.of(FIRST, true, SECOND, true), fixture.model.commits.getFirst());
        assertFalse(fixture.table.isEditing());
      }
    });
  }

  @Test
  void openingAndLeavingMixedRowsDoesNotApplyAnImplicitDefault() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try (final var fixture = new Fixture()) {
        fixture.table.setRowSelectionInterval(1, 2);
        assertTrue(fixture.table.editCellAt(1, 1));
        assertTrue(fixture.table.getCellEditor().stopCellEditing());
        assertTrue(fixture.model.commits.isEmpty());
      }
    });
  }

  @Test
  void modelSwitchCommitsTextToOriginalModelExactlyOnce() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try (final var fixture = new Fixture()) {
        final var replacement = new Model();
        assertTrue(fixture.table.editCellAt(3, 1));
        ((JTextField) fixture.table.getEditorComponent()).setText("ScratchLabel");
        fixture.panel.setAttrTableModel(replacement);
        assertEquals("ScratchLabel", fixture.model.getAttributeSet().getValue(LABEL));
        assertEquals("", replacement.getAttributeSet().getValue(LABEL));
        assertEquals(1, fixture.model.commits.size());
        assertSame(replacement, fixture.panel.getAttrTableModel());
      }
    });
  }

  @Test
  void enterAndFocusTransferCommitTextButEscapeDiscardsIt() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try (final var fixture = new Fixture()) {
        assertTrue(fixture.table.editCellAt(3, 1));
        var field = (JTextField) fixture.table.getEditorComponent();
        field.setText("Entered");
        field.postActionEvent();
        assertEquals("Entered", fixture.model.getAttributeSet().getValue(LABEL));
        assertTrue(fixture.table.editCellAt(3, 1));
        field = (JTextField) fixture.table.getEditorComponent();
        field.setText("Transferred");
        for (final var listener : field.getFocusListeners()) {
          listener.focusLost(new FocusEvent(field, FocusEvent.FOCUS_LOST, false, new JTextField()));
        }
        assertEquals("Transferred", fixture.model.getAttributeSet().getValue(LABEL));
        assertTrue(fixture.table.editCellAt(3, 1));
        ((JTextField) fixture.table.getEditorComponent()).setText("Discarded");
        fixture.table.getCellEditor().cancelCellEditing();
        assertEquals("Transferred", fixture.model.getAttributeSet().getValue(LABEL));
        assertEquals(2, fixture.model.commits.size());
      }
    });
  }

  @Test
  void invalidDraftSurvivesModelSwitchAndCanBeCorrected() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try (final var fixture = new Fixture()) {
        assertTrue(fixture.table.editCellAt(4, 1));
        final var field = (JTextField) fixture.table.getEditorComponent();
        field.setText("not a number");
        fixture.panel.setAttrTableModel(new Model());
        assertTrue(fixture.table.isEditing());
        assertEquals("not a number", field.getText());
        assertSame(fixture.model, fixture.panel.getAttrTableModel());
        assertTrue(fixture.model.commits.isEmpty());
        field.setText("9");
        assertTrue(fixture.table.getCellEditor().stopCellEditing());
        assertEquals(9, fixture.model.getAttributeSet().getValue(NUMBER));
        assertEquals(1, fixture.model.commits.size());
      }
    });
  }

  @Test
  void ordinaryWheelForwardsBothColumnsWithoutEditing() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try (final var fixture = new Fixture()) {
        final var forwarded = new AtomicInteger();
        final var scroll =
            (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, fixture.table);
        scroll.addMouseWheelListener(event -> forwarded.incrementAndGet());
        fixture.wheel(1, 0, 0, -1);
        fixture.wheel(1, 1, 0, -1);
        assertEquals(2, forwarded.get());
        assertTrue(fixture.model.commits.isEmpty());
        assertEquals(false, fixture.model.getAttributeSet().getValue(FIRST));
      }
    });
  }

  @Test
  void explicitEnumWheelBatchesAndStaysWithOriginalModel() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try (final var fixture = new Fixture()) {
        // Boolean choices are [true, false]; downward rotation selects the previous option.
        fixture.wheel(1, 1, InputEvent.ALT_DOWN_MASK, 0.5);
        fixture.wheel(1, 1, InputEvent.ALT_DOWN_MASK, 0.5);
        assertTrue(fixture.model.commits.isEmpty());
        final var replacement = new Model();
        fixture.panel.setAttrTableModel(replacement);
        assertEquals(1, fixture.model.commits.size());
        assertEquals(true, fixture.model.getAttributeSet().getValue(FIRST));
        assertEquals(false, replacement.getAttributeSet().getValue(FIRST));
      }
    });
  }

  @Test
  void enumWheelAtBoundaryDoesNotCreateACommit() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try (final var fixture = new Fixture()) {
        fixture.wheel(1, 1, InputEvent.ALT_DOWN_MASK, -0.5);
        fixture.wheel(1, 1, InputEvent.ALT_DOWN_MASK, -0.5);
        fixture.panel.setAttrTableModel(new Model());
        assertTrue(fixture.model.commits.isEmpty());
        assertEquals(false, fixture.model.getAttributeSet().getValue(FIRST));
      }
    });
  }

  @Test
  void numericWheelKeepsAllDetentsInOneGesture() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try (final var fixture = new Fixture()) {
        fixture.wheel(4, 1, InputEvent.ALT_DOWN_MASK, -3);
        fixture.wheel(4, 1, InputEvent.ALT_DOWN_MASK, -2);
        assertTrue(fixture.model.commits.isEmpty());
        fixture.panel.setAttrTableModel(null);
        assertEquals(12, fixture.model.getAttributeSet().getValue(NUMBER));
        assertEquals(1, fixture.model.commits.size());
      }
    });
  }

  private static JTable findTable(Container container) {
    for (final var child : container.getComponents()) {
      if (child instanceof JTable table) return table;
      if (child instanceof Container nested) {
        final var found = findTable(nested);
        if (found != null) return found;
      }
    }
    return null;
  }

  private static final class Fixture implements AutoCloseable {
    final Model model = new Model();
    final AttrTable panel = new AttrTable(null);
    final JTable table;

    Fixture() {
      panel.setAttrTableModel(model);
      table = findTable(panel);
      table.setSize(300, 200);
      table.doLayout();
    }

    void wheel(int row, int column, int modifiers, double rotation) {
      final var cell = table.getCellRect(row, column, true);
      table.dispatchEvent(new MouseWheelEvent(table, MouseWheelEvent.MOUSE_WHEEL, 1, modifiers,
          cell.x + 2, cell.y + 2, 0, 0, 0, false, MouseWheelEvent.WHEEL_UNIT_SCROLL, 3,
          (int) rotation, rotation));
    }

    @Override
    public void close() {
      if (table.isEditing()) table.getCellEditor().cancelCellEditing();
      panel.setAttrTableModel(null);
      LocaleManager.removeLocaleListener(panel);
    }
  }

  private static final class Model extends AttributeSetTableModel {
    final List<Map<Attribute<Object>, Object>> commits = new ArrayList<>();

    Model() {
      super(AttributeSets.fixedSet(
          new Attribute<?>[] {FIRST, SECOND, LABEL, NUMBER}, new Object[] {false, true, "", 7}));
    }

    @Override
    public String getTitle() {
      return "Test properties";
    }

    @Override
    protected boolean supportsMultiEdit() {
      return true;
    }

    @Override
    protected void setValuesRequested(Map<Attribute<Object>, Object> values) {
      commits.add(Map.copyOf(values));
      values.forEach((attribute, value) -> getAttributeSet().setValue(attribute, value));
    }

    @Override
    protected void setValueRequested(Attribute<Object> attr, Object value) {
      throw new AssertionError("Batch commits must not call the single-attribute path");
    }
  }
}

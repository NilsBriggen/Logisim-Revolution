/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitAttributes;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.SplitterAttributes;
import com.cburch.logisim.circuit.SplitterFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.AttributeSets;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.generic.AttrTableModelRow;
import com.cburch.logisim.gui.generic.AttributeSetTableModel;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.gates.GatesLibrary;
import com.cburch.logisim.std.gates.NegateAttribute;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.tools.AddTool;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.swing.JComboBox;
import org.junit.jupiter.api.Test;

class AttrTableTransactionsTest {
  @Test
  void componentBatchUsesOneActionWithCompleteUndoAndRedo() throws Exception {
    final var fixture = new Fixture();
    final var gate = fixture.gate(0);
    final var model = new AttrTableComponentModel(fixture.project, fixture.circuit, gate);
    final var rows = negateRows(model);
    fixture.set(gate, negate(1), true);

    rows.getFirst().captureEdit(null, rows).commit(true);

    assertEquals(1, fixture.actions.size());
    assertEquals(true, gate.getAttributeSet().getValue(negate(0)));
    assertEquals(true, gate.getAttributeSet().getValue(negate(1)));
    fixture.actions.getFirst().undo(fixture.project);
    assertEquals(false, gate.getAttributeSet().getValue(negate(0)));
    assertEquals(true, gate.getAttributeSet().getValue(negate(1)));
    fixture.actions.getFirst().doIt(fixture.project);
    assertEquals(true, gate.getAttributeSet().getValue(negate(0)));
    assertEquals(true, gate.getAttributeSet().getValue(negate(1)));
    rows.getFirst().captureEdit(null, rows).commit(true);
    assertEquals(1, fixture.actions.size(), "No-op commits must not add undo actions");
  }

  @Test
  void selectionBatchRestoresEveryOriginalComponentValueWithOneUndo() throws Exception {
    final var fixture = new Fixture();
    final var first = fixture.gate(0);
    final var second = fixture.gate(100);
    fixture.set(second, negate(1), true);
    final var model = fixture.selectionModel(Set.of(first, second), first.getAttributeSet());
    final var rows = negateRows(model);

    rows.getFirst().captureEdit(null, rows).commit(true);

    assertEquals(1, fixture.actions.size());
    for (final var component : List.of(first, second)) {
      assertEquals(true, component.getAttributeSet().getValue(negate(0)));
      assertEquals(true, component.getAttributeSet().getValue(negate(1)));
    }
    fixture.actions.getFirst().undo(fixture.project);
    assertEquals(false, first.getAttributeSet().getValue(negate(0)));
    assertEquals(false, first.getAttributeSet().getValue(negate(1)));
    assertEquals(false, second.getAttributeSet().getValue(negate(0)));
    assertEquals(true, second.getAttributeSet().getValue(negate(1)));
    fixture.actions.getFirst().doIt(fixture.project);
    assertEquals(true, second.getAttributeSet().getValue(negate(0)));
  }

  @Test
  void capturedLabelEditFollowsOriginalComponentAfterSelectionBecomesEmpty() throws Exception {
    final var fixture = new Fixture();
    final var original = fixture.gate(0);
    final var model = fixture.selectionModel(Set.of(original), original.getAttributeSet());
    final var row = rowFor(model, StdAttr.LABEL);
    final var edit = row.captureEdit(null, List.of(row));
    when(fixture.selection.isEmpty()).thenReturn(true);
    when(fixture.selection.getComponents()).thenReturn(Set.of());
    model.updateAttributeSet();

    edit.commit("ScratchLabel");

    assertEquals("ScratchLabel", original.getAttributeSet().getValue(StdAttr.LABEL));
    assertEquals("test", fixture.circuit.getName());
    assertEquals(1, fixture.actions.size());
    fixture.actions.getFirst().undo(fixture.project);
    assertEquals("", original.getAttributeSet().getValue(StdAttr.LABEL));
  }

  @Test
  void capturedEditDoesNotFollowNewSelectionOrCircuit() throws Exception {
    final var fixture = new Fixture();
    final var original = fixture.gate(0);
    final var replacement = fixture.gate(100);
    final var model = fixture.selectionModel(Set.of(original), original.getAttributeSet());
    final var row = rowFor(model, StdAttr.FACING);
    final var edit = row.captureEdit(null, List.of(row));
    when(fixture.selection.getComponents()).thenReturn(Set.of(replacement));
    when(fixture.selection.getAttributeSet()).thenReturn(replacement.getAttributeSet());
    when(fixture.canvas.getCircuit()).thenReturn(new Circuit("replacement", fixture.file, null));
    model.updateAttributeSet();

    edit.commit(Direction.NORTH);

    assertEquals(Direction.NORTH, original.getAttributeSet().getValue(StdAttr.FACING));
    assertEquals(Direction.EAST, replacement.getAttributeSet().getValue(StdAttr.FACING));
    assertEquals(1, fixture.actions.size());
  }

  @Test
  void mixedFactoriesShareOneCapturedFacingEdit() throws Exception {
    final var fixture = new Fixture();
    final var gate = fixture.gate(0);
    final var pin = Pin.FACTORY.createComponent(
        Location.create(100, 0, true), Pin.FACTORY.createAttributeSet());
    fixture.add(pin);
    final var attrs =
        AttributeSets.fixedSet(new Attribute<?>[] {StdAttr.FACING}, new Object[] {null});
    final var model = fixture.selectionModel(Set.of(gate, pin), attrs);
    final var row = rowFor(model, StdAttr.FACING);
    final var originalGate = gate.getAttributeSet().getValue(StdAttr.FACING);
    final var originalPin = pin.getAttributeSet().getValue(StdAttr.FACING);

    row.captureEdit(null, List.of(row)).commit(Direction.NORTH);

    assertEquals(1, fixture.actions.size());
    assertEquals(Direction.NORTH, gate.getAttributeSet().getValue(StdAttr.FACING));
    assertEquals(Direction.NORTH, pin.getAttributeSet().getValue(StdAttr.FACING));
    fixture.actions.getFirst().undo(fixture.project);
    assertEquals(originalGate, gate.getAttributeSet().getValue(StdAttr.FACING));
    assertEquals(originalPin, pin.getAttributeSet().getValue(StdAttr.FACING));
  }

  @Test
  void circuitNoOpDoesNotAddUndoAction() throws Exception {
    final var fixture = new Fixture();
    final var model = new AttrTableCircuitModel(fixture.project, fixture.circuit);
    final var row = rowFor(model, CircuitAttributes.NAME_ATTR);
    row.captureEdit(null, List.of(row)).commit("test");
    assertTrue(fixture.actions.isEmpty());
    row.captureEdit(null, List.of(row)).commit("renamed");
    assertEquals(1, fixture.actions.size());
    fixture.actions.getFirst().undo(fixture.project);
    assertEquals("test", fixture.circuit.getName());
  }

  @Test
  void splitterOptionBatchUsesStoredValuesAndSuppressesRepeatAction() throws Exception {
    final var fixture = new Fixture();
    final var splitter = SplitterFactory.instance.createComponent(
        Location.create(0, 0, true), SplitterFactory.instance.createAttributeSet());
    fixture.add(splitter);
    final var model = new AttrTableComponentModel(fixture.project, fixture.circuit, splitter);
    final var rows = new ArrayList<AttrTableModelRow>();
    for (var i = 0; i < model.getRowCount(); i++) {
      if (model.getRow(i).getAttribute() instanceof SplitterAttributes.BitOutAttribute) {
        rows.add(model.getRow(i));
      }
    }
    final var originals =
        rows.stream().map(row -> splitter.getAttributeSet().getValue(row.getAttribute())).toList();
    final var box = (JComboBox<?>) rows.getFirst().getEditor(null);
    final var option = box.getItemAt(0);
    rows.getFirst().captureEdit(null, rows).commit(option);
    rows.getFirst().captureEdit(null, rows).commit(option);
    assertEquals(1, fixture.actions.size());
    fixture.actions.getFirst().undo(fixture.project);
    assertEquals(originals,
        rows.stream().map(row -> splitter.getAttributeSet().getValue(row.getAttribute())).toList());
  }

  private static NegateAttribute negate(int index) {
    // The fixture has two east-facing inputs, whose row attributes include their side.
    return new NegateAttribute(index, index == 0 ? Direction.NORTH : Direction.SOUTH);
  }

  private static List<AttrTableModelRow> negateRows(AttributeSetTableModel model) {
    return List.of(rowFor(model, negate(0)), rowFor(model, negate(1)));
  }

  private static AttrTableModelRow rowFor(AttributeSetTableModel model, Attribute<?> attribute) {
    for (var i = 0; i < model.getRowCount(); i++) {
      final var row = model.getRow(i);
      if (attribute.equals(row.getAttribute())) return row;
    }
    throw new AssertionError("Missing property: " + attribute);
  }

  private static final class Fixture {
    final Project project = mock(Project.class);
    final LogisimFile file = mock(LogisimFile.class);
    final Circuit circuit = new Circuit("test", file, null);
    final Selection selection = mock(Selection.class);
    final Canvas canvas = mock(Canvas.class);
    final List<Action> actions = new ArrayList<>();

    Fixture() {
      when(project.getLogisimFile()).thenReturn(file);
      when(file.contains(circuit)).thenReturn(true);
      doAnswer(invocation -> {
        final Action action = invocation.getArgument(0);
        action.doIt(project);
        actions.add(action);
        return null;
      })
          .when(project)
          .doAction(any(Action.class));
    }

    Component gate(int x) {
      final var factory = ((AddTool) new GatesLibrary().getTool("AND Gate")).getFactory();
      final var component = factory.createComponent(
          Location.create(x, 0, true), factory.createAttributeSet());
      add(component);
      return component;
    }

    void add(Component component) {
      final var mutation = new CircuitMutation(circuit);
      mutation.add(component);
      mutation.execute();
    }

    <V> void set(Component component, Attribute<V> attribute, V value) {
      final var mutation = new CircuitMutation(circuit);
      mutation.set(component, attribute, value);
      mutation.execute();
    }

    AttrTableSelectionModel selectionModel(Set<Component> components, AttributeSet attrs) {
      final var frame = mock(Frame.class);
      when(frame.getCanvas()).thenReturn(canvas);
      when(canvas.getCircuit()).thenReturn(circuit);
      when(canvas.getSelection()).thenReturn(selection);
      when(selection.isEmpty()).thenReturn(false);
      when(selection.getComponents()).thenReturn(components);
      when(selection.getAttributeSet()).thenReturn(attrs);
      return new AttrTableSelectionModel(project, frame);
    }
  }
}

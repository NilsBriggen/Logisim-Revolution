/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.tools;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.main.Selection;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;
import java.awt.Point;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

/** Placing and moving components without a mouse (Enter to place, arrows to nudge). */
class KeyboardPlacementTest {

  private final Project project;
  private final Circuit circuit;
  private final Canvas canvas = mock(Canvas.class);
  private final Selection selection;

  KeyboardPlacementTest() {
    final var file = LogisimFile.createNew(new Loader(null), null);
    project = new Project(file);
    circuit = file.getMainCircuit();
    circuit.setProject(project);
    project.setCurrentCircuit(circuit);
    selection = new Selection(project, canvas);
    when(canvas.getProject()).thenReturn(project);
    when(canvas.getCircuit()).thenReturn(circuit);
    when(canvas.getSelection()).thenReturn(selection);
  }

  @Test
  void enterPlacesTheComponentOnTheGridAtTheCentreOfTheView() {
    when(canvas.getVisibleCircuitCenter()).thenReturn(new Point(203, 148));
    final var tool = new AddTool(Pin.FACTORY);

    final var enter = key(KeyEvent.VK_ENTER, 0);
    tool.keyPressed(canvas, enter);

    assertTrue(enter.isConsumed());
    final var pin = onlyComponent();
    assertEquals(Location.create(200, 150, true), pin.getLocation());
  }

  @Test
  void shiftArrowMovesTheSelectionByLargeGridSteps() {
    final var pin = Pin.FACTORY.createComponent(
        Location.create(200, 150, true), Pin.FACTORY.createAttributeSet());
    final var mutation = new CircuitMutation(circuit);
    mutation.add(pin);
    mutation.execute();
    selection.add(pin);
    final var edit = new EditTool(new SelectTool(), new WiringTool());

    final var right = key(KeyEvent.VK_RIGHT, InputEvent.SHIFT_DOWN_MASK);
    edit.keyPressed(canvas, right);

    assertTrue(right.isConsumed());
    assertEquals(Location.create(200 + 10 * EditTool.LARGE_NUDGE_STEPS, 150, true),
        onlyComponent().getLocation());

    project.undoAction();
    assertEquals(Location.create(200, 150, true), onlyComponent().getLocation());
  }

  @Test
  void arrowOffsetsAreOneGridStepOrALargeStepWithShift() {
    assertEquals(new Point(0, -10), EditTool.nudgeOffset(KeyEvent.VK_UP, 0));
    assertEquals(new Point(-10, 0), EditTool.nudgeOffset(KeyEvent.VK_KP_LEFT, 0));
    assertEquals(new Point(0, 10 * EditTool.LARGE_NUDGE_STEPS),
        EditTool.nudgeOffset(KeyEvent.VK_DOWN, InputEvent.SHIFT_DOWN_MASK));
    assertNull(EditTool.nudgeOffset(KeyEvent.VK_A, 0));
  }

  private Component onlyComponent() {
    final var components = circuit.getNonWires();
    assertEquals(1, components.size());
    return components.iterator().next();
  }

  private static KeyEvent key(int code, int modifiers) {
    return new KeyEvent(new JPanel(), KeyEvent.KEY_PRESSED, 0, modifiers, code,
        KeyEvent.CHAR_UNDEFINED);
  }
}

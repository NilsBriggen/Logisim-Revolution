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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.tools.EditTool;
import com.cburch.logisim.tools.SelectTool;
import com.cburch.logisim.tools.WiringTool;
import java.awt.event.KeyEvent;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

/** Enter drops a floating paste, Escape throws it away (or clears an ordinary selection). */
class FloatingPasteKeysTest {

  private final Project project;
  private final Circuit circuit;
  private final Canvas canvas = mock(Canvas.class);
  private final Selection selection;
  private final EditTool edit = new EditTool(new SelectTool(), new WiringTool());

  FloatingPasteKeysTest() {
    final var file = LogisimFile.createNew(new Loader(null), null);
    file.stopAutosaveThread(false);
    project = new Project(file);
    circuit = file.getMainCircuit();
    circuit.setProject(project);
    project.setCurrentCircuit(circuit);
    selection = new Selection(project, canvas);
    when(canvas.getProject()).thenReturn(project);
    when(canvas.getCircuit()).thenReturn(circuit);
    when(canvas.getSelection()).thenReturn(selection);
  }

  private void floatPin() {
    selection.lifted.add(
        Pin.FACTORY.createComponent(
            Location.create(200, 150, true), Pin.FACTORY.createAttributeSet()));
    selection.fireSelectionChanged();
  }

  private static KeyEvent key(int code) {
    return new KeyEvent(new JPanel(), KeyEvent.KEY_PRESSED, 0, 0, code, KeyEvent.CHAR_UNDEFINED);
  }

  @Test
  void enterDropsTheFloatingPasteIntoTheCircuit() {
    floatPin();
    final var enter = key(KeyEvent.VK_ENTER);

    edit.keyPressed(canvas, enter);

    assertTrue(enter.isConsumed());
    assertTrue(selection.getFloatingComponents().isEmpty());
    assertEquals(1, circuit.getNonWires().size());
  }

  @Test
  void escapeThrowsTheFloatingPasteAway() {
    floatPin();
    final var escape = key(KeyEvent.VK_ESCAPE);

    edit.keyPressed(canvas, escape);

    assertTrue(escape.isConsumed());
    assertTrue(selection.isEmpty());
    assertEquals(0, circuit.getNonWires().size());
  }

  @Test
  void escapeClearsAnOrdinarySelectionAndEnterLeavesItAlone() {
    final var pin =
        Pin.FACTORY.createComponent(
            Location.create(200, 150, true), Pin.FACTORY.createAttributeSet());
    final var mutation = new CircuitMutation(circuit);
    mutation.add(pin);
    mutation.execute();
    selection.add(pin);

    // Enter with nothing floating is left to the other bindings.
    assertFalse(EditTool.commitFloating(canvas));
    assertFalse(selection.isEmpty(), "Enter with nothing floating does not touch the selection");

    final var escape = key(KeyEvent.VK_ESCAPE);
    edit.keyPressed(canvas, escape);
    assertTrue(escape.isConsumed());
    assertTrue(selection.isEmpty());
    assertEquals(1, circuit.getNonWires().size(), "deselecting keeps the component");
  }
}

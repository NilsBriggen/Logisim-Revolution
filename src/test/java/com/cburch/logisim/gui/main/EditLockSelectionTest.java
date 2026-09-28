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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.EditLockAction;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.tools.EditTool;
import com.cburch.logisim.tools.SelectTool;
import com.cburch.logisim.tools.WiringTool;
import java.awt.event.KeyEvent;
import java.util.List;
import javax.swing.JPanel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Deleting or moving a selection with a locked component in it is refused before the selection is
 * rearranged, so the selection still holds what it held and nothing in the circuit moved.
 */
class EditLockSelectionTest {

  private final Project project;
  private final Circuit circuit;
  private final Canvas canvas = mock(Canvas.class);
  private final Selection selection;
  private final Component locked;
  private final Component free;

  EditLockSelectionTest() {
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
    locked = pin(150);
    free = pin(250);
    final var xn = new CircuitMutation(circuit);
    xn.add(locked);
    xn.add(free);
    xn.execute();
    circuit.setComponentsEditLocked(List.of(locked), true);
    selection.add(locked);
    selection.add(free);
  }

  @AfterEach
  void tearDown() {
    project.getSimulator().shutDown();
  }

  @Test
  void deleteKeyLeavesALockedSelectionWhereItIs() {
    final var delete =
        new KeyEvent(
            new JPanel(), KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_DELETE, KeyEvent.CHAR_UNDEFINED);
    new EditTool(new SelectTool(), new WiringTool()).keyPressed(canvas, delete);

    assertTrue(circuit.contains(locked));
    assertTrue(circuit.contains(free), "one locked component keeps the whole deletion from happening");
    assertEquals(2, selection.getComponents().size(), "the selection is untouched");
    assertNull(project.getLastAction());
  }

  @Test
  void moveOfALockedSelectionIsRefused() {
    project.doAction(SelectionActions.translate(selection, 30, 0, null));

    assertTrue(circuit.contains(locked));
    assertEquals(Location.create(200, 150, true), locked.getLocation());
    assertTrue(selection.getComponents().contains(locked));
    assertNull(project.getLastAction());
  }

  @Test
  void onceUnlockedTheSelectionMovesAgain() {
    project.doAction(EditLockAction.setComponentsLocked(circuit, List.of(locked), false));
    project.doAction(SelectionActions.translate(selection, 30, 0, null));
    assertFalse(circuit.contains(locked), "the moved copy replaced it");
    assertEquals(2, circuit.getNonWires().size());
  }

  private static Component pin(int y) {
    return Pin.FACTORY.createComponent(
        Location.create(200, y, true), Pin.FACTORY.createAttributeSet());
  }
}

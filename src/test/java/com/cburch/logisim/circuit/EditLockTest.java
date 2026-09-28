/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.circuit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.file.LogisimFileActions;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.tools.SetAttributeAction;
import com.cburch.logisim.util.StringUtil;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * A lock must hold at the action level, whatever part of the interface asks for the edit: a
 * refused edit changes nothing, leaves the undo history and the dirty flag as they were, and the
 * simulation of a locked circuit still runs.
 */
class EditLockTest {

  private LogisimFile file;
  private Project project;
  private Circuit circuit;
  private Component locked;
  private Component free;

  @BeforeEach
  void setUp() {
    file = LogisimFile.createNew(new Loader(null), null);
    file.retireAutosaveThread();
    project = new Project(file);
    circuit = file.getMainCircuit();
    circuit.setProject(project);
    project.setCurrentCircuit(circuit);
    locked = pin("locked", 100);
    free = pin("free", 200);
    final var xn = new CircuitMutation(circuit);
    xn.add(locked);
    xn.add(free);
    xn.execute();
    project.doAction(EditLockAction.setComponentsLocked(circuit, List.of(locked), true));
  }

  @AfterEach
  void tearDown() {
    project.getSimulator().shutDown();
  }

  @Test
  void lockingIsAnUndoableEditThatMarksTheProjectDirty() {
    assertTrue(circuit.isComponentEditLocked(locked));
    assertFalse(circuit.isComponentEditLocked(free));
    assertTrue(project.isFileDirty());

    project.undoAction();
    assertFalse(circuit.isComponentEditLocked(locked));
    assertFalse(project.isFileDirty());

    project.redoAction();
    assertTrue(circuit.isComponentEditLocked(locked));
  }

  @Test
  void lockedComponentCannotBeDeleted() {
    final var xn = new CircuitMutation(circuit);
    xn.remove(locked);
    assertRefused(xn.toAction(null));
    assertTrue(circuit.contains(locked));
  }

  @Test
  void lockedComponentCannotBeMoved() {
    final var moved = pin("locked", 300);
    final var xn = new CircuitMutation(circuit);
    xn.replace(locked, moved);
    assertRefused(xn.toAction(null));
    assertTrue(circuit.contains(locked));
    assertFalse(circuit.contains(moved));
  }

  @Test
  void lockedComponentCannotBeRotatedOrHaveAttributesEdited() {
    final var rotate = new SetAttributeAction(circuit, StringUtil.constantGetter("rotate"));
    rotate.set(locked, StdAttr.FACING, Direction.NORTH);
    assertRefused(rotate);
    assertEquals(Direction.EAST, locked.getAttributeSet().getValue(StdAttr.FACING));

    final var relabel = new SetAttributeAction(circuit, StringUtil.constantGetter("relabel"));
    relabel.set(locked, StdAttr.LABEL, "renamed");
    assertRefused(relabel);
    assertEquals("locked", locked.getAttributeSet().getValue(StdAttr.LABEL));
  }

  @Test
  void anUnlockedComponentBesideALockedOneStaysEditable() {
    final var relabel = new SetAttributeAction(circuit, StringUtil.constantGetter("relabel"));
    relabel.set(free, StdAttr.LABEL, "renamed");
    project.doAction(relabel);
    assertEquals("renamed", free.getAttributeSet().getValue(StdAttr.LABEL));
    assertSame(relabel, project.getLastAction());
  }

  @Test
  void oneLockedComponentRefusesTheWholeEdit() {
    // Deleting both must not delete the unlocked one and leave the locked one behind.
    final var xn = new CircuitMutation(circuit);
    xn.removeAll(List.of(free, locked));
    assertRefused(xn.toAction(null));
    assertTrue(circuit.contains(free));
    assertTrue(circuit.contains(locked));
  }

  @Test
  void lockedCircuitRefusesAddingAndRemovingComponentsAndWires() {
    project.doAction(EditLockAction.setCircuitLocked(circuit, true));
    assertTrue(circuit.isEditLocked());

    final var add = new CircuitMutation(circuit);
    add.add(pin("new", 300));
    assertRefused(add.toAction(null));

    final var wire = new CircuitMutation(circuit);
    wire.add(Wire.create(Location.create(300, 300, false), Location.create(400, 300, false)));
    assertRefused(wire.toAction(null));
    assertTrue(circuit.getWires().isEmpty());

    final var remove = new CircuitMutation(circuit);
    remove.remove(free);
    assertRefused(remove.toAction(null));
    assertTrue(circuit.contains(free));

    final var relabel = new SetAttributeAction(circuit, StringUtil.constantGetter("relabel"));
    relabel.set(free, StdAttr.LABEL, "renamed");
    assertRefused(relabel);
    assertEquals("free", free.getAttributeSet().getValue(StdAttr.LABEL));
  }

  @Test
  void lockedCircuitCannotBeRenamedOrRemoved() {
    final var other = new Circuit("other", file, project);
    file.addCircuit(other);
    project.doAction(EditLockAction.setCircuitLocked(other, true));

    assertRefused(LogisimFileActions.renameCircuit(other, "renamed"));
    assertEquals("other", other.getName());

    assertRefused(LogisimFileActions.removeCircuit(other));
    assertTrue(file.contains(other));
  }

  @Test
  void unlockingMakesTheCircuitEditableAgain() {
    project.doAction(EditLockAction.setCircuitLocked(circuit, true));
    project.doAction(EditLockAction.setCircuitLocked(circuit, false));
    final var add = new CircuitMutation(circuit);
    final var added = pin("new", 300);
    add.add(added);
    project.doAction(add.toAction(null));
    assertTrue(circuit.contains(added));
  }

  @Test
  void refusedEditLeavesHistoryAndDirtyStateAlone() {
    final var relabel = new SetAttributeAction(circuit, StringUtil.constantGetter("relabel"));
    relabel.set(free, StdAttr.LABEL, "renamed");
    project.doAction(relabel);
    project.undoAction();
    assertTrue(project.getCanRedo());
    final var dirty = project.isFileDirty();

    final var xn = new CircuitMutation(circuit);
    xn.remove(locked);
    assertRefused(xn.toAction(null));

    assertEquals(dirty, project.isFileDirty());
    assertTrue(project.getCanRedo(), "a refused edit must not throw the redo history away");
    project.redoAction();
    assertEquals("renamed", free.getAttributeSet().getValue(StdAttr.LABEL));
  }

  @Test
  void mutationOutsideAnyProjectIsRefusedToo() {
    final var xn = new CircuitMutation(circuit);
    xn.remove(locked);
    final var refusal = assertThrows(EditLockedException.class, xn::execute);
    assertSame(locked, refusal.getComponent());
    assertTrue(refusal.getMessage().contains("locked"), refusal.getMessage());
    assertTrue(circuit.contains(locked));
  }

  @Test
  void programChangesMayPassALockOnPurpose() {
    final var xn = new CircuitMutation(circuit).ignoringEditLocks();
    xn.set(locked, StdAttr.LABEL, "system");
    xn.execute();
    assertEquals("system", locked.getAttributeSet().getValue(StdAttr.LABEL));
  }

  @Test
  void inputsOfALockedCircuitCanStillBePoked() {
    project.doAction(EditLockAction.setCircuitLocked(circuit, true));
    final var state = CircuitState.createRootState(project, circuit, Thread.currentThread());
    state.getPropagator().propagate();

    final var in = state.getInstanceState(free);
    Pin.FACTORY.driveInputPin(in, Value.TRUE);
    Pin.FACTORY.propagate(in);
    state.getPropagator().propagate();

    assertEquals(Value.TRUE, state.getValue(free.getLocation()));
  }

  @Test
  void undoOfTheEditBeforeTheLockStillWorksOnceTheLockIsUndone() {
    final var xn = new CircuitMutation(circuit);
    final var added = pin("new", 300);
    xn.add(added);
    project.doAction(xn.toAction(null));
    project.doAction(EditLockAction.setCircuitLocked(circuit, true));

    project.undoAction(); // the lock
    project.undoAction(); // the addition
    assertFalse(circuit.isEditLocked());
    assertFalse(circuit.contains(added));
  }

  @Test
  void lockActionsSkipWhatIsAlreadyInTheRequestedState() {
    assertNull(EditLockAction.setComponentsLocked(circuit, List.of(locked), true));
    assertNull(EditLockAction.setCircuitLocked(circuit, false));
    assertTrue(EditLockAction.allLocked(circuit, List.of(locked)));
    assertFalse(EditLockAction.allLocked(circuit, List.of(locked, free)));
  }

  /** Does {@code action} through the project and checks that it was turned down cleanly. */
  private void assertRefused(com.cburch.logisim.proj.Action action) {
    final var before = project.getLastAction();
    final var dirty = project.isFileDirty();
    project.doAction(action);
    assertSame(before, project.getLastAction(), "a refused edit must not enter the undo history");
    assertEquals(dirty, project.isFileDirty(), "a refused edit must not make the project dirty");
  }

  private static Component pin(String label, int y) {
    final var attrs = Pin.FACTORY.createAttributeSet();
    attrs.setValue(StdAttr.LABEL, label);
    return Pin.FACTORY.createComponent(Location.create(100, y, false), attrs);
  }
}

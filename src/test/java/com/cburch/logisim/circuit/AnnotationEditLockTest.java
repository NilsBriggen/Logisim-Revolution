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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class AnnotationEditLockTest {
  private LogisimFile file;
  private Project project;
  private Circuit circuit;

  @BeforeEach
  void setUp() {
    file = LogisimFile.createNew(new Loader(null), null);
    file.retireAutosaveThread();
    project = new Project(file);
    circuit = file.getMainCircuit();
    circuit.setProject(project);
    project.setCurrentCircuit(circuit);
  }

  @AfterEach
  void tearDown() {
    project.getSimulator().shutDown();
  }

  @Test
  void lockedChildRefusesAnnotationBeforeAnyParentLabelChanges() {
    final var parentPin = addPin(circuit, "", 100);
    final var child = new Circuit("child", file, project);
    file.addCircuit(child);
    final var childPin = addPin(child, "", 100);
    final var factory = child.getSubcircuitFactory();
    final var add = new CircuitMutation(circuit);
    add.add(factory.createComponent(Location.create(200, 200, false), factory.createAttributeSet()));
    add.execute();
    child.setEditLocked(true);
    final var history = project.getLastAction();

    final var refusal = assertThrows(EditLockedException.class, () -> circuit.annotate(false, false));
    assertSame(child, refusal.getCircuit());
    assertEquals("", parentPin.getAttributeSet().getValue(StdAttr.LABEL));
    assertEquals("", childPin.getAttributeSet().getValue(StdAttr.LABEL));
    assertSame(history, project.getLastAction());
    assertFalse(project.isFileDirty());

    child.setEditLocked(false);
    circuit.annotate(false, false);
    assertFalse(parentPin.getAttributeSet().getValue(StdAttr.LABEL).isEmpty());
    assertFalse(childPin.getAttributeSet().getValue(StdAttr.LABEL).isEmpty());
  }

  @Test
  void clearingLabelsChecksLockedComponentsBeforeClearingAnyLabel() {
    final var first = addPin(circuit, "first", 100);
    final var second = addPin(circuit, "second", 200);
    circuit.setComponentsEditLocked(List.of(second), true);
    assertThrows(EditLockedException.class, () -> circuit.annotate(true, false));
    assertEquals("first", first.getAttributeSet().getValue(StdAttr.LABEL));
    assertEquals("second", second.getAttributeSet().getValue(StdAttr.LABEL));
    assertFalse(project.isFileDirty());
  }

  @Test
  void validLockedLabelDoesNotPreventAnnotatingUnlockedComponents() {
    final var locked = addPin(circuit, "fixed_pin", 100);
    final var free = addPin(circuit, "", 200);
    circuit.setComponentsEditLocked(List.of(locked), true);
    circuit.annotate(false, false);
    assertEquals("fixed_pin", locked.getAttributeSet().getValue(StdAttr.LABEL));
    assertFalse(free.getAttributeSet().getValue(StdAttr.LABEL).isEmpty());
    assertTrue(project.isFileDirty());
  }

  @ParameterizedTest
  @ValueSource(strings = {"invalid@label", "first"})
  void invalidAndDuplicateLockedLabelsArePreflighted(String label) {
    // Retain the labels as a file reader does, before interactive validation is installed.
    circuit.setLabelChecksDeferred(true);
    final var first = addPin(circuit, "first", 100);
    final var locked = addPin(circuit, label, 200);
    circuit.setLabelChecksDeferred(false);
    circuit.setComponentsEditLocked(List.of(locked), true);
    assertThrows(EditLockedException.class, () -> circuit.annotate(false, false));
    assertEquals(label, locked.getAttributeSet().getValue(StdAttr.LABEL));
    assertEquals("first", first.getAttributeSet().getValue(StdAttr.LABEL));
    assertFalse(project.isFileDirty());
  }

  private Component addPin(Circuit target, String label, int y) {
    final var attrs = Pin.FACTORY.createAttributeSet();
    attrs.setValue(StdAttr.LABEL, label);
    final var pin = Pin.FACTORY.createComponent(Location.create(100, y, false), attrs);
    final var add = new CircuitMutation(target);
    add.add(pin);
    add.execute();
    return pin;
  }
}

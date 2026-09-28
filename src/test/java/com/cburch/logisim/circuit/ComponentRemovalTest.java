/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.circuit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.memory.Ram;
import com.cburch.logisim.util.StringUtil;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Removing a component must complete even when its factory's cleanup hook cannot. */
class ComponentRemovalTest {

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
  void ramWithoutSimulationStateCanBeRemovedAndRedone() {
    final var ramFactory = new Ram();
    final var ram =
        ramFactory.createComponent(Location.create(100, 100, true), ramFactory.createAttributeSet());
    add(ram);
    // Never propagated, so the RAM has no RamState: the removal hook must cope with that.
    assertNull(project.getCircuitState(circuit).getData(ram));

    deleteUndoRedo(ram);

    assertFalse(circuit.contains(ram));
    assertTrue(project.isFileDirty());
    assertTrue(file.isDirty());
  }

  @Test
  void failingRemovalHookDoesNotLeaveTheCircuitHalfMutated() {
    final var factory = new ThrowingOnRemoveFactory();
    final var comp =
        factory.createComponent(Location.create(200, 200, true), factory.createAttributeSet());
    add(comp);
    final var removed = new ArrayList<Component>();
    circuit.addCircuitListener(
        event -> {
          if (event.getAction() == CircuitEvent.ACTION_REMOVE) {
            removed.add((Component) event.getData());
          }
        });

    final var mutation = new CircuitMutation(circuit);
    mutation.remove(comp);
    project.doAction(mutation.toAction(StringUtil.constantGetter("delete")));

    assertFalse(circuit.contains(comp));
    assertFalse(circuit.getNonWires().contains(comp));
    assertTrue(removed.contains(comp), "listeners must hear about the removal");
    assertTrue(project.isFileDirty());
  }

  private void add(Component comp) {
    final var mutation = new CircuitMutation(circuit);
    mutation.add(comp);
    mutation.execute();
  }

  private void deleteUndoRedo(Component comp) {
    final var mutation = new CircuitMutation(circuit);
    mutation.remove(comp);
    project.doAction(mutation.toAction(StringUtil.constantGetter("delete")));
    assertFalse(circuit.contains(comp));
    project.undoAction();
    assertTrue(circuit.contains(comp));
    project.redoAction();
  }

  private static final class ThrowingOnRemoveFactory extends InstanceFactory {
    ThrowingOnRemoveFactory() {
      super("ThrowingOnRemove");
      setOffsetBounds(Bounds.create(-10, -10, 20, 20));
    }

    @Override
    public void paintInstance(InstancePainter painter) {
      // not drawn in this test
    }

    @Override
    public void propagate(InstanceState state) {
      // no ports
    }

    @Override
    public void removeComponent(Circuit circ, Component c, CircuitState state) {
      throw new IllegalStateException("simulated cleanup failure");
    }
  }
}

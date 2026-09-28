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

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SimulationTreeCircuitNodeTest {

  @Test
  void subcircuitsAreListedByLabelOrPositionInNumericOrder() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var file = LogisimFile.createNew(new Loader(null), null);
      final var project = new Project(file);
      try {
        final var main = file.getMainCircuit();
        final var sub = new Circuit("adder", file, project);
        file.addCircuit(sub);
        final var factory = sub.getSubcircuitFactory();
        final var mutation = new CircuitMutation(main);
        final var far = factory.createComponent(
            Location.create(100, 20, true), factory.createAttributeSet());
        final var near = factory.createComponent(
            Location.create(20, 20, true), factory.createAttributeSet());
        final var lower = factory.createComponent(
            Location.create(20, 200, true), factory.createAttributeSet());
        final var labelled = factory.createComponent(
            Location.create(300, 300, true), factory.createAttributeSet());
        labelled.getAttributeSet().setValue(StdAttr.LABEL, "ALU");
        mutation.add(far);
        mutation.add(near);
        mutation.add(lower);
        mutation.add(labelled);
        mutation.execute();

        assertEquals("ALU (adder)", SimulationTreeCircuitNode.displayName(labelled));
        assertEquals("adder @ 100,20", SimulationTreeCircuitNode.displayName(far));

        final var model = new SimulationTreeModel(project.getRootCircuitStates());
        final var root = (SimulationTreeCircuitNode) model.getChild(model.getRoot(), 0);
        final var names = new ArrayList<String>();
        for (var i = 0; i < root.getChildCount(); i++) {
          final var child = root.getChildAt(i);
          if (child instanceof SimulationTreeCircuitNode node
              && node.getComponentFactory() instanceof SubcircuitFactory) {
            names.add(child.toString());
          }
        }
        assertEquals(
            List.of("adder @ 20,20", "adder @ 100,20", "adder @ 20,200", "ALU (adder)"), names);
      } finally {
        project.getSimulator().shutDown();
      }
    });
  }
}

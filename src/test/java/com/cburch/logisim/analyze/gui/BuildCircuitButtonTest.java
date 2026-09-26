/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.analyze.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import java.util.List;
import java.util.Set;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class BuildCircuitButtonTest {
  @Test
  void generatedNameAvoidsExistingCircuitsAndSignalsIgnoringCase() {
    assertEquals("main_logic_3", BuildCircuitButton.uniqueCircuitName(
        "main_logic", Set.of("main", "MAIN_LOGIC", "main_logic_2")));
  }

  @Test
  void availableNameIsNotChanged() {
    assertEquals("logic", BuildCircuitButton.uniqueCircuitName("logic", Set.of("main")));
  }

  @Test
  void replaceConfirmationNamesTheCircuitsThatUseTheTarget() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var file = LogisimFile.createNew(new Loader(null), null);
      final var project = new Project(file);
      try {
        final var main = file.getMainCircuit();
        final var sub = new Circuit("sub", file, project);
        file.addCircuit(sub);
        assertEquals(List.of(), BuildCircuitButton.circuitsUsing(sub));
        final var mutation = new CircuitMutation(main);
        mutation.add(sub.getSubcircuitFactory().createComponent(
            Location.create(100, 100, true), sub.getSubcircuitFactory().createAttributeSet()));
        mutation.add(sub.getSubcircuitFactory().createComponent(
            Location.create(300, 100, true), sub.getSubcircuitFactory().createAttributeSet()));
        mutation.execute();
        assertEquals(List.of(main.getName()), BuildCircuitButton.circuitsUsing(sub));
      } finally {
        project.getSimulator().shutDown();
      }
    });
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.memory;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import java.awt.image.BufferedImage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** A memory that was never propagated (e.g. restored by Undo with Auto-Propagate off). */
class MemPaintStateTest {

  @ParameterizedTest
  @ValueSource(strings = {"ram", "rom", "dualram"})
  void paintingCreatesMissingMemoryState(String kind) {
    final var file = LogisimFile.createNew(new Loader(null), null);
    final var project = new Project(file);
    final var circuit = file.getMainCircuit();
    circuit.setProject(project);
    project.setCurrentCircuit(circuit);
    final ComponentFactory factory =
        switch (kind) {
          case "ram" -> new Ram();
          case "rom" -> new Rom();
          default -> new DualRam();
        };
    final var mem = factory.createComponent(Location.create(100, 100, true), factory.createAttributeSet());
    final var mutation = new CircuitMutation(circuit);
    mutation.add(mem);
    mutation.execute();
    final var state = project.getCircuitState();
    assertNull(state.getData(mem));

    final var image = new BufferedImage(400, 400, BufferedImage.TYPE_INT_RGB);
    final var g = image.createGraphics();
    try {
      mem.draw(new ComponentDrawContext(null, circuit, state, g, g));
    } finally {
      g.dispose();
    }

    assertInstanceOf(MemState.class, state.getData(mem));
  }
}

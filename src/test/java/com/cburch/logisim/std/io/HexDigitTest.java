/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.io;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.InstanceDataSingleton;
import com.cburch.logisim.proj.Project;
import org.junit.jupiter.api.Test;

class HexDigitTest {

  /** An undriven input used to read as 0 and light the "0" glyph. */
  @Test
  void unconnectedDisplayStaysBlank() {
    final var file = LogisimFile.createNew(new Loader(null), null);
    final var project = new Project(file);
    final var circuit = file.getMainCircuit();
    circuit.setProject(project);
    final var factory = new HexDigit();
    final var digit = factory.createComponent(Location.create(100, 100, true), factory.createAttributeSet());
    final var mutation = new CircuitMutation(circuit);
    mutation.add(digit);
    mutation.execute();

    final var state = CircuitState.createRootState(project, circuit, Thread.currentThread());
    state.getPropagator().propagate();

    final var data = (InstanceDataSingleton) state.getData(digit);
    assertEquals(0, data.getValue());
  }
}

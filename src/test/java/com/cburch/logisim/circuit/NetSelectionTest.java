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

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Tunnel;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NetSelectionTest {

  private static Location at(int x, int y) {
    return Location.create(x, y, false);
  }

  private static Component tunnel(int x, int y, String label) {
    final var attrs = Tunnel.FACTORY.createAttributeSet();
    attrs.setValue(StdAttr.LABEL, label);
    return Tunnel.FACTORY.createComponent(at(x, y), attrs);
  }

  @Test
  void followsConnectedWiresAndSameNamedTunnels() {
    final var file = LogisimFile.createNew(new Loader(null), null);
    final var project = new Project(file);
    final var circuit = file.getMainCircuit();
    circuit.setProject(project);
    // Keep the simulator from rebuilding connectivity while the net is read.
    project.getSimulator().setAutoPropagation(false);
    try {
      final var ab = Wire.create(at(100, 100), at(200, 100));
      final var bc = Wire.create(at(200, 100), at(200, 200));
      final var unrelated = Wire.create(at(400, 100), at(500, 100));
      final var t1 = tunnel(200, 200, "data");
      final var t2 = tunnel(300, 300, "data");
      final var other = tunnel(500, 100, "clk");
      final var far = Wire.create(at(300, 300), at(300, 400));
      final var xn = new CircuitMutation(circuit);
      for (final var c : new Component[] {ab, bc, unrelated, t1, t2, other, far}) xn.add(c);
      xn.execute();

      assertEquals(Set.of(ab, bc, t1, t2, far), NetSelection.netOf(circuit, ab));
      assertEquals(Set.of(unrelated, other), NetSelection.netOf(circuit, unrelated));
    } finally {
      project.getSimulator().shutDown();
    }
  }
}

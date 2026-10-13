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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.std.wiring.Tunnel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class LabelReplaceTest {

  private final Project project;
  private final Circuit circuit;

  LabelReplaceTest() {
    final var file = LogisimFile.createNew(new Loader(null), null);
    project = new Project(file);
    circuit = file.getMainCircuit();
    circuit.setProject(project);
    final var xn = new CircuitMutation(circuit);
    xn.add(labelled(Pin.FACTORY, 100, "in_a"));
    xn.add(labelled(Pin.FACTORY, 200, "in_b"));
    xn.add(labelled(Pin.FACTORY, 300, "out"));
    xn.add(labelled(Tunnel.FACTORY, 400, "bus"));
    xn.add(labelled(Tunnel.FACTORY, 500, "bus"));
    xn.execute();
  }

  @AfterEach
  void shutDown() {
    project.getSimulator().shutDown();
  }

  private static Component labelled(
      com.cburch.logisim.instance.InstanceFactory factory, int y, String label) {
    final var attrs = factory.createAttributeSet();
    attrs.setValue(StdAttr.LABEL, label);
    return factory.createComponent(Location.create(100, y, false), attrs);
  }

  private Map<String, String> run(String find, String replace, boolean regex) {
    final var result = new HashMap<String, String>();
    for (final var m :
        LabelReplace.find(List.of(circuit), LabelReplace.pattern(find, regex, true), replace, regex)) {
      result.put(m.oldLabel() + "->" + m.newLabel(), m.problem());
    }
    return result;
  }

  @Test
  void literalReplacementRenamesEveryMatch() {
    final var r = run("in_", "x_", false);
    assertEquals(2, r.size());
    assertNull(r.get("in_a->x_a"));
    assertNull(r.get("in_b->x_b"));
  }

  @Test
  void refusesLabelsTakenOrDuplicatedOrInvalid() {
    assertTrue(run("in_a", "out", false).get("in_a->out") != null, "taken by another pin");

    final var dup = run("in_.", "same", true);
    assertEquals(2, dup.size());
    assertEquals(1, dup.values().stream().filter(p -> p == null).count(), "first one wins");

    assertTrue(run("out", "1bad", false).get("out->1bad") != null, "not a valid name");
  }

  @Test
  void tunnelsMayShareTheirNewLabelAndGroupsWork() {
    final var tunnels = run("bus", "data", false);
    assertEquals(1, tunnels.size(), "both tunnels give the same key");
    assertNull(tunnels.get("bus->data"));
    assertNull(run("in_(.)", "pin_$1", true).get("in_a->pin_a"));
  }
}

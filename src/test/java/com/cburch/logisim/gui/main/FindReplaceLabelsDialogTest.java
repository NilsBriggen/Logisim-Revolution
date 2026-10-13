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
import static org.junit.jupiter.api.Assertions.assertNull;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.LabelReplace;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

class FindReplaceLabelsDialogTest {

  private static TreeSet<String> labels(Circuit... circuits) {
    final var ret = new TreeSet<String>();
    for (final var c : circuits) {
      for (final var comp : c.getNonWires()) ret.add(comp.getAttributeSet().getValue(StdAttr.LABEL));
    }
    return ret;
  }

  private static void addPin(Circuit circuit, String label) {
    final var attrs = Pin.FACTORY.createAttributeSet();
    attrs.setValue(StdAttr.LABEL, label);
    final var xn = new CircuitMutation(circuit);
    xn.add(Pin.FACTORY.createComponent(Location.create(100, 100, false), attrs));
    xn.execute();
  }

  @Test
  void replacementAcrossCircuitsIsOneUndoStep() {
    final var file = LogisimFile.createNew(new Loader(null), null);
    final var project = new Project(file);
    try {
      final var main = file.getMainCircuit();
      main.setProject(project);
      final var sub = new Circuit("sub", file, project);
      file.addCircuit(sub);
      addPin(main, "clk_main");
      addPin(sub, "clk_sub");

      final var matches =
          LabelReplace.find(
              List.of(main, sub), LabelReplace.pattern("clk", false, true), "clock", false);
      project.doAction(FindReplaceLabelsDialog.toAction(new ArrayList<>(matches)));
      assertEquals(new TreeSet<>(List.of("clock_main", "clock_sub")), labels(main, sub));

      project.undoAction();
      assertEquals(new TreeSet<>(List.of("clk_main", "clk_sub")), labels(main, sub));
      assertNull(FindReplaceLabelsDialog.toAction(List.of()));
    } finally {
      project.getSimulator().shutDown();
    }
  }
}

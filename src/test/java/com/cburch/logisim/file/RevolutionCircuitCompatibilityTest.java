/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.gates.GatesLibrary;
import com.cburch.logisim.tools.AddTool;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RevolutionCircuitCompatibilityTest {
  @TempDir Path temporary;

  @Test
  void gateLabelAndSerializedIdentitySurviveSavingAndReopening() throws Exception {
    final var loader = new Loader(null);
    final var file = LogisimFile.createNew(loader, null);
    final var gates = loader.getBuiltin().getLibrary(GatesLibrary._ID);
    file.addLibrary(gates);
    final var factory = ((AddTool) gates.getTool("NOT Gate")).getFactory();
    final var attributes = factory.createAttributeSet();
    attributes.setValue(StdAttr.LABEL, "long_gate_label_012345");
    final var location = Location.create(200, 150, false);
    final var gate = factory.createComponent(location, attributes);
    final var mutation = new CircuitMutation(file.getMainCircuit());
    mutation.add(gate);
    mutation.execute();
    final var path = temporary.resolve("revolution-compatible.circ").toFile();

    assertTrue(loader.save(file, path));
    final var xml = Files.readString(path.toPath());
    assertTrue(xml.contains("name=\"NOT Gate\""));
    assertTrue(xml.contains("long_gate_label_012345"));

    final var reopened = new Loader(null).openLogisimFile(path);
    final var reloaded = reopened.getMainCircuit().getNonWires().iterator().next();
    assertEquals(factory.getName(), reloaded.getFactory().getName());
    assertEquals(location, reloaded.getLocation());
    assertEquals("long_gate_label_012345",
        reloaded.getAttributeSet().getValue(StdAttr.LABEL));
  }
}

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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.cburch.logisim.Main;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.fpga.hdlgenerator.HdlGeneratorFactory;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.std.wiring.Pin;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CircuitNameAndRepairTest {
  private final String originalHdlType = AppPreferences.HdlType.get();
  private final boolean wasHeadless = Main.headless;
  private LogisimFile file;

  @BeforeEach
  void setUp() {
    Main.headless = true;
    AppPreferences.HdlType.set(HdlGeneratorFactory.VHDL);
    file = LogisimFile.createNew(new Loader(null), null);
    file.retireAutosaveThread();
  }

  @AfterEach
  void restore() {
    AppPreferences.HdlType.set(originalHdlType);
    Main.headless = wasHeadless;
  }

  @Test
  void validatorExplainsEveryRefusalWithoutPatterns() {
    assertNull(CircuitNameValidator.problemWith(file, "adder", null));
    final var problems =
        List.of(
            CircuitNameValidator.problemWith(file, "", null),
            CircuitNameValidator.problemWith(file, "entity", null),
            CircuitNameValidator.problemWith(file, "my adder", null),
            CircuitNameValidator.problemWith(file, "MAIN", null));
    for (final var problem : problems) {
      assertNotNull(problem);
      assertFalse(problem.contains("\\w") || problem.contains("[a-z"), problem);
    }
    assertEquals(4, new HashSet<>(problems).size(), "each refusal says what is wrong");
  }

  @Test
  void renamingACircuitMayKeepItsOwnNameButNotTakeAPinLabel() {
    final var main = file.getMainCircuit();
    assertNull(CircuitNameValidator.problemWith(file, "main", main));

    final var attrs = Pin.FACTORY.createAttributeSet();
    attrs.setValue(StdAttr.LABEL, "result");
    final var mutation = new CircuitMutation(main);
    mutation.add(Pin.FACTORY.createComponent(Location.create(100, 100, false), attrs));
    mutation.execute();

    assertNotNull(CircuitNameValidator.problemWith(file, "result", main));
    assertNull(CircuitNameValidator.problemWith(file, "result", null));
  }

  @Test
  void hdlSafeLabelKeepsWhatItCan() {
    assertEquals("caf", Circuit.hdlSafeLabel("café"));
    assertEquals("my_out", Circuit.hdlSafeLabel("my  out"));
    assertEquals("L_1st", Circuit.hdlSafeLabel("1st"));
  }

  @Test
  void wiresAreSplitWhereAnotherWireEnds() {
    final var circuit = file.getMainCircuit();
    final var mutation = new CircuitMutation(circuit);
    // A horizontal wire with a vertical one ending on its middle, and a disjoint wire.
    mutation.add(Wire.create(Location.create(0, 100, false), Location.create(200, 100, false)));
    mutation.add(Wire.create(Location.create(80, 0, false), Location.create(80, 100, false)));
    mutation.add(Wire.create(Location.create(0, 300, false), Location.create(200, 300, false)));
    mutation.execute();

    final var ends = new HashSet<List<Integer>>();
    for (final var wire : circuit.getWires()) {
      ends.add(
          List.of(
              wire.getEnd0().getX(), wire.getEnd0().getY(),
              wire.getEnd1().getX(), wire.getEnd1().getY()));
    }
    assertEquals(
        new HashSet<>(
            List.of(
                List.of(0, 100, 80, 100),
                List.of(80, 100, 200, 100),
                List.of(80, 0, 80, 100),
                List.of(0, 300, 200, 300))),
        ends);
  }
}

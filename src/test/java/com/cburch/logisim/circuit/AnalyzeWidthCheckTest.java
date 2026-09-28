/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.circuit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** A width-mismatched circuit used to be analysed silently as all don't-care. */
class AnalyzeWidthCheckTest {

  private static Circuit inputWiredToOutput(int inputWidth, int outputWidth) {
    final var file = LogisimFile.createNew(new Loader(null), null);
    final var project = new Project(file);
    try {
      final var circuit = file.getMainCircuit();
      final var inputAttrs = Pin.FACTORY.createAttributeSet();
      inputAttrs.setValue(StdAttr.LABEL, "a");
      inputAttrs.setValue(StdAttr.WIDTH, BitWidth.create(inputWidth));
      final var outputAttrs = Pin.FACTORY.createAttributeSet();
      outputAttrs.setValue(StdAttr.LABEL, "y");
      outputAttrs.setValue(StdAttr.WIDTH, BitWidth.create(outputWidth));
      outputAttrs.setValue(Pin.ATTR_TYPE, Pin.OUTPUT);
      final var input = Pin.FACTORY.createComponent(Location.create(100, 100, true), inputAttrs);
      final var output = Pin.FACTORY.createComponent(Location.create(200, 100, true), outputAttrs);
      final var mutation = new CircuitMutation(circuit);
      mutation.add(input);
      mutation.add(output);
      mutation.add(Wire.create(input.getLocation(), output.getLocation()));
      mutation.execute();
      return circuit;
    } finally {
      project.getSimulator().shutDown();
    }
  }

  @Test
  void widthMismatchIsReportedWithItsPlace() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var circuit = inputWiredToOutput(1, 8);
      final var e =
          assertThrows(AnalyzeException.WidthMismatch.class, () -> Analyze.checkWidths(circuit));
      assertTrue(e.getMessage().contains("(100,100)") || e.getMessage().contains("(200,100)"),
          e.getMessage());
      assertTrue(e.getMessage().contains("1, 8") || e.getMessage().contains("8, 1"),
          e.getMessage());
    });
  }

  @Test
  void matchingWidthsPass() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var circuit = inputWiredToOutput(4, 4);
      assertDoesNotThrow(() -> Analyze.checkWidths(circuit));
    });
  }
}

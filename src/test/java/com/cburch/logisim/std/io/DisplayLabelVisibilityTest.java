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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.file.Loader;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.AddTool;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** New 7-segment and hex displays show their label; displays in existing files do not change. */
class DisplayLabelVisibilityTest {

  @TempDir Path temporary;

  @ParameterizedTest
  @ValueSource(strings = {SevenSegment._ID, HexDigit._ID})
  void newDisplaysShowTheirLabelButSavedOnesKeepTheirs(String id) throws Exception {
    final var tool = (AddTool) new IoLibrary().getTool(id);
    assertTrue(tool.getAttributeSet().getValue(StdAttr.LABEL_VISIBILITY));
    assertFalse(tool.getFactory().createAttributeSet().getValue(StdAttr.LABEL_VISIBILITY));

    // A display saved before, with the label hidden by default, has no "labelvisible" attribute.
    final var xml =
        """
        <?xml version="1.0" encoding="UTF-8" standalone="no"?>
        <project source="5.0.0" version="1.0">
          <lib desc="#I/O" name="6"/>
          <main name="main"/>
          <circuit name="main">
            <comp lib="6" loc="(100,100)" name="%s">
              <a name="label" val="d"/>
            </comp>
          </circuit>
        </project>
        """
            .formatted(id);
    final var path = temporary.resolve("old.circ");
    Files.writeString(path, xml);
    final var file = new Loader(null).openLogisimFile(path.toFile());
    final var display = file.getMainCircuit().getNonWires().iterator().next();
    assertEquals(id, display.getFactory().getName());
    assertFalse(display.getAttributeSet().getValue(StdAttr.LABEL_VISIBILITY));
  }
}

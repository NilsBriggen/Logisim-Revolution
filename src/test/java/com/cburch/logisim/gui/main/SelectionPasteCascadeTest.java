/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.main;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;
import java.util.List;
import org.junit.jupiter.api.Test;

class SelectionPasteCascadeTest {

  @Test
  void copyPlacedOnFloatingPasteCountsAsOccupied() {
    final var file = LogisimFile.createNew(new Loader(null), null);
    file.stopAutosaveThread(false);
    final var project = new Project(file);
    try {
      final var selection = new SelectionBase(project);
      final Component floating =
          Pin.FACTORY.createComponent(
              Location.create(100, 100, true), Pin.FACTORY.createAttributeSet());
      final Component copy =
          Pin.FACTORY.createComponent(
              Location.create(100, 100, true), Pin.FACTORY.createAttributeSet());

      assertTrue(selection.isOccupied(List.of(copy), 0, 0, List.of(floating)),
          "a second paste at the same spot would hide the first");
      assertFalse(selection.isOccupied(List.of(copy), 10, 10, List.of(floating)));
      assertFalse(selection.isOccupied(List.of(copy), 0, 0, List.of()));
    } finally {
      project.getSimulator().shutDown();
    }
  }
}

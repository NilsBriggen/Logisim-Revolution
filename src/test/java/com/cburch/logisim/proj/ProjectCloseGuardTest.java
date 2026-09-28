/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.proj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;

class ProjectCloseGuardTest {
  private static Project newProject() {
    return new Project(LogisimFile.createNew(new Loader(null), null));
  }

  @Test
  void noGuardsPermitClosing() {
    assertTrue(newProject().confirmCloseGuards());
  }

  @Test
  void firstCancellingGuardStopsTheCloseAndLaterGuardsAreNotAsked() {
    final var project = newProject();
    final var asked = new ArrayList<String>();
    project.addCloseGuard(() -> asked.add("saved"));
    project.addCloseGuard(
        () -> {
          asked.add("cancelled");
          return false;
        });
    project.addCloseGuard(() -> asked.add("never"));

    assertFalse(project.confirmCloseGuards());
    assertEquals(List.of("saved", "cancelled"), asked);
  }

  @Test
  void removedGuardIsNoLongerAskedAndDuplicatesAreIgnored() {
    final var project = newProject();
    final BooleanSupplier cancel = () -> false;
    project.addCloseGuard(cancel);
    project.addCloseGuard(cancel);
    project.removeCloseGuard(cancel);

    assertTrue(project.confirmCloseGuards());
  }
}

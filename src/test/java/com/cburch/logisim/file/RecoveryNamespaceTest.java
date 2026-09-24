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

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecoveryNamespaceTest {
  @TempDir Path temporary;

  @Test
  void openingAProjectNeverSelectsAnEvolutionSidecar() throws Exception {
    final var project = temporary.resolve("example.circ").toFile();
    final var evolution = temporary.resolve(".example.circ.autosave");
    final var revolution = temporary.resolve(".example.circ.revolution.autosave");
    Files.writeString(evolution, "Evolution recovery");

    assertTrue(Loader.findAutosaveFile(project).isEmpty());
    Files.writeString(revolution, "Revolution recovery");
    assertEquals(revolution.toFile(), Loader.findAutosaveFile(project).orElseThrow());
    assertEquals("Evolution recovery", Files.readString(evolution));
  }

  @Test
  void unnamedRecoveryUsesAnAppSpecificDirectory() {
    final var directory = Loader.getUnnamedAutosaveDirectory();
    assertEquals("recovery", directory.getName());
    assertEquals(".logisim-revolution", directory.getParentFile().getName());
    assertEquals("unnamed-", Loader.LOGISIM_UNNAMED_AUTOSAVE_PREFIX);
  }
}

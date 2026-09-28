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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Saving replaces a file's content, not the file's identity: mode, links and read-only stay. */
class SaveFileMetadataTest {

  @TempDir Path tempDir;

  private final List<String> errors = new ArrayList<>();
  private Loader loader;
  private LogisimFile file;

  @BeforeEach
  void setUp() {
    assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
    loader =
        new Loader(null) {
          @Override
          public void showError(String description) {
            errors.add(description);
          }
        };
    file = LogisimFile.createNew(loader, null);
    file.retireAutosaveThread();
  }

  @Test
  void overwriteKeepsTheFileMode() throws Exception {
    final var circ = tempDir.resolve("shared.circ");
    Files.writeString(circ, "old");
    Files.setPosixFilePermissions(circ, PosixFilePermissions.fromString("rw-rw-r--"));

    assertTrue(loader.save(file, circ.toFile()), errors.toString());

    assertEquals("rw-rw-r--", PosixFilePermissions.toString(Files.getPosixFilePermissions(circ)));
  }

  @Test
  void savingThroughASymlinkUpdatesTheTargetAndKeepsTheLink() throws Exception {
    final var real = tempDir.resolve("real.circ");
    Files.writeString(real, "old");
    final var link = Files.createSymbolicLink(tempDir.resolve("link.circ"), real);

    assertTrue(loader.save(file, link.toFile()), errors.toString());

    assertTrue(Files.isSymbolicLink(link));
    assertTrue(Files.readString(real).contains("<project"));
  }

  @Test
  void readOnlyFileIsNotReplaced() throws Exception {
    final var circ = tempDir.resolve("locked.circ");
    Files.writeString(circ, "old");
    Files.setPosixFilePermissions(circ, PosixFilePermissions.fromString("r--r--r--"));
    assumeFalse(Files.isWritable(circ), "running with privileges that ignore file modes");

    assertFalse(loader.save(file, circ.toFile()));

    assertEquals("old", Files.readString(circ));
    assertEquals(1, errors.size(), errors.toString());
  }
}

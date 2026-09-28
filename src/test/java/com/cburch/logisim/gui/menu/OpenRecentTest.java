/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.menu;

import static com.cburch.logisim.gui.Strings.S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Open Recent lists each file by name first, then the folder it is in. */
class OpenRecentTest {
  @TempDir Path tempDir;

  @Test
  void entryStartsWithTheFileName() throws Exception {
    final var file = tempDir.resolve("halfadder.circ").toFile();
    final var text = OpenRecent.getFileText(file);
    assertEquals(
        S.get("fileOpenRecentEntry", "halfadder.circ", tempDir.toFile().getCanonicalPath()), text);
  }

  @Test
  void longFoldersAreShortenedFromTheLeft() {
    final var deep = new StringBuilder();
    for (var i = 0; i < 12; i++) deep.append(File.separator).append("folder").append(i);
    final var file = new File(tempDir.toFile(), deep + File.separator + "x.circ");
    final var text = OpenRecent.getFileText(file);
    assertTrue(text.startsWith("x.circ"), text);
    assertTrue(text.contains("…"), text);
    assertTrue(text.endsWith("folder11"), text);
  }

  @Test
  void noFileSaysNone() {
    assertEquals(S.get("fileOpenRecentNoChoices"), OpenRecent.getFileText(null));
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.generic;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.TestBase;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards the dialog conventions.
 *
 * <p>These are the habits that made failures invisible in the first place, so they are checked
 * rather than left to review.
 */
public class DialogConventionsTest extends TestBase {

  private static final Path SOURCE_ROOT = Path.of("src", "main", "java");

  /** OptionPane itself is the wrapper, so it is the one place allowed to call JOptionPane. */
  private static final String WRAPPER = "OptionPane.java";

  private static List<Path> javaSources() throws IOException {
    try (Stream<Path> files = Files.walk(SOURCE_ROOT)) {
      return files.filter(p -> p.toString().endsWith(".java")).toList();
    }
  }

  @Test
  public void dialogsGoThroughTheWrapper() throws IOException {
    final var offenders = new ArrayList<String>();
    for (final var file : javaSources()) {
      if (file.getFileName().toString().equals(WRAPPER)) continue;
      final var text = Files.readString(file);
      if (text.contains("JOptionPane.show")) offenders.add(SOURCE_ROOT.relativize(file).toString());
    }

    assertTrue(
        offenders.isEmpty(),
        "these call JOptionPane directly instead of the OptionPane wrapper, "
            + "which also handles the headless case: "
            + offenders);
  }

  /**
   * {@code "Sans Serif"} is not a Java logical font name ({@code SansSerif} is), so a font built
   * from it silently falls back to the default and nobody notices the size or style is wrong.
   */
  @Test
  public void noFontIsBuiltFromTheMisspelledLogicalName() throws IOException {
    final var offenders = new ArrayList<String>();
    for (final var file : javaSources()) {
      if (Files.readString(file).contains("\"Sans Serif\"")) {
        offenders.add(SOURCE_ROOT.relativize(file).toString());
      }
    }

    assertTrue(offenders.isEmpty(), "use Font.SANS_SERIF instead of \"Sans Serif\": " + offenders);
  }

  /**
   * A call site that passes no parent must still get an attached dialog.
   *
   * <p>Twenty call sites pass null. Rather than editing each one, the wrapper resolves the parent,
   * so none of them can produce a dialog that hides behind the application.
   */
  @Test
  public void wrapperResolvesAMissingParent() {
    final var panel = new javax.swing.JPanel();
    assertSame(panel, OptionPane.resolveParent(panel));

    // With nothing focused there is no window to attach to, but it must not throw.
    OptionPane.resolveParent(null);
  }
}

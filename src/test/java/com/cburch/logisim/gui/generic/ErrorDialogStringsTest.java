/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.generic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.TestBase;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/**
 * Checks that the strings the error dialog asks for exist.
 *
 * <p>A missing key does not fail the build, it surfaces as a placeholder in the dialog at runtime,
 * which is exactly where nobody is looking.
 */
public class ErrorDialogStringsTest extends TestBase {

  private static final String BUNDLE = "/resources/logisim/strings/gui/gui.properties";

  private static Properties englishBundle() throws Exception {
    final var properties = new Properties();
    try (final var stream = ErrorDialogStringsTest.class.getResourceAsStream(BUNDLE)) {
      assertNotNull(stream, "English gui bundle not found at " + BUNDLE);
      properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
    return properties;
  }

  @Test
  public void detailToggleKeysExist() throws Exception {
    final var bundle = englishBundle();
    for (final var key : new String[] {"errorDetailsShow", "errorDetailsHide"}) {
      final var value = bundle.getProperty(key);
      assertNotNull(value, "missing key in the English gui bundle: " + key);
      assertFalse(value.isBlank(), "key is present but empty: " + key);
    }
  }

  /** The two labels drive the same button, so identical text would make it look inert. */
  @Test
  public void detailToggleLabelsDiffer() throws Exception {
    final var bundle = englishBundle();
    assertTrue(
        !bundle.getProperty("errorDetailsShow").equals(bundle.getProperty("errorDetailsHide")),
        "the show and hide labels must differ");
  }
}

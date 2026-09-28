/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.start;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.PropertyResourceBundle;
import java.util.regex.Pattern;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CliTranslationTest {
  private static PropertyResourceBundle bundle(String name, String language) throws Exception {
    final var suffix = language.equals("en") ? "" : "_" + language;
    final var path = "/resources/logisim/strings/" + name + "/" + name + suffix + ".properties";
    try (final var stream = CliTranslationTest.class.getResourceAsStream(path)) {
      assertNotNull(stream, path);
      return new PropertyResourceBundle(stream);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"de", "el", "en", "es", "fr", "pt", "ru", "it", "nl", "ja", "pl", "zh"})
  void translatedHelpKeepsExitCodesAndAdjacentKeys(String language) throws Exception {
    final var gui = bundle("gui", language);
    final var help = gui.getString("argExitCodes");
    for (final var code : new int[] {0, 1, 2, 3, 10, 100}) {
      assertTrue(Pattern.compile("(?m)^\\s*" + code + "\\s+\\S").matcher(help).find(),
          language + " omits exit code " + code);
    }
    assertFalse(help.contains("argGatesOption ="), language);
    assertFalse(help.contains("cliCircuitNotFoundError ="), language);
    assertFalse(gui.getString("argGatesOption").isBlank(), language);
    assertTrue(String.format(gui.getString("cliCircuitNotFoundError"), "test_circuit")
        .contains("test_circuit"), language);
    assertTrue(String.format(bundle("analyze", language).getString("karnaughTooManyInputsError"), 6)
        .contains("6"), language);
  }
}

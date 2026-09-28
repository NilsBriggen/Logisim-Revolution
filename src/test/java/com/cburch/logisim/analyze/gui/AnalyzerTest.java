/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.analyze.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.Dimension;
import org.junit.jupiter.api.Test;

class AnalyzerTest {
  @Test
  void localeChangeKeepsManualWindowSizeWhenPreferredSizeIsSmaller() {
    final var current = new Dimension(800, 600);
    final var preferred = new Dimension(700, 500);

    assertEquals(current, Analyzer.expandedSizeForLocaleChange(current, preferred));
  }

  @Test
  void localeChangeExpandsWindowWhenPreferredSizeGrows() {
    final var current = new Dimension(800, 600);
    final var preferred = new Dimension(900, 650);

    assertEquals(preferred, Analyzer.expandedSizeForLocaleChange(current, preferred));
  }

  @Test
  void localeChangeExpandsOnlyTheDimensionThatNeedsMoreSpace() {
    final var current = new Dimension(800, 600);
    final var preferred = new Dimension(900, 500);

    assertEquals(new Dimension(900, 600), Analyzer.expandedSizeForLocaleChange(current, preferred));
  }

  @Test
  void opensAtTwoThirdsOfTheParentInsteadOfItsTinyPackedSize() {
    assertEquals(new Dimension(800, 600), AnalyzerManager.initialSize(
        new Dimension(450, 300), new Dimension(1200, 900), new Dimension(1920, 1080)));
  }

  @Test
  void neverOpensSmallerThanPackedOrLargerThanTheScreen() {
    assertEquals(new Dimension(700, 500), AnalyzerManager.initialSize(
        new Dimension(700, 500), new Dimension(600, 450), null));
    assertEquals(new Dimension(900, 540), AnalyzerManager.initialSize(
        new Dimension(450, 300), new Dimension(3000, 3000), new Dimension(1000, 600)));
    assertEquals(new Dimension(450, 300), AnalyzerManager.initialSize(
        new Dimension(450, 300), null, null));
  }
}

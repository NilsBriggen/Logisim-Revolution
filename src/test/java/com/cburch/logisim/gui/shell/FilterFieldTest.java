/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FilterFieldTest {

  @Test
  void clearingReportsAnEmptyFilterAtOnceRatherThanAfterAPause() {
    final var reported = new ArrayList<String>();
    final var field = new FilterField("Filter", reported::add);
    field.setText("alu");
    reported.clear();

    field.clear();

    assertEquals(List.of(""), reported);
    assertEquals("", field.getText());
  }

  @Test
  void clearingAnEmptyFieldReportsNothing() {
    final var reported = new ArrayList<String>();
    final var field = new FilterField("Filter", reported::add);

    field.clear();

    assertTrue(reported.isEmpty());
  }

  @Test
  void typingDoesNotReportUntilTheTypingStops() {
    // Filtering walks every library, so it must not run once per keystroke.
    final var reported = new ArrayList<String>();
    final var field = new FilterField("Filter", reported::add);

    field.setText("a");

    assertTrue(reported.isEmpty(), "reported before the pause: " + reported);
  }
}

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ProblemsPillTest {

  @Test
  void showsOnlyWhileThereIsAProblemAndStepsThroughThem() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var pill = new ProblemsPill();
      final var steps = new ArrayList<Integer>();
      final var help = new AtomicInteger();
      final var resets = new AtomicInteger();
      pill.setNavigator(steps::add);
      pill.setLearnMoreAction(help::incrementAndGet);
      pill.setResetAction(resets::incrementAndGet);
      assertFalse(pill.isVisible());

      pill.setProblems(2, -1, false);
      assertTrue(pill.isVisible());
      assertEquals("Incompatible widths (2)", pill.getSummary());
      final var buttons = visibleButtons(pill);
      // The count (which goes to the first problem), previous, next and Learn more.
      assertEquals(4, buttons.size());
      buttons.get(0).doClick();
      buttons.get(1).doClick();
      buttons.get(2).doClick();
      buttons.get(3).doClick();
      assertEquals(List.of(1, -1, 1), steps);
      assertEquals(1, help.get());

      pill.setProblems(2, 0, false);
      assertEquals("Incompatible widths  1 of 2", pill.getSummary());
      visibleButtons(pill).get(0).doClick();
      assertEquals(0, steps.get(steps.size() - 1));

      pill.setProblems(0, -1, true);
      assertTrue(pill.isVisible());
      assertEquals("Oscillation apparent", pill.getSummary());
      final var reset = visibleButtons(pill);
      assertEquals(1, reset.size());
      reset.get(0).doClick();
      assertEquals(1, resets.get());

      pill.setProblems(0, -1, false);
      assertFalse(pill.isVisible());
    });
  }

  private static List<AbstractButton> visibleButtons(ProblemsPill pill) {
    return ShellChromeTest.components(pill, AbstractButton.class).stream()
        .filter(AbstractButton::isVisible)
        .toList();
  }
}

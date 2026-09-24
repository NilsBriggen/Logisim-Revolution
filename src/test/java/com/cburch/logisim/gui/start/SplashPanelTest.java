/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.start;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class SplashPanelTest {
  @Test
  void reportsAStageWithoutClaimingPercentageProgress() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var panel = new SplashPanel();
      panel.setStage("Loading a circuit…");
      assertEquals("Loading a circuit…", panel.stageText());
      assertTrue(panel.progressBar().isIndeterminate());
      assertTrue(!panel.progressBar().isStringPainted());
      assertTrue(panel.getPreferredSize().width > panel.getPreferredSize().height);
    });
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.generic;

import com.cburch.logisim.util.UiScale;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.UIScale;
import java.awt.Container;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/** In-memory scaled form fixtures; never persist a scale or other application preference. */
public final class FormLayoutTestSupport {
  private FormLayoutTestSupport() {}

  public static void atScale(double scale, Runnable assertions) throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var previousLaf = UIManager.getLookAndFeel();
      final var previousZoom = UIScale.getZoomFactor();
      final var previousFont = UIManager.put("Label.font", null);
      try {
        FlatLightLaf.setup();
        UiScale.setFactor(scale);
        assertions.run();
      } finally {
        try {
          UIManager.setLookAndFeel(previousLaf);
        } catch (javax.swing.UnsupportedLookAndFeelException exception) {
          throw new AssertionError(exception);
        } finally {
          UIScale.setZoomFactor(previousZoom);
          UIManager.put("Label.font", previousFont);
        }
      }
    });
  }

  public static void layoutTree(Container container) {
    container.doLayout();
    for (final var child : container.getComponents()) {
      if (child instanceof Container nested) layoutTree(nested);
    }
  }
}

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

import com.cburch.logisim.gui.theme.AppIcons;
import java.awt.Component;
import java.util.Arrays;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ActivityBarTest {

  @Test
  void visibleCountLeavesRoomForTheMoreButtonOnlyWhenSomethingOverflows() {
    assertEquals(6, ActivityBar.visibleCount(6, 600, 100));
    assertEquals(4, ActivityBar.visibleCount(6, 599, 100));
    assertEquals(0, ActivityBar.visibleCount(6, 50, 100));
  }

  @Test
  void entriesThatDoNotFitMoveBehindAMoreButtonInsteadOfBeingCutOff() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var bar = new ActivityBar();
      for (var i = 0; i < 8; i++) {
        bar.addAction("action" + i, AppIcons.Id.SETTINGS, "Action " + i, () -> {});
      }
      final var size = bar.getPreferredSize();
      bar.setSize(size);
      bar.doLayout();
      assertEquals(8, visibleButtons(bar), "everything fits at the preferred height");

      bar.setSize(size.width, size.height / 2);
      bar.doLayout();
      final var shown = Arrays.stream(bar.getComponents()).filter(Component::isVisible).toList();
      assertTrue(shown.size() < 9);
      for (final var button : shown) {
        assertTrue(button.getY() >= 0 && button.getY() + button.getHeight() <= bar.getHeight(),
            "a visible button was drawn past the end of the bar");
      }
      final var more = Arrays.stream(bar.getComponents())
          .filter(component -> component instanceof JButton button
              && button.getToolTipText() != null
              && !button.getToolTipText().startsWith("Action"))
          .findFirst()
          .orElseThrow();
      assertTrue(more.isVisible(), "no way to reach the hidden entries");

      bar.setSize(size);
      bar.doLayout();
      assertFalse(more.isVisible());
    });
  }

  private static int visibleButtons(ActivityBar bar) {
    return (int) Arrays.stream(bar.getComponents()).filter(Component::isVisible).count();
  }
}

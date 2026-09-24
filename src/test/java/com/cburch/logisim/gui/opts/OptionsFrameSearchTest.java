/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.opts;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.gui.shell.SettingsNav;
import javax.swing.JButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class OptionsFrameSearchTest {
  @Test
  void projectOptionsIndexIncludesIconActionTooltips() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          final var page = new OptionsPanel(null) {
            private static final long serialVersionUID = 1L;

            @Override
            public String getTitle() {
              return "Toolbar";
            }

            @Override
            public String getHelpText() {
              return "Configure tools";
            }

            @Override
            public void localeChanged() {}
          };
          final var button = new JButton();
          button.setToolTipText("Move separator down");
          page.add(button);

          final var indexed = OptionsFrame.searchPage(page);
          assertEquals("Toolbar", indexed.title());
          assertTrue(SettingsNav.matches(indexed.text(), "move separator"));
        });
  }
}

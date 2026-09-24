/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.gui.generic.FormLayoutTestSupport;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SelectionPanelTest {
  @Test
  void transferControlsAreNamedAndActionsRemainExplicit() throws Exception {
    FormLayoutTestSupport.atScale(2, () -> {
      final var added = new AtomicInteger();
      final var removed = new AtomicInteger();
      final var controls = new SelectionPanel.TransferControls(
          added::incrementAndGet, removed::incrementAndGet);
      assertFalse(controls.addButton.isEnabled());
      assertFalse(controls.removeButton.isEnabled());
      assertFalse(controls.addButton.getAccessibleContext().getAccessibleName().isBlank());
      assertFalse(controls.removeButton.getAccessibleContext().getAccessibleName().isBlank());
      controls.setSize(controls.getPreferredSize());
      FormLayoutTestSupport.layoutTree(controls);
      assertTrue(controls.addButton.getWidth() >= controls.addButton.getMinimumSize().width);
      assertTrue(controls.removeButton.getWidth() >= controls.removeButton.getMinimumSize().width);
      controls.addButton.setEnabled(true);
      controls.removeButton.setEnabled(true);
      controls.addButton.doClick(0);
      controls.removeButton.doClick(0);
      assertEquals(1, added.get());
      assertEquals(1, removed.get());
    });
  }
}

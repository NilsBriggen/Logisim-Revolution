/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.test;

import static com.cburch.logisim.gui.Strings.S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

/** The Show/Set action columns and the set/seq columns explain themselves. */
class TestPanelHeadersTest {

  @Test
  void actionColumnsHaveVisibleNamesAndHeaderTips() throws Exception {
    final var panel = new TestPanel[1];
    javax.swing.SwingUtilities.invokeAndWait(() -> panel[0] = new TestPanel(mock(TestFrame.class)));
    final var p = panel[0];

    assertEquals(S.get("testShowHeader"), p.getColumnName(0));
    assertEquals(S.get("testSetHeader"), p.getColumnName(1));
    assertFalse(p.getColumnName(0).isBlank());
    assertFalse(p.getColumnName(1).isBlank());
    assertEquals(S.get("toolTipShow"), p.getColumnToolTip(0));
    assertEquals(S.get("toolTipSet"), p.getColumnToolTip(1));
    assertEquals(S.get("testSetColumnTip"), p.getColumnToolTip(3));
    assertEquals(S.get("testSeqColumnTip"), p.getColumnToolTip(4));
  }
}

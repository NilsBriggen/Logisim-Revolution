/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.awt.Dimension;
import java.awt.Point;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class FrameTest {
  @Test
  void unfinishedFrameDoesNotReportExplorerVisibleDuringMenuConstruction() throws Exception {
    final var frame = (Frame) allocateWithoutConstructor(Frame.class);

    assertFalse(frame.isExplorerVisible());
  }

  @Test
  void growsCircuitViewBeforeRestoringItsScrollPosition() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          final var viewport = new JViewport();
          viewport.setView(new JPanel());
          viewport.setExtentSize(new Dimension(400, 300));
          viewport.setViewSize(new Dimension(400, 300));

          Frame.restoreViewPosition(
              viewport, new Dimension(1000, 800), new Point(325, 175));

          assertEquals(new Dimension(1000, 800), viewport.getViewSize());
          assertEquals(new Point(325, 175), viewport.getViewPosition());
        });
  }

  private static Object allocateWithoutConstructor(Class<?> type) throws Exception {
    final var unsafeClass = Class.forName("sun.misc.Unsafe");
    final var unsafeField = unsafeClass.getDeclaredField("theUnsafe");
    unsafeField.setAccessible(true);
    final var unsafe = unsafeField.get(null);
    return unsafeClass.getMethod("allocateInstance", Class.class).invoke(unsafe, type);
  }
}

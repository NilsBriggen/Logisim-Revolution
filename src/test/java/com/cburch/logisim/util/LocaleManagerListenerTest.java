/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import java.awt.GraphicsEnvironment;
import java.awt.event.HierarchyEvent;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class LocaleManagerListenerTest {

  /** Stands in for a component whose window is opened and disposed, without needing a display. */
  private static final class FakeDisplayable extends JPanel {
    private boolean displayable;

    @Override
    public boolean isDisplayable() {
      return displayable;
    }

    void setDisplayableAndNotify(boolean value) {
      displayable = value;
      dispatchEvent(
          new HierarchyEvent(
              this,
              HierarchyEvent.HIERARCHY_CHANGED,
              this,
              null,
              HierarchyEvent.DISPLAYABILITY_CHANGED));
    }
  }

  @Test
  void ownerBoundListenerIsReleasedWhenOwnerIsDisposedAndRestoredWhenShownAgain() {
    final var owner = new FakeDisplayable();
    final var calls = new AtomicInteger();
    final LocaleListener listener = calls::incrementAndGet;
    try {
      LocaleManager.addLocaleListener(owner, listener);
      assertTrue(LocaleManager.isLocaleListenerRegistered(listener));

      owner.setDisplayableAndNotify(true);
      assertTrue(LocaleManager.isLocaleListenerRegistered(listener));
      assertEquals(0, calls.get(), "first display must not refire the locale");

      owner.setDisplayableAndNotify(false);
      assertFalse(LocaleManager.isLocaleListenerRegistered(listener));

      owner.setDisplayableAndNotify(true);
      assertTrue(LocaleManager.isLocaleListenerRegistered(listener));
      assertEquals(1, calls.get(), "a re-shown owner catches up on missed locale changes");
    } finally {
      LocaleManager.removeLocaleListener(listener);
    }
  }

  @Test
  void disposedFrameReleasesListenersOfItsComponents() throws Exception {
    assumeFalse(GraphicsEnvironment.isHeadless());
    final LocaleListener frameListener = () -> {};
    final LocaleListener childListener = () -> {};
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            final var frame = new JFrame();
            final var child = new JPanel();
            frame.add(child);
            LocaleManager.addLocaleListener(frame, frameListener);
            LocaleManager.addLocaleListener(child, childListener);
            frame.pack();
            assertTrue(LocaleManager.isLocaleListenerRegistered(frameListener));
            assertTrue(LocaleManager.isLocaleListenerRegistered(childListener));

            frame.dispose();
            assertFalse(LocaleManager.isLocaleListenerRegistered(frameListener));
            assertFalse(LocaleManager.isLocaleListenerRegistered(childListener));
          });
    } finally {
      LocaleManager.removeLocaleListener(frameListener);
      LocaleManager.removeLocaleListener(childListener);
    }
  }
}

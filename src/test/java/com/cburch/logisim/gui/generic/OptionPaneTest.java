/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.generic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.TestBase;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

/**
 * Tests the parts of the error dialog that do not need a display.
 *
 * <p>The dialog itself cannot be shown headlessly, so the pieces it is assembled from are kept as
 * separate static helpers and tested here.
 */
public class OptionPaneTest extends TestBase {

  @Test
  public void stackTraceIncludesTypeAndMessage() {
    final var trace = OptionPane.stackTraceOf(new IllegalStateException("boom"));

    assertTrue(trace.contains("IllegalStateException"), trace);
    assertTrue(trace.contains("boom"), trace);
    assertTrue(trace.contains("OptionPaneTest"), "expected the throwing frame in: " + trace);
  }

  /** A cause chain is what makes a report useful, so it has to survive into the detail area. */
  @Test
  public void stackTraceIncludesTheCauseChain() {
    final var root = new java.io.IOException("disk gone");
    final var trace = OptionPane.stackTraceOf(new IllegalStateException("wrapper", root));

    assertTrue(trace.contains("wrapper"), trace);
    assertTrue(trace.contains("Caused by"), trace);
    assertTrue(trace.contains("disk gone"), trace);
  }

  @Test
  public void stackTraceOfNullIsEmpty() {
    assertEquals("", OptionPane.stackTraceOf(null));
  }

  /** The message goes into an HTML label, so raw markup in an exception must not be interpreted. */
  @Test
  public void messageMarkupIsEscaped() {
    assertEquals("&lt;b&gt;bold&lt;/b&gt;", OptionPane.escapeHtml("<b>bold</b>"));
    assertEquals("a &amp; b", OptionPane.escapeHtml("a & b"));
    assertEquals("", OptionPane.escapeHtml(null));
  }

  /** Ampersands must be escaped first, or the escapes themselves get double-escaped. */
  @Test
  public void escapingDoesNotDoubleEscape() {
    assertEquals("&amp;lt;", OptionPane.escapeHtml("&lt;"));
  }

  /** A component outside any window still has to give the dialog something to attach to. */
  @Test
  public void parentResolutionFallsBackToTheComponent() {
    final var orphan = new JPanel();
    assertSame(orphan, OptionPane.resolveParent(orphan));
  }

  /** With no component and no focused window there is simply no parent; it must not throw. */
  @Test
  public void parentResolutionOfNullIsNullWhenNothingHasFocus() {
    final var parent = OptionPane.resolveParent(null);
    if (parent == null) {
      assertNull(parent);
    } else {
      assertFalse(parent instanceof JPanel, "unexpected parent: " + parent);
    }
  }
}

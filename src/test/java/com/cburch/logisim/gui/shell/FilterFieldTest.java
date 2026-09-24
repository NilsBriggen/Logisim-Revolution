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

import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComponent;
import javax.swing.JRootPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class FilterFieldTest {

  private static void press(FilterField field, int key) {
    final var binding = field.getInputMap().get(KeyStroke.getKeyStroke(key, 0));
    field.getActionMap().get(binding).actionPerformed(new ActionEvent(field, 0, ""));
  }

  @Test
  void enterAndNavigationFlushLatestTextBeforeHandingOff() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var events = new ArrayList<String>();
      final var field = new FilterField("Filter", text -> events.add("query:" + text));
      field.setKeyboardHandoff(() -> events.add("accept"),
          direction -> events.add("navigate:" + direction));
      field.setText("mux");
      press(field, KeyEvent.VK_ENTER);
      assertEquals(List.of("query:mux", "accept"), events);
      events.clear();
      field.setText("demux");
      press(field, KeyEvent.VK_DOWN);
      assertEquals(List.of("query:demux", "navigate:1"), events);
      events.clear();
      field.setText("dff");
      press(field, KeyEvent.VK_UP);
      assertEquals(List.of("query:dff", "navigate:-1"), events);
      field.flushPendingChange();
      assertEquals(2, events.size(), "a flushed change must not fire again");
    });
  }

  @Test
  void firstEscapeClearsAndSecondEscapeInvokesAncestorCancel() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var cancelled = new AtomicInteger();
      final var root = new JRootPane();
      final var field = new EscapeField();
      root.getContentPane().add(field);
      root.registerKeyboardAction(event -> cancelled.incrementAndGet(),
          KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
          JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
      field.setText("mux");
      field.escape();
      assertEquals("", field.getText());
      assertEquals(0, cancelled.get());
      field.escape();
      assertEquals(1, cancelled.get());
    });
  }

  @Test
  void removalCancelsPendingTimerCallback() throws Exception {
    final var reported = new CountDownLatch(1);
    SwingUtilities.invokeAndWait(() -> {
      final var field = new FilterField("Filter", text -> reported.countDown());
      field.addNotify();
      field.setText("discarded panel");
      field.removeNotify();
    });
    assertFalse(reported.await(300, TimeUnit.MILLISECONDS));
  }

  private static class EscapeField extends FilterField {
    private static final long serialVersionUID = 1L;

    EscapeField() {
      super("Filter", text -> {});
    }

    void escape() {
      processKeyEvent(new KeyEvent(this, KeyEvent.KEY_PRESSED, 0, 0,
          KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED));
    }
  }

  @Test
  void clearingReportsAnEmptyFilterAtOnceRatherThanAfterAPause() {
    final var reported = new ArrayList<String>();
    final var field = new FilterField("Filter", reported::add);
    field.setText("alu");
    reported.clear();

    field.clear();

    assertEquals(List.of(""), reported);
    assertEquals("", field.getText());
  }

  @Test
  void clearingAnEmptyFieldReportsNothing() {
    final var reported = new ArrayList<String>();
    final var field = new FilterField("Filter", reported::add);

    field.clear();

    assertTrue(reported.isEmpty());
  }

  @Test
  void typingDoesNotReportUntilTheTypingStops() {
    // Filtering walks every library, so it must not run once per keystroke.
    final var reported = new ArrayList<String>();
    final var field = new FilterField("Filter", reported::add);

    field.setText("a");

    assertTrue(reported.isEmpty(), "reported before the pause: " + reported);
  }
}

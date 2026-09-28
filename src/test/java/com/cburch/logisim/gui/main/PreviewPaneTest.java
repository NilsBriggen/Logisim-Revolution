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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class PreviewPaneTest {

  private static final long TIMEOUT_MS = 10_000;

  @Test
  void rendersOffTheEventThreadAndReusesRenderedPages() throws Exception {
    final var calls = new AtomicInteger();
    final var onEventThread = new CopyOnWriteArrayList<Boolean>();
    final PreviewPane.Renderer renderer =
        (index, width, height) -> {
          calls.incrementAndGet();
          onEventThread.add(SwingUtilities.isEventDispatchThread());
          return new PreviewPane.Result(
              new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), "page " + index);
        };
    final var pane = new PreviewPane[1];
    SwingUtilities.invokeAndWait(
        () -> {
          pane[0] = new PreviewPane(index -> "Page " + (index + 1));
          pane[0].setSize(400, 500);
          pane[0].doLayout();
          pane[0].setContent("settings", 3, renderer);
        });
    awaitIdle(pane[0]);
    assertEquals(1, calls.get());
    assertEquals("page 0", caption(pane[0]));

    SwingUtilities.invokeAndWait(() -> pane[0].showPage(2));
    awaitIdle(pane[0]);
    assertEquals("page 2", caption(pane[0]));

    // Back to a page, and the same settings again: both were rendered already.
    SwingUtilities.invokeAndWait(() -> pane[0].showPage(0));
    awaitIdle(pane[0]);
    SwingUtilities.invokeAndWait(() -> pane[0].setContent("settings", 3, renderer));
    awaitIdle(pane[0]);
    assertEquals(2, calls.get());
    assertEquals("page 0", caption(pane[0]));
    assertFalse(onEventThread.contains(true), "pages are rendered off the event thread");

    SwingUtilities.invokeAndWait(() -> pane[0].setContent("changed", 3, renderer));
    awaitIdle(pane[0]);
    assertEquals(3, calls.get());
  }

  @Test
  void failedRenderSaysSo() throws Exception {
    final var pane = new PreviewPane[1];
    SwingUtilities.invokeAndWait(
        () -> {
          pane[0] = new PreviewPane(index -> "");
          pane[0].setSize(300, 300);
          pane[0].doLayout();
          pane[0].setContent(
              "broken",
              1,
              (index, width, height) -> {
                throw new IllegalStateException("cannot draw");
              });
        });
    awaitIdle(pane[0]);
    assertEquals(
        com.cburch.logisim.gui.Strings.S.get("previewUnavailable"), caption(pane[0]));
  }

  private static String caption(PreviewPane pane) throws Exception {
    final var text = new String[1];
    SwingUtilities.invokeAndWait(() -> text[0] = pane.getCaption());
    return text[0];
  }

  private static void awaitIdle(PreviewPane pane) throws Exception {
    final var deadline = System.currentTimeMillis() + TIMEOUT_MS;
    while (System.currentTimeMillis() < deadline) {
      final var busy = new boolean[1];
      SwingUtilities.invokeAndWait(() -> busy[0] = pane.isBusy());
      if (!busy[0]) return;
      Thread.sleep(20);
    }
    assertTrue(false, "the preview did not finish rendering");
  }
}

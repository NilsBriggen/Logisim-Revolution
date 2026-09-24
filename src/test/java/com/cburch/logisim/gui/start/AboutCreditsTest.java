/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.start;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.gui.theme.LogisimDarkLaf;
import com.cburch.logisim.gui.theme.LogisimLightLaf;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

class AboutCreditsTest {
  @Test
  void offscreenPaintUsesProvidedGraphicsAndSupportsTallViewport() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          final var credits = new AboutCredits(600, 200) {
            private static final long serialVersionUID = 1L;

            @Override
            public Graphics getGraphics() {
              throw new AssertionError("Painting must use the supplied graphics");
            }
          };
          credits.advance(4000);
          assertDoesNotThrow(() -> paint(credits, 600, 200));
          assertTrue(credits.getScrollHeight() > 0);
          // The old height/4 alpha calculation threw above 1023 pixels.
          assertDoesNotThrow(() -> paint(credits, 600, 1400));
          assertDoesNotThrow(() -> paint(credits, 0, 0));
        });
  }

  @Test
  void resizeAndFontChangeRemeasureWithoutAccumulatingOldHeight() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          final var oldFont = UIManager.get("Label.font");
          try {
            UIManager.put("Label.font", new Font(Font.DIALOG, Font.PLAIN, 12));
            final var credits = new AboutCredits(600, 200);
            paint(credits, 600, 200);
            final var wideHeight = credits.getScrollHeight();
            paint(credits, 140, 200);
            assertTrue(credits.getScrollHeight() > wideHeight);
            paint(credits, 600, 200);
            assertEquals(wideHeight, credits.getScrollHeight());

            UIManager.put("Label.font", new Font(Font.DIALOG, Font.PLAIN, 24));
            paint(credits, 600, 200);
            assertTrue(credits.getScrollHeight() > wideHeight);
          } finally {
            UIManager.put("Label.font", oldFont);
          }
        });
  }

  @Test
  void resolvedLightAndDarkThemeTextColorsMeetContrastAgainstPaintedSurface() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          final var oldForeground = UIManager.get("Label.foreground");
          try {
            for (final var laf : new javax.swing.LookAndFeel[] {
              new LogisimLightLaf(), new LogisimDarkLaf()
            }) {
              final var defaults = laf.getDefaults();
              final var surface = defaults.getColor("Logisim.sidePanel.background");
              assertNotNull(surface);
              UIManager.put("Label.foreground", defaults.getColor("Label.foreground"));
              for (final var key : new String[] {
                  "Label.foreground", "Logisim.accent", "Logisim.mutedForeground"
              }) {
                final var requested = defaults.getColor(key);
                assertNotNull(requested, key);
                final var actual = AboutCredits.readableColor(requested, surface);
                assertEquals(255, actual.getAlpha());
                assertTrue(AboutCredits.contrast(actual, surface) >= 4.5, laf.getName() + ": " + key);
              }
            }
          } finally {
            UIManager.put("Label.foreground", oldForeground);
          }
        });
  }

  @Test
  void paintingClearsPreviousFrameAndDoesNotMutateCallerGraphics() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          final var credits = new AboutCredits(600, 200);
          credits.setSize(600, 200);
          final var image = new BufferedImage(600, 200, BufferedImage.TYPE_INT_ARGB);
          final var graphics = image.createGraphics();
          try {
            graphics.setColor(Color.MAGENTA);
            credits.paintComponent(graphics);
            assertEquals(Color.MAGENTA, graphics.getColor());
            assertEquals(AboutCredits.surfaceColor().getRGB(), image.getRGB(0, 0));
            credits.advance(4000);
            credits.paintComponent(graphics);
            assertEquals(Color.MAGENTA, graphics.getColor());
            assertTrue(hasTextPixels(image, AboutCredits.surfaceColor().getRGB()));
          } finally {
            graphics.dispose();
          }
        });
  }

  @Test
  void clippingRejectsBothSidesButKeepsPartiallyVisibleLines() {
    assertFalse(AboutCredits.isVisible(-30, 20, 200));
    assertFalse(AboutCredits.isVisible(200, 20, 200));
    assertFalse(AboutCredits.isVisible(210, 20, 200));
    assertTrue(AboutCredits.isVisible(-10, 20, 200));
    assertTrue(AboutCredits.isVisible(190, 20, 200));
  }

  private static void paint(AboutCredits credits, int width, int height) {
    credits.setSize(width, height);
    final var image = new BufferedImage(Math.max(1, width), Math.max(1, height),
        BufferedImage.TYPE_INT_ARGB);
    final var graphics = image.createGraphics();
    try {
      credits.paintComponent(graphics);
    } finally {
      graphics.dispose();
    }
  }

  private static boolean hasTextPixels(BufferedImage image, int background) {
    for (var y = 0; y < image.getHeight(); y++) {
      for (var x = 0; x < image.getWidth(); x++) {
        if (image.getRGB(x, y) != background) return true;
      }
    }
    return false;
  }
}

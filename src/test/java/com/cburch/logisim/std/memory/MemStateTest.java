/*
 * Logisim-evolution - digital logic design tool
 * Copyright by the logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.memory;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.gui.canvas.CanvasStyle;
import com.cburch.logisim.prefs.AppPreferences;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

class MemStateTest {

  @Test
  void memoryValuesAndSurfaceUseTheSameThemePair() {
    final var oldBackground = UIManager.get("TextField.background");
    final var oldForeground = UIManager.get("TextField.foreground");
    try {
      for (final var dark : new boolean[] {false, true}) {
        final var background = dark ? new Color(0x242830) : Color.WHITE;
        final var foreground = dark ? new Color(0xE2E6ED) : Color.BLACK;
        UIManager.put("TextField.background", background);
        UIManager.put("TextField.foreground", foreground);
        final var image = new BufferedImage(200, 120, BufferedImage.TYPE_INT_RGB);
        final var graphics = image.createGraphics();
        graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        new MemState(MemContents.create(8, 8, false)).paint(graphics, 0, 0, 0, 0, 160, 100, 0);
        graphics.dispose();
        var surfacePixels = 0;
        var inkPixels = 0;
        for (var y = 0; y < image.getHeight(); y++) {
          for (var x = 0; x < image.getWidth(); x++) {
            final var pixel = image.getRGB(x, y);
            if (pixel == background.getRGB()) surfacePixels++;
            if (pixel == foreground.getRGB()) inkPixels++;
          }
        }
        assertTrue(surfacePixels > 500, "the numeric display has its themed surface");
        assertTrue(inkPixels > 100, "the numeric display has readable themed ink");
      }
      AppPreferences.runWithPrintViewColors(() -> {
        org.junit.jupiter.api.Assertions.assertEquals(Color.BLACK, CanvasStyle.dataForeground());
        org.junit.jupiter.api.Assertions.assertEquals(Color.WHITE, CanvasStyle.dataBackground());
        org.junit.jupiter.api.Assertions.assertEquals(Color.BLACK, CanvasStyle.dataHighlightForeground());
      });
    } finally {
      UIManager.put("TextField.background", oldBackground);
      UIManager.put("TextField.foreground", oldForeground);
    }
  }

  @Test
  void paintRecalculatesLayoutWhenFontMetricsChange() {
    final var state = new MemState(MemContents.create(8, 8, false));
    final var image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_RGB);
    final var graphics = image.createGraphics();
    try {
      graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 8));
      state.paint(graphics, 0, 0, 0, 0, 100, 100, 1);
      final var smallFontLines = state.getNrOfLines();

      graphics.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 24));
      state.paint(graphics, 0, 0, 0, 0, 100, 100, 1);

      assertTrue(state.getNrOfLines() < smallFontLines);
    } finally {
      graphics.dispose();
    }
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.cburch.logisim.std.Builtin;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.UiScale;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * The palette shows each component's own symbol, which the component draws itself.
 *
 * <p>A tile with nothing in it is the failure that matters, and it can only be seen by drawing.
 */
class ToolPreviewIconTest {

  @Test
  void cachedPreviewRefreshesForDeviceScaleAndInterfaceZoom() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var original = UiScale.factor();
      final var tool = mock(Tool.class);
      final var image = new BufferedImage(200, 200, BufferedImage.TYPE_INT_ARGB);
      final var g = image.createGraphics();
      try {
        UiScale.setFactor(1);
        final var icon = new ToolPreviewIcon(tool);
        final var width = icon.getIconWidth();
        icon.paintIcon(null, g, 0, 0);
        icon.paintIcon(null, g, 0, 0);
        verify(tool, times(1)).paintIcon(any(), anyInt(), anyInt());
        g.scale(2, 2);
        icon.paintIcon(null, g, 0, 0);
        verify(tool, times(2)).paintIcon(any(), anyInt(), anyInt());
        UiScale.setFactor(2);
        assertEquals(width * 2, icon.getIconWidth());
        icon.paintIcon(null, g, 0, 0);
        verify(tool, times(3)).paintIcon(any(), anyInt(), anyInt());
      } finally {
        g.dispose();
        UiScale.setFactor(original);
      }
    });
  }

  private static BufferedImage render(Tool tool) {
    final var icon = new ToolPreviewIcon(tool, 40);
    final var image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
    final var graphics = image.createGraphics();
    try {
      icon.paintIcon(new JPanel(), graphics, 0, 0);
    } finally {
      graphics.dispose();
    }
    return image;
  }

  private static boolean hasInk(BufferedImage image) {
    for (var y = 0; y < image.getHeight(); y++) {
      for (var x = 0; x < image.getWidth(); x++) {
        if ((image.getRGB(x, y) >>> 24) != 0) return true;
      }
    }
    return false;
  }

  @Test
  void everyBuiltInComponentDrawsSomething() {
    final var blank = new ArrayList<String>();
    var drawn = 0;
    for (final var library : new Builtin().getLibraries()) {
      for (final var tool : library.getTools()) {
        if (!(tool instanceof AddTool)) continue;
        drawn++;
        if (!hasInk(render(tool))) blank.add(library.getName() + " / " + tool.getName());
      }
    }
    assertTrue(drawn > 100, "only " + drawn + " components were found to draw");
    assertTrue(blank.isEmpty(), "components that drew nothing: " + blank);
  }

  /** Painting again gives the same picture, whether it was drawn again or kept. */
  @Test
  void paintingTwiceGivesTheSamePicture() {
    final var icon = new ToolPreviewIcon(firstAddTool(), 40);
    final var panel = new JPanel();
    assertTrue(java.util.Arrays.equals(paint(icon, panel), paint(icon, panel)));
    assertFalse(icon.tool() == null);
  }

  private static int[] paint(ToolPreviewIcon icon, JPanel panel) {
    // A fresh image each time: drawing over the previous one would blend with it and say nothing.
    final var image = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
    final var graphics = image.createGraphics();
    try {
      icon.paintIcon(panel, graphics, 0, 0);
    } finally {
      graphics.dispose();
    }
    return image.getRGB(0, 0, 40, 40, null, 0, 40);
  }

  /** The icon knows its size before anything has been drawn, so a group can be laid out unopened. */
  @Test
  void theSizeIsKnownBeforePainting() {
    final var icon = new ToolPreviewIcon(firstAddTool(), 40);
    assertTrue(icon.getIconWidth() == 40 && icon.getIconHeight() == 40);
  }

  private static Tool firstAddTool() {
    for (final var library : new Builtin().getLibraries()) {
      for (final var tool : library.getTools()) {
        if (tool instanceof AddTool) return tool;
      }
    }
    throw new IllegalStateException("no components at all");
  }
}

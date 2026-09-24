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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.UiScale;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ComponentTileTest {
  private static Tool tool(String id, String name) {
    final var tool = mock(Tool.class);
    when(tool.getName()).thenReturn(id);
    when(tool.getDisplayName()).thenReturn(name);
    when(tool.getDescription()).thenReturn("A description without the component name");
    return tool;
  }

  @Test
  void narrowingViewportRemeasuresHeightBeforeOldGridBoundsAreUpdated() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var grid = new JPanel(new ComponentPalette.TileGridLayout());
      for (var i = 0; i < 4; i++) {
        final var tile = new JPanel();
        tile.setPreferredSize(new Dimension(112, 80));
        grid.add(tile);
      }
      final var viewport = new JViewport();
      viewport.setView(grid);
      viewport.setSize(320, 400);
      grid.setSize(320, 400);
      final var wideHeight = grid.getPreferredSize().height;
      viewport.setSize(180, 400);
      assertTrue(grid.getPreferredSize().height > wideHeight);
      grid.setSize(180, grid.getPreferredSize().height);
      grid.doLayout();
      for (final var child : grid.getComponents()) {
        assertTrue(new Rectangle(0, 0, 180, grid.getHeight()).contains(child.getBounds()));
      }
    });
  }

  @Test
  void captionsFitMeasuredRowsAtAllSupportedZoomsAndNarrowWidths() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var original = UiScale.factor();
      try {
        for (final var scale : new double[] {1, 1.25, 1.6, 2, 2.5}) {
          UiScale.setFactor(scale);
          for (final var width : new int[] {220, 320}) {
            final var grid = new JPanel(new ComponentPalette.TileGridLayout());
            grid.setBorder(BorderFactory.createEmptyBorder(
                0, Spacing.sm(), Spacing.sm(), Spacing.sm()));
            for (final var name : List.of("Floating Point Adder", "Floating Point Multiplier",
                "Floating Point Exponentiator", "Programmierbare Steuerung mit Zusatzfunktionen")) {
              final var tile = new ComponentTile(tool(name, name), unused -> {}, unused -> {});
              tile.setFont(new Font(Font.DIALOG, Font.PLAIN, (int) Math.round(13 * scale)));
              tile.setFavourite(true);
              grid.add(tile);
            }
            grid.setSize(width, 1);
            grid.setSize(width, grid.getPreferredSize().height);
            grid.doLayout();
            for (final var child : grid.getComponents()) {
              final var tile = (ComponentTile) child;
              final var metrics = tile.getFontMetrics(tile.getFont());
              final var lines = tile.visibleCaptionLines();
              assertTrue(tile.firstBaseline() - metrics.getAscent() >= 0);
              assertTrue(tile.firstBaseline() + (lines.size() - 1) * metrics.getHeight()
                  + metrics.getDescent()
                  <= tile.getHeight(), scale + "x caption falls outside tile");
              for (final var line : lines) {
                assertTrue(metrics.stringWidth(line) <= tile.captionWidth());
              }
              assertTrue(new Rectangle(0, 0, width, grid.getHeight()).contains(tile.getBounds()));
              assertTrue(new Rectangle(0, 0, tile.getWidth(), tile.getHeight())
                  .contains(tile.favouriteBounds()));
              final var image = new BufferedImage(tile.getWidth(), tile.getHeight(),
                  BufferedImage.TYPE_INT_ARGB);
              final var g = image.createGraphics();
              try {
                tile.paint(g);
              } finally {
                g.dispose();
              }
              var captionInk = false;
              for (var y = tile.firstBaseline() - metrics.getAscent(); y < tile.getHeight(); y++) {
                for (var x = 0; x < tile.getWidth(); x++) {
                  captionInk |= (image.getRGB(x, y) >>> 24) != 0;
                }
              }
              assertTrue(captionInk, "caption must paint, not just report a sufficient height");
            }
          }
        }
      } finally {
        UiScale.setFactor(original);
      }
    });
  }

  @Test
  void narrowHighZoomPaletteUsesCompactRowsAndShortNamesDoNotWrap() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var original = UiScale.factor();
      try {
        UiScale.setFactor(1.6);
        final var grid = new JPanel(new ComponentPalette.TileGridLayout());
        grid.setBorder(Spacing.border(0, Spacing.SM, Spacing.SM, Spacing.SM));
        final var gate = new ComponentTile(tool("NOT Gate", "NOT Gate"), unused -> {}, unused -> {});
        final var fp = new ComponentTile(tool("FPMultiplier", "Floating Point Multiplier"),
            unused -> {}, unused -> {});
        gate.setFont(new Font(Font.DIALOG, Font.PLAIN, 18));
        fp.setFont(gate.getFont());
        grid.add(gate);
        grid.add(fp);
        grid.setSize(320, 1);
        grid.setSize(320, grid.getPreferredSize().height);
        grid.doLayout();
        assertTrue(gate.isCompact());
        assertEquals(List.of("NOT Gate"), gate.visibleCaptionLines());
        assertEquals("Floating Point Multiplier", String.join(" ", fp.visibleCaptionLines()));
        assertTrue(gate.getHeight() < gate.getPreferredSize().height * 0.65,
            "narrow mode must improve density, not only keep the caption inside its bounds");
        assertTrue(gate.getHeight() <= 64, "320px panel at 1.6 must not have 120px gate rows");
        grid.setSize(640, 1);
        grid.setSize(640, grid.getPreferredSize().height);
        grid.doLayout();
        assertTrue(!gate.isCompact());
        assertEquals(List.of("NOT Gate"), gate.visibleCaptionLines());
        assertTrue(fp.visibleCaptionLines().size() <= 2);
      } finally {
        UiScale.setFactor(original);
      }
    });
  }

  @Test
  void distinguishingFloatingPointWordsAndFullNamesRemainAvailable() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var name = "Floating Point Multiplier";
      final var tile = new ComponentTile(tool("FPMultiplier", name), unused -> {}, unused -> {});
      final var metrics = tile.getFontMetrics(tile.getFont());
      final var lines = ComponentTile.captionLines(name, metrics, metrics.stringWidth("Point Multiplier"));
      assertEquals(name, String.join(" ", lines));
      assertEquals(name, tile.getAccessibleContext().getAccessibleName());
      assertTrue(tile.getToolTipText().startsWith(name));
      final var rom = new ComponentTile(tool("PlaRom", "PLA"), unused -> {}, unused -> {});
      assertEquals("PLA ROM", rom.getAccessibleContext().getAccessibleName());
      assertEquals("PlaRom", rom.tool().getName());
    });
  }

  @Test
  void starHitTargetOpensPinActionWithoutArmingTheComponent() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var chosen = new AtomicInteger();
      final var menu = new AtomicInteger();
      final var tile = new ComponentTile(tool("Pin", "Pin"),
          unused -> chosen.incrementAndGet(), unused -> menu.incrementAndGet());
      tile.setSize(tile.getPreferredSize());
      final var hit = tile.favouriteBounds();
      final var event = new MouseEvent(tile, MouseEvent.MOUSE_PRESSED, 0, 0,
          hit.x + hit.width / 2, hit.y + hit.height / 2, 1, false, MouseEvent.BUTTON1);
      for (final var listener : tile.getMouseListeners()) listener.mousePressed(event);
      assertEquals(1, menu.get());
      assertEquals(0, chosen.get());
    });
  }
}

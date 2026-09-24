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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.cburch.logisim.prefs.AppPreferences;
import org.junit.jupiter.api.Test;

/** The repeating tile the grid is drawn from. */
class GridPainterTest {

  private static final int SIZE = 10;

  @Test
  void hierarchySurvivesFractionalZoomAndMinorDotsDisappearWhenCrowded() {
    for (final var zoom : new double[] {0.25, 0.5, 0.75, 1.25, 1.6, 2.0, 4.0}) {
      final var width = GridPainter.tileWidth(SIZE, zoom);
      final var tile = GridPainter.gridPixels(SIZE, zoom, width);
      assertEquals(AppPreferences.GRID_DOT_COLOR.get().intValue(), pixel(tile, width, 0, 0));
      final var minor = zoom * SIZE < 4 ? AppPreferences.GRID_BG_COLOR.get()
          : AppPreferences.GRID_ZOOMED_DOT_COLOR.get();
      assertEquals(minor.intValue(), pixel(tile, width, (int) Math.round(SIZE * zoom), 0));
      assertEquals(AppPreferences.GRID_DOT_COLOR.get().intValue(),
          pixel(tile, width, (int) Math.round(5 * SIZE * zoom) % width, 0), "zoom=" + zoom);
    }
  }

  @Test
  void invalidZoomCannotTrapTheTileSizerInAnInfiniteLoop() {
    for (final var zoom : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY}) {
      assertThrows(IllegalArgumentException.class, () -> GridPainter.tileWidth(SIZE, zoom));
    }
  }

  private static int pixel(int[] tile, int width, int x, int y) {
    return tile[y * width + x];
  }

  /**
   * At its natural size the grid has two levels: a dot on every step and a stronger one every
   * fifth, which is what lets the eye judge a distance across the sheet.
   */
  @Test
  void theUnzoomedGridHasTwoLevels() {
    final var width = GridPainter.tileWidth(SIZE, 1.0);
    final var tile = GridPainter.gridPixels(SIZE, 1.0, width);

    final var major = pixel(tile, width, 0, 0);
    final var minor = pixel(tile, width, SIZE, 0);
    assertNotEquals(major, minor, "a fifth dot must differ from its neighbours");
    assertEquals(AppPreferences.GRID_DOT_COLOR.get().intValue(), major);
    assertEquals(AppPreferences.GRID_ZOOMED_DOT_COLOR.get().intValue(), minor);
  }

  @Test
  void everyFifthDotInBothDirectionsIsAMajorOne() {
    final var width = GridPainter.tileWidth(SIZE, 1.0);
    final var tile = GridPainter.gridPixels(SIZE, 1.0, width);
    final var major = AppPreferences.GRID_DOT_COLOR.get().intValue();
    final var minor = AppPreferences.GRID_ZOOMED_DOT_COLOR.get().intValue();

    for (var y = 0; y < width; y += SIZE) {
      for (var x = 0; x < width; x += SIZE) {
        final var expectMajor = (x / SIZE) % 5 == 0 && (y / SIZE) % 5 == 0;
        assertEquals(
            expectMajor ? major : minor,
            pixel(tile, width, x, y),
            "dot at " + x + "," + y);
      }
    }
  }

  /** Between the dots is background, or the grid would be a field of lines. */
  @Test
  void theSpaceBetweenDotsIsBackground() {
    final var width = GridPainter.tileWidth(SIZE, 1.0);
    final var tile = GridPainter.gridPixels(SIZE, 1.0, width);
    final var background = AppPreferences.GRID_BG_COLOR.get().intValue();

    assertEquals(background, pixel(tile, width, 1, 0));
    assertEquals(background, pixel(tile, width, SIZE / 2, SIZE / 2));
  }

  /** The tile is big enough to be worth blitting, and square. */
  @Test
  void theTileIsLargeEnoughToBlit() {
    for (final var zoom : new double[] {0.25, 0.5, 1.0, 2.0, 4.0}) {
      final var width = GridPainter.tileWidth(SIZE, zoom);
      assertEquals(width * width, GridPainter.gridPixels(SIZE, zoom, width).length);
    }
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.canvas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.circuit.Wire;
import java.awt.BasicStroke;
import org.junit.jupiter.api.Test;

/** The drawing style, and in particular the promise it makes about printing. */
class CanvasStyleTest {

  @Test
  void strokesAreRoundedAtEveryWeight() {
    for (final var width : new float[] {1f, 2f, 3f, 7f}) {
      final var stroke = (BasicStroke) CanvasStyle.stroke(width);
      assertEquals(BasicStroke.CAP_ROUND, stroke.getEndCap(), "cap at width " + width);
      assertEquals(BasicStroke.JOIN_ROUND, stroke.getLineJoin(), "join at width " + width);
      assertEquals(width, stroke.getLineWidth(), "weight at width " + width);
    }
  }

  @Test
  void strokesAreNeverThinnerThanOnePixel() {
    assertEquals(1f, ((BasicStroke) CanvasStyle.stroke(0f)).getLineWidth());
    assertEquals(1f, ((BasicStroke) CanvasStyle.stroke(-4f)).getLineWidth());
  }

  /**
   * Everything that marks where the pointer or the selection is must vanish when printing. An
   * exported image or a printed sheet has no pointer, and an accent colour chosen to stand out on
   * a screen prints as a smudge.
   */
  @Test
  void markersDisappearWhenPrinting() {
    assertEquals(0, CanvasStyle.markerWash(true).getAlpha());
    assertEquals(0, CanvasStyle.halo(true).getAlpha());
    assertEquals(0, CanvasStyle.wireHighlight(true).getAlpha());
  }

  @Test
  void markersAreVisibleOnScreen() {
    assertTrue(CanvasStyle.markerWash(false).getAlpha() > 0);
    assertTrue(CanvasStyle.halo(false).getAlpha() > 0);
    assertTrue(CanvasStyle.wireHighlight(false).getAlpha() > 0);
  }

  /** On paper a selection is still worth showing, but in ink rather than in the accent colour. */
  @Test
  void theMarkerColourDiffersBetweenScreenAndPrint() {
    assertNotEquals(CanvasStyle.marker(false), CanvasStyle.marker(true));
    assertEquals(255, CanvasStyle.marker(true).getAlpha());
  }

  /** A highlight is drawn around a wire, so it has to be wider than the wire at every weight. */
  @Test
  void highlightSurroundsTheWireItMarks() {
    assertTrue(CanvasStyle.highlightWidth(Wire.WIDTH) > Wire.WIDTH);
    assertTrue(CanvasStyle.highlightWidth(Wire.WIDTH_BUS) > Wire.WIDTH_BUS);
  }

  /**
   * The simulator's three markers were raw RED, BLUE and MAGENTA painted straight onto the canvas,
   * the last of them with the author's "fixme" beside it.
   */
  @Test
  void simulationMarkersAreDistinctOnScreen() {
    final var oscillation = CanvasStyle.oscillationMarker(false);
    final var step = CanvasStyle.stepMarker(false);
    final var pending = CanvasStyle.pendingInputMarker(false);
    assertNotEquals(oscillation, step);
    assertNotEquals(step, pending);
    assertNotEquals(oscillation, pending);
  }

  /** On paper they all become ink: a marker pointing at a running simulation means nothing there. */
  @Test
  void simulationMarkersBecomeInkWhenPrinting() {
    assertEquals(CanvasStyle.componentColor(), CanvasStyle.oscillationMarker(true));
    assertEquals(CanvasStyle.componentColor(), CanvasStyle.stepMarker(true));
    assertEquals(CanvasStyle.componentColor(), CanvasStyle.pendingInputMarker(true));
  }

  /** A bus has to be tellable from a wire at a glance, which one pixel of difference is not. */
  @Test
  void busIsClearlyHeavierThanWire() {
    assertTrue(Wire.WIDTH_BUS - Wire.WIDTH >= 2, "bus " + Wire.WIDTH_BUS + " vs " + Wire.WIDTH);
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.canvas;

import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.prefs.AppPreferences;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Stroke;

/**
 * How a circuit is drawn: line ends, weights, corner radii, and the colours used to mark things
 * rather than to describe them.
 *
 * <p>The drawing code is spread over a hundred component painters, but the decisions that make a
 * drawing look the way it does are few, and they pass through a handful of places: the one method
 * that sets a stroke, the shared primitives that draw a body, a pin or a handle, and the colours
 * used for selection and hover. Those are gathered here so the canvas has a described appearance
 * instead of an accumulated one.
 *
 * <p>Component colours themselves are not here: they are preferences, already stored per theme,
 * and a component is free to paint itself in whatever colours it means something by.
 */
public final class CanvasStyle {

  // SansSerif has an explicit raster/SVG/TikZ mapping; Dialog is a UI-only font alias.
  private static final Font DOCUMENT_FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

  /** Default internal component text, in circuit units, never multiplied by interface scale. */
  public static Font documentFont() {
    return DOCUMENT_FONT;
  }

  /**
   * Unscaled radius of a rounded component body.
   *
   * <p>Small on purpose: a component is a schematic symbol and must still read as a rectangle.
   */
  private static final int BODY_RADIUS = 4;

  /** Unscaled edge of a selection handle. */
  private static final int HANDLE_SIZE = 7;

  /** How far a selection outline sits outside the thing it marks, unscaled. */
  private static final int SELECTION_INSET = 3;

  /** How much wider than the wire itself a highlight halo is drawn. */
  private static final int HIGHLIGHT_SPREAD = 6;

  private CanvasStyle() {
    throw new UnsupportedOperationException("Utility class, do not instantiate.");
  }

  /**
   * The stroke for a line of the given nominal width.
   *
   * <p>Rounded ends and joins, which is the single change that most separates the drawing from the
   * mitred, square-ended look it had: wires meeting at a corner no longer show a notch, and a
   * stub does not end in a blunt rectangle.
   */
  public static Stroke stroke(float width) {
    return new BasicStroke(
        Math.max(1f, width), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
  }

  /** The radius of a component body's corner, in circuit units. */
  public static int bodyRadius() {
    return BODY_RADIUS;
  }

  /** The edge length of a selection handle, in circuit units. */
  public static int handleSize() {
    return HANDLE_SIZE;
  }

  /** How far a selection outline sits outside its component, in circuit units. */
  public static int selectionInset() {
    return SELECTION_INSET;
  }

  /**
   * The colour that marks something the user has chosen: a selection, a handle, a hover target.
   *
   * <p>Printing and image export get the ink colour instead — a marker means nothing on paper and
   * an accent colour chosen for a screen prints badly.
   */
  public static Color marker(boolean printView) {
    return printView ? componentColor() : Tokens.accent();
  }

  /** A translucent wash for the interior of a selection rectangle. */
  public static Color markerWash(boolean printView) {
    if (printView) return new Color(0, 0, 0, 0);
    final var accent = Tokens.accent();
    return new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 36);
  }

  /** A faint ring for the component whose properties are being shown. */
  public static Color halo(boolean printView) {
    if (printView) return new Color(0, 0, 0, 0);
    final var accent = Tokens.accent();
    return new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 110);
  }

  /** The ink a component is drawn in, from the preferences, honouring the print palette. */
  public static Color componentColor() {
    return new Color(AppPreferences.COMPONENT_COLOR.get());
  }

  /** Black or white ink with the higher WCAG contrast against an opaque signal fill. */
  public static Color contrastInk(Color fill) {
    final var luminance = 0.2126 * linearChannel(fill.getRed())
        + 0.7152 * linearChannel(fill.getGreen())
        + 0.0722 * linearChannel(fill.getBlue());
    final var blackContrast = (luminance + 0.05) / 0.05;
    final var whiteContrast = 1.05 / (luminance + 0.05);
    return blackContrast >= whiteContrast ? Color.BLACK : Color.WHITE;
  }

  private static double linearChannel(int channel) {
    final var value = channel / 255.0;
    return value <= 0.04045
        ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
  }

  /** Paired colors for automatic numeric displays, not explicit document colors. */
  public static Color dataBackground() {
    return AppPreferences.inPrintView()
        ? Color.WHITE : Tokens.color("TextField.background", Color.WHITE);
  }

  public static Color dataForeground() {
    return AppPreferences.inPrintView()
        ? Color.BLACK : Tokens.color("TextField.foreground", Color.BLACK);
  }

  public static Color dataHighlightBackground() {
    return AppPreferences.inPrintView()
        ? Color.LIGHT_GRAY : Tokens.color("TextField.selectionBackground", new Color(0x245CB4));
  }

  public static Color dataHighlightForeground() {
    return AppPreferences.inPrintView()
        ? Color.BLACK : Tokens.color("TextField.selectionForeground", Color.WHITE);
  }

  /**
   * The width of the halo drawn under a highlighted wire.
   *
   * <p>The highlight is drawn around the wire rather than as a change to the wire, so that a wire
   * under the pointer does not appear to gain weight or change into a dashed line. Its apparent
   * geometry is information: it says how wide the net is.
   */
  public static int highlightWidth(int wireWidth) {
    return wireWidth + HIGHLIGHT_SPREAD;
  }

  /**
   * The colour of that halo.
   *
   * <p>Nothing is drawn when printing: a highlight follows the pointer and is meaningless on paper.
   */
  public static Color wireHighlight(boolean printView) {
    if (printView) return new Color(0, 0, 0, 0);
    final var accent = Tokens.accent();
    return new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 96);
  }

  /** Where the simulator found a signal that will not settle. */
  public static Color oscillationMarker(boolean printView) {
    return printView ? componentColor() : Tokens.error();
  }

  /** Where the simulator has got to, while stepping. */
  public static Color stepMarker(boolean printView) {
    return printView ? componentColor() : Tokens.accent();
  }

  /** An input the simulator has not carried through yet. */
  public static Color pendingInputMarker(boolean printView) {
    return printView ? componentColor() : Tokens.warning();
  }

  /** The colour of a component being dragged or about to be placed. */
  public static Color ghost() {
    return new Color(AppPreferences.COMPONENT_GHOST_COLOR.get());
  }
}

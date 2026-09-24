/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.util;

import java.awt.Font;
import javax.swing.UIManager;

/**
 * Shared typographic scale for the user interface.
 *
 * <p>Fonts used to be constructed in place with a hard coded family and size, which is why parts of
 * the application still show fixed size {@code Serif} text that ignores both the look and feel and
 * the user's interface scale. Every font here is derived from the look and feel's label font, so a
 * theme change or a different application font flows through automatically. The look and feel has
 * already scaled these fonts; only the role's relative size is applied here.
 *
 * <p>These fonts are for interface chrome. Fonts that belong to the circuit itself, such as the ones
 * in {@code com.cburch.draw.shapes.DrawAttr}, are part of the saved document and must not be derived
 * from the current theme.
 */
public final class UiFonts {

  /** Size, relative to the base font, of secondary text. */
  private static final float SMALL_RATIO = 0.85f;

  /** Size, relative to the base font, of a section heading. */
  private static final float HEADING_RATIO = 1.2f;

  /** Used only when the look and feel exposes no label font. */
  private static final Font FALLBACK = new Font(Font.DIALOG, Font.PLAIN, 12);

  private UiFonts() {
    // Utility class, not instantiable.
  }

  /**
   * Returns the look and feel's label font, scaled to the user's interface scale.
   *
   * <p>This is the default font for interface text.
   */
  public static Font body() {
    return scale(base(), 1.0f, base().getStyle());
  }

  /** Returns {@link #body()} in bold, for emphasis and column headers. */
  public static Font bodyBold() {
    return scale(base(), 1.0f, Font.BOLD);
  }

  /** Returns a smaller font, for captions and secondary annotations. */
  public static Font small() {
    return scale(base(), SMALL_RATIO, base().getStyle());
  }

  /** Returns a larger bold font, for section headings. */
  public static Font heading() {
    return scale(base(), HEADING_RATIO, Font.BOLD);
  }

  /**
   * Returns a monospaced font metrically suited to tabular data, at the body text size.
   *
   * <p>Use this for value tables and other columns that must align, rather than naming a specific
   * family such as {@code Courier}, which is not present on every platform.
   */
  public static Font mono() {
    return new Font(Font.MONOSPACED, Font.PLAIN, body().getSize());
  }

  /** Returns {@link #mono()} in bold. */
  public static Font monoBold() {
    return new Font(Font.MONOSPACED, Font.BOLD, body().getSize());
  }

  /** Returns the look and feel's label font, or a fallback when it exposes none. */
  private static Font base() {
    final var font = UIManager.getFont("Label.font");
    return (font == null) ? FALLBACK.deriveFont(UiScale.scaled(FALLBACK.getSize2D())) : font;
  }

  /** Derives a font at the given ratio of the base size, scaled and rounded to a whole point. */
  private static Font scale(Font font, float ratio, int style) {
    final var size = Math.max(1.0f, font.getSize2D() * ratio);
    return font.deriveFont(style, Math.round(size));
  }
}

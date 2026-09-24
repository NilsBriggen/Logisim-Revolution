/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.instance;

import com.cburch.logisim.data.Bounds;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;

/** Opt-in, single-line previews for built-in captions, never for document label attributes. */
public final class ComponentCaption {
  public static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 12);

  private ComponentCaption() {}

  /** Keeps complete Unicode code points and the caller's font; never scales text to fit. */
  public static String fit(FontMetrics metrics, String text, int width) {
    if (metrics.stringWidth(text) <= width) return text;
    final var ellipsis = "…";
    if (metrics.stringWidth(ellipsis) > width) return "";
    var end = text.length();
    while (end > 0) {
      end = text.offsetByCodePoints(end, -1);
      final var shortened = text.substring(0, end) + ellipsis;
      if (metrics.stringWidth(shortened) <= width) return shortened;
    }
    return ellipsis;
  }

  /** Paints a bounded preview; callers must expose the full text through their tooltip/attributes. */
  public static void draw(Graphics graphics, String text, Bounds bounds, Font font) {
    final var g = graphics.create();
    try {
      g.setFont(font);
      g.clipRect(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
      final var fm = g.getFontMetrics();
      final var visible = fit(fm, text, Math.max(0, bounds.getWidth() - 4));
      final var x = bounds.getX() + (bounds.getWidth() - fm.stringWidth(visible)) / 2;
      final var y = bounds.getY()
          + (bounds.getHeight() - fm.getAscent() - fm.getDescent()) / 2 + fm.getAscent();
      g.drawString(visible, x, y);
    } finally {
      g.dispose();
    }
  }
}

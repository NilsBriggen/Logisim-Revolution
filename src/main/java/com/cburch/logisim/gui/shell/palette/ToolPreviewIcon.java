/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell.palette;

import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.tools.Tool;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.swing.Icon;

/**
 * A component's own symbol, drawn small.
 *
 * <p>The palette shows a few hundred of these at once, and each one is drawn by the component
 * itself — the same code that draws it on the canvas — so it is worth drawing only once. The
 * picture is kept until the theme, interface zoom, or device transform changes.
 *
 * <p>Nothing is drawn until the icon is asked to paint, so a group that has not been opened costs
 * nothing.
 */
public final class ToolPreviewIcon implements Icon {

  /**
   * Bumped whenever the theme changes, which retires every picture drawn before it.
   *
   * <p>A counter rather than a list of icons to clear: an icon belonging to a tile that has been
   * thrown away should not be kept alive just so it can be told about a theme it will never be
   * drawn in.
   */
  private static volatile int generation;

  static {
    Theme.addListener(() -> generation++);
  }

  private final Tool tool;
  private final Integer fixedSize;

  private BufferedImage picture;
  private int pictureGeneration = -1;
  private double pictureScaleX;
  private double pictureScaleY;
  private int pictureSize;

  public ToolPreviewIcon(Tool tool) {
    this.tool = tool;
    fixedSize = null;
  }

  public ToolPreviewIcon(Tool tool, int size) {
    this.tool = tool;
    fixedSize = Math.max(1, size);
  }

  @Override
  public int getIconHeight() {
    return fixedSize == null ? AppPreferences.getScaled(AppPreferences.BOX_SIZE) : fixedSize;
  }

  @Override
  public int getIconWidth() {
    return getIconHeight();
  }

  @Override
  public void paintIcon(Component component, Graphics graphics, int x, int y) {
    final var current = generation;
    final var size = getIconWidth();
    final var transform = ((Graphics2D) graphics).getTransform();
    final var scaleX = Math.max(1, Math.hypot(transform.getScaleX(), transform.getShearY()));
    final var scaleY = Math.max(1, Math.hypot(transform.getScaleY(), transform.getShearX()));
    if (picture == null || pictureGeneration != current || pictureSize != size
        || pictureScaleX != scaleX || pictureScaleY != scaleY) {
      picture = draw(component, size, scaleX, scaleY);
      pictureGeneration = current;
      pictureSize = size;
      pictureScaleX = scaleX;
      pictureScaleY = scaleY;
    }
    graphics.drawImage(picture, x, y, size, size, null);
  }

  /** Renders the tool once, on a transparent background so the tile's own colours show through. */
  private BufferedImage draw(Component component, int size, double scaleX, double scaleY) {
    final var image = new BufferedImage((int) Math.ceil(size * scaleX),
        (int) Math.ceil(size * scaleY), BufferedImage.TYPE_INT_ARGB);
    final var base = image.createGraphics();
    try {
      base.scale(scaleX, scaleY);
      base.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      base.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
      base.setColor(new Color(AppPreferences.COMPONENT_ICON_COLOR.get()));
      final var iconGraphics = base.create();
      try {
        final var context =
            new ComponentDrawContext(component, null, null, base, iconGraphics);
        final var border = AppPreferences.getScaled(AppPreferences.ICON_BORDER);
        tool.paintIcon(context, border, border);
      } finally {
        iconGraphics.dispose();
      }
    } catch (RuntimeException drawingFailed) {
      // A component from a library the program did not write can throw while drawing itself.
      // A palette that refuses to open because one icon is broken is worse than a blank tile.
      return image;
    } finally {
      base.dispose();
    }
    return image;
  }

  /** The tool this icon shows. */
  public Tool tool() {
    return tool;
  }
}

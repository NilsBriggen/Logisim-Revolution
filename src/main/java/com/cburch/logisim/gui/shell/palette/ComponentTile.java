/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell.palette;

import com.cburch.logisim.gui.theme.AppIcons;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.UiFonts;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Consumer;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/**
 * One component in the palette: its own symbol above its name.
 *
 * <p>A tile rather than a row in a tree. A component is recognised by its shape long before its
 * name is read, so the shape is what the palette shows, at a size that can be hit without aiming.
 */
public class ComponentTile extends JComponent {

  private static final long serialVersionUID = 1L;

  /** How much of the tile the symbol gets before the caption starts. */
  private static final int PREVIEW_HEIGHT = 40;

  /** Enough for two short words at the palette's font; longer names are shortened. */
  private static final int WIDTH = 68;

  private static final int HEIGHT = PREVIEW_HEIGHT + 22;

  private final Tool tool;
  private final ToolPreviewIcon preview;
  private final Consumer<Tool> onChoose;
  private final Consumer<ComponentTile> onContextMenu;

  private boolean hovered;
  private boolean current;
  private boolean favourite;

  public ComponentTile(Tool tool, Consumer<Tool> onChoose, Consumer<ComponentTile> onContextMenu) {
    this.tool = tool;
    this.preview = new ToolPreviewIcon(tool);
    this.onChoose = onChoose;
    this.onContextMenu = onContextMenu;

    setPreferredSize(new Dimension(WIDTH, HEIGHT));
    setMinimumSize(new Dimension(WIDTH, HEIGHT));
    setMaximumSize(new Dimension(WIDTH, HEIGHT));
    setFocusable(true);
    setToolTipText(describe());

    addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseEntered(MouseEvent event) {
            hovered = true;
            repaint();
          }

          @Override
          public void mouseExited(MouseEvent event) {
            hovered = false;
            repaint();
          }

          @Override
          public void mousePressed(MouseEvent event) {
            requestFocusInWindow();
            if (SwingUtilities.isRightMouseButton(event)) {
              onContextMenu.accept(ComponentTile.this);
            } else {
              onChoose.accept(tool);
            }
          }
        });

    addKeyListener(
        new KeyAdapter() {
          @Override
          public void keyPressed(KeyEvent event) {
            if (event.getKeyCode() == KeyEvent.VK_ENTER
                || event.getKeyCode() == KeyEvent.VK_SPACE) {
              onChoose.accept(tool);
            }
          }
        });

    addFocusListener(
        new FocusAdapter() {
          @Override
          public void focusGained(FocusEvent event) {
            repaint();
          }

          @Override
          public void focusLost(FocusEvent event) {
            repaint();
          }
        });

    Theme.addListener(this::repaint);
  }

  private String describe() {
    final var description = tool.getDescription();
    return description == null || description.isBlank() ? tool.getDisplayName() : description;
  }

  public Tool tool() {
    return tool;
  }

  /** Whether this is the tool the canvas is currently using. */
  public void setCurrent(boolean current) {
    if (this.current == current) return;
    this.current = current;
    repaint();
  }

  /** Whether the user has marked this component to keep it at the top of the palette. */
  public void setFavourite(boolean favourite) {
    if (this.favourite == favourite) return;
    this.favourite = favourite;
    repaint();
  }

  public boolean isFavourite() {
    return favourite;
  }

  @Override
  protected void paintComponent(Graphics graphics) {
    final var g = (Graphics2D) graphics.create();
    try {
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      final var width = getWidth();
      final var height = getHeight();

      if (current || hovered || isFocusOwner()) {
        final var accent = Tokens.accent();
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(),
            current ? 46 : 24));
        g.fillRoundRect(0, 0, width - 1, height - 1, 8, 8);
      }
      if (current || isFocusOwner()) {
        g.setColor(Tokens.accent());
        g.setStroke(new BasicStroke(current ? 1.6f : 1f));
        g.drawRoundRect(0, 0, width - 1, height - 1, 8, 8);
      }

      final var iconX = (width - preview.getIconWidth()) / 2;
      final var iconY = Spacing.xs() + (PREVIEW_HEIGHT - preview.getIconHeight()) / 2;
      preview.paintIcon(this, g, Math.max(0, iconX), Math.max(0, iconY));

      if (favourite) {
        AppIcons.colored(AppIcons.Id.STAR, 10, Tokens.accent())
            .paintIcon(this, g, width - 14, 4);
      }

      g.setFont(UiFonts.small());
      g.setColor(current ? Tokens.accent() : Tokens.mutedForeground());
      final var metrics = g.getFontMetrics();
      final var label = shorten(tool.getDisplayName(), metrics, width - Spacing.xs() * 2);
      g.drawString(
          label,
          (width - metrics.stringWidth(label)) / 2f,
          PREVIEW_HEIGHT + Spacing.sm() + metrics.getAscent() - 2f);
    } finally {
      g.dispose();
    }
  }

  /** Trims a name that will not fit, ending it with an ellipsis rather than cutting a letter. */
  private static String shorten(String text, java.awt.FontMetrics metrics, int available) {
    if (text == null) return "";
    if (metrics.stringWidth(text) <= available) return text;
    final var ellipsis = "…";
    var end = text.length();
    while (end > 1 && metrics.stringWidth(text.substring(0, end) + ellipsis) > available) end--;
    return text.substring(0, end) + ellipsis;
  }
}

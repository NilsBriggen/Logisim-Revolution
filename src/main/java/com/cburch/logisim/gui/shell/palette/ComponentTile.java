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
import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.UiFonts;
import com.cburch.logisim.util.UiScale;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import java.util.function.Consumer;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;

/** A component symbol and a measured, two-line caption. */
public class ComponentTile extends JComponent {
  private static final long serialVersionUID = 1L;

  private final Tool tool;
  private final ToolPreviewIcon preview;
  private boolean hovered;
  private boolean current;
  private boolean favourite;
  private boolean compact;

  public ComponentTile(Tool tool, Consumer<Tool> onChoose, Consumer<ComponentTile> onContextMenu) {
    this.tool = tool;
    preview = new ToolPreviewIcon(tool);
    setFont(UiFonts.small());
    setForeground(javax.swing.UIManager.getColor("Label.foreground"));
    setFocusable(true);
    setToolTipText(describe());
    getAccessibleContext().setAccessibleName(caption());
    getAccessibleContext().setAccessibleDescription(describe());

    addMouseListener(new MouseAdapter() {
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
        setFocusable(true);
        requestFocusInWindow();
        if (SwingUtilities.isRightMouseButton(event)
            || (SwingUtilities.isLeftMouseButton(event)
                && favouriteBounds().contains(event.getPoint()))) {
          onContextMenu.accept(ComponentTile.this);
        } else if (SwingUtilities.isLeftMouseButton(event)) {
          onChoose.accept(tool);
        }
      }
    });
    addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.VK_ENTER || event.getKeyCode() == KeyEvent.VK_SPACE) {
          onChoose.accept(tool);
          event.consume();
        } else if (event.getKeyCode() == KeyEvent.VK_CONTEXT_MENU
            || (event.getKeyCode() == KeyEvent.VK_F10 && event.isShiftDown())) {
          onContextMenu.accept(ComponentTile.this);
          event.consume();
        }
      }
    });
    addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent event) {
        scrollRectToVisible(new Rectangle(0, 0, getWidth(), getHeight()));
        repaint();
      }

      @Override
      public void focusLost(FocusEvent event) {
        repaint();
      }
    });
    // No global subscription: the palette owns theme refresh and discarded tiles can be collected.
  }

  private String describe() {
    final var description = tool.getDescription();
    final var name = caption();
    return description == null || description.isBlank() || description.equals(name)
        ? name : name + " — " + description;
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) {
      accessibleContext = new AccessibleJComponent() {
        @Override
        public AccessibleRole getAccessibleRole() {
          return AccessibleRole.PUSH_BUTTON;
        }
      };
    }
    return accessibleContext;
  }

  @Override
  public void updateUI() {
    super.updateUI();
    setFont(UiFonts.small());
    setForeground(javax.swing.UIManager.getColor("Label.foreground"));
    revalidate();
  }

  @Override
  public Dimension getPreferredSize() {
    final var metrics = getFontMetrics(getFont());
    var width = UiScale.scaled(112);
    for (final var line : balancedLines(caption(), metrics)) {
      width = Math.max(width, metrics.stringWidth(line) + Spacing.sm() * 2);
    }
    return new Dimension(Math.min(UiScale.scaled(168), width), contentHeight());
  }

  @Override
  public Dimension getMinimumSize() {
    return new Dimension(preview.getIconWidth() + Spacing.sm() * 2, contentHeight());
  }

  private String caption() {
    // Both factories are named PLA in legacy bundles; their serialized IDs stay untouched.
    return "PlaRom".equals(tool.getName()) ? tool.getDisplayName() + " ROM" : tool.getDisplayName();
  }

  private int previewHeight() {
    return Math.max(preview.getIconHeight(), favouriteBounds().height);
  }

  private int contentHeight() {
    return Spacing.sm() * 2 + previewHeight() + Spacing.xs()
        + getFontMetrics(getFont()).getHeight() * 2;
  }

  void setCompact(boolean compact) {
    this.compact = compact;
  }

  boolean isCompact() {
    return compact;
  }

  private int pinSize() {
    return Math.max(UiScale.scaled(20), getFontMetrics(getFont()).getHeight());
  }

  private int compactTextX() {
    return Spacing.xs() + preview.getIconWidth() + Spacing.sm();
  }

  private int compactTextWidth(int width) {
    return Math.max(0, width - compactTextX() - pinSize() - Spacing.sm());
  }

  int compactHeight(int width) {
    final var metrics = getFontMetrics(getFont());
    final var lines = captionLines(caption(), metrics, compactTextWidth(width)).size();
    return Spacing.xs() * 2
        + Math.max(previewHeight(), metrics.getHeight() * lines);
  }

  int captionWidth() {
    return compact ? compactTextWidth(getWidth()) : getWidth() - Spacing.sm() * 2;
  }

  List<String> visibleCaptionLines() {
    return captionLines(caption(), getFontMetrics(getFont()), captionWidth());
  }

  int firstBaseline() {
    final var metrics = getFontMetrics(getFont());
    if (compact) {
      return (getHeight() - metrics.getHeight() * visibleCaptionLines().size()) / 2
          + metrics.getAscent();
    }
    return Spacing.sm() + previewHeight() + Spacing.xs()
        + metrics.getAscent();
  }

  Rectangle favouriteBounds() {
    final var size = pinSize();
    return new Rectangle(Math.max(0, getWidth() - size - Spacing.xs()),
        compact ? Math.max(0, (getHeight() - size) / 2) : Spacing.xs(), size, size);
  }

  public Tool tool() {
    return tool;
  }

  public void setCurrent(boolean current) {
    if (this.current == current) return;
    this.current = current;
    repaint();
  }

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
      final var radius = UiScale.scaled(8);
      if (current || hovered || isFocusOwner()) {
        final var accent = Tokens.accent();
        g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(),
            current ? 46 : 24));
        g.fillRoundRect(0, 0, width - 1, height - 1, radius, radius);
      }
      if (current || isFocusOwner()) {
        g.setColor(Tokens.accent());
        g.setStroke(new BasicStroke(current ? 1.6f : 1f));
        g.drawRoundRect(0, 0, width - 1, height - 1, radius, radius);
      }
      final var iconX = compact ? Spacing.xs() : (width - preview.getIconWidth()) / 2;
      final var iconY = compact ? (height - preview.getIconHeight()) / 2
          : Spacing.sm() + (previewHeight() - preview.getIconHeight()) / 2;
      preview.paintIcon(this, g, Math.max(0, iconX), Math.max(0, iconY));
      if (favourite || hovered || isFocusOwner()) {
        final var bounds = favouriteBounds();
        final var star = AppIcons.colored(AppIcons.Id.STAR, 12,
            favourite ? Tokens.accent() : Tokens.mutedForeground());
        star.paintIcon(this, g, bounds.x + (bounds.width - star.getIconWidth()) / 2,
            bounds.y + (bounds.height - star.getIconHeight()) / 2);
      }
      g.setFont(getFont());
      g.setColor(current ? Tokens.accent() : getForeground());
      final var metrics = g.getFontMetrics();
      var baseline = firstBaseline();
      for (final var line : visibleCaptionLines()) {
        g.drawString(line, compact ? compactTextX() : (width - metrics.stringWidth(line)) / 2f,
            baseline);
        baseline += metrics.getHeight();
      }
    } finally {
      g.dispose();
    }
  }

  /** Balance at word boundaries, retaining the distinguishing suffix in a narrow column. */
  static List<String> captionLines(String text, FontMetrics metrics, int available) {
    if (text == null || text.isBlank()) return List.of("");
    text = text.trim();
    if (metrics.stringWidth(text) <= available) return List.of(text);
    return balancedLines(text, metrics).stream()
        .map(line -> shorten(line, metrics, available)).toList();
  }

  private static List<String> balancedLines(String text, FontMetrics metrics) {
    if (text == null || text.isBlank()) return List.of("");
    text = text.trim();
    var split = -1;
    var bestWidth = Integer.MAX_VALUE;
    for (var i = 1; i < text.length() - 1; i++) {
      if (!Character.isWhitespace(text.charAt(i))) continue;
      final var width = Math.max(metrics.stringWidth(text.substring(0, i).trim()),
          metrics.stringWidth(text.substring(i + 1).trim()));
      if (width < bestWidth) {
        bestWidth = width;
        split = i;
      }
    }
    if (split < 0) return List.of(text);
    return List.of(text.substring(0, split).trim(), text.substring(split + 1).trim());
  }

  private static String shorten(String text, FontMetrics metrics, int available) {
    if (metrics.stringWidth(text) <= available) return text;
    if (metrics.stringWidth("…") > available) return "";
    var left = text.codePointCount(0, text.length()) / 2;
    var right = left;
    while (left + right > 0) {
      final var shortened = text.substring(0, text.offsetByCodePoints(0, left)) + "…"
          + text.substring(text.offsetByCodePoints(text.length(), -right));
      if (metrics.stringWidth(shortened) <= available) return shortened;
      if (left >= right) left--;
      else right--;
    }
    return "…";
  }
}

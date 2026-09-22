/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.draw.toolbar;

import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.UiScale;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.UIManager;

/**
 * A single item in a {@link Toolbar}.
 *
 * <p>The glyph itself is drawn by the {@link ToolbarItem}, because items paint live circuit
 * components rather than static images. Everything around the glyph, the hover, pressed and selected
 * backgrounds, is drawn here from look and feel colours so the toolbar reacts to the pointer the way
 * the rest of the interface does.
 *
 * <p>The backgrounds are painted rather than left to the look and feel because the application lets
 * the user pick any installed look and feel, and only some of them style a borderless button.
 */
class ToolbarButton extends JButton {
  private static final long serialVersionUID = 1L;

  /** Unscaled padding between the button edge and the item's glyph. */
  private static final int BORDER = 2;

  /** Unscaled corner radius of the painted state background. */
  private static final int ARC = 6;

  /** Opacity of the selected background, which sits under the glyph. */
  private static final int SELECTED_ALPHA = 80;

  /** Opacity of the hover background. */
  private static final int HOVER_ALPHA = 40;

  private final Toolbar toolbar;
  private final ToolbarItem item;

  /** False for decorations such as separators, which must not react to the pointer. */
  private final boolean interactive;

  ToolbarButton(Toolbar toolbar, ToolbarItem item) {
    this.toolbar = toolbar;
    this.item = item;
    this.interactive =
        (item != null) && (item.isSelectable() || item instanceof ToolbarClickableItem);

    setIcon(new ItemIcon());
    setToolTipText("");
    // Icon-only, so a screen reader would otherwise have nothing to announce.
    getAccessibleContext().setAccessibleName(item == null ? null : item.getToolTip());
    // Deliberately left enabled: disabling would make Swing substitute an automatically derived
    // disabled icon, which would change how decorations such as separators are drawn.
    setFocusable(interactive);
    // The state backgrounds are painted by this class, so the look and feel must not draw its own
    // button decoration on top of, or underneath, the item's glyph.
    applyFlatStyling();
    addActionListener(event -> activate());
  }

  @Override
  public void updateUI() {
    super.updateUI();
    // Switching the look and feel reinstalls that theme's button border and paint flags, which
    // would put a frame back around every toolbar item. Re-apply the borderless styling.
    applyFlatStyling();
  }

  /** Strips the look and feel's button decoration so only the item's glyph and state show. */
  private void applyFlatStyling() {
    setContentAreaFilled(false);
    setBorderPainted(false);
    setFocusPainted(false);
    setOpaque(false);
    setRolloverEnabled(interactive);
    setMargin(new Insets(0, 0, 0, 0));
    setBorder(BorderFactory.createEmptyBorder());
  }

  public ToolbarItem getItem() {
    return item;
  }

  /** Runs the item's action, matching the behaviour the toolbar had before it used buttons. */
  private void activate() {
    if (item == null) return;
    final var model = toolbar.getToolbarModel();
    if (item.isSelectable()) {
      if (model != null) model.itemSelected(item);
    } else if (item instanceof ToolbarClickableItem clickableItem) {
      clickableItem.clicked();
    }
  }

  @Override
  public Dimension getMinimumSize() {
    return getPreferredSize();
  }

  @Override
  public Dimension getPreferredSize() {
    // updateUI() runs from the superclass constructor, before these fields are assigned.
    if (item == null || toolbar == null) return super.getPreferredSize();
    final var dim = item.getDimension(toolbar.getOrientation());
    final var border = 2 * UiScale.scaled(BORDER);
    return new Dimension(dim.width + border, dim.height + border);
  }

  @Override
  public Dimension getMaximumSize() {
    return getPreferredSize();
  }

  @Override
  public String getToolTipText(MouseEvent e) {
    return item.getToolTip();
  }

  @Override
  protected void processMouseEvent(MouseEvent e) {
    // Toolbar tracks the pressed button so it can repaint the previous one. Keep it informed.
    switch (e.getID()) {
      case MouseEvent.MOUSE_PRESSED -> {
        if (item != null && (item.isSelectable() || item instanceof ToolbarClickableItem)) {
          toolbar.setPressed(this);
        }
      }
      case MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_EXITED -> {
        if (toolbar.getPressed() == this) toolbar.setPressed(null);
      }
      default -> {
        // Nothing to track for the remaining event types.
      }
    }
    super.processMouseEvent(e);
  }

  @Override
  protected void paintComponent(Graphics g) {
    final var g2 = (Graphics2D) g.create();
    applyRenderingHints(g2);

    final var model = toolbar.getToolbarModel();
    final var selected = (model != null) && model.isSelected(item);
    final var background = stateBackground(selected);
    if (background != null) {
      final var arc = UiScale.scaled(ARC);
      g2.setColor(background);
      g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
    }
    g2.dispose();

    // Paints the icon, which delegates to the item.
    super.paintComponent(g);
  }

  /** Returns the background for the current state, or null when the button is at rest. */
  private Color stateBackground(boolean selected) {
    if (!interactive) return null;
    final var model = getModel();
    if (model.isPressed() && model.isArmed()) {
      return withAlpha(accentColor(), SELECTED_ALPHA + HOVER_ALPHA);
    }
    if (selected) {
      return withAlpha(accentColor(), SELECTED_ALPHA);
    }
    if (model.isRollover()) {
      return withAlpha(accentColor(), HOVER_ALPHA);
    }
    return null;
  }

  /**
   * Returns a colour that stands out against the toolbar in both light and dark themes.
   *
   * <p>Falls back through progressively more widely supported look and feel keys, then to the
   * button's own foreground, so that every installed look and feel yields something visible.
   */
  private Color accentColor() {
    for (final var key :
        new String[] {"Component.focusColor", "List.selectionBackground", "textHighlight"}) {
      final var color = UIManager.getColor(key);
      if (color != null) return color;
    }
    final var foreground = getForeground();
    return (foreground == null) ? Color.GRAY : foreground;
  }

  private static Color withAlpha(Color color, int alpha) {
    return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.min(alpha, 255));
  }

  private static void applyRenderingHints(Graphics2D g2) {
    if (AppPreferences.UI_ANTIALIASING.getBoolean()) {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }
  }

  /** Adapts a {@link ToolbarItem} so that the button can lay it out and paint it like any icon. */
  private final class ItemIcon implements Icon {

    @Override
    public int getIconWidth() {
      return (item == null) ? 0 : item.getDimension(toolbar.getOrientation()).width;
    }

    @Override
    public int getIconHeight() {
      return (item == null) ? 0 : item.getDimension(toolbar.getOrientation()).height;
    }

    @Override
    public void paintIcon(Component destination, Graphics g, int x, int y) {
      final var g2 = (Graphics2D) g.create();
      applyRenderingHints(g2);
      g2.translate(x, y);
      final var model = getModel();
      if (model.isPressed() && model.isArmed() && item instanceof ToolbarClickableItem clickable) {
        clickable.paintPressedIcon(ToolbarButton.this, g2);
      } else {
        item.paintIcon(ToolbarButton.this, g2);
      }
      g2.dispose();
    }
  }
}

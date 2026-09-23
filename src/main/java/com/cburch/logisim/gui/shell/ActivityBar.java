/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell;

import com.cburch.logisim.gui.theme.AppIcons;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.util.UiScale;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * The strip of icons down the side of the window that chooses what the side panel shows.
 *
 * <p>It replaces a row of tabs. Tabs said what the panel was called but cost a line of the
 * window's height and gave no room to grow; an icon strip is always visible, names each view in a
 * tooltip, and leaves the panel's whole height for its contents.
 */
public class ActivityBar extends JPanel {

  private static final long serialVersionUID = 1L;

  /** Unscaled edge length of one button. */
  private static final int BUTTON_SIZE = 44;

  /** Unscaled width of the bar that marks the active view. */
  private static final int INDICATOR_WIDTH = 2;

  private static final int ICON_SIZE = 20;

  /** An entry in the bar: either a side view or a standalone action such as Settings. */
  private static final class Item extends JComponent {
    private static final long serialVersionUID = 1L;

    private final String id;
    private final AppIcons.Id icon;
    private final Runnable action;
    private final boolean selectable;
    private boolean selected;
    private boolean hovered;

    Item(String id, AppIcons.Id icon, String tooltip, boolean selectable, Runnable action) {
      this.id = id;
      this.icon = icon;
      this.action = action;
      this.selectable = selectable;
      setToolTipText(tooltip);
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      final var size = UiScale.scaled(BUTTON_SIZE);
      setPreferredSize(new Dimension(size, size));
      setMaximumSize(new Dimension(size, size));
      setMinimumSize(new Dimension(size, size));
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
            public void mouseClicked(MouseEvent event) {
              action.run();
            }
          });
    }

    @Override
    protected void paintComponent(Graphics gfx) {
      final var g2 = (Graphics2D) gfx.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        final var width = getWidth();
        final var height = getHeight();
        if (selected || hovered) {
          g2.setColor(
              selected ? Tokens.color("Logisim.activityBar.selectedBackground", getBackground())
                  : getBackground());
        }
        if (selected) {
          g2.setColor(Tokens.accent());
          g2.fillRect(0, 0, UiScale.scaled(INDICATOR_WIDTH), height);
        }
        final var tint =
            selected ? Tokens.accent() : hovered ? Tokens.iconForeground()
                : Tokens.activityBarForeground();
        final var glyph = AppIcons.colored(icon, ICON_SIZE, tint);
        glyph.paintIcon(
            this, g2, (width - glyph.getIconWidth()) / 2, (height - glyph.getIconHeight()) / 2);
      } finally {
        g2.dispose();
      }
    }
  }

  private final List<Item> items = new ArrayList<>();
  private final JPanel top = new JPanel();
  private final JPanel bottom = new JPanel();

  public ActivityBar() {
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    top.setLayout(new BoxLayout(top, BoxLayout.Y_AXIS));
    bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
    top.setOpaque(false);
    bottom.setOpaque(false);
    add(top);
    add(javax.swing.Box.createVerticalGlue());
    add(bottom);
    setOpaque(true);
    applyTheme();
    Theme.addListener(
        () -> {
          applyTheme();
          repaint();
        });
  }

  private void applyTheme() {
    setBackground(Tokens.activityBarBackground());
  }

  /**
   * Adds a button that selects a side view.
   *
   * @param onSelect told which view was asked for; the shell decides whether that means showing it
   *     or collapsing the panel it is already showing
   */
  public void addView(SideView view, String tooltip, Consumer<String> onSelect) {
    final var item =
        new Item(view.id(), view.icon(), tooltip, true, () -> onSelect.accept(view.id()));
    items.add(item);
    top.add(item);
  }

  /** Adds a button that simply runs an action, such as opening the preferences. */
  public void addAction(String id, AppIcons.Id icon, String tooltip, Runnable action) {
    final var item = new Item(id, icon, tooltip, false, action);
    items.add(item);
    bottom.add(item);
  }

  /** Marks {@code id} as the view now showing, or clears the mark when the panel is hidden. */
  public void setSelected(String id) {
    for (final var item : items) {
      if (!item.selectable) continue;
      final var selected = item.id.equals(id);
      if (item.selected != selected) {
        item.selected = selected;
        item.repaint();
      }
    }
  }

  /** Replaces the tooltip of every button, after a change of language. */
  public void setTooltip(String id, String tooltip) {
    for (final var item : items) {
      if (item.id.equals(id)) item.setToolTipText(tooltip);
    }
  }
}

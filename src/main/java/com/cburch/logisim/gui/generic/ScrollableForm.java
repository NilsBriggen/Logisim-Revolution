/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.generic;

import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.UiFonts;
import com.cburch.logisim.util.UiScale;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JViewport;
import javax.swing.Scrollable;

/** A top-aligned settings form which shrinks to its viewport when its layout permits it. */
public final class ScrollableForm extends JPanel implements Scrollable {
  private static final long serialVersionUID = 1L;
  private final JComponent form;
  private final JTextArea help = new JTextArea();

  public ScrollableForm(JComponent form) {
    super(new BorderLayout());
    this.form = form;
    help.setEditable(false);
    help.setFocusable(false);
    help.setOpaque(false);
    help.setLineWrap(true);
    help.setWrapStyleWord(true);
    help.setFont(UiFonts.small());
    help.setBorder(Spacing.panelBorder());
    help.setVisible(false);
    final var body = new JPanel(new BorderLayout());
    body.add(help, BorderLayout.NORTH);
    body.add(form, BorderLayout.CENTER);
    add(body, BorderLayout.NORTH);
  }

  public void setHelpText(String text) {
    help.setText(text == null ? "" : text);
    help.setVisible(text != null && !text.isBlank());
    revalidate();
  }

  @Override
  public void updateUI() {
    super.updateUI();
    if (help != null) {
      help.setFont(UiFonts.small());
      help.setBorder(Spacing.panelBorder());
    }
  }

  public JScrollPane createScrollPane() {
    final var scroll = new JScrollPane(this);
    scroll.setBorder(null);
    // The form supplies font/scale-aware increments through Scrollable.
    return scroll;
  }

  @Override
  public Dimension getPreferredSize() {
    if (form == null) return super.getPreferredSize();
    final var width = getParent() instanceof JViewport viewport
        ? Math.max(preferredMinimumWidth(), viewport.getExtentSize().width)
        : form.getPreferredSize().width;
    // Width-aware forms must measure their wrapped text at the new viewport width, not at the
    // old/preferred width. Otherwise their height (and thus the vertical scroll range) is stale.
    form.setSize(width, form.getHeight());
    final var preferred = form.getPreferredSize();
    if (help.isVisible()) {
      help.setSize(Math.max(1, width), Short.MAX_VALUE);
      return new Dimension(preferred.width, preferred.height + help.getPreferredSize().height);
    }
    return preferred;
  }

  private int preferredMinimumWidth() {
    return Math.max(1, form.getMinimumSize().width);
  }

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return getPreferredSize();
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle visibleRect, int orientation, int direction) {
    return Math.max(UiScale.scaled(16), getFontMetrics(getFont()).getHeight() + Spacing.xs());
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle visibleRect, int orientation, int direction) {
    final var unit = getScrollableUnitIncrement(visibleRect, orientation, direction);
    final var extent = orientation == javax.swing.SwingConstants.VERTICAL
        ? visibleRect.height : visibleRect.width;
    return Math.max(unit, extent - unit);
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return getParent() instanceof JViewport viewport
        && viewport.getExtentSize().width >= preferredMinimumWidth();
  }

  @Override
  public boolean getScrollableTracksViewportHeight() {
    return false;
  }

  /** Scale design sizes once, then clamp against unscaled Swing screen/work-area coordinates. */
  public static Dimension boundedSize(Dimension logical, Dimension available, double scale) {
    return new Dimension(
        Math.max(1, Math.min(available.width, (int) Math.round(logical.width * scale))),
        Math.max(1, Math.min(available.height, (int) Math.round(logical.height * scale))));
  }

  /** Apply scale-aware minimums without ever making the window larger than its work area. */
  public static void sizeWindow(
      Window window, Dimension preferred, Dimension minimum, boolean initial) {
    final var configuration = window.getGraphicsConfiguration();
    final var work = new Rectangle(configuration.getBounds());
    final var insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
    work.x += insets.left;
    work.y += insets.top;
    work.width -= insets.left + insets.right;
    work.height -= insets.top + insets.bottom;
    final var available = work.getSize();
    final var minimumSize = boundedSize(minimum, available, UiScale.factor());
    window.setMinimumSize(minimumSize);
    if (initial) {
      window.setPreferredSize(boundedSize(preferred, available, UiScale.factor()));
      window.pack();
    }
    final var current = window.getSize();
    window.setSize(
        Math.min(available.width, Math.max(minimumSize.width, current.width)),
        Math.min(available.height, Math.max(minimumSize.height, current.height)));
    if (window.isShowing()) {
      window.setLocation(
          Math.max(work.x, Math.min(window.getX(), work.x + work.width - window.getWidth())),
          Math.max(work.y, Math.min(window.getY(), work.y + work.height - window.getHeight())));
    }
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.analyze.gui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.LayoutManager;

/**
 * Lays out groups of buttons in one trailing-aligned row, and moves the later groups onto rows of
 * their own when the row does not fit.
 *
 * <p>The analyzer's six actions were one fixed row: at a larger interface scale, or after a
 * language with longer labels, the last buttons (Build Circuit among them) were simply cut off.
 */
final class ButtonRowLayout implements LayoutManager {
  private final int gap;

  ButtonRowLayout(int gap) {
    this.gap = gap;
  }

  @Override
  public void addLayoutComponent(String name, Component comp) {
    // Components are laid out in the order they were added.
  }

  @Override
  public void removeLayoutComponent(Component comp) {
    // Nothing is cached per component.
  }

  private int rowWidth(Container parent) {
    var width = 0;
    for (final var comp : parent.getComponents()) {
      if (!comp.isVisible()) continue;
      if (width > 0) width += gap;
      width += comp.getPreferredSize().width;
    }
    return width;
  }

  /** Whether all groups fit next to each other in {@code width}. */
  private boolean fitsOneRow(Container parent, int width) {
    final var insets = parent.getInsets();
    return rowWidth(parent) <= width - insets.left - insets.right;
  }

  private Dimension size(Container parent, boolean oneRow) {
    final var insets = parent.getInsets();
    var width = 0;
    var height = 0;
    var count = 0;
    for (final var comp : parent.getComponents()) {
      if (!comp.isVisible()) continue;
      final var pref = comp.getPreferredSize();
      if (oneRow) {
        width += (count > 0 ? gap : 0) + pref.width;
        height = Math.max(height, pref.height);
      } else {
        width = Math.max(width, pref.width);
        height += (count > 0 ? gap : 0) + pref.height;
      }
      count++;
    }
    return new Dimension(
        width + insets.left + insets.right, height + insets.top + insets.bottom);
  }

  @Override
  public Dimension preferredLayoutSize(Container parent) {
    final var width = parent.getWidth();
    return size(parent, width <= 0 || fitsOneRow(parent, width));
  }

  @Override
  public Dimension minimumLayoutSize(Container parent) {
    return size(parent, false);
  }

  @Override
  public void layoutContainer(Container parent) {
    final var insets = parent.getInsets();
    final var right = parent.getWidth() - insets.right;
    final var oneRow = fitsOneRow(parent, parent.getWidth());
    if (oneRow) {
      var x = right - rowWidth(parent);
      for (final var comp : parent.getComponents()) {
        if (!comp.isVisible()) continue;
        final var pref = comp.getPreferredSize();
        comp.setBounds(x, insets.top, pref.width, pref.height);
        x += pref.width + gap;
      }
    } else {
      var y = insets.top;
      for (final var comp : parent.getComponents()) {
        if (!comp.isVisible()) continue;
        final var pref = comp.getPreferredSize();
        final var width = Math.min(pref.width, right - insets.left);
        comp.setBounds(right - width, y, width, pref.height);
        y += pref.height + gap;
      }
    }
  }
}

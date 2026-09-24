/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell;

import com.cburch.logisim.util.UiScale;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;

/**
 * The drawer under the canvas, for output the user watches while a circuit runs: the VHDL console
 * now, the log and timing views later.
 *
 * <p>Separate windows for these meant arranging windows before being able to watch a simulation
 * and a waveform at the same time.
 */
public class BottomPanel extends JTabbedPane {

  private static final long serialVersionUID = 1L;

  private record Panel(String title, javax.swing.Icon icon, JComponent content) {}

  private final Map<String, Panel> panels = new LinkedHashMap<>();

  public BottomPanel() {
    setTabLayoutPolicy(SCROLL_TAB_LAYOUT);
    putClientProperty(FlatClientProperties.TABBED_PANE_TAB_TYPE,
        FlatClientProperties.TABBED_PANE_TAB_TYPE_CARD);
    putClientProperty(FlatClientProperties.TABBED_PANE_SCROLL_BUTTONS_POLICY,
        FlatClientProperties.TABBED_PANE_POLICY_AS_NEEDED);
    // The drawer could be opened -- by the VHDL simulator, among others -- but never shut again.
    putClientProperty(FlatClientProperties.TABBED_PANE_TAB_CLOSABLE, true);
    putClientProperty(
        FlatClientProperties.TABBED_PANE_TAB_CLOSE_CALLBACK,
        (java.util.function.IntConsumer) this::closePanel);
  }

  /**
   * Called when the user shuts a tab.
   *
   * <p>Only this tab is closed. Its content stays registered for the existing Timing, Test Vector
   * and VHDL console actions to reopen. Closing the final tab also hides the empty drawer.
   */
  private void closePanel(int index) {
    if (index < 0 || index >= getTabCount()) return;
    removeTabAt(index);
    if (getTabCount() == 0 && onClose != null) onClose.run();
  }

  /** What to do when the user shuts a tab: put the drawer away. */
  public void setOnClose(Runnable onClose) {
    this.onClose = onClose;
  }

  private Runnable onClose;

  /** Adds a panel, or replaces the one already registered under {@code id}. */
  public void addPanel(String id, String title, javax.swing.Icon icon, JComponent panel) {
    final var existing = panels.put(id, new Panel(title, icon, panel));
    if (existing != null) {
      final var index = indexOfComponent(existing.content());
      if (index >= 0) remove(index);
    }
    addTab(title, icon, panel);
  }

  /** Brings the panel registered under {@code id} to the front. */
  public boolean showPanel(String id) {
    final var panel = panels.get(id);
    if (panel == null) return false;
    if (indexOfComponent(panel.content()) < 0) {
      addTab(panel.title(), panel.icon(), panel.content());
    }
    setSelectedComponent(panel.content());
    return true;
  }

  public boolean hasPanels() {
    return getTabCount() > 0;
  }

  /** Renames the registered panels, after a change of language. */
  public void setTitle(String id, String title) {
    final var panel = panels.get(id);
    if (panel == null) return;
    panels.put(id, new Panel(title, panel.icon(), panel.content()));
    final var index = indexOfComponent(panel.content());
    if (index >= 0) setTitleAt(index, title);
  }

  @Override
  public Dimension getMinimumSize() {
    if (isMinimumSizeSet()) return super.getMinimumSize();
    return new Dimension(0,
        Math.max(UiScale.scaled(LayoutPrefs.MIN_PANEL), usefulHeight(this)));
  }

  /** Reserves controls, headers and at least four body rows in the selected drawer view. */
  private static int usefulHeight(Component component) {
    if (component == null) return 0;
    final var insets = component instanceof Container container
        ? container.getInsets() : new java.awt.Insets(0, 0, 0, 0);
    final var edges = insets.top + insets.bottom;
    if (component instanceof JTabbedPane tabs) {
      final var fontHeight = tabs.getFontMetrics(tabs.getFont()).getHeight();
      return edges + fontHeight + UiScale.scaled(18) + usefulHeight(tabs.getSelectedComponent());
    }
    if (component instanceof JSplitPane split) {
      final var first = usefulHeight(split.getLeftComponent());
      final var second = usefulHeight(split.getRightComponent());
      return edges + (split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT
          ? Math.max(first, second) : first + second + split.getDividerSize());
    }
    if (component instanceof JScrollPane scroll) {
      final var view = scroll.getViewport().getView();
      final var font = view != null && view.getFont() != null ? view.getFont() : scroll.getFont();
      final var rowHeight = view instanceof JTable table ? table.getRowHeight()
          : scroll.getFontMetrics(font).getHeight() + UiScale.scaled(8);
      final var header = scroll.getColumnHeader();
      return edges + 4 * rowHeight + (header == null ? 0 : header.getPreferredSize().height)
          + scroll.getHorizontalScrollBar().getPreferredSize().height
          + (scroll.getViewportBorder() == null ? 0
              : scroll.getViewportBorder().getBorderInsets(scroll).top
                  + scroll.getViewportBorder().getBorderInsets(scroll).bottom);
    }
    if (component instanceof Container container
        && container.getLayout() instanceof BorderLayout layout) {
      final var center = layout.getLayoutComponent(container, BorderLayout.CENTER);
      final var north = layout.getLayoutComponent(container, BorderLayout.NORTH);
      final var south = layout.getLayoutComponent(container, BorderLayout.SOUTH);
      final var east = layout.getLayoutComponent(container, BorderLayout.EAST);
      final var west = layout.getLayoutComponent(container, BorderLayout.WEST);
      final var body = Math.max(usefulHeight(center),
          Math.max(usefulHeight(east), usefulHeight(west)));
      return edges + body + preferredHeight(north) + preferredHeight(south)
          + (north == null ? 0 : layout.getVgap()) + (south == null ? 0 : layout.getVgap());
    }
    if (component instanceof Container container && container.getLayout() instanceof FlowLayout) {
      return component.getPreferredSize().height;
    }
    final var font = component.getFont();
    final var rows = font == null ? UiScale.scaled(80)
        : 4 * (component.getFontMetrics(font).getHeight() + UiScale.scaled(8));
    return Math.max(component.getMinimumSize().height, edges + rows);
  }

  private static int preferredHeight(Component component) {
    return component == null || !component.isVisible() ? 0 : component.getPreferredSize().height;
  }
}

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
import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;

/**
 * The middle of the window: the tab strip, and under it the one editor the tabs choose between.
 */
public class EditorArea extends JPanel {

  private static final long serialVersionUID = 1L;

  private final EditorTabs tabs;
  private final JLayeredPane body = new JLayeredPane();
  private final JComponent editor;
  private JComponent overlay;
  private int reservedHeight;
  private JComponent statusOverlay;

  public EditorArea(EditorTabs tabs, JComponent editor) {
    super(new BorderLayout());
    this.tabs = tabs;
    this.editor = editor;
    add(tabs, BorderLayout.NORTH);
    body.setLayout(null);
    body.add(editor, JLayeredPane.DEFAULT_LAYER);
    add(body, BorderLayout.CENTER);
  }

  /**
   * Floats a component over the bottom corner of the editor.
   *
   * <p>Laid out by hand because the whole point is that it does not take part in the layout: it
   * sits over the drawing rather than beside it.
   */
  public void setOverlay(JComponent component) {
    if (overlay != null) body.remove(overlay);
    overlay = component;
    if (component != null) body.add(component, JLayeredPane.PALETTE_LAYER);
    revalidate();
  }

  /**
   * Floats a second component over the bottom middle of the editor, clear of the corner one.
   *
   * <p>It reports what is wrong with the circuit, where the painted error text used to be.
   */
  public void setStatusOverlay(JComponent component) {
    if (statusOverlay != null) body.remove(statusOverlay);
    statusOverlay = component;
    if (component != null) body.add(component, JLayeredPane.PALETTE_LAYER);
    revalidate();
  }

  @Override
  public void doLayout() {
    super.doLayout();
    editor.setBounds(0, 0, body.getWidth(), body.getHeight());
    final var margin = UiScale.scaled(ZoomPill.MARGIN);
    var cornerLeft = body.getWidth();
    if (overlay != null && overlay.isVisible()) {
      final var size = overlay.getPreferredSize();
      cornerLeft = Math.max(0, body.getWidth() - size.width - margin);
      overlay.setBounds(
          cornerLeft,
          Math.max(0, body.getHeight() - size.height - margin),
          size.width,
          size.height);
    }
    if (statusOverlay != null && statusOverlay.isVisible()) {
      final var size = statusOverlay.getPreferredSize();
      final var centred = (body.getWidth() - size.width) / 2;
      final var x = Math.max(margin, Math.min(centred, cornerLeft - margin - size.width));
      statusOverlay.setBounds(
          x, Math.max(0, body.getHeight() - size.height - margin), size.width, size.height);
    }
  }

  /** Sets the height the drawer below may not take from the editor. */
  void setReservedHeight(int height) {
    reservedHeight = Math.max(0, height);
  }

  @Override
  public Dimension getMinimumSize() {
    final var minimum = super.getMinimumSize();
    if (isMinimumSizeSet()) return minimum;
    return new Dimension(minimum.width, Math.max(minimum.height, reservedHeight));
  }

  @Override
  public Dimension getPreferredSize() {
    final var preferred = super.getPreferredSize();
    return new Dimension(Math.max(preferred.width, editor.getPreferredSize().width), preferred.height);
  }

  public EditorTabs tabs() {
    return tabs;
  }

  /** Hides the tab strip, for the welcome screen, which is not one of the project's circuits. */
  public void setTabsVisible(boolean visible) {
    tabs.setVisible(visible);
    revalidate();
  }
}

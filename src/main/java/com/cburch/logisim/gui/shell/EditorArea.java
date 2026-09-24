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

  @Override
  public void doLayout() {
    super.doLayout();
    editor.setBounds(0, 0, body.getWidth(), body.getHeight());
    if (overlay != null && overlay.isVisible()) {
      final var size = overlay.getPreferredSize();
      final var margin = UiScale.scaled(ZoomPill.MARGIN);
      overlay.setBounds(
          Math.max(0, body.getWidth() - size.width - margin),
          Math.max(0, body.getHeight() - size.height - margin),
          size.width,
          size.height);
    }
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

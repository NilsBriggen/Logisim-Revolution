/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell;

import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * The middle of the window: the tab strip, and under it the one editor the tabs choose between.
 */
public class EditorArea extends JPanel {

  private static final long serialVersionUID = 1L;

  private final EditorTabs tabs;
  private final JPanel body = new JPanel(new BorderLayout());

  public EditorArea(EditorTabs tabs, JComponent editor) {
    super(new BorderLayout());
    this.tabs = tabs;
    add(tabs, BorderLayout.NORTH);
    body.add(editor, BorderLayout.CENTER);
    add(body, BorderLayout.CENTER);
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

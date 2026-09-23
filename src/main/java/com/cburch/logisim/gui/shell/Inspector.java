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
 * The panel on the right showing the properties of whatever is selected.
 *
 * <p>It used to share the left column with the explorer, so the two competed for the same height
 * and a component with many attributes pushed the circuit list out of view. On its own side it
 * can be as tall as the window.
 */
public class Inspector extends JPanel {

  private static final long serialVersionUID = 1L;

  private final PanelHeader header;
  private final JPanel body = new JPanel(new BorderLayout());

  public Inspector(String title) {
    super(new BorderLayout());
    header = new PanelHeader(title);
    add(header, BorderLayout.NORTH);
    add(body, BorderLayout.CENTER);
  }

  /** Sets the panel's contents. */
  public void setContent(JComponent content) {
    body.removeAll();
    if (content != null) body.add(content, BorderLayout.CENTER);
    body.revalidate();
    body.repaint();
  }

  /** Puts a component under the panel's contents, such as the zoom controls. */
  public void setFooter(JComponent footer) {
    final var existing = ((BorderLayout) getLayout()).getLayoutComponent(BorderLayout.SOUTH);
    if (existing != null) remove(existing);
    if (footer != null) add(footer, BorderLayout.SOUTH);
    revalidate();
    repaint();
  }

  public void setTitle(String title) {
    header.setTitle(title);
  }
}

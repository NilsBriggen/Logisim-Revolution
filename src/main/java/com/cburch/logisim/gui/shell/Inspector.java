/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.gui.theme.AppIcons;
import java.awt.BorderLayout;
import javax.swing.JButton;
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

  /**
   * Puts a close button in the heading.
   *
   * <p>The panel's visibility was modelled and remembered between sessions but wired to no
   * control, so once it was on screen it stayed there and a wide schematic could not have the
   * column back.
   */
  public void setOnClose(Runnable onClose) {
    if (onClose == null) {
      header.setActions(null);
      return;
    }
    final var close = new JButton(AppIcons.get(AppIcons.Id.CLOSE, 12));
    close.setToolTipText(S.get("inspectorHideTip"));
    close.setFocusable(false);
    close.setBorderPainted(false);
    close.setContentAreaFilled(false);
    close.addActionListener(event -> onClose.run());
    header.setActions(close);
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

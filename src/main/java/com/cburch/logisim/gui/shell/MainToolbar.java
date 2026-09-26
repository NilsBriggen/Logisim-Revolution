/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell;

import com.cburch.draw.toolbar.Toolbar;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.UiScale;
import java.awt.BorderLayout;
import java.awt.Dimension;
import javax.swing.BoxLayout;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;

/**
 * The row along the top of the window: the drawing tools on the left, the simulation controls on
 * the right.
 *
 * <p>Both were already toolbars; what is new is that they are one row with the simulation
 * controls pushed to the far side, so starting and stopping the simulation is always in the same
 * place instead of wherever the user last dragged a toolbar to.
 *
 * <p>The two halves are pinned to their edges rather than spread by glue. Both toolbars will
 * stretch, so with glue the free space was shared out between them, and when the drawing tools
 * were hidden (with no editor open) the simulation controls jumped to the left edge. The row also
 * keeps the drawing tools' height while they are hidden, so the window does not shift under the
 * pointer.
 */
public class MainToolbar extends JPanel {

  private static final long serialVersionUID = 1L;

  private final Toolbar tools;
  private final Toolbar simulation;
  private final JSeparator divider;

  public MainToolbar(Toolbar tools, Toolbar simulation) {
    super(new BorderLayout());
    this.tools = tools;
    this.simulation = simulation;

    add(tools, BorderLayout.WEST);
    if (simulation != null) {
      final var trailing = new JPanel();
      trailing.setLayout(new BoxLayout(trailing, BoxLayout.X_AXIS));
      trailing.setOpaque(false);
      divider = new JSeparator(SwingConstants.VERTICAL);
      trailing.add(divider);
      trailing.add(simulation);
      add(trailing, BorderLayout.EAST);
    } else {
      divider = null;
    }
    applyTheme();
    Theme.addListener(this, this::applyTheme);
  }

  private void applyTheme() {
    setBorder(Spacing.border(0, Spacing.XS, 0, Spacing.XS));
    setBackground(Tokens.color("Logisim.sidePanel.background", getBackground()));
    if (divider != null) {
      final var width = Math.max(1, UiScale.scaled(1));
      divider.setMinimumSize(new Dimension(width, 0));
      divider.setPreferredSize(new Dimension(width, UiScale.scaled(16)));
      divider.setMaximumSize(new Dimension(width, Integer.MAX_VALUE));
    }
    tools.revalidate();
    if (simulation != null) simulation.revalidate();
    revalidate();
    repaint();
  }

  public Toolbar getToolsToolbar() {
    return tools;
  }

  public Toolbar getSimulationToolbar() {
    return simulation;
  }

  @Override
  public Dimension getPreferredSize() {
    final var size = super.getPreferredSize();
    if (!tools.isVisible()) {
      final var insets = getInsets();
      size.height =
          Math.max(size.height, tools.getPreferredSize().height + insets.top + insets.bottom);
    }
    return size;
  }

  @Override
  public Dimension getMaximumSize() {
    return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
  }
}

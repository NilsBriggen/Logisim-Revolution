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
import java.awt.Dimension;
import javax.swing.BorderFactory;
import javax.swing.Box;
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
 */
public class MainToolbar extends JPanel {

  private static final long serialVersionUID = 1L;

  private final Toolbar tools;
  private final Toolbar simulation;

  public MainToolbar(Toolbar tools, Toolbar simulation) {
    this.tools = tools;
    this.simulation = simulation;
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
    setBorder(BorderFactory.createEmptyBorder(0, Spacing.xs(), 0, Spacing.xs()));

    add(tools);
    add(Box.createHorizontalGlue());
    if (simulation != null) {
      final var divider = new JSeparator(SwingConstants.VERTICAL);
      divider.setMaximumSize(new Dimension(1, Integer.MAX_VALUE));
      add(divider);
      add(simulation);
    }
    applyTheme();
    Theme.addListener(this::applyTheme);
  }

  private void applyTheme() {
    setBackground(Tokens.color("Logisim.sidePanel.background", getBackground()));
  }

  public Toolbar getToolsToolbar() {
    return tools;
  }

  public Toolbar getSimulationToolbar() {
    return simulation;
  }

  @Override
  public Dimension getMaximumSize() {
    return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.main;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.gui.generic.ProjectExplorer;
import com.cburch.logisim.gui.shell.palette.ComponentPalette;
import com.cburch.logisim.gui.theme.AppIcons;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.LocaleListener;
import com.cburch.logisim.util.LocaleManager;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;

/**
 * The components a circuit can be built from.
 *
 * <p>Normally a palette of tiles grouped by what the components are for. The library tree is still
 * here behind a button, because it is the only place a library can be unloaded, reloaded or
 * reordered, and because a project with a deep hierarchy of its own libraries is easier to read as
 * a tree.
 */
class Toolbox extends JPanel implements LocaleListener {
  private static final long serialVersionUID = 1L;

  private static final String PALETTE_CARD = "palette";
  private static final String TREE_CARD = "tree";

  private final ProjectExplorer tree;
  private final ComponentPalette palette;
  private final CardLayout cards = new CardLayout();
  private final JPanel body = new JPanel(cards);
  private final JButton toggle = new JButton();

  Toolbox(Project proj) {
    super(new BorderLayout());

    palette = new ComponentPalette(proj);
    tree = new ProjectExplorer(proj, false);
    tree.setListener(new ToolboxManip(proj, tree));

    final var treeScroll = new JScrollPane(tree);
    treeScroll.setBorder(BorderFactory.createEmptyBorder());
    body.add(palette, PALETTE_CARD);
    body.add(treeScroll, TREE_CARD);
    add(body, BorderLayout.CENTER);

    toggle.setFocusable(false);
    toggle.setBorderPainted(false);
    toggle.setContentAreaFilled(false);
    toggle.addActionListener(
        event ->
            setShowingTree(!AppPreferences.PALETTE_SHOW_TREE.getBoolean()));

    setShowingTree(AppPreferences.PALETTE_SHOW_TREE.getBoolean());
    LocaleManager.addLocaleListener(this);
    localeChanged();
  }

  private void setShowingTree(boolean showTree) {
    AppPreferences.PALETTE_SHOW_TREE.setBoolean(showTree);
    cards.show(body, showTree ? TREE_CARD : PALETTE_CARD);
    refreshToggle();
  }

  private void refreshToggle() {
    final var showingTree = AppPreferences.PALETTE_SHOW_TREE.getBoolean();
    toggle.setIcon(AppIcons.get(showingTree ? AppIcons.Id.LIST_FILTER : AppIcons.Id.FOLDER, 14));
    toggle.setToolTipText(S.get(showingTree ? "paletteShowTilesTip" : "paletteShowTreeTip"));
  }

  /** The button offered in the panel's heading, which switches between the two views. */
  JComponent headerActions() {
    return toggle;
  }

  void setHaloedTool(Tool value) {
    tree.setHaloedTool(value);
    palette.setHaloedTool(value);
  }

  public void updateStructure() {
    tree.updateStructure();
    palette.updateStructure();
  }

  @Override
  public void localeChanged() {
    refreshToggle();
  }
}

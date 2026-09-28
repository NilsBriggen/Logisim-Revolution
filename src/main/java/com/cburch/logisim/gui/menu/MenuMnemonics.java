/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.menu;

import java.awt.event.KeyEvent;
import java.util.HashSet;
import java.util.Set;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

/**
 * Gives every menu and menu item a mnemonic derived from its title, so the menus can be opened and
 * their commands chosen from the keyboard in any language without a marker in every translation.
 */
public final class MenuMnemonics {

  private MenuMnemonics() {
    throw new UnsupportedOperationException("Utility class, do not instantiate.");
  }

  /** Marks a menu that already re-assigns its items' mnemonics each time it opens. */
  private static final String TRACKED = MenuMnemonics.class.getName() + ".tracked";

  /**
   * Assigns each menu of {@code menuBar} the first letter or digit of its title that no earlier
   * menu has taken, and does the same for the items inside every menu and submenu. Call again
   * after the titles change, for example on a locale change; menus whose items change while the
   * application runs (recent files, windows) re-assign their items whenever they open.
   */
  public static void assign(JMenuBar menuBar) {
    final var used = new HashSet<Integer>();
    for (var index = 0; index < menuBar.getMenuCount(); index++) {
      final var menu = menuBar.getMenu(index);
      if (menu != null) {
        assign(menu, used);
        assignItems(menu);
      }
    }
  }

  /**
   * Gives the items of {@code menu} (and, recursively, of its submenus) distinct mnemonics, and
   * keeps them up to date when the menu's contents change.
   */
  static void assignItems(JMenu menu) {
    final var used = new HashSet<Integer>();
    for (var index = 0; index < menu.getItemCount(); index++) {
      final var item = menu.getItem(index);
      if (item == null) continue; // a separator
      assign(item, used);
      if (item instanceof JMenu submenu) assignItems(submenu);
    }
    if (menu.getClientProperty(TRACKED) == null) {
      menu.putClientProperty(TRACKED, Boolean.TRUE);
      menu.addMenuListener(
          new MenuListener() {
            @Override
            public void menuSelected(MenuEvent event) {
              assignItems(menu);
            }

            @Override
            public void menuDeselected(MenuEvent event) {
              // Nothing to do.
            }

            @Override
            public void menuCanceled(MenuEvent event) {
              // Nothing to do.
            }
          });
    }
  }

  /** Assigns {@code item} its first available mnemonic, or clears it if every letter is taken. */
  static void assign(JMenuItem item, Set<Integer> used) {
    final var text = item.getText();
    if (text != null) {
      for (var index = 0; index < text.length(); index++) {
        final var ch = text.charAt(index);
        if (!Character.isLetterOrDigit(ch)) continue;
        final var keyCode = KeyEvent.getExtendedKeyCodeForChar(ch);
        if (keyCode == KeyEvent.VK_UNDEFINED || !used.add(keyCode)) continue;
        item.setMnemonic(keyCode);
        item.setDisplayedMnemonicIndex(index);
        return;
      }
    }
    item.setMnemonic(0);
  }
}

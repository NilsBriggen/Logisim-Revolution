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

/**
 * Gives every top-level menu a mnemonic derived from its title, so the menu bar can be opened from
 * the keyboard in any language without a marker in every translation.
 */
public final class MenuMnemonics {

  private MenuMnemonics() {
    throw new UnsupportedOperationException("Utility class, do not instantiate.");
  }

  /**
   * Assigns each menu of {@code menuBar} the first letter or digit of its title that no earlier
   * menu has taken. Call again after the titles change, for example on a locale change.
   */
  public static void assign(JMenuBar menuBar) {
    final var used = new HashSet<Integer>();
    for (var index = 0; index < menuBar.getMenuCount(); index++) {
      final var menu = menuBar.getMenu(index);
      if (menu != null) assign(menu, used);
    }
  }

  /** Assigns {@code menu} its first available mnemonic, or clears it if every letter is taken. */
  static void assign(JMenu menu, Set<Integer> used) {
    final var text = menu.getText();
    if (text != null) {
      for (var index = 0; index < text.length(); index++) {
        final var ch = text.charAt(index);
        if (!Character.isLetterOrDigit(ch)) continue;
        final var keyCode = KeyEvent.getExtendedKeyCodeForChar(ch);
        if (keyCode == KeyEvent.VK_UNDEFINED || !used.add(keyCode)) continue;
        menu.setMnemonic(keyCode);
        menu.setDisplayedMnemonicIndex(index);
        return;
      }
    }
    menu.setMnemonic(0);
  }
}

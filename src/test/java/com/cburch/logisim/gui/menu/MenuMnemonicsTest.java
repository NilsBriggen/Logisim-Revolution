/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.awt.event.KeyEvent;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.event.MenuEvent;
import org.junit.jupiter.api.Test;

class MenuMnemonicsTest {

  @Test
  void eachMenuGetsItsFirstLetterNotAlreadyTaken() {
    final var bar = new JMenuBar();
    final var file = new JMenu("File");
    final var edit = new JMenu("Edit");
    final var fpga = new JMenu("FPGA");
    final var find = new JMenu("Find");
    bar.add(file);
    bar.add(edit);
    bar.add(fpga);
    bar.add(find);

    MenuMnemonics.assign(bar);

    assertEquals(KeyEvent.VK_F, file.getMnemonic());
    assertEquals(0, file.getDisplayedMnemonicIndex());
    assertEquals(KeyEvent.VK_E, edit.getMnemonic());
    assertEquals(KeyEvent.VK_P, fpga.getMnemonic());
    assertEquals(1, fpga.getDisplayedMnemonicIndex());
    assertEquals(KeyEvent.VK_I, find.getMnemonic());
  }

  @Test
  void punctuationIsSkippedAndAnExhaustedTitleClearsItsMnemonic() {
    final var bar = new JMenuBar();
    final var first = new JMenu("A");
    final var second = new JMenu("…a");
    final var empty = new JMenu("");
    bar.add(first);
    bar.add(second);
    bar.add(empty);

    MenuMnemonics.assign(bar);

    assertEquals(KeyEvent.VK_A, first.getMnemonic());
    assertEquals(0, second.getMnemonic());
    assertEquals(0, empty.getMnemonic());
  }

  @Test
  void itemsAndSubmenuItemsGetDistinctMnemonicsAndLateItemsGetOneOnOpen() {
    final var bar = new JMenuBar();
    final var file = new JMenu("File");
    final var open = new JMenuItem("Open");
    final var save = new JMenuItem("Save");
    final var saveAs = new JMenuItem("Save As");
    final var recent = new JMenu("Open Recent");
    final var first = new JMenuItem("one.circ");
    bar.add(file);
    file.add(open);
    file.addSeparator();
    file.add(save);
    file.add(saveAs);
    file.add(recent);
    recent.add(first);

    MenuMnemonics.assign(bar);

    assertEquals(KeyEvent.VK_O, open.getMnemonic());
    assertEquals(KeyEvent.VK_S, save.getMnemonic());
    assertEquals(KeyEvent.VK_A, saveAs.getMnemonic());
    assertEquals(KeyEvent.VK_P, recent.getMnemonic());
    assertEquals(KeyEvent.VK_O, first.getMnemonic());

    final var second = new JMenuItem("other.circ");
    recent.add(second);
    for (final var listener : recent.getMenuListeners()) {
      listener.menuSelected(new MenuEvent(recent));
    }
    assertEquals(KeyEvent.VK_T, second.getMnemonic());
  }

  @Test
  void reassigningAfterARenameReplacesTheOldMnemonic() {
    final var bar = new JMenuBar();
    final var menu = new JMenu("Datei");
    bar.add(menu);
    MenuMnemonics.assign(bar);
    assertEquals(KeyEvent.VK_D, menu.getMnemonic());

    menu.setText("File");
    MenuMnemonics.assign(bar);

    assertEquals(KeyEvent.VK_F, menu.getMnemonic());
  }
}

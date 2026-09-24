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

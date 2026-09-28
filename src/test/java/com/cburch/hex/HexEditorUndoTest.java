/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.hex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.std.memory.MemContents;
import java.awt.event.KeyEvent;
import org.junit.jupiter.api.Test;

class HexEditorUndoTest {

  @Test
  void typingAFullValueMovesToTheNextOne() {
    final var memory = MemContents.create(4, 8, false);
    final var editor = new HexEditor(memory);
    editor.getCaret().setDot(0, false);

    type(editor, "ab");
    assertEquals(0xab, memory.get(0));
    assertEquals(1, editor.getCaret().getDot(), "two digits fill a byte, so the cursor moves on");

    type(editor, "c");
    assertEquals(0x0c, memory.get(1));
    assertEquals(1, editor.getCaret().getDot(), "a partly typed value keeps the cursor");
  }

  @Test
  void typedValuesAreUndoneAndRedoneOneValueAtATime() {
    final var memory = MemContents.create(4, 8, false);
    final var editor = new HexEditor(memory);
    editor.getCaret().setDot(0, false);
    assertFalse(editor.canUndo());

    type(editor, "12" + "34");
    assertEquals(0x12, memory.get(0));
    assertEquals(0x34, memory.get(1));

    editor.undo();
    assertEquals(0x12, memory.get(0));
    assertEquals(0, memory.get(1), "both digits of the second value are one step");
    editor.undo();
    assertEquals(0, memory.get(0));
    assertFalse(editor.canUndo());

    editor.redo();
    editor.redo();
    assertEquals(0x12, memory.get(0));
    assertEquals(0x34, memory.get(1));
    assertFalse(editor.canRedo());
  }

  @Test
  void deletingASelectionCanBeUndone() {
    final var memory = MemContents.create(4, 8, false);
    for (var i = 0; i < 16; i++) memory.set(i, i + 1);
    final var editor = new HexEditor(memory);
    editor.getCaret().setDot(3, false);
    editor.getCaret().setDot(6, true);

    editor.delete();
    for (var i = 3; i <= 6; i++) assertEquals(0, memory.get(i));

    assertTrue(editor.canUndo());
    editor.undo();
    for (var i = 0; i < 16; i++) assertEquals(i + 1, memory.get(i));
  }

  @Test
  void changesMadeOutsideTheEditorAreNotRecorded() {
    final var memory = MemContents.create(4, 8, false);
    final var editor = new HexEditor(memory);

    memory.set(5, 0x55); // e.g. the simulation writing to a RAM

    assertFalse(editor.canUndo());
  }

  @Test
  void undoRestoresValuesAcrossMemoryPages() {
    // 4096-word pages: an edit starting mid-page and spanning three pages
    final var memory = MemContents.create(14, 8, false);
    for (var i = 4000; i < 8300; i++) memory.set(i, 0x5a);
    final var editor = new HexEditor(memory);
    editor.getCaret().setDot(4000, false);
    editor.getCaret().setDot(8299, true);

    editor.delete();
    assertEquals(0, memory.get(6000));

    editor.undo();
    for (var i = 4000; i < 8300; i++) assertEquals(0x5a, memory.get(i), "address " + i);
  }

  private static void type(HexEditor editor, String digits) {
    for (final var c : digits.toCharArray()) {
      final var event =
          new KeyEvent(editor, KeyEvent.KEY_TYPED, 0L, 0, KeyEvent.VK_UNDEFINED, c);
      for (final var l : editor.getKeyListeners()) l.keyTyped(event);
    }
  }
}

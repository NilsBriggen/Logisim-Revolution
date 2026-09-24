/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.generic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JTextField;
import org.junit.jupiter.api.Test;

/** Whether two selected rows can be given a value in one go. */
class AttrMultiEditTest {
  private static JComboBox<String> choices(String... items) {
    return new JComboBox<>(items);
  }

  @Test
  void rowsOfferingTheSameChoicesCanBeSetTogether() {
    assertTrue(AttrMultiEdit.sameChoices(choices("east", "west"), choices("east", "west")));
  }

  @Test
  void rowsOfferingDifferentChoicesCannot() {
    assertFalse(AttrMultiEdit.sameChoices(choices("east", "west"), choices("east", "north")));
    assertFalse(AttrMultiEdit.sameChoices(choices("east"), choices("east", "west")));
  }

  /** Order is part of the offer: the combo shows them in it. */
  @Test
  void theOrderOfTheChoicesMatters() {
    assertFalse(AttrMultiEdit.sameChoices(choices("east", "west"), choices("west", "east")));
  }

  /**
   * Compared by what the user reads, not by identity: a splitter builds a fresh set of options for
   * every one of its bit rows, and those rows are exactly the case this has to cover.
   */
  @Test
  void equalChoicesFromSeparateObjectsStillMatch() {
    final var first = choices(new String("east"), new String("west"));
    final var second = choices(new String("east"), new String("west"));
    assertTrue(AttrMultiEdit.sameChoices(first, second));
  }

  @Test
  void freeTextRowCannotBeSetWithAnother() {
    assertFalse(AttrMultiEdit.sameChoices(new JTextField("7"), new JTextField("7")));
    assertFalse(AttrMultiEdit.sameChoices(choices("east"), new JTextField("east")));
    assertFalse(AttrMultiEdit.sameChoices(choices("east"), new JLabel("east")));
  }

  /** An editable combo is a text field with suggestions; typing into two at once means nothing. */
  @Test
  void editableComboCannotBeSetWithAnother() {
    final var editable = choices("east", "west");
    editable.setEditable(true);
    assertFalse(AttrMultiEdit.sameChoices(editable, choices("east", "west")));
    assertFalse(AttrMultiEdit.sameChoices(choices("east", "west"), editable));
  }

  @Test
  void nothingIsCompatibleWithNothing() {
    assertFalse(AttrMultiEdit.sameChoices(null, choices("east")));
    assertFalse(AttrMultiEdit.sameChoices(choices("east"), null));
  }

  @Test
  void identicalLabelsDoNotMakeDifferentValueTypesCompatible() {
    final var number = new JComboBox<>(new Integer[] {1, 2});
    assertFalse(AttrMultiEdit.sameChoices(number, choices("1", "2")));
  }
}

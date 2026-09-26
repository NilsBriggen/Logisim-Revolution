/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.analyze.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The analyzer's own undo: table edits, variable changes, expressions and wholesale replacement. */
class AnalyzerHistoryTest {
  private final List<Runnable> queue = new ArrayList<>();
  private final AnalyzerModel model = new AnalyzerModel();

  private AnalyzerHistory history() {
    model.setVariables(List.of(new Var("a", 1), new Var("b", 1)), List.of(new Var("y", 1)));
    return new AnalyzerHistory(model, queue::add);
  }

  /** Runs the scheduled commits, as the end of the Swing event would. */
  private void endOfEvent() {
    final var pending = List.copyOf(queue);
    queue.clear();
    pending.forEach(Runnable::run);
  }

  private Entry cell(int row) {
    return model.getTruthTable().getOutputEntry(row, 0);
  }

  @Test
  void undoAndRedoATruthTableEdit() {
    final var history = history();
    assertFalse(history.canUndo());
    final var before = cell(1);

    model.getTruthTable().setOutputEntry(1, 0, Entry.ONE);
    endOfEvent();
    assertTrue(history.canUndo());

    history.undo();
    assertEquals(before, cell(1));
    assertTrue(history.canRedo());

    history.redo();
    assertEquals(Entry.ONE, cell(1));
  }

  @Test
  void changesOfOneEventAreOneStep() {
    final var history = history();
    model.getTruthTable().setOutputEntry(0, 0, Entry.ONE);
    model.getTruthTable().setOutputEntry(1, 0, Entry.ONE);
    model.getTruthTable().setOutputEntry(2, 0, Entry.ONE);
    endOfEvent();

    history.undo();
    assertFalse(history.canUndo(), "three cells in one event are one undo step");
    assertTrue(cell(0) != Entry.ONE && cell(1) != Entry.ONE && cell(2) != Entry.ONE);
  }

  @Test
  void replacedTableCanBeGotBack() {
    final var history = history();
    model.getTruthTable().setOutputEntry(3, 0, Entry.ONE);
    endOfEvent();

    // Analysing another circuit (or importing a file) replaces everything.
    model.beginReplacement();
    model.setVariables(List.of(new Var("x", 1)), List.of(new Var("z", 1)));
    model.endReplacement();
    endOfEvent();

    history.undo();
    assertEquals(List.of(new Var("a", 1), new Var("b", 1)), model.getInputs().vars);
    assertEquals(List.of(new Var("y", 1)), model.getOutputs().vars);
    assertEquals(Entry.ONE, cell(3), "the hand-made table is back");
  }

  @Test
  void typedExpressionIsRestored() throws Exception {
    final var history = history();
    model.getOutputExpressions().enableUpdates();
    final var typed = Parser.parse("a b + a b", model);
    model.getOutputExpressions().setExpression("y", typed, "a b + a b");
    endOfEvent();
    model.getTruthTable().setOutputEntry(0, 0, Entry.ONE);
    endOfEvent();

    history.undo();
    assertEquals("a b + a b", model.getOutputExpressions().getExpressionString("y"));
  }

  @Test
  void newEditClearsRedo() {
    final var history = history();
    model.getTruthTable().setOutputEntry(1, 0, Entry.ONE);
    endOfEvent();
    history.undo();
    model.getTruthTable().setOutputEntry(2, 0, Entry.ONE);
    endOfEvent();
    assertFalse(history.canRedo());
  }
}

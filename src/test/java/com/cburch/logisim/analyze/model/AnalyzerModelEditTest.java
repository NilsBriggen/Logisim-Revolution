/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.analyze.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * Re-analysing a circuit or importing a table used to replace a hand-made table without a word;
 * the model now knows whether it holds hand edits so that callers can confirm first.
 */
class AnalyzerModelEditTest {

  private static void replace(AnalyzerModel model) {
    model.beginReplacement();
    try {
      model.setVariables(List.of(new Var("a", 1), new Var("b", 1)), List.of(new Var("x", 1)));
      model.getOutputExpressions().setExpression("x", Expressions.and(
          Expressions.variable("a"), Expressions.variable("b")));
    } finally {
      model.endReplacement();
    }
  }

  @Test
  void handEditsAreTrackedAndClearedByReplacement() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var model = new AnalyzerModel();
      assertFalse(model.isEdited());

      replace(model);
      assertFalse(model.isEdited(), "an analysis is not a hand edit");

      model.getTruthTable().setOutputEntry(0, 0, Entry.ONE);
      assertTrue(model.isEdited(), "a truth table click is a hand edit");

      // A replacement that fails before changing anything keeps the hand edits flagged.
      model.beginReplacement();
      model.endReplacement();
      assertTrue(model.isEdited());

      replace(model);
      assertFalse(model.isEdited());

      model.getOutputExpressions().setExpression("x", Expressions.or(
          Expressions.variable("a"), Expressions.variable("b")));
      assertTrue(model.isEdited(), "typing an expression is a hand edit");

      replace(model);
      model.getInputs().add(new Var("c", 1));
      assertTrue(model.isEdited(), "adding a variable is a hand edit");
    });
  }
}

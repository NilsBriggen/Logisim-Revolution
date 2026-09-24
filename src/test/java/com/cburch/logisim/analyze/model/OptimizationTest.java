/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.analyze.model;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class OptimizationTest {
  private static final int SOP = AnalyzerModel.FORMAT_SUM_OF_PRODUCTS;
  private static final int POS = AnalyzerModel.FORMAT_PRODUCT_OF_SUMS;

  @Test
  void cancellationInsidePrimeGenerationLeavesEveryLiveOutputUntouched() throws Exception {
    final var model = onEdt(() -> model(10));
    final var outputs = model.getOutputExpressions();
    final var before =
        onEdt(() -> List.of(outputs.getMinimalExpression("x"), outputs.getMinimalExpression("y")));
    final var notifications = new AtomicInteger();
    final var snapshot = onEdt(() -> {
      outputs.addOutputExpressionsListener(event -> notifications.incrementAndGet());
      return outputs.createOptimizationSnapshot(POS);
    });
    final var checks = new AtomicInteger();
    final var context = new OptimizationContext(() -> checks.incrementAndGet() >= 3000, text -> {});

    assertThrows(CancellationException.class, () -> snapshot.compute(context));

    assertEquals(3000, checks.get());
    onEdt(() -> {
      assertSame(before.get(0), outputs.getMinimalExpression("x"));
      assertSame(before.get(1), outputs.getMinimalExpression("y"));
      assertEquals(SOP, outputs.getMinimizedFormat("x"));
      assertEquals(SOP, outputs.getMinimizedFormat("y"));
      assertEquals(0, notifications.get());
      return null;
    });
  }

  @Test
  void cancellationAfterFirstOutputDoesNotPublishAPartialBatch() throws Exception {
    final var model = onEdt(() -> model(4));
    final var outputs = model.getOutputExpressions();
    final var before = onEdt(() -> outputs.getMinimalExpression("x"));
    final var snapshot = onEdt(() -> outputs.createOptimizationSnapshot(POS));
    final var cancel = new AtomicBoolean();
    final var outputReports = new AtomicInteger();
    final var context = new OptimizationContext(cancel::get, text -> {
      if (text.startsWith("\n")
          && text.contains(com.cburch.logisim.analyze.Strings.S.fmt("implicantOutputName", "y"))) {
        outputReports.incrementAndGet();
        cancel.set(true);
      }
    });
    assertThrows(CancellationException.class, () -> snapshot.compute(context));
    assertEquals(1, outputReports.get());
    onEdt(() -> {
      assertSame(before, outputs.getMinimalExpression("x"));
      assertEquals(SOP, outputs.getMinimizedFormat("x"));
      assertEquals(SOP, outputs.getMinimizedFormat("y"));
      return null;
    });
  }

  @Test
  void failureAfterFirstOutputLeavesTheLiveModelUnchanged() throws Exception {
    final var model = onEdt(() -> model(4));
    final var outputs = model.getOutputExpressions();
    final var before = onEdt(() -> outputs.getMinimalExpression("x"));
    final var snapshot = onEdt(() -> outputs.createOptimizationSnapshot(POS));
    final var context = new OptimizationContext(() -> false, text -> {
      if (text.contains(com.cburch.logisim.analyze.Strings.S.fmt("implicantOutputName", "y"))) {
        throw new IllegalStateException("Injected computation failure");
      }
    });
    assertThrows(IllegalStateException.class, () -> snapshot.compute(context));
    onEdt(() -> {
      assertSame(before, outputs.getMinimalExpression("x"));
      assertEquals(SOP, outputs.getMinimizedFormat("x"));
      assertEquals(SOP, outputs.getMinimizedFormat("y"));
      return null;
    });
  }

  @Test
  void interruptionIsCooperativeAndPreservesTheInterruptFlag() throws Exception {
    final var model = onEdt(() -> model(4));
    final var snapshot = onEdt(() -> model.getOutputExpressions().createOptimizationSnapshot(SOP));
    try {
      Thread.currentThread().interrupt();
      assertThrows(CancellationException.class, () -> snapshot.compute(OptimizationContext.SILENT));
      assertTrue(Thread.currentThread().isInterrupted());
    } finally {
      Thread.interrupted();
    }
  }

  @Test
  void completeResultsApplyOnceOnEdtAndObserversSeeAllOutputs() throws Exception {
    final var model = onEdt(() -> model(4));
    final var outputs = model.getOutputExpressions();
    final var snapshot = onEdt(() -> outputs.createOptimizationSnapshot(POS));
    final var result = snapshot.compute(OptimizationContext.SILENT);
    final var notifications = new AtomicInteger();
    onEdt(() -> {
      outputs.addOutputExpressionsListener(event -> {
        assertTrue(SwingUtilities.isEventDispatchThread());
        assertEquals(POS, outputs.getMinimizedFormat("x"));
        assertEquals(POS, outputs.getMinimizedFormat("y"));
        notifications.incrementAndGet();
      });
      assertTrue(outputs.applyOptimization(result));
      assertFalse(outputs.applyOptimization(result));
      assertEquals(4, notifications.get());
      return null;
    });
  }

  @Test
  void cancelledCompletedResultCannotBeApplied() throws Exception {
    final var model = onEdt(() -> model(4));
    final var outputs = model.getOutputExpressions();
    final var cancel = new AtomicBoolean();
    final var snapshot = onEdt(() -> outputs.createOptimizationSnapshot(POS));
    final var result = snapshot.compute(new OptimizationContext(cancel::get, text -> {}));
    cancel.set(true);
    onEdt(() -> {
      assertThrows(CancellationException.class, () -> outputs.applyOptimization(result));
      assertEquals(SOP, outputs.getMinimizedFormat("x"));
      assertEquals(SOP, outputs.getMinimizedFormat("y"));
      return null;
    });
  }

  @Test
  void truthTableEditRejectsStaleResultAndPreservesNewValue() throws Exception {
    final var model = onEdt(() -> model(4));
    final var outputs = model.getOutputExpressions();
    final var snapshot = onEdt(() -> outputs.createOptimizationSnapshot(POS));
    final var result = snapshot.compute(OptimizationContext.SILENT);
    onEdt(() -> {
      final var table = model.getTruthTable();
      final var changed = table.getOutputEntry(0, 0) == Entry.ZERO ? Entry.ONE : Entry.ZERO;
      table.setOutputEntry(0, 0, changed);
      assertFalse(outputs.applyOptimization(result));
      assertSame(changed, table.getOutputEntry(0, 0));
      assertEquals(SOP, outputs.getMinimizedFormat("x"));
      return null;
    });
  }

  @Test
  void snapshotAndApplyRejectBackgroundModelAccess() throws Exception {
    final var model = onEdt(() -> model(4));
    final var outputs = model.getOutputExpressions();
    assertThrows(IllegalStateException.class, () -> outputs.createOptimizationSnapshot(SOP));
    final var snapshot = onEdt(() -> outputs.createOptimizationSnapshot(SOP));
    final var result = snapshot.compute(OptimizationContext.SILENT);
    assertThrows(IllegalStateException.class, () -> outputs.applyOptimization(result));
  }

  @Test
  void forcedEightInputResultsAreDeterministicAndEquivalentInBothFormats() throws Exception {
    for (final var format : List.of(SOP, POS)) {
      final var model = onEdt(() -> model(8));
      final var outputs = model.getOutputExpressions();
      final var snapshot = onEdt(() -> outputs.createOptimizationSnapshot(format));
      final var first = snapshot.compute(OptimizationContext.SILENT);
      final var second = snapshot.compute(OptimizationContext.SILENT);
      final var firstTerms = new ArrayList<List<Implicant>>();
      onEdt(() -> {
        assertTrue(outputs.applyOptimization(first));
        for (final var output : List.of("x", "y")) {
          firstTerms.add(outputs.getMinimalImplicants(output));
          assertEquivalent(model, output, outputs.getMinimalExpression(output));
        }
        assertFalse(outputs.applyOptimization(second), "An older revision must not apply twice");
        return null;
      });
      final var nextSnapshot = onEdt(() -> outputs.createOptimizationSnapshot(format));
      final var next = nextSnapshot.compute(OptimizationContext.SILENT);
      onEdt(() -> {
        assertTrue(outputs.applyOptimization(next));
        assertEquals(firstTerms.get(0), outputs.getMinimalImplicants("x"));
        assertEquals(firstTerms.get(1), outputs.getMinimalImplicants("y"));
        return null;
      });
    }
  }

  private static AnalyzerModel model(int inputCount) {
    final var model = new AnalyzerModel();
    model.setVariables(
        List.of(new Var("a", inputCount)), List.of(new Var("x", 1), new Var("y", 1)));
    model.getOutputExpressions().enableUpdates();
    for (var column = 0; column < 2; column++) {
      final var entries = new Entry[1 << inputCount];
      for (var row = 0; row < entries.length; row++) {
        entries[row] = (row & (column == 0 ? 3 : 5)) == 0 ? Entry.ONE : Entry.ZERO;
      }
      model.getTruthTable().setOutputColumn(column, entries);
    }
    return model;
  }

  private static void assertEquivalent(AnalyzerModel model, String output, Expression expression) {
    final var table = model.getTruthTable();
    final var actual = new Entry[table.getRowCount()];
    final var assignment = new Assignments();
    final var inputs = model.getInputs().bits;
    for (var row = 0; row < actual.length; row++) {
      for (var column = 0; column < inputs.size(); column++) {
        assignment.put(inputs.get(column), TruthTable.isInputSet(row, column, inputs.size()));
      }
      actual[row] = expression.evaluate(assignment) ? Entry.ONE : Entry.ZERO;
    }
    assertArrayEquals(table.getOutputColumn(model.getOutputs().bits.indexOf(output)), actual);
  }

  private static <T> T onEdt(Callable<T> operation) throws Exception {
    final var task = new FutureTask<>(operation);
    SwingUtilities.invokeAndWait(task);
    return task.get();
  }
}

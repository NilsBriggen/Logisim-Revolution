/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.analyze.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.analyze.model.AnalyzerModel;
import com.cburch.logisim.analyze.model.Entry;
import com.cburch.logisim.analyze.model.Var;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class OptimizationWorkerTest {
  @Test
  void progressCompletionAndApplicationRunOnEdt() throws Exception {
    final var finished = new CountDownLatch(1);
    final var status = new AtomicReference<OptimizationWorker.Status>();
    final var progressCalls = new AtomicInteger();
    final var events = new AtomicInteger();
    SwingUtilities.invokeAndWait(() -> {
      final var model = model();
      final var outputs = model.getOutputExpressions();
      outputs.addOutputExpressionsListener(event -> {
        assertTrue(SwingUtilities.isEventDispatchThread());
        events.incrementAndGet();
      });
      final var worker = new OptimizationWorker(outputs, AnalyzerModel.FORMAT_PRODUCT_OF_SUMS,
          text -> {
            assertTrue(SwingUtilities.isEventDispatchThread());
            progressCalls.incrementAndGet();
          },
          completion -> {
            try {
              assertTrue(SwingUtilities.isEventDispatchThread());
              status.set(completion.status());
            } finally {
              finished.countDown();
            }
          });
      worker.execute();
    });
    assertTrue(finished.await(5, TimeUnit.SECONDS));
    assertEquals(OptimizationWorker.Status.APPLIED, status.get());
    assertEquals(2, events.get());
    assertTrue(progressCalls.get() > 0);
  }

  @Test
  void cancellationBeforeExecutionNeverAppliesResults() throws Exception {
    final var finished = new CountDownLatch(1);
    final var status = new AtomicReference<OptimizationWorker.Status>();
    final var events = new AtomicInteger();
    SwingUtilities.invokeAndWait(() -> {
      final var outputs = model().getOutputExpressions();
      outputs.addOutputExpressionsListener(event -> events.incrementAndGet());
      final var worker = new OptimizationWorker(
          outputs, AnalyzerModel.FORMAT_PRODUCT_OF_SUMS, text -> {}, completion -> {
            status.set(completion.status());
            finished.countDown();
          });
      worker.requestCancellation();
      worker.execute();
    });
    assertTrue(finished.await(5, TimeUnit.SECONDS));
    assertEquals(OptimizationWorker.Status.CANCELLED, status.get());
    assertEquals(0, events.get());
  }

  @Test
  void cancelAfterBackgroundCompletionStillPreventsEdtApplication() throws Exception {
    final var finished = new CountDownLatch(1);
    final var status = new AtomicReference<OptimizationWorker.Status>();
    final var events = new AtomicInteger();
    SwingUtilities.invokeAndWait(() -> {
      final var outputs = model().getOutputExpressions();
      outputs.addOutputExpressionsListener(event -> events.incrementAndGet());
      final var worker = new OptimizationWorker(
          outputs, AnalyzerModel.FORMAT_PRODUCT_OF_SUMS, text -> {}, completion -> {
            status.set(completion.status());
            finished.countDown();
          });
      worker.execute();
      try {
        // Hold the EDT only in this test so completion cannot apply before the late Cancel.
        worker.get(5, TimeUnit.SECONDS);
        assertTrue(worker.isDone());
        worker.requestCancellation();
      } catch (Exception ex) {
        throw new AssertionError(ex);
      }
    });
    assertTrue(finished.await(5, TimeUnit.SECONDS));
    assertEquals(OptimizationWorker.Status.CANCELLED, status.get());
    assertEquals(0, events.get());
  }

  @Test
  void repeatedCancellationOutsideEdtCompletesOnceEvenWithLateDoneCallback() throws Exception {
    final var finished = new CountDownLatch(1);
    final var worker = new AtomicReference<OptimizationWorker>();
    final var status = new AtomicReference<OptimizationWorker.Status>();
    final var completions = new AtomicInteger();
    final var events = new AtomicInteger();
    SwingUtilities.invokeAndWait(() -> {
      final var outputs = model().getOutputExpressions();
      outputs.addOutputExpressionsListener(event -> events.incrementAndGet());
      worker.set(new OptimizationWorker(
          outputs, AnalyzerModel.FORMAT_PRODUCT_OF_SUMS, text -> {}, completion -> {
            try {
              assertTrue(SwingUtilities.isEventDispatchThread());
              status.set(completion.status());
              completions.incrementAndGet();
            } finally {
              finished.countDown();
            }
          }));
    });
    worker.get().requestCancellation();
    worker.get().requestCancellation();
    worker.get().execute();
    assertTrue(finished.await(5, TimeUnit.SECONDS));
    // A running worker/JDK may deliver its own done callback after cancellation completed the UI.
    SwingUtilities.invokeAndWait(worker.get()::done);
    assertEquals(OptimizationWorker.Status.CANCELLED, status.get());
    assertEquals(1, completions.get());
    assertEquals(0, events.get());
  }

  private static AnalyzerModel model() {
    final var model = new AnalyzerModel();
    model.setVariables(List.of(new Var("a", 2)), List.of(new Var("x", 1)));
    model.getOutputExpressions().enableUpdates();
    model.getTruthTable().setOutputColumn(
        0, new Entry[] {Entry.ONE, Entry.ZERO, Entry.ONE, Entry.ZERO});
    return model;
  }
}

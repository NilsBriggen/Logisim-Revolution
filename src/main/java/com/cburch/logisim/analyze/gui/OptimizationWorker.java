/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.analyze.gui;

import com.cburch.logisim.analyze.model.OptimizationContext;
import com.cburch.logisim.analyze.model.OutputExpressions;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/** Bridges snapshot computation to EDT-only progress, completion and model application. */
final class OptimizationWorker extends SwingWorker<OutputExpressions.OptimizationResult, String> {
  enum Status { APPLIED, CANCELLED, STALE, FAILED }

  record Completion(Status status, Throwable error) {}

  private final OutputExpressions outputs;
  private final OutputExpressions.OptimizationSnapshot snapshot;
  private final Consumer<String> progress;
  private final Consumer<Completion> completion;
  private final AtomicBoolean cancellationRequested = new AtomicBoolean();
  private final AtomicBoolean completionDelivered = new AtomicBoolean();

  OptimizationWorker(OutputExpressions outputs, int format, Consumer<String> progress,
      Consumer<Completion> completion) {
    this.outputs = outputs;
    snapshot = outputs.createOptimizationSnapshot(format);
    this.progress = progress;
    this.completion = completion;
  }

  @Override
  protected OutputExpressions.OptimizationResult doInBackground() {
    final var buffer = new StringBuilder();
    final var lastReport = new long[] {0};
    final var context = new OptimizationContext(this::cancellationRequested, text -> {
      buffer.append(text);
      final var now = System.nanoTime();
      if (buffer.length() >= 4096 || now - lastReport[0] >= 100_000_000L) {
        publish(buffer.toString());
        buffer.setLength(0);
        lastReport[0] = now;
      }
    });
    final var result = snapshot.compute(context);
    if (!buffer.isEmpty()) publish(buffer.toString());
    return result;
  }

  @Override
  protected void process(List<String> chunks) {
    if (cancellationRequested()) return;
    progress.accept(String.join("", chunks));
  }

  @Override
  protected void done() {
    if (!completionDelivered.compareAndSet(false, true)) return;
    Completion result;
    try {
      if (cancellationRequested()) throw new CancellationException();
      result =
          new Completion(outputs.applyOptimization(get()) ? Status.APPLIED : Status.STALE, null);
    } catch (CancellationException ex) {
      result = new Completion(Status.CANCELLED, null);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      result = new Completion(Status.CANCELLED, null);
    } catch (ExecutionException ex) {
      result = new Completion(Status.FAILED, ex.getCause());
    } catch (RuntimeException ex) {
      result = new Completion(Status.FAILED, ex);
    }
    completion.accept(result);
  }

  private boolean cancellationRequested() {
    return cancellationRequested.get() || isCancelled();
  }

  void requestCancellation() {
    // A completed Future may still be waiting for its EDT done callback. In that interval,
    // cancel(true) alone returns false; the token must also prevent applying its result.
    if (!cancellationRequested.compareAndSet(false, true)) return;
    cancel(true);
    // Some JDKs invoke done only after the background callable starts. Cancellation while
    // queued must also finish the UI; done's guard handles a later SwingWorker callback.
    SwingUtilities.invokeLater(this::done);
  }
}

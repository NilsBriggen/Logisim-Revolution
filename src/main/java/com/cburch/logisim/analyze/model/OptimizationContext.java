/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.analyze.model;

import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** Cancellation and progress for a computation that does not own Swing components. */
public final class OptimizationContext {
  static final OptimizationContext SILENT = new OptimizationContext(() -> false, text -> {});

  private final BooleanSupplier cancelled;
  private final Consumer<String> progress;

  public OptimizationContext(BooleanSupplier cancelled, Consumer<String> progress) {
    this.cancelled = cancelled;
    this.progress = progress;
  }

  public void checkCancelled() {
    if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
      throw new CancellationException();
    }
  }

  void report(String text) {
    checkCancelled();
    progress.accept(text);
  }
}

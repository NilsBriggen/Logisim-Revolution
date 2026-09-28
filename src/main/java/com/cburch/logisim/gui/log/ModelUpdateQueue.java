/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.log;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/**
 * Carries simulator samples from the simulation thread to the event dispatch thread.
 *
 * <p>The log {@link Model} and the timing diagram are Swing-side state: every change to them, and
 * every listener they notify, must happen on the event dispatch thread. The simulator, however,
 * reports propagations and resets on its own thread. That thread therefore only reads the signal
 * values into an immutable {@link Model.Sample} and posts it here. All samples posted while the
 * event thread is busy are applied, in order, by one coalesced {@code invokeLater}.
 *
 * <p>If the event thread falls far behind a fast-running simulation, posting waits briefly so the
 * backlog stays bounded, much as recording used to slow the simulation when it ran on the
 * simulation thread.
 */
final class ModelUpdateQueue {
  /** Upper bound on samples waiting for the event thread before posting starts to wait. */
  static final int MAX_PENDING = 4096;

  /** Longest a single post waits for the event thread, so a stalled event thread cannot hang it. */
  private static final long MAX_WAIT_NANOS = TimeUnit.MILLISECONDS.toNanos(250);

  private record Update(
      Model model, Model.Sample sample, boolean reset, boolean ticked, boolean stepped,
      boolean propagated) {
    void apply() {
      if (reset) model.applyReset(sample);
      else model.applyPropagation(sample, ticked, stepped, propagated);
    }
  }

  private final Object lock = new Object();
  private final Consumer<Runnable> eventThread;
  private ArrayList<Update> pending = new ArrayList<>();
  private boolean scheduled;

  ModelUpdateQueue() {
    this(SwingUtilities::invokeLater);
  }

  /** Test hook: {@code eventThread} stands in for {@link SwingUtilities#invokeLater}. */
  ModelUpdateQueue(Consumer<Runnable> eventThread) {
    this.eventThread = eventThread;
  }

  /** Samples {@code model} now and queues the propagation for the event thread. */
  void propagationCompleted(Model model, boolean ticked, boolean stepped, boolean propagated) {
    if (!model.wantsSample(stepped, propagated)) return;
    post(new Update(model, model.sample(), false, ticked, stepped, propagated));
  }

  /** Samples {@code model} now and queues a reset for the event thread. */
  void simulatorReset(Model model) {
    post(new Update(model, model.sample(), true, false, false, false));
  }

  int pendingCount() {
    synchronized (lock) {
      return pending.size();
    }
  }

  private void post(Update update) {
    synchronized (lock) {
      if (pending.size() >= MAX_PENDING && !SwingUtilities.isEventDispatchThread()) {
        final var deadline = System.nanoTime() + MAX_WAIT_NANOS;
        var remaining = MAX_WAIT_NANOS;
        while (pending.size() >= MAX_PENDING && remaining > 0) {
          try {
            TimeUnit.NANOSECONDS.timedWait(lock, remaining);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
          }
          remaining = deadline - System.nanoTime();
        }
      }
      pending.add(update);
      if (scheduled) return;
      scheduled = true;
    }
    eventThread.accept(this::drain);
  }

  /** Applies every queued update, oldest first. Runs on the event dispatch thread. */
  void drain() {
    final ArrayList<Update> batch;
    synchronized (lock) {
      batch = pending;
      pending = new ArrayList<>();
      scheduled = false;
      lock.notifyAll();
    }
    for (final var update : batch) update.apply();
  }
}

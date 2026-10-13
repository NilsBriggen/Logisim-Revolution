/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.log;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.circuit.PauseReason;
import com.cburch.logisim.data.Value;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

/**
 * The watchpoints of one log model. They are edited on the event dispatch thread and evaluated on
 * the simulation thread, against each sample the log takes.
 */
public final class WatchpointSet {
  private final CopyOnWriteArrayList<Watchpoint> watchpoints = new CopyOnWriteArrayList<>();
  private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();

  public List<Watchpoint> getAll() {
    return List.copyOf(watchpoints);
  }

  public boolean isEmpty() {
    return watchpoints.isEmpty();
  }

  public void add(Watchpoint watchpoint) {
    watchpoints.add(watchpoint);
    fireChanged();
  }

  public void remove(Watchpoint watchpoint) {
    if (watchpoints.remove(watchpoint)) fireChanged();
  }

  /** Listeners run on whichever thread changed the set, or on the simulation thread on a hit. */
  public void addListener(Runnable listener) {
    listeners.add(listener);
  }

  public void removeListener(Runnable listener) {
    listeners.remove(listener);
  }

  private void fireChanged() {
    for (final var listener : listeners) listener.run();
  }

  /** Forgets every watchpoint's last value, as after a simulator reset. */
  void reset() {
    for (final var watchpoint : watchpoints) watchpoint.reset();
  }

  /**
   * Feeds a sample to every watchpoint. Simulation thread.
   *
   * @param sampled the sampled value of each signal, null for one that was not sampled
   * @param tick the propagator tick count, recorded in the pause reason
   * @return why to pause when at least one watchpoint fired, naming all that did; otherwise null
   */
  PauseReason check(Function<SignalInfo, Value> sampled, long tick) {
    if (watchpoints.isEmpty()) return null;
    StringBuilder hits = null;
    Watchpoint first = null;
    for (final var watchpoint : watchpoints) {
      if (watchpoint.update(sampled.apply(watchpoint.getSignal()))) {
        if (first == null) {
          first = watchpoint;
          hits = new StringBuilder(watchpoint.describe());
        } else {
          hits.append(", ").append(watchpoint.describe());
        }
      }
    }
    if (first == null) return null;
    fireChanged();
    return new PauseReason(
        PauseReason.Kind.WATCHPOINT, S.get("watchHitMessage", hits), tick, first, true);
  }
}

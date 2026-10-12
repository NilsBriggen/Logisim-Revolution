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

import com.cburch.logisim.data.Value;
import com.cburch.logisim.util.StringGetter;

/**
 * A condition on one logged signal that pauses the simulation clock when it becomes true.
 *
 * <p>Value conditions fire when the signal starts to match, not on every sample while it matches,
 * so the simulation can be resumed past a hit.
 */
public final class Watchpoint {

  /** What to watch for. */
  public enum Condition {
    EQUALS(S.getter("watchConditionEquals")),
    NOT_EQUALS(S.getter("watchConditionNotEquals")),
    RISING(S.getter("watchConditionRising")),
    FALLING(S.getter("watchConditionFalling")),
    CHANGES(S.getter("watchConditionChanges"));

    private final StringGetter label;

    Condition(StringGetter label) {
      this.label = label;
    }

    /** Whether the condition compares against a value. */
    public boolean usesOperand() {
      return this == EQUALS || this == NOT_EQUALS;
    }

    @Override
    public String toString() {
      return label.toString();
    }
  }

  private final SignalInfo signal;
  private final Condition condition;
  private final long operand;
  private volatile boolean enabled = true;
  private volatile int hits;
  // Simulation thread only.
  private Value previous;

  public Watchpoint(SignalInfo signal, Condition condition, long operand) {
    this.signal = signal;
    this.condition = condition;
    this.operand = operand;
  }

  public SignalInfo getSignal() {
    return signal;
  }

  public Condition getCondition() {
    return condition;
  }

  public long getOperand() {
    return operand;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean value) {
    enabled = value;
  }

  public int getHits() {
    return hits;
  }

  /** Forgets the last seen value, so the next sample only establishes a baseline. */
  void reset() {
    previous = null;
  }

  /**
   * Records the signal's new value and returns whether this watchpoint fires for it. A disabled
   * watchpoint still follows the value, so enabling it does not fire on stale history.
   */
  boolean update(Value current) {
    final var before = previous;
    previous = current;
    if (!enabled || current == null || before == null) return false;
    final var fired = fires(condition, operand, before, current);
    if (fired) hits++;
    return fired;
  }

  static boolean fires(Condition condition, long operand, Value before, Value current) {
    return switch (condition) {
      case EQUALS, NOT_EQUALS ->
          active(condition, operand, current) && !active(condition, operand, before);
      case RISING -> isZero(before) && isNonZero(current);
      case FALLING -> isNonZero(before) && isZero(current);
      case CHANGES -> !before.equals(current);
    };
  }

  private static boolean active(Condition condition, long operand, Value v) {
    if (!v.isFullyDefined()) return condition == Condition.NOT_EQUALS;
    final var width = v.getWidth();
    final var mask = width >= 64 ? -1L : (1L << width) - 1;
    final var equal = (v.toLongValue() & mask) == (operand & mask);
    return condition == Condition.EQUALS ? equal : !equal;
  }

  private static boolean isZero(Value v) {
    return v.isFullyDefined() && v.toLongValue() == 0;
  }

  private static boolean isNonZero(Value v) {
    return v.isFullyDefined() && v.toLongValue() != 0;
  }

  /** A description such as "count = 12" or "clk rises", for the pause message. */
  public String describe() {
    final var name = signal.getDisplayName();
    return switch (condition) {
      case EQUALS -> S.get("watchDescribeEquals", name, Long.toString(operand));
      case NOT_EQUALS -> S.get("watchDescribeNotEquals", name, Long.toString(operand));
      case RISING -> S.get("watchDescribeRising", name);
      case FALLING -> S.get("watchDescribeFalling", name);
      case CHANGES -> S.get("watchDescribeChanges", name);
    };
  }
}

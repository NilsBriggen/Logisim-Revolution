/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.proj;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/** The shared save/discard/cancel decision, without closing a window or stopping recovery. */
public final class ProjectCloseConfirmation {
  private ProjectCloseConfirmation() {}

  /**
   * Allows closing only after an explicit discard or a successful save.
   *
   * <p>The prompt presents Save, Discard, Cancel in that order. Dismissal and unknown results are
   * cancellation. Cleanup belongs to the caller, after every project in a quit request has agreed.
   */
  public static boolean confirm(boolean dirty, IntSupplier prompt, BooleanSupplier save) {
    if (!dirty) return true;
    return switch (prompt.getAsInt()) {
      case 0 -> save.getAsBoolean();
      case 1 -> true;
      default -> false;
    };
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.generic;

import com.cburch.logisim.Main;
import java.awt.Component;
import java.awt.Cursor;
import java.util.function.Supplier;
import javax.swing.SwingUtilities;

/**
 * Shows a busy pointer while a short blocking task runs on the event dispatch thread.
 *
 * <p>Opening or saving a circuit runs on the event thread, so the interface stops repainting for as
 * long as it takes. Without a busy pointer there is nothing at all to say the application is
 * working rather than hung.
 *
 * <p>This is for tasks short enough to keep on the event thread. Anything long enough for the user
 * to consider giving up belongs on a {@link javax.swing.SwingWorker} with a progress indicator, as
 * the circuit analyser already does.
 */
public final class WaitCursor {

  private WaitCursor() {
    // Utility class, not instantiable.
  }

  /** Runs {@code task}, showing a busy pointer for its duration. */
  public static void run(Component parentComponent, Runnable task) {
    get(
        parentComponent,
        () -> {
          task.run();
          return null;
        });
  }

  /**
   * Runs {@code task} and returns its result, showing a busy pointer for its duration.
   *
   * <p>The pointer is always restored, including when the task throws.
   */
  public static <T> T get(Component parentComponent, Supplier<T> task) {
    final var root = rootOf(parentComponent);
    if (root == null) return task.get();

    final var previous = root.getCursor();
    root.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
    try {
      return task.get();
    } finally {
      root.setCursor(previous);
    }
  }

  /**
   * Returns the root pane to set the pointer on, or null when there is nothing to show it on.
   *
   * <p>Setting the cursor on the root pane rather than the component itself means the whole window
   * shows as busy, which is what the user is actually waiting for.
   */
  private static Component rootOf(Component parentComponent) {
    if (!Main.hasGui() || parentComponent == null) return null;
    final var root = SwingUtilities.getRootPane(parentComponent);
    return (root == null) ? parentComponent : root;
  }
}

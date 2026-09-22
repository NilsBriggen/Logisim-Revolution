/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.generic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.TestBase;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

/** Tests the busy pointer helper. */
public class WaitCursorTest extends TestBase {

  @Test
  public void taskRunsAndItsResultIsReturned() {
    assertEquals("done", WaitCursor.get(new JPanel(), () -> "done"));
  }

  @Test
  public void taskRunsWhenThereIsNoComponent() {
    final var ran = new AtomicBoolean(false);
    WaitCursor.run(null, () -> ran.set(true));
    assertTrue(ran.get(), "the task must still run without a component to show the pointer on");
  }

  /** A task that throws must not leave the window stuck showing a busy pointer. */
  @Test
  public void pointerIsRestoredWhenTheTaskThrows() {
    final var panel = new JPanel();
    final var before = panel.getCursor();

    assertThrows(
        IllegalStateException.class,
        () ->
            WaitCursor.run(
                panel,
                () -> {
                  throw new IllegalStateException("boom");
                }));

    assertEquals(before, panel.getCursor());
  }

  @Test
  public void pointerIsRestoredAfterASuccessfulTask() {
    final var panel = new JPanel();
    final var before = panel.getCursor();

    WaitCursor.run(panel, () -> {});

    assertEquals(before, panel.getCursor());
  }
}

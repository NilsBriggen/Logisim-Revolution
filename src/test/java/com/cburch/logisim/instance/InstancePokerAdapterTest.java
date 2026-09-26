/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.ComponentUserEvent;
import com.cburch.logisim.gui.main.Canvas;
import java.awt.Point;
import java.awt.event.MouseEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The press that starts a poke reaches the poker exactly once.
 *
 * <p>{@code PokeTool} asks for a caret and then hands it the press. The adapter used to deliver the
 * press itself as well, so a poker that toggles on press (the DIP switch) toggled twice on the
 * first click and appeared to do nothing.
 */
class InstancePokerAdapterTest {

  /** Counts presses; instantiated reflectively by the adapter, hence the static counter. */
  public static class CountingPoker extends InstancePoker {
    static int presses;

    @Override
    public void mousePressed(InstanceState state, MouseEvent e) {
      presses++;
    }
  }

  @BeforeEach
  void reset() {
    CountingPoker.presses = 0;
  }

  @Test
  void theFirstPressIsDispatchedOnce() {
    final var canvas = mock(Canvas.class);
    when(canvas.getCircuitState()).thenReturn(mock(CircuitState.class));
    when(canvas.getLocationOnScreen()).thenReturn(new Point());
    final var adapter = new InstancePokerAdapter(mock(InstanceComponent.class), CountingPoker.class);

    // What PokeTool.mousePressed does on a click on a component without a caret yet.
    final var caret = adapter.getPokeCaret(new ComponentUserEvent(canvas, 15, 25));
    assertNotNull(caret);
    assertEquals(0, CountingPoker.presses, "asking for a caret must not press");
    caret.mousePressed(
        new MouseEvent(canvas, MouseEvent.MOUSE_PRESSED, 0, 0, 15, 25, 1, false));

    assertEquals(1, CountingPoker.presses);
  }
}

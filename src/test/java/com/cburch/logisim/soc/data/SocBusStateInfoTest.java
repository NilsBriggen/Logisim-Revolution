/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.soc.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import com.cburch.logisim.comp.Component;
import java.awt.Window;
import org.junit.jupiter.api.Test;

class SocBusStateInfoTest {

  /**
   * A bus is registered whenever a circuit containing it is loaded, including by {@code --tty}
   * without a display, so registering it must not build its memory-map window.
   */
  @Test
  void creatingBusStateOpensNoWindow() {
    final var windowsBefore = Window.getWindows().length;

    final var info = new SocBusStateInfo(mock(SocSimulationManager.class), mock(Component.class));
    info.setVisible(false);

    assertFalse(info.isVisible());
    assertEquals(windowsBefore, Window.getWindows().length);
  }
}

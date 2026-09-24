/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.soc.gui;

import static com.cburch.logisim.soc.Strings.S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cburch.logisim.soc.data.SocBusStateInfo;
import org.junit.jupiter.api.Test;

class BusTransactionInsertionGuiTest {
  @Test
  void transactionTitleUsesCurrentBusDisplayIdentity() {
    final var bus = mock(SocBusStateInfo.class);
    when(bus.getName()).thenReturn("Peripheral bus", "Soc Bus@120,240");

    assertEquals(
        S.get("SocInsertTransWindowTitle") + " Peripheral bus",
        BusTransactionInsertionGui.getTransactionTitle(bus));
    assertEquals(
        S.get("SocInsertTransWindowTitle") + " Soc Bus@120,240",
        BusTransactionInsertionGui.getTransactionTitle(bus));
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.circuit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Location;
import org.junit.jupiter.api.Test;

/** Whether a net is drawn as a bus. */
class WireBundleTest {

  private static WireBundle bundle() {
    return new WireBundle(Location.create(0, 0, false));
  }

  /** A multi-bit net fed by one width-setting port, such as an 8-bit input into a probe. */
  @Test
  void netWithOneMultiBitPortIsBus() {
    final var net = bundle();
    net.setWidth(BitWidth.create(8), Location.create(10, 0, false));
    assertTrue(net.isBus());
    assertTrue(net.isValid());
  }

  @Test
  void netWithTwoMatchingMultiBitPortsIsBus() {
    final var net = bundle();
    net.setWidth(BitWidth.create(8), Location.create(10, 0, false));
    net.setWidth(BitWidth.create(8), Location.create(20, 0, false));
    assertTrue(net.isBus());
  }

  @Test
  void oneBitNetIsNotBus() {
    final var net = bundle();
    net.setWidth(BitWidth.ONE, Location.create(10, 0, false));
    assertFalse(net.isBus());
    net.setWidth(BitWidth.ONE, Location.create(20, 0, false));
    assertFalse(net.isBus());
  }

  @Test
  void netWithoutWidthIsNotBus() {
    final var net = bundle();
    net.setWidth(BitWidth.UNKNOWN, Location.create(10, 0, false));
    assertFalse(net.isBus());
  }

  @Test
  void conflictingWidthsMakeTheNetInvalid() {
    final var net = bundle();
    net.setWidth(BitWidth.create(8), Location.create(10, 0, false));
    net.setWidth(BitWidth.create(4), Location.create(20, 0, false));
    assertFalse(net.isValid());
  }
}

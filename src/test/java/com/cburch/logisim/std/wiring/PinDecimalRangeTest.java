/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.wiring;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class PinDecimalRangeTest {

  @Test
  void rangeMatchesWhatTheDialogAccepts() {
    assertArrayEquals(
        new BigInteger[] {BigInteger.valueOf(-128), BigInteger.valueOf(127)},
        Pin.decimalRange(8, true));
    assertArrayEquals(
        new BigInteger[] {BigInteger.ZERO, BigInteger.valueOf(255)}, Pin.decimalRange(8, false));
    assertArrayEquals(
        new BigInteger[] {BigInteger.ZERO, new BigInteger("18446744073709551615")},
        Pin.decimalRange(64, false));
  }

  @Test
  void hintNamesBothEnds() {
    final var hint = Pin.decimalRangeHint(4, true, false);
    assertTrue(hint.contains("-8") && hint.contains("7"), hint);
  }
}

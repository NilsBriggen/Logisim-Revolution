/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.ttl;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;

/**
 * TTL 74280: 9-bit odd/even parity generator/checker. Σ EVEN is HIGH when an even number of the
 * inputs A to I are HIGH, Σ ODD when an odd number are.
 *
 * <p>Model based on the Texas Instruments SN74LS280 data sheet (pin 3 is not connected).
 */
public class Ttl74280 extends AbstractTtlGate {
  /**
   * Unique identifier of the tool, used as reference in project files. Do NOT change as it will
   * prevent project files from loading.
   *
   * <p>Identifier value must MUST be unique string among all tools.
   */
  public static final String _ID = "74280";

  public static final int PORT_G = 0;
  public static final int PORT_H = 1;
  public static final int PORT_I = 2;
  public static final int PORT_EVEN = 3;
  public static final int PORT_ODD = 4;
  public static final int PORT_A = 5;
  public static final int PORT_B = 6;
  public static final int PORT_C = 7;
  public static final int PORT_D = 8;
  public static final int PORT_E = 9;
  public static final int PORT_F = 10;

  private static final int[] INPUTS = {
    PORT_A, PORT_B, PORT_C, PORT_D, PORT_E, PORT_F, PORT_G, PORT_H, PORT_I
  };
  private static final int DELAY = 2;

  public Ttl74280() {
    super(
        _ID,
        (byte) 14,
        new byte[] {5, 6},
        new byte[] {3},
        new String[] {"G", "H", "I", "ΣEVEN", "ΣODD", "A", "B", "C", "D", "E", "F"},
        null);
  }

  @Override
  public void paintInternal(InstancePainter painter, int x, int y, int height, boolean up) {
    super.paintBase(painter, false, false);
    Drawgates.paintPortNamesByPin(
        painter,
        x,
        y,
        height,
        new String[] {"G", "H", null, "I", "EVN", "ODD", null, "A", "B", "C", "D", "E", "F", null});
  }

  @Override
  public void propagateTtl(InstanceState state) {
    var odd = false;
    var defined = true;
    for (final var port : INPUTS) {
      final var v = state.getPortValue(port);
      if (v == Value.TRUE) odd = !odd;
      else if (v != Value.FALSE) defined = false;
    }
    state.setPort(PORT_EVEN, !defined ? Value.UNKNOWN : odd ? Value.FALSE : Value.TRUE, DELAY);
    state.setPort(PORT_ODD, !defined ? Value.UNKNOWN : odd ? Value.TRUE : Value.FALSE, DELAY);
  }
}

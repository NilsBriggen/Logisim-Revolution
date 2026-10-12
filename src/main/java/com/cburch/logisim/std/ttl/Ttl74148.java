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
 * TTL 74148: 8-line to 3-line priority encoder, all inputs and outputs active LOW. Input 7 has the
 * highest priority. EO goes low when the encoder is enabled and no input is active (for cascading
 * into the EI of a lower-priority stage); GS goes low when it is enabled and some input is active.
 *
 * <p>Model based on the Texas Instruments SN74LS148 data sheet.
 */
public class Ttl74148 extends AbstractTtlGate {
  /**
   * Unique identifier of the tool, used as reference in project files. Do NOT change as it will
   * prevent project files from loading.
   *
   * <p>Identifier value must MUST be unique string among all tools.
   */
  public static final String _ID = "74148";

  // Logical port indexes (physical pins 8 and 16 are GND and VCC).
  public static final int PORT_I4 = 0;
  public static final int PORT_I5 = 1;
  public static final int PORT_I6 = 2;
  public static final int PORT_I7 = 3;
  public static final int PORT_EI = 4;
  public static final int PORT_A2 = 5;
  public static final int PORT_A1 = 6;
  public static final int PORT_A0 = 7;
  public static final int PORT_I0 = 8;
  public static final int PORT_I1 = 9;
  public static final int PORT_I2 = 10;
  public static final int PORT_I3 = 11;
  public static final int PORT_GS = 12;
  public static final int PORT_EO = 13;

  private static final int[] INPUTS = {PORT_I0, PORT_I1, PORT_I2, PORT_I3, PORT_I4, PORT_I5, PORT_I6, PORT_I7};
  private static final int DELAY = 1;

  public Ttl74148() {
    super(
        _ID,
        (byte) 16,
        new byte[] {6, 7, 9, 14, 15},
        new String[] {
          "n4", "n5", "n6", "n7", "nEI (Enable input, active LOW)", "nA2", "nA1", "nA0",
          "n0", "n1", "n2", "n3", "nGS (Group select, active LOW)", "nEO (Enable output, active LOW)"
        },
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
        new String[] {
          "4", "5", "6", "7", "EI", "A2", "A1", null, "A0", "0", "1", "2", "3", "GS", "EO", null
        });
  }

  @Override
  public void propagateTtl(InstanceState state) {
    final var enable = state.getPortValue(PORT_EI);
    Value a2;
    Value a1;
    Value a0;
    Value gs;
    Value eo;
    if (enable == Value.TRUE) {
      a2 = a1 = a0 = gs = eo = Value.TRUE;
    } else if (enable != Value.FALSE) {
      a2 = a1 = a0 = gs = eo = Value.UNKNOWN;
    } else {
      var active = -1;
      var undefined = false;
      for (var i = 7; i >= 0 && active < 0; i--) {
        final var v = state.getPortValue(INPUTS[i]);
        if (v == Value.FALSE) active = i;
        else if (v != Value.TRUE) undefined = true;
      }
      if (undefined) {
        // An undefined input of higher priority than any active one decides nothing.
        a2 = a1 = a0 = gs = eo = Value.UNKNOWN;
      } else if (active < 0) {
        a2 = a1 = a0 = gs = Value.TRUE;
        eo = Value.FALSE;
      } else {
        // Outputs are the inverted binary code of the active input.
        a2 = (active & 4) != 0 ? Value.FALSE : Value.TRUE;
        a1 = (active & 2) != 0 ? Value.FALSE : Value.TRUE;
        a0 = (active & 1) != 0 ? Value.FALSE : Value.TRUE;
        gs = Value.FALSE;
        eo = Value.TRUE;
      }
    }
    state.setPort(PORT_A2, a2, DELAY);
    state.setPort(PORT_A1, a1, DELAY);
    state.setPort(PORT_A0, a0, DELAY);
    state.setPort(PORT_GS, gs, DELAY);
    state.setPort(PORT_EO, eo, DELAY);
  }
}

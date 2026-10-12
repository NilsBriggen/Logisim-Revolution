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
 * TTL 74154: 4-line to 16-line decoder/demultiplexer with active-LOW outputs, enabled while both
 * nG1 and nG2 are LOW. A is the least significant select input.
 *
 * <p>Model based on the Texas Instruments SN74154 data sheet (DIP-24).
 */
public class Ttl74154 extends AbstractTtlGate {
  /**
   * Unique identifier of the tool, used as reference in project files. Do NOT change as it will
   * prevent project files from loading.
   *
   * <p>Identifier value must MUST be unique string among all tools.
   */
  public static final String _ID = "74154";

  // Logical ports 0 to 15 are the outputs nY0 to nY15 (physical pins 1-11 and 13-17).
  public static final int PORT_NG1 = 16;
  public static final int PORT_NG2 = 17;
  public static final int PORT_D = 18;
  public static final int PORT_C = 19;
  public static final int PORT_B = 20;
  public static final int PORT_A = 21;

  private static final int DELAY = 1;

  public Ttl74154() {
    super(
        _ID,
        (byte) 24,
        new byte[] {1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 13, 14, 15, 16, 17},
        new String[] {
          "nY0", "nY1", "nY2", "nY3", "nY4", "nY5", "nY6", "nY7", "nY8", "nY9", "nY10",
          "nY11", "nY12", "nY13", "nY14", "nY15",
          "nG1 (Enable, active LOW)", "nG2 (Enable, active LOW)", "D", "C", "B", "A"
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
          "0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", null,
          "11", "12", "13", "14", "15", "G1", "G2", "D", "C", "B", "A", null
        });
  }

  @Override
  public void propagateTtl(InstanceState state) {
    final var g1 = state.getPortValue(PORT_NG1);
    final var g2 = state.getPortValue(PORT_NG2);
    final var select =
        Value.create(
            new Value[] {
              state.getPortValue(PORT_A),
              state.getPortValue(PORT_B),
              state.getPortValue(PORT_C),
              state.getPortValue(PORT_D)
            });
    final var disabled = g1 == Value.TRUE || g2 == Value.TRUE;
    final var defined =
        disabled || (g1 == Value.FALSE && g2 == Value.FALSE && select.isFullyDefined());
    final var selected = defined && !disabled ? (int) select.toLongValue() : -1;
    for (var i = 0; i < 16; i++) {
      final var out = !defined ? Value.UNKNOWN : i == selected ? Value.FALSE : Value.TRUE;
      state.setPort(i, out, DELAY);
    }
  }
}

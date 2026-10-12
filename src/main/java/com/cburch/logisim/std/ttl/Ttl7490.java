/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.ttl;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.fpga.designrulecheck.netlistComponent;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.StdAttr;

/**
 * TTL 7490: decade counter, a divide-by-two stage (CKA to QA) and a divide-by-five stage (CKB to
 * QB, QC, QD), both on falling edges. R0(1) and R0(2) both high reset to 0; R9(1) and R9(2) both
 * high set 9 and take priority. Connecting QA to CKB counts 0 to 9 in BCD.
 *
 * <p>Model based on the Texas Instruments SN74LS90 data sheet (DIP-14, VCC on pin 5, GND on pin
 * 10).
 */
public class Ttl7490 extends AbstractTtlGate {
  /**
   * Unique identifier of the tool, used as reference in project files. Do NOT change as it will
   * prevent project files from loading.
   *
   * <p>Identifier value must MUST be unique string among all tools.
   */
  public static final String _ID = "7490";

  public static final int PORT_INDEX_CKB = 0;
  public static final int PORT_INDEX_R0_1 = 1;
  public static final int PORT_INDEX_R0_2 = 2;
  public static final int PORT_INDEX_R9_1 = 3;
  public static final int PORT_INDEX_R9_2 = 4;
  public static final int PORT_INDEX_QC = 5;
  public static final int PORT_INDEX_QB = 6;
  public static final int PORT_INDEX_QD = 7;
  public static final int PORT_INDEX_QA = 8;
  public static final int PORT_INDEX_CKA = 9;

  private static final byte[] OUTPUT_PORTS = {8, 9, 11, 12};
  private static final byte[] UNUSED_PINS = {4, 13};
  private static final String[] PORT_NAMES = {
    "CKB (Clock B)",
    "R0(1) (Reset to 0, active HIGH)",
    "R0(2) (Reset to 0, active HIGH)",
    "R9(1) (Set to 9, active HIGH)",
    "R9(2) (Set to 9, active HIGH)",
    "QC",
    "QB",
    "QD",
    "QA",
    "CKA (Clock A)"
  };
  private static final BitWidth WIDTH = BitWidth.create(4);

  public Ttl7490() {
    super(_ID, (byte) 14, OUTPUT_PORTS, UNUSED_PINS, PORT_NAMES, (byte) 5, (byte) 10, null);
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
          "CKB", "R0(1)", "R0(2)", null, null, "R9(1)", "R9(2)",
          "QC", "QB", null, "QD", "QA", null, "CKA"
        });
  }

  @Override
  public void propagateTtl(InstanceState state) {
    var data = (TtlRegisterData) state.getData();
    if (data == null) {
      data = new TtlRegisterData(WIDTH);
      state.setData(data);
    }
    final var clockA = data.updateClock(state.getPortValue(PORT_INDEX_CKA), 0, StdAttr.TRIG_FALLING);
    final var clockB = data.updateClock(state.getPortValue(PORT_INDEX_CKB), 1, StdAttr.TRIG_FALLING);
    final var values = data.getValue().getAll();

    if (state.getPortValue(PORT_INDEX_R9_1) == Value.TRUE
        && state.getPortValue(PORT_INDEX_R9_2) == Value.TRUE) {
      data.setValue(Value.createKnown(WIDTH, 9));
    } else if (state.getPortValue(PORT_INDEX_R0_1) == Value.TRUE
        && state.getPortValue(PORT_INDEX_R0_2) == Value.TRUE) {
      data.setValue(Value.createKnown(WIDTH, 0));
    } else {
      if (clockA) values[0] = values[0].not();
      if (clockB) {
        final var fiveCount = Value.create(new Value[] {values[1], values[2], values[3]});
        if (fiveCount.isFullyDefined()) {
          // 0 to 4, then back to 0; the unused codes 5 to 7 also return to 0.
          final var next = fiveCount.toLongValue() >= 4 ? 0 : fiveCount.toLongValue() + 1;
          for (var i = 0; i < 3; i++) values[i + 1] = (next >> i & 1) == 1 ? Value.TRUE : Value.FALSE;
        } else {
          for (var i = 1; i < 4; i++) values[i] = Value.UNKNOWN;
        }
      }
      data.setValue(Value.create(values));
    }

    state.setPort(PORT_INDEX_QA, data.getValue().get(0), 4);
    state.setPort(PORT_INDEX_QB, data.getValue().get(1), 4);
    state.setPort(PORT_INDEX_QC, data.getValue().get(2), 4);
    state.setPort(PORT_INDEX_QD, data.getValue().get(3), 4);
  }

  @Override
  public boolean checkForGatedClocks(netlistComponent comp) {
    return true;
  }

  @Override
  public int[] clockPinIndex(netlistComponent comp) {
    return new int[] {PORT_INDEX_CKB, PORT_INDEX_CKA};
  }
}

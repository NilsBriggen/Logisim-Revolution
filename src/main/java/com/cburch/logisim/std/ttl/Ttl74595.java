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
import com.cburch.logisim.fpga.designrulecheck.netlistComponent;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import java.util.Arrays;

/**
 * TTL 74595: 8-bit serial-in shift register with an output storage register and three-state
 * outputs. SRCLK shifts SER into QA's stage on its rising edge, nSRCLR clears the shift register
 * asynchronously, RCLK copies the shift register to the storage register on its rising edge, and
 * nOE releases QA to QH. QH' is the last shift-register stage, for cascading.
 *
 * <p>With SRCLK and RCLK tied together the storage register is one clock behind the shift
 * register, as the data sheet describes. Model based on the Texas Instruments SN74HC595 data sheet.
 */
public class Ttl74595 extends AbstractTtlGate {
  /**
   * Unique identifier of the tool, used as reference in project files. Do NOT change as it will
   * prevent project files from loading.
   *
   * <p>Identifier value must MUST be unique string among all tools.
   */
  public static final String _ID = "74595";

  // Logical ports: QB..QH (pins 1-7), QH' (9), nSRCLR, SRCLK, RCLK, nOE, SER (10-14), QA (15).
  public static final int PORT_QH_SERIAL = 7;
  public static final int PORT_NSRCLR = 8;
  public static final int PORT_SRCLK = 9;
  public static final int PORT_RCLK = 10;
  public static final int PORT_NOE = 11;
  public static final int PORT_SER = 12;
  public static final int PORT_QA = 13;

  /** Logical ports of QA to QH. */
  static final int[] OUTPUTS = {PORT_QA, 0, 1, 2, 3, 4, 5, 6};

  private static final int DELAY = 2;

  private static final class State extends ClockState implements InstanceData {
    Value[] shift = new Value[8];
    Value[] storage = new Value[8];

    State() {
      Arrays.fill(shift, Value.FALSE);
      Arrays.fill(storage, Value.FALSE);
    }

    @Override
    public State clone() {
      final var copy = (State) super.clone();
      copy.shift = shift.clone();
      copy.storage = storage.clone();
      return copy;
    }
  }

  public Ttl74595() {
    super(
        _ID,
        (byte) 16,
        new byte[] {1, 2, 3, 4, 5, 6, 7, 9, 15},
        new String[] {
          "QB", "QC", "QD", "QE", "QF", "QG", "QH", "QH' (Serial out)",
          "nSRCLR (Shift register clear, active LOW)", "SRCLK (Shift clock)",
          "RCLK (Storage register clock)", "nOE (Output enable, active LOW)", "SER (Serial in)", "QA"
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
          "QB", "QC", "QD", "QE", "QF", "QG", "QH", null,
          "QH'", "CLR", "SCK", "RCK", "OE", "SER", "QA", null
        });
  }

  @Override
  public void propagateTtl(InstanceState state) {
    var data = (State) state.getData();
    if (data == null) {
      data = new State();
      state.setData(data);
    }
    final var shiftEdge = data.updateClock(state.getPortValue(PORT_SRCLK), 0);
    final var storeEdge = data.updateClock(state.getPortValue(PORT_RCLK), 1);
    // Store first, so a shared clock stores the value from before the shift.
    if (storeEdge) data.storage = data.shift.clone();
    final var clear = state.getPortValue(PORT_NSRCLR);
    if (clear == Value.FALSE) {
      Arrays.fill(data.shift, Value.FALSE);
    } else if (shiftEdge) {
      System.arraycopy(data.shift, 0, data.shift, 1, 7);
      data.shift[0] = clear == Value.TRUE ? state.getPortValue(PORT_SER) : Value.ERROR;
    }

    final var oe = state.getPortValue(PORT_NOE);
    for (var i = 0; i < 8; i++) {
      final var q =
          oe == Value.FALSE ? data.storage[i] : oe == Value.TRUE ? Value.UNKNOWN : Value.ERROR;
      state.setPort(OUTPUTS[i], q, DELAY);
    }
    state.setPort(PORT_QH_SERIAL, data.shift[7], DELAY);
  }

  @Override
  public boolean checkForGatedClocks(netlistComponent comp) {
    return true;
  }

  @Override
  public int[] clockPinIndex(netlistComponent comp) {
    return new int[] {PORT_SRCLK, PORT_RCLK};
  }
}

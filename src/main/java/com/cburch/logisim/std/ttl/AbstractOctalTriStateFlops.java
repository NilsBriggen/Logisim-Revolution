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

/**
 * Eight rising-edge D flip-flops with a common clock and three-state outputs (74374, 74574). The
 * flip-flops keep clocking while the outputs are released by a HIGH nOE.
 */
abstract class AbstractOctalTriStateFlops extends AbstractTtlGate {
  static final int PORT_NOE = 0;
  static final int PORT_CLK = 9;
  private static final int DELAY = 2;

  private final int[] dataPorts;
  private final int[] outputPorts;
  private final String[] pinNames;

  /**
   * @param outputPins the physical pins of Q1 to Q8
   * @param portNames tooltips in logical port order
   * @param dataPorts the logical ports of D1 to D8
   * @param outputPortIndexes the logical ports of Q1 to Q8
   * @param pinNames short names by physical pin, for the internal-structure view
   */
  AbstractOctalTriStateFlops(
      String id,
      byte[] outputPins,
      String[] portNames,
      int[] dataPorts,
      int[] outputPortIndexes,
      String[] pinNames) {
    super(id, (byte) 20, outputPins, portNames, null);
    this.dataPorts = dataPorts;
    this.outputPorts = outputPortIndexes;
    this.pinNames = pinNames;
  }

  @Override
  public void paintInternal(InstancePainter painter, int x, int y, int height, boolean up) {
    super.paintBase(painter, false, false);
    Drawgates.paintPortNamesByPin(painter, x, y, height, pinNames);
  }

  @Override
  public void propagateTtl(InstanceState state) {
    var data = (TtlRegisterData) state.getData();
    if (data == null) {
      data = new TtlRegisterData(BitWidth.create(8));
      state.setData(data);
    }
    if (data.updateClock(state.getPortValue(PORT_CLK))) {
      final var values = new Value[8];
      for (var i = 0; i < 8; i++) values[i] = state.getPortValue(dataPorts[i]);
      data.setValue(Value.create(values));
    }
    final var oe = state.getPortValue(PORT_NOE);
    for (var i = 0; i < 8; i++) {
      final var q =
          oe == Value.FALSE
              ? data.getValue().get(i)
              : oe == Value.TRUE ? Value.UNKNOWN : Value.ERROR;
      state.setPort(outputPorts[i], q, DELAY);
    }
  }

  @Override
  public boolean checkForGatedClocks(netlistComponent comp) {
    return true;
  }

  @Override
  public int[] clockPinIndex(netlistComponent comp) {
    return new int[] {PORT_CLK};
  }
}

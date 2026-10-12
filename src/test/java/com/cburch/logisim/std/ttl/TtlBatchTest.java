/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.ttl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import org.junit.jupiter.api.Test;

/** The 7490, 74148, 74154, 74280, 74374, 74574 and 74595. */
class TtlBatchTest {

  private static Value bit(boolean b) {
    return b ? Value.TRUE : Value.FALSE;
  }

  private static Instance instance(InstanceFactory factory, boolean power) {
    final var attrs = factory.createAttributeSet();
    attrs.setValue(TtlLibrary.VCC_GND, power);
    return Instance.getInstanceFor(factory.createComponent(Location.create(0, 0, false), attrs));
  }

  private static void assertPortCounts(InstanceFactory factory, int ports, int outputs) {
    final var hidden = instance(factory, false);
    assertEquals(ports, hidden.getPorts().size());
    assertEquals(
        outputs,
        hidden.getPorts().stream().filter(p -> p.getType() == EndData.OUTPUT_ONLY).count());
    assertEquals(ports + 2, instance(factory, true).getPorts().size());
  }

  private static void pulse(InstanceFactory gate, TtlTestState state, int port, boolean rising) {
    state.setPortValue(port, bit(!rising));
    gate.propagate(state);
    state.setPortValue(port, bit(rising));
    gate.propagate(state);
    state.setPortValue(port, bit(!rising));
    gate.propagate(state);
  }

  @Test
  void portLayouts() {
    assertPortCounts(new Ttl7490(), 10, 4);
    assertPortCounts(new Ttl74148(), 14, 5);
    assertPortCounts(new Ttl74154(), 22, 16);
    assertPortCounts(new Ttl74280(), 11, 2);
    assertPortCounts(new Ttl74374(), 18, 8);
    assertPortCounts(new Ttl74574(), 18, 8);
    assertPortCounts(new Ttl74595(), 14, 9);
    // 7490 power is on pins 5 (VCC) and 10 (GND), as on the 7493.
    final var counter = instance(new Ttl7490(), true);
    assertEquals(Location.create(90, -30, false), counter.getPortLocation(10)); // GND, pin 10
    assertEquals(Location.create(90, 30, false), counter.getPortLocation(11)); // VCC, pin 5
  }

  private static int count7490(TtlTestState s) {
    return (int)
        (s.getPortValue(Ttl7490.PORT_INDEX_QA).toLongValue()
            | s.getPortValue(Ttl7490.PORT_INDEX_QB).toLongValue() << 1
            | s.getPortValue(Ttl7490.PORT_INDEX_QC).toLongValue() << 2
            | s.getPortValue(Ttl7490.PORT_INDEX_QD).toLongValue() << 3);
  }

  @Test
  void ttl7490CountsBcdAndSetsNine() {
    final var gate = new Ttl7490();
    final var s = new TtlTestState(gate, false);
    for (final var p : new int[] {Ttl7490.PORT_INDEX_R9_1, Ttl7490.PORT_INDEX_R9_2}) {
      s.setPortValue(p, Value.FALSE);
    }
    s.setPortValue(Ttl7490.PORT_INDEX_R0_1, Value.TRUE);
    s.setPortValue(Ttl7490.PORT_INDEX_R0_2, Value.TRUE);
    s.setPortValue(Ttl7490.PORT_INDEX_CKA, Value.FALSE);
    s.setPortValue(Ttl7490.PORT_INDEX_CKB, Value.FALSE);
    gate.propagate(s);
    assertEquals(0, count7490(s));
    s.setPortValue(Ttl7490.PORT_INDEX_R0_1, Value.FALSE);

    // QA drives CKB: falling edges on CKA count 0..9 and wrap.
    for (var expected = 1; expected <= 12; expected++) {
      final var qaBefore = s.getPortValue(Ttl7490.PORT_INDEX_QA);
      s.setPortValue(Ttl7490.PORT_INDEX_CKA, Value.TRUE);
      gate.propagate(s);
      s.setPortValue(Ttl7490.PORT_INDEX_CKA, Value.FALSE);
      gate.propagate(s);
      s.setPortValue(Ttl7490.PORT_INDEX_CKB, qaBefore);
      gate.propagate(s);
      s.setPortValue(Ttl7490.PORT_INDEX_CKB, s.getPortValue(Ttl7490.PORT_INDEX_QA));
      gate.propagate(s);
      assertEquals(expected % 10, count7490(s), "after " + expected + " pulses");
    }

    // R9 sets 9 even while R0 resets.
    s.setPortValue(Ttl7490.PORT_INDEX_R0_1, Value.TRUE);
    s.setPortValue(Ttl7490.PORT_INDEX_R9_1, Value.TRUE);
    s.setPortValue(Ttl7490.PORT_INDEX_R9_2, Value.TRUE);
    gate.propagate(s);
    assertEquals(9, count7490(s));
  }

  @Test
  void ttl74148EncodesHighestActiveInput() {
    final var gate = new Ttl74148();
    final var s = new TtlTestState(gate, false);
    final int[] inputs = {
      Ttl74148.PORT_I0, Ttl74148.PORT_I1, Ttl74148.PORT_I2, Ttl74148.PORT_I3,
      Ttl74148.PORT_I4, Ttl74148.PORT_I5, Ttl74148.PORT_I6, Ttl74148.PORT_I7
    };
    for (final var p : inputs) s.setPortValue(p, Value.TRUE);
    s.setPortValue(Ttl74148.PORT_EI, Value.FALSE);
    gate.propagate(s);
    assertEquals(Value.TRUE, s.getPortValue(Ttl74148.PORT_GS));
    assertEquals(Value.FALSE, s.getPortValue(Ttl74148.PORT_EO));

    s.setPortValue(Ttl74148.PORT_I2, Value.FALSE);
    s.setPortValue(Ttl74148.PORT_I5, Value.FALSE);
    gate.propagate(s);
    // 5 = 101, outputs inverted: A2 A1 A0 = L H L.
    assertEquals(Value.FALSE, s.getPortValue(Ttl74148.PORT_A2));
    assertEquals(Value.TRUE, s.getPortValue(Ttl74148.PORT_A1));
    assertEquals(Value.FALSE, s.getPortValue(Ttl74148.PORT_A0));
    assertEquals(Value.FALSE, s.getPortValue(Ttl74148.PORT_GS));
    assertEquals(Value.TRUE, s.getPortValue(Ttl74148.PORT_EO));

    s.setPortValue(Ttl74148.PORT_EI, Value.TRUE);
    gate.propagate(s);
    assertEquals(Value.TRUE, s.getPortValue(Ttl74148.PORT_A2));
    assertEquals(Value.TRUE, s.getPortValue(Ttl74148.PORT_EO));
  }

  @Test
  void ttl74154SelectsOneLowOutput() {
    final var gate = new Ttl74154();
    final var s = new TtlTestState(gate, false);
    s.setPortValue(Ttl74154.PORT_NG1, Value.FALSE);
    s.setPortValue(Ttl74154.PORT_NG2, Value.FALSE);
    for (var sel = 0; sel < 16; sel++) {
      s.setPortValue(Ttl74154.PORT_A, bit((sel & 1) != 0));
      s.setPortValue(Ttl74154.PORT_B, bit((sel & 2) != 0));
      s.setPortValue(Ttl74154.PORT_C, bit((sel & 4) != 0));
      s.setPortValue(Ttl74154.PORT_D, bit((sel & 8) != 0));
      gate.propagate(s);
      for (var out = 0; out < 16; out++) {
        assertEquals(bit(out != sel), s.getPortValue(out), "select " + sel + " output " + out);
      }
    }
    s.setPortValue(Ttl74154.PORT_NG2, Value.TRUE);
    gate.propagate(s);
    for (var out = 0; out < 16; out++) assertEquals(Value.TRUE, s.getPortValue(out));
    s.setPortValue(Ttl74154.PORT_NG2, Value.FALSE);
    s.setPortValue(Ttl74154.PORT_C, Value.UNKNOWN);
    gate.propagate(s);
    assertEquals(Value.UNKNOWN, s.getPortValue(0));
  }

  @Test
  void ttl74280ReportsParity() {
    final var gate = new Ttl74280();
    final var s = new TtlTestState(gate, false);
    final int[] inputs = {
      Ttl74280.PORT_A, Ttl74280.PORT_B, Ttl74280.PORT_C, Ttl74280.PORT_D, Ttl74280.PORT_E,
      Ttl74280.PORT_F, Ttl74280.PORT_G, Ttl74280.PORT_H, Ttl74280.PORT_I
    };
    for (var pattern = 0; pattern < 512; pattern += 37) {
      for (var i = 0; i < 9; i++) s.setPortValue(inputs[i], bit((pattern >> i & 1) != 0));
      gate.propagate(s);
      final var odd = Integer.bitCount(pattern) % 2 == 1;
      assertEquals(bit(!odd), s.getPortValue(Ttl74280.PORT_EVEN), "pattern " + pattern);
      assertEquals(bit(odd), s.getPortValue(Ttl74280.PORT_ODD), "pattern " + pattern);
    }
  }

  @Test
  void octalFlopsLatchOnRisingEdgeAndReleaseOutputs() {
    // D1..D8 and Q1..Q8 logical ports of each part.
    checkOctalFlops(
        new Ttl74374(), new int[] {2, 3, 6, 7, 11, 12, 15, 16}, new int[] {1, 4, 5, 8, 10, 13, 14, 17});
    checkOctalFlops(
        new Ttl74574(), new int[] {1, 2, 3, 4, 5, 6, 7, 8}, new int[] {17, 16, 15, 14, 13, 12, 11, 10});
  }

  private static void checkOctalFlops(AbstractTtlGate gate, int[] d, int[] q) {
    final var s = new TtlTestState(gate, false);
    s.setPortValue(AbstractOctalTriStateFlops.PORT_NOE, Value.FALSE);
    final var pattern = 0b1011_0010;
    for (var i = 0; i < 8; i++) s.setPortValue(d[i], bit((pattern >> i & 1) != 0));
    pulse(gate, s, AbstractOctalTriStateFlops.PORT_CLK, true);
    for (var i = 0; i < 8; i++) {
      assertEquals(bit((pattern >> i & 1) != 0), s.getPortValue(q[i]), gate.getName() + " Q" + (i + 1));
    }
    // Changing D without a clock edge holds the outputs.
    for (var i = 0; i < 8; i++) s.setPortValue(d[i], Value.FALSE);
    gate.propagate(s);
    assertEquals(Value.TRUE, s.getPortValue(q[1]));
    // Released outputs float, and the flops still clock meanwhile.
    s.setPortValue(AbstractOctalTriStateFlops.PORT_NOE, Value.TRUE);
    pulse(gate, s, AbstractOctalTriStateFlops.PORT_CLK, true);
    assertEquals(Value.UNKNOWN, s.getPortValue(q[1]));
    s.setPortValue(AbstractOctalTriStateFlops.PORT_NOE, Value.FALSE);
    gate.propagate(s);
    assertEquals(Value.FALSE, s.getPortValue(q[1]));
  }

  @Test
  void ttl74595ShiftsThenStores() {
    final var gate = new Ttl74595();
    final var s = new TtlTestState(gate, false);
    s.setPortValue(Ttl74595.PORT_NOE, Value.FALSE);
    s.setPortValue(Ttl74595.PORT_NSRCLR, Value.TRUE);
    s.setPortValue(Ttl74595.PORT_RCLK, Value.FALSE);
    // Shift in 1, 0, 1, 1 (the last one ends up in QA).
    for (final var b : new boolean[] {true, false, true, true}) {
      s.setPortValue(Ttl74595.PORT_SER, bit(b));
      pulse(gate, s, Ttl74595.PORT_SRCLK, true);
    }
    // Nothing stored yet.
    assertEquals(Value.FALSE, s.getPortValue(Ttl74595.PORT_QA));
    pulse(gate, s, Ttl74595.PORT_RCLK, true);
    final boolean[] expected = {true, true, false, true, false, false, false, false};
    for (var i = 0; i < 8; i++) {
      assertEquals(bit(expected[i]), s.getPortValue(Ttl74595.OUTPUTS[i]), "Q" + (char) ('A' + i));
    }

    // Four more shifts push the first bit out to QH'.
    s.setPortValue(Ttl74595.PORT_SER, Value.FALSE);
    for (var i = 0; i < 4; i++) pulse(gate, s, Ttl74595.PORT_SRCLK, true);
    assertEquals(Value.TRUE, s.getPortValue(Ttl74595.PORT_QH_SERIAL));

    // Clearing empties only the shift register; nOE floats the outputs.
    s.setPortValue(Ttl74595.PORT_NSRCLR, Value.FALSE);
    gate.propagate(s);
    assertEquals(Value.FALSE, s.getPortValue(Ttl74595.PORT_QH_SERIAL));
    assertEquals(Value.TRUE, s.getPortValue(Ttl74595.PORT_QA));
    s.setPortValue(Ttl74595.PORT_NOE, Value.TRUE);
    gate.propagate(s);
    assertEquals(Value.UNKNOWN, s.getPortValue(Ttl74595.PORT_QA));
  }

  @Test
  void ttl74595SharedClockStoresTheValueBeforeTheShift() {
    final var gate = new Ttl74595();
    final var s = new TtlTestState(gate, false);
    s.setPortValue(Ttl74595.PORT_NOE, Value.FALSE);
    s.setPortValue(Ttl74595.PORT_NSRCLR, Value.TRUE);
    s.setPortValue(Ttl74595.PORT_SER, Value.TRUE);
    for (var i = 0; i < 2; i++) {
      s.setPortValue(Ttl74595.PORT_SRCLK, Value.FALSE);
      s.setPortValue(Ttl74595.PORT_RCLK, Value.FALSE);
      gate.propagate(s);
      s.setPortValue(Ttl74595.PORT_SRCLK, Value.TRUE);
      s.setPortValue(Ttl74595.PORT_RCLK, Value.TRUE);
      gate.propagate(s);
    }
    // Two shifts of 1, but storage got the shift register as it was before the second one.
    assertEquals(Value.TRUE, s.getPortValue(Ttl74595.OUTPUTS[0]));
    assertEquals(Value.FALSE, s.getPortValue(Ttl74595.OUTPUTS[1]));
  }
}

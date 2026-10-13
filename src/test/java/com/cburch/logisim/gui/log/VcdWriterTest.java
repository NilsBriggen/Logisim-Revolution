/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class VcdWriterTest {

  private static Signal signal(int idx, String name, int width, Value initial, long duration) {
    final var info = mock(SignalInfo.class);
    when(info.getWidth()).thenReturn(width);
    when(info.getDisplayName()).thenReturn(name);
    return new Signal(idx, info, initial, duration, 0, 0);
  }

  @Test
  void writesHeaderDumpvarsAndOnlyChanges() throws IOException {
    final var clk = signal(0, "clk", 1, Value.FALSE, 10);
    clk.extend(Value.TRUE, 10);
    clk.extend(Value.TRUE, 10); // no change: no line
    clk.extend(Value.FALSE, 10);
    final var bus = signal(1, "data bus", 4, Value.createKnown(BitWidth.create(4), 5), 20);
    bus.extend(Value.createKnown(BitWidth.create(4), 10), 20);

    final var out = new StringWriter();
    VcdWriter.write(List.of(clk, bus), "main circuit", 0, 40, out);
    final var vcd = out.toString();

    assertTrue(vcd.contains("$timescale 1ns $end"), vcd);
    assertTrue(vcd.contains("$scope module main_circuit $end"), vcd);
    assertTrue(vcd.contains("$var wire 1 ! clk $end"), vcd);
    assertTrue(vcd.contains("$var wire 4 \" data_bus $end"), vcd);
    final var body = vcd.substring(vcd.indexOf("$enddefinitions $end") + 21);
    assertEquals(
        """
        #0
        $dumpvars
        0!
        b0101 "
        $end
        #10
        1!
        #20
        b1010 "
        #30
        0!
        """,
        body);
  }

  @Test
  void encodesFloatingAndErrorBits() {
    final var mixed =
        Value.create(new Value[] {Value.TRUE, Value.UNKNOWN, Value.ERROR, Value.FALSE});
    assertEquals("b0xz1 #\n", VcdWriter.encode(mixed, 4, "#"));
    assertEquals("z!\n", VcdWriter.encode(Value.UNKNOWN, 1, "!"));
    assertEquals("x!\n", VcdWriter.encode(null, 1, "!"));
  }

  @Test
  void identifiersStayUniqueBeyondOneCharacter() {
    final var seen = new HashSet<String>();
    for (var i = 0; i < 94 * 95; i++) {
      final var id = VcdWriter.identifier(i);
      assertTrue(id.chars().allMatch(c -> c >= '!' && c <= '~'), id);
      assertTrue(seen.add(id), id);
    }
    assertEquals("!", VcdWriter.identifier(0));
    assertEquals("~", VcdWriter.identifier(93));
    assertEquals(2, VcdWriter.identifier(94).length());
  }
}

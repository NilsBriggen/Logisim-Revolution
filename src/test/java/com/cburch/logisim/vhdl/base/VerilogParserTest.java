/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.vhdl.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.instance.Port;
import java.util.List;
import org.junit.jupiter.api.Test;

class VerilogParserTest {

  private static VerilogParser parse(String source) throws Exception {
    final var parser = new VerilogParser(source);
    parser.parse();
    return parser;
  }

  private static String describe(List<VhdlParser.PortDescription> ports) {
    final var result = new StringBuilder();
    for (final var port : ports) {
      if (result.length() > 0) result.append(", ");
      result.append(port.getType()).append(' ').append(port.getName()).append(':');
      result.append(port.getWidth().getWidth());
    }
    return result.toString();
  }

  private static VerilogParser.IllegalVerilogContentException failure(String source) {
    return assertThrows(
        VerilogParser.IllegalVerilogContentException.class, () -> parse(source));
  }

  @Test
  void readsAnsiPortsWithDirectionsTypesAndWidths() throws Exception {
    final var parser =
        parse(
            """
            `timescale 1ns / 1ps
            // A counter.
            module counter (
                input  wire        clk,
                input  wire [7:0]  load,
                input              reset, enable,
                output reg  signed [0:3] count,
                output logic       done,
                inout  [15:8]      bus
            );
              always @(posedge clk) count <= count + 1;
            endmodule
            """);

    assertEquals("counter", parser.getName());
    assertEquals(
        "input clk:1, input load:8, input reset:1, input enable:1, output count:4, "
            + "output done:1, inout bus:8",
        describe(parser.getPorts()));
  }

  @Test
  void laterNamesInheritTheDirectionAndRangeOfTheirDeclaration() throws Exception {
    final var parser = parse("module m (input [3:0] a, b, output q);\nendmodule\n");

    assertEquals("input a:4, input b:4, output q:1", describe(parser.getPorts()));
  }

  @Test
  void acceptsModulesWithoutPorts() throws Exception {
    assertEquals(0, parse("module empty;\nendmodule").getPorts().size());
    assertEquals(0, parse("module empty();\nendmodule").getPorts().size());
  }

  @Test
  void commentsAndStringsDoNotEndTheModule() throws Exception {
    final var parser =
        parse(
            """
            /* module fake (input x); */
            module real_one (input a /* , input b */, output q); // endmodule
              initial $display("endmodule module");
              /* endmodule
                 module other; */
              assign q = a;
            endmodule
            """);

    assertEquals("real_one", parser.getName());
    assertEquals("input a:1, output q:1", describe(parser.getPorts()));
  }

  @Test
  void theNameOffsetsPointAtTheModuleName() throws Exception {
    final var source = "// header\nmodule  adder (input a);\nendmodule\n";
    final var parser = parse(source);

    assertEquals("adder", source.substring(parser.getNameStart(), parser.getNameEnd()));
  }

  @Test
  void refusesNonAnsiHeadersOnTheirLine() {
    final var error =
        failure("module old_style (a, b);\n  input a;\n  output b;\nendmodule\n");

    assertEquals(1, error.getLine());
    assertTrue(error.getMessage().startsWith("Line 1:"), error.getMessage());
    assertTrue(error.getMessage().contains("'a'"), error.getMessage());
    assertTrue(error.getMessage().contains("ANSI"), error.getMessage());
  }

  @Test
  void refusesParameterPortLists() {
    final var error = failure("module p\n  #(parameter W = 8)\n  (input [W-1:0] a);\nendmodule");

    assertEquals(2, error.getLine());
    assertTrue(error.getMessage().contains("#("), error.getMessage());
  }

  @Test
  void refusesRangesThatAreNotIntegerConstants() {
    final var error = failure("module p (\n  input a,\n  input [W-1:0] b\n);\nendmodule");

    assertEquals(3, error.getLine());
    assertTrue(error.getMessage().contains("[7:0]"), error.getMessage());
  }

  @Test
  void refusesMissingEndmodule() {
    final var error = failure("module m (input a);\n  assign b = a;\n");

    assertTrue(error.getMessage().contains("endmodule"), error.getMessage());
    assertEquals(3, error.getLine());
  }

  @Test
  void refusesASecondModule() {
    final var error =
        failure("module a (input x);\nendmodule\n\nmodule b (input y);\nendmodule\n");

    assertEquals(4, error.getLine());
  }

  @Test
  void refusesDuplicatePortsArraysAndDefaults() {
    assertEquals(2, failure("module m (input a,\n input a);\nendmodule").getLine());
    assertTrue(
        failure("module m (input [7:0] mem [0:3]);\nendmodule").getMessage().contains("mem"));
    assertTrue(
        failure("module m (output reg q = 1'b0);\nendmodule").getMessage().contains("default"));
  }

  @Test
  void refusesPortsWiderThanLogisimBuses() {
    final var error = failure("module m (input [64:0] wide);\nendmodule");

    assertTrue(error.getMessage().contains("65"), error.getMessage());
  }

  @Test
  void refusesEmptyTextMissingModulesAndUnclosedComments() {
    assertTrue(failure("   \n").getMessage().contains("empty"));
    assertEquals(2, failure("// nothing\nentity e is end;").getLine());
    assertEquals(1, failure("/* never closed\nmodule m; endmodule").getLine());
  }

  @Test
  void refusesUnsupportedPortTypes() {
    final var error = failure("module m (input integer count);\nendmodule");

    assertTrue(error.getMessage().contains("'integer'"), error.getMessage());
  }

  @Test
  void keepsInputsOutputsAndInoutsAsLogisimPortTypes() throws Exception {
    final var ports = parse("module m (inout io, input i, output o);\nendmodule").getPorts();

    assertEquals(Port.INOUT, ports.get(0).getType());
    assertEquals(Port.INPUT, ports.get(1).getType());
    assertEquals(Port.OUTPUT, ports.get(2).getType());
  }
}

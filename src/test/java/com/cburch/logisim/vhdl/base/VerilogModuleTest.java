/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.vhdl.base;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.fpga.designrulecheck.Netlist;
import com.cburch.logisim.fpga.hdlgenerator.HdlGeneratorFactory;
import com.cburch.logisim.instance.InstanceComponent;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;
import java.util.ArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class VerilogModuleTest {
  private static final String ADDER =
      """
      `timescale 1ns / 1ps
      module Adder (
          input  wire [3:0] a,
          output wire [4:0] sum,
          input  wire [3:0] b,
          inout  wire       bus
      );
        assign sum = a + b;
      endmodule
      """;

  private final String originalHdlType = AppPreferences.HdlType.get();

  @AfterEach
  void restoreHdlType() {
    AppPreferences.HdlType.set(originalHdlType);
  }

  private static VerilogContent adder(LogisimFile file) {
    final var content = VerilogContent.parse("Adder", ADDER, file);
    assertTrue(content.isValid());
    return content;
  }

  private static InstanceComponent place(VhdlEntity factory, Direction facing) {
    final var attrs = factory.createAttributeSet();
    attrs.setValue(StdAttr.FACING, facing);
    attrs.setValue(StdAttr.LABEL, "add1");
    return (InstanceComponent) factory.createComponent(Location.create(200, 200, true), attrs);
  }

  @Test
  void contentReadsTheHeaderAndMakesAVerilogFactory() {
    final var content = adder(null);

    assertTrue(content.isVerilog());
    assertEquals("Adder", content.getName());
    assertEquals(4, content.getPorts().size());
    assertTrue(content.createFactory() instanceof VerilogModule);
    assertSame(VerilogContent.NAME_ATTR, content.getNameAttribute());
    assertEquals(
        VerilogContent.NAME_ATTR, content.getStaticAttributes().getAttributes().get(0));
  }

  @Test
  void placedInstancesKeepDeclarationOrderAndInoutTypeWhateverTheFacing() {
    final var factory = new VerilogModule(adder(null));
    for (final var facing : Direction.cardinals) {
      final var ports = place(factory, facing).getInstance().getPorts();

      assertEquals(4, ports.size(), facing.toString());
      assertEquals("a", ports.get(0).getToolTip().toString());
      assertEquals(Port.INPUT, typeOf(ports.get(0)));
      assertEquals(BitWidth.create(4), ports.get(0).getFixedBitWidth());
      assertEquals("sum", ports.get(1).getToolTip().toString(), facing.toString());
      assertEquals(Port.OUTPUT, typeOf(ports.get(1)));
      assertEquals(BitWidth.create(5), ports.get(1).getFixedBitWidth());
      assertEquals("b", ports.get(2).getToolTip().toString());
      assertEquals("bus", ports.get(3).getToolTip().toString());
      assertEquals(Port.INOUT, typeOf(ports.get(3)));
    }
  }

  private static String typeOf(Port port) {
    return switch (port.getType()) {
      case com.cburch.logisim.comp.EndData.INPUT_ONLY -> Port.INPUT;
      case com.cburch.logisim.comp.EndData.OUTPUT_ONLY -> Port.OUTPUT;
      default -> Port.INOUT;
    };
  }

  @Test
  void instancesHaveNoExternalSimulationName() {
    final var attrs = new VerilogModule(adder(null)).createAttributeSet();

    assertFalse(attrs.containsAttribute(VhdlSimConstants.SIM_NAME_ATTR));
    assertTrue(attrs.containsAttribute(VerilogContent.NAME_ATTR));
    assertEquals("Adder", attrs.getValue(VerilogContent.NAME_ATTR));
  }

  @Test
  void outputsAreUnknownAndNothingIsSentToTheSimulator() {
    final var factory = new VerilogModule(adder(null));
    final var comp = place(factory, Direction.EAST);
    final var instance = comp.getInstance();
    final var project = new Project(LogisimFile.createNew(new Loader(null), null));
    final var state = mock(InstanceState.class);
    when(state.getProject()).thenReturn(project);
    when(state.getInstance()).thenReturn(instance);
    when(state.getAttributeSet()).thenReturn(comp.getAttributeSet());
    when(state.getPortIndex(any(Port.class)))
        .thenAnswer(inv -> instance.getPorts().indexOf(inv.getArgument(0)));

    assertDoesNotThrow(() -> factory.propagate(state));

    verify(state).setPort(1, Value.createUnknown(BitWidth.create(5)), 1);
    verify(state, never()).setPort(eq(0), any(Value.class), anyInt());
    verify(state, never()).setPort(eq(3), any(Value.class), anyInt());
    assertFalse(factory.isSimulatedExternally(project));
  }

  @Test
  void renamingRewritesTheModuleHeaderOnly() {
    final var content = adder(null);

    assertTrue(content.setName("Summer"));

    assertEquals("Summer", content.getName());
    assertTrue(content.getContent().contains("module Summer ("), content.getContent());
    assertTrue(content.getContent().contains("assign sum = a + b;"));
    assertTrue(content.getSourceNamed("summer").contains("module summer ("));
  }

  @Test
  void editedHeadersUpdatePlacedInstances() {
    final var content = adder(null);
    final var comp = place(new VerilogModule(content), Direction.EAST);

    assertTrue(content.setContent("module Adder (input x, output [1:0] y);\nendmodule\n"));

    final var ports = comp.getInstance().getPorts();
    assertEquals(2, ports.size());
    assertEquals(BitWidth.create(2), ports.get(1).getFixedBitWidth());
  }

  @Test
  void namesFollowTheIdentifierAndKeywordRules() {
    assertNull(VerilogContent.nameProblem("counter_8", null));
    assertNotNull(VerilogContent.nameProblem("8bit", null));
    assertNotNull(VerilogContent.nameProblem("a__b", null));
    assertNotNull(VerilogContent.nameProblem("always", null));
    assertNotNull(VerilogContent.nameProblem("entity", null));
    final var file = LogisimFile.createNew(new Loader(null), null);
    assertNotNull(VerilogContent.nameProblem(file.getMainCircuit().getName(), file));
  }

  @Test
  void generatesTheModuleVerbatimUnderItsHdlName() {
    AppPreferences.HdlType.set(HdlGeneratorFactory.VERILOG);
    final var content = adder(null);
    final var factory = new VerilogModule(content);
    final var attrs = factory.createAttributeSet();
    final var generator = factory.getHDLGenerator(attrs);
    final var netlist = mock(Netlist.class);
    when(netlist.projName()).thenReturn("TestProject");

    assertTrue(generator.isHdlSupportedTarget(attrs));
    final var name = factory.getHDLName(attrs);
    assertEquals("adder", name);
    final var source = String.join("\n", generator.getArchitecture(netlist, attrs, name));

    assertTrue(source.contains("`timescale 1ns / 1ps"), source);
    assertTrue(source.contains("module adder ("), source);
    assertTrue(source.contains("    output wire [4:0] sum,"), source);
    assertTrue(source.contains("  assign sum = a + b;\nendmodule"), source);
    assertTrue(generator.getEntity(netlist, attrs, name).isEmpty());
  }

  @Test
  void vhdlGenerationRefusesVerilogModulesInTheDesignRuleCheck() {
    final var file = LogisimFile.createNew(new Loader(null), null);
    final var project = new Project(file);
    final var circuit = file.getMainCircuit();
    circuit.setProject(project);
    project.setCurrentCircuit(circuit);
    final var content = adder(file);
    file.addVhdlContent(content);
    final Component comp = place((VhdlEntity) file.getAddTool(content).getFactory(), Direction.EAST);
    final var mutation = new CircuitMutation(circuit);
    mutation.add(comp);
    mutation.execute();

    AppPreferences.HdlType.set(HdlGeneratorFactory.VHDL);
    assertFalse(comp.getFactory().isHDLSupportedComponent(comp.getAttributeSet()));
    final var vhdlResult = circuit.getNetList().designRuleCheckResult(true, new ArrayList<>());
    assertTrue((vhdlResult & Netlist.DRC_ERROR) != 0);

    AppPreferences.HdlType.set(HdlGeneratorFactory.VERILOG);
    assertTrue(comp.getFactory().isHDLSupportedComponent(comp.getAttributeSet()));
  }
}

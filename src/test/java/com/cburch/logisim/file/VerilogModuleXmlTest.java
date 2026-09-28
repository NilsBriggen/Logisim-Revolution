/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.vhdl.base.VerilogContent;
import com.cburch.logisim.vhdl.base.VerilogModule;
import com.cburch.logisim.vhdl.base.VhdlContent;
import com.cburch.logisim.vhdl.base.VhdlEntity;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.xml.sax.InputSource;

class VerilogModuleXmlTest {
  private static final String MODULE =
      """
      // Adds two nibbles.
      module adder4 (
          input  wire [3:0] a,
          input  wire [3:0] b,
          output wire [4:0] sum
      );
        assign sum = a + b; // <&> survive XML escaping
      endmodule
      """;

  private static final String ENTITY =
      """
      library ieee;
      use ieee.std_logic_1164.all;

      entity invert is
        port ( a : in std_logic; q : out std_logic );
      end invert;

      architecture rtl of invert is
      begin
        q <= not a;
      end rtl;
      """;

  private static byte[] save(LogisimFile file, Loader loader) throws Exception {
    final var output = new ByteArrayOutputStream();
    file.write(output, loader);
    return output.toByteArray();
  }

  @Test
  void verilogModuleAndItsInstanceRoundTrip() throws Exception {
    final var loader = new Loader(null);
    final var file = LogisimFile.createNew(loader, null);
    final var content = VerilogContent.parse("adder4", MODULE, file);
    content.setAppearance(StdAttr.APPEAR_CLASSIC);
    file.addVhdlContent(content);
    final var factory = (VhdlEntity) file.getAddTool(content).getFactory();
    final var attrs = factory.createAttributeSet();
    attrs.setValue(StdAttr.LABEL, "sum1");
    final var mutation = new CircuitMutation(file.getMainCircuit());
    mutation.add(factory.createComponent(Location.create(200, 200, true), attrs));
    mutation.execute();

    final var bytes = save(file, loader);
    final var xml = new String(bytes, StandardCharsets.UTF_8);
    assertTrue(xml.contains("language=\"verilog\""), xml);

    final var loaded = LogisimFile.load(new ByteArrayInputStream(bytes), new Loader(null));
    final var loadedContent = loaded.getVhdlContent("adder4");
    assertTrue(loadedContent.isVerilog());
    assertTrue(loadedContent.isValid());
    assertEquals(MODULE, loadedContent.getContent());
    assertEquals(3, loadedContent.getPorts().size());
    assertEquals(StdAttr.APPEAR_CLASSIC, loadedContent.getAppearance());

    final var placed = loaded.getMainCircuit().getNonWires().iterator().next();
    assertTrue(placed.getFactory() instanceof VerilogModule);
    assertEquals("sum1", placed.getAttributeSet().getValue(StdAttr.LABEL));
    assertEquals(3, placed.getEnds().size());

    // Saving again produces the same file.
    assertEquals(xml, new String(save(loaded, new Loader(null)), StandardCharsets.UTF_8));
  }

  @Test
  void vhdlEntitiesAreSavedWithoutALanguageAttribute() throws Exception {
    final var loader = new Loader(null);
    final var file = LogisimFile.createNew(loader, null);
    file.addVhdlContent(VhdlContent.parse("invert", ENTITY, file));

    final var bytes = save(file, loader);
    final var xml = new String(bytes, StandardCharsets.UTF_8);
    assertFalse(xml.contains("language="), xml);

    final var loaded = LogisimFile.load(new ByteArrayInputStream(bytes), new Loader(null));
    assertFalse(loaded.getVhdlContent("invert").isVerilog());
    assertTrue(loaded.getVhdlContent("invert").isValid());
  }

  @Test
  void moduleThatLostItsLanguageAttributeIsStillRecognised() throws Exception {
    // A version without Verilog support writes the module back without the attribute.
    assertTrue(XmlReader.isVerilogElement(vhdlElement(null, MODULE)));
    assertFalse(XmlReader.isVerilogElement(vhdlElement(null, ENTITY)));
    assertTrue(XmlReader.isVerilogElement(vhdlElement("verilog", "anything")));
    assertFalse(XmlReader.isVerilogElement(vhdlElement("vhdl", MODULE)));
  }

  private static org.w3c.dom.Element vhdlElement(String language, String text) throws Exception {
    final var doc =
        DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(new InputSource(new java.io.StringReader("<vhdl name=\"x\"/>")));
    final var elt = doc.getDocumentElement();
    if (language != null) elt.setAttribute("language", language);
    elt.setTextContent(text);
    return elt;
  }
}

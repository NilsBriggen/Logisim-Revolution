/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.vhdl.base;

import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.fpga.designrulecheck.Netlist;
import com.cburch.logisim.fpga.file.FileWriter;
import com.cburch.logisim.fpga.hdlgenerator.AbstractHdlGeneratorFactory;
import com.cburch.logisim.fpga.hdlgenerator.Hdl;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates a Verilog module component: the user's module is written out verbatim (renamed to the
 * HDL name every instance refers to), and instances connect to its ports by name. There is no VHDL
 * form, so the design rule check refuses the component when VHDL is the selected language.
 */
public class VerilogHdlGeneratorFactory extends AbstractHdlGeneratorFactory {

  public VerilogHdlGeneratorFactory() {
    super(VhdlHdlGeneratorFactory.HDL_DIRECTORY);
    getWiresPortsDuringHDLWriting = true;
  }

  @Override
  public void getGenerationTimeWiresPorts(Netlist theNetlist, AttributeSet attrs) {
    final var content = ((VhdlEntityAttributes) attrs).getContent();
    var i = 0;
    for (final var port : content.getPorts()) {
      myPorts.add(port.getType(), port.getName(), port.getWidth().getWidth(), i++);
    }
  }

  @Override
  public List<String> getArchitecture(
      Netlist theNetlist, AttributeSet attrs, String componentName) {
    final var contents =
        new ArrayList<>(FileWriter.getGenerateRemark(componentName, theNetlist.projName()));
    final var content = (VerilogContent) ((VhdlEntityAttributes) attrs).getContent();
    contents.addAll(content.getSourceNamed(componentName).lines().toList());
    return contents;
  }

  @Override
  public boolean isHdlSupportedTarget(AttributeSet attrs) {
    return Hdl.isVerilog();
  }
}

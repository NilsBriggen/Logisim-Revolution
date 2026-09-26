/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.vhdl.base;

import static com.cburch.logisim.vhdl.Strings.S;

import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.gui.icons.ArithmeticIcon;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.StringGetter;

/**
 * A component described by a Verilog module of the project.
 *
 * <p>Like every project-defined HDL component it is addressed by its own name in the saved file.
 * Logisim cannot simulate Verilog, and the external VHDL simulator (QuestaSim) is only given the VHDL
 * entities: inside Logisim the outputs are always unknown and the component carries the fallback
 * badge. Verilog modules are for FPGA/HDL generation with Verilog as the selected language.
 */
public class VerilogModule extends VhdlEntity {
  static final ArithmeticIcon VERILOG_ICON = new ArithmeticIcon("V", 1);

  public VerilogModule(VerilogContent content) {
    super(content, new VerilogHdlGeneratorFactory(), VERILOG_ICON);
  }

  @Override
  public VerilogContent getContent() {
    return (VerilogContent) super.getContent();
  }

  @Override
  public String getName() {
    final var content = getContent();
    return content == null ? "Verilog Module" : content.getName();
  }

  @Override
  public StringGetter getDisplayGetter() {
    return getContent() == null ? S.getter("verilogComponent") : super.getDisplayGetter();
  }

  /** Never simulated: QuestaSim is only given the VHDL entities. */
  @Override
  protected boolean isSimulatedExternally(Project project) {
    return false;
  }

  /** There is no simulation source to save: nothing simulates the module. */
  @Override
  public void saveFile(AttributeSet attrs) {
    // Verilog modules are not simulated externally.
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.memory;

import static com.cburch.logisim.std.Strings.S;

import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.fpga.designrulecheck.Netlist;
import com.cburch.logisim.fpga.designrulecheck.SimpleDrcContainer;
import com.cburch.logisim.fpga.designrulecheck.netlistComponent;
import com.cburch.logisim.fpga.gui.Reporter;
import com.cburch.logisim.fpga.hdlgenerator.Hdl;
import com.cburch.logisim.fpga.hdlgenerator.InlinedHdlGeneratorFactory;
import com.cburch.logisim.fpga.hdlgenerator.WithSelectHdlGenerator;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.util.LineBuffer;

public class RomHdlGeneratorFactory extends InlinedHdlGeneratorFactory {

  @Override
  public LineBuffer getInlinedCode(
      Netlist nets, Long componentId, netlistComponent componentInfo, String circuitName) {
    AttributeSet attrs = componentInfo.getComponent().getAttributeSet();
    final var addressWidth = attrs.getValue(Mem.ADDR_ATTR).getWidth();
    final var dataWidth = attrs.getValue(Mem.DATA_ATTR).getWidth();
    final var romContents = attrs.getValue(Rom.CONTENTS_ATTR);
    final var addrIndex = RamAppearance.getAddrIndex(0, attrs);
    final var dataIndex = RamAppearance.getDataOutIndex(0, attrs);
    // Nothing reads the data output, so there is nothing to generate.
    if (!componentInfo.isEndConnected(dataIndex)) return LineBuffer.getBuffer();
    // A bus name is null when the bus is unconnected or split over several nets. Report it on the
    // component instead of crashing the whole generation with a NullPointerException.
    final var addrBus = Hdl.getBusName(componentInfo, addrIndex, nets);
    final var dataBus = Hdl.getBusName(componentInfo, dataIndex, nets);
    if (addrBus == null || dataBus == null) {
      reportUnsupportedBus(nets, componentInfo, attrs);
      return LineBuffer.getBuffer();
    }
    final var generator =
        (new WithSelectHdlGenerator(
                componentInfo.getComponent().getAttributeSet().getValue(StdAttr.LABEL),
                addrBus,
                addressWidth,
                dataBus,
                dataWidth))
            .setDefault(0L);
    for (var addr = 0L; addr < (1L << addressWidth); addr++) {
      final var romValue = romContents.get(addr);
      if (romValue != 0L) generator.add(addr, romValue);
    }
    return LineBuffer.getBuffer().add(generator.getHdlCode());
  }

  private static void reportUnsupportedBus(
      Netlist nets, netlistComponent componentInfo, AttributeSet attrs) {
    final var label = attrs.getValue(StdAttr.LABEL);
    final var loc = componentInfo.getComponent().getLocation();
    final var name =
        (label == null || label.isEmpty()) ? S.get("romComponent") + " " + loc : label;
    final var error =
        new SimpleDrcContainer(
            nets.getCircuit(),
            S.get("romHdlBusNotConnected", name),
            SimpleDrcContainer.LEVEL_FATAL,
            SimpleDrcContainer.MARK_INSTANCE);
    error.addMarkComponent(componentInfo.getComponent());
    Reporter.report.addError(error);
  }

  @Override
  public boolean isHdlSupportedTarget(AttributeSet attrs) {
    if (attrs == null) return false;
    if (attrs.getValue(Mem.LINE_ATTR) == null) return false;
    return attrs.getValue(Mem.LINE_ATTR).equals(Mem.SINGLE);
  }
}

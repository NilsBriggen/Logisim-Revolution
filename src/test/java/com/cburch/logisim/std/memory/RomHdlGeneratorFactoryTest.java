/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.memory;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cburch.logisim.data.Location;
import com.cburch.logisim.fpga.designrulecheck.Net;
import com.cburch.logisim.fpga.designrulecheck.Netlist;
import com.cburch.logisim.fpga.designrulecheck.netlistComponent;
import com.cburch.logisim.fpga.hdlgenerator.HdlGeneratorFactory;
import com.cburch.logisim.prefs.AppPreferences;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class RomHdlGeneratorFactoryTest {
  private final String originalHdlType = AppPreferences.HdlType.get();

  @AfterEach
  void restoreHdlType() {
    AppPreferences.HdlType.set(originalHdlType);
  }

  private static Netlist netlist() {
    final var nets = mock(Netlist.class);
    when(nets.isContinuesBus(any(), anyInt())).thenCallRealMethod();
    return nets;
  }

  private static netlistComponent unconnectedRom() {
    final var factory = new Rom();
    final var rom = factory.createComponent(Location.create(0, 0, true), factory.createAttributeSet());
    return new netlistComponent(rom);
  }

  /** Wires every bit of an end, in order, to one net of exactly that width. */
  private static Net connectWholeBus(netlistComponent comp, int endIndex, Netlist nets, int id) {
    final var end = comp.getEnd(endIndex);
    final var net = mock(Net.class);
    when(net.getBitWidth()).thenReturn(end.getNrOfBits());
    when(nets.getNetId(net)).thenReturn(id);
    for (var bit = 0; bit < end.getNrOfBits(); bit++) {
      end.get((byte) bit).setParentNet(net, (byte) bit);
    }
    return net;
  }

  private static int dataIndex(netlistComponent comp) {
    return RamAppearance.getDataOutIndex(0, comp.getComponent().getAttributeSet());
  }

  private static int addrIndex(netlistComponent comp) {
    return RamAppearance.getAddrIndex(0, comp.getComponent().getAttributeSet());
  }

  @Test
  void unusedDataOutputGeneratesNothing() {
    AppPreferences.HdlType.set(HdlGeneratorFactory.VHDL);
    final var nets = netlist();
    final var code = new RomHdlGeneratorFactory().getInlinedCode(nets, 1L, unconnectedRom(), "rom");
    assertTrue(code.isEmpty());
  }

  @Test
  void unconnectedAddressIsReportedInsteadOfThrowing() {
    AppPreferences.HdlType.set(HdlGeneratorFactory.VHDL);
    final var nets = netlist();
    final var rom = unconnectedRom();
    connectWholeBus(rom, dataIndex(rom), nets, 2);

    final var code = new RomHdlGeneratorFactory().getInlinedCode(nets, 1L, rom, "rom");

    assertTrue(code.isEmpty());
  }

  @Test
  void connectedRomUsesBothBusNames() {
    AppPreferences.HdlType.set(HdlGeneratorFactory.VHDL);
    final var nets = netlist();
    final var rom = unconnectedRom();
    connectWholeBus(rom, addrIndex(rom), nets, 1);
    connectWholeBus(rom, dataIndex(rom), nets, 2);

    final var code =
        String.join("\n", new RomHdlGeneratorFactory().getInlinedCode(nets, 1L, rom, "rom").get());

    assertTrue(code.contains("s_logisimBus1"), code);
    assertTrue(code.contains("s_logisimBus2"), code);
  }
}

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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cburch.logisim.comp.EndData;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.InstanceComponent;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.hdl.VhdlEntityComponent;
import org.junit.jupiter.api.Test;

class VhdlEntityTest {
  private static String entity(String name, String ports) {
    return "library ieee;\nuse ieee.std_logic_1164.all;\n\nentity "
        + name
        + " is\n  port (\n"
        + ports
        + "\n  );\nend "
        + name
        + ";\n\narchitecture behavior of "
        + name
        + " is\nbegin\nend behavior;\n";
  }

  private static InstanceComponent place(InstanceFactory factory) {
    return (InstanceComponent)
        factory.createComponent(Location.create(100, 100, true), factory.createAttributeSet());
  }

  private static InstanceState stateFor(InstanceComponent comp) {
    final var project = new Project(LogisimFile.createNew(new Loader(null), null));
    final var instance = comp.getInstance();
    final var state = mock(InstanceState.class);
    when(state.getProject()).thenReturn(project);
    when(state.getInstance()).thenReturn(instance);
    when(state.getAttributeSet()).thenReturn(comp.getAttributeSet());
    when(state.getPortIndex(any(Port.class)))
        .thenAnswer(inv -> instance.getPorts().indexOf(inv.getArgument(0)));
    when(state.getPortValue(anyInt())).thenReturn(Value.UNKNOWN);
    return state;
  }

  private static int outputIndex(InstanceComponent comp) {
    final var ports = comp.getInstance().getPorts();
    for (var i = 0; i < ports.size(); i++) {
      if (ports.get(i).getType() == EndData.OUTPUT_ONLY) return i;
    }
    throw new AssertionError("no output port");
  }

  @Test
  void validatedContentUpdatesThePortsOfPlacedInstances() {
    final var content =
        VhdlContent.parse(
            "PortsTest", entity("PortsTest", "    a : in std_logic;\n    q : out std_logic"), null);
    final var comp = place(new VhdlEntity(content));
    assertEquals(2, comp.getInstance().getPorts().size());

    assertTrue(
        content.setContent(
            entity(
                "PortsTest",
                "    a : in std_logic;\n    b : in std_logic;\n"
                    + "    q : out std_logic_vector(3 downto 0)")));

    final var ports = comp.getInstance().getPorts();
    assertEquals(3, ports.size());
    assertTrue(ports.stream().anyMatch(p -> p.getFixedBitWidth().equals(BitWidth.create(4))));
  }

  @Test
  void entityWithoutExternalSimulatorDrivesUnknownInsteadOfStoppingTheSimulation() {
    final var content =
        VhdlContent.parse(
            "NoSimTest",
            entity("NoSimTest", "    a : in std_logic;\n    q : out std_logic_vector(3 downto 0)"),
            null);
    final var factory = new VhdlEntity(content);
    final var comp = place(factory);
    final var state = stateFor(comp);

    assertDoesNotThrow(() -> factory.propagate(state));

    final var outIndex = outputIndex(comp);
    verify(state).setPort(outIndex, Value.createUnknown(BitWidth.create(4)), 1);
    verify(state, never()).setPort(eq(1 - outIndex), any(Value.class), anyInt());
  }

  @Test
  void entityComponentWithoutExternalSimulatorDoesNotThrow() {
    final var factory = new VhdlEntityComponent();
    final var state = stateFor(place(factory));

    assertDoesNotThrow(() -> factory.propagate(state));
  }
}

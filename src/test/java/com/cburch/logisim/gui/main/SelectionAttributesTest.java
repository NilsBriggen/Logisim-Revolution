/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.wiring.Clock;
import com.cburch.logisim.std.wiring.Pin;
import java.util.LinkedHashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class SelectionAttributesTest {

  @Test
  void componentsSelectedTogetherWithTheirWiresKeepTheirSharedAttributes() {
    final var pin =
        Pin.FACTORY.createComponent(Location.create(100, 100, true),
            Pin.FACTORY.createAttributeSet());
    final var gate =
        Clock.FACTORY.createComponent(Location.create(200, 100, true),
            Clock.FACTORY.createAttributeSet());
    final var wire = Wire.create(Location.create(100, 100, true), Location.create(170, 100, true));
    final List<Component> selected = List.of(pin, wire, gate);

    final var canvas = mock(Canvas.class);
    final var selection = mock(Selection.class);
    when(selection.getComponents()).thenReturn(new LinkedHashSet<>(selected));
    final var attrs = new SelectionAttributes(canvas, selection);

    final var shown = attrs.getAttributes();
    assertTrue(shown.contains(StdAttr.FACING), "facing is shared by the pin and the clock");
    assertTrue(shown.contains(StdAttr.LABEL));
  }

  @Test
  void wiresAloneStillShowTheirOwnAttributes() {
    final var wire = Wire.create(Location.create(100, 100, true), Location.create(170, 100, true));
    final List<Component> selected = List.of(wire);
    assertEquals(selected, SelectionAttributes.attributeSources(selected));
  }
}

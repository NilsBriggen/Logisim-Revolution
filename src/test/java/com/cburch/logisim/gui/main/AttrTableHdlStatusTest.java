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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.AttributeSets;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.fpga.gui.HdlColorRenderer;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import java.util.Arrays;
import java.util.LinkedHashSet;
import org.junit.jupiter.api.Test;

class AttrTableHdlStatusTest {
  @Test
  void emptyAndWireOnlySelectionsDoNotClaimSupport() {
    assertEquals(HdlColorRenderer.UNKNOWN_STRING, model().getRow(0).getValue());
    final var wire = Wire.create(Location.create(0, 0, true), Location.create(20, 0, true));
    assertEquals(HdlColorRenderer.UNKNOWN_STRING, model(wire).getRow(0).getValue());
  }

  @Test
  void mixedFactoriesAreEvaluatedIndividuallyRegardlessOfSelectionOrder() {
    final var supported = component(mock(ComponentFactory.class), "supported", true);
    final var unsupported = component(mock(ComponentFactory.class), "unsupported", false);
    assertEquals(HdlColorRenderer.UNKNOWN_STRING,
        model(supported, unsupported).getRow(0).getValue());
    assertEquals(HdlColorRenderer.UNKNOWN_STRING,
        model(unsupported, supported).getRow(0).getValue());
  }

  @Test
  void sameFactoryUsesEachInstancesAttributesAndReflectsLaterChanges() {
    final var factory = mock(ComponentFactory.class);
    final var first = component(factory, "first", true);
    final var second = component(factory, "second", false);
    final var model = model(first, second);
    assertEquals(HdlColorRenderer.UNKNOWN_STRING, model.getRow(0).getValue());
    when(factory.isHDLSupportedComponent(second.getAttributeSet())).thenReturn(true);
    assertEquals(HdlColorRenderer.SUPPORT_STRING, model.getRow(0).getValue());
    verify(factory, never()).isHDLSupportedComponent(model.getAttributeSet());
  }

  @Test
  void uniformlyUnsupportedSelectionReportsUnsupportedRatherThanUnknown() {
    final var first = component(mock(ComponentFactory.class), "first", false);
    final var second = component(mock(ComponentFactory.class), "second", false);
    assertEquals(HdlColorRenderer.NO_SUPPORT_STRING, model(first, second).getRow(0).getValue());
  }

  private static Component component(ComponentFactory factory, String label, boolean supported) {
    final var component = mock(Component.class);
    final var attributes = attributes(label);
    when(component.getFactory()).thenReturn(factory);
    when(component.getAttributeSet()).thenReturn(attributes);
    when(factory.isHDLSupportedComponent(attributes)).thenReturn(supported);
    return component;
  }

  private static AttributeSet attributes(String label) {
    return AttributeSets.fixedSet(new Attribute<?>[] {StdAttr.LABEL}, new Object[] {label});
  }

  private static AttrTableSelectionModel model(Component... components) {
    final var frame = mock(Frame.class);
    final var canvas = mock(Canvas.class);
    final var selection = mock(Selection.class);
    when(frame.getCanvas()).thenReturn(canvas);
    when(canvas.getSelection()).thenReturn(selection);
    when(selection.getAttributeSet()).thenReturn(attributes("shared"));
    when(selection.getComponents()).thenReturn(new LinkedHashSet<>(Arrays.asList(components)));
    return new AttrTableSelectionModel(mock(Project.class), frame);
  }
}

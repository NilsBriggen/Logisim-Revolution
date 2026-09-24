/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.soc.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cburch.logisim.comp.ComponentUserEvent;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.soc.dma.SocDma;
import com.cburch.logisim.tools.ToolTipMaker;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.List;
import org.junit.jupiter.api.Test;

class SocCaptionLayoutTest {
  @Test
  void busPreviewCannotPaintIntoAdjacentPanelsAndKeepsItsFullName() {
    final var name = "a_very_long_bus_name_that_cannot_fit_inside_one_hundred_units";
    final var manager = mock(SocSimulationManager.class);
    when(manager.getSocBusDisplayString("bus")).thenReturn(name);
    final var bus = new SocBusInfo("bus");
    bus.setSocSimulationManager(manager, null);
    final var image = new BufferedImage(220, 80, BufferedImage.TYPE_INT_ARGB);
    final var g = image.createGraphics();
    final var area = Bounds.create(20, 20, 100, 16);
    final var font = new Font(Font.SERIF, Font.BOLD, 32);
    try {
      g.setFont(font);
      bus.paint(g, area);
      assertEquals(font, g.getFont());
      assertEquals(name, bus.getDisplayName());
      for (var y = 0; y < image.getHeight(); y++) {
        for (var x = 0; x < image.getWidth(); x++) {
          if ((image.getRGB(x, y) >>> 24) != 0) assertTrue(area.contains(x, y));
        }
      }
      assertEquals("bus", bus.getBusId());
    } finally {
      g.dispose();
    }
  }

  @Test
  void dmaTooltipExposesUnshortenedBusNamesAndRetainsPortTooltips() {
    final var factory = new SocDma();
    final var attrs = factory.createAttributeSet();
    final var comp = factory.createComponent(Location.create(50, 50, true), attrs);
    final var bounds = comp.getBounds();
    final var ends = List.copyOf(comp.getEnds());
    final var manager = mock(SocSimulationManager.class);
    final var name = "the_complete_control_bus_name_for_a_small_preview";
    when(manager.getSocBusDisplayString("control")).thenReturn(name);
    final var bus = attrs.getValue(SocSimulationManager.SOC_BUS_SELECT);
    bus.setBusId("control");
    bus.setSocSimulationManager(manager, comp);
    final var tooltip = (ToolTipMaker) comp.getFeature(ToolTipMaker.class);
    final var canvas = mock(Canvas.class);
    assertTrue(tooltip.getToolTip(new ComponentUserEvent(canvas, 150, 80)).contains(name));
    final var reset = ends.get(SocDma.RESET_INDEX).getLocation();
    final var portText = tooltip.getToolTip(new ComponentUserEvent(canvas, reset.getX(), reset.getY()));
    assertEquals(com.cburch.logisim.soc.Strings.S.get("SocDmaResetInput"), portText);
    assertEquals(bounds, comp.getBounds());
    assertEquals(ends, comp.getEnds());
  }
}

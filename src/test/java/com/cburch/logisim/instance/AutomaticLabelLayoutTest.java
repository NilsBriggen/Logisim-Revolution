/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.instance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.comp.ComponentUserEvent;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.tools.TextEditable;
import com.cburch.logisim.util.GraphicsUtil;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class AutomaticLabelLayoutTest {
  @Test
  void measuredPlacementSharesPaintHitAndCaretBoundsWithoutChangingTheDocument() {
    for (final var facing : List.of(Direction.EAST, Direction.WEST, Direction.NORTH, Direction.SOUTH)) {
      final var factory = new LabelFactory();
      final var attrs = factory.createAttributeSet();
      final var font = new Font(Font.SERIF, Font.BOLD | Font.ITALIC, 24);
      attrs.setValue(StdAttr.FACING, facing);
      attrs.setValue(StdAttr.LABEL_FONT, font);
      attrs.setValue(StdAttr.LABEL, "long_factory_label_0123456789");
      final var comp = (InstanceComponent) factory.createComponent(Location.create(250, 150, true), attrs);
      final var field = (InstanceTextField) comp.getFeature(TextEditable.class);
      final var body = comp.getBounds();
      final var ends = List.copyOf(comp.getEnds());
      final var image = new BufferedImage(900, 300, BufferedImage.TYPE_INT_ARGB);
      final var g = image.createGraphics();
      try {
        final var label = field.getBounds(g);
        assertEquals(Bounds.EMPTY_BOUNDS, label.intersect(body));
        for (final var end : ends) {
          assertEquals(Bounds.EMPTY_BOUNDS,
              label.intersect(Bounds.create(end.getLocation()).expand(6)));
        }
        g.setColor(Color.BLACK);
        field.draw(comp, new ComponentDrawContext(null, null, null, g, g));
        assertEquals(label, field.getBounds(g));
        assertTrue(comp.getBounds(g).contains(label));
        assertTrue(comp.contains(Location.create(label.getCenterX(), label.getCenterY(), false), g));
        final var canvas = mock(Canvas.class);
        when(canvas.getGraphics()).thenReturn(g);
        assertNotNull(field.getTextCaret(new ComponentUserEvent(canvas,
            label.getCenterX(), label.getCenterY())));
        assertEquals(body, comp.getBounds());
        assertEquals(ends, comp.getEnds());
        assertEquals(font, attrs.getValue(StdAttr.LABEL_FONT));
        assertEquals("long_factory_label_0123456789", attrs.getValue(StdAttr.LABEL));
      } finally {
        g.dispose();
      }
    }
  }

  @Test
  void changingFontRemeasuresAndExplicitPositionDisablesAutomaticPlacement() {
    final var factory = new LabelFactory();
    final var comp = (InstanceComponent) factory.createComponent(
        Location.create(100, 100, true), factory.createAttributeSet());
    final var field = (InstanceTextField) comp.getFeature(TextEditable.class);
    final var g = new BufferedImage(500, 300, BufferedImage.TYPE_INT_ARGB).createGraphics();
    try {
      final var small = field.getBounds(g);
      assertTrue(comp.getBounds().contains(small));
      comp.getAttributeSet().setValue(StdAttr.LABEL_FONT, new Font(Font.MONOSPACED, Font.BOLD, 48));
      final var large = field.getBounds(g);
      assertFalse(comp.getBounds().contains(large));
      comp.getInstance().setTextField(StdAttr.LABEL, StdAttr.LABEL_FONT,
          40, 50, GraphicsUtil.H_LEFT, GraphicsUtil.V_TOP);
      final var fixed = field.getBounds(g);
      assertEquals(40, fixed.getX());
      assertEquals(50, fixed.getY());
      field.draw(comp, new ComponentDrawContext(null, null, null, g, g));
      assertEquals(fixed, field.getBounds(g));
    } finally {
      g.dispose();
    }
  }

  @Test
  void factoryInteriorChangesAreResolvedBeforePaintingOrHitTesting() {
    final var factory = new LabelFactory();
    final var comp = (InstanceComponent) factory.createComponent(
        Location.create(100, 100, true), factory.createAttributeSet());
    final var safe = new AtomicReference<>(comp.getBounds().expand(-8));
    comp.getInstance().setAutoLabelTextField(100, 100,
        GraphicsUtil.H_CENTER, GraphicsUtil.V_CENTER, safe::get);
    final var field = (InstanceTextField) comp.getFeature(TextEditable.class);
    final var g = new BufferedImage(300, 200, BufferedImage.TYPE_INT_ARGB).createGraphics();
    try {
      assertTrue(comp.getBounds().contains(field.getBounds(g)));
      safe.set(Bounds.EMPTY_BOUNDS);
      assertFalse(comp.getBounds().contains(field.getBounds(g)));
      safe.set(comp.getBounds().expand(-8));
      assertTrue(comp.getBounds().contains(field.getBounds(g)));
    } finally {
      g.dispose();
    }
  }

  private static final class LabelFactory extends InstanceFactory {
    private LabelFactory() {
      super("TestFactoryLabel");
      setOffsetBounds(Bounds.create(-20, -20, 40, 40));
      setAttributes(new Attribute<?>[] {StdAttr.LABEL, StdAttr.LABEL_FONT, StdAttr.FACING},
          new Object[] {"a", new Font(Font.DIALOG, Font.PLAIN, 12), Direction.EAST});
    }

    @Override
    protected void configureNewInstance(Instance instance) {
      instance.setPorts(new Port[] {new Port(20, 0, Port.OUTPUT, 1)});
      final var b = instance.getBounds();
      instance.setAutoLabelTextField(b.getCenterX(), b.getCenterY(),
          GraphicsUtil.H_CENTER, GraphicsUtil.V_CENTER, b.expand(-8));
    }

    @Override
    public void paintInstance(InstancePainter painter) {
      painter.drawLabel();
    }

    @Override
    public void propagate(InstanceState state) {}
  }
}

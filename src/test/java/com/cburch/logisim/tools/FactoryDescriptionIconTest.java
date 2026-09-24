/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */


package com.cburch.logisim.tools;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.gui.icons.ComponentIcons;
import com.cburch.logisim.std.wiring.Power;
import com.cburch.logisim.std.wiring.WiringLibrary;
import com.cburch.logisim.util.StringUtil;
import com.cburch.logisim.util.UiScale;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class FactoryDescriptionIconTest {
  private static final Icon EXPLICIT = new Icon() {
    @Override
    public int getIconWidth() {
      return 16;
    }

    @Override
    public int getIconHeight() {
      return 16;
    }

    @Override
    public void paintIcon(Component component, Graphics graphics, int x, int y) {
      graphics.setColor(Color.ORANGE);
      graphics.fillRect(x + 3, y + 2, 8, 9);
    }
  };

  @Test
  void explicitIconConstructorPreservesObjectBeforeAndAfterFactoryLoad() {
    final var desc = new FactoryDescription(Power.class, StringUtil.constantGetter("Power"), EXPLICIT);
    assertSame(EXPLICIT, desc.getIcon());
    assertFalse(desc.isFactoryLoaded());
    assertNotNull(desc.getFactory(WiringLibrary.class));
    assertSame(EXPLICIT, desc.getIcon());
    assertTrue(desc.hasPersistentIcon());
    assertArrayEquals(renderIcon(EXPLICIT), renderTool(new AddTool(WiringLibrary.class, desc)));
  }

  @Test
  void extensionExplicitIconAlsoRemainsAuthoritativeAfterLoading() {
    final var desc = new FactoryDescription(
        IconTestFactory.class, StringUtil.constantGetter("Extension"), EXPLICIT);
    final var tool = new AddTool(WiringLibrary.class, desc);
    assertNotNull(tool.getFactory());
    assertArrayEquals(renderIcon(EXPLICIT), renderTool(tool));
  }

  @Test
  void builtinDescriptorResolvesVectorWithoutConstructingFactory() {
    final var desc = new FactoryDescription(
        Power.class, StringUtil.constantGetter("Power"), "power.gif");
    assertSame(ComponentIcons.forFactory(Power.class), desc.getIcon());
    assertFalse(desc.isFactoryLoaded());
    assertFalse(desc.getIcon() instanceof ImageIcon);
  }

  @Test
  void builtinLazyAndDirectIconsRemainIdenticalAtEveryScale() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var scale = UiScale.factor();
      try {
        for (final var factor : new double[] {1, 1.6, 2}) {
          UiScale.setFactor(factor);
          final var desc = new FactoryDescription(
              Power.class, StringUtil.constantGetter("Power"), "power.gif");
          final var before = renderIcon(desc.getIcon());
          final var tool = new AddTool(WiringLibrary.class, desc);
          assertNotNull(tool.getFactory());
          assertArrayEquals(before, renderTool(tool), "loaded descriptor at " + factor);
          assertArrayEquals(before, renderTool(new AddTool(new Power())), "direct at " + factor);
        }
      } finally {
        UiScale.setFactor(scale);
      }
    });
  }

  @Test
  void unknownFactoryRetainsRasterResourceAndCustomPaintFallback() {
    final var desc = new FactoryDescription(
        IconTestFactory.class, StringUtil.constantGetter("Extension"), "subcirc.gif");
    assertInstanceOf(ImageIcon.class, desc.getIcon());
    assertFalse(desc.isFactoryLoaded());
    assertFalse(desc.hasPersistentIcon());
    assertArrayEquals(
        renderTool(new AddTool(new IconTestFactory())),
        renderTool(new AddTool(WiringLibrary.class, desc)));
  }

  @Test
  void absentOrNullExtensionIconUsesItsOwnPainter() {
    final var desc = new FactoryDescription(
        IconTestFactory.class, StringUtil.constantGetter("Extension"), (Icon) null);
    assertNull(desc.getIcon());
    assertArrayEquals(
        renderTool(new AddTool(new IconTestFactory())),
        renderTool(new AddTool(WiringLibrary.class, desc)));
    final var missing = new FactoryDescription(
        IconTestFactory.class, StringUtil.constantGetter("Extension"), "not-a-real-icon.gif");
    assertNull(missing.getIcon());
    assertArrayEquals(
        renderTool(new AddTool(new IconTestFactory())),
        renderTool(new AddTool(WiringLibrary.class, missing)));
  }

  @Test
  void nullExplicitBuiltinIconUsesSemanticDefault() {
    final var desc = new FactoryDescription(
        Power.class, StringUtil.constantGetter("Power"), (Icon) null);
    assertSame(ComponentIcons.forFactory(Power.class), desc.getIcon());
  }

  private static int[] renderIcon(Icon icon) {
    final var image = new BufferedImage(60, 60, BufferedImage.TYPE_INT_ARGB);
    final var graphics = image.createGraphics();
    try {
      icon.paintIcon(null, graphics, 5, 5);
    } finally {
      graphics.dispose();
    }
    return image.getRGB(0, 0, 60, 60, null, 0, 60);
  }

  private static int[] renderTool(Tool tool) {
    final var image = new BufferedImage(60, 60, BufferedImage.TYPE_INT_ARGB);
    final var graphics = image.createGraphics();
    try {
      tool.paintIcon(new ComponentDrawContext(null, null, null, graphics, graphics), 3, 3);
    } finally {
      graphics.dispose();
    }
    return image.getRGB(0, 0, 60, 60, null, 0, 60);
  }
}

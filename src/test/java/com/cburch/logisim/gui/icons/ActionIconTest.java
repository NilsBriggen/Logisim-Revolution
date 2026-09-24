/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */


package com.cburch.logisim.gui.icons;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.draw.icons.DrawCurveIcon;
import com.cburch.draw.icons.DrawLineIcon;
import com.cburch.draw.icons.DrawPolylineIcon;
import com.cburch.draw.icons.DrawShapeIcon;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.gui.theme.AppIcons;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.tools.MenuTool;
import com.cburch.logisim.tools.PokeTool;
import com.cburch.logisim.tools.WiringTool;
import com.cburch.logisim.util.UiScale;
import com.cburch.logisim.vhdl.gui.HdlToolbarModel;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.List;
import javax.swing.Icon;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class ActionIconTest {
  @Test
  void compatibilityWrappersMapToTheirSemanticAction() {
    assertEquals(AppIcons.Id.COMPILE, new CompileIcon().iconId());
    assertEquals(AppIcons.Id.RUN, new RunIcon().iconId());
    assertEquals(AppIcons.Id.TYPE, new TextIcon().iconId());
    assertEquals(AppIcons.Id.IMAGE, new ImageIcon().iconId());
    assertEquals(AppIcons.Id.POINTER, new SelectIcon().iconId());
    assertEquals(AppIcons.Id.APPEARANCE, new AppearEditIcon().iconId());
    assertEquals(AppIcons.Id.OPEN, new OpenSaveIcon(OpenSaveIcon.FILE_OPEN).iconId());
    assertEquals(AppIcons.Id.SAVE, new OpenSaveIcon(OpenSaveIcon.FILE_SAVE).iconId());
    assertEquals(AppIcons.Id.SAVE_AS, new OpenSaveIcon(OpenSaveIcon.FILE_SAVE_AS).iconId());
    assertEquals(AppIcons.Id.CHECK, new HdlIcon(HdlToolbarModel.HDL_VALIDATE).iconId());
    assertEquals(AppIcons.Id.EXPORT, new HdlIcon(HdlToolbarModel.HDL_EXPORT).iconId());
    assertEquals(AppIcons.Id.OPEN, new HdlIcon(HdlToolbarModel.HDL_IMPORT).iconId());
    assertEquals(AppIcons.Id.ZOOM_IN, new ZoomIcon(ZoomIcon.ZOOMIN).iconId());
    assertEquals(AppIcons.Id.ZOOM_OUT, new ZoomIcon(ZoomIcon.ZOOMOUT).iconId());
    assertEquals(AppIcons.Id.SEARCH, new ZoomIcon().iconId());
    assertEquals(AppIcons.Id.ARROW_UP, new FatArrowIcon(Direction.NORTH).iconId());
    assertEquals(AppIcons.Id.ARROW_LEFT, new FatArrowIcon(Direction.WEST).iconId());
    assertEquals(AppIcons.Id.ARROW_RIGHT, new FatArrowIcon(Direction.EAST).iconId());
    assertEquals(AppIcons.Id.ARROW_DOWN, new FatArrowIcon(Direction.SOUTH).iconId());
  }

  @Test
  void everySvgActuallyRendersAtNormalFractionalAndDoubleScale() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var original = UiScale.factor();
      try {
        for (final var scale : new double[] {1, 1.6, 2}) {
          UiScale.setFactor(scale);
          for (final var id : AppIcons.Id.values()) {
            final var icon = AppIcons.get(id);
            assertEquals(UiScale.scaled(AppIcons.SIZE), icon.getIconWidth());
            assertTrue(hasInk(render(icon)), id + " at " + scale);
          }
          for (final var icon : List.of(
              new CompileIcon(), new TextIcon(), new ImageIcon(), new SelectIcon(),
              new DrawLineIcon(), new DrawCurveIcon(), new DrawPolylineIcon(false),
              new DrawPolylineIcon(true), new DrawShapeIcon(DrawShapeIcon.RECTANGLE),
              new DrawShapeIcon(DrawShapeIcon.ROUNDED_RECTANGLE),
              new DrawShapeIcon(DrawShapeIcon.ELIPSE))) {
            assertEquals(AppPreferences.getIconSize(), icon.getIconWidth());
            assertTrue(hasInk(render(icon)));
          }
        }
      } finally {
        UiScale.setFactor(original);
      }
    });
  }

  @Test
  void emptyDrcIsNotAnUnverifiedSuccessBadge() {
    assertFalse(hasInk(render(new DrcIcon(false))));
    assertTrue(hasInk(render(new DrcIcon(true))));
  }

  @Test
  void pressedStateAndDisabledActionRemainDistinct() {
    assertFalse(Arrays.equals(render(new ShowStateIcon(false)), render(new ShowStateIcon(true))));
    final var icon = new ProjectAddIcon(false);
    final var enabled = render(icon);
    icon.setDeselect(true);
    assertFalse(Arrays.equals(enabled, render(icon)));
  }

  @Test
  void drawingPrimitivesRemainDistinct() {
    final var line = render(new DrawLineIcon());
    final var curve = render(new DrawCurveIcon());
    assertFalse(Arrays.equals(line, curve));
    assertFalse(Arrays.equals(
        render(new DrawPolylineIcon(false)), render(new DrawPolylineIcon(true))));
    assertFalse(Arrays.equals(
        render(new DrawShapeIcon(DrawShapeIcon.RECTANGLE)),
        render(new DrawShapeIcon(DrawShapeIcon.ELIPSE))));
  }

  @Test
  void pokeWireAndMenuToolIconsUseSvgRegistry() {
    for (final var tool : List.of(new PokeTool(), new WiringTool(), new MenuTool())) {
      final var image = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);
      final var expected = new BufferedImage(80, 80, BufferedImage.TYPE_INT_ARGB);
      final var graphics = image.createGraphics();
      final var expectedGraphics = expected.createGraphics();
      try {
        tool.paintIcon(new ComponentDrawContext(null, null, null, graphics, graphics), 3, 5);
        final var id = tool instanceof PokeTool ? AppIcons.Id.HAND
            : tool instanceof WiringTool ? AppIcons.Id.WIRE : AppIcons.Id.MORE;
        AppIcons.get(id,
            AppPreferences.IconSize).paintIcon(null, expectedGraphics, 3, 5);
        assertTrue(Arrays.equals(
            image.getRGB(0, 0, 80, 80, null, 0, 80),
            expected.getRGB(0, 0, 80, 80, null, 0, 80)));
      } finally {
        graphics.dispose();
        expectedGraphics.dispose();
      }
    }
  }

  private static int[] render(Icon icon) {
    final var size = icon.getIconWidth();
    final var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    final var graphics = image.createGraphics();
    try {
      icon.paintIcon(null, graphics, 0, 0);
    } finally {
      graphics.dispose();
    }
    return image.getRGB(0, 0, size, size, null, 0, size);
  }

  private static boolean hasInk(int[] pixels) {
    return Arrays.stream(pixels).anyMatch(pixel -> (pixel >>> 24) != 0);
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.draw.toolbar;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.TestBase;
import com.cburch.logisim.util.UiScale;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Tests the toolbar button wrapper around a {@link ToolbarItem}. */
public class ToolbarButtonTest extends TestBase {

  /** A minimal item whose painting is a no-op. */
  private static class FakeItem implements ToolbarItem {
    private final boolean selectable;
    private final Dimension dimension;

    FakeItem(boolean selectable, int width, int height) {
      this.selectable = selectable;
      this.dimension = new Dimension(width, height);
    }

    @Override
    public Dimension getDimension(Object orientation) {
      return new Dimension(dimension);
    }

    @Override
    public String getToolTip() {
      return "tip";
    }

    @Override
    public boolean isSelectable() {
      return selectable;
    }

    @Override
    public void paintIcon(Component destination, Graphics gfx) {
      // Nothing to paint in tests.
    }
  }

  /** Records which items the toolbar asked to select. */
  private static class RecordingModel implements ToolbarModel {
    private final List<ToolbarItem> items = new ArrayList<>();
    private final List<ToolbarItem> selected = new ArrayList<>();

    @Override
    public void addToolbarModelListener(ToolbarModelListener listener) {
      // Not needed for these tests.
    }

    @Override
    public List<ToolbarItem> getItems() {
      return items;
    }

    @Override
    public boolean isSelected(ToolbarItem item) {
      return false;
    }

    @Override
    public void itemSelected(ToolbarItem item) {
      selected.add(item);
    }

    @Override
    public void removeToolbarModelListener(ToolbarModelListener listener) {
      // Not needed for these tests.
    }
  }

  @Test
  public void preferredSizeAddsScaledPaddingAroundTheItem() {
    final var model = new RecordingModel();
    final var item = new FakeItem(true, 16, 16);
    model.items.add(item);
    final var toolbar = new Toolbar(model);
    final var button = new ToolbarButton(toolbar, item);

    final var expected = 16 + 2 * UiScale.scaled(2);
    assertEquals(new Dimension(expected, expected), button.getPreferredSize());
  }

  @Test
  public void announcesItsToolTipAsAccessibleName() {
    final var model = new RecordingModel();
    final var item = new FakeItem(true, 16, 16);
    model.items.add(item);
    final var button = new ToolbarButton(new Toolbar(model), item);

    assertEquals("tip", button.getAccessibleContext().getAccessibleName());
  }

  @Test
  public void minimumAndMaximumSizeMatchPreferredSize() {
    final var model = new RecordingModel();
    final var item = new FakeItem(true, 20, 12);
    final var button = new ToolbarButton(new Toolbar(model), item);

    assertEquals(button.getPreferredSize(), button.getMinimumSize());
    assertEquals(button.getPreferredSize(), button.getMaximumSize());
  }

  @Test
  public void clickingASelectableItemSelectsItOnTheModel() {
    final var model = new RecordingModel();
    final var item = new FakeItem(true, 16, 16);
    model.items.add(item);
    final var button = new ToolbarButton(new Toolbar(model), item);

    button.doClick();

    assertEquals(1, model.selected.size());
    assertSame(item, model.selected.get(0));
  }

  /** Decorations such as separators must not take focus or react to the pointer. */
  @Test
  public void nonSelectableItemsAreNotInteractive() {
    final var model = new RecordingModel();
    final var separator = new FakeItem(false, 8, 16);
    final var button = new ToolbarButton(new Toolbar(model), separator);

    assertFalse(button.isFocusable());
    assertFalse(button.isRolloverEnabled());

    button.doClick();
    assertTrue(model.selected.isEmpty());
  }

  @Test
  public void selectableItemsAreInteractive() {
    final var model = new RecordingModel();
    final var item = new FakeItem(true, 16, 16);
    final var button = new ToolbarButton(new Toolbar(model), item);

    assertTrue(button.isFocusable());
    assertTrue(button.isRolloverEnabled());
  }

  /** The look and feel must not reinstate its own button frame when the theme changes. */
  @Test
  public void stylingSurvivesALookAndFeelChange() {
    final var model = new RecordingModel();
    final var button = new ToolbarButton(new Toolbar(model), new FakeItem(true, 16, 16));

    button.updateUI();

    assertFalse(button.isBorderPainted());
    assertFalse(button.isContentAreaFilled());
    assertFalse(button.isFocusPainted());
  }

  @Test
  public void exposesTheItemAndItsTooltip() {
    final var model = new RecordingModel();
    final var item = new FakeItem(true, 16, 16);
    final var button = new ToolbarButton(new Toolbar(model), item);

    assertSame(item, button.getItem());
    assertEquals("tip", button.getToolTipText(null));
  }
}

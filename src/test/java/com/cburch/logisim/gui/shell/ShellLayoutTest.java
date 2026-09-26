/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Dimension;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.SwingUtilities;
import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import org.junit.jupiter.api.Test;

class ShellLayoutTest {

  @Test
  void actualDividerExtentIsUsedWhenTheLookAndFeelScalesItsRequestedSize() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      for (final var orientation : new int[] {
          JSplitPane.HORIZONTAL_SPLIT, JSplitPane.VERTICAL_SPLIT}) {
        final var stored = new AtomicInteger(300);
        final var writes = new AtomicInteger();
        final var pane = split(orientation, true, stored, writes);
        pane.setUI(new BasicSplitPaneUI() {
          @Override
          public BasicSplitPaneDivider createDefaultDivider() {
            return new BasicSplitPaneDivider(this) {
              @Override
              public void setDividerSize(int size) {
                super.setDividerSize(size + 4);
              }
            };
          }
        });
        pane.setBorder(null);
        pane.setDividerSize(8);
        pane.reapply();
        pane.doLayout();
        assertEquals(12, ((BasicSplitPaneUI) pane.getUI()).getDivider().getDividerSize());
        assertEquals(300, childSize(pane, true));
        pane.doLayout();
        assertEquals(300, childSize(pane, true));
        assertEquals(0, writes.get());
      }
    });
  }

  private static ShellLayout.SizedSplit split(
      int orientation, boolean second, AtomicInteger stored, AtomicInteger writes) {
    final var first = new JPanel();
    final var last = new JPanel();
    first.setMinimumSize(new Dimension(100, 100));
    last.setMinimumSize(new Dimension(100, 100));
    final var split = new ShellLayout.SizedSplit(
        orientation, first, last, second, stored::get, size -> {
          stored.set(size);
          writes.incrementAndGet();
        });
    split.setBorder(null);
    split.setDividerSize(8);
    split.setSize(1000, 800);
    split.doLayout();
    return split;
  }

  private static int childSize(ShellLayout.SizedSplit split, boolean second) {
    final var child = second ? split.getRightComponent() : split.getLeftComponent();
    return split.getOrientation() == JSplitPane.HORIZONTAL_SPLIT
        ? child.getWidth() : child.getHeight();
  }

  @Test
  void continuousDraggingKeepsAllThreeDividersAtThePointerAndPersistsOnRelease()
      throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      for (var position = 0; position < 3; position++) {
        final var vertical = position == 2;
        final var second = position != 0;
        final var stored = new AtomicInteger(250);
        final var writes = new AtomicInteger();
        final var split = split(
            vertical ? JSplitPane.VERTICAL_SPLIT : JSplitPane.HORIZONTAL_SPLIT,
            second, stored, writes);
        final var divider = ((BasicSplitPaneUI) split.getUI()).getDivider();
        final var original = split.getDividerLocation();
        divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_PRESSED, 1,
            InputEvent.BUTTON1_DOWN_MASK, 2, 2, 1, false, MouseEvent.BUTTON1));
        divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_DRAGGED, 2,
            InputEvent.BUTTON1_DOWN_MASK, vertical ? 2 : 102, vertical ? 102 : 2,
            0, false, MouseEvent.NOBUTTON));
        split.doLayout();
        split.doLayout();
        assertEquals(original + 100, split.getDividerLocation(), "drag snapped back");
        assertEquals(0, writes.get(), "layout must not write preferences");
        divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_RELEASED, 3,
            0, 2, 2, 1, false, MouseEvent.BUTTON1));
        split.doLayout();
        assertEquals(second ? 150 : 350, stored.get());
        assertEquals(stored.get(), childSize(split, second), "divider width was persisted");
        assertEquals(1, writes.get());
        split.setSize(1200, 1000);
        split.doLayout();
        assertEquals(stored.get(), childSize(split, second));
      }
    });
  }

  @Test
  void transientSmallWindowsAndRepeatedLayoutsDoNotOverwriteRestoredSizes() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var stored = new AtomicInteger(300);
      final var writes = new AtomicInteger();
      final var split = split(JSplitPane.HORIZONTAL_SPLIT, true, stored, writes);
      assertEquals(300, childSize(split, true));
      split.setSize(350, 800);
      split.doLayout();
      assertTrue(childSize(split, true) < 300);
      assertEquals(0, writes.get());
      split.setSize(1000, 800);
      split.doLayout();
      assertEquals(300, childSize(split, true));
      split.setDividerLocation(500);
      split.doLayout();
      split.doLayout();
      assertEquals(500, split.getDividerLocation(), "ordinary layout restored stale preferences");
      split.reapply();
      split.doLayout();
      assertEquals(300, childSize(split, true));
    });
  }

  @Test
  void restoredDrawerExpandsForContentAndHonorsExplicitReopen() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var split = split(JSplitPane.VERTICAL_SPLIT, true,
          new AtomicInteger(160), new AtomicInteger());
      final var content = (JPanel) split.getBottomComponent();
      content.setMinimumSize(new Dimension(0, 360));
      split.doLayout();
      assertEquals(360, content.getHeight());
      split.setBottomComponent(null);
      split.doLayout();
      split.setBottomComponent(content);
      split.reapply();
      split.doLayout();
      assertEquals(360, content.getHeight());
      assertTrue(split.getTopComponent().getHeight() >= 100);
    });
  }

  @Test
  void editorReserveKeepsTheCanvasUsableWithoutStarvingTheDrawer() {
    final var min = com.cburch.logisim.util.UiScale.scaled(ShellLayout.MIN_EDITOR_HEIGHT);
    final var max = com.cburch.logisim.util.UiScale.scaled(ShellLayout.MAX_EDITOR_RESERVE);
    assertEquals(min, ShellLayout.editorReserve(0));
    assertEquals(max, ShellLayout.editorReserve(max * 4));
    final var middle = (min + max) / 2 * 2;
    assertEquals(middle / 2, ShellLayout.editorReserve(middle));
  }

  @Test
  void drawerOpenedForAPanelGrowsToItsHeightButNotPastTheEditorReserve() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var stored = new AtomicInteger(160);
      final var split = split(JSplitPane.VERTICAL_SPLIT, true, stored, new AtomicInteger());
      final var content = (JPanel) split.getBottomComponent();
      assertEquals(160, content.getHeight());
      split.ensureAtLeast(340);
      split.doLayout();
      assertEquals(340, content.getHeight());
      split.ensureAtLeast(200);
      split.doLayout();
      assertEquals(340, content.getHeight(), "a smaller request must not shrink the drawer");

      ((JPanel) split.getTopComponent()).setMinimumSize(new Dimension(0, 600));
      split.ensureAtLeast(500);
      split.doLayout();
      assertTrue(split.getTopComponent().getHeight() >= 600, "the editor reserve was ignored");
    });
  }

  @Test
  void draggingPastTheLargestPanelSizeIsClampedInMemoryAsWellAsOnDisk() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var maximum = com.cburch.logisim.util.UiScale.scaled(LayoutPrefs.MAX_PANEL);
      final var stored = new AtomicInteger(200);
      final var split = split(JSplitPane.VERTICAL_SPLIT, true, stored, new AtomicInteger());
      split.setSize(1000, maximum + 600);
      split.doLayout();
      final var divider = ((BasicSplitPaneUI) split.getUI()).getDivider();
      final var drag = maximum + 100 - 200;
      divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_PRESSED, 1,
          InputEvent.BUTTON1_DOWN_MASK, 2, 2, 1, false, MouseEvent.BUTTON1));
      divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_DRAGGED, 2,
          InputEvent.BUTTON1_DOWN_MASK, 2, 2 - drag, 0, false, MouseEvent.NOBUTTON));
      split.doLayout();
      assertTrue(split.getBottomComponent().getHeight() > maximum, "fixture did not overshoot");
      divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_RELEASED, 3,
          0, 2, 2, 1, false, MouseEvent.BUTTON1));
      split.doLayout();
      assertEquals(maximum, stored.get());
      assertEquals(maximum, split.getBottomComponent().getHeight());
    });
  }
}

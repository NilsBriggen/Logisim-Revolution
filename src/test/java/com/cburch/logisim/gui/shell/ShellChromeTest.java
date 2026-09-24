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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.draw.toolbar.Toolbar;
import com.cburch.logisim.gui.theme.AppIcons;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.UiFonts;
import com.cburch.logisim.util.UiScale;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

class ShellChromeTest {

  @Test
  void sectionActivityAndCloseControlsHaveNativeKeyboardActionsAndNames() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var content = new JPanel();
      final var section = new SectionPanel("Register state", content, false);
      final var heading = components(section, JToggleButton.class).get(0);
      assertEquals("Register state", heading.getAccessibleContext().getAccessibleName());
      assertFalse(content.isVisible());
      activate(heading, KeyEvent.VK_SPACE);
      assertTrue(section.isExpanded());
      assertTrue(content.isVisible());
      assertTrue(heading.isSelected());
      activate(heading, KeyEvent.VK_ENTER);
      assertFalse(section.isExpanded());
      assertFalse(heading.isSelected());

      final var invoked = new AtomicInteger();
      final var activity = new ActivityBar();
      activity.addAction(
          "properties", AppIcons.Id.INSPECTOR, "Properties", invoked::incrementAndGet);
      final var action = components(activity, AbstractButton.class).get(0);
      activate(action, KeyEvent.VK_ENTER);
      assertEquals(1, invoked.get());
      activity.setTooltip("properties", "Eigenschaften");
      assertEquals("Eigenschaften", action.getAccessibleContext().getAccessibleName());

      final var inspector = new Inspector("ALU_control");
      inspector.setOnClose(() -> invoked.addAndGet(10));
      inspector.setOnClose(() -> invoked.addAndGet(100));
      final var close = components(inspector, AbstractButton.class).get(0);
      assertFalse(close.getAccessibleContext().getAccessibleName().isBlank());
      activate(close, KeyEvent.VK_SPACE);
      assertEquals(101, invoked.get(), "replacing close must not retain the old callback");

      for (final var button : List.of(heading, action, close)) {
        assertTrue(button.isFocusable());
        assertTrue(button.isFocusPainted());
      }
      assertTrue(components(inspector, JLabel.class).stream()
          .anyMatch(label -> label.getText().equals("ALU_control")));
    });
  }

  @Test
  void existingChromeRemeasuresFontsPaddingAndBoundsAcrossLiveScaleChanges() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var originalScale = UiScale.factor();
      final var originalFont = UIManager.get("Label.font");
      final var root = new JPanel();
      final var activity = new ActivityBar();
      activity.addAction("test", AppIcons.Id.TEST, "Tests", () -> {});
      final var section = new SectionPanel("Signals", new JPanel(), false);
      section.setBadge(AppIcons.Id.CIRCUIT);
      final var header = new PanelHeader("MixedCaseCircuit");
      final var inspector = new Inspector("Properties");
      inspector.setOnClose(() -> {});
      final var toolbar = new MainToolbar(new Toolbar(null), new Toolbar(null));
      root.add(activity);
      root.add(section);
      root.add(header);
      root.add(inspector);
      root.add(toolbar);
      root.add(new SidePanel());
      root.addNotify();
      try {
        for (final var scale : new double[] {1.0, 1.6, 2.0, 1.0}) {
          UiScale.setFactor(scale);
          UIManager.put("Label.font",
              new Font(Font.DIALOG, Font.PLAIN, (int) Math.round(13 * scale)));
          Theme.fireChanged();
          final var activityButton = components(activity, AbstractButton.class).get(0);
          assertEquals(UiScale.scaled(44), activityButton.getPreferredSize().width);
          assertEquals(activityButton.getPreferredSize(), activityButton.getMaximumSize());
          assertEquals(activityButton.getPreferredSize(), activityButton.getMinimumSize());
          final var heading = components(section, JToggleButton.class).get(0);
          assertEquals(UiFonts.small(), heading.getFont());
          assertEquals(Spacing.XS / 2, heading.getMargin().top,
              "native button margins must stay logical");
          assertEquals(Spacing.xs(), header.getInsets().top);
          assertEquals(Spacing.sm(), header.getInsets().left);
          assertEquals(Spacing.xs(), toolbar.getInsets().left);
          assertEquals(Math.max(1, UiScale.scaled(1)),
              components(toolbar, JSeparator.class).get(0).getMaximumSize().width);
          final var close = components(inspector, AbstractButton.class).get(0);
          assertEquals(UiFonts.small(), close.getFont());
          assertEquals(Spacing.XS, close.getMargin().left);
          final var title = components(header, JLabel.class).get(0);
          assertEquals(UiFonts.small(), title.getFont());
          assertEquals("MixedCaseCircuit", title.getText());
          root.setSize(root.getPreferredSize());
          layoutTree(root);
          assertTrue(heading.getHeight() >= heading.getFontMetrics(heading.getFont()).getHeight());
        }
        final var title = components(header, JLabel.class).get(0);
        final var detachedFont = title.getFont();
        root.removeNotify();
        UIManager.put("Label.font", new Font(Font.DIALOG, Font.PLAIN, 30));
        Theme.fireChanged();
        assertEquals(detachedFont, title.getFont(), "detached header kept a theme subscription");
        root.addNotify();
        assertEquals(UiFonts.small(), title.getFont(), "reattached header failed to refresh");
      } finally {
        root.removeNotify();
        UIManager.put("Label.font", originalFont);
        UiScale.setFactor(originalScale);
      }
    });
  }

  static void activate(AbstractButton button, int keyCode) {
    for (final var released : new boolean[] {false, true}) {
      final var key = KeyStroke.getKeyStroke(keyCode, 0, released);
      final var binding = button.getInputMap(JComponent.WHEN_FOCUSED).get(key);
      assertNotNull(binding, "missing keyboard binding");
      final var action = button.getActionMap().get(binding);
      assertNotNull(action, "missing button action");
      action.actionPerformed(new ActionEvent(button, ActionEvent.ACTION_PERFORMED, ""));
    }
  }

  static <T extends Component> List<T> components(Container root, Class<T> type) {
    final var result = new ArrayList<T>();
    for (final var component : root.getComponents()) {
      if (type.isInstance(component)) result.add(type.cast(component));
      if (component instanceof Container container) result.addAll(components(container, type));
    }
    return result;
  }

  private static void layoutTree(Container root) {
    root.doLayout();
    for (final var child : root.getComponents()) {
      if (child instanceof Container container) layoutTree(container);
    }
  }
}

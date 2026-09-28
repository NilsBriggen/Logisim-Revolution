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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import com.cburch.draw.toolbar.Toolbar;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class ShellWelcomeModeTest {

  @Test
  void welcomeModeHidesSideAndInspectorWithoutTouchingPreferences() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try (final var preferences = mockStatic(LayoutPrefs.class)) {
        configure(preferences);
        final var shell = shell(new JPanel());
        assertTrue(shell.isSideVisible());
        assertTrue(shell.isInspectorVisible());
        preferences.clearInvocations();

        shell.setWelcomeMode(true);
        assertFalse(shell.isSideVisible());
        assertFalse(shell.isInspectorVisible());
        assertTrue(shell.sideVisibleForPrefs(), "closing on the welcome screen must not forget");
        assertTrue(shell.inspectorVisibleForPrefs());

        shell.setWelcomeMode(false);
        assertTrue(shell.isSideVisible());
        assertTrue(shell.isInspectorVisible());
        preferences.verify(() -> LayoutPrefs.setSideVisible(anyBoolean()), never());
        preferences.verify(() -> LayoutPrefs.setInspectorVisible(anyBoolean()), never());
      }
    });
  }

  @Test
  void panelClosedDuringWelcomeStaysClosed() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try (final var preferences = mockStatic(LayoutPrefs.class)) {
        configure(preferences);
        final var shell = shell(new JPanel());
        shell.setWelcomeMode(true);
        shell.setSideVisible(true);
        shell.setSideVisible(false);
        shell.setWelcomeMode(false);
        assertFalse(shell.isSideVisible());
        assertTrue(shell.isInspectorVisible());
      }
    });
  }

  @Test
  void focusOrderFollowsRegionsNotRows() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var root = new JPanel(null);
      final var regions = new ArrayList<JPanel>();
      for (var i = 0; i < 3; i++) {
        final var region = new JPanel(null);
        root.add(region);
        regions.add(region);
      }
      // The side panel (region 0) is tall; the editor (region 1) starts higher up than the side
      // panel's second button, and the inspector (region 2) is to the right.
      regions.get(0).setBounds(0, 0, 100, 500);
      regions.get(1).setBounds(100, 0, 300, 500);
      regions.get(2).setBounds(400, 0, 100, 500);
      final var sideLower = button(regions.get(0), 0, 300);
      final var sideUpper = button(regions.get(0), 0, 10);
      final var editor = button(regions.get(1), 0, 100);
      final var inspector = button(regions.get(2), 0, 5);

      final var order = new ArrayList<>(List.of(inspector, editor, sideLower, sideUpper));
      order.sort(ShellLayout.RegionFocusPolicy.comparator(root, regions));
      assertEquals(List.of(sideUpper, sideLower, editor, inspector), order);
    });
  }

  private static JButton button(JPanel parent, int x, int y) {
    final var button = new JButton();
    button.setBounds(x, y, 20, 20);
    parent.add(button);
    return button;
  }

  private static void configure(MockedStatic<LayoutPrefs> preferences) {
    preferences.when(LayoutPrefs::sideWidth).thenReturn(260);
    preferences.when(LayoutPrefs::inspectorWidth).thenReturn(280);
    preferences.when(LayoutPrefs::bottomHeight).thenReturn(200);
    preferences.when(LayoutPrefs::sideVisible).thenReturn(true);
    preferences.when(LayoutPrefs::inspectorVisible).thenReturn(true);
    preferences.when(LayoutPrefs::bottomVisible).thenReturn(false);
    preferences.when(LayoutPrefs::activeSideView).thenReturn("");
  }

  private static ShellLayout shell(JPanel editor) {
    return new ShellLayout(new MainToolbar(new Toolbar(null), null),
        new ActivityBar(), new SidePanel(),
        new EditorArea(new EditorTabs(new EditorTabModel(), tab -> "HDL"), editor),
        new Inspector("Properties"), new BottomPanel(), new StatusBar());
  }
}

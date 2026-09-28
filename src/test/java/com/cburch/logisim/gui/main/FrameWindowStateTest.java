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
import static org.mockito.Mockito.mockStatic;

import com.cburch.draw.toolbar.Toolbar;
import com.cburch.logisim.gui.shell.ActivityBar;
import com.cburch.logisim.gui.shell.BottomPanel;
import com.cburch.logisim.gui.shell.EditorArea;
import com.cburch.logisim.gui.shell.EditorTabModel;
import com.cburch.logisim.gui.shell.EditorTabs;
import com.cburch.logisim.gui.shell.Inspector;
import com.cburch.logisim.gui.shell.LayoutPrefs;
import com.cburch.logisim.gui.shell.MainToolbar;
import com.cburch.logisim.gui.shell.ShellLayout;
import com.cburch.logisim.gui.shell.SidePanel;
import com.cburch.logisim.gui.shell.StatusBar;
import java.awt.Dimension;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class FrameWindowStateTest {

  private static final Dimension SCREEN = new Dimension(1920, 1040);

  @Test
  void savedWindowOfAFewPixelsOpensAtTheMinimumSize() {
    final var minimum = Frame.minimumWindowSize(SCREEN, 1.0);
    assertEquals(new Dimension(Frame.MIN_WINDOW_WIDTH, Frame.MIN_WINDOW_HEIGHT), minimum);
    assertEquals(minimum, Frame.initialWindowSize(1, 1, SCREEN, minimum));
  }

  @Test
  void savedSizeIsKeptWithinTheScreenAndMissingSizeUsesTheDefault() {
    final var minimum = Frame.minimumWindowSize(SCREEN, 1.0);
    assertEquals(new Dimension(1000, 700), Frame.initialWindowSize(1000, 700, SCREEN, minimum));
    assertEquals(SCREEN, Frame.initialWindowSize(5000, 5000, SCREEN, minimum));
    assertEquals(new Dimension(1728, 936), Frame.initialWindowSize(null, null, SCREEN, minimum));
  }

  @Test
  void minimumScalesWithTheInterfaceButNeverExceedsTheScreen() {
    assertEquals(new Dimension(1120, 720), Frame.minimumWindowSize(SCREEN, 2.0));
    final var small = new Dimension(800, 500);
    assertEquals(small, Frame.minimumWindowSize(small, 3.0));
  }

  @Test
  void savingAWindowStoresItsOwnPanelsNotTheLastToggleOfAnyWindow() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try (final var prefs = mockStatic(LayoutPrefs.class)) {
        prefs.when(LayoutPrefs::sideVisible).thenReturn(true);
        prefs.when(LayoutPrefs::inspectorVisible).thenReturn(true);
        prefs.when(LayoutPrefs::bottomVisible).thenReturn(false);
        prefs.when(LayoutPrefs::activeSideView).thenReturn("");
        final var first = shell();
        final var second = shell();
        first.setSideVisible(false);
        second.setInspectorVisible(false);
        prefs.clearInvocations();

        Frame.savePanelVisibility(first);

        prefs.verify(() -> LayoutPrefs.setSideVisible(false));
        prefs.verify(() -> LayoutPrefs.setInspectorVisible(true));
        prefs.verify(() -> LayoutPrefs.setBottomVisible(false));
      }
    });
  }

  private static ShellLayout shell() {
    return new ShellLayout(new MainToolbar(new Toolbar(null), null),
        new ActivityBar(), new SidePanel(),
        new EditorArea(new EditorTabs(new EditorTabModel(), tab -> "HDL"), new JPanel()),
        new Inspector("Properties"), new BottomPanel(), new StatusBar());
  }
}

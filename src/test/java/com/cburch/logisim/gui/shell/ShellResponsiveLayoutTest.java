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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;

import com.cburch.draw.toolbar.Toolbar;
import com.cburch.logisim.util.UiScale;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.UIScale;
import java.awt.Container;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.basic.BasicSplitPaneUI;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class ShellResponsiveLayoutTest {
  @Test
  void constrainedDoubleScaleReservesTheEditorAndRestoresBothWidthsWithoutSavingCompression()
      throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try (final var metrics = new NativeMetrics();
          final var preferences = mockStatic(LayoutPrefs.class)) {
        configurePreferences(preferences);
        final var shell = shell();
        preferences.clearInvocations();
        layout(shell, 2560);
        assertEquals(520, shell.getSidePanel().getWidth());
        assertEquals(560, shell.getInspector().getWidth());

        for (var pass = 0; pass < 4; pass++) {
          layout(shell, 1280);
          assertTrue(shell.getEditorArea().getWidth() >= 640, "center viewport was starved");
          assertTrue(shell.getSidePanel().getWidth() > 0);
          assertTrue(shell.getSidePanel().getWidth() < 520);
          assertTrue(shell.getInspector().getWidth() > 0);
          assertTrue(shell.getInspector().getWidth() < 560);
          assertEquals(520.0 / 560,
              shell.getSidePanel().getWidth() / (double) shell.getInspector().getWidth(), 0.01);
          assertTrue(shell.isSideVisible());
          assertTrue(shell.isInspectorVisible());
        }
        final var split = (JSplitPane) shell.getSidePanel().getParent();
        final var divider = ((BasicSplitPaneUI) split.getUI()).getDivider();
        divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_PRESSED, 1,
            InputEvent.BUTTON1_DOWN_MASK, 2, 2, 1, false, MouseEvent.BUTTON1));
        divider.dispatchEvent(new MouseEvent(divider, MouseEvent.MOUSE_RELEASED, 2,
            0, 2, 2, 1, false, MouseEvent.BUTTON1));
        verifyNoGeometryWrites(preferences);
        shell.savePreferences();
        preferences.verify(() -> LayoutPrefs.setSideWidth(520));
        preferences.verify(() -> LayoutPrefs.setInspectorWidth(560));
        preferences.verify(() -> LayoutPrefs.setSideVisible(anyBoolean()), never());
        preferences.verify(() -> LayoutPrefs.setInspectorVisible(anyBoolean()), never());
        preferences.clearInvocations();

        layout(shell, 2560);
        assertEquals(520, shell.getSidePanel().getWidth());
        assertEquals(560, shell.getInspector().getWidth());
        verifyNoGeometryWrites(preferences);
      }
    });
  }

  @Test
  void initiallyConstrainedWindowAndExplicitHiddenPanelKeepTheirStoredIntent() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      try (final var metrics = new NativeMetrics();
          final var preferences = mockStatic(LayoutPrefs.class)) {
        configurePreferences(preferences);
        final var shell = shell();
        preferences.clearInvocations();

        layout(shell, 1280);
        assertTrue(shell.getEditorArea().getWidth() >= 640);
        verifyNoGeometryWrites(preferences);
        shell.savePreferences();
        preferences.verify(() -> LayoutPrefs.setSideWidth(520));
        preferences.verify(() -> LayoutPrefs.setInspectorWidth(560));

        shell.setInspectorVisible(false);
        preferences.clearInvocations();
        layout(shell, 1280);
        assertFalse(shell.isInspectorVisible());
        assertEquals(520, shell.getSidePanel().getWidth());
        assertTrue(shell.getEditorArea().getWidth() >= 640);
        layout(shell, 2560);
        assertFalse(shell.isInspectorVisible(), "resize must not reopen a user-hidden panel");
        assertEquals(520, shell.getSidePanel().getWidth());
        verifyNoGeometryWrites(preferences);

        shell.setInspectorVisible(true);
        preferences.clearInvocations();
        layout(shell, 1280);
        assertTrue(shell.getEditorArea().getWidth() >= 640);
        layout(shell, 2560);
        assertEquals(560, shell.getInspector().getWidth());
        verifyNoGeometryWrites(preferences);
      }
    });
  }

  private static void configurePreferences(MockedStatic<LayoutPrefs> preferences) {
    preferences.when(LayoutPrefs::sideWidth).thenAnswer(ignored -> UiScale.scaled(260));
    preferences.when(LayoutPrefs::inspectorWidth).thenAnswer(ignored -> UiScale.scaled(280));
    preferences.when(LayoutPrefs::bottomHeight).thenAnswer(ignored -> UiScale.scaled(200));
    preferences.when(LayoutPrefs::sideVisible).thenReturn(true);
    preferences.when(LayoutPrefs::inspectorVisible).thenReturn(true);
    preferences.when(LayoutPrefs::bottomVisible).thenReturn(false);
    preferences.when(LayoutPrefs::activeSideView).thenReturn("");
  }

  private static void verifyNoGeometryWrites(MockedStatic<LayoutPrefs> preferences) {
    preferences.verify(() -> LayoutPrefs.setSideWidth(anyInt()), never());
    preferences.verify(() -> LayoutPrefs.setInspectorWidth(anyInt()), never());
    preferences.verify(() -> LayoutPrefs.setSideVisible(anyBoolean()), never());
    preferences.verify(() -> LayoutPrefs.setInspectorVisible(anyBoolean()), never());
  }

  private static ShellLayout shell() {
    return new ShellLayout(new MainToolbar(new Toolbar(null), null),
        new ActivityBar(), new SidePanel(),
        new EditorArea(new EditorTabs(new EditorTabModel(), tab -> "HDL"), new JPanel()),
        new Inspector("Properties"), new BottomPanel(), new StatusBar());
  }

  private static void layout(ShellLayout shell, int width) {
    assertEquals(2.0, UiScale.factor(), 0.001, "fixture must remain at native scale 2.0");
    shell.setSize(width, 800);
    layoutTree(shell);
    layoutTree(shell);
  }

  private static void layoutTree(Container container) {
    container.doLayout();
    for (final var child : container.getComponents()) {
      if (child instanceof Container nested) layoutTree(nested);
    }
  }

  /** Keep native fonts and scale coherent regardless of the preceding Swing fixture. */
  private static final class NativeMetrics implements AutoCloseable {
    private final LookAndFeel lookAndFeel = UIManager.getLookAndFeel();
    private final float zoom = UIScale.getZoomFactor();
    // Preserve only the developer override, not a resolved (already scaled) LaF font.
    private final Object labelOverride = UIManager.put("Label.font", null);

    private NativeMetrics() {
      assertTrue(FlatLightLaf.setup());
      UiScale.setFactor(2.0);
    }

    @Override
    public void close() {
      try {
        UIManager.setLookAndFeel(lookAndFeel);
      } catch (javax.swing.UnsupportedLookAndFeelException exception) {
        throw new AssertionError(exception);
      } finally {
        UIScale.setZoomFactor(zoom);
        UIManager.put("Label.font", labelOverride);
      }
    }
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell.palette;

import static com.cburch.logisim.gui.Strings.S;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.gui.shell.FilterField;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Tool;
import java.awt.Component;
import java.awt.Container;
import java.awt.KeyboardFocusManager;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ComponentPaletteTest {
  private record Fixture(Project project, ComponentPalette palette, AddTool and, AddTool mux,
                         List<List<String>> savedRecents, List<List<String>> savedFavourites) {}

  private static Fixture fixture() {
    final var and = tool("AND Gate", "AND");
    final var mux = tool("Multiplexer", "Multiplexer");
    final var file = mock(LogisimFile.class);
    when(file.getName()).thenReturn("test-project");
    when(file.getDisplayName()).thenReturn("Test project");
    when(file.getTools()).thenReturn(List.of(and, mux));
    when(file.getLibraries()).thenReturn(List.of());
    final var project = mock(Project.class);
    when(project.getLogisimFile()).thenReturn(file);
    final var savedRecents = new ArrayList<List<String>>();
    final var savedFavourites = new ArrayList<List<String>>();
    final var palette = new ComponentPalette(project, List.of(), List.of(),
        values -> savedFavourites.add(List.copyOf(values)),
        values -> savedRecents.add(List.copyOf(values)));
    return new Fixture(project, palette, and, mux, savedRecents, savedFavourites);
  }

  private static LogisimMenuBar installHelpFixture(Fixture fixture) {
    final var inverter = tool("NOT Gate", "NOT");
    final var factory = mock(ComponentFactory.class);
    when(factory.getName()).thenReturn("NOT Gate");
    when(inverter.getFactory()).thenReturn(factory);
    when(fixture.project().getLogisimFile().getTools())
        .thenReturn(List.of(fixture.and(), fixture.mux(), inverter));
    // Keyboard focus or a context menu can refer to a different tile than the armed tool.
    when(fixture.project().getTool()).thenReturn(fixture.and());
    final var frame = mock(Frame.class);
    final var menuBar = mock(LogisimMenuBar.class);
    when(frame.getJMenuBar()).thenReturn(menuBar);
    when(fixture.project().getFrame()).thenReturn(frame);
    fixture.palette().updateStructure();
    return menuBar;
  }

  private static ComponentTile notTile(Fixture fixture) {
    return descendants(fixture.palette(), ComponentTile.class).stream()
        .filter(tile -> "NOT Gate".equals(tile.tool().getName())).findFirst().orElseThrow();
  }

  private static void assertHelpDidNotEdit(Fixture fixture) {
    verify(fixture.project(), never()).setTool(any());
    verify(fixture.project(), never()).doAction(any());
    verify(fixture.project(), never()).setForcedDirty();
    verify(fixture.project(), never()).setFileAsDirty();
    verify(fixture.project().getLogisimFile(), never()).setDirty(anyBoolean());
    assertTrue(fixture.savedRecents().isEmpty());
    assertTrue(fixture.savedFavourites().isEmpty());
  }

  private static AddTool tool(String id, String name) {
    final var tool = mock(AddTool.class);
    when(tool.getName()).thenReturn(id);
    when(tool.getDisplayName()).thenReturn(name);
    when(tool.getDescription()).thenReturn(name);
    return tool;
  }

  private static <T> List<T> descendants(Container root, Class<T> type) {
    final var found = new ArrayList<T>();
    for (final var child : root.getComponents()) {
      if (type.isInstance(child)) found.add(type.cast(child));
      if (child instanceof Container container) found.addAll(descendants(container, type));
    }
    return found;
  }

  private static void press(JComponent component, int key) {
    final var action = component.getInputMap().get(KeyStroke.getKeyStroke(key, 0));
    component.getActionMap().get(action).actionPerformed(new ActionEvent(component, 0, ""));
  }

  private static void dispatchKey(JComponent component, int key) {
    final var event = new KeyEvent(component, KeyEvent.KEY_PRESSED, System.currentTimeMillis(),
        0, key, KeyEvent.CHAR_UNDEFINED);
    KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(component, event);
    assertTrue(event.isConsumed(), "the installed tile handler must handle this key");
  }

  @Test
  void f1UsesTheReceivingTileAndSurvivesAFilterRebuildWithoutArmingIt() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var fixture = fixture();
      try {
        final var menuBar = installHelpFixture(fixture);
        final var tile = notTile(fixture);
        final var binding = tile.getInputMap(JComponent.WHEN_FOCUSED)
            .get(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
        assertNotNull(binding, "the real palette tile must install F1");
        assertNotNull(tile.getActionMap().get(binding));
        dispatchKey(tile, KeyEvent.VK_F1);
        verify(menuBar).showHelp("gates_not");

        final var filter = descendants(fixture.palette(), FilterField.class).getFirst();
        filter.setText("NOT");
        press(filter, KeyEvent.VK_DOWN);
        final var result = notTile(fixture);
        assertTrue(result.isFocusable());
        dispatchKey(result, KeyEvent.VK_F1);
        verify(menuBar, times(2)).showHelp("gates_not");
        assertHelpDidNotEdit(fixture);
      } finally {
        fixture.palette().dispose();
      }
    });
  }

  @Test
  void theInstalledContextMenuOffersLocalizedHelpForItsTileWithoutArmingIt() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var fixture = fixture();
      try {
        final var menuBar = installHelpFixture(fixture);
        final var tile = notTile(fixture);
        // Intercept only the native popup boundary; use the tile's actual context-menu callback.
        try (final var popups = mockConstruction(JPopupMenu.class)) {
          dispatchKey(tile, KeyEvent.VK_CONTEXT_MENU);
          assertEquals(1, popups.constructed().size());
          final var popup = popups.constructed().getFirst();
          verify(popup).show(tile, tile.getWidth() / 2, tile.getHeight() / 2);
          final var items = ArgumentCaptor.forClass(JMenuItem.class);
          verify(popup, times(2)).add(items.capture());
          final var help = items.getAllValues().stream()
              .filter(item -> S.get("libHelpItem", tile.tool().getDisplayName())
                  .equals(item.getText())).findFirst().orElseThrow();
          assertTrue(items.getAllValues().stream()
              .anyMatch(item -> S.get("palettePin").equals(item.getText())));
          final var binding = tile.getInputMap().get(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0));
          assertSame(tile.getActionMap().get(binding), help.getAction());
          help.doClick(0);
          verify(menuBar).showHelp("gates_not");
        }
        assertHelpDidNotEdit(fixture);
      } finally {
        fixture.palette().dispose();
      }
    });
  }

  @Test
  void enterUsesTheJustTypedQueryAndNoResultNeverArmsAnOldTile() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var fixture = fixture();
      final var filter = descendants(fixture.palette(), FilterField.class).get(0);
      filter.setText("mux");
      press(filter, KeyEvent.VK_ENTER);
      verify(fixture.project()).setTool(fixture.mux());
      verify(fixture.project(), never()).setTool(fixture.and());
      assertEquals(1, fixture.savedRecents().size());
      filter.setText("nothing-matches-this");
      press(filter, KeyEvent.VK_ENTER);
      verify(fixture.project(), times(1)).setTool(any(Tool.class));
      assertTrue(descendants(fixture.palette(), ComponentTile.class).isEmpty());
      assertTrue(descendants(fixture.palette(), javax.swing.JButton.class).stream()
          .anyMatch(button -> button.getActionListeners().length > 0));
      filter.clear();
      assertTrue(descendants(fixture.palette(), ComponentTile.class).size() > 2,
          "a recent group should be updated immediately");
      fixture.palette().dispose();
    });
  }

  @Test
  void oneTileIsInTabOrderAndUpEntersAtLastVisibleResult() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var fixture = fixture();
      final var filter = descendants(fixture.palette(), FilterField.class).get(0);
      press(filter, KeyEvent.VK_UP);
      final var tiles = descendants(fixture.palette(), ComponentTile.class);
      assertEquals(1, tiles.stream().filter(Component::isFocusable).count());
      assertTrue(tiles.getLast().isFocusable());
      press(filter, KeyEvent.VK_DOWN);
      assertTrue(tiles.getFirst().isFocusable());
      fixture.palette().dispose();
    });
  }

  @Test
  void arrowsUseActualRowsAcrossSectionsAndHomeEndReachBounds() {
    final var rows = List.of(new Rectangle(0, 0, 100, 80), new Rectangle(110, 0, 100, 80),
        new Rectangle(0, 80, 100, 80), new Rectangle(0, 200, 210, 80));
    assertEquals(1, ComponentPalette.navigationTarget(rows, 0, KeyEvent.VK_RIGHT));
    assertEquals(2, ComponentPalette.navigationTarget(rows, 1, KeyEvent.VK_DOWN));
    assertEquals(3, ComponentPalette.navigationTarget(rows, 2, KeyEvent.VK_DOWN));
    assertEquals(2, ComponentPalette.navigationTarget(rows, 3, KeyEvent.VK_UP));
    assertEquals(0, ComponentPalette.navigationTarget(rows, 3, KeyEvent.VK_HOME));
    assertEquals(3, ComponentPalette.navigationTarget(rows, 0, KeyEvent.VK_END));
  }

  @Test
  void removalUnsubscribesPaletteAndRebuildsDoNotSubscribeTilesOrSections() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var fixture = fixture(); // Initialize process-wide preview generation before the mock.
      try (final var theme = mockStatic(Theme.class)) {
        fixture.palette().addNotify();
        fixture.palette().updateStructure();
        fixture.palette().updateStructure();
        final var listener = ArgumentCaptor.forClass(Runnable.class);
        theme.verify(() -> Theme.addListener(listener.capture()), times(1));
        fixture.palette().removeNotify();
        theme.verify(() -> Theme.removeListener(listener.getValue()), times(1));
        verify(fixture.project()).removeProjectListener(fixture.palette());
        verify(fixture.project()).removeLibraryListener(fixture.palette());
        assertFalse(fixture.palette().isDisplayable());
        fixture.palette().dispose();
        theme.verify(() -> Theme.removeListener(listener.getValue()), times(1));
      }
    });
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell.palette;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.shell.FilterField;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Tool;
import java.awt.Component;
import java.awt.Container;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ComponentPaletteTest {
  private record Fixture(Project project, ComponentPalette palette, AddTool and, AddTool mux,
                         List<List<String>> savedRecents) {}

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
    final var palette = new ComponentPalette(project, List.of(), List.of(),
        unused -> {}, values -> savedRecents.add(List.copyOf(values)));
    return new Fixture(project, palette, and, mux, savedRecents);
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

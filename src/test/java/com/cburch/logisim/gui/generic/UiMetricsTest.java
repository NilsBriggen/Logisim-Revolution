/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.generic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.UiFonts;
import com.cburch.logisim.util.UiScale;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import javax.swing.JLabel;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

class UiMetricsTest {

  @Test
  void attributeTitleAndRowsRefreshWithoutRescalingFontsOrChangingTheModel() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var originalScale = UiScale.factor();
      final var originalFont = UIManager.get("Label.font");
      final var model = mock(AttrTableModel.class);
      final var row = mock(AttrTableModelRow.class);
      when(model.getTitle()).thenReturn("MixedCaseDevice");
      when(model.getRowCount()).thenReturn(1);
      when(model.getRow(0)).thenReturn(row);
      when(row.getLabel()).thenReturn("Label");
      when(row.getValue()).thenReturn("Unchanged");
      final var panel = new AttrTable(null);
      panel.setAttrTableModel(model);
      panel.addNotify();
      try {
        final var table = find(panel, JTable.class);
        final var title = findTitle(panel, "MixedCaseDevice");
        for (final var scale : new double[] {1.0, 1.6, 2.0, 1.0}) {
          UiScale.setFactor(scale);
          UIManager.put("Label.font",
              new Font(Font.DIALOG, Font.PLAIN, (int) Math.round(13 * scale)));
          panel.updateUI();
          Theme.fireChanged();
          assertEquals(UiFonts.heading(), title.getFont());
          assertEquals(UiFonts.body(), table.getFont());
          final var cell = table.prepareRenderer(table.getCellRenderer(0, 0), 0, 0);
          assertEquals(UiFonts.body(), cell.getFont());
          assertTrue(table.getRowHeight() >= table.getFontMetrics(table.getFont()).getHeight()
              + 2 * Spacing.xs());
          assertTrue(table.getRowHeight() >= UiScale.iconSize());
          assertSame(model, panel.getAttrTableModel());
          assertEquals("Unchanged", row.getValue());
        }
      } finally {
        panel.removeNotify();
        LocaleManager.removeLocaleListener(panel);
        UIManager.put("Label.font", originalFont);
        UiScale.setFactor(originalScale);
      }
    });
  }

  @Test
  void explorerRowsFitIconsAndTextAndRepeatedRenderingDoesNotGrowTheFont() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var originalScale = UiScale.factor();
      final var originalFont = UIManager.get("Label.font");
      final var project = mock(Project.class);
      final var file = mock(LogisimFile.class);
      when(project.getLogisimFile()).thenReturn(file);
      when(file.getDisplayName()).thenReturn("Library");
      final var tree = new ProjectExplorer(project, false);
      // Keep this headless tree undisplayable: a lightweight peer without a heavyweight
      // ancestor cannot safely reinstall AWT mouse listeners during updateUI.
      try {
        final var root = tree.getModel().getRoot();
        for (final var scale : new double[] {1.0, 1.6, 2.0, 1.0}) {
          UiScale.setFactor(scale);
          UIManager.put("Label.font",
              new Font(Font.DIALOG, Font.PLAIN, (int) Math.round(13 * scale)));
          tree.updateUI();
          Theme.fireChanged();
          final var renderer = tree.getCellRenderer();
          for (var pass = 0; pass < 3; pass++) {
            final var cell = (JLabel) renderer.getTreeCellRendererComponent(
                tree, root, false, true, false, 0, false);
            assertEquals(UiFonts.body(), cell.getFont());
            assertTrue(tree.getRowHeight() >= cell.getIcon().getIconHeight());
            assertTrue(tree.getRowHeight()
                >= cell.getFontMetrics(cell.getFont()).getHeight() + Spacing.xs());
          }
          assertTrue(tree.getRowHeight() >= UiScale.scaled(AppPreferences.BOX_SIZE));
        }
      } finally {
        LocaleManager.removeLocaleListener(tree);
        UIManager.put("Label.font", originalFont);
        UiScale.setFactor(originalScale);
      }
    });
  }

  private static <T extends Component> T find(Container root, Class<T> type) {
    for (final var child : root.getComponents()) {
      if (type.isInstance(child)) return type.cast(child);
      if (child instanceof Container container) {
        final var result = find(container, type);
        if (result != null) return result;
      }
    }
    return null;
  }

  private static JLabel findTitle(Container root, String text) {
    for (final var child : root.getComponents()) {
      if (child instanceof JLabel label && text.equals(label.getText())) return label;
      if (child instanceof Container container) {
        final var result = findTitle(container, text);
        if (result != null) return result;
      }
    }
    return null;
  }
}

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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitAttributes;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import java.awt.Component;
import java.awt.Container;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class CircuitListViewTest {

  @Test
  void projectSelectionAndLibraryChangesSurviveGarbageCollection() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var file = LogisimFile.createNew(new Loader(null), null);
      file.stopAutosaveThread(false);
      final var project = new Project(file);
      final var view = new CircuitListView(project);
      try {
        final var list = findList(view);
        final var main = project.getCurrentCircuit();
        final var second = new Circuit("second", file, project);
        file.addCircuit(second);
        assertEquals(2, list.getModel().getSize());
        System.gc();
        project.setCurrentCircuit(second);
        assertEquals(1, list.getSelectedIndex());
        final var state = project.getCircuitState();
        second.getStaticAttributes().setValue(CircuitAttributes.NAME_ATTR, "renamed");
        assertTrue(rowText(list, 1).contains("renamed"));
        assertSame(state, project.getCircuitState(),
            "rebuilding must not activate a root instance");
        project.setCurrentCircuit(main);
        assertEquals(0, list.getSelectedIndex());
        file.removeCircuit(second);
        assertEquals(1, list.getModel().getSize());
      } finally {
        view.removeNotify();
        project.getSimulator().shutDown();
      }
    });
  }

  @Test
  void detachedStaleRowsCannotOpenDeletedCircuitsAndReattachingRefreshesTheList()
      throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var file = LogisimFile.createNew(new Loader(null), null);
      file.stopAutosaveThread(false);
      final var project = new Project(file);
      final var view = new CircuitListView(project);
      try {
        final var main = project.getCurrentCircuit();
        final var second = new Circuit("second", file, project);
        file.addCircuit(second);
        final var list = findList(view);
        view.removeNotify();
        file.removeCircuit(second);
        assertEquals(2, list.getModel().getSize(), "detached view must stop listening");
        list.setSelectedIndex(1);
        assertSame(main, project.getCurrentCircuit(), "deleted row reopened a ghost circuit");
        view.addNotify();
        assertEquals(1, list.getModel().getSize());
        assertEquals(0, list.getSelectedIndex());
        file.addCircuit(second);
        assertEquals(2, list.getModel().getSize(), "reattached view did not resume listening");
      } finally {
        view.removeNotify();
        project.getSimulator().shutDown();
      }
    });
  }

  private static JList<?> findList(Container parent) {
    for (final Component child : parent.getComponents()) {
      if (child instanceof JList<?> list) return list;
      if (child instanceof Container container) {
        final var found = findList(container);
        if (found != null) return found;
      }
    }
    return null;
  }

  private static <T> String rowText(JList<T> list, int index) {
    return ((JLabel) list.getCellRenderer().getListCellRendererComponent(
        list, list.getModel().getElementAt(index), index, false, false)).getText();
  }
}

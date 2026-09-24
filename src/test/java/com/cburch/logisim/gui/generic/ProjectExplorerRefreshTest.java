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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.AddTool;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.TreePath;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProjectExplorerRefreshTest {
  private static JTree createTree() {
    final var project = mock(Project.class);
    final var file = mock(LogisimFile.class);
    when(project.getLogisimFile()).thenReturn(file);
    when(file.getTools()).thenReturn(List.of(mock(AddTool.class)));
    when(file.getLibraries()).thenReturn(List.of());
    final var tree = new JTree();
    tree.setModel(new ProjectExplorerModel(project, tree, false));
    return tree;
  }

  @Test
  void queuedRefreshEventsUseModelNodesRatherThanLibraryUserObjects() throws Exception {
    final var treeRef = new AtomicReference<JTree>();
    final var listener = mock(TreeModelListener.class);
    SwingUtilities.invokeAndWait(() -> {
      final var tree = createTree();
      treeRef.set(tree);
      tree.getModel().addTreeModelListener(listener);
      ((ProjectExplorerModel) tree.getModel()).updateStructure();
    });
    SwingUtilities.invokeAndWait(() -> {
      final var events = ArgumentCaptor.forClass(TreeModelEvent.class);
      verify(listener).treeNodesChanged(events.capture());
      verify(listener).treeStructureChanged(events.capture());
      for (final var event : events.getAllValues()) {
        assertEquals(1, event.getPath().length);
        assertSame(treeRef.get().getModel().getRoot(), event.getTreePath().getLastPathComponent());
      }
    });
  }

  @Test
  void repeatedQueuedRefreshAndUiReinstallKeepTheRootExpandableAndChildrenVisible()
      throws Exception {
    final var treeRef = new AtomicReference<JTree>();
    SwingUtilities.invokeAndWait(() -> treeRef.set(createTree()));
    for (var pass = 0; pass < 3; pass++) {
      SwingUtilities.invokeAndWait(() ->
          ((ProjectExplorerModel) treeRef.get().getModel()).updateStructure());
      // A separate EDT turn lets the queued structure event arrive before the LAF UI reinstall.
      SwingUtilities.invokeAndWait(() -> {
        final var tree = treeRef.get();
        final var root = tree.getModel().getRoot();
        final var path = new TreePath(root);
        assertEquals(1, tree.getModel().getChildCount(root));
        assertFalse(tree.getModel().isLeaf(root));
        assertTrue(tree.isExpanded(path), "refresh lost the real root's expansion state");
        tree.updateUI();
        assertEquals(2, tree.getRowCount(), "UI reinstall hid the existing child");
        tree.collapsePath(path);
        assertEquals(1, tree.getRowCount());
        tree.expandPath(path);
        assertEquals(2, tree.getRowCount());
      });
    }
  }
}

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

import com.cburch.logisim.gui.shell.EditorTabModel.Kind;
import com.cburch.logisim.gui.shell.EditorTabModel.Tab;
import java.awt.BorderLayout;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class EditorTabsTest {

  @Test
  void cyclingWrapsFromCanvasListFilterAndCodeFocusWithoutConsumingPlainTab() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var model = new EditorTabModel();
      final var activated = new ArrayList<Tab>();
      model.setNavigator(activated::add);
      final var tabs = new EditorTabs(model, tab -> "A circuit with a long name");
      for (var index = 0; index < 20; index++) {
        model.syncTo(new Tab(index == 19 ? Kind.HDL : Kind.LAYOUT, new Object()));
      }
      assertEquals(JTabbedPane.SCROLL_TAB_LAYOUT, tabs.getTabLayoutPolicy());
      final var root = new JRootPane();
      final var content = new JPanel(new BorderLayout());
      root.setContentPane(content);
      content.add(tabs, BorderLayout.NORTH);
      final var focusArea = new JPanel();
      content.add(focusArea);
      for (final var focus : new javax.swing.JComponent[] {
          new JPanel(), new JList<>(), new JTextField(), new JTextArea()
      }) {
        focusArea.add(focus);
        model.syncTo(model.tabs().get(19));
        activated.clear();
        assertTrue(tabs.dispatchTabKey(key(focus, InputEvent.CTRL_DOWN_MASK)));
        assertEquals(0, model.selectedIndex());
        assertEquals(1, activated.size());
        assertTrue(tabs.dispatchTabKey(new KeyEvent(focus, KeyEvent.KEY_TYPED, 2,
            InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_UNDEFINED, '\t')));
        assertTrue(tabs.dispatchTabKey(new KeyEvent(focus, KeyEvent.KEY_RELEASED, 3,
            InputEvent.CTRL_DOWN_MASK, KeyEvent.VK_TAB, '\t')));
        assertEquals(1, activated.size(), "typed/released events must not cycle again");
        assertTrue(tabs.dispatchTabKey(key(focus,
            InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK)));
        assertEquals(19, model.selectedIndex());
        assertEquals(19, tabs.getSelectedIndex());
        assertFalse(tabs.dispatchTabKey(key(focus, 0)), "plain Tab must still traverse");
      }
      final var otherWindow = new JRootPane();
      final var otherEditor = new JTextField();
      otherWindow.getContentPane().add(otherEditor);
      assertFalse(tabs.dispatchTabKey(key(otherEditor, InputEvent.CTRL_DOWN_MASK)));
      assertEquals(19, model.selectedIndex());
      tabs.refresh();
      assertEquals(19, tabs.getSelectedIndex());
      assertEquals(20, tabs.getTabCount());
    });
  }

  private static KeyEvent key(java.awt.Component source, int modifiers) {
    return new KeyEvent(source, KeyEvent.KEY_PRESSED, 1, modifiers,
        KeyEvent.VK_TAB, '\t');
  }
}

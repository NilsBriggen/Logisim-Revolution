/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.gui.shell.EditorTabModel.Kind;
import com.cburch.logisim.gui.shell.EditorTabModel.Tab;
import com.cburch.logisim.gui.theme.AppIcons;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.GraphicsEnvironment;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Function;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

/**
 * The strip of tabs above the canvas, one per circuit the user has opened.
 *
 * <p>The tabs are a view of {@link EditorTabModel} and hold no state of their own; every rule
 * about which tab is showing and what closing one does lives in that class.
 */
public class EditorTabs extends JTabbedPane {

  private static final long serialVersionUID = 1L;

  /** Marks a tab whose circuit has unsaved changes. */
  private static final String DIRTY_MARK = "\u25cf ";

  private final EditorTabModel model;
  private final Function<Tab, String> titler;
  private final KeyEventDispatcher tabDispatcher = this::dispatchTabKey;

  /** Set while this class is the one changing the selection, so the change is not sent back. */
  private boolean syncing;

  private static final String NEXT_TAB = "nextEditorTab";
  private static final String PREVIOUS_TAB = "previousEditorTab";
  private static final String CLOSE_TAB = "closeEditorTab";

  /**
   * @param titler supplies a tab's label; asked again whenever the strip is rebuilt, so renaming
   *     a circuit needs no bookkeeping here
   */
  public EditorTabs(EditorTabModel model, Function<Tab, String> titler) {
    this.model = model;
    this.titler = titler;
    setTabLayoutPolicy(SCROLL_TAB_LAYOUT);
    putClientProperty(FlatClientProperties.TABBED_PANE_TAB_CLOSABLE, true);
    putClientProperty(FlatClientProperties.TABBED_PANE_SCROLL_BUTTONS_POLICY,
        FlatClientProperties.TABBED_PANE_POLICY_AS_NEEDED);
    putClientProperty(
        FlatClientProperties.TABBED_PANE_TAB_CLOSE_CALLBACK,
        (java.util.function.IntConsumer) model::close);
    installKeyBindings();
    installContextMenu();

    addChangeListener(
        event -> {
          if (!syncing) model.select(getSelectedIndex());
        });
    // The middle button closes a tab, as it does in every editor with tabs.
    addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent event) {
            if (event.getButton() != MouseEvent.BUTTON2) return;
            final var index = indexAtLocation(event.getX(), event.getY());
            if (index >= 0) model.close(index);
          }
        });

    model.setListener(ignored -> rebuild());
    rebuild();
  }

  /**
   * The keys an editor's tab strip is expected to answer.
   *
   * <p>With several circuits open there was no way to switch, cycle or close a tab without the
   * mouse: Ctrl+Tab and Ctrl+W did nothing, and Ctrl+Shift+W closed the whole project window.
   */
  private void installKeyBindings() {
    final var input = getInputMap(WHEN_IN_FOCUSED_WINDOW);
    final var actions = getActionMap();
    final var menuMask = GraphicsEnvironment.isHeadless() ? InputEvent.CTRL_DOWN_MASK
        : java.awt.Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    input.put(KeyStroke.getKeyStroke(KeyEvent.VK_TAB, InputEvent.CTRL_DOWN_MASK), NEXT_TAB);
    input.put(
        KeyStroke.getKeyStroke(KeyEvent.VK_TAB,
            InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK),
        PREVIOUS_TAB);
    input.put(KeyStroke.getKeyStroke(KeyEvent.VK_W, menuMask), CLOSE_TAB);
    actions.put(NEXT_TAB, action(() -> step(1)));
    actions.put(PREVIOUS_TAB, action(() -> step(-1)));
    actions.put(CLOSE_TAB, action(this::closeCurrent));
  }

  @Override
  public void addNotify() {
    super.addNotify();
    KeyboardFocusManager.getCurrentKeyboardFocusManager()
        .addKeyEventDispatcher(tabDispatcher);
  }

  @Override
  public void removeNotify() {
    KeyboardFocusManager.getCurrentKeyboardFocusManager()
        .removeKeyEventDispatcher(tabDispatcher);
    super.removeNotify();
  }

  /** Routes editor cycling before Swing consumes Ctrl+Tab as a focus-traversal key. */
  boolean dispatchTabKey(KeyEvent event) {
    final var tabKey = event.getKeyCode() == KeyEvent.VK_TAB
        || (event.getID() == KeyEvent.KEY_TYPED && event.getKeyChar() == '\t');
    if (event.isConsumed() || !tabKey || !event.isControlDown()
        || event.isAltDown() || event.isMetaDown() || !isVisible() || model.count() == 0) {
      return false;
    }
    final var root = getRootPane();
    if (root == null || event.getComponent() == null
        || SwingUtilities.getRootPane(event.getComponent()) != root) return false;
    if (event.getID() == KeyEvent.KEY_PRESSED) {
      final var key = event.isShiftDown() ? PREVIOUS_TAB : NEXT_TAB;
      getActionMap().get(key).actionPerformed(
          new java.awt.event.ActionEvent(this, java.awt.event.ActionEvent.ACTION_PERFORMED, key));
    }
    event.consume();
    return true;
  }

  private static javax.swing.Action action(Runnable body) {
    return new javax.swing.AbstractAction() {
      private static final long serialVersionUID = 1L;

      @Override
      public void actionPerformed(java.awt.event.ActionEvent event) {
        body.run();
      }
    };
  }

  /** Moves to the next or previous tab, wrapping round. */
  private void step(int delta) {
    final var count = getTabCount();
    if (count <= 1) return;
    model.select(Math.floorMod(getSelectedIndex() + delta, count));
  }

  private void closeCurrent() {
    final var index = getSelectedIndex();
    if (index >= 0) model.close(index);
  }

  /** Close, Close Others and Close All, on the tab under the pointer. */
  private void installContextMenu() {
    addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent event) {
            showMenu(event);
          }

          @Override
          public void mouseReleased(MouseEvent event) {
            showMenu(event);
          }

          private void showMenu(MouseEvent event) {
            if (!event.isPopupTrigger()) return;
            final var index = indexAtLocation(event.getX(), event.getY());
            if (index < 0) return;
            final var menu = new javax.swing.JPopupMenu();
            menu.add(item(S.get("editorTabClose"), () -> model.close(index)));
            menu.add(item(S.get("editorTabCloseOthers"), () -> closeOthers(index)));
            menu.add(item(S.get("editorTabCloseAll"), () -> closeAll()));
            menu.show(EditorTabs.this, event.getX(), event.getY());
          }
        });
  }

  private static javax.swing.JMenuItem item(String text, Runnable body) {
    final var menuItem = new javax.swing.JMenuItem(text);
    menuItem.addActionListener(event -> body.run());
    return menuItem;
  }

  /** Closed from the right so the earlier indexes stay valid as tabs go. */
  private void closeOthers(int keep) {
    model.select(keep);
    for (var index = getTabCount() - 1; index >= 0; index--) {
      if (index != keep) model.close(index);
    }
  }

  private void closeAll() {
    for (var index = getTabCount() - 1; index >= 0; index--) {
      model.close(index);
    }
  }

  /** Rebuilds the strip from the model. Cheap: the tabs hold a placeholder, not an editor. */
  private void rebuild() {
    syncing = true;
    try {
      final var tabs = model.tabs();
      while (getTabCount() > tabs.size()) removeTabAt(getTabCount() - 1);
      for (var index = 0; index < tabs.size(); index++) {
        final var tab = tabs.get(index);
        final var prefix = model.isDirty(tab) ? DIRTY_MARK : "";
        final var title = prefix + titler.apply(tab);
        if (index == getTabCount()) {
          addTab(title, iconFor(tab), null, tooltipFor(tab));
        } else {
          setTitleAt(index, title);
          setIconAt(index, iconFor(tab));
          setToolTipTextAt(index, tooltipFor(tab));
        }
      }
      final var selected = model.selectedIndex();
      if (selected >= 0 && selected < getTabCount()) setSelectedIndex(selected);
      setVisible(getTabCount() > 0);
    } finally {
      syncing = false;
    }
  }

  private static javax.swing.Icon iconFor(Tab tab) {
    return AppIcons.get(
        switch (tab.kind()) {
          case LAYOUT -> AppIcons.Id.CIRCUIT;
          case APPEARANCE -> AppIcons.Id.APPEARANCE;
          case HDL -> AppIcons.Id.HDL;
        },
        14);
  }

  private String tooltipFor(Tab tab) {
    final var name = titler.apply(tab);
    return tab.kind() == Kind.APPEARANCE ? name + " \u2014 appearance" : name;
  }

  /** Re-reads the labels, after a circuit was renamed or the language changed. */
  public void refresh() {
    rebuild();
  }

  @Override
  public JComponent getComponentAt(int index) {
    // The tabs never hold content: one canvas sits below the strip and the tabs choose what it
    // shows. Returning null keeps Swing from laying out an empty page behind it.
    return null;
  }
}

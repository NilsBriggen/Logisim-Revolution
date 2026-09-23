/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell;

import com.cburch.logisim.gui.shell.EditorTabModel.Kind;
import com.cburch.logisim.gui.shell.EditorTabModel.Tab;
import com.cburch.logisim.gui.theme.AppIcons;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Function;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;

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

  /** Set while this class is the one changing the selection, so the change is not sent back. */
  private boolean syncing;

  /**
   * @param titler supplies a tab's label; asked again whenever the strip is rebuilt, so renaming
   *     a circuit needs no bookkeeping here
   */
  public EditorTabs(EditorTabModel model, Function<Tab, String> titler) {
    this.model = model;
    this.titler = titler;
    putClientProperty(FlatClientProperties.TABBED_PANE_TAB_CLOSABLE, true);
    putClientProperty(FlatClientProperties.TABBED_PANE_SCROLL_BUTTONS_POLICY,
        FlatClientProperties.TABBED_PANE_POLICY_AS_NEEDED);
    putClientProperty(
        FlatClientProperties.TABBED_PANE_TAB_CLOSE_CALLBACK,
        (java.util.function.IntConsumer) model::close);
    setFocusable(false);

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

  /** Rebuilds the strip from the model. Cheap: the tabs hold a placeholder, not an editor. */
  private void rebuild() {
    syncing = true;
    try {
      removeAll();
      for (final var tab : model.tabs()) {
        final var prefix = model.isDirty(tab) ? DIRTY_MARK : "";
        addTab(prefix + titler.apply(tab), iconFor(tab), null, tooltipFor(tab));
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

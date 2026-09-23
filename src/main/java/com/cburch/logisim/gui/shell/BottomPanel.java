/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell;

import com.formdev.flatlaf.FlatClientProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;

/**
 * The drawer under the canvas, for output the user watches while a circuit runs: the VHDL console
 * now, the log and timing views later.
 *
 * <p>Separate windows for these meant arranging windows before being able to watch a simulation
 * and a waveform at the same time.
 */
public class BottomPanel extends JTabbedPane {

  private static final long serialVersionUID = 1L;

  private final Map<String, JComponent> panels = new LinkedHashMap<>();

  public BottomPanel() {
    putClientProperty(FlatClientProperties.TABBED_PANE_TAB_TYPE,
        FlatClientProperties.TABBED_PANE_TAB_TYPE_CARD);
    putClientProperty(FlatClientProperties.TABBED_PANE_SCROLL_BUTTONS_POLICY,
        FlatClientProperties.TABBED_PANE_POLICY_AS_NEEDED);
  }

  /** Adds a panel, or replaces the one already registered under {@code id}. */
  public void addPanel(String id, String title, javax.swing.Icon icon, JComponent panel) {
    final var existing = panels.put(id, panel);
    if (existing != null) {
      final var index = indexOfComponent(existing);
      if (index >= 0) remove(index);
    }
    addTab(title, icon, panel);
  }

  /** Brings the panel registered under {@code id} to the front. */
  public boolean showPanel(String id) {
    final var panel = panels.get(id);
    if (panel == null) return false;
    final var index = indexOfComponent(panel);
    if (index < 0) return false;
    setSelectedIndex(index);
    return true;
  }

  public boolean hasPanels() {
    return getTabCount() > 0;
  }

  /** Renames the registered panels, after a change of language. */
  public void setTitle(String id, String title) {
    final var panel = panels.get(id);
    if (panel == null) return;
    final var index = indexOfComponent(panel);
    if (index >= 0) setTitleAt(index, title);
  }
}

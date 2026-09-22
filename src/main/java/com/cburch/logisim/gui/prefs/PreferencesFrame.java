/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.prefs;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.fpga.prefs.FpgaOptions;
import com.cburch.logisim.fpga.prefs.SoftwaresOptions;
import com.cburch.logisim.gui.generic.LFrame;
import com.cburch.logisim.util.LocaleListener;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.WindowMenuItemManager;
import com.cburch.logisim.util.StringGetter;
import java.awt.BorderLayout;
import java.util.List;
import java.util.function.Function;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;

public class PreferencesFrame extends LFrame.Dialog {

  private static final long serialVersionUID = 1L;

  /**
   * One tab of the preferences window: its title, and how to build its panel.
   *
   * <p>Described statically so that the tabs can be listed, by the search dialog for one, without
   * building the window, which needs a display. The title must agree with the panel's own {@code
   * getTitle()}.
   */
  record Tab(StringGetter title, Function<PreferencesFrame, OptionsPanel> factory) {}

  /** The tabs, in display order. */
  static final List<Tab> TABS =
      List.of(
          new Tab(S.getter("templateTitle"), TemplateOptions::new),
          new Tab(S.getter("intlTitle"), IntlOptions::new),
          new Tab(S.getter("windowTitle"), WindowOptions::new),
          new Tab(S.getter("layoutTitle"), LayoutOptions::new),
          new Tab(S.getter("simTitle"), SimOptions::new),
          new Tab(S.getter("experimentTitle"), ExperimentalOptions::new),
          new Tab(com.cburch.logisim.fpga.Strings.S.getter("softwaresTitle"), SoftwaresOptions::new),
          new Tab(com.cburch.logisim.fpga.Strings.S.getter("FPGATitle"), FpgaOptions::new),
          new Tab(S.getter("hotkeyOptTitle"), HotkeyOptions::new),
          new Tab(S.getter("autosaveTitle"), AutosaveOptions::new));

  private static WindowMenuManager MENU_MANAGER = null;
  private final MyListener myListener = new MyListener();
  private final OptionsPanel[] panels;
  private final JTabbedPane tabbedPane;
  private int fpgaTabIdx = -1;

  private PreferencesFrame() {
    super(null);

    panels = TABS.stream().map(tab -> tab.factory().apply(this)).toArray(OptionsPanel[]::new);
    tabbedPane = new JTabbedPane();
    int intlIndex = -1;
    for (var index = 0; index < panels.length; index++) {
      final var panel = panels[index];
      tabbedPane.addTab(panel.getTitle(), null, panel, panel.getToolTipText());
      if (panel instanceof IntlOptions) intlIndex = index;
      if (panel instanceof FpgaOptions) fpgaTabIdx = index;
    }

    final var contents = getContentPane();
    contents.add(new JScrollPane(tabbedPane), BorderLayout.CENTER);

    if (intlIndex >= 0) tabbedPane.setSelectedIndex(intlIndex);

    LocaleManager.addLocaleListener(myListener);
    myListener.localeChanged();
    pack();
  }

  public static void initializeManager() {
    MENU_MANAGER = new WindowMenuManager();
  }

  public static void showPreferences() {
    final var frame = MENU_MANAGER.getJFrame(true, null);
    frame.setVisible(true);
  }

  /** The localized title of every tab, in display order, without building the window. */
  public static List<String> getTabTitles() {
    return TABS.stream().map(tab -> tab.title().toString()).toList();
  }

  /** Shows the preferences window open at the tab at {@code index} in {@link #getTabTitles()}. */
  public static void showPreferences(int index) {
    final var frame = (PreferencesFrame) MENU_MANAGER.getJFrame(true, null);
    if (index >= 0 && index < frame.tabbedPane.getTabCount()) {
      frame.tabbedPane.setSelectedIndex(index);
    }
    frame.setVisible(true);
  }

  public static void showFPGAPreferences() {
    final var frame = (PreferencesFrame) MENU_MANAGER.getJFrame(true, null);
    frame.setFpgaTab();
    frame.setVisible(true);
  }

  public void setFpgaTab() {
    if (fpgaTabIdx < 0) return;
    tabbedPane.setSelectedIndex(fpgaTabIdx);
  }

  private static class WindowMenuManager extends WindowMenuItemManager implements LocaleListener {
    private PreferencesFrame window = null;

    WindowMenuManager() {
      super(S.get("preferencesFrameMenuItem"), true);
      LocaleManager.addLocaleListener(this);
    }

    @Override
    public JFrame getJFrame(boolean create, java.awt.Component parent) {
      if (create) {
        if (window == null) {
          window = new PreferencesFrame();
          window.setLocationRelativeTo(parent);
          frameOpened(window);
        }
      }
      return window;
    }

    @Override
    public void localeChanged() {
      setText(S.get("preferencesFrameMenuItem"));
    }
  }

  private class MyListener implements LocaleListener {
    @Override
    public void localeChanged() {
      setTitle(S.get("preferencesFrameTitle"));
      for (int i = 0; i < panels.length; i++) {
        tabbedPane.setTitleAt(i, panels[i].getTitle());
        tabbedPane.setToolTipTextAt(i, panels[i].getToolTipText());
        panels[i].localeChanged();
      }
    }
  }
}

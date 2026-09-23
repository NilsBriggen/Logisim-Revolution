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
import com.cburch.logisim.gui.shell.PanelHeader;
import com.cburch.logisim.gui.shell.SettingsNav;
import com.cburch.logisim.prefs.AppPreferences;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.util.List;
import java.util.function.Function;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

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
  private final SettingsNav nav;
  private final CardLayout pages = new CardLayout();
  private final JPanel pageHolder = new JPanel(pages);
  private final PanelHeader pageHeader = new PanelHeader("");
  private int fpgaTabIdx = -1;
  private int selectedIndex = -1;

  private PreferencesFrame() {
    super(null);

    panels = TABS.stream().map(tab -> tab.factory().apply(this)).toArray(OptionsPanel[]::new);
    var intlIndex = -1;
    for (var index = 0; index < panels.length; index++) {
      final var panel = panels[index];
      pageHolder.add(new JScrollPane(panel), String.valueOf(index));
      if (panel instanceof IntlOptions) intlIndex = index;
      if (panel instanceof FpgaOptions) fpgaTabIdx = index;
    }

    nav = new SettingsNav(S.get("preferencesFilterHint"), this::showPage);
    final var navWidth = AppPreferences.getScaled(NAV_WIDTH);
    nav.setPreferredSize(new Dimension(navWidth, 0));
    nav.setMinimumSize(new Dimension(navWidth, 0));

    final var page = new JPanel(new BorderLayout());
    page.add(pageHeader, BorderLayout.NORTH);
    page.add(pageHolder, BorderLayout.CENTER);

    final var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, nav, page);
    split.setBorder(null);
    split.setDividerSize(1);
    split.setResizeWeight(0.0);

    getContentPane().add(split, BorderLayout.CENTER);
    setPreferredSize(
        new Dimension(AppPreferences.getScaled(WINDOW_WIDTH), AppPreferences.getScaled(WINDOW_HEIGHT)));

    LocaleManager.addLocaleListener(myListener);
    myListener.localeChanged();
    showPage(intlIndex >= 0 ? intlIndex : 0);
    pack();
    // After packing: a split pane clamps a divider position to its own width, which it does not
    // have until the window has been laid out.
    split.setDividerLocation(navWidth);
  }

  /** Unscaled width of the page list. */
  private static final int NAV_WIDTH = 190;

  /** Unscaled size the window opens at. */
  private static final int WINDOW_WIDTH = 820;

  private static final int WINDOW_HEIGHT = 560;

  /** Shows the page at {@code index} in {@link #TABS} order. */
  private void showPage(int index) {
    if (index < 0 || index >= panels.length) return;
    selectedIndex = index;
    pages.show(pageHolder, String.valueOf(index));
    pageHeader.setTitle(panels[index].getTitle());
    nav.setSelectedIndex(index);
  }

  /** Builds a window without showing it, for the development snapshot tool. */
  public static PreferencesFrame buildForSnapshot() {
    return new PreferencesFrame();
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
    frame.showPage(index);
    frame.setVisible(true);
  }

  public static void showFPGAPreferences() {
    final var frame = (PreferencesFrame) MENU_MANAGER.getJFrame(true, null);
    frame.setFpgaTab();
    frame.setVisible(true);
  }

  public void setFpgaTab() {
    showPage(fpgaTabIdx);
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
      for (final var panel : panels) {
        panel.localeChanged();
      }
      nav.setFilterHint(S.get("preferencesFilterHint"));
      nav.setTitles(getTabTitles());
      if (selectedIndex >= 0) {
        pageHeader.setTitle(panels[selectedIndex].getTitle());
        nav.setSelectedIndex(selectedIndex);
      }
    }
  }
}

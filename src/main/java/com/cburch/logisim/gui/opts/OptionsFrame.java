/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.opts;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.file.LibraryEvent;
import com.cburch.logisim.file.LibraryListener;
import com.cburch.logisim.file.LogisimFileActions;
import com.cburch.logisim.file.Options;
import com.cburch.logisim.gui.generic.LFrame;
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.gui.generic.ScrollableForm;
import com.cburch.logisim.gui.shell.PanelHeader;
import com.cburch.logisim.gui.shell.SettingsNav;
import com.cburch.logisim.gui.theme.AppIcons;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.util.LocaleListener;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.UiScale;
import com.cburch.logisim.util.WindowMenuItemManager;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.util.Arrays;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

public class OptionsFrame extends LFrame.Dialog {
  private static final long serialVersionUID = 1L;
  private final MyListener myListener = new MyListener();
  private final WindowMenuManager windowManager = new WindowMenuManager();
  private final OptionsPanel[] panels;
  private final ScrollableForm[] forms;
  private final JSplitPane split;
  private final CardLayout pages = new CardLayout();
  private final JPanel pageHolder = new JPanel(pages);
  private final PanelHeader pageHeader = new PanelHeader("");
  private final JButton revertButton = new JButton();
  private SettingsNav nav;
  private double layoutScale = -1;

  /** Unscaled width of the page list, matching the preferences window. */
  private static final int NAV_WIDTH = 190;

  public OptionsFrame(Project project) {
    super(project);
    project.addLibraryListener(myListener);
    project.addProjectListener(
        event -> {
          final var action = event.getAction();
          if (action == ProjectEvent.ACTION_SET_STATE) {
            computeTitle();
          }
        });
    // A page list, as the preferences window already has. It was four tabs, the fourth of which
    // held a single button, in a window fixed at 450x300 whose tab names did not fit.
    panels =
        new OptionsPanel[] {
          new SimulateOptions(this), new ToolbarOptions(this), new MouseOptions(this)
        };
    forms = new ScrollableForm[panels.length];
    for (var index = 0; index < panels.length; index++) {
      forms[index] = new ScrollableForm(panels[index]);
      pageHolder.add(forms[index].createScrollPane(), String.valueOf(index));
    }

    nav = new SettingsNav(S.get("preferencesFilterHint"), this::showPage);
    pageHolder.add(nav.getEmptyState(), "empty");

    final var page = new JPanel(new BorderLayout());
    page.add(pageHeader, BorderLayout.NORTH);
    page.add(pageHolder, BorderLayout.CENTER);
    page.add(revertFooter(), BorderLayout.SOUTH);
    page.setMinimumSize(new Dimension(0, 0));

    split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, nav, page);
    split.setBorder(null);
    split.setDividerSize(1);
    split.setResizeWeight(0.0);

    getContentPane().add(split, BorderLayout.CENTER);
    LocaleManager.addLocaleListener(myListener);
    myListener.localeChanged();
    showPage(0);
    updateWindowSize(true);
    Theme.addListener(getRootPane(), () -> updateWindowSize(false));
    setLocationRelativeTo(project.getFrame());
  }

  private void updateWindowSize(boolean initial) {
    nav.setPreferredSize(new Dimension(UiScale.scaled(NAV_WIDTH), 0));
    nav.setMinimumSize(new Dimension(UiScale.scaled(120), 0));
    ScrollableForm.sizeWindow(
        this, new Dimension(720, 460), new Dimension(560, 340), initial);
    if (initial || layoutScale != UiScale.factor()) {
      split.setDividerLocation(Math.min(UiScale.scaled(NAV_WIDTH), getWidth() / 3));
    }
    layoutScale = UiScale.factor();
  }

  /**
   * The reset control, in a footer rather than a page of its own.
   *
   * <p>It throws away every setting in the project at once, so it now says so before doing it.
   */
  private JComponent revertFooter() {
    final var footer = new JPanel(new BorderLayout());
    footer.setBorder(Spacing.panelBorder());
    revertButton.setIcon(
        AppIcons.colored(AppIcons.Id.RESET, AppIcons.SIZE, Tokens.error()));
    revertButton.addActionListener(
        event -> {
          final var answer =
              OptionPane.showConfirmDialog(
                  this,
                  S.get("revertConfirmPrompt"),
                  S.get("revertConfirmTitle"),
                  OptionPane.YES_NO_OPTION);
          if (answer == OptionPane.YES_OPTION) {
            project.doAction(LogisimFileActions.revertDefaults());
          }
        });
    footer.add(revertButton);
    return footer;
  }

  private void showPage(int index) {
    if (index == -1) {
      pages.show(pageHolder, "empty");
      pageHeader.setTitle(S.get("preferencesFilterHint"));
      pageHeader.setSubtitle("");
      return;
    }
    if (index < 0 || index >= panels.length) return;
    pages.show(pageHolder, String.valueOf(index));
    pageHeader.setTitle(panels[index].getTitle());
    pageHeader.setSubtitle("");
    nav.setSelectedIndex(index);
    nav.revealMatch(panels[index]);
  }

  static SettingsNav.SearchPage searchPage(OptionsPanel panel) {
    return new SettingsNav.SearchPage(
        panel.getTitle(), panel.getHelpText() + " " + SettingsNav.searchableText(panel));
  }

  private void computeTitle() {
    final var file = project.getLogisimFile();
    final var name = (file == null) ? "???" : file.getName();
    final var title = S.get("optionsFrameTitle", name);
    setTitle(title);
  }

  public Options getOptions() {
    return project.getLogisimFile().getOptions();
  }

  OptionsPanel[] getPrefPanels() {
    return panels;
  }

  @Override
  public void setVisible(boolean value) {
    if (value) {
      windowManager.frameOpened(this);
    }
    super.setVisible(value);
  }

  private class MyListener implements LibraryListener, LocaleListener {
    @Override
    public void libraryChanged(LibraryEvent event) {
      if (event.getAction() == LibraryEvent.SET_NAME) {
        computeTitle();
        windowManager.localeChanged();
      }
    }

    @Override
    public void localeChanged() {
      computeTitle();
      for (var index = 0; index < panels.length; index++) {
        panels[index].localeChanged();
        forms[index].setHelpText(panels[index].getHelpText());
      }
      revertButton.setText(S.get("revertButton"));
      revertButton.setToolTipText(S.get("revertButton"));
      nav.setFilterHint(S.get("preferencesFilterHint"));
      nav.setPages(Arrays.stream(panels).map(OptionsFrame::searchPage).toList());
      windowManager.localeChanged();
    }
  }

  private class WindowMenuManager extends WindowMenuItemManager implements LocaleListener {
    WindowMenuManager() {
      super(S.get("optionsFrameMenuItem"), false);
    }

    @Override
    public JFrame getJFrame(boolean create, java.awt.Component parent) {
      return OptionsFrame.this;
    }

    @Override
    public void localeChanged() {
      final var title = project.getLogisimFile().getDisplayName();
      setText(S.get("optionsFrameMenuItem", title));
    }
  }
}

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
import com.cburch.logisim.gui.shell.PanelHeader;
import com.cburch.logisim.gui.shell.SettingsNav;
import com.cburch.logisim.gui.theme.AppIcons;
import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.util.LocaleListener;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.TableLayout;
import com.cburch.logisim.util.WindowMenuItemManager;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;

public class OptionsFrame extends LFrame.Dialog {
  private static final long serialVersionUID = 1L;
  private final MyListener myListener = new MyListener();
  private final WindowMenuManager windowManager = new WindowMenuManager();
  private final OptionsPanel[] panels;
  private final CardLayout pages = new CardLayout();
  private final JPanel pageHolder = new JPanel(pages);
  private final PanelHeader pageHeader = new PanelHeader("");
  private final JButton revertButton = new JButton();
  private SettingsNav nav;
  private int selectedIndex = -1;

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
    for (var index = 0; index < panels.length; index++) {
      pageHolder.add(new JScrollPane(panels[index]), String.valueOf(index));
    }

    nav = new SettingsNav(S.get("preferencesFilterHint"), this::showPage);
    final var navWidth = AppPreferences.getScaled(NAV_WIDTH);
    nav.setPreferredSize(new Dimension(navWidth, 0));
    nav.setMinimumSize(new Dimension(navWidth, 0));

    final var page = new JPanel(new BorderLayout());
    page.add(pageHeader, BorderLayout.NORTH);
    page.add(pageHolder, BorderLayout.CENTER);
    page.add(revertFooter(), BorderLayout.SOUTH);

    final var split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, nav, page);
    split.setBorder(null);
    split.setDividerSize(1);
    split.setResizeWeight(0.0);

    getContentPane().add(split, BorderLayout.CENTER);
    setPreferredSize(
        new Dimension(AppPreferences.getScaled(720), AppPreferences.getScaled(460)));

    LocaleManager.addLocaleListener(myListener);
    myListener.localeChanged();
    showPage(0);
    pack();
    // After packing: a split pane clamps a divider position to its own width, which it does not
    // have until the window has been laid out.
    split.setDividerLocation(navWidth);
    setLocationRelativeTo(project.getFrame());
  }

  /**
   * The reset control, in a footer rather than a page of its own.
   *
   * <p>It throws away every setting in the project at once, so it now says so before doing it.
   */
  private JComponent revertFooter() {
    final var footer = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.TRAILING, 0, 0));
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
    if (index < 0 || index >= panels.length) return;
    selectedIndex = index;
    pages.show(pageHolder, String.valueOf(index));
    pageHeader.setTitle(panels[index].getTitle());
    pageHeader.setSubtitle(panels[index].getHelpText());
    nav.setSelectedIndex(index);
  }

  private java.util.List<String> pageTitles() {
    final var titles = new java.util.ArrayList<String>(panels.length);
    for (final var panel : panels) {
      titles.add(panel.getTitle());
    }
    return titles;
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
      for (final var panel : panels) {
        panel.localeChanged();
      }
      revertButton.setText(S.get("revertButton"));
      nav.setFilterHint(S.get("preferencesFilterHint"));
      nav.setTitles(pageTitles());
      if (selectedIndex >= 0) {
        pageHeader.setTitle(panels[selectedIndex].getTitle());
        pageHeader.setSubtitle(panels[selectedIndex].getHelpText());
        nav.setSelectedIndex(selectedIndex);
      }
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

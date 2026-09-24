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

import com.cburch.logisim.data.Direction;
import com.cburch.logisim.fpga.gui.ZoomSlider;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Projects;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.UiFonts;
import com.cburch.logisim.util.UiScale;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

class WindowOptions extends OptionsPanel {
  private static final long serialVersionUID = 1L;
  private final PrefBoolean[] checks;
  private final PrefOptionList toolbarPlacement;

  private final JButton resetWindowLayoutButton;

  private final ZoomSlider zoomValue;
  private final JButton zoomAutoButton;
  private final JLabel themeLabel;
  private final JLabel appFontLabel;
  private final JLabel restartWarning;
  private final JLabel zoomLabel;
  private final JTextArea zoomFactorImportant;
  private final JComboBox<String> themeMode;
  private final JLabel editorThemeLabel;
  private final JComboBox<String> editorTheme;
  private final JComboBox<String> appFont;

  private String initialAppFont;

  protected final String cmdResetWindowLayout = "reset-window-layout";
  protected final String cmdSetAutoScaleFactor = "set-auto-scale-factor";

  /** Kept so the theme combo can be relabelled without the relabel looking like a user choice. */
  private final SettingsChangeListener myListener = new SettingsChangeListener();

  public WindowOptions(PreferencesFrame window) {
    super(window);

    final var listener = myListener;
    final var panel = new JPanel(new GridBagLayout());

    checks =
        new PrefBoolean[] {
          new PrefBoolean(AppPreferences.UI_ANTIALIASING, S.getter("layoutAntiAliasing")),
          new PrefBoolean(AppPreferences.SHOW_TICK_RATE, S.getter("windowTickRate")),
          new PrefBoolean(
              AppPreferences.SEARCH_DOUBLE_SHIFT, S.getter("windowSearchDoubleShift")),
        };

    // Only two of the five choices ever did anything: the window puts the toolbar across the top
    // whatever is picked here, and honours "hidden". The three that did nothing are gone, and so
    // is the whole "Main canvas location" control, which had no reader anywhere in the program.
    toolbarPlacement =
        new PrefOptionList(
            AppPreferences.TOOLBAR_PLACEMENT,
            S.getter("windowToolbarLocation"),
            new PrefOption[] {
              new PrefOption(Direction.NORTH.toString(), S.getter("windowToolbarShown")),
              new PrefOption(AppPreferences.TOOLBAR_HIDDEN, S.getter("windowToolbarHidden"))
            });

    addSetting(panel, toolbarPlacement.getJLabel(), toolbarPlacement.getJComboBox(), 0);

    // The seven colour controls that were here have moved to the Colors page, which owns every
    // colour in one grouped list. They had been split across this page and Simulation, with three
    // separate reset buttons between them.

    zoomFactorImportant = new JTextArea(S.get("windowScaleHelp"));
    zoomFactorImportant.setEditable(false);
    zoomFactorImportant.setFocusable(false);
    zoomFactorImportant.setOpaque(false);
    zoomFactorImportant.setLineWrap(true);
    zoomFactorImportant.setWrapStyleWord(true);
    zoomFactorImportant.setMinimumSize(new Dimension(0, 0));
    addFullRow(panel, zoomFactorImportant, 1);

    zoomLabel = new JLabel(S.get("windowToolbarZoomfactor"));
    zoomValue =
        new ZoomSlider(
            JSlider.HORIZONTAL, 100, 300, (int) Math.round(UiScale.factor() * 100));
    zoomAutoButton = new JButton();
    zoomAutoButton.addActionListener(listener);
    zoomAutoButton.setActionCommand(cmdSetAutoScaleFactor);
    zoomAutoButton.setText(S.get("windowSetAutoScaleFactor"));
    addSetting(panel, zoomLabel, zoomValue, 2);
    addFullRow(panel, zoomAutoButton, 3);
    zoomValue.addChangeListener(listener);

    // Initialize components before adding
    themeMode = new JComboBox<>();
    for (final var mode : Theme.Mode.values()) {
      themeMode.addItem(themeModeLabel(mode));
    }
    themeMode.setSelectedIndex(Theme.currentMode().ordinal());

    editorTheme = new JComboBox<>();
    editorTheme.addItem("Default");
    editorTheme.addItem("Dark");
    editorTheme.addItem("Monokai");
    editorTheme.addItem("Eclipse");
    editorTheme.addItem("IDEA");
    editorTheme.addItem("Visual Studio");
    editorTheme.addItem("Druid");
    updateEditorThemeSelection();
    editorThemeLabel = new JLabel(S.get("windowEditorTheme"));

    appFont = new JComboBox<>();
    // Installed font names must not determine the minimum width of the entire settings page.
    // The popup still lists every full name; the selected value may elide inside the control.
    appFont.setPrototypeDisplayValue("SansSerif");
    appFont.addItem(S.get("windowAppFontDefault"));
    for (String f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
      appFont.addItem(f);
    }
    initialAppFont = AppPreferences.APP_FONT.get();
    appFont.setSelectedItem((initialAppFont == null || initialAppFont.isEmpty())
        ? S.get("windowAppFontDefault") : initialAppFont);

    appFontLabel = new JLabel(S.get("windowAppFont"));

    // Add components
    themeLabel = new JLabel(S.get("windowTheme"));
    addSetting(panel, themeLabel, themeMode, 4);
    themeMode.addActionListener(listener);

    addSetting(panel, editorThemeLabel, editorTheme, 5);
    editorTheme.addActionListener(listener);

    addSetting(panel, appFontLabel, appFont, 6);
    appFont.addActionListener(listener);

    restartWarning = new CollapsibleLabel(S.get("windowRestartWarning"));
    restartWarning.setIcon(UIManager.getIcon("OptionPane.warningIcon"));
    restartWarning.setVisible(false);

    addFullRow(panel, restartWarning, 7);

    setLayout(new GridBagLayout());
    resetWindowLayoutButton = new JButton();
    resetWindowLayoutButton.addActionListener(listener);
    resetWindowLayoutButton.setActionCommand(cmdResetWindowLayout);
    resetWindowLayoutButton.setText(S.get("windowToolbarReset"));
    addFullRow(this, resetWindowLayoutButton, 0);
    var row = 1;
    for (final var check : checks) {
      addFullRow(this, check, row++);
    }
    addFullRow(this, panel, row);
    refreshFonts();
  }

  @Override
  public void updateUI() {
    super.updateUI();
    refreshFonts();
  }

  private void refreshFonts() {
    // Explicit role/style fonts are not UIResource fonts, so Swing does not replace them.
    // Always derive from the current UI font, never from the previously scaled component font.
    final var body = UiFonts.body();
    if (zoomFactorImportant != null) {
      zoomFactorImportant.setFont(body.deriveFont(Font.ITALIC));
    }
    if (restartWarning != null) {
      restartWarning.setFont(body.deriveFont(Font.ITALIC));
      restartWarning.setIcon(UIManager.getIcon("OptionPane.warningIcon"));
    }
    if (zoomValue != null && zoomValue.getLabelTable() != null) {
      final var labels = zoomValue.getLabelTable();
      for (final var values = labels.elements(); values.hasMoreElements(); ) {
        if (values.nextElement() instanceof JLabel label) label.setFont(body);
      }
      zoomValue.setFont(body);
      zoomValue.setLabelTable(labels);
      zoomValue.revalidate();
      zoomValue.repaint();
    }
  }

  private static void addFullRow(JPanel panel, JComponent component, int row) {
    final var constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = row;
    constraints.gridwidth = 2;
    constraints.weightx = 1;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.anchor = GridBagConstraints.NORTHWEST;
    constraints.insets = new Insets(Spacing.xs(), 0, Spacing.xs(), 0);
    panel.add(component, constraints);
  }

  private static void addSetting(JPanel panel, JLabel label, JComponent control, int row) {
    label.setLabelFor(control);
    final var constraints = new GridBagConstraints();
    constraints.gridx = 0;
    constraints.gridy = row;
    constraints.anchor = GridBagConstraints.LINE_START;
    constraints.insets = new Insets(Spacing.xs(), 0, Spacing.xs(), Spacing.sm());
    panel.add(label, constraints);
    constraints.gridx = 1;
    constraints.weightx = 1;
    constraints.fill = GridBagConstraints.HORIZONTAL;
    constraints.insets.right = 0;
    panel.add(control, constraints);
  }

  @Override
  public Dimension getPreferredSize() {
    if (zoomFactorImportant != null) {
      final var insets = getInsets();
      final var width = getWidth() > 0 ? getWidth() : UiScale.scaled(360);
      zoomFactorImportant.setSize(
          Math.max(1, width - insets.left - insets.right), Short.MAX_VALUE);
    }
    return super.getPreferredSize();
  }

  /** The localized name of a theme choice, in the order {@link Theme.Mode} declares them. */
  private static String themeModeLabel(Theme.Mode mode) {
    return switch (mode) {
      case LIGHT -> S.get("windowThemeLight");
      case DARK -> S.get("windowThemeDark");
      case SYSTEM -> S.get("windowThemeSystem");
    };
  }

  /**
   * Rebuilds the theme choices in the current language, without letting the rebuild look like the
   * user picking a different theme.
   */
  private void relabelThemeModes() {
    final var selected = themeMode.getSelectedIndex();
    themeMode.removeActionListener(myListener);
    themeMode.removeAllItems();
    for (final var mode : Theme.Mode.values()) {
      themeMode.addItem(themeModeLabel(mode));
    }
    themeMode.setSelectedIndex(selected >= 0 ? selected : Theme.currentMode().ordinal());
    themeMode.addActionListener(myListener);
  }

  private void updateEditorThemeSelection() {
    final var preference =
        AppPreferences.isDarkTheme()
            ? AppPreferences.DARK_EDITOR_THEME
            : AppPreferences.LIGHT_EDITOR_THEME;
    final var selectedTheme = preference.get();
    for (var i = 0; i < AppPreferences.EDITOR_THEMES.length; i++) {
      if (AppPreferences.EDITOR_THEMES[i].equals(selectedTheme)) {
        editorTheme.setSelectedIndex(i);
        return;
      }
    }
  }

  @Override
  public String getHelpText() {
    return S.get("windowHelp");
  }

  @Override
  public String getTitle() {
    return S.get("windowTitle");
  }

  @Override
  public void localeChanged() {
    for (final var check : checks) {
      check.localeChanged();
    }
    toolbarPlacement.localeChanged();
    zoomLabel.setText(S.get("windowToolbarZoomfactor"));
    themeLabel.setText(S.get("windowTheme"));
    relabelThemeModes();
    editorThemeLabel.setText(S.get("windowEditorTheme"));
    appFontLabel.setText(S.get("windowAppFont"));
    restartWarning.setText(S.get("windowRestartWarning"));
    zoomFactorImportant.setText(S.get("windowScaleHelp"));
    resetWindowLayoutButton.setText(S.get("windowToolbarReset"));
    zoomAutoButton.setText(S.get("windowSetAutoScaleFactor"));
  }

  private class SettingsChangeListener implements ChangeListener, ActionListener {

    @Override
    public void stateChanged(ChangeEvent e) {
      final var source = (JSlider) e.getSource();
      if (!source.getValueIsAdjusting()) {
        int value = source.getValue();
        AppPreferences.SCALE_FACTOR.set((double) value / 100.0);
        // Persist an explicit choice even when it happens to equal the current auto scale.
        AppPreferences.getPrefs().putDouble("Scale", (double) value / 100.0);
        UiScale.refresh();
      }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      if (e.getSource().equals(themeMode)) {
        final var selected = themeMode.getSelectedIndex();
        if (selected >= 0 && selected < Theme.Mode.values().length) {
          Theme.setMode(Theme.Mode.values()[selected]);
          updateEditorThemeSelection();
        }
      } else if (e.getSource().equals(editorTheme)) {
        final var selectedIndex = editorTheme.getSelectedIndex();
        if (selectedIndex >= 0 && selectedIndex < AppPreferences.EDITOR_THEMES.length) {
          final var preference =
              AppPreferences.isDarkTheme()
                  ? AppPreferences.DARK_EDITOR_THEME
                  : AppPreferences.LIGHT_EDITOR_THEME;
          preference.set(AppPreferences.EDITOR_THEMES[selectedIndex]);
        }
      } else if (e.getSource().equals(appFont)) {
        String val = (String) appFont.getSelectedItem();
        AppPreferences.APP_FONT.set(S.get("windowAppFontDefault").equals(val) ? "" : val);
        checkRestartWarning();
      } else if (e.getActionCommand().equals(cmdResetWindowLayout)) {
        AppPreferences.resetWindow();
        final var nowOpen = Projects.getOpenProjects();
        for (final var proj : nowOpen) {
          proj.getFrame().resetLayout();
          proj.getFrame().revalidate();
          proj.getFrame().repaint();
        }
      } else if (e.getActionCommand().equals(cmdSetAutoScaleFactor)) {
        final var tmp = AppPreferences.getAutoScaleFactor();
        zoomValue.removeChangeListener(this);
        zoomValue.setValue((int) Math.round(tmp * 100));
        zoomValue.addChangeListener(this);
        AppPreferences.SCALE_FACTOR.set(tmp);
        AppPreferences.getPrefs().remove(AppPreferences.SCALE_FACTOR.getIdentifier());
        UiScale.refresh();
      }
    }

    private void checkRestartWarning() {
      boolean show = !Objects.equals(AppPreferences.APP_FONT.get(), initialAppFont);
      restartWarning.setVisible(show);
    }
  }

  private static class CollapsibleLabel extends JLabel {
    public CollapsibleLabel(String text) {
      super(text);
    }

    @Override
    public Dimension getPreferredSize() {
      if (!isVisible()) {
        return new Dimension(0, 0);
      }
      return super.getPreferredSize();
    }
  }
}

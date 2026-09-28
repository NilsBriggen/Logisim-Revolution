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
import com.cburch.logisim.gui.generic.SettingsForm;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Projects;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.UiFonts;
import com.cburch.logisim.util.UiScale;
import java.awt.BorderLayout;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Objects;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;

class WindowOptions extends OptionsPanel {
  private static final long serialVersionUID = 1L;

  /** The step the interface-scale slider snaps to, in percent. */
  static final int ZOOM_STEP = 25;

  private final PrefBoolean[] checks;
  private final PrefOptionList toolbarPlacement;

  private final JButton resetWindowLayoutButton;

  private final ScaleSlider zoomValue;
  private final JButton zoomAutoButton;
  private final JLabel themeLabel;
  private final JLabel appFontLabel;
  private final JTextArea restartWarning;
  private final JLabel zoomLabel;
  private final JLabel zoomReadout;
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

    // The seven colour controls that were here have moved to the Colors page, which owns every
    // colour in one grouped list. They had been split across this page and Simulation, with three
    // separate reset buttons between them.

    zoomLabel = new JLabel(S.get("windowToolbarZoomfactor"));
    zoomValue = new ScaleSlider();
    // Quarter steps, which the slider snaps to, and the chosen value shown beside it: the slider
    // had ten-percent ticks and no readout, so there was no telling what a position meant.
    zoomValue.setMinorTickSpacing(ZOOM_STEP);
    zoomValue.setSnapToTicks(true);
    zoomReadout = new JLabel();
    zoomValue.addChangeListener(event -> updateZoomReadout());
    updateZoomReadout();
    final var zoomRow = new JPanel(new BorderLayout(Spacing.sm(), 0));
    zoomRow.add(zoomValue, BorderLayout.CENTER);
    // Level with the slider's track rather than centred on the track and its tick labels.
    final var readoutHolder = new JPanel(new BorderLayout());
    readoutHolder.add(zoomReadout, BorderLayout.NORTH);
    zoomRow.add(readoutHolder, BorderLayout.LINE_END);
    zoomAutoButton = new JButton();
    zoomAutoButton.addActionListener(listener);
    zoomAutoButton.setActionCommand(cmdSetAutoScaleFactor);
    zoomAutoButton.setText(S.get("windowSetAutoScaleFactor"));

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
    themeLabel = new JLabel(S.get("windowTheme"));
    themeMode.addActionListener(listener);
    editorTheme.addActionListener(listener);
    appFont.addActionListener(listener);

    resetWindowLayoutButton = new JButton();
    resetWindowLayoutButton.addActionListener(listener);
    resetWindowLayoutButton.setActionCommand(cmdResetWindowLayout);
    resetWindowLayoutButton.setText(S.get("windowToolbarReset"));

    final var form = new SettingsForm();
    form.addRow(themeLabel, themeMode);
    form.addRow(editorThemeLabel, editorTheme);
    form.addRow(appFontLabel, appFont);
    restartWarning = form.addHint(S.get("windowRestartWarning"));
    restartWarning.setVisible(false);
    form.addRow(zoomLabel, zoomRow);
    zoomFactorImportant = form.addHint(S.get("windowScaleHelp"));
    form.addControl(zoomAutoButton);
    form.addRow(toolbarPlacement.getJLabel(), toolbarPlacement.getJComboBox());
    for (final var check : checks) {
      form.addFull(check);
    }
    form.addFull(resetWindowLayoutButton);
    setLayout(new BorderLayout());
    add(form, BorderLayout.NORTH);
  }

  private void updateZoomReadout() {
    // Preview a mouse gesture; otherwise show the effective scale, even if layout snapped the thumb.
    final var percent = zoomValue.mouseGesture
        ? zoomValue.getValue()
        : (int) Math.round(UiScale.factor() * 100);
    zoomReadout.setText(percent + "%");
  }

  private void refreshZoom() {
    if (zoomValue != null && zoomReadout != null && !zoomValue.mouseGesture) {
      zoomValue.setValue((int) Math.round(UiScale.factor() * 100));
      // Setting the same value does not fire a change, but Auto may have changed the effective scale.
      updateZoomReadout();
    }
  }

  /** Commits only after Swing has finished handling and snapping a user gesture. */
  private void applyZoom() {
    final var factor = zoomValue.getValue() / 100.0;
    AppPreferences.SCALE_FACTOR.set(factor);
    // The monitor may already hold this value while Auto is selected.
    AppPreferences.getPrefs().putDouble("Scale", factor);
    UiScale.refresh();
    updateZoomReadout();
  }

  private class ScaleSlider extends ZoomSlider {
    private boolean mouseGesture;
    private int mouseStartValue;

    ScaleSlider() {
      super(JSlider.HORIZONTAL, 100, 300, (int) Math.round(UiScale.factor() * 100));
    }

    @Override
    protected void processMouseEvent(MouseEvent event) {
      // The UI's mouse listener runs before listeners added by the page. Start tracking before it
      // handles a press, and commit after it has handled the release and snapped the final value.
      if (event.getID() == MouseEvent.MOUSE_PRESSED
          && SwingUtilities.isLeftMouseButton(event) && isEnabled()) {
        mouseStartValue = getValue();
        mouseGesture = true;
      }
      try {
        super.processMouseEvent(event);
      } finally {
        if (event.getID() == MouseEvent.MOUSE_RELEASED
            && SwingUtilities.isLeftMouseButton(event) && mouseGesture) {
          mouseGesture = false;
          if (isEnabled() && getValue() != mouseStartValue) applyZoom();
          else updateZoomReadout();
        }
      }
    }

    @Override
    protected boolean processKeyBinding(
        KeyStroke stroke, KeyEvent event, int condition, boolean pressed) {
      final var before = getValue();
      final var handled = super.processKeyBinding(stroke, event, condition, pressed);
      if (handled && getValue() != before && !mouseGesture) applyZoom();
      return handled;
    }
  }

  /** The value the interface-scale slider shows beside it; for tests. */
  String getZoomReadout() {
    return zoomReadout.getText();
  }

  JSlider getZoomSlider() {
    return zoomValue;
  }

  @Override
  public void updateUI() {
    super.updateUI();
    refreshFonts();
    refreshZoom();
  }

  private void refreshFonts() {
    // Explicit role fonts are not UIResource fonts, so Swing does not replace them.
    final var body = UiFonts.body();
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

  /** The localized name of a theme choice, in the order {@link Theme.Mode} declares them. */
  static String themeModeLabel(Theme.Mode mode) {
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

  private class SettingsChangeListener implements ActionListener {

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
        AppPreferences.getPrefs().remove(AppPreferences.SCALE_FACTOR.getIdentifier());
        UiScale.refresh();
        refreshZoom();
      }
    }

    private void checkRestartWarning() {
      boolean show = !Objects.equals(AppPreferences.APP_FONT.get(), initialAppFont);
      restartWarning.setVisible(show);
    }
  }
}

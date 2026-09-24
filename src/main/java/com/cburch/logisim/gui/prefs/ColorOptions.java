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

import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.gui.shell.SectionPanel;
import com.cburch.logisim.gui.theme.AppIcons;
import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.prefs.PrefMonitor;
import com.cburch.logisim.util.Spacing;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Every colour the program draws with, in one place.
 *
 * <p>They used to be thirty-five controls spread over two preference pages that had nothing else
 * to do with each other — the canvas and component colours under Window, the signal and Karnaugh
 * colours under Simulation — with three separate reset buttons between them and no grouping on
 * either page. Nobody looking for "the colour of a wire carrying a one" would have guessed which
 * page to open.
 */
public class ColorOptions extends OptionsPanel {

  private static final long serialVersionUID = 1L;

  /** One row: a caption and the swatch that sets it. */
  private record Swatch(String key, JLabel caption, ColorChooserButton button) {}

  private final List<Swatch> swatches = new ArrayList<>();
  private final List<SectionPanel> sections = new ArrayList<>();
  private final List<String> sectionKeys = new ArrayList<>();
  private final JButton defaultsButton = new JButton();
  private final JButton colorBlindButton = new JButton();

  public ColorOptions(PreferencesFrame window) {
    super(window);
    setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
    setBorder(Spacing.panelBorder());

    addSection(
        "colorsCanvasGroup",
        window,
        new String[] {"windowCanvasBgColor", "windowGridBgColor", "windowGridDotColor",
          "windowGridZoomedDotColor"},
        new PrefMonitor[] {AppPreferences.CANVAS_BG_COLOR, AppPreferences.GRID_BG_COLOR,
          AppPreferences.GRID_DOT_COLOR, AppPreferences.GRID_ZOOMED_DOT_COLOR},
        true);

    addSection(
        "colorsComponentGroup",
        window,
        new String[] {"windowComponentColor", "windowComponentIconColor", "windowTextToolColor"},
        new PrefMonitor[] {AppPreferences.COMPONENT_COLOR, AppPreferences.COMPONENT_ICON_COLOR,
          AppPreferences.TEXT_TOOL_COLOR},
        true);

    addSection(
        "colorsValueGroup",
        window,
        new String[] {"simTrueColTitle", "simFalseColTitle", "simUnknownColTitle", "simErrorColTitle",
          "simNilColTitle", "simBusColTitle", "simStrokeColTitle"},
        new PrefMonitor[] {AppPreferences.TRUE_COLOR, AppPreferences.FALSE_COLOR,
          AppPreferences.UNKNOWN_COLOR, AppPreferences.ERROR_COLOR, AppPreferences.NIL_COLOR,
          AppPreferences.BUS_COLOR, AppPreferences.STROKE_COLOR},
        true);

    addSection(
        "colorsProblemGroup",
        window,
        new String[] {"simWidthErrorTitle", "simWidthErrorCaptionTitle",
          "simWidthErrorHighlightTitle", "simWidthErrorBackgroundTitle", "simClockFrequencyTitle"},
        new PrefMonitor[] {AppPreferences.WIDTH_ERROR_COLOR,
          AppPreferences.WIDTH_ERROR_CAPTION_COLOR, AppPreferences.WIDTH_ERROR_HIGHLIGHT_COLOR,
          AppPreferences.WIDTH_ERROR_BACKGROUND_COLOR, AppPreferences.CLOCK_FREQUENCY_COLOR},
        false);

    // Sixteen, folded away: they matter only to someone reading a Karnaugh map.
    addCoverSection(window, AppPreferences.kmapColorMonitors());

    add(resetRow());
    localeChanged();
  }

  private void addSection(
      String titleKey, PreferencesFrame window, String[] keys, PrefMonitor<?>[] monitors,
      boolean expanded) {
    final var grid = new JPanel(new GridBagLayout());
    final var gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.LINE_START;
    gbc.insets = Spacing.formGaps();
    for (var index = 0; index < keys.length; index++) {
      gbc.gridx = 0;
      gbc.gridy = index;
      final var caption = new JLabel();
      grid.add(caption, gbc);
      gbc.gridx = 1;
      @SuppressWarnings("unchecked")
      final var monitor = (PrefMonitor<Integer>) monitors[index];
      final var button = new ColorChooserButton(window, monitor);
      grid.add(button, gbc);
      swatches.add(new Swatch(keys[index], caption, button));
      gbc.gridx = 2;
      gbc.weightx = 1.0;
      grid.add(javax.swing.Box.createHorizontalGlue(), gbc);
      gbc.weightx = 0.0;
    }
    register(titleKey, grid, expanded);
  }

  /** The cover colours, four to a row, numbered rather than named. */
  private void addCoverSection(PreferencesFrame window, PrefMonitor<Integer>[] covers) {
    final var grid = new JPanel(new GridBagLayout());
    final var gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.LINE_START;
    gbc.insets = Spacing.formGaps();
    for (var index = 0; index < covers.length; index++) {
      gbc.gridx = (index % 4) * 2;
      gbc.gridy = index / 4;
      final var caption = new JLabel(S.get("simKmapColors", index + 1));
      grid.add(caption, gbc);
      gbc.gridx++;
      grid.add(new ColorChooserButton(window, covers[index]), gbc);
    }
    gbc.gridx = 8;
    gbc.gridy = 0;
    gbc.weightx = 1.0;
    grid.add(javax.swing.Box.createHorizontalGlue(), gbc);
    register("colorsKarnaughGroup", grid, false);
  }

  private void register(String titleKey, JPanel content, boolean expanded) {
    final var section = new SectionPanel(S.get(titleKey), content, expanded);
    section.setAlignmentX(LEFT_ALIGNMENT);
    sections.add(section);
    sectionKeys.add(titleKey);
    add(section);
  }

  /**
   * One reset control for every colour on the page.
   *
   * <p>There used to be three, on two pages: shipped defaults for the signal colours, a
   * colour-blind preset beside it, and a separate grid-colour reset on the other page.
   */
  private JPanel resetRow() {
    final var row = new JPanel();
    row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
    row.setBorder(Spacing.border(Spacing.MD, 0, 0, 0));
    row.setAlignmentX(LEFT_ALIGNMENT);
    defaultsButton.setIcon(AppIcons.colored(AppIcons.Id.RESET, 14, Tokens.error()));
    defaultsButton.addActionListener(event -> resetToShipped());
    colorBlindButton.addActionListener(
        event -> {
          SimOptions.applyColorBlindPalette();
          for (final var project : com.cburch.logisim.proj.Projects.getOpenProjects()) {
            project.getFrame().repaint();
          }
        });
    row.add(defaultsButton);
    row.add(javax.swing.Box.createHorizontalStrut(Spacing.sm()));
    row.add(colorBlindButton);
    row.add(javax.swing.Box.createHorizontalGlue());
    return row;
  }

  private void resetToShipped() {
    final var answer =
        OptionPane.showConfirmDialog(
            this,
            S.get("colorsResetPrompt"),
            S.get("colorsResetTitle"),
            OptionPane.YES_NO_OPTION);
    if (answer != OptionPane.YES_OPTION) return;
    SimOptions.applyShippedPalette();
    AppPreferences.setDefaultGridColors();
    for (final var project : com.cburch.logisim.proj.Projects.getOpenProjects()) {
      project.getFrame().repaint();
    }
    repaint();
  }

  @Override
  public String getHelpText() {
    return S.get("colorsHelp");
  }

  @Override
  public String getTitle() {
    return S.get("colorsTitle");
  }

  @Override
  public void localeChanged() {
    for (final var swatch : swatches) {
      swatch.caption().setText(S.get(swatch.key()));
    }
    for (var index = 0; index < sections.size(); index++) {
      sections.get(index).setTitle(S.get(sectionKeys.get(index)));
    }
    defaultsButton.setText(S.get("simDefaultColors"));
    colorBlindButton.setText(S.get("simColorBlindColors"));
  }
}

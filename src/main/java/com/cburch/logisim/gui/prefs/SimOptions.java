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

import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.generic.SettingsForm;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.prefs.PrefMonitor;
import com.cburch.logisim.proj.Projects;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Set;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import javax.swing.JComboBox;
import javax.swing.JLabel;

public class SimOptions extends OptionsPanel {

  private static final long serialVersionUID = 1L;
  private final JLabel trueCharTitle = new JLabel();
  private final SymbolChooser trueChar = new SymbolChooser(AppPreferences.TRUE_CHAR, "1T");
  private final JLabel falseCharTitle = new JLabel();
  private final SymbolChooser falseChar = new SymbolChooser(AppPreferences.FALSE_CHAR, "0F");
  private final JLabel unknownCharTitle = new JLabel();
  private final SymbolChooser unknownChar = new SymbolChooser(AppPreferences.UNKNOWN_CHAR, "U?Z");
  private final JLabel errorCharTitle = new JLabel();
  private final SymbolChooser errorChar = new SymbolChooser(AppPreferences.ERROR_CHAR, "E!X");
  private final JLabel dontCareCharTitle = new JLabel();
  private final SymbolChooser dontCareChar = new SymbolChooser(AppPreferences.DONTCARE_CHAR, "-X");


  public SimOptions(PreferencesFrame window) {
    super(window);
    AppPreferences.getPrefs().addPreferenceChangeListener(new MyListener());

    // Only the value characters remain here. Every colour moved to the Colors page, which owns
    // them all in one grouped list instead of scattering them over two unrelated pages.
    final var form = new SettingsForm();
    form.addRow(trueCharTitle, trueChar);
    form.addRow(falseCharTitle, falseChar);
    form.addRow(unknownCharTitle, unknownChar);
    form.addRow(errorCharTitle, errorChar);
    form.addRow(dontCareCharTitle, dontCareChar);
    setLayout(new BorderLayout());
    add(form, BorderLayout.NORTH);

    localeChanged();
  }

  @Override
  public String getHelpText() {
    return S.get("simHelp");
  }

  @Override
  public String getTitle() {
    return S.get("simTitle");
  }

  @Override
  public void localeChanged() {
    trueCharTitle.setText(S.get("simTrueCharTitle"));
    falseCharTitle.setText(S.get("simFalseCharTitle"));
    unknownCharTitle.setText(S.get("simUnknownCharTitle"));
    errorCharTitle.setText(S.get("simErrorCharTitle"));
    dontCareCharTitle.setText(S.get("simDontCareCharTitle"));
  }

  /** The shipped palette. Shared so the colours page and this one cannot drift apart. */
  static void applyShippedPalette() {
    AppPreferences.TRUE_COLOR.set(AppPreferences.DEFAULT_TRUE_COLOR);
    AppPreferences.FALSE_COLOR.set(AppPreferences.DEFAULT_FALSE_COLOR);
    AppPreferences.UNKNOWN_COLOR.set(AppPreferences.DEFAULT_UNKNOWN_COLOR);
    AppPreferences.ERROR_COLOR.set(AppPreferences.DEFAULT_ERROR_COLOR);
    AppPreferences.NIL_COLOR.set(AppPreferences.DEFAULT_NIL_COLOR);
    AppPreferences.BUS_COLOR.set(AppPreferences.DEFAULT_BUS_COLOR);
    AppPreferences.STROKE_COLOR.set(AppPreferences.DEFAULT_STROKE_COLOR);
    AppPreferences.WIDTH_ERROR_COLOR.set(AppPreferences.DEFAULT_WIDTH_ERROR_COLOR);
    AppPreferences.WIDTH_ERROR_CAPTION_COLOR.set(AppPreferences.DEFAULT_WIDTH_ERROR_CAPTION_COLOR);
    AppPreferences.WIDTH_ERROR_HIGHLIGHT_COLOR.set(AppPreferences.DEFAULT_WIDTH_ERROR_HIGHLIGHT_COLOR);
    AppPreferences.WIDTH_ERROR_BACKGROUND_COLOR.set(AppPreferences.DEFAULT_WIDTH_ERROR_BACKGROUND_COLOR);
    AppPreferences.CLOCK_FREQUENCY_COLOR.set(AppPreferences.DEFAULT_CLOCK_FREQUENCY_COLOR);
    AppPreferences.KMAP1_COLOR.set(0x810000);
    AppPreferences.KMAP2_COLOR.set(0xE7194B);
    AppPreferences.KMAP3_COLOR.set(0xFABEBF);
    AppPreferences.KMAP4_COLOR.set(0xAA6E29);
    AppPreferences.KMAP5_COLOR.set(0xF58231);
    AppPreferences.KMAP6_COLOR.set(0xFFD7B5);
    AppPreferences.KMAP7_COLOR.set(0x818000);
    AppPreferences.KMAP8_COLOR.set(0xFFFF1A);
    AppPreferences.KMAP9_COLOR.set(0xD2F53D);
    AppPreferences.KMAP10_COLOR.set(0x000081);
    AppPreferences.KMAP11_COLOR.set(0x911EB5);
    AppPreferences.KMAP12_COLOR.set(0x3CB5AF);
    AppPreferences.KMAP13_COLOR.set(0x0082CC);
    AppPreferences.KMAP14_COLOR.set(0xE7BEFF);
    AppPreferences.KMAP15_COLOR.set(0xAAFFC4);
    AppPreferences.KMAP16_COLOR.set(0xF032E7);
  }

  /**
   * The colours a wire can be drawn in, for one canvas.
   *
   * <p>{@code SignalPaletteContrastTest} holds every palette to at least 3:1 against its canvas for
   * each state, 3:1 between high and low, and (for the colour-blind sets) to staying apart under
   * simulated protanopia, deuteranopia and tritanopia.
   */
  record WirePalette(
      int trueColor,
      int falseColor,
      int unknownColor,
      int errorColor,
      int nilColor,
      int busColor,
      int strokeColor) {}

  /** Orange high, navy low: told apart by hue and by lightness, on the light canvas. */
  static final WirePalette COLOR_BLIND_LIGHT =
      new WirePalette(0xC65F00, 0x0A2A66, 0x8866FF, 0xE0109E, 0x818181, 0x000000, 0x1F2430);

  /** Amber high, blue low, on the dark canvas. */
  static final WirePalette COLOR_BLIND_DARK =
      new WirePalette(0xFFD23F, 0x3A6FD8, 0xB0A0E0, 0xE0324F, 0x8A8F98, 0xD7DCE5, 0xE6E9F0);

  static WirePalette colorBlindPalette(boolean dark) {
    return dark ? COLOR_BLIND_DARK : COLOR_BLIND_LIGHT;
  }

  /**
   * A palette chosen so the signals and covers stay distinct to a colour-blind reader.
   *
   * <p>Applied to the theme showing now, like any other colour choice; each theme has its own set,
   * since colours readable on a white canvas vanish on a dark one and the other way round.
   */
  static void applyColorBlindPalette() {
    final var wires = colorBlindPalette(AppPreferences.isDarkTheme());
    AppPreferences.TRUE_COLOR.set(wires.trueColor());
    AppPreferences.FALSE_COLOR.set(wires.falseColor());
    AppPreferences.UNKNOWN_COLOR.set(wires.unknownColor());
    AppPreferences.ERROR_COLOR.set(wires.errorColor());
    AppPreferences.NIL_COLOR.set(wires.nilColor());
    AppPreferences.BUS_COLOR.set(wires.busColor());
    AppPreferences.STROKE_COLOR.set(wires.strokeColor());
    AppPreferences.WIDTH_ERROR_COLOR.set(0xC413DB);
    AppPreferences.WIDTH_ERROR_CAPTION_COLOR.set(0x560000);
    AppPreferences.WIDTH_ERROR_HIGHLIGHT_COLOR.set(0xFFFE00);
    AppPreferences.WIDTH_ERROR_BACKGROUND_COLOR.set(0xFFE6D2);
    AppPreferences.CLOCK_FREQUENCY_COLOR.set(0xFF00B4); // FIXME: Calculate proper color!
    final var colorBlindCovers =
        new int[] {
          0x490092,
          0x920000,
          0x004949,
          0x006DDB,
          0x924900,
          0x009292,
          0xB66DFF,
          0xDBD100,
          0xFF6DB6,
          0x6DB6FF,
          0x24FF24,
          0xFFB677,
          0xB6DBFF,
          0xFFFF6D,
          0x009292,
          0xFFB677,
        };
    final var covers = AppPreferences.kmapColorMonitors();
    for (var index = 0; index < covers.length; index++) {
      covers[index].set(colorBlindCovers[index]);
    }
  }

  private static class MyListener implements PreferenceChangeListener {

    /** The colours {@link Value} caches, all of which are reloaded together. */
    private static final Set<String> COLOR_KEYS =
        Set.of(
            AppPreferences.TRUE_COLOR.getIdentifier(),
            AppPreferences.FALSE_COLOR.getIdentifier(),
            AppPreferences.UNKNOWN_COLOR.getIdentifier(),
            AppPreferences.ERROR_COLOR.getIdentifier(),
            AppPreferences.NIL_COLOR.getIdentifier(),
            AppPreferences.BUS_COLOR.getIdentifier(),
            AppPreferences.STROKE_COLOR.getIdentifier(),
            AppPreferences.WIDTH_ERROR_COLOR.getIdentifier(),
            AppPreferences.WIDTH_ERROR_CAPTION_COLOR.getIdentifier(),
            AppPreferences.WIDTH_ERROR_HIGHLIGHT_COLOR.getIdentifier(),
            AppPreferences.WIDTH_ERROR_BACKGROUND_COLOR.getIdentifier(),
            AppPreferences.CLOCK_FREQUENCY_COLOR.getIdentifier());

    @Override
    public void preferenceChange(PreferenceChangeEvent evt) {
      var update = false;
      final var key = evt.getKey();
      if (COLOR_KEYS.contains(key)) {
        // One reload rather than a branch per colour, so a colour added later cannot be forgotten.
        Value.reloadColors();
        update = true;
      } else if (key.equals(AppPreferences.TRUE_CHAR.getIdentifier())) {
        Value.TRUECHAR = AppPreferences.TRUE_CHAR.get().charAt(0);
        update = true;
      } else if (key.equals(AppPreferences.FALSE_CHAR.getIdentifier())) {
        Value.FALSECHAR = AppPreferences.FALSE_CHAR.get().charAt(0);
        update = true;
      } else if (key.equals(AppPreferences.UNKNOWN_CHAR.getIdentifier())) {
        Value.UNKNOWNCHAR = AppPreferences.UNKNOWN_CHAR.get().charAt(0);
        update = true;
      } else if (key.equals(AppPreferences.ERROR_CHAR.getIdentifier())) {
        Value.ERRORCHAR = AppPreferences.ERROR_CHAR.get().charAt(0);
        update = true;
      } else if (key.equals(AppPreferences.DONTCARE_CHAR.getIdentifier())) {
        Value.DONTCARECHAR = AppPreferences.DONTCARE_CHAR.get().charAt(0);
        update = true;
      }
      if (update) {
        for (final var proj : Projects.getOpenProjects()) proj.getFrame().repaint();
      }
    }
  }

  private static class SymbolChooser extends JComboBox<Character> {
    private static final long serialVersionUID = 1L;
    private final PrefMonitor<String> myPref;

    public SymbolChooser(PrefMonitor<String> pref, String choices) {
      super();
      myPref = pref;
      this.addActionListener(new MyactionListener());
      final Character def = pref.get().charAt(0);
      var seldef = -1;
      for (var i = 0; i < choices.length(); i++) {
        final Character sel = choices.charAt(i);
        if (sel.equals(def)) seldef = i;
        this.addItem(sel);
      }
      if (seldef >= 0) this.setSelectedIndex(seldef);
    }

    private class MyactionListener implements ActionListener {
      @Override
      public void actionPerformed(ActionEvent e) {
        @SuppressWarnings("unchecked")
        final var me = (JComboBox<Character>) e.getSource();
        final Character s = (Character) me.getSelectedItem();
        if (s != myPref.get().charAt(0)) {
          myPref.set(Character.toString(s));
        }
      }
    }
  }
}

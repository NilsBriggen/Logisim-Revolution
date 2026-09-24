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
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.prefs.PrefMonitor;
import com.cburch.logisim.proj.Projects;
import java.awt.Color;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.Set;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.SwingConstants;

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

    final var gbc = new GridBagConstraints();
    setLayout(new GridBagLayout());
    gbc.insets = new Insets(2, 4, 4, 2);
    gbc.anchor = GridBagConstraints.CENTER;
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    // Only the value characters remain here. Every colour moved to the Colors page, which owns
    // them all in one grouped list instead of scattering them over two unrelated pages.
    add(trueCharTitle, gbc);
    gbc.gridx++;
    add(trueChar, gbc);

    gbc.gridx = 0;
    gbc.gridy++;
    add(falseCharTitle, gbc);
    gbc.gridx++;
    add(falseChar, gbc);

    gbc.gridx = 0;
    gbc.gridy++;
    add(unknownCharTitle, gbc);
    gbc.gridx++;
    add(unknownChar, gbc);

    gbc.gridx = 0;
    gbc.gridy++;
    add(errorCharTitle, gbc);
    gbc.gridx++;
    add(errorChar, gbc);

    gbc.gridx = 0;
    gbc.gridy++;
    add(dontCareCharTitle, gbc);
    gbc.gridx++;
    add(dontCareChar, gbc);

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

  /** A palette chosen so the covers stay distinct to a colour-blind reader. */
  static void applyColorBlindPalette() {
    AppPreferences.TRUE_COLOR.set(0xF4EB42);
    AppPreferences.FALSE_COLOR.set(0x203BE8);
    AppPreferences.UNKNOWN_COLOR.set(0x01BC9D);
    AppPreferences.ERROR_COLOR.set(0x00C10000);
    AppPreferences.NIL_COLOR.set(0x818181);
    AppPreferences.BUS_COLOR.set(1);
    AppPreferences.STROKE_COLOR.set(0xBBBBBB);
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

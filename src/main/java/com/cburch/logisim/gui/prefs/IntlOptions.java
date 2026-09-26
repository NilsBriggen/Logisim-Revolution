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

import com.cburch.logisim.gui.generic.SettingsForm;
import com.cburch.logisim.prefs.AppPreferences;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;

class IntlOptions extends OptionsPanel {
  private static final long serialVersionUID = 1L;
  private final JLabel localeLabel = new JLabel();
  private final JComponent locale;
  private final PrefOptionList gateShape;

  public IntlOptions(PreferencesFrame window) {
    super(window);

    locale = S.createLocaleSelector();
    gateShape =
        new PrefOptionList(
            AppPreferences.GATE_SHAPE,
            S.getter("intlGateShape"),
            new PrefOption[] {
              new PrefOption(AppPreferences.SHAPE_SHAPED, S.getter("shapeShaped")),
              new PrefOption(AppPreferences.SHAPE_RECTANGULAR, S.getter("shapeRectangular"))
            });
    // new PrefOption(AppPreferences.SHAPE_DIN40700, S.getter("shapeDIN40700"))

    final var form = new SettingsForm();
    form.addRow(gateShape.getJLabel(), gateShape.getJComboBox());
    form.addRow(localeLabel, locale);
    setLayout(new BorderLayout());
    add(form, BorderLayout.NORTH);
  }

  @Override
  public String getHelpText() {
    return S.get("intlHelp");
  }

  @Override
  public String getTitle() {
    return S.get("intlTitle");
  }

  @Override
  public void localeChanged() {
    gateShape.localeChanged();
    localeLabel.setText(S.get("intlLocale"));
  }
}

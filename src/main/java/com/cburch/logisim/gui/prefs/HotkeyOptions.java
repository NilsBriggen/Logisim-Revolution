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
import com.cburch.logisim.prefs.PrefMonitor;
import com.cburch.logisim.prefs.PrefMonitorKeyStroke;
import com.cburch.logisim.util.JHotkeyInput;
import java.awt.BorderLayout;
import java.awt.event.InputEvent;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.KeyStroke;

class HotkeyOptions extends OptionsPanel {
  private static final long serialVersionUID = 1L;
  /*
   * Hotkey Options TAB
   *
   * Author: Hanyuan Zhao <2524395907@qq.com>
   *
   * Description:
   * This is the hotkey settings Tab in the preferences.
   * Allowing users to decide which hotkey to bind to the specific function.
   *
   * To add your own hotkey bindings from your code, you need some operations as follows.
   * Firstly add your hotkey configurations to AppPreferences and set up their strings in resources
   * Fill the resetHotkeys method in AppPreferences, adding the reset code for your hotkeys
   * Set up the hotkey in your code by accessing AppPreferences.HOTKEY_ADD_BY_YOU
   * Do not forget to sync with the user's settings.
   * You should go modifying hotkeySync in AppPreferences, adding your codes there.
   *
   * Every accelerator on the menu bar is a preference listed here, and a new binding is refused
   * when it collides with one of them; see AppPreferences.hotkeyCheckConflict.
   *
   * */
  private final List<PrefMonitor<KeyStroke>> hotkeys = new ArrayList<>();
  private final List<JHotkeyInput> keyInputList;
  private final List<JLabel> keyLabels;
  private final JLabel menuKeyHeaderLabel;
  private final JLabel normalKeyHeaderLabel;
  private JHotkeyInput northBtn;
  private JHotkeyInput southBtn;
  private JHotkeyInput eastBtn;
  private JHotkeyInput westBtn;
  private final JButton resetBtn;
  private final JLabel orientDescLabel;
  private final JLabel orientNorthLabel;
  private final JLabel orientEastLabel;
  private final JLabel orientSouthLabel;
  private final JLabel orientWestLabel;

  public HotkeyOptions(PreferencesFrame window) {
    super(window);

    resetBtn = new JButton();
    resetBtn.addActionListener(e -> AppPreferences.resetHotkeys());
    final var menuRows = new ArrayList<Integer>();
    final var normalRows = new ArrayList<Integer>();

    /* bind up the hotkeys */
    Field[] fields = AppPreferences.class.getDeclaredFields();
    try {
      for (var f : fields) {
        String name = f.getName();
        if (name.contains("HOTKEY_")) {
          @SuppressWarnings("unchecked")
          PrefMonitor<KeyStroke> keyStroke = (PrefMonitor<KeyStroke>) f.get(AppPreferences.class);
          hotkeys.add(keyStroke);
        }
      }
    } catch (Exception e) {
      AppPreferences.hotkeyReflectError(e);
    }

    /* initialize the hotkey labels and hotkey-inputs */
    keyInputList = new ArrayList<>();
    keyLabels = new ArrayList<>();
    for (int i = 0; i < hotkeys.size(); i++) {
      keyInputList.add(new JHotkeyInput(window, ""));
      keyLabels.add(new JLabel());
    }
    for (int i = 0; i < hotkeys.size(); i++) {
      /* I do this chore because they have a different layout */
      var prefKeyStroke = ((PrefMonitorKeyStroke) hotkeys.get(i));
      if (hotkeys.get(i) == AppPreferences.HOTKEY_DIR_NORTH
          || hotkeys.get(i) == AppPreferences.HOTKEY_DIR_SOUTH
          || hotkeys.get(i) == AppPreferences.HOTKEY_DIR_EAST
          || hotkeys.get(i) == AppPreferences.HOTKEY_DIR_WEST) {
        if (hotkeys.get(i) == AppPreferences.HOTKEY_DIR_NORTH) {
          northBtn = new JHotkeyInput(window, prefKeyStroke.getDisplayString());
          keyInputList.set(i, northBtn);
        }
        if (hotkeys.get(i) == AppPreferences.HOTKEY_DIR_SOUTH) {
          southBtn = new JHotkeyInput(window, prefKeyStroke.getDisplayString());
          keyInputList.set(i, southBtn);
        }
        if (hotkeys.get(i) == AppPreferences.HOTKEY_DIR_EAST) {
          eastBtn = new JHotkeyInput(window, prefKeyStroke.getDisplayString());
          keyInputList.set(i, eastBtn);
        }
        if (hotkeys.get(i) == AppPreferences.HOTKEY_DIR_WEST) {
          westBtn = new JHotkeyInput(window, prefKeyStroke.getDisplayString());
          keyInputList.set(i, westBtn);
        }
        keyInputList.get(i).setEnabled(prefKeyStroke.canModify());
        keyInputList.get(i).setBoundKeyStroke(prefKeyStroke);
        continue;
      }
      keyLabels.get(i).setText(S.get(prefKeyStroke.getName()));
      keyInputList.set(i, new JHotkeyInput(window, prefKeyStroke.getDisplayString()));
      keyInputList.get(i).setEnabled(prefKeyStroke.canModify());
      keyInputList.get(i).setBoundKeyStroke(prefKeyStroke);
      (prefKeyStroke.needMetaKey() ? menuRows : normalRows).add(i);
    }

    // One form: the shortcuts under two headings, then the orientation keys under a third.
    final var form = new SettingsForm();
    form.addFull(resetBtn);
    menuKeyHeaderLabel = form.addSection("");
    for (final var i : menuRows) form.addRow(keyLabels.get(i), keyInputList.get(i));
    normalKeyHeaderLabel = form.addSection("");
    for (final var i : normalRows) form.addRow(keyLabels.get(i), keyInputList.get(i));
    orientDescLabel = form.addSection("");
    orientEastLabel = new JLabel();
    orientNorthLabel = new JLabel();
    orientSouthLabel = new JLabel();
    orientWestLabel = new JLabel();
    form.addRow(orientNorthLabel, northBtn);
    form.addRow(orientEastLabel, eastBtn);
    form.addRow(orientSouthLabel, southBtn);
    form.addRow(orientWestLabel, westBtn);
    setLayout(new BorderLayout());
    add(form, BorderLayout.NORTH);

    // A 200ms timer used to poll this panel's own width for ever, to set a preferred size the
    // layout can work out for itself. It never stopped, in every open preferences window.

    AppPreferences.getPrefs().addPreferenceChangeListener(evt -> {
      AppPreferences.hotkeySync();
      for (int i = 0; i < hotkeys.size(); i++) {
        keyInputList.get(i).resetText(((PrefMonitorKeyStroke) hotkeys.get(i)).getDisplayString());
      }
      for (var h : keyInputList) {
        h.exitEditMode();
      }
    });
  }

  @Override
  public String getHelpText() {
    return S.get("hotkeyOptHelp");
  }

  @Override
  public String getTitle() {
    return S.get("hotkeyOptTitle");
  }

  @Override
  public void localeChanged() {
    menuKeyHeaderLabel.setText(S.get("hotkeyOptMenuKeyHeader",
        InputEvent.getModifiersExText(AppPreferences.hotkeyMenuMask)));
    normalKeyHeaderLabel.setText(S.get("hotkeyOptNormalKeyHeader",
        InputEvent.getModifiersExText(AppPreferences.hotkeyMenuMask)));
    resetBtn.setText(S.get("hotkeyOptResetBtn"));
    orientDescLabel.setText(S.get("hotkeyOptOrientDesc"));
    orientDescLabel.setToolTipText(S.get("hotkeyOptOrientTip"));
    orientEastLabel.setText(S.get("hotkeyDirEast"));
    orientWestLabel.setText(S.get("hotkeyDirWest"));
    orientSouthLabel.setText(S.get("hotkeyDirSouth"));
    orientNorthLabel.setText(S.get("hotkeyDirNorth"));
    for (int i = 0; i < hotkeys.size(); i++) {
      var prefKeyStroke = ((PrefMonitorKeyStroke) hotkeys.get(i));
      if (hotkeys.get(i) == AppPreferences.HOTKEY_DIR_NORTH
          || hotkeys.get(i) == AppPreferences.HOTKEY_DIR_SOUTH
          || hotkeys.get(i) == AppPreferences.HOTKEY_DIR_EAST
          || hotkeys.get(i) == AppPreferences.HOTKEY_DIR_WEST) {
        continue;
      }
      keyLabels.get(i).setText(S.get(prefKeyStroke.getName()));
    }
  }
}

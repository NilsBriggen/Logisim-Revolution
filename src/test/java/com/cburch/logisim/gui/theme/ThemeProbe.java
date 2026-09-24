package com.cburch.logisim.gui.theme;

import javax.swing.UIManager;

/** Temporary probe: prints how the theme keys resolved. */
public class ThemeProbe {
  public static void main(String[] args) {
    com.cburch.logisim.prefs.AppPreferences.THEME_MODE.set(args.length > 0 ? args[0] : "light");
    Theme.install();
    for (final var key :
        new String[] {
          "Logisim.accent", "Logisim.statusBar.background", "Logisim.statusBar.foreground",
          "Logisim.activityBar.background", "Logisim.mutedForeground", "Logisim.icon.foreground",
          "Panel.background", "Separator.foreground"
        }) {
      System.out.println(key + " = " + UIManager.get(key));
    }
    System.exit(0);
  }
}

package com.cburch.logisim.gui;

import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.ProjectActions;
import java.io.File;
import javax.swing.SwingUtilities;

/** Opens the secondary windows offscreen to prove they still build. */
public final class OptionsProbe {
  private OptionsProbe() {}

  public static void main(String[] args) throws Exception {
    AppPreferences.THEME_MODE.set("dark");
    Theme.install();
    AppPreferences.applyThemeColors();
    final var project = ProjectActions.doOpen(null, null, new File(args[0]));
    final var deadline = System.currentTimeMillis() + 30_000;
    while (project.getFrame() == null && System.currentTimeMillis() < deadline) Thread.sleep(50);
    SwingUtilities.invokeAndWait(
        () -> {
          project.getOptionsFrame();
          System.out.println("PROBE options ok");
        });
    SwingUtilities.invokeAndWait(
        () -> {
          com.cburch.logisim.analyze.gui.AnalyzerManager.getAnalyzer(project.getFrame());
          System.out.println("PROBE analyzer ok");
        });
    System.exit(0);
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.start;

import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.prefs.AppPreferences;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Offscreen, isolated review capture of the loading window. */
public final class SplashSnapshot {
  private SplashSnapshot() {
    throw new UnsupportedOperationException("Utility class, not instantiable");
  }

  public static void main(String[] args) throws Exception {
    final var theme = Theme.Mode.fromKey(System.getProperty("snapshot.theme", "dark"));
    AppPreferences.THEME_MODE.set(theme.key());
    AppPreferences.SCALE_FACTOR.set(Double.parseDouble(
        System.getProperty("snapshot.scale", "1.0")));
    Theme.install();
    AppPreferences.applyThemeColors();
    final var output = new File(System.getProperty(
        "snapshot.out", "build/splash-snapshot.png"));
    final var captured = new BufferedImage[1];
    SwingUtilities.invokeAndWait(() -> {
      final var splash = new SplashScreen();
      splash.setProgress(SplashScreen.LIBRARIES);
      splash.pack();
      splash.validate();
      final var target = splash.getContentPane();
      target.doLayout();
      final var image = new BufferedImage(
          target.getWidth(), target.getHeight(), BufferedImage.TYPE_INT_ARGB);
      final var graphics = image.createGraphics();
      try {
        target.printAll(graphics);
      } finally {
        graphics.dispose();
        splash.dispose();
      }
      captured[0] = image;
    });
    final var parent = output.getAbsoluteFile().getParentFile();
    if (parent != null) parent.mkdirs();
    ImageIO.write(captured[0], "png", output);
    System.out.println("wrote " + output);
    System.exit(0);
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim;

import com.cburch.logisim.generated.BuildInfo;
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.gui.start.Startup;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.prefs.PreferenceBootstrap;
import java.awt.GraphicsEnvironment;
import java.io.PrintWriter;
import java.io.StringWriter;
import javax.swing.SwingUtilities;

public class Main {
  /**
   * Application entry point.
   *
   * @param args Optional arguments.
   */
  public static void main(String[] args) {
    // To print debug log level information, run with:
    //    java -Dorg.slf4j.simpleLogger.defaultLogLevel=debug
    // or uncomment next line
    // System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug");

    System.setProperty("apple.awt.application.name", BuildInfo.name);
    PreferenceBootstrap.offerImportIfInteractive(args);
    try {
      if (!GraphicsEnvironment.isHeadless()) {
        SwingUtilities.invokeAndWait(Theme::install);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }

    final var startup = Startup.parseArgs(args);
    if (startup == null) System.exit(10);
    if (startup.shallQuit()) System.exit(0);

    try {
      startup.run();
    } catch (Throwable e) {
      final var strWriter = new StringWriter();
      final var printWriter = new PrintWriter(strWriter);
      e.printStackTrace(printWriter);
      OptionPane.showMessageDialog(null, strWriter.toString());
      System.exit(100);
    }
  }

  public static boolean headless = false;

  /** Uncommitted document marker in the window title. */
  public static final String DIRTY_MARKER = "●";

  public static boolean hasGui() {
    return !headless;
  }

}

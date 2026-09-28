/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.analyze.gui;

import static com.cburch.logisim.analyze.Strings.S;

import com.cburch.logisim.util.LocaleListener;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.WindowMenuItemManager;
import java.awt.Dimension;
import java.awt.Window;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

public class AnalyzerManager extends WindowMenuItemManager implements LocaleListener {
  public static Analyzer getAnalyzer(java.awt.Component parent) {
    if (analysisWindow == null) {
      analysisWindow = new Analyzer();
      analysisWindow.pack();
      // The packed size is only what the empty first tab needs; open at a useful share of the
      // window the analyzer was opened from instead, within the usable screen area.
      final var window = parent instanceof Window w
          ? w
          : (parent == null ? null : SwingUtilities.getWindowAncestor(parent));
      final var screen = analysisWindow.getGraphicsConfiguration() == null
          ? null
          : analysisWindow.getGraphicsConfiguration().getBounds();
      analysisWindow.setSize(initialSize(analysisWindow.getSize(),
          window == null ? null : window.getSize(),
          screen == null ? null : screen.getSize()));
      analysisWindow.setLocationRelativeTo(parent);
      if (analysisManager != null) analysisManager.frameOpened(analysisWindow);
    }
    return analysisWindow;
  }

  /**
   * The analyzer's opening size: at least its packed size, else about two thirds of the parent
   * window, and never more than 90% of the screen.
   */
  static Dimension initialSize(Dimension packed, Dimension parent, Dimension screen) {
    var width = packed.width;
    var height = packed.height;
    if (parent != null) {
      width = Math.max(width, parent.width * 2 / 3);
      height = Math.max(height, parent.height * 2 / 3);
    }
    if (screen != null) {
      width = Math.min(width, screen.width * 9 / 10);
      height = Math.min(height, screen.height * 9 / 10);
    }
    return new Dimension(width, height);
  }

  public static void initialize() {
    analysisManager = new AnalyzerManager();
  }

  private static Analyzer analysisWindow = null;
  private static AnalyzerManager analysisManager = null;

  private AnalyzerManager() {
    super(S.get("analyzerWindowTitle"), true);
    LocaleManager.addLocaleListener(this);
  }

  @Override
  public JFrame getJFrame(boolean create, java.awt.Component parent) {
    return (create) ? getAnalyzer(parent) : analysisWindow;
  }

  @Override
  public void localeChanged() {
    setText(S.get("analyzerWindowTitle"));
  }
}

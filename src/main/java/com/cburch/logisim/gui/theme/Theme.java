/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.theme;

import com.cburch.logisim.prefs.AppPreferences;
import com.formdev.flatlaf.FlatLaf;
import java.awt.GraphicsEnvironment;
import java.awt.Window;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * The application's appearance: one light theme, one dark theme, and the choice between them.
 *
 * <p>Logisim-evolution used to offer whatever look and feel happened to be installed, which meant
 * the interface could not be designed at all: anything the application drew itself had to survive
 * a decade of unrelated themes. There are now exactly two, both derived from the same palette, so
 * every panel, icon and canvas colour can be specified.
 */
public final class Theme {

  /** What the user asked for, which is not the same as what is currently showing. */
  public enum Mode {
    LIGHT,
    DARK,
    /** Follow the desktop, re-checked while the application runs. */
    SYSTEM;

    /** The preference value for this mode. */
    public String key() {
      return name().toLowerCase(Locale.ROOT);
    }

    /** Parses a stored preference value, falling back to {@link #SYSTEM}. */
    public static Mode fromKey(String key) {
      if (key != null) {
        for (final var mode : values()) {
          if (mode.key().equalsIgnoreCase(key.trim())) return mode;
        }
      }
      return SYSTEM;
    }
  }

  /** Used when the desktop does not say what it wants. */
  private static final boolean SYSTEM_FALLBACK_DARK = true;

  /** How often the desktop is re-asked while the mode is {@link Mode#SYSTEM}. */
  private static final int SYSTEM_POLL_MS = 20_000;

  private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();

  private static boolean defaultsRegistered = false;
  private static boolean installed = false;
  private static boolean dark = false;
  private static javax.swing.Timer systemPoll;
  private static Boolean systemDarkCache;

  private Theme() {
    throw new UnsupportedOperationException("Utility class, do not instantiate.");
  }

  /**
   * Installs the themes and applies the stored choice. Must run before any Swing component is
   * created, and after {@link AppPreferences} is loaded.
   */
  public static void install() {
    if (GraphicsEnvironment.isHeadless()) return;
    installed = true;
    // Both windows and dialogs get the theme's own title bar, so the whole window is one surface
    // instead of a themed interior inside a system frame. macOS is left alone: its title bar is
    // part of the platform and already follows the system appearance.
    if (!isMacOs()) {
      JFrame.setDefaultLookAndFeelDecorated(true);
      JDialog.setDefaultLookAndFeelDecorated(true);
    }
    apply(currentMode());
  }

  /** The mode the user has chosen. */
  public static Mode currentMode() {
    return Mode.fromKey(AppPreferences.THEME_MODE.get());
  }

  /** Stores {@code mode} and applies it. */
  public static void setMode(Mode mode) {
    AppPreferences.THEME_MODE.set(mode.key());
    apply(mode);
  }

  /** Applies {@code mode} to every open window and notifies listeners. */
  public static void apply(Mode mode) {
    registerDefaults();
    final var wantDark = resolveDark(mode);
    final var changed = !installed || wantDark != dark || !isOurLaf();
    dark = wantDark;
    if (changed) {
      if (wantDark) {
        LogisimDarkLaf.setup();
      } else {
        LogisimLightLaf.setup();
      }
      FlatLaf.updateUI();
      for (final var window : Window.getWindows()) {
        SwingUtilities.updateComponentTreeUI(window);
      }
      fireChanged();
    }
    updateSystemPoll(mode);
  }

  /**
   * Whether a dark theme is showing right now.
   *
   * <p>Asked from component painting, so it must be cheap. Before a theme is installed, which is
   * the case in tests and in the command-line tools, the desktop is consulted at most once.
   */
  public static boolean isDark() {
    if (installed) return dark;
    final var mode = currentMode();
    if (mode != Mode.SYSTEM) return mode == Mode.DARK;
    if (systemDarkCache == null) {
      systemDarkCache = SystemTheme.isSystemDark().orElse(SYSTEM_FALLBACK_DARK);
    }
    return systemDarkCache;
  }

  /** Resolves a mode to light or dark, asking the desktop when the mode is {@link Mode#SYSTEM}. */
  static boolean resolveDark(Mode mode) {
    return switch (mode) {
      case LIGHT -> false;
      case DARK -> true;
      case SYSTEM -> {
        systemDarkCache = SystemTheme.isSystemDark().orElse(SYSTEM_FALLBACK_DARK);
        yield systemDarkCache;
      }
    };
  }

  /** Runs {@code listener} whenever the theme changes. */
  public static void addListener(Runnable listener) {
    if (listener != null) listeners.add(listener);
  }

  public static void removeListener(Runnable listener) {
    listeners.remove(listener);
  }

  /** Notifies listeners; a failing listener must not stop the others. */
  public static void fireChanged() {
    for (final var listener : listeners) {
      try {
        listener.run();
      } catch (RuntimeException e) {
        System.err.println("theme listener failed: " + e);
      }
    }
  }

  /**
   * Starts or stops watching the desktop. Only while following the system is there anything to
   * watch, and the check is a cheap command, so a slow poll costs nothing and saves the user
   * restarting after switching their desktop to dark.
   */
  private static void updateSystemPoll(Mode mode) {
    if (mode != Mode.SYSTEM) {
      if (systemPoll != null) systemPoll.stop();
      return;
    }
    if (systemPoll == null) {
      systemPoll =
          new javax.swing.Timer(
              SYSTEM_POLL_MS,
              event -> {
                if (currentMode() == Mode.SYSTEM) apply(Mode.SYSTEM);
              });
      systemPoll.setRepeats(true);
    }
    if (!systemPoll.isRunning()) systemPoll.start();
  }

  /**
   * Points FlatLaf at the theme's property files. They carry the shape and colour of the whole
   * interface, so this has to happen before either theme is set up.
   */
  private static void registerDefaults() {
    if (defaultsRegistered) return;
    FlatLaf.registerCustomDefaultsSource("com.cburch.logisim.gui.theme");
    defaultsRegistered = true;
  }

  private static boolean isOurLaf() {
    final var laf = UIManager.getLookAndFeel();
    return laf instanceof LogisimLightLaf || laf instanceof LogisimDarkLaf;
  }

  static boolean isMacOs() {
    return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
  }
}

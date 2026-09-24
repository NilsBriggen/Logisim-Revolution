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
import com.cburch.logisim.proj.Projects;
import com.cburch.logisim.util.UiScale;
import com.formdev.flatlaf.FlatLaf;
import java.awt.GraphicsEnvironment;
import java.awt.event.HierarchyEvent;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import javax.swing.JComponent;
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
  private static final AtomicBoolean uiRefreshPending = new AtomicBoolean();

  private static boolean defaultsRegistered = false;
  private static volatile boolean installed = false;
  private static volatile boolean dark = false;
  private static javax.swing.Timer systemPoll;
  private static volatile Boolean systemDarkCache;
  private static Mode requestedMode;
  private static boolean probePending;
  private static final ExecutorService SYSTEM_PROBE =
      Executors.newSingleThreadExecutor(
          runnable -> {
            final var thread = new Thread(runnable, "system-theme-probe");
            thread.setDaemon(true);
            return thread;
          });

  private Theme() {
    throw new UnsupportedOperationException("Utility class, do not instantiate.");
  }

  /**
   * Installs the themes and applies the stored choice. Must run before any Swing component is
   * created, and after {@link AppPreferences} is loaded.
   */
  public static void install() {
    if (!SwingUtilities.isEventDispatchThread()) {
      try {
        SwingUtilities.invokeAndWait(Theme::install);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted installing theme", e);
      } catch (java.lang.reflect.InvocationTargetException e) {
        throw new IllegalStateException("Could not install theme", e.getCause());
      }
      return;
    }
    final var appFont = AppPreferences.APP_FONT.get();
    if (appFont != null && !appFont.isBlank()) FlatLaf.setPreferredFontFamily(appFont);
    // Both windows and dialogs get the theme's own title bar, so the whole window is one surface
    // instead of a themed interior inside a system frame. macOS is left alone: its title bar is
    // part of the platform and already follows the system appearance.
    if (!GraphicsEnvironment.isHeadless() && !isMacOs()) {
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
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(() -> apply(mode));
      return;
    }
    requestedMode = mode;
    registerDefaults();
    applyResolved(resolveDark(mode));
    updateSystemPoll(mode);
    if (mode == Mode.SYSTEM) requestSystemTheme(SystemTheme::isSystemDark);
  }

  /** Installs defaults, drawing colours and custom widgets as one ordered EDT transaction. */
  private static void applyResolved(boolean wantDark) {
    final var changed = !installed || wantDark != dark || !isOurLaf();
    dark = wantDark;
    if (changed) {
      if (wantDark) {
        LogisimDarkLaf.setup();
      } else {
        LogisimLightLaf.setup();
      }
      installed = true;
      UiScale.install();
      AppPreferences.applyThemeColors();
      for (final var project : Projects.getOpenProjects()) {
        for (final var circuit : project.getLogisimFile().getCircuits()) {
          if (circuit.getAppearance().isDefaultAppearance()) {
            circuit.getAppearance().recomputeDefaultAppearance();
          }
        }
      }
      refreshUiLater();
    }
  }

  /**
   * Whether a dark theme is showing right now.
   *
   * <p>Asked from component painting, so it must be cheap. Before a theme is installed, which is
   * the case in tests and command-line tools, only the cached result or fallback is used.
   */
  public static boolean isDark() {
    if (installed) return dark;
    final var mode = currentMode();
    if (mode != Mode.SYSTEM) return mode == Mode.DARK;
    return systemDarkCache == null ? SYSTEM_FALLBACK_DARK : systemDarkCache;
  }

  /** Resolves a mode without I/O; a background probe refreshes the cached system choice. */
  static boolean resolveDark(Mode mode) {
    return switch (mode) {
      case LIGHT -> false;
      case DARK -> true;
      case SYSTEM -> systemDarkCache == null ? SYSTEM_FALLBACK_DARK : systemDarkCache;
    };
  }

  /** Runs {@code listener} whenever the theme changes. */
  public static void addListener(Runnable listener) {
    if (listener != null) listeners.add(listener);
  }

  /** Subscribes only while the owner is displayable, without retaining disposed component trees. */
  public static void addListener(JComponent owner, Runnable listener) {
    owner.addHierarchyListener(
        event -> {
          if ((event.getChangeFlags() & HierarchyEvent.DISPLAYABILITY_CHANGED) == 0) return;
          if (owner.isDisplayable()) {
            listeners.add(listener);
            listener.run();
          } else {
            listeners.remove(listener);
          }
        });
    if (owner.isDisplayable()) listeners.add(listener);
  }

  /** At most one background probe runs; stale results cannot override a manual choice. */
  static void requestSystemTheme(Supplier<Optional<Boolean>> probe) {
    if (probePending) return;
    probePending = true;
    CompletableFuture.supplyAsync(probe, SYSTEM_PROBE)
        .whenComplete((result, failure) -> SwingUtilities.invokeLater(() -> {
          probePending = false;
          if (failure != null || result == null || result.isEmpty()) return;
          systemDarkCache = result.get();
          if (requestedMode == Mode.SYSTEM) applyResolved(systemDarkCache);
        }));
  }

  public static void removeListener(Runnable listener) {
    listeners.remove(listener);
  }

  /**
   * Coalesces native and custom-widget refreshes after the initiating Swing event has returned.
   * Replacing a combo or slider delegate inside its own handler clears fields that handler still
   * needs; listeners must therefore run after the deferred native tree update as well.
   */
  public static void refreshUiLater() {
    if (!uiRefreshPending.compareAndSet(false, true)) return;
    SwingUtilities.invokeLater(() -> {
      uiRefreshPending.set(false);
      FlatLaf.updateUI();
      fireChanged();
    });
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
   * watch. The timer only schedules work; desktop I/O always runs off the EDT.
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
                if (requestedMode == Mode.SYSTEM) requestSystemTheme(SystemTheme::isSystemDark);
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

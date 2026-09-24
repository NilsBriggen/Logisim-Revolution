/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.prefs;
import com.cburch.logisim.gui.generic.OptionPane;

import com.cburch.logisim.AppIdentity;
import com.formdev.flatlaf.FlatLightLaf;
import java.awt.GraphicsEnvironment;
import java.lang.reflect.InvocationTargetException;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** First-run offer made before {@link AppPreferences} constructs its preference monitors. */
public final class PreferenceBootstrap {
  private static final Logger logger = LoggerFactory.getLogger(PreferenceBootstrap.class);
  private static final String DECISION_KEY = "revolution.importDecision";
  private static final String PALETTE_KEY = "canvasPaletteVersion";
  private static final Set<String> NON_INTERACTIVE = Set.of(
      "--help", "-h", "--version", "-v", "--tty", "-t", "--test-circuit", "-b",
      "--test-fpga", "-f", "--test-vector", "-w", "--new-file-format", "-n",
      "--clear-prefs");

  private PreferenceBootstrap() {
    throw new UnsupportedOperationException("Utility class, not instantiable");
  }

  /** Command-line tasks and headless runs must never show a settings dialog. */
  public static void offerImportIfInteractive(String[] args) {
    if (GraphicsEnvironment.isHeadless() || !isInteractive(args)) return;
    try {
      final var root = Preferences.userRoot();
      if (!root.nodeExists(AppIdentity.EVOLUTION_PREFERENCES_PATH)) return;
      final var destination = root.node(AppIdentity.PREFERENCES_PATH);
      final var source = root.node(AppIdentity.EVOLUTION_PREFERENCES_PATH);
      if (!shouldOffer(source, destination)) return;

      final var accepted = new boolean[1];
      final var prompt = (Runnable) () -> {
        FlatLightLaf.setup();
        final var language = source.get("locale", "");
        final var locale = language.isBlank() ? Locale.getDefault() : Locale.forLanguageTag(language);
        final var bundle = ResourceBundle.getBundle(
            "resources/logisim/strings/gui/gui", locale);
        accepted[0] = OptionPane.showConfirmDialog(
            null, bundle.getString("revolutionImportQuestion"),
            bundle.getString("revolutionImportTitle"), OptionPane.YES_NO_OPTION,
            OptionPane.QUESTION_MESSAGE) == OptionPane.YES_OPTION;
      };
      if (SwingUtilities.isEventDispatchThread()) prompt.run();
      else SwingUtilities.invokeAndWait(prompt);

      if (accepted[0]) copyCompatible(source, destination);
      rememberDecision(destination, accepted[0]);
    } catch (BackingStoreException | InterruptedException | InvocationTargetException e) {
      if (e instanceof InterruptedException) Thread.currentThread().interrupt();
      logger.warn("Could not complete the optional settings import", e);
    }
  }

  static boolean isInteractive(String[] args) {
    for (final var arg : args) {
      if (NON_INTERACTIVE.contains(arg)) return false;
    }
    return true;
  }

  static boolean shouldOffer(Preferences source, Preferences destination)
      throws BackingStoreException {
    return destination.get(DECISION_KEY, null) == null && source.keys().length > 0;
  }

  static void rememberDecision(Preferences destination, boolean imported)
      throws BackingStoreException {
    destination.put(DECISION_KEY, imported ? "imported" : "declined");
    destination.flush();
  }

  /** Copy values as stored; shortcut byte arrays keep their encoded representation. */
  static int copyCompatible(Preferences source, Preferences destination)
      throws BackingStoreException {
    final var paletteCurrent = source.getInt(PALETTE_KEY, 0)
        >= ThemeColorPrefs.shippedPaletteVersion();
    var copied = 0;
    for (final var key : source.keys()) {
      if (!isCompatible(key, paletteCurrent) || destination.get(key, null) != null) continue;
      final var value = source.get(key, null);
      if (value == null) continue;
      if ("Scale".equals(key)) {
        try {
          final var scale = Double.parseDouble(value);
          if (!Double.isFinite(scale) || scale < 0.5 || scale > 4.0) continue;
        } catch (NumberFormatException ignored) {
          continue;
        }
      }
      destination.put(key, value);
      copied++;
    }
    if (paletteCurrent && destination.get(PALETTE_KEY, null) == null) {
      destination.putInt(PALETTE_KEY, ThemeColorPrefs.shippedPaletteVersion());
    }
    return copied;
  }

  static boolean isCompatible(String key, boolean paletteCurrent) {
    if (key.startsWith("window") || key.startsWith("Window") || key.startsWith("shell.")) {
      return false;
    }
    if (key.startsWith("theme.light.") || key.startsWith("theme.dark.")) {
      return paletteCurrent;
    }
    if (key.startsWith("textToolTheme") || key.equals(PALETTE_KEY)
        || key.equals("LookAndFeel") || key.startsWith("revolution.")) return false;
    return key.equals("locale") || key.equals("theme") || key.equals("AppFont")
        || key.equals("Scale") || key.equals("lightEditorTheme")
        || key.equals("darkEditorTheme") || key.startsWith("hotkey")
        || key.matches("recent[0-9]+") || key.toLowerCase(Locale.ROOT).contains("template")
        || key.equals("paletteFavourites") || key.equals("paletteRecents")
        || key.equals("paletteShowTree") || key.equals("componentTips")
        || key.equals("SelectedBoard") || key.equals("FPGAWorkspace")
        || key.equals("HdlType") || key.equals("toolbarPlacement");
  }
}

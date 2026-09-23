/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.generic;

import com.cburch.logisim.prefs.AppPreferences;
import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import javax.swing.SwingUtilities;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Theme;

public final class EditorTheme {
  private static final String LISTENER_KEY = EditorTheme.class.getName() + ".listener";
  private static final String THEME_ROOT = "/org/fife/ui/rsyntaxtextarea/themes/";

  /**
   * The editors currently showing, so a theme change can repaint them all.
   *
   * <p>Held weakly: an editor whose window has closed must not be kept alive by this.
   */
  private static final Set<RSyntaxTextArea> editors =
      Collections.newSetFromMap(new WeakHashMap<>());

  static {
    com.cburch.logisim.gui.theme.Theme.addListener(EditorTheme::applyToAll);
  }

  private EditorTheme() {}

  public static void install(RSyntaxTextArea editor) {
    apply(editor);
    synchronized (editors) {
      editors.add(editor);
    }

    final PropertyChangeListener listener =
        event -> {
          if (AppPreferences.LIGHT_EDITOR_THEME.isSource(event)
              || AppPreferences.DARK_EDITOR_THEME.isSource(event)) {
            apply(editor);
          }
        };
    editor.putClientProperty(LISTENER_KEY, listener);
    AppPreferences.addPropertyChangeListener(listener);
  }

  /** Re-applies the syntax colours to every open editor, on the event thread. */
  private static void applyToAll() {
    final RSyntaxTextArea[] snapshot;
    synchronized (editors) {
      snapshot = editors.toArray(RSyntaxTextArea[]::new);
    }
    SwingUtilities.invokeLater(
        () -> {
          for (final var editor : snapshot) apply(editor);
        });
  }

  private static void apply(RSyntaxTextArea editor) {
    final var preference =
        AppPreferences.isDarkTheme()
            ? AppPreferences.DARK_EDITOR_THEME
            : AppPreferences.LIGHT_EDITOR_THEME;
    final var themePath = THEME_ROOT + preference.get() + ".xml";
    try (final var input = EditorTheme.class.getResourceAsStream(themePath)) {
      if (input != null) {
        Theme.load(input).apply(editor);
      }
    } catch (IOException ignored) {
    }
  }
}

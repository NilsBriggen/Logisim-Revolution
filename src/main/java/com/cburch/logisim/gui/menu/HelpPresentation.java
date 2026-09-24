/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.menu;

import com.cburch.logisim.gui.theme.AppIcons;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.util.UiFonts;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ContainerAdapter;
import java.awt.event.ContainerEvent;
import java.util.ConcurrentModificationException;
import java.util.Locale;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JTabbedPane;
import javax.swing.JToolBar;
import javax.swing.Timer;
import javax.swing.SwingUtilities;
import javax.swing.text.html.HTMLDocument;

/** Presentation only: use JavaHelp's public component tree and Swing's CSS1 HTML renderer. */
final class HelpPresentation {
  private static final String INSTALLED = HelpPresentation.class.getName() + ".installed";
  private static final String STYLE_RETRY = HelpPresentation.class.getName() + ".retry";

  private record StyleRetry(HTMLDocument document, int attempts) {}

  private HelpPresentation() {}

  static void install(JComponent viewer) {
    visit(viewer);
    Theme.addListener(viewer, () -> visit(viewer));
  }

  /** Replace JavaHelp's bundled bitmap navigation symbols without changing its actions. */
  static void installNavigation(JComponent help) {
    styleNavigation(help);
    Theme.addListener(help, () -> styleNavigation(help));
  }

  static void styleNavigation(Component component) {
    if (component instanceof JToolBar toolbar) {
      final var buttons = java.util.Arrays.stream(toolbar.getComponents())
          .filter(AbstractButton.class::isInstance)
          .map(AbstractButton.class::cast)
          .toList();
      // JavaHelp 2.0.05 has Back, Forward, Home, then Favorites. Leave unknown
      // toolbars untouched rather than assigning an icon to the wrong action.
      if (buttons.size() == 4
          && buttons.stream().allMatch(button ->
              button.getClass().getName().startsWith("javax.help."))) {
        final var icons = new AppIcons.Id[] {
            AppIcons.Id.ARROW_LEFT, AppIcons.Id.ARROW_RIGHT,
            AppIcons.Id.HOME, AppIcons.Id.STAR
        };
        for (var index = 0; index < icons.length; index++) {
          final var button = buttons.get(index);
          button.setIcon(AppIcons.get(icons[index], 18));
          button.setDisabledIcon(AppIcons.disabled(icons[index], 18));
          button.setBorderPainted(false);
          button.setContentAreaFilled(false);
        }
      }
    }
    if (component instanceof JTabbedPane tabs && tabs.getTabCount() == 3
        && java.util.stream.IntStream.range(0, 3).allMatch(index ->
            tabs.getComponentAt(index).getClass().getName().startsWith("javax.help."))) {
      final var icons = new AppIcons.Id[] {
          AppIcons.Id.FOLDER, AppIcons.Id.SEARCH, AppIcons.Id.STAR
      };
      for (var index = 0; index < icons.length; index++) {
        tabs.setIconAt(index, AppIcons.get(icons[index], 16));
      }
    }
    if (component instanceof Container container) {
      for (final var child : container.getComponents()) styleNavigation(child);
    }
  }

  private static void visit(Component component) {
    if (component instanceof JEditorPane editor) {
      if (editor.getClientProperty(INSTALLED) == null) {
        editor.putClientProperty(INSTALLED, Boolean.TRUE);
        editor.addPropertyChangeListener(event -> {
          if ("document".equals(event.getPropertyName()) || "page".equals(event.getPropertyName())) {
            if (SwingUtilities.isEventDispatchThread()) apply(editor);
            else SwingUtilities.invokeLater(() -> apply(editor));
          }
        });
      }
      apply(editor);
    } else if (component instanceof Container container) {
      // A LAF change may replace the viewer's children. Observe that public lifecycle too.
      if (container instanceof JComponent swing && swing.getClientProperty(INSTALLED) == null) {
        swing.putClientProperty(INSTALLED, Boolean.TRUE);
        container.addContainerListener(new ContainerAdapter() {
          @Override
          public void componentAdded(ContainerEvent event) {
            visit(event.getChild());
          }
        });
      }
      for (final var child : container.getComponents()) visit(child);
    }
  }

  static void apply(JEditorPane editor) {
    final var font = UiFonts.body();
    final var background = Tokens.color("EditorPane.background", Color.WHITE);
    final var foreground = Tokens.color("EditorPane.foreground", Color.BLACK);
    final var accent = readableLink(Tokens.accent(), foreground, background);
    editor.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, Boolean.TRUE);
    editor.putClientProperty(JEditorPane.W3C_LENGTH_UNITS, Boolean.TRUE);
    editor.setFont(font);
    editor.setBackground(background);
    editor.setForeground(foreground);
    if (!(editor.getDocument() instanceof HTMLDocument document)) return;

    // JavaHelp can be halfway through an asynchronous page load. Preserve the document and
    // its rule precedence; defer a rare conflicting stylesheet update until Swing has settled.
    final var styles = document.getStyleSheet();
    try {
      final var family = font.getFamily().replace("'", "");
      styles.addRule(String.format(Locale.ROOT,
          "body { font-family: '%s'; font-size: %dpx; color: %s; background-color: %s; margin: 12px; }",
          family, font.getSize(), css(foreground), css(background)));
      for (final var selector : new String[] {"h1", "h2", "h3", "h4", "var", "b.propertie",
          "b.reffig", "b.refguide", "b.refquest", "dt.lib", "b.tkeybd", "b.note", "p.blue",
          "span.totranslate"}) {
        styles.addRule(selector + " { color: " + css(foreground) + "; }");
      }
      final double[] headingSizes = {1.6, 1.35, 1.15, 1.0};
      for (var i = 0; i < headingSizes.length; i++) {
        styles.addRule("h" + (i + 1) + " { font-size: "
            + Math.round(font.getSize() * headingSizes[i]) + "px; }");
      }
      for (final var selector : new String[] {"pre", "code", "tt"}) {
        styles.addRule(selector + " { font-family: monospace; font-size: "
            + font.getSize() + "px; }");
      }
      styles.addRule("a { color: " + css(accent) + "; text-decoration: underline; }");
      styles.addRule("a:hover { color: " + css(accent) + "; }");
      for (final var selector : new String[] {"b.menu", "b.button"}) {
        styles.addRule(selector + " { font-family: '" + family + "'; font-size: 100%; color: "
            + css(foreground) + "; background-color: " + css(background) + "; }");
      }
      editor.putClientProperty(STYLE_RETRY, null);
    } catch (ConcurrentModificationException ignored) {
      final var previous = (StyleRetry) editor.getClientProperty(STYLE_RETRY);
      final var attempts = previous != null && previous.document() == document
          ? previous.attempts() + 1 : 1;
      if (attempts <= 12) {
        editor.putClientProperty(STYLE_RETRY, new StyleRetry(document, attempts));
        final var timer = new Timer(75, event -> {
          if (editor.getDocument() == document) apply(editor);
        });
        timer.setRepeats(false);
        timer.start();
      }
      return;
    }
    editor.revalidate();
    editor.repaint();
  }

  static Color readableLink(Color accent, Color foreground, Color background) {
    final var first = luminance(accent) + 0.05;
    final var second = luminance(background) + 0.05;
    return Math.max(first, second) / Math.min(first, second) >= 4.5 ? accent : foreground;
  }

  private static double luminance(Color color) {
    final var channels = color.getRGBColorComponents(null);
    final double[] weights = {0.2126, 0.7152, 0.0722};
    var result = 0.0;
    for (var i = 0; i < channels.length; i++) {
      final var value = channels[i];
      result += weights[i] * (value <= 0.04045 ? value / 12.92
          : Math.pow((value + 0.055) / 1.055, 2.4));
    }
    return result;
  }

  private static String css(Color color) {
    return String.format(Locale.ROOT, "#%06x", color.getRGB() & 0xffffff);
  }
}

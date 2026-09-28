/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.util;

import com.cburch.logisim.prefs.AppPreferences;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;
import javax.swing.AbstractAction;
import javax.swing.DefaultListModel;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;

/**
 * The list of interface languages.
 *
 * <p>A language is applied when it is clicked or when Enter is pressed on it, not whenever the
 * selection moves: applying on every selection change relabelled the whole program on each arrow
 * key while the user was only looking for their language.
 */
@SuppressWarnings("rawtypes")
class LocaleSelector extends JList implements LocaleListener {
  private static class LocaleOption implements Runnable {
    private final Locale locale;
    private String text;

    LocaleOption(Locale locale) {
      this.locale = locale;
      update(locale);
    }

    @Override
    public void run() {
      if (!LocaleManager.getLocale().equals(locale)) {
        LocaleManager.setLocale(locale);
        AppPreferences.LOCALE.set(locale.getLanguage());
      }
    }

    @Override
    public String toString() {
      return text;
    }

    void update(Locale current) {
      text =
          (current != null && current.equals(locale))
              ? locale.getDisplayName(locale)
              : locale.getDisplayName(locale) + " / " + locale.getDisplayName(current);
    }
  }

  private static final long serialVersionUID = 1L;

  /** The action, bound to Enter, that applies the selected language. */
  static final String APPLY_ACTION = "applyLocale";

  private final LocaleOption[] items;

  @SuppressWarnings("unchecked")
  LocaleSelector(Locale[] locales) {
    setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    final var model = new DefaultListModel<LocaleOption>();
    items = new LocaleOption[locales.length];
    for (var i = 0; i < locales.length; i++) {
      items[i] = new LocaleOption(locales[i]);
      model.addElement(items[i]);
    }
    setModel(model);
    setVisibleRowCount(Math.min(items.length, 8));
    LocaleManager.addLocaleListener(this);
    localeChanged();
    addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent event) {
            if (SwingUtilities.isLeftMouseButton(event)
                && locationToIndex(event.getPoint()) == getSelectedIndex()) {
              applySelection();
            }
          }
        });
    getInputMap(JComponent.WHEN_FOCUSED)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), APPLY_ACTION);
    getActionMap()
        .put(
            APPLY_ACTION,
            new AbstractAction() {
              private static final long serialVersionUID = 1L;

              @Override
              public void actionPerformed(ActionEvent event) {
                applySelection();
              }
            });
    // Browsing with the arrow keys and leaving without applying must not leave a language
    // highlighted that is not the one in use.
    addFocusListener(
        new FocusAdapter() {
          @Override
          public void focusLost(FocusEvent event) {
            localeChanged();
          }
        });
  }

  /** Switches to the selected language, if it is not already the one in use. */
  void applySelection() {
    final var opt = (LocaleOption) getSelectedValue();
    if (opt != null) opt.run();
  }

  @Override
  public void localeChanged() {
    final var current = LocaleManager.getLocale();
    LocaleOption sel = null;
    for (final var item : items) {
      item.update(current);
      if (current.equals(item.locale)) sel = item;
    }
    // The current locale often carries a country ("en_US") that the offered one ("en") lacks.
    for (final var item : items) {
      if (sel == null && current.getLanguage().equals(item.locale.getLanguage())) sel = item;
    }
    if (sel != null) {
      setSelectedValue(sel, true);
    } else {
      clearSelection();
    }
    repaint();
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell;

import com.cburch.logisim.gui.theme.AppIcons;
import com.cburch.logisim.gui.theme.Tokens;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * The search box at the top of a side panel.
 *
 * <p>One component for every panel that filters a list, rather than each building its own row of a
 * label, a field and a button. The magnifier and the clear button sit inside the field, so the
 * panel does not lose a line of height to them.
 */
public class FilterField extends JTextField {

  private static final long serialVersionUID = 1L;

  /**
   * How long typing pauses before the list is filtered.
   *
   * <p>Filtering walks every library, which is slow enough to feel on a large project; waiting for
   * a gap in the typing keeps each keystroke immediate.
   */
  private static final int DEBOUNCE_MS = 120;

  private final javax.swing.Timer debounce;
  private final Consumer<String> onChange;
  private boolean pendingChange;

  public FilterField(String placeholder, Consumer<String> onChange) {
    this.onChange = onChange;
    putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
    putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);

    final var leading = new JLabel(AppIcons.colored(AppIcons.Id.SEARCH, 14, Tokens.mutedForeground()));
    putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_COMPONENT, leading);

    debounce = new javax.swing.Timer(DEBOUNCE_MS, event -> flushPendingChange());
    debounce.setRepeats(false);
    getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent event) {
                pendingChange = true;
                debounce.restart();
              }

              @Override
              public void removeUpdate(DocumentEvent event) {
                pendingChange = true;
                debounce.restart();
              }

              @Override
              public void changedUpdate(DocumentEvent event) {
                pendingChange = true;
                debounce.restart();
              }
            });

    // Escape clears the filter, which is quicker than selecting the text to delete it.
    getInputMap(WHEN_FOCUSED).put(
        javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "filter.clear");
    getActionMap().put("filter.clear", new AbstractAction() {
      @Override
      public boolean isEnabled() {
        // A disabled local binding falls through to the ancestor/root cancel action.
        return !getText().isEmpty();
      }

      @Override
      public void actionPerformed(ActionEvent event) {
        clear();
      }
    });
  }

  /**
   * What Enter and the arrow keys should do.
   *
   * <p>A filter with one match left is a question the user has already answered; pressing Enter
   * used to do nothing at all, and the arrow keys did not reach the list below, so the only way on
   * was the mouse.
   *
   * @param onAccept run on Enter — take the first match
   * @param onNavigate run on Down or Up with the direction, to move into the list
   */
  public void setKeyboardHandoff(Runnable onAccept, java.util.function.IntConsumer onNavigate) {
    if (onAccept != null) {
      registerKeyboardAction(
          event -> {
            flushPendingChange();
            onAccept.run();
          },
          javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
          WHEN_FOCUSED);
    }
    if (onNavigate != null) {
      registerKeyboardAction(
          event -> {
            flushPendingChange();
            onNavigate.accept(1);
          },
          javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0),
          WHEN_FOCUSED);
      registerKeyboardAction(
          event -> {
            flushPendingChange();
            onNavigate.accept(-1);
          },
          javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0),
          WHEN_FOCUSED);
    }
  }

  /** Empties the field and reports the change at once, without waiting for the pause. */
  public void clear() {
    if (getText().isEmpty()) return;
    setText("");
    flushPendingChange();
  }

  /** Reports the latest text before a key action can act on stale results. */
  public void flushPendingChange() {
    debounce.stop();
    if (!pendingChange) return;
    pendingChange = false;
    onChange.accept(getText());
  }

  @Override
  public void removeNotify() {
    debounce.stop();
    super.removeNotify();
  }

  @Override
  public void addNotify() {
    super.addNotify();
    if (pendingChange) debounce.restart();
  }

  /** Sets the greyed-out prompt, after a change of language. */
  public void setPlaceholder(String placeholder) {
    putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, placeholder);
  }

  /** Adds a button inside the field, at its trailing edge. */
  public void setTrailingButton(JButton button) {
    putClientProperty(FlatClientProperties.TEXT_FIELD_TRAILING_COMPONENT, button);
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.util;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.gui.theme.AppIcons;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.prefs.PrefMonitorKeyStroke;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.Locale;
import java.util.prefs.BackingStoreException;
import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/** A shortcut draft is applied explicitly; leaving the editor never changes the binding. */
public class JHotkeyInput extends JPanel {
  private static final long serialVersionUID = 1L;
  private final JButton resetButton = new JButton();
  private final JButton applyButton = new JButton();
  private final JPanel actions = new JPanel(new GridLayout(1, 2));
  public final JTextField hotkeyInputField;
  private transient PrefMonitorKeyStroke boundKeyStroke;
  private KeyStroke draft;
  private boolean clearRequested;
  private boolean editing;
  private String previousData;

  public JHotkeyInput(JFrame frame, String text) {
    super(new BorderLayout());
    previousData = text.toUpperCase(Locale.ROOT);
    hotkeyInputField = new JTextField(previousData, 12);
    hotkeyInputField.setHorizontalAlignment(SwingConstants.CENTER);
    // The key listener owns the draft, not JTextField's ordinary text editing bindings.
    hotkeyInputField.setEditable(false);
    hotkeyInputField.addFocusListener(new FocusAdapter() {
      @Override
      public void focusGained(FocusEvent event) {
        enterEditMode();
      }

      @Override
      public void focusLost(FocusEvent event) {
        final var next = event.getOppositeComponent();
        if (!event.isTemporary() && next != null
            && !SwingUtilities.isDescendingFrom(next, JHotkeyInput.this)) {
          exitEditModeWithoutRefresh();
        }
      }
    });
    hotkeyInputField.addKeyListener(new KeyAdapter() {
      @Override
      public void keyPressed(KeyEvent event) {
        // Consume before validation or window-level Escape bindings can handle this key.
        event.consume();
        if (event.getKeyCode() == KeyEvent.VK_ESCAPE) {
          exitEditModeWithoutRefresh();
          return;
        }
        enterEditMode();
        capture(event);
      }

      @Override
      public void keyReleased(KeyEvent event) {
        // In particular, Escape's release must not erase the restored binding.
        event.consume();
      }

      @Override
      public void keyTyped(KeyEvent event) {
        event.consume();
      }
    });
    getInputMap(WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "cancelCapture");
    getActionMap().put("cancelCapture", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent event) {
        exitEditModeWithoutRefresh();
      }
    });
    applyButton.addActionListener(event -> applyChanges());
    resetButton.addActionListener(event -> exitEditMode());
    actions.add(applyButton);
    actions.add(resetButton);
    actions.setVisible(false);
    add(hotkeyInputField, BorderLayout.CENTER);
    add(actions, BorderLayout.LINE_END);
    refreshPresentation();
    Theme.addListener(this, this::refreshPresentation);
  }

  private void refreshPresentation() {
    applyButton.setIcon(AppIcons.get(AppIcons.Id.CHECK));
    resetButton.setIcon(AppIcons.get(AppIcons.Id.CLOSE));
    applyButton.setToolTipText(S.get("hotkeyApply"));
    resetButton.setToolTipText(S.get("hotkeyCancel"));
    applyButton.getAccessibleContext().setAccessibleName(S.get("hotkeyApply"));
    resetButton.getAccessibleContext().setAccessibleName(S.get("hotkeyCancel"));
    hotkeyInputField.setToolTipText(S.get("hotkeyCaptureHelp"));
    revalidate();
    repaint();
  }

  @Override
  public Dimension getPreferredSize() {
    final var size = super.getPreferredSize();
    // Reserve action space even outside capture, so neighbouring controls never jump.
    if (actions != null && !actions.isVisible()) size.width += actions.getPreferredSize().width;
    return size;
  }

  private void enterEditMode() {
    if (editing || boundKeyStroke == null || !isEnabled()) return;
    editing = true;
    clearDraft();
    hotkeyInputField.setText("");
    actions.setVisible(true);
    revalidate();
  }

  private void clearDraft() {
    draft = null;
    clearRequested = false;
    applyButton.setEnabled(false);
  }

  private void capture(KeyEvent event) {
    if (!editing) return;
    final var code = event.getKeyCode();
    if (code == KeyEvent.VK_CONTROL || code == KeyEvent.VK_ALT
        || code == KeyEvent.VK_ALT_GRAPH || code == KeyEvent.VK_SHIFT
        || code == KeyEvent.VK_META || code == KeyEvent.VK_UNDEFINED) return;
    clearDraft();
    if (code == KeyEvent.VK_BACK_SPACE || code == KeyEvent.VK_DELETE) {
      clearRequested = true;
      hotkeyInputField.setText("");
      applyButton.setEnabled(true);
      return;
    }
    final var modifiers = event.getModifiersEx();
    if (!boundKeyStroke.metaCheckPass(modifiers)) {
      showError(S.get("hotkeyErrMeta",
          InputEvent.getModifiersExText(AppPreferences.hotkeyMenuMask)));
      return;
    }
    final var conflict =
        AppPreferences.hotkeyCheckConflict(boundKeyStroke.getName(), code, modifiers);
    if (!conflict.isEmpty()) {
      showError(conflict);
      return;
    }
    draft = KeyStroke.getKeyStroke(code, modifiers);
    final var prefix = InputEvent.getModifiersExText(modifiers);
    setText((prefix.isEmpty() ? "" : prefix + "+") + KeyEvent.getKeyText(code));
    applyButton.setEnabled(true);
  }

  private void showError(String message) {
    hotkeyInputField.setText("");
    OptionPane.showMessageDialog(this, message, S.get("hotkeyOptTitle"), OptionPane.ERROR_MESSAGE);
  }

  public void exitEditModeWithoutRefresh() {
    editing = false;
    clearDraft();
    actions.setVisible(false);
    hotkeyInputField.setText(previousData);
    revalidate();
    repaint();
  }

  public void exitEditMode() {
    exitEditModeWithoutRefresh();
  }

  private void applyChanges() {
    if (boundKeyStroke == null || (!clearRequested && draft == null)) return;
    boundKeyStroke.set(clearRequested ? null : draft);
    previousData = hotkeyInputField.getText();
    try {
      AppPreferences.getPrefs().flush();
      AppPreferences.hotkeySync();
    } catch (BackingStoreException exception) {
      throw new RuntimeException(exception);
    }
    exitEditModeWithoutRefresh();
  }

  public void setText(String text) {
    hotkeyInputField.setText(text.toUpperCase(Locale.ROOT));
  }

  public void resetText(String text) {
    previousData = text.toUpperCase(Locale.ROOT);
    exitEditModeWithoutRefresh();
  }

  public void setBoundKeyStroke(PrefMonitorKeyStroke keyStroke) {
    boundKeyStroke = keyStroke;
    hotkeyInputField.getAccessibleContext().setAccessibleName(S.get(keyStroke.getName()));
  }

  public PrefMonitorKeyStroke getBoundKeyStroke() {
    return boundKeyStroke;
  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    if (hotkeyInputField != null) hotkeyInputField.setEnabled(enabled);
    if (!enabled && actions != null) exitEditModeWithoutRefresh();
  }

  public void setApplyEnabled(boolean enabled) {
    applyButton.setEnabled(enabled);
  }
}

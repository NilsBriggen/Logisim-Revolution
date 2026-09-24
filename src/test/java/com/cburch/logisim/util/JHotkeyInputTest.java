/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cburch.logisim.gui.generic.FormLayoutTestSupport;
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.prefs.PrefMonitorKeyStroke;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.FocusEvent;
import java.awt.event.KeyEvent;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class JHotkeyInputTest {
  @Test
  void escapeCancelsBeforeValidationAndReleaseKeepsPreviousText() throws Exception {
    FormLayoutTestSupport.atScale(1, () -> {
      final var binding = mock(PrefMonitorKeyStroke.class);
      when(binding.getName()).thenReturn("hotkeySimAutoPropagate");
      final var input = new JHotkeyInput(null, "Ctrl+K");
      input.setBoundKeyStroke(binding);
      focus(input);
      clearInvocations(binding);
      try (final var preferences = mockStatic(AppPreferences.class);
          final var dialogs = mockStatic(OptionPane.class)) {
        key(input, KeyEvent.VK_ESCAPE);
        assertEquals("CTRL+K", input.hotkeyInputField.getText());
        verifyNoInteractions(binding);
        preferences.verifyNoInteractions();
        dialogs.verifyNoInteractions();
      }
    });
  }

  @Test
  void escapeDiscardsClearDraftWithoutWritingPreferences() throws Exception {
    FormLayoutTestSupport.atScale(1, () -> {
      final var binding = mock(PrefMonitorKeyStroke.class);
      when(binding.getName()).thenReturn("hotkeySimAutoPropagate");
      final var input = new JHotkeyInput(null, "Ctrl+K");
      input.setBoundKeyStroke(binding);
      focus(input);
      clearInvocations(binding);
      key(input, KeyEvent.VK_DELETE);
      assertEquals("", input.hotkeyInputField.getText());
      key(input, KeyEvent.VK_ESCAPE);
      assertEquals("CTRL+K", input.hotkeyInputField.getText());
      verifyNoInteractions(binding);
    });
  }

  @Test
  void escapeAlsoCancelsWhenFocusIsOnAnActionButton() throws Exception {
    FormLayoutTestSupport.atScale(1, () -> {
      final var binding = mock(PrefMonitorKeyStroke.class);
      when(binding.getName()).thenReturn("hotkeySimAutoPropagate");
      final var input = new JHotkeyInput(null, "Ctrl+K");
      input.setBoundKeyStroke(binding);
      focus(input);
      key(input, KeyEvent.VK_DELETE);
      final var action = input.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
          .get(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0));
      input.getActionMap().get(action).actionPerformed(new ActionEvent(input, 0, "Escape"));
      assertEquals("CTRL+K", input.hotkeyInputField.getText());
    });
  }

  @Test
  void invalidCaptureParentsFeedbackAndEscapeStillRestoresBinding() throws Exception {
    FormLayoutTestSupport.atScale(1, () -> {
      final var binding = mock(PrefMonitorKeyStroke.class);
      when(binding.getName()).thenReturn("hotkeySimAutoPropagate");
      when(binding.metaCheckPass(anyInt())).thenReturn(false);
      final var input = new JHotkeyInput(null, "Ctrl+K");
      input.setBoundKeyStroke(binding);
      focus(input);
      try (final var dialogs = mockStatic(OptionPane.class)) {
        key(input, KeyEvent.VK_A);
        dialogs.verify(() -> OptionPane.showMessageDialog(same(input),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
            eq(OptionPane.ERROR_MESSAGE)));
        key(input, KeyEvent.VK_ESCAPE);
        assertEquals("CTRL+K", input.hotkeyInputField.getText());
      }
    });
  }

  @Test
  void actionButtonsFitAndExplicitMetricsDoNotStickAfterScaleDown() throws Exception {
    FormLayoutTestSupport.atScale(2, () -> {
      final var binding = mock(PrefMonitorKeyStroke.class);
      when(binding.getName()).thenReturn("hotkeySimAutoPropagate");
      final var input = new JHotkeyInput(null, "Ctrl+K");
      input.setBoundKeyStroke(binding);
      focus(input);
      input.setSize(input.getPreferredSize());
      FormLayoutTestSupport.layoutTree(input);
      assertButtonsFit(input);
      final var largeHeight = input.getPreferredSize().height;
      UiScale.setFactor(1);
      SwingUtilities.updateComponentTreeUI(input);
      input.setSize(input.getPreferredSize());
      FormLayoutTestSupport.layoutTree(input);
      assertButtonsFit(input);
      assertTrue(input.getPreferredSize().height < largeHeight);
    });
  }

  private static void assertButtonsFit(Container container) {
    for (final var child : container.getComponents()) {
      if (child instanceof JButton button) {
        assertFalse(button.getAccessibleContext().getAccessibleName().isBlank());
        assertTrue(button.getWidth() >= button.getMinimumSize().width);
        assertTrue(button.getHeight() >= button.getMinimumSize().height);
      } else if (child instanceof Container nested) {
        assertButtonsFit(nested);
      }
    }
  }

  private static void focus(JHotkeyInput input) {
    final var event = new FocusEvent(input.hotkeyInputField, FocusEvent.FOCUS_GAINED);
    for (final var listener : input.hotkeyInputField.getFocusListeners()) {
      listener.focusGained(event);
    }
  }

  private static void key(JHotkeyInput input, int code) {
    final var pressed = new KeyEvent(input.hotkeyInputField, KeyEvent.KEY_PRESSED, 0, 0,
        code, KeyEvent.CHAR_UNDEFINED);
    final var released = new KeyEvent(input.hotkeyInputField, KeyEvent.KEY_RELEASED, 0, 0,
        code, KeyEvent.CHAR_UNDEFINED);
    for (final var listener : input.hotkeyInputField.getKeyListeners()) {
      listener.keyPressed(pressed);
      listener.keyReleased(released);
    }
    assertTrue(pressed.isConsumed());
    assertTrue(released.isConsumed());
  }
}

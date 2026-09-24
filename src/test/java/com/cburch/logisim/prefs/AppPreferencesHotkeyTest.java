/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.prefs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.gui.menu.Menu;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class AppPreferencesHotkeyTest {
  private static final int MENU = AppPreferences.hotkeyMenuMask;
  private static final int SHIFT_MENU = InputEvent.SHIFT_DOWN_MASK | MENU;

  @Test
  void hotkeyUpdatesUseEdtAndAllowRegistrationChangesDuringNotification() throws Exception {
    final var called = new CountDownLatch(2);
    final var offEdt = new AtomicBoolean();
    final Menu first = new Menu() {
      @Override
      protected void computeEnabled() {}

      @Override
      public void hotkeyUpdate() {
        if (!SwingUtilities.isEventDispatchThread()) offEdt.set(true);
        AppPreferences.gui_sync_objects.remove(this);
        called.countDown();
      }
    };
    final Menu second = new Menu() {
      @Override
      protected void computeEnabled() {}

      @Override
      public void hotkeyUpdate() {
        if (!SwingUtilities.isEventDispatchThread()) offEdt.set(true);
        called.countDown();
      }
    };
    AppPreferences.gui_sync_objects.add(first);
    AppPreferences.gui_sync_objects.add(second);
    try {
      AppPreferences.hotkeySync();
      assertTrue(called.await(2, TimeUnit.SECONDS));
      assertFalse(offEdt.get());
    } finally {
      AppPreferences.gui_sync_objects.remove(first);
      AppPreferences.gui_sync_objects.remove(second);
    }
  }

  @Test
  void rejectsSwingShowToolTipShortcut() {
    final var conflict =
        AppPreferences.hotkeyCheckConflict(
            "hotkeyToolSelect1", KeyEvent.VK_F1, InputEvent.CTRL_DOWN_MASK);

    assertFalse(conflict.isEmpty());
  }

  /**
   * The menu accelerators that used to be hard-coded became preferences. Their defaults must be
   * exactly the literals they replaced, or a shortcut people have used for years changes under
   * them without anyone touching the hotkey settings.
   */
  @Test
  void rebindableMenuShortcutsDefaultToTheLiteralsTheyReplaced() {
    final var expected = new LinkedHashMap<PrefMonitor<KeyStroke>, KeyStroke>();
    expected.put(AppPreferences.HOTKEY_FILE_NEW, KeyStroke.getKeyStroke(KeyEvent.VK_N, MENU));
    expected.put(AppPreferences.HOTKEY_FILE_MERGE, KeyStroke.getKeyStroke(KeyEvent.VK_M, MENU));
    expected.put(AppPreferences.HOTKEY_FILE_OPEN, KeyStroke.getKeyStroke(KeyEvent.VK_O, MENU));
    expected.put(
        AppPreferences.HOTKEY_WINDOW_CLOSE, KeyStroke.getKeyStroke(KeyEvent.VK_W, SHIFT_MENU));
    expected.put(AppPreferences.HOTKEY_FILE_SAVE, KeyStroke.getKeyStroke(KeyEvent.VK_S, MENU));
    expected.put(
        AppPreferences.HOTKEY_FILE_SAVE_AS, KeyStroke.getKeyStroke(KeyEvent.VK_S, SHIFT_MENU));
    expected.put(AppPreferences.HOTKEY_FILE_QUIT, KeyStroke.getKeyStroke(KeyEvent.VK_Q, MENU));
    expected.put(AppPreferences.HOTKEY_EDIT_CUT, KeyStroke.getKeyStroke(KeyEvent.VK_X, MENU));
    expected.put(AppPreferences.HOTKEY_EDIT_COPY, KeyStroke.getKeyStroke(KeyEvent.VK_C, MENU));
    expected.put(AppPreferences.HOTKEY_EDIT_PASTE, KeyStroke.getKeyStroke(KeyEvent.VK_V, MENU));
    expected.put(
        AppPreferences.HOTKEY_EDIT_DELETE, KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0));
    expected.put(
        AppPreferences.HOTKEY_EDIT_SELECT_ALL, KeyStroke.getKeyStroke(KeyEvent.VK_A, MENU));
    expected.put(AppPreferences.HOTKEY_EDIT_RAISE, KeyStroke.getKeyStroke(KeyEvent.VK_UP, MENU));
    expected.put(
        AppPreferences.HOTKEY_EDIT_LOWER, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, MENU));
    expected.put(
        AppPreferences.HOTKEY_EDIT_RAISE_TOP, KeyStroke.getKeyStroke(KeyEvent.VK_UP, SHIFT_MENU));
    expected.put(
        AppPreferences.HOTKEY_EDIT_LOWER_BOTTOM,
        KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, SHIFT_MENU));

    for (final var entry : expected.entrySet()) {
      final var monitor = (PrefMonitorKeyStroke) entry.getKey();
      assertEquals(
          List.of(entry.getValue()), monitor.getDefaultList(), monitor.getName());
    }
  }

  @Test
  void newlyAssignedShortcutsRequireTheMenuKey() {
    for (final var monitor :
        List.of(
            AppPreferences.HOTKEY_FILE_PREFERENCES,
            AppPreferences.HOTKEY_PROJ_ADD_CIRCUIT,
            AppPreferences.HOTKEY_PROJ_ANALYZE,
            AppPreferences.HOTKEY_PROJ_STATS,
            AppPreferences.HOTKEY_PROJ_OPTIONS)) {
      final var keyStroke = (PrefMonitorKeyStroke) monitor;
      assertTrue(keyStroke.needMetaKey(), keyStroke.getName());
      assertFalse(keyStroke.getDefaultList().isEmpty(), keyStroke.getName());
    }
  }

  /** Two menu shortcuts with the same default would leave one of them unreachable. */
  @Test
  void noTwoMenuShortcutsShareADefault() {
    final var owners = new HashMap<KeyStroke, String>();
    for (final var monitor : allHotkeys()) {
      if (!monitor.needMetaKey()) continue;
      for (final var keyStroke : monitor.getDefaultList()) {
        final var previous = owners.put(keyStroke, monitor.getName());
        assertTrue(
            previous == null,
            () -> keyStroke + " is the default of both " + previous + " and " + monitor.getName());
      }
    }
  }

  @Test
  void resetRestoresEveryDeclaredDefault() {
    final var hotkeys = allHotkeys();
    final var saved = new HashMap<PrefMonitorKeyStroke, KeyStroke[]>();
    for (final var monitor : hotkeys) {
      saved.put(monitor, monitor.getList().toArray(KeyStroke[]::new));
    }
    try {
      final var probe = (PrefMonitorKeyStroke) AppPreferences.HOTKEY_FILE_PREFERENCES;
      probe.set(KeyStroke.getKeyStroke(KeyEvent.VK_F12, SHIFT_MENU));
      assertNotEquals(probe.getDefaultList(), probe.getList());

      AppPreferences.resetHotkeys();

      for (final var monitor : hotkeys) {
        assertEquals(monitor.getDefaultList(), monitor.getList(), monitor.getName());
      }
    } finally {
      for (final var entry : saved.entrySet()) {
        entry.getKey().set(entry.getValue());
      }
    }
  }

  private static List<PrefMonitorKeyStroke> allHotkeys() {
    final var result = new ArrayList<PrefMonitorKeyStroke>();
    for (final var field : AppPreferences.class.getDeclaredFields()) {
      if (!Modifier.isStatic(field.getModifiers()) || !field.getName().startsWith("HOTKEY_")) {
        continue;
      }
      try {
        result.add((PrefMonitorKeyStroke) field.get(null));
      } catch (IllegalAccessException e) {
        throw new AssertionError(field.getName(), e);
      }
    }
    assertFalse(result.isEmpty());
    return result;
  }
}

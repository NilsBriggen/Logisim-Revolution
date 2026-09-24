/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.theme;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.prefs.AppPreferences;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ThemeTest {

  private final String originalMode = AppPreferences.THEME_MODE.get();

  @AfterEach
  void restoreMode() {
    AppPreferences.THEME_MODE.set(originalMode);
  }

  @Test
  void unknownOrMissingPreferenceFollowsTheSystem() {
    assertEquals(Theme.Mode.SYSTEM, Theme.Mode.fromKey(null));
    assertEquals(Theme.Mode.SYSTEM, Theme.Mode.fromKey(""));
    assertEquals(Theme.Mode.SYSTEM, Theme.Mode.fromKey("solarized"));
  }

  @Test
  void storedValuesRoundTripThroughTheirKeys() {
    for (final var mode : Theme.Mode.values()) {
      assertEquals(mode, Theme.Mode.fromKey(mode.key()), mode.name());
      assertEquals(mode.key(), mode.key().toLowerCase(java.util.Locale.ROOT));
    }
    assertEquals(Theme.Mode.DARK, Theme.Mode.fromKey(" Dark "));
  }

  @Test
  void anExplicitChoiceDoesNotConsultTheDesktop() {
    assertFalse(Theme.resolveDark(Theme.Mode.LIGHT));
    assertTrue(Theme.resolveDark(Theme.Mode.DARK));
  }

  @Test
  void installedThemeTracksExplicitChoices() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      Theme.apply(Theme.Mode.DARK);
      assertTrue(Theme.isDark());
      Theme.apply(Theme.Mode.LIGHT);
      assertFalse(Theme.isDark());
    });
  }

  @Test
  void themeModePreferenceOnlyAcceptsTheThreeModes() {
    assertEquals(3, AppPreferences.THEME_MODES.length);
    for (final var value : AppPreferences.THEME_MODES) {
      assertEquals(value, Theme.Mode.fromKey(value).key());
    }
  }

  @Test
  void tokensAnswerWithoutAThemeInstalled() {
    // Painting code reads these during tests and from the command-line tools, where no look and
    // feel is set; a null colour there would be a crash in a paint method.
    assertNotNull(Tokens.accent());
    assertNotNull(Tokens.error());
    assertNotNull(Tokens.warning());
    assertNotNull(Tokens.divider());
    assertNotNull(Tokens.iconForeground());
    assertNotNull(Tokens.statusBarBackground());
    assertNotNull(Tokens.canvasHalo());
  }

  @Test
  void failingListenerDoesNotStopTheOthers() {
    final var reached = new boolean[1];
    final Runnable boom =
        () -> {
          throw new IllegalStateException("listener under test");
        };
    final Runnable good = () -> reached[0] = true;
    Theme.addListener(boom);
    Theme.addListener(good);
    try {
      Theme.fireChanged();
    } finally {
      Theme.removeListener(boom);
      Theme.removeListener(good);
    }

    assertTrue(reached[0]);
  }

  @Test
  void listenerSeesUpdatedDrawingPaletteOnTheEdt() throws Exception {
    final var observed = new AtomicBoolean();
    final Runnable listener = () -> {
      observed.set(SwingUtilities.isEventDispatchThread()
          && AppPreferences.COMPONENT_COLOR.get() == AppPreferences.DARK_COMPONENT_COLOR);
    };
    SwingUtilities.invokeAndWait(() -> {
      Theme.apply(Theme.Mode.LIGHT);
    });
    // Drain the light-theme refresh before observing the following dark-theme transaction.
    SwingUtilities.invokeAndWait(() -> {});
    try {
      SwingUtilities.invokeAndWait(() -> {
        Theme.addListener(listener);
        Theme.apply(Theme.Mode.DARK);
        assertFalse(observed.get(), "refresh must wait for the initiating event to return");
      });
      SwingUtilities.invokeAndWait(() -> {});
      assertTrue(observed.get(), "listeners must see the new palette, not the outgoing one");
    } finally {
      Theme.removeListener(listener);
    }
  }

  @Test
  void stalledDesktopProbeDoesNotBlockSwing() throws Exception {
    final var entered = new CountDownLatch(1);
    final var release = new CountDownLatch(1);
    final var finished = new CountDownLatch(1);
    final var offEdt = new AtomicBoolean();
    try {
      SwingUtilities.invokeAndWait(() -> Theme.requestSystemTheme(() -> {
        offEdt.set(!SwingUtilities.isEventDispatchThread());
        entered.countDown();
        try {
          release.await(5, TimeUnit.SECONDS);
          return Optional.of(true);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return Optional.empty();
        } finally {
          finished.countDown();
        }
      }));
      assertTrue(entered.await(2, TimeUnit.SECONDS));
      final var ping = new CountDownLatch(1);
      SwingUtilities.invokeLater(ping::countDown);
      assertTrue(ping.await(1, TimeUnit.SECONDS), "desktop I/O blocked Swing");
      assertTrue(offEdt.get());
      SwingUtilities.invokeAndWait(() -> Theme.apply(Theme.Mode.LIGHT));
    } finally {
      release.countDown();
      assertTrue(finished.await(2, TimeUnit.SECONDS));
    }
  }

  @Test
  void ownerSubscriptionEndsOnDisposeAndResumesOnReattach() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var owner = new JPanel();
      final var calls = new AtomicInteger();
      Theme.addListener(owner, calls::incrementAndGet);
      Theme.fireChanged();
      assertEquals(0, calls.get());
      owner.addNotify();
      final var attached = calls.get();
      Theme.fireChanged();
      assertEquals(attached + 1, calls.get());
      owner.removeNotify();
      final var detached = calls.get();
      Theme.fireChanged();
      assertEquals(detached, calls.get());
      owner.addNotify();
      assertTrue(calls.get() > detached);
      owner.removeNotify();
    });
  }
}

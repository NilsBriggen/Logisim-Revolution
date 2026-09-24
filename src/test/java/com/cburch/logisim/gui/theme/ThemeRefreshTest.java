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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Projects;
import com.cburch.logisim.util.DesktopScale;
import com.cburch.logisim.util.UiScale;
import com.formdev.flatlaf.util.UIScale;
import java.awt.event.MouseEvent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.prefs.Preferences;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JSlider;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import javax.swing.plaf.basic.BasicComboBoxUI;
import javax.swing.plaf.basic.BasicSliderUI;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class ThemeRefreshTest {
  @Test
  void comboNavigationKeepsItsDelegateAliveAndCoalescesThemeAndScaleRefreshes() throws Exception {
    withFixture(fixture -> {
      final var combo = new JComboBox<>(new String[] {"Light", "Dark", "Light again"});
      final var delegate = new NavigatingComboUi();
      combo.setUI(delegate);
      fixture.observe(combo);
      combo.addActionListener(event ->
          Theme.apply(combo.getSelectedIndex() == 1 ? Theme.Mode.DARK : Theme.Mode.LIGHT));

      delegate.next();
      assertTrue(delegate.isAttached(), "dark selection destroyed its active delegate");
      assertSame(delegate, combo.getUI());
      delegate.next();
      assertTrue(delegate.isAttached(), "light selection destroyed its active delegate");
      assertSame(delegate, combo.getUI());
      fixture.scale = 1.6;
      UiScale.refresh();
      assertEquals(0, fixture.refreshes, "custom widgets must also wait for the event to finish");
    }, fixture -> {
      assertEquals(1, fixture.refreshes, "theme and scale changes must share one refresh");
      assertNotSame(fixture.originalDelegate, fixture.component.getUI());
      assertEquals(1.6, UiScale.factor(), 0.00001);
    });
  }

  @Test
  void sliderReleaseKeepsItsDelegateAliveUntilAllChangeListenersReturn() throws Exception {
    withFixture(fixture -> {
      final var slider = new JSlider();
      final var delegate = new ReleasingSliderUi(slider);
      slider.setUI(delegate);
      fixture.observe(slider);
      slider.addChangeListener(event -> {
        if (!slider.getValueIsAdjusting()) UiScale.refresh();
      });
      for (final var scale : new double[] {1.0, 1.6}) {
        slider.setValueIsAdjusting(true);
        fixture.scale = scale;
        // Exercise the exact BasicSliderUI handler tail from the native stack trace.
        delegate.release();
        assertTrue(delegate.isAttached(), "release destroyed the delegate before repaint");
        assertSame(delegate, slider.getUI());
        assertEquals(scale, UiScale.factor(), 0.00001);
        assertEquals(0, fixture.refreshes);
      }
    }, fixture -> {
      assertEquals(1, fixture.refreshes);
      assertNotSame(fixture.originalDelegate, fixture.component.getUI());
      assertEquals(1.6, UiScale.factor(), 0.00001);
    });
  }

  private static void withFixture(Consumer<Fixture> event, Consumer<Fixture> after)
      throws Exception {
    final var reference = new AtomicReference<Fixture>();
    try {
      SwingUtilities.invokeAndWait(() -> {
        final var fixture = new Fixture();
        reference.set(fixture);
        Theme.apply(Theme.Mode.LIGHT);
      });
      SwingUtilities.invokeAndWait(() -> {});
      SwingUtilities.invokeAndWait(() -> event.accept(reference.get()));
      // This event follows the queued refresh, without sleeping or a timing assumption.
      SwingUtilities.invokeAndWait(() -> after.accept(reference.get()));
    } finally {
      SwingUtilities.invokeAndWait(() -> {
        if (reference.get() != null) reference.get().close();
      });
      SwingUtilities.invokeAndWait(() -> {});
    }
  }

  private static final class Fixture implements AutoCloseable {
    private final LookAndFeel originalLaf = UIManager.getLookAndFeel();
    private final float originalZoom = UIScale.getZoomFactor();
    private final Theme.Mode originalMode = Theme.isDark() ? Theme.Mode.DARK : Theme.Mode.LIGHT;
    private final MockedStatic<AppPreferences> preferences = mockStatic(AppPreferences.class);
    private final MockedStatic<Projects> projects = mockStatic(Projects.class);
    private final MockedStatic<DesktopScale> desktop = mockStatic(DesktopScale.class);
    private double scale = 2.0;
    private JComponent component;
    private ComponentUI originalDelegate;
    private Runnable listener;
    private int refreshes;

    private Fixture() {
      final var store = mock(Preferences.class);
      preferences.when(AppPreferences::getPrefs).thenReturn(store);
      preferences.when(AppPreferences::getAutoScaleFactor).thenAnswer(ignored -> scale);
      // No preference writes, real projects, external processes or asynchronous desktop probes.
      desktop.when(DesktopScale::detect).thenReturn(new CompletableFuture<Double>());
    }

    private void observe(JComponent component) {
      this.component = component;
      originalDelegate = component.getUI();
      listener = () -> {
        refreshes++;
        // Headless components are not in Window.getWindows(): refresh this local tree explicitly.
        SwingUtilities.updateComponentTreeUI(component);
      };
      Theme.addListener(listener);
    }

    @Override
    public void close() {
      if (listener != null) Theme.removeListener(listener);
      try {
        Theme.apply(originalMode);
        UIManager.setLookAndFeel(originalLaf);
        UIScale.setZoomFactor(originalZoom);
      } catch (javax.swing.UnsupportedLookAndFeelException exception) {
        throw new AssertionError(exception);
      } finally {
        desktop.close();
        projects.close();
        preferences.close();
      }
    }
  }

  private static final class NavigatingComboUi extends BasicComboBoxUI {
    private void next() {
      selectNextPossibleValue();
    }

    private boolean isAttached() {
      return comboBox != null;
    }
  }

  private static final class ReleasingSliderUi extends BasicSliderUI {
    private ReleasingSliderUi(JSlider slider) {
      super(slider);
    }

    private void release() {
      createTrackListener(slider).mouseReleased(new MouseEvent(slider,
          MouseEvent.MOUSE_RELEASED, 1, 0, 0, 0, 1, false, MouseEvent.BUTTON1));
    }

    private boolean isAttached() {
      return slider != null;
    }
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.prefs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.gui.generic.FormLayoutTestSupport;
import com.cburch.logisim.gui.generic.SettingsForm;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.UiScale;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.ui.FlatSliderUI;
import com.formdev.flatlaf.util.UIScale;
import java.awt.Container;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JSlider;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.ComponentUI;
import org.junit.jupiter.api.Test;

class SettingsPagesTest {

  @Test
  void theAutosaveIntervalIsCommittedWhenTheFieldIsLeft() throws Exception {
    final var saved = AppPreferences.AUTOSAVE_INTERVAL.get();
    try {
      SwingUtilities.invokeAndWait(
          () -> {
            AppPreferences.AUTOSAVE_INTERVAL.set(30);
            final var page = new AutosaveOptions(null);
            page.getIntervalField().setText("45");
            // Leaving the field commits, as Enter does.
            page.commitInterval();
            assertEquals(45, AppPreferences.AUTOSAVE_INTERVAL.get());
            page.getIntervalField().setText("not a number");
            page.commitInterval();
            assertEquals(45, AppPreferences.AUTOSAVE_INTERVAL.get());
            assertEquals("45", page.getIntervalField().getText(), "invalid text is put back");
          });
      // The focus listener that calls the commit is what the page relies on.
      SwingUtilities.invokeAndWait(
          () -> {
            final var page = new AutosaveOptions(null);
            final var field = page.getIntervalField();
            field.setText("50");
            for (final var listener : field.getFocusListeners()) {
              listener.focusLost(
                  new java.awt.event.FocusEvent(field, java.awt.event.FocusEvent.FOCUS_LOST));
            }
            assertEquals(50, AppPreferences.AUTOSAVE_INTERVAL.get());
          });
    } finally {
      AppPreferences.AUTOSAVE_INTERVAL.set(saved);
    }
  }

  @Test
  void theFirstTrackClickPersistsTheSnappedScale() throws Exception {
    withScale(1.0, null, page -> {
      final var slider = page.getZoomSlider();
      layoutSlider(page);
      assertTrue(slider.getSnapToTicks());
      assertEquals(WindowOptions.ZOOM_STEP, slider.getMinorTickSpacing());
      clickSlider(slider, 175);
      assertScale(page, 175);
    });
  }

  @Test
  void draggingPreviewsTheScaleAndCommitsOnRelease() throws Exception {
    for (final var snapOnRelease : new boolean[] {false, true}) {
      withScale(1.0, null, page -> {
        UIManager.getLookAndFeelDefaults().put("Slider.snapToTicksOnReleased", snapOnRelease);
        final var slider = page.getZoomSlider();
        layoutSlider(page);
        mouse(slider, MouseEvent.MOUSE_PRESSED, 100);
        mouse(slider, MouseEvent.MOUSE_DRAGGED, 162);
        assertTrue(slider.getValueIsAdjusting());
        assertEquals(slider.getValue() + "%", page.getZoomReadout());
        assertNull(AppPreferences.getPrefs().get("Scale", null), "dragging only previews");
        mouse(slider, MouseEvent.MOUSE_RELEASED, 162);
        assertFalse(slider.getValueIsAdjusting());
        assertScale(page, 150);
      });
    }
  }

  @Test
  void keyboardBindingsCommitQuarterStepsAndEndpoints() throws Exception {
    withScale(1.0, null, page -> {
      layoutSlider(page);
      final var slider = page.getZoomSlider();
      key(slider, KeyEvent.VK_RIGHT);
      assertScale(page, 125);
      key(slider, KeyEvent.VK_PAGE_UP);
      assertScale(page, 150);
      key(slider, KeyEvent.VK_END);
      assertScale(page, 300);
      key(slider, KeyEvent.VK_HOME);
      assertScale(page, 100);
    });
  }

  @Test
  void autoShowsTheEffectiveScaleAcrossLayoutAndDelegateRefresh() throws Exception {
    withScale(1.6, null, page -> {
      layoutSlider(page);
      clickSlider(page.getZoomSlider(), 200);
      assertScale(page, 200);
      autoButton(page).doClick(0);
      assertEquals("160%", page.getZoomReadout());
      assertEquals(1.6, UiScale.factor(), 0.00001);
      SwingUtilities.updateComponentTreeUI(page);
      layoutSlider(page);
      assertEquals("160%", page.getZoomReadout());
      assertNull(AppPreferences.getPrefs().get("Scale", null));
      // A subsequent scale refresh must update the display even without a slider gesture.
      UiScale.setFactor(1.7);
      SwingUtilities.updateComponentTreeUI(page);
      layoutSlider(page);
      assertEquals(175, page.getZoomSlider().getValue());
      assertEquals("170%", page.getZoomReadout());
      assertNull(AppPreferences.getPrefs().get("Scale", null));
      // A later choice of the same manual value still leaves Auto.
      clickSlider(page.getZoomSlider(), 200);
      assertScale(page, 200);
    });
  }

  @Test
  void autoRefreshesTheReadoutEvenWhenTheThumbDoesNotMove() throws Exception {
    withScale(1.6, 1.5, page -> {
      layoutSlider(page);
      assertEquals(150, page.getZoomSlider().getValue());
      autoButton(page).doClick(0);
      assertEquals(150, page.getZoomSlider().getValue());
      assertEquals("160%", page.getZoomReadout());
      assertNull(AppPreferences.getPrefs().get("Scale", null));
    });
  }

  @Test
  void openingTheWindowPageNeverWritesTheScale() throws Exception {
    for (final Double explicit : new Double[] {null, 1.6}) {
      withScale(1.6, explicit, page -> {
        final var saved = AppPreferences.getPrefs().get("Scale", null);
        layoutSlider(page);
        SwingUtilities.updateComponentTreeUI(page);
        layoutSlider(page);
        assertEquals(150, page.getZoomSlider().getValue());
        assertEquals("160%", page.getZoomReadout());
        assertEquals(saved, AppPreferences.getPrefs().get("Scale", null));
      });
    }
  }

  @Test
  void openingAndUnboundKeysNeverPersistLayoutSnapping() throws Exception {
    for (final Double explicit : new Double[] {null, 1.6}) {
      withScale(1.6, explicit, page -> {
        final var saved = AppPreferences.getPrefs().get("Scale", null);
        key(page.getZoomSlider(), KeyEvent.VK_SHIFT);
        layoutSlider(page);
        assertEquals(150, page.getZoomSlider().getValue());
        assertEquals("160%", page.getZoomReadout());
        assertEquals(saved, AppPreferences.getPrefs().get("Scale", null));
      });
    }
  }

  @Test
  void programmaticChangesAfterAutoNeverBecomeUserChoices() throws Exception {
    withScale(1.6, null, page -> {
      layoutSlider(page);
      clickSlider(page.getZoomSlider(), 200);
      autoButton(page).doClick(0);
      // Model synchronization and the UI's subsequent snap must not revive the old gesture.
      page.getZoomSlider().setValue(185);
      layoutSlider(page);
      assertNull(AppPreferences.getPrefs().get("Scale", null));
      assertEquals("160%", page.getZoomReadout());
    });
  }

  private static void assertScale(WindowOptions page, int percent) {
    assertEquals(percent, page.getZoomSlider().getValue());
    assertEquals(percent + "%", page.getZoomReadout());
    assertEquals(percent / 100.0, AppPreferences.getPrefs().getDouble("Scale", Double.NaN));
    assertEquals(percent / 100.0, UiScale.factor(), 0.00001);
  }

  private static void layoutSlider(WindowOptions page) {
    page.setSize(800, 600);
    FormLayoutTestSupport.layoutTree(page);
    final var slider = page.getZoomSlider();
    final var image = new BufferedImage(slider.getWidth(), slider.getHeight(),
        BufferedImage.TYPE_INT_ARGB);
    final var graphics = image.createGraphics();
    try {
      slider.paint(graphics);
    } finally {
      graphics.dispose();
    }
  }

  private static void clickSlider(JSlider slider, int percent) {
    mouse(slider, MouseEvent.MOUSE_PRESSED, percent);
    mouse(slider, MouseEvent.MOUSE_RELEASED, percent);
  }

  private static void mouse(JSlider slider, int id, int percent) {
    final var point = ((ScaleSliderUI) slider.getUI()).position(percent);
    final var released = id == MouseEvent.MOUSE_RELEASED;
    slider.dispatchEvent(new MouseEvent(slider, id, System.currentTimeMillis(),
        released ? 0 : MouseEvent.BUTTON1_DOWN_MASK, point.x, point.y, 1, false,
        id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1));
  }

  private static void key(JSlider slider, int code) {
    // Redispatch reaches both KeyListeners and Swing's installed InputMap/ActionMap in headless mode.
    for (final var id : new int[] {KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED}) {
      KeyboardFocusManager.getCurrentKeyboardFocusManager().redispatchEvent(slider,
          new KeyEvent(slider, id, System.currentTimeMillis(), 0, code, KeyEvent.CHAR_UNDEFINED));
    }
  }

  private static JButton autoButton(Container root) {
    for (final var child : root.getComponents()) {
      if (child instanceof JButton button
          && "set-auto-scale-factor".equals(button.getActionCommand())) return button;
      if (child instanceof Container container) {
        final var found = autoButton(container);
        if (found != null) return found;
      }
    }
    return null;
  }

  /** Real FlatLaf event handling; only exposes geometry so tests click the actual track/thumb. */
  public static class ScaleSliderUI extends FlatSliderUI {
    public static ComponentUI createUI(JComponent component) {
      return new ScaleSliderUI();
    }

    Point position(int percent) {
      return new Point(xPositionForValue(percent), thumbRect.y + thumbRect.height / 2);
    }
  }

  private static void withScale(
      double autoScale, Double explicit, Consumer<WindowOptions> assertions) throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var saved = AppPreferences.getPrefs().get("Scale", null);
      final var savedMonitor = AppPreferences.SCALE_FACTOR.get();
      final var savedLaf = UIManager.getLookAndFeel();
      final var savedZoom = UIScale.getZoomFactor();
      final var savedProperty = System.getProperty("flatlaf.uiScale");
      try {
        System.setProperty("flatlaf.uiScale", Double.toString(autoScale));
        FlatLightLaf.setup();
        UIManager.getLookAndFeelDefaults().put("SliderUI", ScaleSliderUI.class.getName());
        UIScale.setZoomFactor(1);
        if (explicit == null) {
          AppPreferences.getPrefs().remove("Scale");
        } else {
          AppPreferences.SCALE_FACTOR.set(explicit);
          AppPreferences.getPrefs().putDouble("Scale", explicit);
        }
        UiScale.refresh();
        assertions.accept(new WindowOptions(null));
      } finally {
        AppPreferences.SCALE_FACTOR.set(savedMonitor);
        if (saved == null) {
          AppPreferences.getPrefs().remove("Scale");
        } else {
          AppPreferences.getPrefs().put("Scale", saved);
        }
        if (savedProperty == null) {
          System.clearProperty("flatlaf.uiScale");
        } else {
          System.setProperty("flatlaf.uiScale", savedProperty);
        }
        try {
          UIManager.setLookAndFeel(savedLaf);
        } catch (javax.swing.UnsupportedLookAndFeelException exception) {
          throw new AssertionError(exception);
        } finally {
          UIScale.setZoomFactor(savedZoom);
        }
      }
    });
  }

  /** Every preferences page is laid out by the one shared form. */
  @Test
  void everyPageUsesTheSharedFormLayout() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          for (final var tab : PreferencesFrame.TABS) {
            final var page = tab.factory().apply(null);
            assertTrue(contains(page, SettingsForm.class), tab.title() + " has no SettingsForm");
          }
        });
  }

  private static boolean contains(Container root, Class<?> type) {
    for (final var child : root.getComponents()) {
      if (type.isInstance(child)) return true;
      if (child instanceof Container container && contains(container, type)) return true;
    }
    return false;
  }
}

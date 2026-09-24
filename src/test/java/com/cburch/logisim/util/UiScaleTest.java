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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.TestBase;
import com.cburch.logisim.gui.theme.AppIcons;
import com.formdev.flatlaf.util.UIScale;
import javax.swing.JCheckBox;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests the interface scale facade. */
public class UiScaleTest extends TestBase {
  private UiScaleTestSupport.NativeFonts nativeFonts;

  @BeforeEach
  public void isolateFonts() throws Exception {
    nativeFonts = new UiScaleTestSupport.NativeFonts();
  }

  @AfterEach
  public void restoreScale() throws Exception {
    nativeFonts.close();
  }

  @Test
  public void identityAtUnitScale() {
    UiScaleTestSupport.setScaleFactor(1.0);
    assertEquals(1.0, UiScale.factor());
    assertEquals(16, UiScale.scaled(16));
  }

  @Test
  public void scalingIsProportional() {
    UiScaleTestSupport.setScaleFactor(2.0);
    assertEquals(32, UiScale.scaled(16));
    assertEquals(2.0f, UiScale.scaled(1.0f));
  }

  @Test
  public void scalingIsMonotonic() {
    var previous = Integer.MIN_VALUE;
    for (final var scale : new double[] {1.0, 1.25, 1.5, 2.0, 3.0}) {
      UiScaleTestSupport.setScaleFactor(scale);
      final var current = UiScale.scaled(10);
      assertTrue(current > previous, "scale " + scale + " did not increase the result");
      previous = current;
    }
  }

  /** Round tripping must land back on the original value, or layouts drift as scale changes. */
  @Test
  public void unscaledInvertsScaled() {
    UiScaleTestSupport.setScaleFactor(2.0);
    assertEquals(16, UiScale.unscaled(UiScale.scaled(16)));
  }

  @Test
  public void iconMetricsFollowTheScaleFactor() {
    UiScaleTestSupport.setScaleFactor(1.0);
    final var baseIcon = UiScale.iconSize();
    UiScaleTestSupport.setScaleFactor(2.0);
    assertEquals(2 * baseIcon, UiScale.iconSize());
    assertTrue(UiScale.iconBorder() > 0);
  }

  @Test
  public void nativeAndCustomMetricsShareOneScaleIncludingLiveRoundTrips() throws Exception {
    UiScaleTestSupport.setScaleFactor(1.0);
    final var baseFont = UIManager.getFont("Label.font").getSize2D();
    final var icon = AppIcons.get(AppIcons.Id.RUN, 16);
    for (final var scale : new double[] {1.0, 1.25, 1.6, 2.0, 2.5, 1.0, 1.6}) {
      UiScaleTestSupport.setScaleFactor(scale);
      SwingUtilities.invokeAndWait(() -> {
        assertEquals(scale, UiScale.factor(), 0.00001);
        assertEquals(UIScale.scale(16), icon.getIconWidth(), "held SVG scales only once");
        assertEquals(Math.round(16 * scale), UiScale.scaled(16));
        assertEquals(Math.round(baseFont * scale), UiFonts.body().getSize());
        assertEquals(UIManager.getFont("Label.font").getSize(),
            UIManager.getFont("MenuItem.acceleratorFont").getSize());
        final var check = new JCheckBox("Scale");
        assertTrue(check.getPreferredSize().height >= check.getFontMetrics(check.getFont()).getHeight());
      });
    }
  }
}

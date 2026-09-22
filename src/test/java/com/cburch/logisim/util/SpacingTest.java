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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Tests the shared spacing scale. */
public class SpacingTest extends TestBase {

  @AfterEach
  public void restoreScale() {
    UiScaleTestSupport.setScaleFactor(1.0);
  }

  /** The steps must stay ordered, otherwise call sites cannot reason about which is larger. */
  @Test
  public void stepsAreStrictlyIncreasing() {
    UiScaleTestSupport.setScaleFactor(1.0);
    assertTrue(Spacing.xs() < Spacing.sm());
    assertTrue(Spacing.sm() < Spacing.md());
    assertTrue(Spacing.md() < Spacing.lg());
    assertTrue(Spacing.lg() < Spacing.xl());
  }

  /** A gap that collapses to zero would silently remove the padding it was asked for. */
  @Test
  public void stepsAreNeverZero() {
    for (final var scale : new double[] {1.0, 1.25, 1.5, 2.0, 3.0}) {
      UiScaleTestSupport.setScaleFactor(scale);
      assertTrue(Spacing.xs() > 0, "xs collapsed at scale " + scale);
      assertTrue(Spacing.sm() > 0, "sm collapsed at scale " + scale);
      assertTrue(Spacing.md() > 0, "md collapsed at scale " + scale);
    }
  }

  @Test
  public void stepsFollowTheScaleFactor() {
    UiScaleTestSupport.setScaleFactor(1.0);
    assertEquals(Spacing.MD, Spacing.md());

    UiScaleTestSupport.setScaleFactor(2.0);
    assertEquals(2 * Spacing.MD, Spacing.md());
  }

  @Test
  public void borderUsesTheSameValueOnEachSide() {
    UiScaleTestSupport.setScaleFactor(1.5);
    final var insets = Spacing.border(Spacing.SM).getBorderInsets(null);
    final var expected = UiScale.scaled(Spacing.SM);
    assertEquals(expected, insets.top);
    assertEquals(expected, insets.left);
    assertEquals(expected, insets.bottom);
    assertEquals(expected, insets.right);
  }

  @Test
  public void formGapsAreWiderThanTheyAreTall() {
    UiScaleTestSupport.setScaleFactor(1.0);
    final var gaps = Spacing.formGaps();
    assertTrue(gaps.left > gaps.top);
    assertEquals(gaps.top, gaps.bottom);
    assertEquals(gaps.left, gaps.right);
  }
}

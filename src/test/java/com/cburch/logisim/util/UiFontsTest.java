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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.TestBase;
import java.awt.Font;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Tests the shared typographic scale. */
public class UiFontsTest extends TestBase {
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
  public void everyRoleReturnsAUsableFont() {
    UiScaleTestSupport.setScaleFactor(1.0);
    for (final var font :
        new Font[] {
          UiFonts.body(),
          UiFonts.bodyBold(),
          UiFonts.small(),
          UiFonts.heading(),
          UiFonts.mono(),
          UiFonts.monoBold()
        }) {
      assertNotNull(font);
      assertTrue(font.getSize() > 0, "font collapsed to zero size");
    }
  }

  @Test
  public void rolesAreOrderedBySize() {
    UiScaleTestSupport.setScaleFactor(1.0);
    assertTrue(UiFonts.small().getSize() <= UiFonts.body().getSize());
    assertTrue(UiFonts.body().getSize() < UiFonts.heading().getSize());
  }

  @Test
  public void boldRolesAreBold() {
    assertTrue(UiFonts.bodyBold().isBold());
    assertTrue(UiFonts.heading().isBold());
    assertTrue(UiFonts.monoBold().isBold());
  }

  @Test
  public void sizesFollowTheScaleFactor() {
    UiScaleTestSupport.setScaleFactor(1.0);
    final var baseSize = UiFonts.body().getSize();
    UiScaleTestSupport.setScaleFactor(2.0);
    assertTrue(UiFonts.body().getSize() > baseSize);
    assertEquals(UIManager.getFont("Label.font").getSize(), UiFonts.body().getSize(),
        "the already scaled LaF font must not be multiplied again");
  }

  /** Tabular data must align, so the monospaced roles have to stay monospaced. */
  @Test
  public void monoUsesTheLogicalMonospacedFamily() {
    assertEquals(Font.MONOSPACED, UiFonts.mono().getFamily());
    assertEquals(UiFonts.body().getSize(), UiFonts.mono().getSize());
  }
}

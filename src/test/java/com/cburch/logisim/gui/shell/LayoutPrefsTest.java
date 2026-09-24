/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.prefs.AbstractPreferences;
import org.junit.jupiter.api.Test;

class LayoutPrefsTest {

  @Test
  void freshProfilesDoNotConvertLegacyDefaultFractionsIntoNarrowPanels() {
    for (final var scale : new double[] {1.0, 1.6, 2.0}) {
      final var prefs = new MemoryPreferences();
      LayoutPrefs.migrate(prefs, 1280, 1600, scale);
      assertNull(prefs.get("shell.sideWidth", null));
      assertNull(prefs.get("shell.bottomHeight", null));
      assertNull(prefs.get("shell.sideVisible", null));
      assertEquals(LayoutPrefs.DEFAULT_SIDE_WIDTH,
          prefs.getInt("shell.sideWidth", LayoutPrefs.DEFAULT_SIDE_WIDTH));
    }
  }

  @Test
  void explicitLegacyFractionsAndVisibilityMigrateOnlyOnce() {
    final var prefs = new MemoryPreferences();
    prefs.putDouble("windowMainSplit", 0.25);
    prefs.putDouble("windowRightSplit", 0.75);
    prefs.putBoolean("windowExplorerVisible", false);
    LayoutPrefs.migrate(prefs, 1920, 1280, 1.6);
    assertEquals(300, prefs.getInt("shell.sideWidth", 0));
    assertEquals(200, prefs.getInt("shell.bottomHeight", 0));
    assertFalse(prefs.getBoolean("shell.sideVisible", true));
    LayoutPrefs.migrate(prefs, 4000, 3000, 2.0);
    assertEquals(300, prefs.getInt("shell.sideWidth", 0));
    assertEquals(200, prefs.getInt("shell.bottomHeight", 0));
  }

  @Test
  void legacyFractionsNeverOverwriteNewerExplicitShellDimensions() {
    final var prefs = new MemoryPreferences();
    prefs.putDouble("windowMainSplit", 0.25);
    prefs.putDouble("windowRightSplit", 0.75);
    prefs.putInt("shell.sideWidth", 640);
    prefs.putInt("shell.bottomHeight", 480);
    prefs.putBoolean("shell.sideVisible", false);
    LayoutPrefs.migrate(prefs, 1920, 1280, 1.6);
    assertEquals(400, prefs.getInt("shell.sideWidth", 0));
    assertEquals(300, prefs.getInt("shell.bottomHeight", 0));
    assertFalse(prefs.getBoolean("shell.sideVisible", true));
  }

  @Test
  void existingDeviceSizesMigrateOnceAndFreshPreferencesKeepLogicalDefaults() {
    for (final var scale : new double[] {1.0, 1.6, 2.0}) {
      final var prefs = new MemoryPreferences();
      prefs.putInt("shell.sideWidth", (int) Math.round(300 * scale));
      prefs.putInt("shell.inspectorWidth", (int) Math.round(280 * scale));
      prefs.putInt("shell.bottomHeight", (int) Math.round(200 * scale));
      LayoutPrefs.migrateSizes(prefs, scale);
      assertEquals(300, prefs.getInt("shell.sideWidth", 0));
      assertEquals(280, prefs.getInt("shell.inspectorWidth", 0));
      assertEquals(200, prefs.getInt("shell.bottomHeight", 0));
      LayoutPrefs.migrateSizes(prefs, 2.0);
      assertEquals(300, prefs.getInt("shell.sideWidth", 0), "migration must run only once");
    }
    final var fresh = new MemoryPreferences();
    LayoutPrefs.migrateSizes(fresh, 2.0);
    assertNull(fresh.get("shell.sideWidth", null));
    assertNull(fresh.get("shell.bottomHeight", null));
  }

  /** An entirely in-memory store: these tests never read or write user preferences. */
  private static final class MemoryPreferences extends AbstractPreferences {
    private final Map<String, String> values = new HashMap<>();

    MemoryPreferences() {
      super(null, "");
    }

    @Override
    protected void putSpi(String key, String value) {
      values.put(key, value);
    }

    @Override
    protected String getSpi(String key) {
      return values.get(key);
    }

    @Override
    protected void removeSpi(String key) {
      values.remove(key);
    }

    @Override
    protected void removeNodeSpi() {
      values.clear();
    }

    @Override
    protected String[] keysSpi() {
      return values.keySet().toArray(String[]::new);
    }

    @Override
    protected String[] childrenNamesSpi() {
      return new String[0];
    }

    @Override
    protected AbstractPreferences childSpi(String name) {
      throw new UnsupportedOperationException();
    }

    @Override
    protected void syncSpi() {}

    @Override
    protected void flushSpi() {}
  }

  @Test
  void panelSizeIsAlwaysUsableAndNeverWiderThanAWindow() {
    assertEquals(LayoutPrefs.MIN_PANEL, LayoutPrefs.clamp(0));
    assertEquals(LayoutPrefs.MIN_PANEL, LayoutPrefs.clamp(-40));
    assertEquals(LayoutPrefs.MAX_PANEL, LayoutPrefs.clamp(10_000));
    assertEquals(300, LayoutPrefs.clamp(300));
  }

  @Test
  void theShippedSizesAreWithinTheAllowedRange() {
    for (final var size :
        new int[] {
          LayoutPrefs.DEFAULT_SIDE_WIDTH,
          LayoutPrefs.DEFAULT_INSPECTOR_WIDTH,
          LayoutPrefs.DEFAULT_BOTTOM_HEIGHT
        }) {
      assertEquals(size, LayoutPrefs.clamp(size), "default outside the clamp range: " + size);
    }
  }

  @Test
  void theRangeLeavesRoomForBothSidePanelsOnASmallScreen() {
    // Two panels at their smallest plus a usable canvas has to fit a 1024-wide window, or the
    // shipped layout is unusable on a laptop.
    assertTrue(2 * LayoutPrefs.MIN_PANEL + 400 <= 1024,
        "minimum panels leave too little for the canvas");
  }
}

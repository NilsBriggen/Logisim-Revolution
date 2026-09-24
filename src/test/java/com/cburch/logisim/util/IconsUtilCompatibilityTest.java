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
import static org.junit.jupiter.api.Assertions.assertNull;

import com.cburch.logisim.prefs.AppPreferences;
import javax.swing.ImageIcon;
import org.junit.jupiter.api.Test;

class IconsUtilCompatibilityTest {
  @Test
  void legacyLoaderRetainsImageIconReturnTypeAndImageAccess() throws Exception {
    // This assignment also guards source compatibility for plug-ins using ImageIcon.getImage().
    final ImageIcon icon = IconsUtil.getIcon("subcirc.gif");
    assertNotNull(icon);
    assertNotNull(icon.getImage());
    assertEquals(ImageIcon.class, IconsUtil.class.getMethod("getIcon", String.class).getReturnType());
    assertEquals(AppPreferences.getIconSize(), icon.getIconWidth());
    assertEquals(AppPreferences.getIconSize(), icon.getIconHeight());
  }

  @Test
  void unknownResourceStillReturnsNull() {
    assertNull(IconsUtil.getIcon("missing-extension-icon.gif"));
  }
}

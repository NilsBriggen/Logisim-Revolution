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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.util.UiScale;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import org.junit.jupiter.api.Test;

/**
 * A missing icon file draws nothing at all and is easy to miss on screen, so every declared icon
 * is checked to exist and to be a real SVG.
 */
class AppIconsTest {

  @Test
  void everyIconHasAFile() throws IOException {
    final var missing = new ArrayList<String>();
    for (final var id : AppIcons.Id.values()) {
      final var url = AppIconsTest.class.getClassLoader().getResource(id.resource());
      if (url == null) {
        missing.add(id.name() + " -> " + id.resource());
        continue;
      }
      final var content = new String(url.openStream().readAllBytes(), StandardCharsets.UTF_8);
      assertTrue(content.contains("<svg"), id.name() + " is not an SVG");
      assertTrue(content.contains("currentColor"), id.name() + " does not follow the theme colour");
    }

    assertTrue(missing.isEmpty(), "icons without a file: " + missing);
  }

  @Test
  void noTwoIconsShareAGlyphFile() {
    final var seen = new HashSet<String>();
    final var duplicates = new ArrayList<String>();
    for (final var id : AppIcons.Id.values()) {
      if (!seen.add(id.resource())) duplicates.add(id.name());
    }

    // Sharing a file is allowed only where two roles really are the same picture; today none are.
    assertTrue(duplicates.isEmpty(), "icons reusing another's file: " + duplicates);
  }

  @Test
  void iconsAreBuiltAtTheRequestedScaledSize() {
    final var icon = AppIcons.get(AppIcons.Id.RUN, 16);

    assertNotNull(icon);
    assertEquals(UiScale.scaled(16), icon.getIconWidth());
    assertEquals(UiScale.scaled(16), icon.getIconHeight());
  }

  @Test
  void repeatedRequestsReuseTheSameIcon() {
    AppIcons.clearCache();
    final var first = AppIcons.get(AppIcons.Id.SAVE, 16);
    final var second = AppIcons.get(AppIcons.Id.SAVE, 16);

    assertEquals(first, second);
  }

  @Test
  void tintedIconUsesTheColourItWasGiven() {
    final var icon = AppIcons.colored(AppIcons.Id.ERROR, 16, java.awt.Color.RED);

    assertNotNull(icon.getColorFilter());
    assertEquals(java.awt.Color.RED, icon.getColorFilter().filter(java.awt.Color.BLACK));
  }

  @Test
  void variantsNeverMutateAnExistingNormalIcon() {
    final var normal = AppIcons.get(AppIcons.Id.RUN, 16);
    final var filter = normal.getColorFilter();
    final var foreground = filter.filter(java.awt.Color.BLACK);
    final var accented = AppIcons.accented(AppIcons.Id.RUN, 16);
    final var disabled = AppIcons.disabled(AppIcons.Id.RUN, 16);
    assertNotSame(normal, accented);
    assertNotSame(normal, disabled);
    assertNotSame(accented, disabled);
    assertSame(filter, normal.getColorFilter());
    assertEquals(foreground, normal.getColorFilter().filter(java.awt.Color.BLACK));
    assertEquals(Tokens.accent(), accented.getColorFilter().filter(java.awt.Color.BLACK));
  }
}

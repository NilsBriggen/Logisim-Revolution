/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.search.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.gui.prefs.PreferencesFrame;
import com.cburch.logisim.gui.search.SearchContext;
import com.cburch.logisim.gui.search.SearchProviders;
import com.cburch.logisim.gui.search.SearchQuery;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PreferenceSearchProviderTest {

  @Test
  void isRegisteredAndAvailableEverywhere() {
    assertTrue(new PreferenceSearchProvider().isAvailable(new SearchContext(null, null, null)));
    assertTrue(
        SearchProviders.getAll().stream().anyMatch(PreferenceSearchProvider.class::isInstance));
  }

  @Test
  void offersOneCandidatePerTabAndOpensThatTab() {
    final var opened = new ArrayList<Integer>();
    final var provider =
        new PreferenceSearchProvider(() -> List.of("Window", "Simulation"), opened::add);
    provider.prepare(new SearchContext(null, null, null));

    final var results = provider.search(new SearchQuery(""));
    assertEquals(List.of("Window", "Simulation"),
        results.stream().map(r -> r.candidate().title()).toList());
    results.get(1).candidate().action().run();

    assertEquals(List.of(1), opened);
  }

  @Test
  void realTabTitlesAreListedWithoutBuildingTheWindow() {
    final var titles = PreferencesFrame.getTabTitles();

    assertFalse(titles.isEmpty());
    assertTrue(titles.stream().noneMatch(String::isBlank));
    assertEquals(titles.size(), titles.stream().distinct().count(), "tab titles must be unique");
  }
}

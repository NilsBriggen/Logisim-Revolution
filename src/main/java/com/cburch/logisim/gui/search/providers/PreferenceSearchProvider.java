/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.search.providers;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.gui.prefs.PreferencesFrame;
import com.cburch.logisim.gui.search.IndexedSearchProvider;
import com.cburch.logisim.gui.search.SearchCandidate;
import com.cburch.logisim.gui.search.SearchContext;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** Offers the tabs of the preferences window; choosing one opens the window at that tab. */
public class PreferenceSearchProvider extends IndexedSearchProvider {

  private final Supplier<List<String>> titles;
  private final IntConsumer opener;

  public PreferenceSearchProvider() {
    this(PreferencesFrame::getTabTitles, PreferencesFrame::showPreferences);
  }

  /**
   * @param titles the tab titles, in the order {@code opener} indexes them
   * @param opener shows the preferences window at the given tab
   */
  PreferenceSearchProvider(Supplier<List<String>> titles, IntConsumer opener) {
    this.titles = titles;
    this.opener = opener;
  }

  @Override
  public String getDisplayName() {
    return S.get("searchProviderPreferences");
  }

  @Override
  protected List<SearchCandidate> buildCandidates(SearchContext context) {
    final var tabs = titles.get();
    final var candidates = new ArrayList<SearchCandidate>(tabs.size());
    for (var index = 0; index < tabs.size(); index++) {
      final var tab = index;
      candidates.add(
          SearchCandidate.of(tabs.get(tab), getDisplayName(), true, () -> opener.accept(tab)));
    }
    return candidates;
  }
}

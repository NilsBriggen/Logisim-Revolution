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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.gui.prefs.PreferencesFrame;
import com.cburch.logisim.gui.search.SearchCandidate;
import com.cburch.logisim.gui.search.SearchContext;
import com.cburch.logisim.gui.search.SearchProviders;
import com.cburch.logisim.gui.search.SearchQuery;
import com.cburch.logisim.gui.search.SearchResult;
import com.cburch.logisim.gui.shell.SettingsIndex;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SettingsSearchProviderTest {

  private final JComboBox<String> theme = new JComboBox<>(new String[] {"Light", "Dark"});
  private final JCheckBox tickRate = new JCheckBox("Show tick rate");
  private final List<String> shown = new ArrayList<>();
  private final List<Integer> openedPages = new ArrayList<>();
  private int indexed;
  private SettingsSearchProvider provider;

  @BeforeEach
  void setUp() {
    final var source =
        new SettingsSearchProvider.Source() {
          @Override
          public String name() {
            return "Preferences";
          }

          @Override
          public List<SettingsIndex.Page> index() {
            indexed++;
            return List.of(
                new SettingsIndex.Page("International", "language gate shape", List.of()),
                new SettingsIndex.Page(
                    "Window",
                    "theme tick rate",
                    List.of(
                        new SettingsIndex.Setting("Theme", "", "Dark", "Dark Light", theme),
                        new SettingsIndex.Setting(
                            "Show tick rate", "Status bar", "Off", "Off", tickRate))));
          }

          @Override
          public void show(int page, JComponent control) {
            shown.add(page + ":" + (control == null ? "page" : control.getClass().getSimpleName()));
          }
        };
    provider =
        new SettingsSearchProvider(
            () -> List.of("International", "Window"), openedPages::add, context -> List.of(source));
    provider.prepare(new SearchContext(null, null, null));
  }

  @Test
  void isRegisteredAndAvailableWithoutAProject() {
    assertTrue(provider.isAvailable(new SearchContext(null, null, null)));
    assertTrue(
        SearchProviders.getAll().stream().anyMatch(SettingsSearchProvider.class::isInstance));
  }

  @Test
  void anEmptyQueryListsThePagesWithoutBuildingAnyWindow() {
    final var results = provider.search(new SearchQuery(""));

    assertEquals(List.of("International", "Window"), titles(results));
    assertEquals(0, indexed);
    results.get(1).candidate().action().run();
    assertEquals(List.of(1), openedPages);
  }

  @Test
  void findsAControlShowingItsValueAndPathAndRevealsIt() {
    final var theme = find("theme");

    assertEquals(
        "Preferences" + SearchCandidate.CONTEXT_SEPARATOR + "Window", theme.candidate().context());
    assertEquals("Dark", theme.candidate().hint());
    theme.candidate().action().run();
    assertEquals(List.of("1:JComboBox"), shown);

    final var tick = find("tick rate");
    assertEquals(
        "Preferences" + SearchCandidate.CONTEXT_SEPARATOR + "Window"
            + SearchCandidate.CONTEXT_SEPARATOR + "Status bar",
        tick.candidate().context());
  }

  @Test
  void findsAControlByOneOfItsOptionsBelowAnyMatchOnAName() {
    final var results = provider.search(new SearchQuery("light"));

    final var theme = byTitle(results, "Theme");
    assertEquals(SettingsSearchProvider.KEYWORD_SCORE, theme.score());
    assertEquals(0, theme.highlights().length);
  }

  @Test
  void theWindowNameAloneDoesNotMatchEveryControl() {
    final var results = provider.search(new SearchQuery("preferences"));

    assertNull(byTitle(results, "Theme"));
    assertNull(byTitle(results, "Show tick rate"));
    // The pages still answer to the window's name, as they did before controls were indexed.
    assertFalse(results.isEmpty());
  }

  @Test
  void findsAPageByItsSearchTextAndOpensIt() {
    final var page = byTitle(provider.search(new SearchQuery("gate shape")), "International");

    page.candidate().action().run();
    assertEquals(List.of("0:page"), shown);
  }

  @Test
  void readsTheWindowsOncePerSearchAndAgainForTheNext() {
    provider.search(new SearchQuery("t"));
    provider.search(new SearchQuery("th"));
    provider.search(new SearchQuery("the"));
    assertEquals(1, indexed);

    provider.prepare(new SearchContext(null, null, null));
    provider.search(new SearchQuery("t"));
    assertEquals(2, indexed, "values are read afresh each time the dialog opens");
  }

  @Test
  void windowThatCannotBeBuiltLeavesTheOthersSearchable() {
    final var failing =
        new SettingsSearchProvider(
            List::of,
            page -> {},
            context ->
                List.of(
                    new SettingsSearchProvider.Source() {
                      @Override
                      public String name() {
                        return "Broken";
                      }

                      @Override
                      public List<SettingsIndex.Page> index() {
                        throw new IllegalStateException("headless");
                      }

                      @Override
                      public void show(int page, JComponent control) {}
                    }));
    failing.prepare(new SearchContext(null, null, null));

    assertTrue(failing.search(new SearchQuery("theme")).isEmpty());
  }

  @Test
  void longValuesAreCutShort() {
    final var shortened = SettingsSearchProvider.shorten("x".repeat(100));

    assertEquals(SettingsSearchProvider.MAX_VALUE_LENGTH, shortened.length());
    assertTrue(shortened.endsWith("…"));
  }

  @Test
  void realTabTitlesAreListedWithoutBuildingTheWindow() {
    final var titles = PreferencesFrame.getTabTitles();

    assertFalse(titles.isEmpty());
    assertTrue(titles.stream().noneMatch(String::isBlank));
    assertEquals(titles.size(), titles.stream().distinct().count(), "tab titles must be unique");
  }

  private SearchResult find(String query) {
    final var results = provider.search(new SearchQuery(query));
    assertFalse(results.isEmpty(), query);
    return results.stream()
        .max(java.util.Comparator.comparingInt(SearchResult::score))
        .orElseThrow();
  }

  private static SearchResult byTitle(List<SearchResult> results, String title) {
    return results.stream().filter(r -> r.candidate().title().equals(title)).findFirst().orElse(null);
  }

  private static List<String> titles(List<SearchResult> results) {
    return results.stream().map(r -> r.candidate().title()).toList();
  }
}

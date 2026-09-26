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
import com.cburch.logisim.gui.search.SearchProvider;
import com.cburch.logisim.gui.search.SearchQuery;
import com.cburch.logisim.gui.search.SearchResult;
import com.cburch.logisim.gui.shell.SettingsIndex;
import com.cburch.logisim.gui.shell.SettingsNav;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.Supplier;
import javax.swing.JComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Offers every setting of the Preferences window and of the project's Options window: each page,
 * and each control on it with its current value. Choosing a control opens its window at its page,
 * scrolls to it and focuses it.
 *
 * <p>A control is found by a loose match on its name, or by the words of its page, section,
 * options, hint and value, which rank below any match on a name.
 *
 * <p>The windows are read only once something is typed, and only once per search: building the
 * Preferences window for its index is the one costly step, and an empty query lists the page names
 * without it.
 */
public class SettingsSearchProvider implements SearchProvider {

  static final Logger logger = LoggerFactory.getLogger(SettingsSearchProvider.class);

  /**
   * Score of a setting found only by the words of its page or section, its options, hint or
   * value. Below any real match on a name, so that a search for "dark" lists the Theme setting,
   * but after the settings that are called dark.
   */
  static final int KEYWORD_SCORE = 10;

  /** The longest value shown beside a setting before it is cut short. */
  static final int MAX_VALUE_LENGTH = 32;

  /** A settings window the search looks into. */
  public interface Source {
    /** The window's name, shown before each of its pages. */
    String name();

    /** Its pages and their controls. May build the window, but must not show it. */
    List<SettingsIndex.Page> index();

    /** Shows the window at {@code page}, revealing {@code control} if it is not null. */
    void show(int page, JComponent control);
  }

  /** A candidate with the text it can also be found by. */
  private record Entry(SearchCandidate candidate, int matchFrom, String keywords) {}

  private final Supplier<List<String>> preferencePages;
  private final IntConsumer openPreferencePage;
  private final Function<SearchContext, List<Source>> sourcesFor;

  private List<Source> sources = List.of();
  private List<Entry> entries;

  public SettingsSearchProvider() {
    this(
        PreferencesFrame::getTabTitles,
        PreferencesFrame::showPreferences,
        SettingsSearchProvider::defaultSources);
  }

  /**
   * @param preferencePages the Preferences page titles, listed without building the window
   * @param openPreferencePage shows the Preferences window at the given page
   * @param sourcesFor the windows to index for a search opened from the given context
   */
  SettingsSearchProvider(
      Supplier<List<String>> preferencePages,
      IntConsumer openPreferencePage,
      Function<SearchContext, List<Source>> sourcesFor) {
    this.preferencePages = preferencePages;
    this.openPreferencePage = openPreferencePage;
    this.sourcesFor = sourcesFor;
  }

  private static List<Source> defaultSources(SearchContext context) {
    final var list = new ArrayList<Source>();
    list.add(
        new Source() {
          @Override
          public String name() {
            return S.get("searchProviderPreferences");
          }

          @Override
          public List<SettingsIndex.Page> index() {
            return PreferencesFrame.indexSettings();
          }

          @Override
          public void show(int page, JComponent control) {
            PreferencesFrame.showSetting(page, control);
          }
        });
    final var project = context.project();
    if (project != null && project.getLogisimFile() != null) {
      list.add(
          new Source() {
            @Override
            public String name() {
              return S.get("searchSettingsProjectOptions");
            }

            @Override
            public List<SettingsIndex.Page> index() {
              return project.getOptionsFrame().indexSettings();
            }

            @Override
            public void show(int page, JComponent control) {
              project.getOptionsFrame().showSetting(page, control);
            }
          });
    }
    return list;
  }

  @Override
  public String getDisplayName() {
    return S.get("searchProviderSettings");
  }

  @Override
  public void prepare(SearchContext context) {
    sources = sourcesFor.apply(context);
    // Read again for every search, so the values shown are the current ones.
    entries = null;
  }

  @Override
  public List<SearchResult> search(SearchQuery query) {
    if (query.isEmpty()) return pageList();
    final var results = new ArrayList<SearchResult>();
    for (final var entry : entries()) {
      final var result = IndexedSearchProvider.score(query, entry.candidate(), entry.matchFrom());
      if (result != null) {
        results.add(result);
      } else if (!entry.keywords().isEmpty() && SettingsNav.matches(entry.keywords(), query.text())) {
        results.add(SearchResult.of(entry.candidate(), KEYWORD_SCORE));
      }
    }
    return results;
  }

  /** The Preferences pages, for browsing before anything is typed. */
  private List<SearchResult> pageList() {
    final var titles = preferencePages.get();
    final var context = S.get("searchProviderPreferences");
    final var results = new ArrayList<SearchResult>(titles.size());
    for (var index = 0; index < titles.size(); index++) {
      final var page = index;
      results.add(
          SearchResult.of(
              SearchCandidate.of(
                  titles.get(page), context, true, () -> openPreferencePage.accept(page)),
              0));
    }
    return results;
  }

  private List<Entry> entries() {
    if (entries == null) {
      final var built = new ArrayList<Entry>();
      for (final var source : sources) addEntries(source, built);
      entries = built;
    }
    return entries;
  }

  private static void addEntries(Source source, List<Entry> out) {
    final List<SettingsIndex.Page> pages;
    try {
      pages = source.index();
    } catch (RuntimeException e) {
      // Without a display the windows cannot be built; the other providers still answer.
      logger.warn("Could not index the settings of {}", source.name(), e);
      return;
    }
    final var name = source.name();
    for (var index = 0; index < pages.size(); index++) {
      final var pageIndex = index;
      final var page = pages.get(index);
      out.add(
          new Entry(
              SearchCandidate.of(page.title(), name, true, () -> source.show(pageIndex, null)),
              0,
              page.text() == null ? "" : page.text()));
      for (final var setting : page.settings()) {
        final var context = new StringBuilder(name)
            .append(SearchCandidate.CONTEXT_SEPARATOR)
            .append(page.title());
        if (!setting.section().isEmpty()) {
          context.append(SearchCandidate.CONTEXT_SEPARATOR).append(setting.section());
        }
        final var control = setting.control();
        final var candidate =
            new SearchCandidate(
                setting.title(),
                context.toString(),
                null,
                shorten(setting.value()),
                true,
                () -> source.show(pageIndex, control));
        // Only the name is matched loosely. The page and section are matched word for word, with
        // the options and hint: matched loosely, a page name such as "Colors" found a control for
        // nearly every query.
        out.add(
            new Entry(
                candidate,
                candidate.titleOffset(),
                page.title() + " " + setting.section() + " " + setting.keywords()));
      }
    }
  }

  static String shorten(String value) {
    if (value == null) return "";
    return value.length() <= MAX_VALUE_LENGTH
        ? value
        : value.substring(0, MAX_VALUE_LENGTH - 1).trim() + "…";
  }
}

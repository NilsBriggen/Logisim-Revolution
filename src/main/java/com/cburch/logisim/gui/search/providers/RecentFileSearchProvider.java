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

import com.cburch.logisim.gui.search.IndexedSearchProvider;
import com.cburch.logisim.gui.search.SearchCandidate;
import com.cburch.logisim.gui.search.SearchContext;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.ProjectActions;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Offers the recently opened project files; choosing one opens it. */
public class RecentFileSearchProvider extends IndexedSearchProvider {

  /** How a chosen file is opened; indirected so a headless test can observe it. */
  @FunctionalInterface
  interface Opener {
    void open(SearchContext context, File file);
  }

  private final Supplier<List<File>> files;
  private final Opener opener;

  public RecentFileSearchProvider() {
    this(AppPreferences::getRecentFiles, RecentFileSearchProvider::openInNewWindow);
  }

  RecentFileSearchProvider(Supplier<List<File>> files, Opener opener) {
    this.files = files;
    this.opener = opener;
  }

  private static void openInNewWindow(SearchContext context, File file) {
    final var project = context.project();
    ProjectActions.doOpenReplacingBlank(context.owner(), project, project, file);
  }

  @Override
  public String getDisplayName() {
    return S.get("searchProviderRecent");
  }

  @Override
  protected List<SearchCandidate> buildCandidates(SearchContext context) {
    final var candidates = new ArrayList<SearchCandidate>();
    for (final var file : files.get()) {
      if (file == null) continue;
      final var parent = file.getParentFile();
      candidates.add(
          new SearchCandidate(
              file.getName(),
              getDisplayName(),
              null,
              parent == null ? "" : parent.getPath(),
              true,
              () -> opener.open(context, file)));
    }
    return candidates;
  }
}

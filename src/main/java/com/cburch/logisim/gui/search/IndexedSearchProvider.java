/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.search;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for providers that can enumerate everything they offer up front.
 * <p>
 * Subclasses only build a list of candidates in
 * {@link #buildCandidates(SearchContext)}; the matching, highlighting and scoring
 * are handled here, which keeps ranking consistent across every local provider.
 * Providers that cannot enumerate their contents - anything talking to a remote
 * service - should implement {@link SearchProvider} directly instead.
 */
public abstract class IndexedSearchProvider implements SearchProvider {

  /**
   * Added when the whole query lands inside the candidate's own title rather than
   * its context, so that typing {@code "save"} prefers the Save item over an
   * unrelated item in a menu whose name happens to contain those letters.
   */
  private static final int TITLE_MATCH_BONUS = 25;

  /**
   * Subtracted from unavailable candidates, so they sink below equally good
   * available ones without a strong match being buried under weak noise.
   */
  private static final int DISABLED_PENALTY = 60;

  private List<SearchCandidate> candidates = List.of();

  /**
   * Enumerates everything this provider offers for {@code context}. Called on the
   * event dispatch thread each time the dialog opens.
   */
  protected abstract List<SearchCandidate> buildCandidates(SearchContext context);

  @Override
  public void prepare(SearchContext context) {
    candidates = buildCandidates(context);
  }

  @Override
  public List<SearchResult> search(SearchQuery query) {
    final var results = new ArrayList<SearchResult>();
    for (final var candidate : candidates) {
      if (query.isEmpty()) {
        // No query yet: offer everything, unranked, so the list doubles as a browsable index.
        results.add(SearchResult.of(candidate, 0));
        continue;
      }
      final var result = score(query, candidate, 0);
      if (result != null) results.add(result);
    }
    return results;
  }

  /**
   * Scores {@code candidate} against a non-empty {@code query} the way every indexed provider
   * does, or returns {@code null} when it does not match.
   *
   * @param matchFrom where in {@link SearchCandidate#displayText()} matching starts, so that a
   *     leading part shown only for orientation, such as the name of the window a setting is in,
   *     does not make everything under it match
   */
  public static SearchResult score(SearchQuery query, SearchCandidate candidate, int matchFrom) {
    final var text = candidate.displayText();
    final var from = Math.max(0, Math.min(matchFrom, candidate.titleOffset()));
    final var match = FuzzyMatcher.match(query.text(), from == 0 ? text : text.substring(from));
    if (match == null) return null;
    final var positions = match.positions();
    if (from > 0) {
      for (var i = 0; i < positions.length; i++) positions[i] += from;
    }
    var score = match.score();
    if (positions[0] >= candidate.titleOffset()) score += TITLE_MATCH_BONUS;
    if (!candidate.enabled()) score -= DISABLED_PENALTY;
    return new SearchResult(candidate, score, positions);
  }
}

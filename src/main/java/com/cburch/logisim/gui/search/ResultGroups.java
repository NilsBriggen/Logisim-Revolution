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
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Arranges the results of every provider into the rows of the search list: a heading per
 * provider, its results in order of score, and at most a few of them until the group is expanded.
 *
 * <p>Grouping keeps each kind of result findable. Ranked in one list, a query that happens to
 * match hundreds of placed components or settings would push the one menu command wanted out of
 * sight; grouped and capped, each kind shows its best few, and the group with the best match comes
 * first.
 */
final class ResultGroups {

  /** Results shown per group before the rest are folded behind a "show all" row. */
  static final int GROUP_CAP = 6;

  /** One row of the list. */
  sealed interface Row permits Header, Item, More {
    /** Whether the cursor can rest on the row. */
    default boolean isSelectable() {
      return true;
    }
  }

  /** A group heading: the provider's name and how many results it has. */
  record Header(String title, int count) implements Row {
    @Override
    public boolean isSelectable() {
      return false;
    }
  }

  /** A result. */
  record Item(SearchResult result) implements Row {}

  /** Stands for the results of {@code provider} that are folded away. */
  record More(SearchProvider provider, int hidden) implements Row {}

  /** One provider's results, as it returned them. */
  record Group(SearchProvider provider, List<SearchResult> results) {}

  private ResultGroups() {
    throw new UnsupportedOperationException("Utility class, do not instantiate.");
  }

  /**
   * Lays out {@code groups}, given in provider order.
   *
   * @param ranked whether a query was typed: results and groups are then ordered by score and each
   *     group is capped; otherwise everything is listed in provider order, as a browsable index
   * @param expanded the providers whose groups the user asked to see in full
   */
  static List<Row> arrange(List<Group> groups, boolean ranked, Set<SearchProvider> expanded) {
    final var ordered = new ArrayList<Group>();
    for (final var group : groups) {
      if (group.results().isEmpty()) continue;
      if (!ranked) {
        ordered.add(group);
        continue;
      }
      final var sorted = new ArrayList<>(group.results());
      // Stable, so equal scores keep the order the provider gave, which for menus is menu order.
      sorted.sort(Comparator.comparingInt(SearchResult::score).reversed());
      ordered.add(new Group(group.provider(), sorted));
    }
    if (ranked) {
      // The group holding the best match leads; ties go to the provider's priority, then to
      // registration order.
      ordered.sort(
          Comparator.<Group>comparingInt(group -> group.results().get(0).score())
              .thenComparingInt(group -> group.provider().getPriority())
              .reversed());
    }

    final var rows = new ArrayList<Row>();
    for (final var group : ordered) {
      final var results = group.results();
      rows.add(new Header(group.provider().getDisplayName(), results.size()));
      final var shown =
          ranked && !expanded.contains(group.provider())
              ? Math.min(GROUP_CAP, results.size())
              : results.size();
      for (var index = 0; index < shown; index++) rows.add(new Item(results.get(index)));
      if (shown < results.size()) rows.add(new More(group.provider(), results.size() - shown));
    }
    return rows;
  }

  /** The number of results among {@code rows}, counting folded ones. */
  static int resultCount(List<Row> rows) {
    var count = 0;
    for (final var row : rows) {
      if (row instanceof Header header) count += header.count();
    }
    return count;
  }

  /**
   * The row the cursor lands on moving {@code delta} rows from {@code from}, wrapping around the
   * ends and stepping over headings in the direction of travel; -1 when nothing is selectable.
   */
  static int step(List<Row> rows, int from, int delta) {
    final var size = rows.size();
    if (size == 0 || delta == 0) return firstSelectable(rows);
    final var direction = delta > 0 ? 1 : -1;
    var next = Math.floorMod(from + delta, size);
    for (var tried = 0; tried < size; tried++) {
      if (rows.get(next).isSelectable()) return next;
      next = Math.floorMod(next + direction, size);
    }
    return -1;
  }

  /** The first row the cursor can rest on, or -1. */
  static int firstSelectable(List<Row> rows) {
    for (var index = 0; index < rows.size(); index++) {
      if (rows.get(index).isSelectable()) return index;
    }
    return -1;
  }
}

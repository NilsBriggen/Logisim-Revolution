/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ResultGroupsTest {

  private static final SearchProvider MENUS = provider("Menus", 0);
  private static final SearchProvider PLACED = provider("Placed", 0);
  private static final SearchProvider CIRCUITS = provider("Circuits", 10);

  @Test
  void groupsAreHeadedAndLedByTheGroupWithTheBestMatch() {
    final var rows =
        ResultGroups.arrange(
            List.of(
                new ResultGroups.Group(MENUS, List.of(result("Save", 50))),
                new ResultGroups.Group(PLACED, List.of(result("s1", 20), result("save_reg", 90)))),
            true,
            Set.of());

    assertEquals(
        List.of("# Placed 2", "save_reg", "s1", "# Menus 1", "Save"), describe(rows));
  }

  @Test
  void tiedBestScoreGoesToTheHigherPriorityThenToProviderOrder() {
    final var rows =
        ResultGroups.arrange(
            List.of(
                new ResultGroups.Group(MENUS, List.of(result("a", 40))),
                new ResultGroups.Group(PLACED, List.of(result("b", 40))),
                new ResultGroups.Group(CIRCUITS, List.of(result("c", 40)))),
            true,
            Set.of());

    assertEquals(
        List.of("# Circuits 1", "c", "# Menus 1", "a", "# Placed 1", "b"), describe(rows));
  }

  @Test
  void largeGroupIsCappedUntilExpandedAndKeepsItsTotal() {
    final var many =
        IntStream.range(0, 40).mapToObj(i -> result("gate" + i, 100 - i)).toList();
    final var groups =
        List.of(
            new ResultGroups.Group(PLACED, many),
            new ResultGroups.Group(MENUS, List.of(result("Gate menu", 10))));

    final var capped = ResultGroups.arrange(groups, true, Set.of());
    assertEquals(1 + ResultGroups.GROUP_CAP + 1 + 2, capped.size());
    final var more = assertInstanceOf(ResultGroups.More.class, capped.get(ResultGroups.GROUP_CAP + 1));
    assertEquals(40 - ResultGroups.GROUP_CAP, more.hidden());
    assertEquals("Gate menu", describe(capped).get(capped.size() - 1), "the next group stays in view");
    assertEquals(41, ResultGroups.resultCount(capped));

    final var expanded = ResultGroups.arrange(groups, true, Set.of(PLACED));
    assertEquals(1 + 40 + 2, expanded.size());
  }

  @Test
  void withoutAQueryEverythingIsListedInProviderOrderUncapped() {
    final var many = IntStream.range(0, 20).mapToObj(i -> result("item" + i, 0)).toList();
    final var rows =
        ResultGroups.arrange(
            List.of(
                new ResultGroups.Group(MENUS, many),
                new ResultGroups.Group(CIRCUITS, List.of(result("main", 0))),
                new ResultGroups.Group(PLACED, List.of())),
            false,
            Set.of());

    assertEquals(1 + 20 + 1 + 1, rows.size());
    assertEquals("# Menus 20", describe(rows).get(0));
    assertEquals("# Circuits 1", describe(rows).get(21));
    assertTrue(rows.stream().noneMatch(ResultGroups.More.class::isInstance));
  }

  @Test
  void steppingSkipsHeadingsAndWrapsAround() {
    final var rows =
        ResultGroups.arrange(
            List.of(
                new ResultGroups.Group(MENUS, List.of(result("a", 30), result("b", 20))),
                new ResultGroups.Group(PLACED, List.of(result("c", 10)))),
            true,
            Set.of());
    // 0 header, 1 a, 2 b, 3 header, 4 c

    assertEquals(1, ResultGroups.firstSelectable(rows));
    assertEquals(4, ResultGroups.step(rows, 2, 1), "down over a heading");
    assertEquals(2, ResultGroups.step(rows, 4, -1), "up over a heading");
    assertEquals(1, ResultGroups.step(rows, 4, 1), "down from the last wraps to the first");
    assertEquals(4, ResultGroups.step(rows, 1, -1), "up from the first wraps to the last");
    assertEquals(-1, ResultGroups.step(List.of(new ResultGroups.Header("x", 0)), 0, 1));
  }

  private static List<String> describe(List<ResultGroups.Row> rows) {
    return rows.stream()
        .map(
            row ->
                switch (row) {
                  case ResultGroups.Header header -> "# " + header.title() + " " + header.count();
                  case ResultGroups.Item item -> item.result().candidate().title();
                  case ResultGroups.More more -> "+" + more.hidden();
                })
        .toList();
  }

  private static SearchResult result(String title, int score) {
    return SearchResult.of(SearchCandidate.of(title, "", true, () -> {}), score);
  }

  private static SearchProvider provider(String name, int priority) {
    return new SearchProvider() {
      @Override
      public String getDisplayName() {
        return name;
      }

      @Override
      public List<SearchResult> search(SearchQuery query) {
        return List.of();
      }

      @Override
      public int getPriority() {
        return priority;
      }
    };
  }
}

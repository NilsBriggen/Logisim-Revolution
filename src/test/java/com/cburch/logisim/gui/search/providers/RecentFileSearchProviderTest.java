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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.gui.search.SearchContext;
import com.cburch.logisim.gui.search.SearchProviders;
import com.cburch.logisim.gui.search.SearchQuery;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecentFileSearchProviderTest {

  @Test
  void isRegistered() {
    assertTrue(
        SearchProviders.getAll().stream().anyMatch(RecentFileSearchProvider.class::isInstance));
  }

  @Test
  void listsFileNamesWithTheirFolderAsHintAndOpensTheChosenOne() {
    final var alu = new File(new File("work", "labs"), "alu.circ");
    final var cpu = new File("cpu.circ");
    final var opened = new ArrayList<File>();
    final var context = new SearchContext(null, null, null);
    final var provider =
        new RecentFileSearchProvider(
            () -> Arrays.asList(alu, null, cpu),
            (ctx, file) -> {
              assertSame(context, ctx);
              opened.add(file);
            });
    provider.prepare(context);

    final var candidates =
        provider.search(new SearchQuery("")).stream().map(r -> r.candidate()).toList();

    assertEquals(List.of("alu.circ", "cpu.circ"),
        candidates.stream().map(c -> c.title()).toList());
    assertEquals(alu.getParentFile().getPath(), candidates.get(0).hint());
    assertEquals("", candidates.get(1).hint());
    candidates.get(0).action().run();
    assertEquals(List.of(alu), opened);
  }

  @Test
  void offersNothingWhenThereIsNoHistory() {
    final var provider = new RecentFileSearchProvider(List::of, (ctx, file) -> {});
    provider.prepare(new SearchContext(null, null, null));

    assertTrue(provider.search(new SearchQuery("")).isEmpty());
  }
}

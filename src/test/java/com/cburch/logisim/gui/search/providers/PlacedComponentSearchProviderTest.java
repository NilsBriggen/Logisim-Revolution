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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.search.SearchContext;
import com.cburch.logisim.gui.search.SearchProviders;
import com.cburch.logisim.gui.search.SearchQuery;
import com.cburch.logisim.gui.search.SearchResult;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PlacedComponentSearchProviderTest {

  private LogisimFile file;
  private Project project;
  private Circuit main;
  private Circuit alu;
  private Component clock;
  private Component reset;
  private Component unlabelled;
  private PlacedComponentSearchProvider provider;

  @BeforeEach
  void setUp() {
    file = LogisimFile.createNew(new Loader(null), null);
    project = new Project(file);
    main = file.getMainCircuit();
    main.setProject(project);
    project.setCurrentCircuit(main);
    alu = new Circuit("alu", file, project);
    file.addCircuit(alu);

    clock = pin("clk", 0);
    unlabelled = pin("", 40);
    add(main, clock);
    add(main, unlabelled);
    reset = pin("reset", 0);
    add(alu, reset);

    provider = new PlacedComponentSearchProvider();
    provider.prepare(new SearchContext(null, null, project));
  }

  @Test
  void isRegisteredAndNeedsAProject() {
    assertFalse(provider.isAvailable(new SearchContext(null, null, null)));
    assertTrue(provider.isAvailable(new SearchContext(null, null, project)));
    assertTrue(
        SearchProviders.getAll().stream()
            .anyMatch(PlacedComponentSearchProvider.class::isInstance));
  }

  @Test
  void offersNothingUntilSomethingIsTyped() {
    assertTrue(provider.search(new SearchQuery("")).isEmpty());
    assertEquals(0, provider.indexReads(), "nothing is indexed for an empty query");
  }

  @Test
  void findsAComponentByLabelWithItsCircuitAndType() {
    final var results = provider.search(new SearchQuery("reset"));

    assertEquals(1, results.size());
    final var candidate = results.get(0).candidate();
    assertEquals("reset", candidate.title());
    assertEquals("alu", candidate.context());
    assertEquals(Pin.FACTORY.getDisplayName(), candidate.hint());
    // The highlights fall on the label, after the circuit name.
    assertTrue(results.get(0).highlights()[0] >= candidate.titleOffset());
  }

  @Test
  void findsComponentsByTypeLabelledOnesBelowTheirOwnNameMatches() {
    final var results = provider.search(new SearchQuery(Pin.FACTORY.getDisplayName()));

    assertEquals(3, results.size(), "every pin, labelled or not");
    final var best = results.stream().max(Comparator.comparingInt(SearchResult::score)).get();
    assertEquals(
        Pin.FACTORY.getDisplayName(), best.candidate().title(), "the unlabelled pin is its type");
    assertTrue(best.candidate().hint().contains(unlabelled.getLocation().toString()));
  }

  @Test
  void theCircuitNameAloneDoesNotMatchItsComponents() {
    assertTrue(provider.search(new SearchQuery("alu")).isEmpty());
  }

  @Test
  void readsEachCircuitOnceUntilItChanges() {
    provider.search(new SearchQuery("c"));
    provider.search(new SearchQuery("cl"));
    provider.search(new SearchQuery("clk"));
    assertEquals(2, provider.indexReads(), "one read per circuit");

    add(main, pin("enable", 80));
    final var results = provider.search(new SearchQuery("enable"));

    assertEquals(1, results.size(), "a component added since is found");
    assertEquals(3, provider.indexReads(), "only the changed circuit is read again");
  }

  @Test
  void relabelledComponentIsFoundUnderItsNewName() {
    provider.search(new SearchQuery("clk"));
    clock.getAttributeSet().setValue(StdAttr.LABEL, "sysclock");

    assertEquals(1, provider.search(new SearchQuery("sysclock")).size());
    assertTrue(provider.search(new SearchQuery("clk")).isEmpty());
  }

  @Test
  void deletedCircuitIsDropped() {
    provider.search(new SearchQuery("reset"));
    file.removeCircuit(alu);

    assertTrue(provider.search(new SearchQuery("reset")).isEmpty());
  }

  @Test
  void choosingAComponentShowsItsCircuit() {
    final var result = provider.search(new SearchQuery("reset")).get(0);

    result.candidate().action().run();

    assertSame(alu, project.getCurrentCircuit());
  }

  private static Component pin(String label, int x) {
    final var attrs = Pin.FACTORY.createAttributeSet();
    attrs.setValue(StdAttr.LABEL, label);
    return Pin.FACTORY.createComponent(Location.create(x, 0, true), attrs);
  }

  private static void add(Circuit circuit, Component component) {
    final var mutation = new CircuitMutation(circuit);
    mutation.add(component);
    mutation.execute();
  }
}

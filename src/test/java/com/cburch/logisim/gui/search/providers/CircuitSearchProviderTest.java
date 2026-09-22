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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.search.SearchCandidate;
import com.cburch.logisim.gui.search.SearchContext;
import com.cburch.logisim.gui.search.SearchProviders;
import com.cburch.logisim.gui.search.SearchQuery;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.vhdl.base.VhdlContent;
import java.util.List;
import org.junit.jupiter.api.Test;

class CircuitSearchProviderTest {

  @Test
  void isAvailableOnlyForProjectWindowsAndIsRegistered() {
    final var provider = new CircuitSearchProvider();

    assertFalse(provider.isAvailable(new SearchContext(null, null, null)));
    assertTrue(provider.isAvailable(new SearchContext(null, null, mock(Project.class))));
    assertTrue(
        SearchProviders.getAll().stream().anyMatch(CircuitSearchProvider.class::isInstance));
  }

  @Test
  void listsEveryCircuitMarkingTheMainAndCurrentOnes() {
    final var main = circuit("main");
    final var current = circuit("alu");
    final var other = circuit("decoder");
    final var project = project(List.of(main, current, other), List.of());
    when(project.getCurrentCircuit()).thenReturn(current);

    final var candidates = candidates(project);

    assertEquals(List.of("main", "alu", "decoder"), candidates.stream().map(c -> c.title()).toList());
    assertTrue(candidates.get(0).enabled());
    assertFalse(candidates.get(0).hint().isEmpty());
    assertFalse(candidates.get(1).enabled(), "the circuit on screen has nothing to activate");
    assertTrue(candidates.get(2).enabled());
    assertEquals("", candidates.get(2).hint());
  }

  @Test
  void activatingACircuitShowsItThroughTheProject() {
    final var circuit = circuit("alu");
    final var project = project(List.of(circuit), List.of());

    candidates(project).get(0).action().run();

    verify(project).setCurrentCircuit(circuit);
  }

  @Test
  void listsVhdlEntitiesAfterCircuitsAndShowsThemAsHdl() {
    final var circuit = circuit("main");
    final var vhdl = mock(VhdlContent.class);
    when(vhdl.getName()).thenReturn("counter");
    final var project = project(List.of(circuit), List.of(vhdl));

    final var candidates = candidates(project);

    assertEquals(2, candidates.size());
    assertEquals("counter", candidates.get(1).title());
    assertFalse(candidates.get(1).hint().isEmpty());
    candidates.get(1).action().run();
    verify(project).setCurrentHdlModel(vhdl);
  }

  @Test
  void matchesOnTheCircuitNameAlone() {
    final var project = project(List.of(circuit("main"), circuit("alu")), List.of());
    final var provider = new CircuitSearchProvider();
    provider.prepare(new SearchContext(null, null, project));

    final var results = provider.search(new SearchQuery("alu"));

    assertEquals(1, results.size());
    assertEquals("alu", results.get(0).candidate().title());
  }

  private static Circuit circuit(String name) {
    final var circuit = mock(Circuit.class);
    when(circuit.getName()).thenReturn(name);
    return circuit;
  }

  private static Project project(List<Circuit> circuits, List<VhdlContent> vhdl) {
    final var file = mock(LogisimFile.class);
    when(file.getCircuits()).thenReturn(circuits);
    when(file.getVhdlContents()).thenReturn(vhdl);
    when(file.getMainCircuit()).thenReturn(circuits.isEmpty() ? null : circuits.get(0));
    final var project = mock(Project.class);
    when(project.getLogisimFile()).thenReturn(file);
    return project;
  }

  private static List<SearchCandidate> candidates(Project project) {
    final var provider = new CircuitSearchProvider();
    provider.prepare(new SearchContext(null, null, project));
    return provider.search(new SearchQuery("")).stream().map(r -> r.candidate()).toList();
  }
}

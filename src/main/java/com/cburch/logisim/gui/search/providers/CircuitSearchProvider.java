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
import java.util.ArrayList;
import java.util.List;

/**
 * Offers every circuit and VHDL entity in the current project; choosing one shows it.
 *
 * <p>Jumping to a circuit is the most common navigation act in a project of any size, and without
 * this the only way was to find it in the explorer tree.
 */
public class CircuitSearchProvider extends IndexedSearchProvider {

  @Override
  public String getDisplayName() {
    return S.get("searchProviderCircuits");
  }

  @Override
  public boolean isAvailable(SearchContext context) {
    return context.project() != null;
  }

  /** Ranked above menus so that a circuit named like a menu item wins on an exact match. */
  @Override
  public int getPriority() {
    return 10;
  }

  @Override
  protected List<SearchCandidate> buildCandidates(SearchContext context) {
    final var project = context.project();
    if (project == null || project.getLogisimFile() == null) return List.of();
    final var file = project.getLogisimFile();
    final var current = project.getCurrentCircuit();
    final var currentHdl = project.getCurrentHdl();
    final var candidates = new ArrayList<SearchCandidate>();
    for (final var circuit : file.getCircuits()) {
      final var isCurrent = circuit == current;
      final var hint =
          isCurrent
              ? S.get("searchCircuitCurrentHint")
              : circuit == file.getMainCircuit() ? S.get("searchCircuitMainHint") : "";
      // The circuit on screen is still listed, so a search for it confirms it exists, but greyed
      // out: there is nothing to do for it.
      candidates.add(
          new SearchCandidate(
              circuit.getName(),
              getDisplayName(),
              null,
              hint,
              !isCurrent,
              () -> project.setCurrentCircuit(circuit)));
    }
    for (final var vhdl : file.getVhdlContents()) {
      final var isCurrent = vhdl == currentHdl;
      candidates.add(
          new SearchCandidate(
              vhdl.getName(),
              getDisplayName(),
              null,
              isCurrent ? S.get("searchCircuitCurrentHint") : S.get("searchCircuitVhdlHint"),
              !isCurrent,
              () -> project.setCurrentHdlModel(vhdl)));
    }
    return candidates;
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.search.providers;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.instance.StdAttr;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * The labels and type names of the components placed in circuits, kept per circuit.
 *
 * <p>A circuit is read the first time it is searched, and read again only after it has changed: each
 * indexed circuit is watched, and a component added or removed marks its entries stale. Labels are
 * read from the components as they are searched, so an edited label needs no re-reading. Searching a large project therefore costs one pass over its components, not one
 * per keystroke or per dialog.
 *
 * <p>Circuits are held weakly, so a closed project's circuits are not kept alive by the index.
 */
final class PlacedComponentIndex {

  /**
   * One placed component.
   *
   * @param component the component
   * @param type the display name of its type
   */
  record Entry(Component component, String type) {
    /**
     * Its label, or the empty string when it has none. Read from the component each time, so a
     * label edited by any means is found under its new name.
     */
    String label() {
      final var attributes = component.getAttributeSet();
      if (attributes == null || !attributes.containsAttribute(StdAttr.LABEL)) return "";
      final var value = attributes.getValue(StdAttr.LABEL);
      return value == null ? "" : value.trim();
    }
  }

  /** What the index knows about one circuit, and the listener that marks it stale. */
  private static final class CircuitEntries implements CircuitListener {
    private volatile boolean stale = true;
    private List<Entry> entries = List.of();

    @Override
    public void circuitChanged(CircuitEvent event) {
      // Selection and halo changes leave the components as they were.
      if (event.getAction() != CircuitEvent.ACTION_DISPLAY_CHANGE) stale = true;
    }
  }

  private final Map<Circuit, CircuitEntries> circuits = new WeakHashMap<>();
  private int reads;

  /** The components of {@code circuit}, reading them only when they are not known or stale. */
  List<Entry> entries(Circuit circuit) {
    var known = circuits.get(circuit);
    if (known == null) {
      known = new CircuitEntries();
      circuit.addCircuitListener(known);
      circuits.put(circuit, known);
    }
    if (known.stale) {
      // Cleared first, so a change that lands while reading marks the result stale again.
      known.stale = false;
      known.entries = read(circuit);
      reads++;
    }
    return known.entries;
  }

  /** Forgets every circuit but {@code live}, such as circuits deleted from the project. */
  void retainOnly(Collection<Circuit> live) {
    final Set<Circuit> keep = Collections.newSetFromMap(new IdentityHashMap<>());
    keep.addAll(live);
    final var iterator = circuits.entrySet().iterator();
    while (iterator.hasNext()) {
      final var entry = iterator.next();
      if (!keep.contains(entry.getKey())) {
        entry.getKey().removeCircuitListener(entry.getValue());
        iterator.remove();
      }
    }
  }

  /** How many times a circuit has been read, for tests. */
  int reads() {
    return reads;
  }

  private static List<Entry> read(Circuit circuit) {
    final var components = new ArrayList<Component>(circuit.getNonWires());
    // Reading order, top to bottom then left to right, so results come out in a stable order.
    components.sort(
        Comparator.<Component>comparingInt(component -> component.getLocation().getY())
            .thenComparingInt(component -> component.getLocation().getX()));
    final var entries = new ArrayList<Entry>(components.size());
    for (final var component : components) {
      final var factory = component.getFactory();
      if (factory == null) continue;
      final var type = factory.getDisplayName();
      entries.add(new Entry(component, type == null ? "" : type));
    }
    return entries;
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.main;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.std.wiring.Pin;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The editor tab dots mark the circuits an edit changed, not simply the one in view. */
class EditedCircuitTrackerTest {

  private static void addPin(Circuit circuit) {
    final var mutation = new CircuitMutation(circuit);
    mutation.add(
        Pin.FACTORY.createComponent(Location.create(100, 100, true), Pin.FACTORY.createAttributeSet()));
    mutation.execute();
  }

  @Test
  void reportsTheCircuitThatWasModified() {
    final var file = LogisimFile.createNew(new Loader(null), null);
    file.stopAutosaveThread(false);
    final var main = file.getCircuits().get(0);
    final var other = new Circuit("other", file, null);
    file.addCircuit(other);

    final var tracker = new EditedCircuitTracker();
    tracker.watch(file);
    tracker.begin();
    addPin(other);

    assertEquals(Set.of(other), tracker.finish(main));
  }

  @Test
  void fallsBackToTheCircuitInViewWhenNoCircuitReportedAChange() {
    final var file = LogisimFile.createNew(new Loader(null), null);
    file.stopAutosaveThread(false);
    final var main = file.getCircuits().get(0);

    final var tracker = new EditedCircuitTracker();
    tracker.watch(file);
    tracker.begin();

    assertEquals(Set.of(main), tracker.finish(main));
  }

  @Test
  void changesBeforeTheEditStartedAreNotCounted() {
    final var file = LogisimFile.createNew(new Loader(null), null);
    file.stopAutosaveThread(false);
    final var main = file.getCircuits().get(0);
    final var other = new Circuit("other", file, null);
    file.addCircuit(other);

    final var tracker = new EditedCircuitTracker();
    tracker.watch(file);
    addPin(other);
    tracker.begin();
    addPin(main);

    assertEquals(Set.of(main), tracker.finish(null));
  }

  @Test
  void forgottenCircuitIsNoLongerReported() {
    final var file = LogisimFile.createNew(new Loader(null), null);
    file.stopAutosaveThread(false);
    final var main = file.getCircuits().get(0);

    final var tracker = new EditedCircuitTracker();
    tracker.watch(file);
    tracker.forget(main);
    tracker.begin();
    addPin(main);

    assertEquals(Set.of(), tracker.finish(null));
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.log;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.RadixOption;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Clock;
import com.cburch.logisim.std.wiring.Pin;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ModelTest {

  @Test
  void clockModeInitializesToCurrentClockPhaseDuration() {
    final var fixture = new Fixture();
    final var attrs = Clock.FACTORY.createAttributeSet();
    attrs.setValue(Clock.ATTR_LOW, 2);
    final var clock = Clock.FACTORY.createComponent(Location.create(100, 100, true), attrs);
    add(fixture.circuit, clock);

    final var model = new Model(fixture.state);

    assertEquals(10_000, model.getEndTime());
    assertDoesNotThrow(() -> model.propagationCompleted(false, false, true));
  }

  @Test
  void realTimeModeKeepsInitialValueUntilFirstObservedChange() throws Exception {
    final var fixture = new Fixture();
    final var pin = Pin.FACTORY.createComponent(Location.create(100, 100, true), Pin.FACTORY.createAttributeSet());
    add(fixture.circuit, pin);
    Pin.FACTORY.driveInputPin(fixture.state.getInstanceState(pin), Value.FALSE);

    final var now = new AtomicLong(0);
    final var model = new Model(fixture.state, now::get);
    model.setRealMode(1_000_000_000, false);

    now.set(20_000_000);
    Pin.FACTORY.driveInputPin(fixture.state.getInstanceState(pin), Value.TRUE);
    model.propagationCompleted(false, false, true);

    final var signal = model.getSignal(0);
    assertEquals(Value.FALSE, signal.getValue(model.getEndTime() / 2));
    assertEquals(Value.TRUE, signal.getValue(model.getEndTime() - 1));
  }

  @Test
  void realTimeModeInitialDurationDoesNotExceedTimeScale() {
    final var fixture = new Fixture();
    final var model = new Model(fixture.state);

    model.setRealMode(5, false);

    assertEquals(5, model.getEndTime());
  }

  @Test
  void realTimeModeChangeDurationDoesNotExceedTimeScale() throws Exception {
    final var fixture = new Fixture();
    final var pin = Pin.FACTORY.createComponent(Location.create(100, 100, true), Pin.FACTORY.createAttributeSet());
    add(fixture.circuit, pin);
    Pin.FACTORY.driveInputPin(fixture.state.getInstanceState(pin), Value.FALSE);

    final var now = new AtomicLong(0);
    final var model = new Model(fixture.state, now::get);
    model.setRealMode(5, false);

    now.set(1_000_000_000);
    Pin.FACTORY.driveInputPin(fixture.state.getInstanceState(pin), Value.TRUE);
    model.propagationCompleted(false, false, true);

    assertEquals(15, model.getEndTime());
  }

  @Test
  void realTimeModeUnchangedSignalsOnlyRecordElapsedTime() throws Exception {
    final var fixture = new Fixture();
    final var pin = Pin.FACTORY.createComponent(Location.create(100, 100, true), Pin.FACTORY.createAttributeSet());
    add(fixture.circuit, pin);
    Pin.FACTORY.driveInputPin(fixture.state.getInstanceState(pin), Value.FALSE);

    final var now = new AtomicLong(0);
    final var model = new Model(fixture.state, now::get);
    model.setRealMode(5, false);

    now.set(1_000_000_000);
    model.propagationCompleted(false, false, true);

    assertEquals(10, model.getEndTime());
  }

  @Test
  void recordsTheSampledValuesNotTheValuesAtApplyTime() {
    final var fixture = new Fixture();
    final var pin = inputPin(fixture, 100, 100);
    Pin.FACTORY.driveInputPin(fixture.state.getInstanceState(pin), Value.FALSE);
    final var model = new Model(fixture.state);

    // Sampled on the simulation thread, applied later on the event thread.
    final var sample = model.sample();
    Pin.FACTORY.driveInputPin(fixture.state.getInstanceState(pin), Value.TRUE);
    model.applyPropagation(sample, false, false, true);

    assertEquals(Value.FALSE, model.getSignal(0).getValue(model.getEndTime() - 1));
  }

  @Test
  void sampleSurvivesSignalsBeingReorderedAndRemovedBeforeItIsApplied() {
    final var fixture = new Fixture();
    final var first = inputPin(fixture, 100, 100);
    final var second = inputPin(fixture, 100, 200);
    Pin.FACTORY.driveInputPin(fixture.state.getInstanceState(first), Value.FALSE);
    Pin.FACTORY.driveInputPin(fixture.state.getInstanceState(second), Value.TRUE);
    final var model = new Model(fixture.state);
    final var secondItem = model.getItem(1);
    final var expected = secondItem.fetchValue(fixture.state);

    final var sample = model.sample();
    model.move(new int[] {1}, 0);
    model.remove(List.of(model.getItem(1)));
    model.applyPropagation(sample, false, false, true);

    assertEquals(1, model.getSignalCount());
    assertSame(secondItem, model.getItem(0));
    assertEquals(expected, model.getSignal(0).getValue(model.getEndTime() - 1));
  }

  @Test
  void signalsFireOnlyWhenTheSampleIsApplied() {
    final var fixture = new Fixture();
    inputPin(fixture, 100, 100);
    final var model = new Model(fixture.state);
    final var extended = new AtomicInteger();
    final Model.Listener listener = new Model.Listener() {
      @Override
      public void signalsExtended(Model.Event event) {
        extended.incrementAndGet();
      }
    };
    model.addModelListener(listener);

    final var sample = model.sample();
    assertEquals(0, extended.get());
    model.applyPropagation(sample, false, false, true);
    assertEquals(1, extended.get());
  }

  @Test
  void removingTheSpotlightSignalClearsTheSpotlight() {
    final var fixture = new Fixture();
    inputPin(fixture, 100, 100);
    inputPin(fixture, 100, 200);
    final var model = new Model(fixture.state);
    model.setSpotlight(model.getSignal(1));

    model.remove(List.of(model.getItem(1)));

    assertNull(model.getSpotlight());
  }

  @Test
  void removingAnotherSignalKeepsTheSpotlight() {
    final var fixture = new Fixture();
    inputPin(fixture, 100, 100);
    inputPin(fixture, 100, 200);
    final var model = new Model(fixture.state);
    final var spot = model.getSignal(1);
    model.setSpotlight(spot);

    model.remove(List.of(model.getItem(0)));

    assertSame(spot, model.getSpotlight());
    assertEquals(0, spot.idx);
  }

  @Test
  void severalClocksPickATopLevelClockWithoutAsking() {
    final var fixture = new Fixture();
    add(fixture.circuit, Clock.FACTORY.createComponent(
        Location.create(100, 100, true), Clock.FACTORY.createAttributeSet()));
    add(fixture.circuit, Clock.FACTORY.createComponent(
        Location.create(100, 200, true), Clock.FACTORY.createAttributeSet()));

    // Used to open a modal dialog, from any tab switch that met this circuit.
    final var model = new Model(fixture.state);

    assertNotNull(model.getClockSourceInfo());
    assertEquals(1, model.getClockSourceInfo().getDepth());
    assertTrue(model.isClockMode());
  }

  @Test
  void rulerLabelsUseOneUnit() {
    final var unit = Model.durationUnit(2_500);
    final var decimals = Model.durationDecimals(2_500, unit);
    assertEquals(1_000, unit);
    assertEquals(1, decimals);
    for (final var t : new long[] {0, 2_500, 5_000, 7_500}) {
      assertTrue(Model.formatDuration(t, unit, decimals).endsWith("µs"), Long.toString(t));
    }
    assertEquals(0, Model.durationDecimals(5_000, unit));
    assertEquals(3, Model.durationDecimals(5_001, unit));
    assertEquals(1, Model.durationUnit(250));
    assertEquals(1_000_000_000L, Model.durationUnit(2_000_000_000L));
  }

  @Test
  void signalStartsInThePinRadix() {
    final var fixture = new Fixture();
    final var attrs = Pin.FACTORY.createAttributeSet();
    attrs.setValue(StdAttr.WIDTH, BitWidth.create(8));
    attrs.setValue(RadixOption.ATTRIBUTE, RadixOption.RADIX_16);
    add(fixture.circuit, Pin.FACTORY.createComponent(Location.create(100, 100, true), attrs));

    final var model = new Model(fixture.state);

    assertSame(RadixOption.RADIX_16, model.getItem(0).getRadix());
  }

  @Test
  void floatRadixFitsOnlyFloatingPointWidths() {
    for (final var width : new int[] {8, 16, 32, 64}) {
      assertTrue(SignalInfo.radixFits(RadixOption.RADIX_FLOAT, width));
    }
    for (final var width : new int[] {1, 4, 12, 24}) {
      assertFalse(SignalInfo.radixFits(RadixOption.RADIX_FLOAT, width));
      assertTrue(SignalInfo.radixFits(RadixOption.RADIX_16, width));
    }
  }

  private static Component inputPin(Fixture fixture, int x, int y) {
    final var pin =
        Pin.FACTORY.createComponent(Location.create(x, y, true), Pin.FACTORY.createAttributeSet());
    add(fixture.circuit, pin);
    return pin;
  }

  private static void add(Circuit circuit, Component component) {
    final var mutation = new CircuitMutation(circuit);
    mutation.add(component);
    mutation.execute();
  }

  private static final class Fixture {
    private final Circuit circuit;
    private final CircuitState state;

    private Fixture() {
      final var file = LogisimFile.createNew(new Loader(null), null);
      final var project = new Project(file);
      circuit = file.getMainCircuit();
      circuit.setProject(project);
      project.setCurrentCircuit(circuit);
      state = project.getCircuitState();
    }
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.circuit.appear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.std.wiring.Pin;
import org.junit.jupiter.api.Test;

class AppearancePortTest {
  @Test
  void matchesIsReflexiveAndSymmetricForTheSameLocationAndPin() {
    final var pin = mock(Instance.class);
    final var port = new AppearancePort(Location.create(50, 60, false), pin);
    final var same = new AppearancePort(Location.create(50, 60, false), pin);
    final var copy = port.clone();

    assertTrue(port.matches(port));
    assertNotSame(port, same);
    assertTrue(port.matches(same));
    assertTrue(same.matches(port));
    assertNotSame(port, copy);
    assertTrue(port.matches(copy));
    assertTrue(copy.matches(port));
    assertEquals(port.matchesHashCode(), same.matchesHashCode());
    assertEquals(port.matchesHashCode(), copy.matchesHashCode());
  }

  @Test
  void matchesRejectsDifferentLocationsEvenWithTheSamePin() {
    final var pin = mock(Instance.class);
    final var port = new AppearancePort(Location.create(50, 60, false), pin);
    final var moved = new AppearancePort(Location.create(60, 60, false), pin);

    assertFalse(port.matches(moved));
    assertFalse(moved.matches(port));
    moved.translate(-10, 0);
    assertTrue(port.matches(moved));
    assertTrue(moved.matches(port));
  }

  @Test
  void matchesRequiresPinIdentityAndAnAppearancePort() {
    final var location = Location.create(50, 60, false);
    final var port = new AppearancePort(location, mock(Instance.class));
    final var otherPin = new AppearancePort(location, mock(Instance.class));
    final var unassigned = new AppearancePort(location, null);
    final var otherUnassigned = new AppearancePort(location, null);
    final var anchor = new AppearanceAnchor(location);

    assertFalse(port.matches(otherPin));
    assertFalse(otherPin.matches(port));
    assertFalse(port.matches(unassigned));
    assertFalse(unassigned.matches(port));
    assertTrue(unassigned.matches(otherUnassigned));
    assertTrue(otherUnassigned.matches(unassigned));
    assertFalse(port.matches(anchor));
    assertFalse(anchor.matches(port));
    assertFalse(port.matches(null));
  }

  @Test
  void matchesRequiresSamePositionAndPin() {
    final var component = Pin.FACTORY.createComponent(
        Location.create(100, 100, true), Pin.FACTORY.createAttributeSet());
    final var original = new AppearancePort(
        Location.create(40, 50, true), Instance.getInstanceFor(component));
    final var clone = (AppearancePort) original.clone();
    assertTrue(original.matches(clone));
    assertEquals(original.matchesHashCode(), clone.matchesHashCode());

    clone.translate(10, 0);
    assertFalse(original.matches(clone));
    final var otherComponent = Pin.FACTORY.createComponent(
        Location.create(100, 100, true), Pin.FACTORY.createAttributeSet());
    assertFalse(original.matches(new AppearancePort(
        original.getLocation(), Instance.getInstanceFor(otherComponent))));
    assertFalse(original.matches(new AppearanceAnchor(original.getLocation())));
  }
}

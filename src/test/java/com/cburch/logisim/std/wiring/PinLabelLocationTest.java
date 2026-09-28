/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.generated.BuildInfo;
import com.cburch.logisim.instance.StdAttr;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A pin's "Label Location" is honoured, and pins that never had one keep their old layout.
 *
 * <p>The attribute was shown and saved but the label was always put opposite the port. Pins in
 * existing files never recorded a location, so an unset location still means "opposite the port"
 * and follows the pin's facing, while a location the user picks is used as it is.
 */
class PinLabelLocationTest {

  @TempDir Path tempDir;

  /** What the file reader does for a pin whose file entry has no label location. */
  private static PinAttributes loaded(Direction facing) {
    final var attrs = (PinAttributes) Pin.FACTORY.createAttributeSet();
    attrs.setValue(StdAttr.FACING, facing);
    final var unset = Pin.FACTORY.getDefaultAttributeValue(StdAttr.LABEL_LOC, BuildInfo.version);
    attrs.setValue(StdAttr.LABEL_LOC, unset);
    return attrs;
  }

  @Test
  void anUnsetLocationIsOppositeThePort() {
    assertEquals(Direction.WEST, loaded(Direction.EAST).getValue(StdAttr.LABEL_LOC));
    assertEquals(Direction.EAST, loaded(Direction.WEST).getValue(StdAttr.LABEL_LOC));
    assertEquals(Direction.SOUTH, loaded(Direction.NORTH).getValue(StdAttr.LABEL_LOC));
    assertEquals(Direction.NORTH, loaded(Direction.SOUTH).getValue(StdAttr.LABEL_LOC));
  }

  @Test
  void anUnsetLocationFollowsRotation() {
    final var attrs = (PinAttributes) Pin.FACTORY.createAttributeSet();
    attrs.setValue(StdAttr.FACING, Direction.NORTH);
    assertEquals(Direction.SOUTH, attrs.getValue(StdAttr.LABEL_LOC));
    attrs.setValue(StdAttr.FACING, Direction.WEST);
    assertEquals(Direction.EAST, attrs.getValue(StdAttr.LABEL_LOC));
  }

  @Test
  void chosenLocationIsHonouredAndKeptThroughRotation() {
    final var attrs = (PinAttributes) Pin.FACTORY.createAttributeSet();
    attrs.setValue(StdAttr.LABEL_LOC, Direction.NORTH);
    assertEquals(Direction.NORTH, attrs.getValue(StdAttr.LABEL_LOC));
    attrs.setValue(StdAttr.FACING, Direction.SOUTH);
    assertEquals(Direction.NORTH, attrs.getValue(StdAttr.LABEL_LOC));
    attrs.setValue(StdAttr.LABEL_LOC, StdAttr.LABEL_CENTER);
    assertEquals(StdAttr.LABEL_CENTER, attrs.getValue(StdAttr.LABEL_LOC));
  }

  /** A saved location reloads as chosen; an unset one is not written, so old readers see nothing. */
  @Test
  void onlyAChosenLocationIsSaved() {
    final var attrs = (PinAttributes) Pin.FACTORY.createAttributeSet();
    assertFalse(attrs.isToSave(StdAttr.LABEL_LOC));
    attrs.setValue(StdAttr.LABEL_LOC, StdAttr.LABEL_LOC.parse("north"));
    assertTrue(attrs.isToSave(StdAttr.LABEL_LOC));
    assertEquals(Direction.NORTH, attrs.getValue(StdAttr.LABEL_LOC));
  }

  @Test
  void copyKeepsTheUnsetLocation() {
    final var attrs = (PinAttributes) Pin.FACTORY.createAttributeSet();
    final var copy = (PinAttributes) attrs.clone();
    copy.setValue(StdAttr.FACING, Direction.NORTH);
    assertEquals(Direction.SOUTH, copy.getValue(StdAttr.LABEL_LOC));
  }

  @Test
  void labelLocationsSurviveSaveAndReload() throws Exception {
    final var loader = new Loader(null);
    final var file = LogisimFile.createNew(loader, null);
    file.addLibrary(loader.getBuiltin().getLibrary(WiringLibrary._ID));
    final var unset = Pin.FACTORY.createAttributeSet();
    unset.setValue(StdAttr.FACING, Direction.NORTH);
    unset.setValue(StdAttr.LABEL, "unset");
    final var chosen = Pin.FACTORY.createAttributeSet();
    chosen.setValue(StdAttr.LABEL_LOC, Direction.NORTH);
    chosen.setValue(StdAttr.LABEL, "chosen");
    final var mutation = new CircuitMutation(file.getMainCircuit());
    mutation.add(Pin.FACTORY.createComponent(Location.create(100, 100, false), unset));
    mutation.add(Pin.FACTORY.createComponent(Location.create(200, 200, false), chosen));
    mutation.execute();
    final var path = tempDir.resolve("pin-labels.circ").toFile();

    assertTrue(loader.save(file, path));
    final var xml = Files.readString(path.toPath());
    assertFalse(xml.contains("\"auto\""), xml);
    final var reloaded = new Loader(null).openLogisimFile(path);
    for (final var comp : reloaded.getMainCircuit().getNonWires()) {
      final var attrs = comp.getAttributeSet();
      final var expected =
          "unset".equals(attrs.getValue(StdAttr.LABEL)) ? Direction.SOUTH : Direction.NORTH;
      assertEquals(expected, attrs.getValue(StdAttr.LABEL_LOC), xml);
    }
    // The unset one still follows its facing after the reload.
    for (final var comp : reloaded.getMainCircuit().getNonWires()) {
      final var attrs = comp.getAttributeSet();
      if (!"unset".equals(attrs.getValue(StdAttr.LABEL))) continue;
      attrs.setValue(StdAttr.FACING, Direction.EAST);
      assertEquals(Direction.WEST, attrs.getValue(StdAttr.LABEL_LOC));
    }
  }
}

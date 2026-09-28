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
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.draw.model.CanvasObject;
import com.cburch.draw.shapes.Text;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitAttributes;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.std.wiring.WiringLibrary;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DefaultCustomAppearanceTest {

  @TempDir Path temporary;

  @Test
  void newCircuitDefaultNamesItsPinsAndKeepsTheAnchorOffThePorts() {
    final var file = LogisimFile.createNew(new Loader(null), null);
    final var circuit = file.getMainCircuit();
    addPins(circuit);

    final var objects = circuit.getAppearance().getCustomObjectsFromBottom();
    assertEquals(Set.of("a", "b", "y"), texts(objects));
    assertFalse(portLocations(objects).contains(anchor(objects)));
  }

  @Test
  void fileWithUnsavedDefaultKeepsThePlainBoxAndSavedLabelledDefaultRoundTrips() throws Exception {
    final var loader = new Loader(null);
    final var file = LogisimFile.createNew(loader, null);
    file.addLibrary(loader.getBuiltin().getLibrary(WiringLibrary._ID));
    final var circuit = file.getMainCircuit();
    circuit.getStaticAttributes().setValue(CircuitAttributes.APPEARANCE_ATTR, CircuitAttributes.APPEAR_CUSTOM);
    addPins(circuit);
    final var path = temporary.resolve("labelled.circ").toFile();
    assertTrue(loader.save(file, path));

    // The labelled default is written out and read back as the (still generated) default.
    final var reopened = new Loader(null).openLogisimFile(path).getMainCircuit().getAppearance();
    assertEquals(Set.of("a", "b", "y"), texts(reopened.getCustomObjectsFromBottom()));
    assertTrue(reopened.isLabelledDefaultCustomAppearance());

    // A file from before the change has no <appear>: it regenerates the old plain box, with the
    // anchor on the first output port as before.
    final var xml = Files.readString(path.toPath()).replaceAll("(?s)<appear>.*?</appear>", "");
    final var legacyPath = temporary.resolve("legacy.circ");
    Files.writeString(legacyPath, xml);
    final var legacy = new Loader(null).openLogisimFile(legacyPath.toFile()).getMainCircuit().getAppearance();
    final var objects = legacy.getCustomObjectsFromBottom();
    assertTrue(texts(objects).isEmpty());
    assertTrue(portLocations(objects).contains(anchor(objects)));
    assertTrue(legacy.isLegacyDefaultCustomAppearance());
  }

  private static void addPins(Circuit circuit) {
    final var mutation = new CircuitMutation(circuit);
    mutation.add(pin("a", false, 100));
    mutation.add(pin("b", false, 120));
    mutation.add(pin("y", true, 140));
    mutation.execute();
  }

  private static com.cburch.logisim.comp.Component pin(String label, boolean output, int y) {
    final var attrs = Pin.FACTORY.createAttributeSet();
    attrs.setValue(StdAttr.LABEL, label);
    if (output) attrs.setValue(Pin.ATTR_TYPE, Pin.OUTPUT);
    return Pin.FACTORY.createComponent(Location.create(output ? 300 : 100, y, true), attrs);
  }

  private static Set<String> texts(List<CanvasObject> objects) {
    return objects.stream()
        .filter(Text.class::isInstance)
        .map(obj -> ((Text) obj).getText())
        .collect(Collectors.toSet());
  }

  private static Set<Location> portLocations(List<CanvasObject> objects) {
    return objects.stream()
        .filter(AppearancePort.class::isInstance)
        .map(obj -> ((AppearancePort) obj).getLocation())
        .collect(Collectors.toSet());
  }

  private static Location anchor(List<CanvasObject> objects) {
    return objects.stream()
        .filter(AppearanceAnchor.class::isInstance)
        .map(obj -> ((AppearanceAnchor) obj).getLocation())
        .findFirst()
        .orElseThrow();
  }
}

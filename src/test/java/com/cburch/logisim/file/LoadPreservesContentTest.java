/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.Main;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.fpga.hdlgenerator.HdlGeneratorFactory;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.std.wiring.Tunnel;
import com.cburch.logisim.std.wiring.WiringLibrary;
import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Reading a file must give back what was saved: labels are neither cleared nor rewritten for a
 * current file, and what an old or inconsistent file needs changed is renamed predictably and
 * reported once, never deleted.
 */
class LoadPreservesContentTest {

  @TempDir Path tempDir;

  private final String originalHdlType = AppPreferences.HdlType.get();
  private final boolean wasHeadless = Main.headless;

  @BeforeEach
  void noDialogs() {
    Main.headless = true;
  }

  @AfterEach
  void restore() {
    AppPreferences.HdlType.set(originalHdlType);
    Main.headless = wasHeadless;
  }

  @Test
  void currentFileKeepsEveryLabelByteForByte() throws Exception {
    AppPreferences.HdlType.set(HdlGeneratorFactory.VHDL);
    final var saved =
        save(
            "labels.circ",
            pin("café", 100),
            pin("my out", 140),
            pin("x1", 180),
            tunnel("a b", 220),
            tunnel("a b", 260));
    final var before = Files.readAllBytes(saved.toPath());

    final var loaded = load(saved);
    assertEquals(
        List.of("café", "my out", "x1", "a b", "a b"), labels(loaded.getMainCircuit()));
    assertNull(loaded.getMessage(), "nothing was changed, so there is nothing to report");

    final var again = tempDir.resolve("again.circ").toFile();
    assertTrue(loaded.getLoader().save(loaded, again));
    assertEquals(new String(before, "UTF-8"), Files.readString(again.toPath()));
  }

  @Test
  void clashingLabelsAreRenamedNotCleared() throws Exception {
    AppPreferences.HdlType.set(HdlGeneratorFactory.VERILOG);
    final var saved = save("clash.circ", pin("x", 100), pin("y", 140), pin("z", 180));
    // Only a file edited elsewhere holds such labels; the editor refuses to create them.
    final var xml =
        Files.readString(saved.toPath())
            .replace("val=\"y\"", "val=\"x\"")
            .replace("val=\"z\"", "val=\"Pin\"");
    Files.writeString(saved.toPath(), xml);

    final var loaded = load(saved);

    assertEquals(List.of("x", "x_1", "Pin_1"), labels(loaded.getMainCircuit()));
    final var message = loaded.getMessage();
    assertNotNull(message, "the renames must be reported");
    assertTrue(message.contains("x_1") && message.contains("Pin_1"), message);
    assertNull(loaded.getMessage(), "one message for all renames");
    // The same file always gives the same names.
    assertEquals(labels(loaded.getMainCircuit()), labels(load(saved).getMainCircuit()));
  }

  @Test
  void tunnelLabelsAreNeverTouched() throws Exception {
    AppPreferences.HdlType.set(HdlGeneratorFactory.VERILOG);
    final var saved = save("tunnels.circ", tunnel("Pin", 100), tunnel("Pin", 140), pin("p", 180));

    final var loaded = load(saved);

    assertEquals(List.of("p", "Pin", "Pin"), labels(loaded.getMainCircuit()));
  }

  @Test
  void oldFileLabelsAreConvertedPredictablyAndReported() throws Exception {
    AppPreferences.HdlType.set(HdlGeneratorFactory.NONE);
    final var saved = save("old.circ", pin("my out", 100), pin("my-out", 140), tunnel("a b", 180));
    final var xml = Files.readString(saved.toPath());
    final var source = xml.replaceFirst("source=\"[^\"]*\"", "source=\"2.7.1\"");
    assertFalse(source.equals(xml), xml);
    Files.writeString(saved.toPath(), source);

    final var first = load(saved);
    final var second = load(saved);

    assertEquals(List.of("my_out", "my_out_1", "a b"), labels(first.getMainCircuit()));
    assertEquals(labels(first.getMainCircuit()), labels(second.getMainCircuit()));
    final var messages = new ArrayList<String>();
    for (var m = first.getMessage(); m != null; m = first.getMessage()) messages.add(m);
    assertTrue(
        messages.stream().anyMatch(m -> m.contains("my_out_1")), "conversion is reported");
  }

  @Test
  void duplicateCircuitNamesAreMadeUnique() throws Exception {
    final var loader = new Loader(null);
    final var file = LogisimFile.createNew(loader, null);
    file.retireAutosaveThread();
    file.addLibrary(loader.getBuiltin().getLibrary(WiringLibrary._ID));
    final var other = new Circuit("other", file, null);
    file.addCircuit(other);
    add(other, pin("inOther", 100));
    final var saved = tempDir.resolve("dup.circ").toFile();
    assertTrue(loader.save(file, saved));
    Files.writeString(
        saved.toPath(), Files.readString(saved.toPath()).replace("\"other\"", "\"main\""));

    final var loaded = load(saved);

    final var names = loaded.getCircuits().stream().map(Circuit::getName).toList();
    assertEquals(List.of("main", "main_2"), names);
    assertEquals("main", loaded.getMainCircuit().getName());
    assertEquals(List.of("inOther"), labels(loaded.getCircuit("main_2")));
    final var message = loaded.getMessage();
    assertNotNull(message);
    assertTrue(message.contains("main_2"), message);
  }

  @Test
  void emptyProjectElementFailsWithOneReadableMessage() throws Exception {
    final var errors = new ArrayList<String>();
    final var empty = write("empty-project.circ", "<?xml version=\"1.0\"?>\n<project/>\n");

    final var loaded = recordingLoader(errors).openLogisimFile(empty);
    loaded.retireAutosaveThread();

    assertEquals(1, loaded.getCircuitCount(), "an empty project gets a main circuit");
    assertTrue(errors.isEmpty(), errors.toString());
  }

  @Test
  void malformedFileFailsOnceWithFileNameAndLine() throws Exception {
    final var errors = new ArrayList<String>();
    final var broken =
        write("broken.circ", "<?xml version=\"1.0\"?>\n<project>\n<circuit name=\"a\">\n");

    final var failure =
        assertThrows(
            LoadFailedException.class, () -> recordingLoader(errors).openLogisimFile(broken));

    assertFalse(failure.isShown(), "the caller shows it, once");
    assertTrue(errors.isEmpty(), "no second dialog: " + errors);
    assertTrue(failure.getMessage().contains("broken.circ"), failure.getMessage());
    assertTrue(failure.getMessage().contains("line"), failure.getMessage());
    assertFalse(failure.getMessage().contains("Exception"), failure.getMessage());
    assertNotNull(failure.getCause(), "the details stay available");
  }

  @Test
  void xmlThatIsNotAProjectIsNamedAsSuch() throws Exception {
    final var errors = new ArrayList<String>();
    final var other = write("other.circ", "<?xml version=\"1.0\"?>\n<svg/>\n");

    final var failure =
        assertThrows(
            LoadFailedException.class, () -> recordingLoader(errors).openLogisimFile(other));

    assertTrue(errors.isEmpty(), errors.toString());
    assertTrue(failure.getMessage().contains("not a Logisim project"), failure.getMessage());
  }

  @Test
  void missingFileIsDescribedPlainly() {
    final var missing = tempDir.resolve("gone.circ").toFile();
    final var failure =
        assertThrows(
            LoadFailedException.class,
            () -> recordingLoader(new ArrayList<>()).openLogisimFile(missing));
    assertTrue(failure.getMessage().contains("does not exist"), failure.getMessage());
  }

  @Test
  void changeByAnotherProgramIsNoticed() throws Exception {
    final var saved = save("watched.circ", pin("p", 100));
    final var loader = recordingLoader(new ArrayList<>());
    final var file = loader.openLogisimFile(saved);
    file.retireAutosaveThread();
    assertFalse(loader.isMainFileChangedExternally());

    Files.setLastModifiedTime(
        saved.toPath(),
        FileTime.fromMillis(Files.getLastModifiedTime(saved.toPath()).toMillis() + 10_000));
    assertTrue(loader.isMainFileChangedExternally());

    assertTrue(loader.save(file, saved));
    assertFalse(loader.isMainFileChangedExternally(), "our own save is not a foreign change");
  }

  @Test
  void removingTheMainCircuitPromotesTheFirstRemainingCircuit() {
    final var loader = new Loader(null);
    final var file = LogisimFile.createNew(loader, null);
    file.retireAutosaveThread();
    final var main = file.getMainCircuit();
    final var second = new Circuit("second", file, null);
    file.addCircuit(second);

    assertEquals(second, file.mainCircuitAfterRemoving(main));
    assertEquals(main, file.mainCircuitAfterRemoving(second));
    file.removeCircuit(main);
    assertEquals(second, file.getMainCircuit());
  }

  private File save(String name, Component... components) {
    final var loader = new Loader(null);
    final var file = LogisimFile.createNew(loader, null);
    file.retireAutosaveThread();
    file.addLibrary(loader.getBuiltin().getLibrary(WiringLibrary._ID));
    for (final var component : components) add(file.getMainCircuit(), component);
    final var circ = tempDir.resolve(name).toFile();
    assertTrue(loader.save(file, circ));
    return circ;
  }

  private File write(String name, String content) throws Exception {
    final var path = tempDir.resolve(name);
    Files.writeString(path, content);
    return path.toFile();
  }

  private static void add(Circuit circuit, Component component) {
    final var mutation = new CircuitMutation(circuit);
    mutation.add(component);
    mutation.execute();
  }

  private static LogisimFile load(File circ) throws Exception {
    final var loader = recordingLoader(new ArrayList<>());
    try (final var in = new FileInputStream(circ)) {
      final var file = LogisimFile.loadSub(in, loader, circ);
      file.retireAutosaveThread();
      return file;
    }
  }

  private static List<String> labels(Circuit circuit) {
    final var sorted = new ArrayList<>(circuit.getNonWires());
    sorted.sort((a, b) -> a.getLocation().compareTo(b.getLocation()));
    return sorted.stream().map(c -> c.getAttributeSet().getValue(StdAttr.LABEL)).toList();
  }

  private static Component pin(String label, int y) {
    final var attrs = Pin.FACTORY.createAttributeSet();
    attrs.setValue(StdAttr.LABEL, label);
    return Pin.FACTORY.createComponent(Location.create(100, y, false), attrs);
  }

  private static Component tunnel(String label, int y) {
    final var attrs = Tunnel.FACTORY.createAttributeSet();
    attrs.setValue(StdAttr.LABEL, label);
    return Tunnel.FACTORY.createComponent(Location.create(300, y, false), attrs);
  }

  private static Loader recordingLoader(List<String> errors) {
    return new Loader(null) {
      @Override
      public void showError(String description) {
        errors.add(description);
      }
    };
  }
}

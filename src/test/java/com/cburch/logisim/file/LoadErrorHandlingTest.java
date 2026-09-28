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
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.base.BaseLibrary;
import com.cburch.logisim.std.base.Text;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.std.wiring.WiringLibrary;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A file that cannot be read completely must not be quietly overwritten by what did load: bad
 * attribute values fall back to defaults, and the project is flagged so saving can warn first.
 */
class LoadErrorHandlingTest {

  @TempDir Path tempDir;

  @Test
  void invalidAttributeValueKeepsTheComponentWithItsDefault() throws Exception {
    final var circ = savedPinCircuit("bad-attr.circ");
    final var xml = Files.readString(circ.toPath());
    assertTrue(xml.contains("name=\"width\" val=\"4\""), xml);
    Files.writeString(circ.toPath(), xml.replace("name=\"width\" val=\"4\"", "name=\"width\" val=\"banana\""));

    final var errors = new ArrayList<String>();
    final var file = open(circ, errors);

    final var pins =
        file.getMainCircuit().getNonWires().stream()
            .filter(c -> c.getFactory() instanceof Pin)
            .toList();
    assertEquals(1, pins.size(), "the pin must survive a bad attribute value");
    assertEquals(BitWidth.ONE, pins.get(0).getAttributeSet().getValue(StdAttr.WIDTH));
    assertEquals(1, errors.size(), errors.toString());
    assertTrue(errors.get(0).contains("banana"), errors.toString());
    assertTrue(file.isLoadedWithErrors());
    assertLoadsDirty(file);
  }

  @Test
  void unknownComponentFlagsTheFile() throws Exception {
    final var circ = savedPinCircuit("unknown.circ");
    final var xml = Files.readString(circ.toPath());
    final var marker = "<comp ";
    final var at = xml.indexOf(marker);
    assertTrue(at > 0, xml);
    Files.writeString(
        circ.toPath(),
        xml.substring(0, at)
            + "<comp loc=\"(300,300)\" name=\"FrobnicatorXYZ\"/>\n"
            + xml.substring(at));

    final var errors = new ArrayList<String>();
    final var file = open(circ, errors);

    assertFalse(errors.isEmpty());
    assertTrue(file.isLoadedWithErrors());
    assertLoadsDirty(file);
    file.clearLoadedWithErrors();
    assertFalse(file.isLoadedWithErrors());
  }

  @Test
  void cleanFileIsNotFlagged() throws Exception {
    final var errors = new ArrayList<String>();
    final var file = open(savedPinCircuit("clean.circ"), errors);
    assertTrue(errors.isEmpty(), errors.toString());
    assertFalse(file.isLoadedWithErrors());
    final var project = new Project(file);
    try {
      assertFalse(project.isFileDirty());
    } finally {
      project.getSimulator().shutDown();
    }
  }

  @Test
  void unreadableAutosaveFallsBackToTheSavedFile() throws Exception {
    final var circ = savedPinCircuit("corrupt-recovery.circ");
    final var autosave = autosaveFor(circ);
    Files.writeString(autosave.toPath(), "<project><not-closed>");

    final var errors = new ArrayList<String>();
    final LogisimFile file;
    final var wasHeadless = Main.headless;
    Main.headless = false; // the recovery prompt is only offered with a GUI
    try {
      file = open(circ, errors, 0);
    } finally {
      Main.headless = wasHeadless;
    }

    assertNotNull(file, "a broken autosave must not block the real file");
    assertFalse(file.isAutosaveLoaded());
    assertEquals(1, file.getMainCircuit().getNonWires().size());
    assertEquals(1, errors.size(), errors.toString());
    assertTrue(errors.get(0).contains("autosave"), errors.toString());
  }

  @Test
  void headlessLoadIgnoresTheAutosavePromptAndKeepsTheRecovery() throws Exception {
    final var circ = savedPinCircuit("headless.circ");
    final var autosave = autosaveFor(circ);
    Files.copy(circ.toPath(), autosave.toPath());
    final var wasHeadless = Main.headless;
    Main.headless = true;
    try {
      final var errors = new ArrayList<String>();
      // CLOSED_OPTION is what a headless prompt answers; it must not fail the load.
      final var file = open(circ, errors, -1);
      assertNotNull(file);
      assertFalse(file.isAutosaveLoaded());
      assertTrue(autosave.exists());
    } finally {
      Main.headless = wasHeadless;
    }
  }

  @Test
  void legacyLatin1FileKeepsItsAccents() throws Exception {
    final var loader = new Loader(null);
    final var file = LogisimFile.createNew(loader, null);
    file.retireAutosaveThread();
    file.addLibrary(loader.getBuiltin().getLibrary(BaseLibrary._ID));
    final var attrs = Text.FACTORY.createAttributeSet();
    attrs.setValue(Text.ATTR_TEXT, "cafe");
    final var mutation = new CircuitMutation(file.getMainCircuit());
    mutation.add(Text.FACTORY.createComponent(Location.create(100, 100, false), attrs));
    mutation.execute();
    final var circ = tempDir.resolve("latin1.circ").toFile();
    assertTrue(loader.save(file, circ));
    // Logisim before 2.5.1 wrote Latin-1 bytes while declaring UTF-8.
    final var xml = Files.readString(circ.toPath()).replace("cafe", "caf\u00e9");
    Files.write(circ.toPath(), xml.getBytes(StandardCharsets.ISO_8859_1));

    final var reloaded = open(circ, new ArrayList<>(), 1);

    final var text = reloaded.getMainCircuit().getNonWires().iterator().next();
    assertEquals("caf\u00e9", text.getAttributeSet().getValue(Text.ATTR_TEXT));
  }

  @Test
  void saveOntoAFolderFailsWithoutLeavingABackup() throws Exception {
    final var errors = new ArrayList<String>();
    final var loader = recordingLoader(errors, 1);
    final var file = LogisimFile.createNew(loader, null);
    file.retireAutosaveThread();
    final var folder = tempDir.resolve("folder.circ");
    Files.createDirectory(folder);

    assertFalse(loader.save(file, folder.toFile()));

    assertEquals(1, errors.size(), errors.toString());
    try (final var entries = Files.list(tempDir)) {
      assertEquals(List.of(folder), entries.toList(), "no .bak or staged file may be left");
    }
  }

  @Test
  void circuitsThatContainEachOtherAreRejectedOnLoad() throws Exception {
    final var loader = new Loader(null);
    final var file = LogisimFile.createNew(loader, null);
    file.retireAutosaveThread();
    final var outer = file.getMainCircuit();
    final var inner = new Circuit("inner", file, null);
    file.addCircuit(inner);
    assertNull(XmlReader.findSubcircuitCycle(file));
    addInstance(outer, inner, 100);
    assertNull(XmlReader.findSubcircuitCycle(file));
    addInstance(inner, outer, 200);
    assertEquals(3, XmlReader.findSubcircuitCycle(file).size());
    final var circ = tempDir.resolve("loop.circ").toFile();
    assertTrue(loader.save(file, circ));

    final var errors = new ArrayList<String>();
    final var failure = assertThrows(LoadFailedException.class, () -> open(circ, errors));

    assertTrue(failure.isShown());
    assertEquals(1, errors.size(), errors.toString());
    assertTrue(errors.get(0).contains("inner"), errors.toString());
  }

  private static void addInstance(Circuit parent, Circuit child, int x) {
    final var factory = child.getSubcircuitFactory();
    final var mutation = new CircuitMutation(parent);
    mutation.add(
        factory.createComponent(Location.create(x, 100, false), factory.createAttributeSet()));
    mutation.execute();
  }

  private static void assertLoadsDirty(LogisimFile file) {
    final var project = new Project(file);
    try {
      assertTrue(project.isFileDirty(), "the project no longer matches the file on disk");
    } finally {
      project.getSimulator().shutDown();
    }
  }

  private File savedPinCircuit(String name) {
    final var loader = new Loader(null);
    final var file = LogisimFile.createNew(loader, null);
    file.retireAutosaveThread();
    file.addLibrary(loader.getBuiltin().getLibrary(WiringLibrary._ID));
    final var attrs = Pin.FACTORY.createAttributeSet();
    attrs.setValue(StdAttr.WIDTH, BitWidth.create(4));
    final var mutation = new CircuitMutation(file.getMainCircuit());
    mutation.add(Pin.FACTORY.createComponent(Location.create(100, 100, false), attrs));
    mutation.execute();
    final var circ = tempDir.resolve(name).toFile();
    assertTrue(loader.save(file, circ));
    return circ;
  }

  private static LogisimFile open(File circ, List<String> errors) throws Exception {
    return open(circ, errors, 1);
  }

  private static LogisimFile open(File circ, List<String> errors, int autosaveChoice)
      throws Exception {
    final var file = recordingLoader(errors, autosaveChoice).openLogisimFile(circ);
    if (file != null) file.retireAutosaveThread();
    return file;
  }

  private static Loader recordingLoader(List<String> errors, int autosaveChoice) {
    return new Loader(null) {
      @Override
      public void showError(String description) {
        errors.add(description);
      }

      @Override
      public int showOptions(String message, String title, String[] options, int initial) {
        return autosaveChoice;
      }
    };
  }

  private static File autosaveFor(File circ) {
    return new File(circ.getParentFile(), "." + circ.getName() + ".revolution.autosave");
  }
}

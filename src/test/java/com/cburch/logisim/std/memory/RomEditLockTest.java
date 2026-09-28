/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.EditLockAction;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.gui.hex.HexFile;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.proj.Project;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RomEditLockTest {
  private Project project;
  private Circuit circuit;
  private Component rom;
  private MemContents contents;

  @BeforeEach
  void setUp() {
    final var file = LogisimFile.createNew(new Loader(null), null);
    file.retireAutosaveThread();
    project = new Project(file);
    circuit = file.getMainCircuit();
    circuit.setProject(project);
    project.setCurrentCircuit(circuit);
    final var factory = new Rom();
    rom = factory.createComponent(Location.create(100, 100, false), factory.createAttributeSet());
    final var add = new CircuitMutation(circuit);
    add.add(rom);
    add.execute();
    contents = rom.getAttributeSet().getValue(Rom.CONTENTS_ATTR);
    contents.set(0, 0x12);
    contents.set(255, 0x34);
    // This is the same image retained by an already-open hex editor or Poke tool.
    RomAttributes.register(contents, project);
  }

  @AfterEach
  void tearDown() {
    project.getSimulator().shutDown();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void existingImageRefusesEveryContentWriteAfterLocking(boolean wholeCircuit) {
    project.doAction(wholeCircuit
        ? EditLockAction.setCircuitLocked(circuit, true)
        : EditLockAction.setComponentsLocked(circuit, List.of(rom), true));
    final var history = project.getLastAction();
    final var dirty = project.isFileDirty();
    final var source = MemContents.create(8, 8, false);
    source.fill(0, 256, 0x56);

    contents.set(0, 0xff);
    contents.set(0, new long[] {0xaa, 0xbb});
    contents.fill(0, 256, 0x78);
    contents.clear();
    contents.condClear();
    contents.copyFrom(0, source, 0, 256);

    assertEquals(0x12, contents.get(0));
    assertEquals(0, contents.get(1));
    assertEquals(0x34, contents.get(255));
    assertSame(history, project.getLastAction());
    assertEquals(dirty, project.isFileDirty());

    project.undoAction();
    contents.set(0, 0x56);
    assertEquals(0x56, contents.get(0));
    project.undoAction();
    assertEquals(0x12, contents.get(0));
    project.redoAction();
    assertEquals(0x56, contents.get(0));
  }

  @Test
  void refusedWritePreservesRedoAndCleanProject() {
    contents.set(0, 0x56);
    project.undoAction();
    circuit.setEditLocked(true);
    assertFalse(project.isFileDirty());
    contents.clear();
    assertFalse(project.isFileDirty());
    assertTrue(project.getCanRedo());
    assertEquals(0x12, contents.get(0));
    circuit.setEditLocked(false);
    project.redoAction();
    assertEquals(0x56, contents.get(0));
  }

  @Test
  void fileImportChecksLockBeforeChangingExistingImage(@TempDir Path directory) throws Exception {
    final var image = directory.resolve("image.hex");
    Files.writeString(image, "v2.0 raw\n56 78\n");
    circuit.setComponentsEditLocked(List.of(rom), true);
    assertFalse(HexFile.open(contents, image.toFile()));
    assertEquals(0x12, contents.get(0));
    assertEquals(0x34, contents.get(255));
    circuit.setComponentsEditLocked(List.of(rom), false);
    assertTrue(HexFile.open(contents, image.toFile()));
    assertEquals(0x56, contents.get(0));
  }

  @Test
  void copiedRomIsIndependentOfOriginalLockAndReplacementImageIsGuarded() {
    circuit.setComponentsEditLocked(List.of(rom), true);
    final var copy = (RomAttributes) rom.getAttributeSet().clone();
    copy.getValue(Rom.CONTENTS_ATTR).set(0, 0x56);
    assertEquals(0x56, copy.getValue(Rom.CONTENTS_ATTR).get(0));
    assertEquals(0x12, contents.get(0));

    circuit.setComponentsEditLocked(List.of(rom), false);
    final var replacement = MemContents.create(8, 8, false);
    final var change = new CircuitMutation(circuit);
    change.set(rom, Rom.CONTENTS_ATTR, replacement);
    change.execute();
    circuit.setComponentsEditLocked(List.of(rom), true);
    replacement.set(0, 0x78);
    assertEquals(0, replacement.get(0));
  }

  @Test
  void lockedRamRemainsWritableSimulationState() {
    final var factory = new Ram();
    final var ram = factory.createComponent(
        Location.create(300, 100, false), factory.createAttributeSet());
    final var add = new CircuitMutation(circuit);
    add.add(ram);
    add.execute();
    circuit.setEditLocked(true);
    final var state = factory.getState(Instance.getInstanceFor(ram), project.getCircuitState());
    state.getContents().set(0, 0x56);
    assertEquals(0x56, state.getContents().get(0));
    state.getContents().clear();
    assertEquals(0, state.getContents().get(0));
  }
}

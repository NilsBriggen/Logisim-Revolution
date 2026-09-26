/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.proj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.Main;
import com.cburch.logisim.file.LibraryEvent;
import com.cburch.logisim.file.LibraryListener;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The project's dirty flag decides whether closing prompts, whether autosave writes, and what the
 * title and tabs show. Every path that changes the circuit (do, undo, redo, recovery) must keep the
 * flag and the file's DIRTY_STATE notifications truthful.
 */
class ProjectDirtyStateTest {

  @TempDir Path tempDir;

  private final List<Project> projects = new ArrayList<>();
  private final List<Boolean> dirtyEvents = new ArrayList<>();
  private final LibraryListener dirtyListener =
      event -> {
        if (event.getAction() == LibraryEvent.DIRTY_STATE) {
          dirtyEvents.add((Boolean) event.getData());
        }
      };
  private Project project;

  @BeforeEach
  void setUp() {
    project = track(newProject());
    project.addLibraryListener(dirtyListener);
  }

  @AfterEach
  void tearDown() {
    for (final var p : projects) {
      p.getLogisimFile().retireAutosaveThread();
      p.getSimulator().shutDown();
    }
  }

  @Test
  void freshProjectIsClean() {
    assertClean();
  }

  @Test
  void doActionMarksDirtyAndUndoReturnsToClean() {
    project.doAction(new Edit());
    assertDirty();
    project.undoAction();
    assertClean();
  }

  @Test
  void redoMarksDirtyAgainAndNotifies() {
    project.doAction(new Edit());
    project.undoAction();
    dirtyEvents.clear();

    project.redoAction();

    assertDirty();
    assertEquals(List.of(Boolean.TRUE), dirtyEvents, "redo must publish DIRTY_STATE");
  }

  @Test
  void redoOfNonModificationDoesNotMarkDirty() {
    project.doAction(new Edit(false));
    project.undoAction();
    project.redoAction();
    assertClean();
  }

  @Test
  void undoPastTheSavePointIsDirtyAndRedoBackIsClean() {
    project.doAction(new Edit());
    project.setFileAsClean();
    assertClean();

    project.undoAction();
    assertDirty();

    project.redoAction();
    assertClean();
  }

  @Test
  void newEditAfterUndoingPastTheSavePointStaysDirtyUntilSaved() {
    project.doAction(new Edit());
    project.setFileAsClean();
    project.undoAction();

    // The saved state was only reachable through redo, which this edit discards.
    project.doAction(new Edit());
    assertDirty();
    project.undoAction();
    assertDirty();

    project.setFileAsClean();
    assertClean();
  }

  @Test
  void clearingTheHistoryKeepsUnsavedEditsDirty() {
    project.doAction(new Edit());
    project.discardAllEdits();
    assertDirty();
  }

  @Test
  void clearingTheHistoryOfACleanProjectStaysClean() {
    project.doAction(new Edit());
    project.setFileAsClean();
    project.discardAllEdits();
    assertClean();
  }

  @Test
  void failingRedoLeavesTheProjectDirty() {
    final var edit = new Edit();
    project.doAction(edit);
    project.undoAction();
    edit.failNext = true;

    assertThrows(IllegalStateException.class, project::redoAction);
    assertDirty();
  }

  @Test
  void failingUndoLeavesTheProjectDirty() {
    final var edit = new Edit();
    project.doAction(edit);
    project.setFileAsClean();
    edit.failNext = true;

    assertThrows(IllegalStateException.class, project::undoAction);
    assertDirty();
  }

  @Test
  void recoveredAutosaveLoadsDirtyAndKeepsTheRecoveryFile() throws Exception {
    final var circ = savedCircuit("recover.circ");
    final var autosave = autosaveFor(circ);
    Files.copy(circ.toPath(), autosave.toPath());

    final var recovered = track(new Project(open(circ, 0)));

    assertTrue(recovered.getLogisimFile().isAutosaveLoaded());
    assertTrue(recovered.isFileDirty(), "recovered edits are not in the real file yet");
    assertTrue(recovered.getLogisimFile().isDirty());
    assertTrue(autosave.exists(), "the recovery must survive until an explicit Save or Discard");

    recovered.setFileAsClean();
    assertFalse(recovered.isFileDirty());
  }

  @Test
  void discardedAutosaveLoadsCleanAndIsRemoved() throws Exception {
    final var circ = savedCircuit("discard.circ");
    final var autosave = autosaveFor(circ);
    Files.copy(circ.toPath(), autosave.toPath());

    final var opened = track(new Project(open(circ, 1)));

    assertFalse(opened.getLogisimFile().isAutosaveLoaded());
    assertFalse(opened.isFileDirty());
    assertFalse(autosave.exists());
  }

  @Test
  void replacingAForcedDirtyFileStartsClean() throws Exception {
    project.setForcedDirty();
    final var replacement = LogisimFile.createNew(new Loader(null), null);
    replacement.retireAutosaveThread();
    project.setLogisimFile(replacement);
    assertClean();
  }

  private void assertDirty() {
    assertTrue(project.isFileDirty());
    assertTrue(project.getLogisimFile().isDirty(), "file flag must agree with the project");
  }

  private void assertClean() {
    assertFalse(project.isFileDirty());
    assertFalse(project.getLogisimFile().isDirty(), "file flag must agree with the project");
  }

  private Project track(Project p) {
    projects.add(p);
    return p;
  }

  private static Project newProject() {
    final var file = LogisimFile.createNew(new Loader(null), null);
    // Never let a test write a recovery file into the user's autosave directory.
    file.retireAutosaveThread();
    return new Project(file);
  }

  private File savedCircuit(String name) {
    final var loader = new Loader(null);
    final var file = LogisimFile.createNew(loader, null);
    file.retireAutosaveThread();
    final var circ = tempDir.resolve(name).toFile();
    assertTrue(loader.save(file, circ));
    return circ;
  }

  private static File autosaveFor(File circ) {
    return new File(circ.getParentFile(), "." + circ.getName() + ".revolution.autosave");
  }

  private static LogisimFile open(File circ, int autosaveChoice) throws Exception {
    // The recovery prompt is only offered with a GUI; other tests may have left headless set.
    final var wasHeadless = Main.headless;
    Main.headless = false;
    try {
      return openWithPrompt(circ, autosaveChoice);
    } finally {
      Main.headless = wasHeadless;
    }
  }

  private static LogisimFile openWithPrompt(File circ, int autosaveChoice) throws Exception {
    final var loader =
        new Loader(null) {
          @Override
          public int showOptions(
              String message, String title, String[] options, int initialSelection) {
            return autosaveChoice;
          }
        };
    final var file = loader.openLogisimFile(circ);
    file.retireAutosaveThread();
    return file;
  }

  /** An edit that touches nothing; only its bookkeeping in the undo log matters here. */
  private static final class Edit extends Action {
    private final boolean modification;
    private boolean failNext;

    Edit() {
      this(true);
    }

    Edit(boolean modification) {
      this.modification = modification;
    }

    @Override
    public void doIt(Project proj) {
      failIfRequested();
    }

    @Override
    public String getName() {
      return "edit";
    }

    @Override
    public boolean isModification() {
      return modification;
    }

    @Override
    public void undo(Project proj) {
      failIfRequested();
    }

    private void failIfRequested() {
      if (failNext) {
        failNext = false;
        throw new IllegalStateException("simulated failure");
      }
    }
  }
}

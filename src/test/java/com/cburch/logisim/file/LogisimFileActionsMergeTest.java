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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.file.LogisimFileActions.MergeConflictChoice;
import com.cburch.logisim.file.LogisimFileActions.MergeConflictDecision;
import com.cburch.logisim.file.LogisimFileActions.MergeConflictResolver;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.ttl.TtlLibrary;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LogisimFileActionsMergeTest {

  private static final MergeConflictResolver NO_CONFLICT_EXPECTED =
      (name, suggested) -> fail("unexpected conflict for " + name);

  @Test
  void mergingAFileThatUsesABuiltinLibraryAddsItWithoutAskingForAFile() {
    final var project = newProject("main");
    final var source = project.getLogisimFile();

    final var mergeLoader = new Loader(null);
    final var mergelib = LogisimFile.createNew(mergeLoader, null);
    mergelib.getMainCircuit().setName("other");
    mergelib.addLibrary(mergeLoader.getBuiltin().getLibrary(TtlLibrary._ID));

    // Before the fix, the "#TTL" descriptor was treated as a file name: the user was asked to
    // locate a file called "TTL" (in a headless test: a HeadlessException from the chooser).
    final var action =
        LogisimFileActions.mergeFile(mergelib, source, null, true, NO_CONFLICT_EXPECTED);
    assertNotNull(action);
    project.doAction(action);

    assertSame(
        source.getLoader().getBuiltin().getLibrary(TtlLibrary._ID),
        source.getLibrary(TtlLibrary._ID),
        "the project's own copy of the built-in library is added");
    assertNotNull(source.getCircuit("other"));
  }

  @Test
  void mergeIsASingleUndoableStep() {
    final var project = newProject("main");
    final var source = project.getLogisimFile();

    final var mergeLoader = new Loader(null);
    final var mergelib = LogisimFile.createNew(mergeLoader, null);
    mergelib.getMainCircuit().setName("other");
    mergelib.addLibrary(mergeLoader.getBuiltin().getLibrary(TtlLibrary._ID));

    final var action =
        LogisimFileActions.mergeFile(mergelib, source, null, true, NO_CONFLICT_EXPECTED);
    project.doAction(action);

    assertSame(action, project.getLastAction(), "the steps of the merge are not separate entries");
    assertEquals(1, project.getUndoActions().size());
    assertTrue(action.isModification());

    project.undoAction();
    assertNull(source.getCircuit("other"));
    assertNull(source.getLibrary(TtlLibrary._ID));

    project.redoAction();
    assertNotNull(source.getCircuit("other"));
    assertNotNull(source.getLibrary(TtlLibrary._ID));
  }

  @Test
  void cancellingAConflictMergesNothing() {
    final var project = newProject("main");
    final var mergelib = LogisimFile.createNew(new Loader(null), null);
    mergelib.getMainCircuit().setName("main");

    final var action =
        LogisimFileActions.mergeFile(
            mergelib,
            project.getLogisimFile(),
            null,
            true,
            (name, suggested) -> new MergeConflictDecision(MergeConflictChoice.CANCEL, false));

    assertNull(action, "nothing to do, so no undo entry either");
  }

  @Test
  void renameAppliedToAllConflictsIsAskedOnce() {
    final var project = newProject("main");
    final var source = project.getLogisimFile();
    source.addCircuit(new Circuit("helper", source, project));

    final var mergelib = LogisimFile.createNew(new Loader(null), null);
    mergelib.getMainCircuit().setName("main");
    mergelib.addCircuit(new Circuit("helper", mergelib, null));

    final var questions = new AtomicInteger();
    final var action =
        LogisimFileActions.mergeFile(
            mergelib,
            source,
            null,
            true,
            (name, suggested) -> {
              questions.incrementAndGet();
              return new MergeConflictDecision(MergeConflictChoice.RENAME, true);
            });
    project.doAction(action);

    assertEquals(1, questions.get());
    assertNotNull(source.getCircuit("main_1"));
    assertNotNull(source.getCircuit("helper_1"));
    assertNotNull(source.getCircuit("main"));
    assertNotNull(source.getCircuit("helper"));
  }

  @Test
  void loadingOnlyRejectedLibrariesLeavesNoUndoEntry() {
    final var project = newProject("main");
    final var source = project.getLogisimFile();
    final var ttl = source.getLoader().getBuiltin().getLibrary(TtlLibrary._ID);
    project.doAction(LogisimFileActions.loadLibrary(ttl, source));
    final var undoEntries = project.getUndoActions().size();

    // a second library of the same name is rejected
    final var otherTtl = new Loader(null).getBuiltin().getLibrary(TtlLibrary._ID);
    assertNull(LogisimFileActions.loadLibrary(otherTtl, source));
    project.doAction(LogisimFileActions.loadLibrary(otherTtl, source));
    assertEquals(undoEntries, project.getUndoActions().size());
    assertFalse(source.getLibraries().contains(otherTtl));
  }

  private static Project newProject(String mainName) {
    final var file = LogisimFile.createNew(new Loader(null), null);
    file.getMainCircuit().setName(mainName);
    final var project = new Project(file);
    file.getMainCircuit().setProject(project);
    return project;
  }
}

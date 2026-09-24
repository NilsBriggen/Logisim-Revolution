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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.Library;
import java.io.OutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LogisimFileAutosaveTest {
  @TempDir Path tempDir;

  @Test
  void replacementJoinsOldWriterWithoutDeletingSharedLoaderRecovery() throws Exception {
    final var recovery = tempDir.resolve("recovery.circ.autosave");
    Files.writeString(recovery, "loaded recovery");
    final var loader = mock(Loader.class);
    when(loader.deleteAutosave()).thenAnswer(invocation -> Files.deleteIfExists(recovery));
    final var entered = new CountDownLatch(1);
    final var interrupted = new CountDownLatch(1);
    final var release = new CountDownLatch(1);
    final var replacementSaved = new CountDownLatch(1);
    final var oldWriter = new AtomicReference<Thread>();
    final var oldWrites = new AtomicInteger();
    final var old = newFile(loader, true);
    final var replacement = newFile(loader, true);
    when(loader.autosave(old)).thenAnswer(invocation -> {
      oldWriter.set(Thread.currentThread());
      oldWrites.incrementAndGet();
      entered.countDown();
      awaitRelease(release, interrupted);
      Files.writeString(recovery, "old write completed");
      return true;
    });
    when(loader.autosave(replacement)).thenAnswer(invocation -> {
      Files.writeString(recovery, "replacement recovery");
      replacementSaved.countDown();
      return true;
    });
    final var project = new Project(old);
    try {
      old.setDirty(true);
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      final var replaced = onEdt(() -> project.setLogisimFile(replacement));
      assertTrue(interrupted.await(5, TimeUnit.SECONDS));
      assertFalse(replaced.isDone(), "Replacement must wait for the in-flight old write");
      assertSame(old, project.getLogisimFile());
      release.countDown();
      replaced.get(5, TimeUnit.SECONDS);
      assertFalse(oldWriter.get().isAlive());
      assertSame(replacement, project.getLogisimFile());
      assertTrue(Files.exists(recovery));
      old.setDirty(true);
      replacement.setDirty(true);
      assertTrue(replacementSaved.await(5, TimeUnit.SECONDS));
      replacement.retireAutosaveThread();
      assertEquals("replacement recovery", Files.readString(recovery));
      assertEquals(1, oldWrites.get());
      verify(loader, never()).deleteAutosave();
    } finally {
      release.countDown();
      old.retireAutosaveThread();
      replacement.retireAutosaveThread();
      project.getSimulator().shutDown();
    }
  }

  @Test
  void sameFileAndRejectedNullReplacementKeepCurrentAutosaveAndRecovery() throws Exception {
    final var recovery = tempDir.resolve("retained.circ.autosave");
    Files.writeString(recovery, "retained recovery");
    final var loader = mock(Loader.class);
    when(loader.deleteAutosave()).thenAnswer(invocation -> Files.deleteIfExists(recovery));
    final var saved = new CountDownLatch(1);
    when(loader.autosave(any())).thenAnswer(invocation -> {
      saved.countDown();
      return true;
    });
    final var file = newFile(loader, true);
    final var project = new Project(file);
    try {
      onEdt(() -> {
        file.setDirty(true);
        project.setLogisimFile(file);
        assertTrue(file.isDirty());
        assertThrows(NullPointerException.class, () -> project.setLogisimFile(null));
        assertSame(file, project.getLogisimFile());
      }).get(5, TimeUnit.SECONDS);
      assertTrue(saved.await(5, TimeUnit.SECONDS));
      assertEquals("retained recovery", Files.readString(recovery));
      verify(loader, never()).deleteAutosave();
    } finally {
      file.retireAutosaveThread();
      project.getSimulator().shutDown();
    }
  }

  @Test
  void interruptedRetirementStillJoinsAndRestoresInterruptFlag() throws Exception {
    final var loader = mock(Loader.class);
    final var entered = new CountDownLatch(1);
    final var interrupted = new CountDownLatch(1);
    final var release = new CountDownLatch(1);
    final var writer = new AtomicReference<Thread>();
    when(loader.autosave(any())).thenAnswer(invocation -> {
      writer.set(Thread.currentThread());
      entered.countDown();
      awaitRelease(release, interrupted);
      return true;
    });
    final var file = newFile(loader, true);
    final var restored = new AtomicBoolean();
    final var retired = new FutureTask<Void>(() -> {
      Thread.currentThread().interrupt();
      file.retireAutosaveThread();
      restored.set(Thread.currentThread().isInterrupted());
      return null;
    });
    try {
      file.setDirty(true);
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      final var retiring = new Thread(retired, "autosave-retirement-test");
      retiring.setDaemon(true);
      retiring.start();
      assertTrue(interrupted.await(5, TimeUnit.SECONDS));
      assertFalse(retired.isDone());
      release.countDown();
      retired.get(5, TimeUnit.SECONDS);
      assertTrue(restored.get());
      assertFalse(writer.get().isAlive());
      verify(loader, never()).deleteAutosave();
    } finally {
      release.countDown();
      file.retireAutosaveThread();
    }
  }

  @Test
  void explicitCloseDeletesRecoveryOnlyAfterTheWriterFinishes() throws Exception {
    final var recovery = tempDir.resolve("closed.circ.autosave");
    Files.writeString(recovery, "existing recovery");
    final var loader = mock(Loader.class);
    final var entered = new CountDownLatch(1);
    final var interrupted = new CountDownLatch(1);
    final var release = new CountDownLatch(1);
    final var writer = new AtomicReference<Thread>();
    when(loader.autosave(any())).thenAnswer(invocation -> {
      writer.set(Thread.currentThread());
      entered.countDown();
      awaitRelease(release, interrupted);
      Files.writeString(recovery, "last write");
      return true;
    });
    when(loader.deleteAutosave()).thenAnswer(invocation -> {
      assertFalse(writer.get().isAlive());
      return Files.deleteIfExists(recovery);
    });
    final var file = newFile(loader, true);
    try {
      file.setDirty(true);
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      final var closed = onEdt(() -> file.stopAutosaveThread(true));
      assertTrue(interrupted.await(5, TimeUnit.SECONDS));
      assertFalse(closed.isDone());
      assertTrue(Files.exists(recovery));
      release.countDown();
      closed.get(5, TimeUnit.SECONDS);
      assertFalse(Files.exists(recovery));
      verify(loader).deleteAutosave();
    } finally {
      release.countDown();
      file.retireAutosaveThread();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void autosaveErrorsAreDeliveredOnEdt(boolean serializationError) throws Exception {
    final var loader = mock(Loader.class);
    final var reported = new CountDownLatch(1);
    final var onEdt = new AtomicBoolean();
    doAnswer(invocation -> {
      onEdt.set(SwingUtilities.isEventDispatchThread());
      reported.countDown();
      return null;
    }).when(loader).showError(anyString());
    final var file = newFile(loader, true);
    addUnknownLibrary(file);
    when(loader.autosave(file)).thenAnswer(invocation -> {
      if (serializationError) {
        assertThrows(IOException.class, () -> file.write(OutputStream.nullOutputStream(), loader));
      }
      return false;
    });
    try {
      file.setDirty(true);
      assertTrue(reported.await(5, TimeUnit.SECONDS));
      assertTrue(onEdt.get());
    } finally {
      file.retireAutosaveThread();
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void edtReplacementDoesNotWaitForErrorDialogAndSuppressesRetiredErrors(
      boolean serializationError) throws Exception {
    final var loader = mock(Loader.class);
    final var entered = new CountDownLatch(1);
    final var interrupted = new CountDownLatch(1);
    final var release = new CountDownLatch(1);
    final var dismissDialog = new CountDownLatch(1);
    final var old = newFile(loader, true);
    final var replacement = newFile(loader, false);
    addUnknownLibrary(old);
    doAnswer(invocation -> {
      // Model a modal error call that cannot return while the EDT is joining its caller.
      awaitRelease(dismissDialog, new CountDownLatch(0));
      return null;
    }).when(loader).showError(anyString());
    when(loader.autosave(old)).thenAnswer(invocation -> {
      entered.countDown();
      awaitRelease(release, interrupted);
      if (serializationError) {
        assertThrows(IOException.class, () -> old.write(OutputStream.nullOutputStream(), loader));
      }
      return false;
    });
    final var project = new Project(old);
    try {
      old.setDirty(true);
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      final var replaced = onEdt(() -> project.setLogisimFile(replacement));
      assertTrue(interrupted.await(5, TimeUnit.SECONDS));
      release.countDown();
      replaced.get(5, TimeUnit.SECONDS);
      onEdt(() -> {}).get(5, TimeUnit.SECONDS);
      assertSame(replacement, project.getLogisimFile());
      verify(loader, never()).showError(anyString());
      verify(loader, never()).deleteAutosave();
    } finally {
      release.countDown();
      dismissDialog.countDown();
      old.retireAutosaveThread();
      replacement.retireAutosaveThread();
      project.getSimulator().shutDown();
    }
  }

  private static LogisimFile newFile(Loader loader, boolean autosaveEnabled) {
    final var file = new LogisimFile(loader, autosaveEnabled, () -> 10L);
    file.addCircuit(new Circuit("main", file, null));
    return file;
  }

  private static void addUnknownLibrary(LogisimFile file) {
    final var library = mock(Library.class);
    when(library.getName()).thenReturn("unknown");
    file.addLibrary(library);
  }

  private static FutureTask<Void> onEdt(Runnable action) {
    final var task = new FutureTask<Void>(action, null);
    SwingUtilities.invokeLater(task);
    return task;
  }

  private static void awaitRelease(CountDownLatch release, CountDownLatch interrupted) {
    var done = false;
    while (!done) {
      try {
        done = release.await(10, TimeUnit.SECONDS);
        if (!done) throw new AssertionError("Timed out waiting for test release");
      } catch (InterruptedException ex) {
        interrupted.countDown();
      }
    }
  }
}

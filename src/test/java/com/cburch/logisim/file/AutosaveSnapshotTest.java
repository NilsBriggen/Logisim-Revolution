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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/**
 * An autosave worker has the event thread serialise the project, where the editor changes it, and
 * must neither deadlock with an event thread waiting for the worker to stop nor lose the snapshot
 * to an interrupt.
 */
class AutosaveSnapshotTest {

  @Test
  void returnsTheSnapshotTakenOnTheEventThread() throws Exception {
    final var snapshot = new CompletableFuture<Boolean>();
    SwingUtilities.invokeLater(() -> snapshot.complete(SwingUtilities.isEventDispatchThread()));

    assertTrue(LogisimFile.awaitSnapshot(snapshot, () -> true));
  }

  @Test
  void stopsWaitingOnceTheWorkerIsStopped() {
    final var keepWaiting = new AtomicBoolean(true);
    final var never = new CompletableFuture<String>();
    new Thread(
            () -> {
              try {
                Thread.sleep(150);
              } catch (InterruptedException ignored) {
                // stop anyway
              }
              keepWaiting.set(false);
            })
        .start();

    assertThrows(
        InterruptedIOException.class, () -> LogisimFile.awaitSnapshot(never, keepWaiting::get));
  }

  @Test
  void anInterruptDoesNotAbandonTheSnapshot() throws Exception {
    final var snapshot = new CompletableFuture<String>();
    final var waiter = Thread.currentThread();
    new Thread(
            () -> {
              waiter.interrupt();
              try {
                Thread.sleep(250);
              } catch (InterruptedException ignored) {
                // complete anyway
              }
              snapshot.complete("content");
            })
        .start();

    assertEquals("content", LogisimFile.awaitSnapshot(snapshot, () -> true));
    assertTrue(Thread.interrupted(), "the interrupt is passed on, not swallowed");
  }

  @Test
  void failedSnapshotIsReportedAsSuch() {
    final var snapshot = new CompletableFuture<String>();
    snapshot.completeExceptionally(new IOException("disk"));

    assertThrows(IOException.class, () -> LogisimFile.awaitSnapshot(snapshot, () -> true));
  }
}

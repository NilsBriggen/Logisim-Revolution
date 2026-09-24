/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.log;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.TestBase;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Tests that a failure to write the log file is reported.
 *
 * <p>The failure path used to turn file logging off and return, so the user ticked "log to file"
 * and silently got nothing.
 */
public class LogThreadTest extends TestBase {

  @Test
  public void fileFailureIsReported() {
    final var seen = new AtomicReference<File>();
    final var cause = new IOException("permission denied");
    final var file = new File("/nowhere/log.txt");

    final var previous = LogThread.setFailureReporter((f, e) -> seen.set(f));
    try {
      LogThread.reportFileFailure(file, cause);
    } finally {
      LogThread.setFailureReporter(previous);
    }

    assertSame(file, seen.get(), "the failure must reach the reporter");
  }

  /** Swapping the reporter must hand back the one it replaced, so a test can restore it. */
  @Test
  public void setFailureReporterReturnsThePreviousOne() {
    final java.util.function.BiConsumer<File, IOException> mine = (f, e) -> {};

    final var original = LogThread.setFailureReporter(mine);
    try {
      assertSame(mine, LogThread.setFailureReporter(mine));
    } finally {
      LogThread.setFailureReporter(original);
    }
  }

  /** The message has to name the file, or the user cannot tell which path failed. */
  @Test
  public void failureMessageNamesTheFile() {
    final var message = LogThread.fileFailureMessage(new File("/tmp/simulation.csv"));

    assertTrue(message.contains("simulation.csv"), message);
    assertFalse(message.isBlank());
  }

  @Test
  public void failureMessageToleratesNoFile() {
    assertFalse(LogThread.fileFailureMessage(null).isBlank());
  }
}

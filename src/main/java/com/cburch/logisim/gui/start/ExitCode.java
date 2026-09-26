/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.start;

/**
 * Process exit codes of the command-line modes ({@code --tty}, {@code --test-vector},
 * {@code --test-circuit}, {@code --new-file-format}, {@code --test-fpga}).
 *
 * <p>They are part of the documented command-line contract (see {@code --help} and the
 * "Command-line options" help page), so scripts can rely on them: do not renumber.
 */
public final class ExitCode {
  /** Everything requested was done and every check passed. */
  public static final int SUCCESS = 0;

  /** The circuit did not behave: it oscillated, or test vectors or the test bench failed. */
  public static final int SIMULATION_FAILED = 1;

  /** The project could not be loaded, or loading it reported errors. */
  public static final int LOAD_ERROR = 2;

  /** Any other failure, such as an unreadable memory image or a missing halt pin. */
  public static final int FAILURE = 3;

  /** The command line was invalid. */
  public static final int USAGE = 10;

  /** An unexpected internal error occurred. */
  public static final int INTERNAL_ERROR = 100;

  private ExitCode() {}

  /** Returns the more severe of two exit codes, for a run that processes several files. */
  static int worst(int first, int second) {
    return Math.max(first, second);
  }
}

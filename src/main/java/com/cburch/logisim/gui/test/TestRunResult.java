/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.test;

import java.util.List;

/**
 * The outcome of running one test vector file against one circuit from the command line.
 *
 * @param project the project file name, for reports
 * @param circuit the circuit tested
 * @param vectorFile the test vector file
 * @param rows one result per evaluated row, in row order
 * @param error why the run could not start (unreadable vector, unknown circuit), or null
 * @param millis how long the run took
 */
public record TestRunResult(
    String project,
    String circuit,
    String vectorFile,
    List<Row> rows,
    String error,
    long millis) {

  /**
   * One evaluated row.
   *
   * @param row the one-based row number in the vector file's data
   * @param failures why the row failed (mismatched outputs, failed assertions); empty if it passed
   */
  public record Row(int row, List<String> failures) {
    public boolean passed() {
      return failures.isEmpty();
    }
  }

  public static TestRunResult error(String project, String circuit, String vectorFile, String error) {
    return new TestRunResult(project, circuit, vectorFile, List.of(), error, 0);
  }

  public int passedCount() {
    return (int) rows.stream().filter(Row::passed).count();
  }

  public int failedCount() {
    return rows.size() - passedCount();
  }
}

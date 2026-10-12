/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.test;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.TestVectorEvaluator;
import com.cburch.logisim.data.TestException;
import com.cburch.logisim.data.TestVector;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.UniquelyNamedThread;
import java.util.ArrayList;
import java.util.List;

public class TestThread extends UniquelyNamedThread {

  private final Project project;
  private final Circuit circuit;
  private final CircuitState circuitState;
  private final TestVector vector;
  private final TestVectorEvaluator evaluator;
  private Model model;
  private boolean canceled = false;
  private boolean paused = false;

  public TestThread(Model model) throws TestException {
    super("TestThread-Model");
    this.model = model;

    this.project = model.getProject();
    this.circuit = model.getCircuit();
    this.circuitState = this.project.getCircuitState().cloneAsNewRootState(this);
    this.vector = model.getVector();
    this.evaluator = new TestVectorEvaluator(circuitState, vector);
  }

  // used only for automated testing via command line arguments
  private TestThread(Project proj, Circuit circuit, TestVector vec) throws TestException {
    super("TestThread-Project");
    this.project = proj;
    this.circuit = circuit;
    this.circuitState = CircuitState.createRootState(this.project, this.circuit, Thread.currentThread());
    this.vector = vec;
    evaluator = new TestVectorEvaluator(circuitState, vector);
  }

  // used only for automated testing via command line arguments
  public static int doTestVector(Project proj, Circuit circuit, String vectorname) {
    final var result = runTestVector(proj, circuit, vectorname);
    return result.error() != null ? -1 : result.failedCount();
  }

  /**
   * Runs a test vector file against a circuit for the command line, printing progress and
   * failures as it goes.
   *
   * @return the result; its error is set if the vector could not be loaded or set up
   */
  public static TestRunResult runTestVector(Project proj, Circuit circuit, String vectorname) {
    final var projectName = proj.getLogisimFile().getName();
    System.out.println(S.get("testLoadingVector", vectorname));
    TestVector vec;
    try {
      vec = new TestVector(vectorname);
    } catch (Exception e) {
      final var message = S.get("testLoadingFailed", e.getMessage());
      System.err.println(message);
      return TestRunResult.error(projectName, circuit.getName(), vectorname, message);
    }

    TestThread tester;
    try {
      tester = new TestThread(proj, circuit, vec);
    } catch (TestException e) {
      final var message = S.get("testSetupFailed", e.getMessage());
      System.err.println(message);
      return TestRunResult.error(projectName, circuit.getName(), vectorname, message);
    }
    final var undriven = TestVectorEvaluator.findUndrivenInputs(vec, circuit);
    if (!undriven.isEmpty()) {
      System.err.println(S.get("testUndrivenInputs", String.join(", ", undriven)));
    }
    final var start = System.currentTimeMillis();
    final var rows = tester.runRows();
    return new TestRunResult(
        projectName, circuit.getName(), vectorname, rows, null, System.currentTimeMillis() - start);
  }

  public int doTestVector() {
    return (int) runRows().stream().filter(row -> !row.passed()).count();
  }

  private List<TestRunResult.Row> runRows() {
    System.out.println(S.get("testRunning", Integer.toString(vector.data.size())));
    final var rows = new ArrayList<TestRunResult.Row>();
    final var passFail = evaluator.evaluate((row, report) -> {
      System.out.print((row + 1) + " \r");
      final var failures = new ArrayList<String>();
      if (report != null && !report.isEmpty()) {
        System.out.println();
        System.err.println(S.get("testFailed", Integer.toString(row + 1)));
        for (final var e1 : report) {
          System.out.println("  " + e1);
          failures.add(e1.toString());
        }
      }
      rows.add(new TestRunResult.Row(row + 1, List.copyOf(failures)));
    });
    System.out.println();
    System.out.println(S.get("testResults", Integer.toString(passFail[0]), Integer.toString(passFail[1])));
    return rows;
  }

  public void cancel() {
    canceled = true;
  }

  @Override
  public void run() {
    try {
      executeSequentialTests();
    } finally {
      model.stop();
    }
  }

  private void executeSequentialTests() {
    evaluator.evaluate((row, report) -> {
      canceled = canceled || !model.setResult(vector, row, report);
      if (canceled) evaluator.setCanceled(canceled);
    });
  }

  public void setPaused(boolean paused) {
    this.paused = paused;
  }
}

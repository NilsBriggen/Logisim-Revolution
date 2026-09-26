/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.test;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.TestVectorEvaluator;
import com.cburch.logisim.data.TestException;
import com.cburch.logisim.data.TestVector;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.EventSourceWeakSupport;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;

class Model implements CircuitListener {

  private final EventSourceWeakSupport<ModelListener> listeners;
  private final Project project;
  private final Circuit circuit;
  // Display order of the rows. It depends only on the vector, never on the results, so rows do not
  // move while a run fills them in or when the results are reset.
  private final ArrayList<Integer> sortedIndices = new ArrayList<>();
  // Results arrive from the test thread one row at a time; at most one refresh is queued on the
  // EDT at any moment, so a large vector cannot flood the event queue.
  private final AtomicBoolean refreshPending = new AtomicBoolean();
  private boolean selected = false;
  private boolean running;
  private boolean paused;
  private TestThread tester;
  private int numPass = 0;
  private int numFail = 0;
  private TestVector vec = null;
  private ArrayList<TestVectorEvaluator.LineReport>[] results;

  public Model(Project proj, Circuit circuit) {
    listeners = new EventSourceWeakSupport<>();
    this.circuit = circuit;
    this.project = proj;
    // Listen for the lifetime of the model rather than the lifetime of a test
    // run: TestThread comes and goes, and circuit listeners are held weakly, so
    // a thread-owned registration is dropped or duplicated unpredictably.
    circuit.addCircuitListener(this);
  }

  @Override
  public void circuitChanged(CircuitEvent event) {
    // Renaming or changing the viewed/haloed circuit does not change its behavior.
    // In particular, Project emits DISPLAY_CHANGE on both sides of an HDL editor round trip.
    if (event.getAction() == CircuitEvent.ACTION_SET_NAME
        || event.getAction() == CircuitEvent.ACTION_DISPLAY_CHANGE) return;
    clearResults();
  }

  public void addModelListener(ModelListener l) {
    listeners.add(l);
  }

  public void clearResults() {
    stop();
    synchronized (this) {
      if (vec == null || results == null) return;
      numPass = numFail = 0;
    }
    fireTestResultsChanged();
  }

  private void fireTestingChanged() {
    for (ModelListener listener : listeners) {
      listener.testingChanged();
    }
  }

  private void fireTestResultsChanged() {
    for (ModelListener listener : listeners) {
      listener.testResultsChanged(numPass, numFail);
    }
  }

  private void fireVectorChanged() {
    for (ModelListener listener : listeners) {
      listener.vectorChanged();
    }
  }

  public Circuit getCircuit() {
    return circuit;
  }

  public int getFail() {
    return numFail;
  }

  public int getPass() {
    return numPass;
  }

  public Project getProject() {
    return project;
  }

  public ArrayList<TestVectorEvaluator.LineReport>[] getResults() {
    return results;
  }

  public TestVector getVector() {
    return vec;
  }

  @SuppressWarnings("unchecked")
  public synchronized void setVector(TestVector v) {
    stop();
    synchronized (this) {
      vec = v;
      results = ((v != null) ? (new ArrayList[v.data.size()]) : null);
      numPass = numFail = 0;
      updateSortedIndices();
    }
    fireVectorChanged();
  }

  public boolean isPaused() {
    return paused;
  }

  public void setPaused(boolean paused) {
    synchronized (this) {
      if (running && tester != null) tester.setPaused(paused);
      this.paused = paused;
    }
    fireTestingChanged();
  }

  public boolean isRunning() {
    return running;
  }

  public boolean isSelected() {
    return selected;
  }

  public void setSelected(boolean value) {
    if (selected == value) return;
    selected = value;
    setPaused(!selected);
  }

  public void removeModelListener(ModelListener l) {
    listeners.remove(l);
  }

  public boolean setResult(TestVector v, int idx, ArrayList<TestVectorEvaluator.LineReport> report) {
    synchronized (this) {
      if (v != vec || idx < 0 || idx >= results.length || idx != numPass + numFail) return false;
      if (report == null || report.isEmpty()) {
        results[idx] = null;
        numPass++;
      } else {
        results[idx] = report;
        numFail++;
      }
    }
    resultsChanged();
    return true;
  }

  public void updateResult(TestVector v, int idx, ArrayList<TestVectorEvaluator.LineReport> err) {
    synchronized (this) {
      if (v != vec || idx < 0 || idx >= results.length) return;
      // Update the result, adjusting pass/fail counts if needed
      ArrayList<TestVectorEvaluator.LineReport> oldResult = results[idx];
      boolean wasCounted = (idx < numPass + numFail);

      results[idx] = err;

      if (wasCounted) {
        // The old result was already counted, so adjust counts
        if (oldResult == null && err != null) {
          // Changed from pass to fail
          numPass--;
          numFail++;
        } else if (oldResult != null && err == null) {
          // Changed from fail to pass
          numPass++;
          numFail--;
        }
        // If both are null or both are non-null, counts don't change
      } else {
        // The old result was not counted, so add the new one
        if (err == null) {
          numPass++;
        } else {
          numFail++;
        }
      }
    }
    resultsChanged();
  }

  private void resultsChanged() {
    if (SwingUtilities.isEventDispatchThread()) {
      fireTestResultsChanged();
    } else if (refreshPending.compareAndSet(false, true)) {
      SwingUtilities.invokeLater(() -> {
        refreshPending.set(false);
        fireTestResultsChanged();
      });
    }
  }

  public int sortedIndex(int i) {
    return i < sortedIndices.size() ? sortedIndices.get(i) : i;
  }

  public void start() throws TestException {
    synchronized (this) {
      if (vec == null) return;
      if (running) {
        setPaused(false);
        return;
      }
      tester = new TestThread(this);
      running = true;
      paused = false;
      tester.start();
    }
    fireTestingChanged();
  }

  public void stop() {
    synchronized (this) {
      if (!running) return;
      running = false;
      if (tester != null) tester.cancel();
      tester = null;
    }
    fireTestingChanged();
  }

  private void updateSortedIndices() {
    sortedIndices.clear();
    if (vec == null) return;
    for (var i = 0; i < vec.data.size(); i++) sortedIndices.add(i);
    // By set, then by sequence, then in file order (the sort is stable). TestVector already
    // requires ascending sets and sequences, so this is normally the file order itself.
    sortedIndices.sort((a, b) -> {
      final var setCompare = Integer.compare(vec.setNumbers[a], vec.setNumbers[b]);
      return setCompare != 0 ? setCompare : Integer.compare(vec.seqNumbers[a], vec.seqNumbers[b]);
    });
  }
}

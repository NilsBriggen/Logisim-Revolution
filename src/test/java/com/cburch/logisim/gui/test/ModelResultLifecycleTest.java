/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.TestVectorEvaluator;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.TestVector;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.instance.InstanceComponent;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.vhdl.base.HdlModel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModelResultLifecycleTest {
  @TempDir Path tempDir;

  @Test
  void completedResultsSurviveDisplayChangeAndProjectHdlRoundTrip() throws Exception {
    final var vector = vector();
    SwingUtilities.invokeAndWait(() -> {
      try (final var fixture = new Fixture(vector)) {
        final var model = fixture.model;
        final var reports = model.getResults().clone();
        final var order = new int[] {model.sortedIndex(0), model.sortedIndex(1),
            model.sortedIndex(2), model.sortedIndex(3)};
        fixture.project.getCurrentCircuit().displayChanged();
        assertCompleted(model);

        final var events = new ArrayList<Integer>();
        final CircuitListener trace = event -> events.add(event.getAction());
        model.getCircuit().addCircuitListener(trace);
        try {
          fixture.project.setCurrentHdlModel(mock(HdlModel.class));
          assertFalse(model.isSelected());
          assertTrue(model.isPaused());
          assertCompleted(model);
          fixture.project.setCurrentCircuit(model.getCircuit());
          assertSame(model, fixture.history.select(model.getCircuit()));
          assertTrue(model.isSelected());
          assertCompleted(model);
          assertEquals(List.of(CircuitEvent.ACTION_DISPLAY_CHANGE,
              CircuitEvent.ACTION_DISPLAY_CHANGE), events);
          assertArrayEquals(reports, model.getResults());
          assertArrayEquals(order, new int[] {model.sortedIndex(0), model.sortedIndex(1),
              model.sortedIndex(2), model.sortedIndex(3)});
        } finally {
          model.getCircuit().removeCircuitListener(trace);
        }
      }
    });
  }

  @Test
  void actualStructureAdditionStillInvalidatesCompletedResults() throws Exception {
    final var vector = vector();
    SwingUtilities.invokeAndWait(() -> {
      try (final var fixture = new Fixture(vector)) {
        final var mutation = new CircuitMutation(fixture.model.getCircuit());
        mutation.add(Wire.create(Location.create(300, 100, true),
            Location.create(400, 100, true)));
        mutation.execute();
        assertCleared(fixture.model);
        assertSame(vector, fixture.model.getVector());
      }
    });
  }

  @Test
  void invalidationEventStillClearsResultsWhileSelectionIsSuspended() throws Exception {
    final var vector = vector();
    SwingUtilities.invokeAndWait(() -> {
      try (final var fixture = new Fixture(vector)) {
        fixture.project.setCurrentHdlModel(mock(HdlModel.class));
        assertCompleted(fixture.model);
        fixture.input.fireInvalidated();
        assertCleared(fixture.model);
        fixture.project.setCurrentCircuit(fixture.model.getCircuit());
        assertCleared(fixture.model);
      }
    });
  }

  @Test
  void replacingVectorStillClearsCompletedResults() throws Exception {
    final var vector = vector();
    final var replacement = vector();
    SwingUtilities.invokeAndWait(() -> {
      try (final var fixture = new Fixture(vector)) {
        fixture.model.setVector(replacement);
        assertSame(replacement, fixture.model.getVector());
        assertCleared(fixture.model);
      }
    });
  }

  @Test
  void rowsStayInFileOrderWhateverTheResults() throws Exception {
    final var vector = vector();
    SwingUtilities.invokeAndWait(() -> {
      try (final var fixture = new Fixture(vector)) {
        // Rows 2 and 3 fail; they used to be moved to the top, and back again on Reset.
        assertArrayEquals(new int[] {0, 1, 2, 3}, order(fixture.model));
        fixture.model.clearResults();
        assertArrayEquals(new int[] {0, 1, 2, 3}, order(fixture.model));
      }
    });
  }

  @Test
  void resultsFromTheTestThreadAreCoalescedOnTheEdt() throws Exception {
    final var rows = 5000;
    final var big = Files.createTempFile(tempDir, "big-", ".txt");
    Files.writeString(big, "a y\n" + "0 0\n".repeat(rows));
    final var vector = new TestVector(big.toFile());
    final var small = vector();
    final var fixture = new AtomicReference<Fixture>();
    SwingUtilities.invokeAndWait(() -> {
      fixture.set(new Fixture(small));
      fixture.get().model.setVector(vector);
    });
    final var model = fixture.get().model;
    final var notifications = new AtomicInteger();
    final ModelListener counter = new ModelListener() {
      @Override
      public void testingChanged() {}

      @Override
      public void testResultsChanged(int numPass, int numFail) {
        notifications.incrementAndGet();
      }

      @Override
      public void vectorChanged() {}
    };
    model.addModelListener(counter);
    final var release = new CountDownLatch(1);
    try {
      // Hold the EDT while the "test thread" (this one) reports every row.
      SwingUtilities.invokeLater(() -> {
        try {
          release.await();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }
      });
      for (var row = 0; row < rows; row++) {
        assertTrue(model.setResult(vector, row, null));
      }
      release.countDown();
      SwingUtilities.invokeAndWait(() -> {});
      assertEquals(rows, model.getPass());
      // One queued refresh, not one per row.
      assertEquals(1, notifications.get());
    } finally {
      release.countDown();
      model.removeModelListener(counter);
      SwingUtilities.invokeAndWait(() -> fixture.get().close());
    }
  }

  @Test
  void loadVectorChooserStartsNextToTheLastVector() throws Exception {
    final var vector = vector();
    SwingUtilities.invokeAndWait(() -> {
      try (final var fixture = new Fixture(vector)) {
        // An unsaved project has no folder: keep the chooser's default.
        assertEquals(null, TestFrame.defaultVectorDirectory(null, fixture.project));
        final var last = tempDir.resolve("vectors").resolve("v.txt").toFile();
        assertEquals(last.getParentFile(),
            TestFrame.defaultVectorDirectory(last, fixture.project));
      }
    });
  }

  @Test
  void inputsTheVectorDoesNotSetAreReported() throws Exception {
    final var vector = vector();
    final var outputsOnly = Files.createTempFile(tempDir, "outputs-", ".txt");
    Files.writeString(outputsOnly, "y\n0\n");
    final var partial = new TestVector(outputsOnly.toFile());
    SwingUtilities.invokeAndWait(() -> {
      try (final var fixture = new Fixture(vector)) {
        final var circuit = fixture.model.getCircuit();
        assertEquals(List.of(), TestVectorEvaluator.findUndrivenInputs(vector, circuit));
        assertEquals(List.of("a"), TestVectorEvaluator.findUndrivenInputs(partial, circuit));
      }
    });
  }

  @Test
  void failedCellsShowExpectedThenComputed() {
    final var one = Value.createKnown(1, 1);
    final var zero = Value.createKnown(1, 0);
    final var arrow = TestPanel.EXPECTED_COMPUTED_SEPARATOR;
    assertEquals("1", TestPanel.cellText(one, false, null, 2));
    assertEquals("1" + arrow + "0", TestPanel.cellText(one, false, zero, 2));
    assertEquals("<float>", TestPanel.cellText(Value.UNKNOWN, true, null, 2));
    assertEquals("<float>" + arrow + "1", TestPanel.cellText(Value.UNKNOWN, true, one, 2));
  }

  private static int[] order(Model model) {
    final var order = new int[model.getVector().data.size()];
    for (var i = 0; i < order.length; i++) order[i] = model.sortedIndex(i);
    return order;
  }

  private TestVector vector() throws Exception {
    final var path = Files.createTempFile(tempDir, "identity-", ".txt");
    Files.writeString(path, "a y\n0 0\n1 1\n0 1\n1 0\n");
    return new TestVector(path.toFile());
  }

  private static void assertCompleted(Model model) {
    assertEquals(2, model.getPass());
    assertEquals(2, model.getFail());
  }

  private static void assertCleared(Model model) {
    assertEquals(0, model.getPass());
    assertEquals(0, model.getFail());
  }

  private static final class Fixture implements AutoCloseable {
    private final Project project;
    private final TestFrame.ModelHistory history;
    private final Model model;
    private final InstanceComponent input;
    private final ProjectListener listener;

    private Fixture(TestVector vector) {
      final var file = LogisimFile.createNew(new Loader(null), null);
      project = new Project(file);
      final var circuit = file.getMainCircuit();
      final var inputAttrs = Pin.FACTORY.createAttributeSet();
      inputAttrs.setValue(StdAttr.LABEL, "a");
      final var outputAttrs = Pin.FACTORY.createAttributeSet();
      outputAttrs.setValue(StdAttr.LABEL, "y");
      outputAttrs.setValue(Pin.ATTR_TYPE, Pin.OUTPUT);
      input = (InstanceComponent) Pin.FACTORY.createComponent(
          Location.create(100, 100, true), inputAttrs);
      final var output = Pin.FACTORY.createComponent(Location.create(200, 100, true), outputAttrs);
      final var mutation = new CircuitMutation(circuit);
      mutation.add(input);
      mutation.add(output);
      mutation.add(Wire.create(input.getLocation(), output.getLocation()));
      mutation.execute();
      project.setCurrentCircuit(circuit);
      history = new TestFrame.ModelHistory(selected -> new Model(project, selected),
          mock(ModelListener.class));
      model = history.select(circuit);
      listener = event -> {
        if (event.getAction() == ProjectEvent.ACTION_SET_STATE) {
          history.select(project.getCurrentCircuit());
        }
      };
      project.addProjectListener(listener);
      try {
        model.setVector(vector);
        final var state = project.getCircuitState().cloneAsNewRootState(Thread.currentThread());
        final var evaluator = new TestVectorEvaluator(state, vector);
        assertArrayEquals(new int[] {2, 2}, evaluator.evaluate((row, report) ->
            assertTrue(model.setResult(vector, row, report))));
        assertCompleted(model);
      } catch (Exception | AssertionError e) {
        close();
        throw new AssertionError(e);
      }
    }

    @Override
    public void close() {
      history.select(null);
      project.removeProjectListener(listener);
      project.getSimulator().shutDown();
    }
  }
}

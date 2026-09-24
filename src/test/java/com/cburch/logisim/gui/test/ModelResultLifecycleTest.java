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

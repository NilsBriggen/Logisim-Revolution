/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.main;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.circuit.WidthIncompatibilityData;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.gui.shell.ProblemsPill;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.tools.PokeTool;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;

/**
 * Keeps the problems pill in step with the circuit on screen, and carries out what it asks for.
 *
 * <p>Width errors were announced by painted text that could not be clicked and did not say where
 * they were. This finds them in a stable order, selects the wires of one and brings it into view,
 * steps to the next, and opens the help page that explains the wire colours.
 */
final class CircuitProblems {

  /** The help topic describing what each wire colour means, orange included. */
  static final String WIRE_COLORS_HELP = "features_colors";

  private final Project project;
  private final Frame frame;
  private final Canvas canvas;
  private final ProblemsPill pill;
  private final Listener listener = new Listener();
  private static final Comparator<Location> READING_ORDER =
      Comparator.comparingInt(Location::getY).thenComparingInt(Location::getX);

  private final AtomicBoolean refreshQueued = new AtomicBoolean();
  private List<WidthIncompatibilityData> problems = List.of();
  private int current = -1;

  CircuitProblems(Project project, Frame frame, Canvas canvas, ProblemsPill pill) {
    this.project = project;
    this.frame = frame;
    this.canvas = canvas;
    this.pill = pill;
    pill.setNavigator(this::step);
    pill.setLearnMoreAction(this::showHelp);
    pill.setResetAction(this::resetSimulation);
    project.addProjectListener(listener);
    project.addCircuitListener(listener);
    project.getSimulator().addSimulatorListener(listener);
    frame.addPropertyChangeListener(Frame.EDITOR_VIEW, event -> queueRefresh());
    queueRefresh();
  }

  /** Width errors in reading order, top to bottom and then left to right, so stepping is stable. */
  static List<WidthIncompatibilityData> ordered(Collection<WidthIncompatibilityData> found) {
    if (found == null || found.isEmpty()) return List.of();
    final var list = new ArrayList<>(found);
    list.sort(Comparator.comparing(CircuitProblems::anchor, READING_ORDER));
    return List.copyOf(list);
  }

  /** The first point of a problem in reading order, which is what the ordering goes by. */
  static Location anchor(WidthIncompatibilityData problem) {
    var best = problem.getPoint(0);
    for (var i = 1; i < problem.size(); i++) {
      final var point = problem.getPoint(i);
      if (READING_ORDER.compare(point, best) < 0) best = point;
    }
    return best;
  }

  /**
   * The problem to show after a step, wrapping around at both ends.
   *
   * @param current the one shown now, or -1 for none
   * @param delta -1, 0 or +1
   */
  static int stepIndex(int current, int count, int delta) {
    if (count <= 0) return -1;
    if (current < 0) return delta < 0 ? count - 1 : 0;
    return Math.floorMod(current + delta, count);
  }

  /** The wires of a problem's net, or failing that the components meeting at its points. */
  static Collection<Component> netOf(Circuit circuit, WidthIncompatibilityData problem) {
    final var result = new LinkedHashSet<Component>();
    for (var i = 0; i < problem.size(); i++) {
      for (final var wire : circuit.getWires(problem.getPoint(i))) {
        final var net = circuit.getWireSet(wire);
        for (final var candidate : circuit.getWires()) {
          if (net.containsWire(candidate)) result.add(candidate);
        }
      }
    }
    if (result.isEmpty()) {
      for (var i = 0; i < problem.size(); i++) {
        result.addAll(circuit.getNonWires(problem.getPoint(i)));
      }
    }
    return result;
  }

  private void queueRefresh() {
    // Simulation events arrive on the simulation thread, often; one pending refresh is enough.
    if (!refreshQueued.compareAndSet(false, true)) return;
    SwingUtilities.invokeLater(() -> {
      refreshQueued.set(false);
      refresh();
    });
  }

  /** Re-reads the problems. Runs on the event thread, where the connectivity map is built. */
  void refresh() {
    final var circuit = project.getCurrentCircuit();
    final var showing = circuit != null && Frame.EDIT_LAYOUT.equals(frame.getEditorView());
    final List<WidthIncompatibilityData> found =
        showing ? ordered(circuit.getWidthIncompatibilityData()) : List.of();
    if (!found.equals(problems)) {
      // A different set of problems makes the old position meaningless.
      current = -1;
      problems = found;
    }
    pill.setProblems(problems.size(), current, showing && project.getSimulator().isOscillating());
  }

  List<WidthIncompatibilityData> getProblems() {
    return problems;
  }

  /** Steps through the width problems, selecting the offending net and scrolling it into view. */
  void step(int delta) {
    refresh();
    if (problems.isEmpty()) return;
    current = stepIndex(current, problems.size(), delta);
    pill.setProblems(problems.size(), current, project.getSimulator().isOscillating());
    show(problems.get(current));
  }

  private void show(WidthIncompatibilityData problem) {
    final var circuit = project.getCurrentCircuit();
    if (circuit == null) return;
    final var net = netOf(circuit, problem);
    final var selection = project.getSelection();
    final var drop = SelectionActions.dropAll(selection);
    if (drop != null) project.doAction(drop);
    selection.addAll(net);
    // The Poke tool does not draw the selection; it highlights a net instead.
    if (project.getTool() instanceof PokeTool) {
      final var wire = net.stream().filter(Wire.class::isInstance).map(Wire.class::cast).findFirst();
      wire.ifPresent(w -> canvas.setHighlightedWires(circuit.getWireSet(w)));
    }
    canvas.revealBounds(boundsOf(problem));
    canvas.repaint();
  }

  /** Where the conflicting ends are, which is where the badges are drawn. */
  private static Bounds boundsOf(WidthIncompatibilityData problem) {
    var bounds = Bounds.EMPTY_BOUNDS;
    for (var i = 0; i < problem.size(); i++) bounds = bounds.add(problem.getPoint(i));
    return bounds.expand(20);
  }

  private void showHelp() {
    if (frame.getJMenuBar() instanceof LogisimMenuBar menuBar) {
      menuBar.showHelp(WIRE_COLORS_HELP);
    }
  }

  private void resetSimulation() {
    final var simulator = project.getSimulator();
    simulator.reset();
    // The oscillation switched automatic propagation off; resetting alone would leave it off.
    simulator.setAutoPropagation(true);
    project.repaintCanvas();
  }

  private final class Listener implements ProjectListener, CircuitListener, Simulator.Listener {
    @Override
    public void projectChanged(ProjectEvent event) {
      if (event.getAction() == ProjectEvent.ACTION_SET_CURRENT) current = -1;
      queueRefresh();
    }

    @Override
    public void circuitChanged(CircuitEvent event) {
      queueRefresh();
    }

    @Override
    public void propagationCompleted(Simulator.Event event) {
      queueRefresh();
    }

    @Override
    public void simulatorStateChanged(Simulator.Event event) {
      queueRefresh();
    }

    @Override
    public void simulatorReset(Simulator.Event event) {
      queueRefresh();
    }
  }
}

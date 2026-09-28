/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.analyze.model;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Undo and redo for the Combinational Analysis window.
 *
 * <p>The analyzer's edits (typing in the truth table, clicking a K-map cell, entering an
 * expression, changing the variables, importing a table or analysing a circuit) are not project
 * actions, so the analyzer had no undo at all and one stray click or a re-analysis silently threw
 * away a hand-made table. This keeps snapshots of the model's content instead of reversible
 * commands: every change the model reports is folded into one step per user action (the changes are
 * coalesced until the scheduler runs, by default at the end of the current Swing event), and undo
 * puts the previous snapshot back.
 *
 * <p>Snapshots copy the truth table, so history is kept only while the table is small enough; for
 * very large tables it is switched off (and emptied) rather than eat memory.
 */
public final class AnalyzerHistory {
  /** How many steps can be undone. */
  public static final int MAX_STEPS = 50;

  /** Tables with more output cells than this are not recorded. */
  static final long MAX_CELLS = 1L << 18;

  private record State(
      List<Var> inputs,
      List<Var> outputs,
      Entry[][] columns,
      boolean compact,
      Map<String, OutputExpressions.TypedExpression> typed,
      Map<String, Integer> formats) {

    boolean sameAs(State other) {
      if (other == null) return false;
      if (!inputs.equals(other.inputs) || !outputs.equals(other.outputs)) return false;
      if (compact != other.compact || !formats.equals(other.formats)) return false;
      if (!Arrays.deepEquals(columns, other.columns)) return false;
      if (!typed.keySet().equals(other.typed.keySet())) return false;
      for (final var entry : typed.entrySet()) {
        final var mine = entry.getValue();
        final var theirs = other.typed.get(entry.getKey());
        if (!String.valueOf(mine.text()).equals(String.valueOf(theirs.text()))) return false;
        if (!String.valueOf(mine.expression()).equals(String.valueOf(theirs.expression()))) {
          return false;
        }
      }
      return true;
    }
  }

  private final AnalyzerModel model;
  private final Consumer<Runnable> scheduler;
  private final Deque<State> undoStack = new ArrayDeque<>();
  private final Deque<State> redoStack = new ArrayDeque<>();
  private final List<Runnable> listeners = new ArrayList<>();
  private State current;
  private boolean pending;
  private boolean restoring;

  /**
   * Starts recording {@code model}.
   *
   * @param scheduler runs the commit of a step later; changes reported before it runs form one
   *     step. The analyzer passes {@code SwingUtilities::invokeLater}.
   */
  public AnalyzerHistory(AnalyzerModel model, Consumer<Runnable> scheduler) {
    this.model = model;
    this.scheduler = scheduler;
    current = capture();
    final var tracker = new Tracker();
    model.getTruthTable().addTruthTableListener(tracker);
    model.getInputs().addVariableListListener(tracker);
    model.getOutputs().addVariableListListener(tracker);
    model.getOutputExpressions().addOutputExpressionsListener(tracker);
  }

  /** Called (on the scheduler's thread) whenever what can be undone or redone changes. */
  public void addListener(Runnable listener) {
    listeners.add(listener);
  }

  public boolean canUndo() {
    return !undoStack.isEmpty();
  }

  public boolean canRedo() {
    return !redoStack.isEmpty();
  }

  /** Puts back the content before the last step. */
  public void undo() {
    commit();
    if (undoStack.isEmpty()) return;
    final var target = undoStack.pop();
    if (current != null) redoStack.push(current);
    restore(target);
  }

  /** Puts back the step that was last undone. */
  public void redo() {
    commit();
    if (redoStack.isEmpty()) return;
    final var target = redoStack.pop();
    if (current != null) undoStack.push(current);
    restore(target);
  }

  private void changed() {
    if (restoring || pending) return;
    pending = true;
    scheduler.accept(this::commit);
  }

  /** Turns the changes since the last step into a step of their own. */
  void commit() {
    if (!pending) return;
    pending = false;
    final var after = capture();
    if (after == null) {
      // Too large to record: forget the history rather than keep one that no longer matches.
      undoStack.clear();
      redoStack.clear();
      current = null;
    } else if (!after.sameAs(current)) {
      if (current != null) {
        undoStack.push(current);
        while (undoStack.size() > MAX_STEPS) undoStack.removeLast();
      }
      redoStack.clear();
      current = after;
    }
    fireChanged();
  }

  private State capture() {
    final var table = model.getTruthTable();
    final var outputCount = table.getOutputColumnCount();
    if ((long) table.getRowCount() * Math.max(1, outputCount) > MAX_CELLS) return null;
    final var columns = new Entry[outputCount][];
    for (var col = 0; col < outputCount; col++) columns[col] = table.getOutputColumn(col).clone();
    final var expressions = model.getOutputExpressions();
    return new State(
        List.copyOf(model.getInputs().vars),
        List.copyOf(model.getOutputs().vars),
        columns,
        table.getVisibleRowCount() != table.getRowCount(),
        Map.copyOf(expressions.getTypedExpressions()),
        Map.copyOf(expressions.getMinimizedFormats()));
  }

  private void restore(State state) {
    restoring = true;
    try {
      final var table = model.getTruthTable();
      if (!model.getInputs().vars.equals(state.inputs())
          || !model.getOutputs().vars.equals(state.outputs())) {
        model.setVariables(state.inputs(), state.outputs());
      }
      for (var col = 0; col < state.columns().length; col++) {
        if (!Arrays.equals(table.getOutputColumn(col), state.columns()[col])) {
          table.setOutputColumn(col, state.columns()[col].clone());
        }
      }
      final var expressions = model.getOutputExpressions();
      for (final var entry : state.formats().entrySet()) {
        expressions.setMinimizedFormat(entry.getKey(), entry.getValue());
      }
      for (final var entry : state.typed().entrySet()) {
        final var typed = entry.getValue();
        expressions.setExpression(entry.getKey(), typed.expression(), typed.text());
      }
      if (state.compact()) table.compactVisibleRows();
      else table.expandVisibleRows();
    } finally {
      restoring = false;
    }
    current = capture();
    fireChanged();
  }

  private void fireChanged() {
    for (final var listener : List.copyOf(listeners)) listener.run();
  }

  private class Tracker
      implements TruthTableListener, VariableListListener, OutputExpressionsListener {
    @Override
    public void rowsChanged(TruthTableEvent event) {
      changed();
    }

    @Override
    public void cellsChanged(TruthTableEvent event) {
      changed();
    }

    @Override
    public void structureChanged(TruthTableEvent event) {
      changed();
    }

    @Override
    public void listChanged(VariableListEvent event) {
      changed();
    }

    @Override
    public void expressionChanged(OutputExpressionsEvent event) {
      changed();
    }
  }
}

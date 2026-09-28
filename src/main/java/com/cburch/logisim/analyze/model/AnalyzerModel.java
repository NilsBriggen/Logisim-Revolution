/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.analyze.model;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.proj.Project;
import java.util.List;

public class AnalyzerModel {
  public static final int MAX_INPUTS = 20;
  public static final int MAX_OUTPUTS = 256;

  public static final int FORMAT_SUM_OF_PRODUCTS = 0;
  public static final int FORMAT_PRODUCT_OF_SUMS = 1;

  private final VariableList inputs = new VariableList(MAX_INPUTS);
  private final VariableList outputs = new VariableList(MAX_OUTPUTS);
  private final TruthTable table;
  private final OutputExpressions outputExpressions;
  private Project currentProject = null;
  private Circuit currentCircuit = null;
  // Whether the table or the variables were changed by hand since they were last replaced
  // wholesale (by analysing a circuit or importing a file); nothing can undo such a replacement.
  private boolean edited = false;
  private int replacing = 0;
  private boolean changedWhileReplacing = false;

  public AnalyzerModel() {
    // the order here is important, because the output expressions
    // need the truth table to exist for listening.
    table = new TruthTable(this);
    outputExpressions = new OutputExpressions(this);
    // Expression edits reach the truth table too, so these listeners see every change of content.
    final var editTracker = new EditTracker();
    table.addTruthTableListener(editTracker);
    inputs.addVariableListListener(editTracker);
    outputs.addVariableListListener(editTracker);
  }

  private class EditTracker implements TruthTableListener, VariableListListener {
    @Override
    public void rowsChanged(TruthTableEvent event) {
      contentChanged();
    }

    @Override
    public void cellsChanged(TruthTableEvent event) {
      contentChanged();
    }

    @Override
    public void structureChanged(TruthTableEvent event) {
      contentChanged();
    }

    @Override
    public void listChanged(VariableListEvent event) {
      contentChanged();
    }
  }

  private void contentChanged() {
    if (replacing > 0) {
      changedWhileReplacing = true;
    } else {
      edited = true;
    }
  }

  /** True if the user changed the table, expressions or variables since the last replacement. */
  public boolean isEdited() {
    return edited;
  }

  /**
   * Brackets a wholesale replacement of the contents, such as analysing a circuit or importing a
   * truth table. Changes made in between do not count as hand edits; if anything changed, the model
   * counts as unedited afterwards. Calls must be paired with {@link #endReplacement()}.
   */
  public void beginReplacement() {
    if (replacing++ == 0) changedWhileReplacing = false;
  }

  public void endReplacement() {
    if (--replacing == 0 && changedWhileReplacing) edited = false;
  }

  public Circuit getCurrentCircuit() {
    return currentCircuit;
  }

  //
  // access methods
  //
  public Project getCurrentProject() {
    return currentProject;
  }

  public VariableList getInputs() {
    return inputs;
  }

  public OutputExpressions getOutputExpressions() {
    return outputExpressions;
  }

  public VariableList getOutputs() {
    return outputs;
  }

  public TruthTable getTruthTable() {
    return table;
  }

  //
  // modifier methods
  //
  public void setCurrentCircuit(Project value, Circuit circuit) {
    currentProject = value;
    currentCircuit = circuit;
  }

  public void setVariables(List<Var> inputs, List<Var> outputs) {
    this.inputs.setAll(inputs);
    this.outputs.setAll(outputs);
  }
}

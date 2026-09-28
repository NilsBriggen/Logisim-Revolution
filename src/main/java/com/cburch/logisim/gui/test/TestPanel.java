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

import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.circuit.TestVectorEvaluator;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.TestException;
import com.cburch.logisim.data.TestVector;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.log.ValueTable;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Clock;
import com.cburch.logisim.std.wiring.Pin;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestPanel extends JPanel implements ValueTable.Model, Simulator.Listener {

  /**
   * The three status colours, read per call so a change of theme is picked up.
   *
   * <p>They used to be three pastels chosen by colour name — pink for a failure, mint for the
   * running test, lavender for an idle one — which said nothing on their own and were illegible
   * against a dark window.
   */
  static Color failColor() {
    final var error = Tokens.error();
    return new Color(error.getRed(), error.getGreen(), error.getBlue(), 56);
  }

  static Color activeButtonColor() {
    return Tokens.success();
  }

  static Color inactiveButtonColor() {
    return Tokens.badgeBackground();
  }
  private static final long serialVersionUID = 1L;
  private static final Logger logger = LoggerFactory.getLogger(TestPanel.class);

  private final TestFrame testFrame;
  private final ValueTable table;
  private final MyListener myListener = new MyListener();
  ComponentAdapter componentAdapter = null;
  private boolean simulatorListening = false;

  // Track which rows' Show buttons are currently active (green)
  private final Set<Integer> activeShowRows = new HashSet<>();
  // Track which rows' Set buttons are currently active (green)
  private final Set<Integer> activeSetRows = new HashSet<>();
  private final String [] specialHeaders = {"", "", S.get("statusHeader"), "<set>", "<seq>"};

  /** Pin values right after Show or Set, and the evaluator whose pins they belong to. */
  private record ShownValues(TestVectorEvaluator evaluator, Value[] values) {}

  // Written by the simulation thread (after Show/Set) and the EDT (reset), read by the simulation
  // thread in propagationCompleted; one reference, so both parts are always seen together.
  private final AtomicReference<ShownValues> shownValues = new AtomicReference<>();

  // Pin columns that have shown a failure for the current vector; they are widened once so that
  // "expected -> computed" fits.
  private final BitSet failedColumns = new BitSet();
  static final String EXPECTED_COMPUTED_SEPARATOR = " \u2192 ";

  public TestPanel(TestFrame frame) {
    this.testFrame = frame;
    table = new ValueTable(getModel() == null ? null : this);
    setLayout(new BorderLayout());
    add(table);
    modelChanged(null, getModel());
    setComponentAdapter();
  }

  @Override
  public String specialColumnEntry(int i) {
    if (i < specialHeaders.length) return null; // special columns are handled separately.
    TestVector vec = getModel().getVector();
    final var pinIndex = i - specialHeaders.length;
    final var special = vec.specialColumnEntry(pinIndex);
    if (!failedColumns.get(pinIndex)) return special;
    // Reserve room for the widest "expected -> computed" pair in this column.
    final var radix = getColumnValueRadix(i);
    final var width = vec.columnWidth[pinIndex];
    final var widest =
        Value.createKnown(
                width,
                radix == 2 ? 0 : (radix == 10 ? (1L << (width.getWidth() - 1)) : width.getMask()))
            .toDisplayString(radix);
    final var expected = special != null && special.length() > widest.length() ? special : widest;
    return expected + EXPECTED_COMPUTED_SEPARATOR + widest;
  }

  @Override
  public void changeColumnValueRadix(int i) {
    if (i < specialHeaders.length) return; // special columns have no radix.
    TestVector vec = getModel().getVector();
    // Regular pin columns
    int pinIndex = i - specialHeaders.length;
    switch (vec.columnRadix[pinIndex]) {
      case 2 -> vec.columnRadix[pinIndex] = 10;
      case 10 -> vec.columnRadix[pinIndex] = 16;
      default -> vec.columnRadix[pinIndex] = 2;
    }
    table.modelChanged();
  }

  @Override
  public int getColumnCount() {
    TestVector vec = getModel().getVector();
    return vec == null ? 0 : vec.columnName.length + specialHeaders.length;
  }

  @Override
  public String getColumnName(int i) {
    // The two action columns get a visible name; they used to be blank, explained only on hover.
    if (i == 0) return S.get("testShowHeader");
    if (i == 1) return S.get("testSetHeader");
    if (i == 2) return S.get("statusHeader");
    if (i < specialHeaders.length) return specialHeaders[i];
    TestVector vec = getModel().getVector();
    return vec.columnName[i - specialHeaders.length];
  }

  @Override
  public String getColumnToolTip(int i) {
    return switch (i) {
      case 0 -> S.get("toolTipShow");
      case 1 -> S.get("toolTipSet");
      case 3 -> S.get("testSetColumnTip");
      case 4 -> S.get("testSeqColumnTip");
      default -> null;
    };
  }

  @Override
  public int getColumnValueRadix(int i) {
    if (i < 3) return 0; // first three have no radix.
    if (i < specialHeaders.length) return 10; // <set> and <seq> are displayed in decimal.
    TestVector vec = getModel().getVector();
    return vec.columnRadix[i - specialHeaders.length];
  }

  // ValueTable.Model implementation

  @Override
  public BitWidth getColumnValueWidth(int i) {
    if (i < specialHeaders.length) return null;
    TestVector vec = getModel().getVector();
    return vec.columnWidth[i - specialHeaders.length];
  }

  Model getModel() {
    return testFrame.getModel();
  }

  @Override
  public int getRowCount() {
    TestVector vec = getModel().getVector();
    return vec == null ? 0 : vec.data.size();
  }

  @Override
  public void getRowData(int firstRow, int numRows, ValueTable.Cell[][] rowData) {
    Model model = getModel();
    ArrayList<TestVectorEvaluator.LineReport>[] results = model.getResults();
    var numPass = model.getPass();
    var numFail = model.getFail();
    final var vec = model.getVector();
    int columns = vec.columnName.length;
    final var msg = new String[columns];
    final var altdata = new Value[columns];
    final var passMsg = S.get("passStatus");
    final var failMsg = S.get("failStatus");
    var widen = false;

    final int pinColumnStart = specialHeaders.length;

    for (var outRow = 0; outRow < numRows; outRow++) {
      final var row = model.sortedIndex(outRow + firstRow);
      final var data = vec.data.get(row);
      String rowmsg = null;
      String status = null;
      var failed = false;
      if (row < numPass + numFail) {
        final var err = results[row];
        if (err != null) { // err instanceof FailException failEx) {
          failed = true;
          for (final var e : err) {
            var col = e.column();
            msg[col] = S.get("expectedValueMessage", e.expected().toDisplayString(getColumnValueRadix(pinColumnStart + col)));
            altdata[col] = e.computed();
          }
        }
        status = failed ? failMsg : passMsg;
      }

      // Show button column (column 0)
      Color showButtonBg = activeShowRows.contains(row) ? activeButtonColor() : inactiveButtonColor();
      rowData[outRow][0] = new ValueTable.Cell(S.get("testShowButton"), showButtonBg, null, S.get("toolTipShow"));

      // Set button column (column 1)
      Color setButtonBg = activeSetRows.contains(row) ? activeButtonColor() : inactiveButtonColor();
      rowData[outRow][1] = new ValueTable.Cell(S.get("testSetButton"), setButtonBg, null, S.get("toolTipSet"));

      // Status column (column 2)
      rowData[outRow][2] = new ValueTable.Cell(status, rowmsg != null ? failColor() : null, null, rowmsg);

      // <set> column (column 3)
      int setValue = vec.setNumbers[row];
      rowData[outRow][3] =
          new ValueTable.Cell(
              Integer.toString(setValue), null, null, S.get("testSetCellTip", setValue));

      // <seq> column (column 4)
      int seqValue = vec.seqNumbers[row];
      String seqText = seqValue == 0 ? "comb" : Integer.toString(seqValue);
      String seqTooltip = seqValue == 0 ? S.get("toolTipCombinational") : S.get("toolTipSequential", "" + seqValue);
      rowData[outRow][4] = new ValueTable.Cell(seqText, null, null, seqTooltip);

      // Pin columns
      for (var col = 0; col < columns; col++) {
        int colIndex = col + specialHeaders.length;
        String tooltip = msg[col];
        String displayText;
        Color bgColor = msg[col] != null ? failColor() : null;

        // Check for special values first
        if (vec.isDontCare(row, col)) {
          displayText = "<DC>";
          tooltip = (tooltip != null ? tooltip + " | " : "") + S.get("toolTipDontCare", displayText);
        } else {
          final var floating = vec.isFloating(row, col);
          if (floating) {
            tooltip = (tooltip != null ? tooltip + " | " : "") + S.get("toolTipFloating", "<float>");
          }
          displayText = cellText(data[col], floating, altdata[col],
              getColumnValueRadix(pinColumnStart + col));
          if (altdata[col] != null) {
            if (!failedColumns.get(col)) {
              failedColumns.set(col);
              widen = true;
            }
          }
        }

        rowData[outRow][colIndex] = new ValueTable.Cell(displayText, bgColor, null, tooltip);
        msg[col] = null;
        altdata[col] = null;
      }
    }
    if (widen) {
      // Column widths are computed from specialColumnEntry; recompute them outside this paint.
      SwingUtilities.invokeLater(table::modelChanged);
    }
  }

  /**
   * Text of a pin cell. It always starts with the expected value from the file; a failure appends
   * the computed value ("1 → 0"), so a cell never switches meaning when results arrive or are
   * reset.
   */
  static String cellText(Value expected, boolean floating, Value computed, int radix) {
    final var text = floating ? "<float>" : expected.toDisplayString(radix);
    return computed == null
        ? text
        : text + EXPECTED_COMPUTED_SEPARATOR + computed.toDisplayString(radix);
  }

  public void localeChanged() {
    table.modelChanged();
  }

  public void modelChanged(Model oldModel, Model newModel) {
    if (oldModel != null) {
      oldModel.removeModelListener(myListener);
      // Remove simulator listener from old project
      if (oldModel.getProject() != null) {
        oldModel.getProject().getSimulator().removeSimulatorListener(this);
        simulatorListening = false;
      }
    }
    if (newModel != null) {
      newModel.addModelListener(myListener);
      // Add simulator listener to new project
      if (newModel.getProject() != null && !simulatorListening && testFrame.isShowing()) {
        newModel.getProject().getSimulator().addSimulatorListener(this);
        simulatorListening = true;
      }
    }
    // Reset active rows when model changes
    resetActiveRows();
    failedColumns.clear();
    table.setModel(newModel == null ? null : this);
  }

  private void setComponentAdapter() {
    if (getModel() != null && componentAdapter == null) {
      componentAdapter = new ComponentAdapter() {
        @Override
        public void componentShown(ComponentEvent e) {
          if (getModel() != null && !simulatorListening) {
            getModel().getProject().getSimulator().addSimulatorListener(TestPanel.this);
            simulatorListening = true;
          }
        }

        @Override
        public void componentHidden(ComponentEvent e) {
          if (getModel() != null) {
            getModel().getProject().getSimulator().removeSimulatorListener(TestPanel.this);
            simulatorListening = false;
          }
        }
      };
      testFrame.addComponentListener(componentAdapter);
    }
  }

  private void resetActiveRows() {
    activeShowRows.clear();
    activeSetRows.clear();
    shownValues.set(null);
  }


  @Override
  public boolean isButtonColumn(int col) {
    return col == 0 || col == 1; // Show button (0) and Set button (1) are button columns
  }

  @Override
  public void handleButtonClick(int displayRow, int col, int modifiersEx) {
    Model model = getModel();
    if (model == null) return;
    if (model.getProject().getSimulator().isAutoTicking()) {
      OptionPane.showMessageDialog(this,
          S.get("testButtonWhileTickingMessage"), S.get("testButtonWhileTickingTitle"),
          OptionPane.ERROR_MESSAGE);
      return;
    }
    TestVector vec = model.getVector();
    if (vec == null) return;

    // Convert display row to file row index
    int fileRow = model.sortedIndex(displayRow);

    // Get sequence number for this row
    int seqValue = (vec.seqNumbers != null && fileRow < vec.seqNumbers.length)
        ? vec.seqNumbers[fileRow] : 0;

    // Check if menu shortcut key (Ctrl/Cmd) is pressed - if so, don't reset
    int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
    boolean resetFirst = (modifiersEx & menuMask) == 0;

    try {
      if (col == 0) {
        // Show button clicked
        executeShowButton(fileRow, seqValue, resetFirst);
      } else if (col == 1) {
        // Set button clicked - execute the test
        executeGoButton(fileRow, seqValue, resetFirst);
      }
    } catch (TestException e) {
      // Show error dialog
      OptionPane.showMessageDialog(
          this,
          e.getMessage(), S.get("testExecutionErrorTitle"),
          OptionPane.ERROR_MESSAGE);
    }
  }

  private void executeShowButton(int targetFileRow, int targetSeq, boolean resetFirst) throws TestException {
    Model model = getModel();
    TestVector vec = model.getVector();
    Project project = model.getProject();
    CircuitState state = project.getCircuitState();

    if (vec.setNumbers == null || targetFileRow >= vec.setNumbers.length) {
      return;
    }
    final var stepsToExecute = new ArrayList<Integer>();

    // Handle combinational tests (targetSeq == 0)
    if (targetSeq == 0) {
      stepsToExecute.add(targetFileRow);
    } else {
      // Sequential test - Get the target set number
      int targetSet = vec.setNumbers[targetFileRow];

      // Find all rows in the same set with sequence numbers from 1 to targetSeq (inclusive)
      for (int row = 0; row < vec.data.size(); row++) {
        int rowSet = vec.setNumbers[row];
        int rowSeq = vec.seqNumbers[row];

        // Include rows with same set and sequence from 1 to targetSeq
        if (rowSet == targetSet && rowSeq > 0 && rowSeq <= targetSeq) {
          stepsToExecute.add(row);
        }
      }
    }

    // Sort by sequence number to execute in order
    stepsToExecute.sort((a, b) -> {
      int seqA = vec.seqNumbers[a];
      int seqB = vec.seqNumbers[b];
      return Integer.compare(seqA, seqB);
    });
    // Mark all executed steps as active for Show button (will show green buttons)
    activeShowRows.clear();
    activeShowRows.addAll(stepsToExecute);
    // Update Set button highlight to match current state (the last step shown)
    activeSetRows.clear();
    if (!stepsToExecute.isEmpty()) {
      activeSetRows.add(stepsToExecute.get(stepsToExecute.size() - 1));
    }

    final var sim = project.getSimulator();
    TestVectorEvaluator evaluator = new TestVectorEvaluator(state, vec, stepsToExecute, tve -> {
      setStoredPinValues(state, tve);
      SwingUtilities.invokeLater(this::finishShowTestVector);
    });
    evaluator.setPropagateOnLast(sim.isAutoPropagating());
    sim.showTestVector(evaluator);
  }

  private void setStoredPinValues(CircuitState state, TestVectorEvaluator evaluator) {
    // Store the pin values after all steps are executed
    Instance[] pins = evaluator.getPins();
    final var storedPinValues = new Value[pins.length];
    for (int j = 0; j < pins.length; j++) {
      final var isPin = pins[j].getFactory() instanceof Pin;
      InstanceState instanceState = state.getInstanceState(pins[j]);
      storedPinValues[j] = isPin ? Pin.FACTORY.getValue(instanceState) : Clock.FACTORY.getValue(instanceState);
    }
    shownValues.set(new ShownValues(evaluator, storedPinValues));
  }

  private void finishShowTestVector() {
    // Refresh the table to show the green button
    table.dataChanged();
    table.repaint();
  }

  private void executeGoButton(int targetFileRow, int targetSeq, boolean resetFirst) throws TestException {
    Model model = getModel();
    TestVector vec = model.getVector();
    Project project = model.getProject();
    CircuitState state = project.getCircuitState();

    final var stepsToExecute = new ArrayList<Integer>();
    stepsToExecute.add(targetFileRow);
    final var simReset = targetSeq == 0 ? resetFirst : false;
    activeShowRows.clear(); // Clear Show button highlights
    activeSetRows.clear();
    activeSetRows.add(targetFileRow);

    final var sim = project.getSimulator();
    TestVectorEvaluator evaluator = new TestVectorEvaluator(state, vec, stepsToExecute, tve -> {
      setStoredPinValues(state, tve);
      SwingUtilities.invokeLater(this::finishShowTestVector);
    });
    evaluator.setAllowReset(simReset);
    evaluator.setPropagateOnLast(sim.isAutoPropagating());
    sim.showTestVector(evaluator);
  }

  @Override
  public void propagationCompleted(Simulator.Event e) {
    // Runs on the simulation thread: read the shared state once, never field by field.
    Model model = getModel();
    final var shown = shownValues.get();
    if (model == null || shown == null) return;
    final var storedPinValues = shown.values();
    CircuitState state = model.getProject().getCircuitState();
    Instance[] pins = shown.evaluator().getPins();

    // Check if any pin values have changed
    for (int j = 0; j < pins.length; j++) {
      final var isPin = pins[j].getFactory() instanceof Pin;
      Value currentValue = null;
      try {
        InstanceState instanceState = state.getInstanceState(pins[j]);
        currentValue = isPin ? Pin.FACTORY.getValue(instanceState) : Clock.FACTORY.getValue(instanceState);
      } catch (Exception ex) {
        // state has been modified. Leave currentValue as null.
      }
      if (storedPinValues[j] == null || currentValue == null || !currentValue.equals(storedPinValues[j])) {
        // Only the thread that clears this snapshot resets the highlights.
        if (shownValues.compareAndSet(shown, null)) resetActiveRowsAndNotifyTableChanged();
        break;
      }
    }
  }

  @Override
  public void simulatorReset(com.cburch.logisim.circuit.Simulator.Event e) {
    resetActiveRowsAndNotifyTableChanged();
  }

  @Override
  public void simulatorStateChanged(com.cburch.logisim.circuit.Simulator.Event e) {
    resetActiveRowsAndNotifyTableChanged();
  }

  private void resetActiveRowsAndNotifyTableChanged() {
    SwingUtilities.invokeLater(() -> {
      resetActiveRows();
      table.dataChanged();
    });
  }

  private class MyListener implements ModelListener {

    @Override
    public void testingChanged() {}

    @Override
    public void testResultsChanged(int numPass, int numFail) {
      table.dataChanged();
    }

    @Override
    public void vectorChanged() {
      resetActiveRows();
      failedColumns.clear();
      table.modelChanged();
    }
  }
}

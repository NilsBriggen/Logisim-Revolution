/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.analyze.gui;

import static com.cburch.logisim.analyze.Strings.S;

import com.cburch.logisim.analyze.file.AnalyzerTexWriter;
import com.cburch.logisim.analyze.model.AnalyzerModel;
import com.cburch.logisim.analyze.model.Implicant;
import com.cburch.logisim.analyze.model.TruthTableEvent;
import com.cburch.logisim.analyze.model.TruthTableListener;
import com.cburch.logisim.gui.generic.LFrame;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.LocaleListener;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.Spacing;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingWorker;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class Analyzer extends LFrame.SubWindow {
  private final AnalyzerMenuListener menuListener;

  private class MyChangeListener implements ChangeListener {
    @Override
    public void stateChanged(ChangeEvent e) {
      Object selected = tabbedPane.getSelectedComponent();
      if (selected instanceof JScrollPane selScrollPane) {
        selected = selScrollPane.getViewport().getView();
      }
      if (selected instanceof JPanel selPanel) {
        selPanel.requestFocus();
      }
      if (selected instanceof AnalyzerTab tab) {
        menuListener.setEditHandler(tab.getEditHandler());
        menuListener.setPrintHandler(tab.getPrintHandler());
        model.getOutputExpressions().enableUpdates();
        tab.updateTab();
      } else {
        model.getOutputExpressions().disableUpdates();
      }
    }
  }

  private class MyLocaleListener implements LocaleListener {
    @Override
    public void localeChanged() {
      Analyzer.this.setTitle(S.get("analyzerWindowTitle"));
      tabbedPane.setTitleAt(IO_TAB, S.get("inputsOutputsTab"));
      tabbedPane.setTitleAt(TABLE_TAB, S.get("tableTab"));
      tabbedPane.setTitleAt(EXPRESSION_TAB, S.get("expressionTab"));
      tabbedPane.setTitleAt(MINIMIZED_TAB, S.get("minimizedTab"));
      tabbedPane.setToolTipTextAt(IO_TAB, S.get("inputsOutputsTabTip"));
      tabbedPane.setToolTipTextAt(TABLE_TAB, S.get("tableTabTip"));
      tabbedPane.setToolTipTextAt(EXPRESSION_TAB, S.get("expressionTabTip"));
      tabbedPane.setToolTipTextAt(MINIMIZED_TAB, S.get("minimizedTabTip"));
      importTable.setText(S.get("importTableButton"));
      buildCircuit.setText(S.get("buildCircuitButton"));
      exportTable.setText(S.get("exportTableButton"));
      exportTex.setText(S.get("exportLatexButton"));
      minimizeMinterms.setText(S.get("minimizeMintermsButton"));
      minimizeMaxterms.setText(S.get("minimizeMaxtermsButton"));
      ioPanel.localeChanged();
      truthTablePanel.localeChanged();
      expressionPanel.localeChanged();
      minimizedPanel.localeChanged();
      importTable.localeChanged();
      buildCircuit.localeChanged();
      exportTable.localeChanged();
      exportTex.localeChanged();
      resizeForLocaleChange();
    }
  }

  private class TableListener implements TruthTableListener {
    @Override
    public void rowsChanged(TruthTableEvent event) {
      update();
    }

    @Override
    public void cellsChanged(TruthTableEvent event) {
      // dummy
    }

    @Override
    public void structureChanged(TruthTableEvent event) {
      update();
    }

    private void update() {
      final var tt = model.getTruthTable();
      final var nrOfInputs = tt.getInputColumnCount();
      final var nrOfOutputs = tt.getOutputColumnCount();
      final var hasInputsAndOutputs = (nrOfInputs > 0) && (nrOfOutputs > 0);
      buildCircuit.setEnabled(hasInputsAndOutputs);
      minimizeMinterms.setEnabled(hasInputsAndOutputs
              && (nrOfInputs > Implicant.MAXIMAL_NR_OF_INPUTS_FOR_AUTO_MINIMAL_FORM));
      minimizeMaxterms.setEnabled(hasInputsAndOutputs
              && (nrOfInputs > Implicant.MAXIMAL_NR_OF_INPUTS_FOR_AUTO_MINIMAL_FORM));
      exportTable.setEnabled(hasInputsAndOutputs);
      exportTex.setEnabled(hasInputsAndOutputs
              && tt.getRowCount() <= AnalyzerTexWriter.MAX_TRUTH_TABLE_ROWS);
      tabbedPane.setEnabledAt(TABLE_TAB, hasInputsAndOutputs);
      tabbedPane.setEnabledAt(EXPRESSION_TAB, hasInputsAndOutputs
              && (nrOfInputs <= Implicant.MAXIMAL_NR_OF_INPUTS_FOR_AUTO_MINIMAL_FORM));
      tabbedPane.setEnabledAt(MINIMIZED_TAB, hasInputsAndOutputs
              && (nrOfInputs <= Implicant.MAXIMAL_NR_OF_INPUTS_FOR_AUTO_MINIMAL_FORM));
      ioPanel.updateTab();
    }
  }

  private static final long serialVersionUID = 1L;
  // used by circuit analysis to select the relevant tab automatically.
  public static final int IO_TAB = 0;
  public static final int TABLE_TAB = 1;
  public static final int EXPRESSION_TAB = 2;
  public static final int MINIMIZED_TAB = 3;

  private final AnalyzerModel model = new AnalyzerModel();

  private JTabbedPane tabbedPane = new JTabbedPane();
  private final VariableTab ioPanel;
  private final TableTab truthTablePanel;
  private final ExpressionTab expressionPanel;
  private final MinimizedTab minimizedPanel;

  private final BuildCircuitButton buildCircuit;
  private final ImportTableButton importTable;
  private final ExportTableButton exportTable;
  private final ExportLatexButton exportTex;
  private final MinimizeButton minimizeMinterms;
  private final MinimizeButton minimizeMaxterms;

  Analyzer() {
    super(null);
    final var tableListener = new TableListener();
    model.getTruthTable().addTruthTableListener(tableListener);
    menuListener = new AnalyzerMenuListener(menubar);
    ioPanel = new VariableTab(model.getInputs(), model.getOutputs(), menubar);
    truthTablePanel = new TableTab(model.getTruthTable());
    expressionPanel = new ExpressionTab(model, menubar);
    minimizedPanel = new MinimizedTab(model, menubar);
    importTable = new ImportTableButton(this, model);
    buildCircuit = new BuildCircuitButton(this, model);
    buildCircuit.setEnabled(false);
    exportTable = new ExportTableButton(this, model);
    exportTable.setEnabled(false);
    exportTex = new ExportLatexButton(this, model);
    exportTex.setEnabled(false);
    minimizeMinterms = new MinimizeButton(this, model, AnalyzerModel.FORMAT_SUM_OF_PRODUCTS);
    minimizeMinterms.setEnabled(false);
    minimizeMaxterms = new MinimizeButton(this, model, AnalyzerModel.FORMAT_PRODUCT_OF_SUMS);
    minimizeMaxterms.setEnabled(false);

    tabbedPane = new JTabbedPane();
    addTab(IO_TAB, ioPanel);
    addTab(TABLE_TAB, truthTablePanel);
    addTab(EXPRESSION_TAB, expressionPanel);
    addTab(MINIMIZED_TAB, minimizedPanel);
    tabbedPane.setEnabledAt(MINIMIZED_TAB, false);
    tabbedPane.setEnabledAt(EXPRESSION_TAB, false);
    tabbedPane.setEnabledAt(TABLE_TAB, false);

    final var contents = getContentPane();

    // The window used to be sized by two invisible panels wedged into WEST and NORTH. A minimum
    // size says the same thing and does not appear in the layout.
    setMinimumSize(
        new Dimension(AppPreferences.getScaled(450), AppPreferences.getScaled(300)));

    // The six actions were a centred FlowLayout strip that re-wrapped onto a second row when the
    // window narrowed. They now sit at the trailing edge, grouped: the two that change the table,
    // then the one that builds a circuit, then the two that export.
    final var buttonPanel = new JPanel();
    buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.X_AXIS));
    buttonPanel.setBorder(Spacing.panelBorder());
    buttonPanel.add(Box.createHorizontalGlue());
    buttonPanel.add(importTable);
    buttonPanel.add(Box.createHorizontalStrut(Spacing.sm()));
    buttonPanel.add(minimizeMinterms);
    buttonPanel.add(Box.createHorizontalStrut(Spacing.sm()));
    buttonPanel.add(minimizeMaxterms);
    buttonPanel.add(Box.createHorizontalStrut(Spacing.lg()));
    buttonPanel.add(exportTable);
    buttonPanel.add(Box.createHorizontalStrut(Spacing.sm()));
    buttonPanel.add(exportTex);
    buttonPanel.add(Box.createHorizontalStrut(Spacing.lg()));
    // Building the circuit is what the window is for, and the only one that changes the project.
    buildCircuit.putClientProperty("JButton.buttonType", "default");
    buttonPanel.add(buildCircuit);

    contents.add(tabbedPane, BorderLayout.CENTER);
    contents.add(buttonPanel, BorderLayout.SOUTH);
    getRootPane().setDefaultButton(buildCircuit);

    final var myLocaleListener = new MyLocaleListener();
    LocaleManager.addLocaleListener(myLocaleListener);
    myLocaleListener.localeChanged();
    final var myChangeListener = new MyChangeListener();
    tabbedPane.addChangeListener(myChangeListener);
    setSelectedTab(0);
    myChangeListener.stateChanged(null);
  }

  private void addTab(int index, final JComponent comp) {
    if (comp instanceof TableTab || comp instanceof VariableTab || comp instanceof ExpressionTab) {
      tabbedPane.insertTab(S.get("untitled"), null, comp, null, index);
      return;
    }
    final var pane = new JScrollPane(comp,
        ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
        ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    pane.addComponentListener(new ComponentAdapter() {
      @Override
      public void componentResized(ComponentEvent event) {
        final var width = pane.getViewport().getWidth();
        comp.setSize(new Dimension(width, comp.getHeight()));
      }
    });
    tabbedPane.insertTab(S.get("untitled"), null, pane, null, index);
  }

  public AnalyzerModel getModel() {
    return model;
  }

  public void setSelectedTab(int index) {
    if (tabbedPane.getComponentAt(index) instanceof AnalyzerTab found) {
      model.getOutputExpressions().enableUpdates();
      found.updateTab();
    } else {
      model.getOutputExpressions().disableUpdates();
    }
    tabbedPane.setSelectedIndex(index);
  }

  private void resizeForLocaleChange() {
    getContentPane().invalidate();
    invalidate();

    final var current = getSize();
    if (current.width <= 0 || current.height <= 0) return;

    final var expanded = expandedSizeForLocaleChange(current, getPreferredSize());
    if (!expanded.equals(current)) {
      setSize(expanded);
    }
    validate();
    repaint();
  }

  static Dimension expandedSizeForLocaleChange(Dimension current, Dimension preferred) {
    return new Dimension(
        Math.max(current.width, preferred.width),
        Math.max(current.height, preferred.height));
  }

  public abstract static class PleaseWait<T> extends JDialog {
    private static final long serialVersionUID = 1L;

    private final SwingWorker<T, Void> worker;
    private final java.awt.Component parentComponent;

    public abstract T doInBackground();

    private boolean alreadyFinished = false;

    public PleaseWait(String title, java.awt.Component parentComponent) {
      super(null, title, ModalityType.APPLICATION_MODAL);
      this.parentComponent = parentComponent;
      worker =
          new SwingWorker<>() {
            @Override
            protected T doInBackground() {
              return PleaseWait.this.doInBackground();
            }

            @Override
            protected void done() {
              if (PleaseWait.this.isVisible()) {
                PleaseWait.this.dispose();
              } else {
                PleaseWait.this.alreadyFinished = true;
              }
            }
          };
    }

    public T get() {
      worker.execute();
      final var progressBar = new JProgressBar();
      progressBar.setIndeterminate(true);
      final var panel = new JPanel(new BorderLayout());
      panel.add(progressBar, BorderLayout.CENTER);
      panel.add(new JLabel(S.get("analyzePleaseWait")), BorderLayout.PAGE_START);
      add(panel);
      setPreferredSize(new Dimension(300, 70));
      setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
      pack();
      setLocationRelativeTo(parentComponent);
      try {
        try {
          return worker.get(300, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ignored) {
          // do nothing
        }
        if (!alreadyFinished) setVisible(true);
        return worker.get();
      } catch (Exception e) {
        return null;
      }
    }
  }
}

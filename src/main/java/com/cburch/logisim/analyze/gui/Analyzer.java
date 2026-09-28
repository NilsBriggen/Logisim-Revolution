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
import com.cburch.logisim.analyze.model.AnalyzerHistory;
import com.cburch.logisim.analyze.model.AnalyzerModel;
import com.cburch.logisim.analyze.model.Implicant;
import com.cburch.logisim.analyze.model.TruthTableEvent;
import com.cburch.logisim.analyze.model.TruthTableListener;
import com.cburch.logisim.gui.generic.LFrame;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.LocaleListener;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.Spacing;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.swing.BorderFactory;
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
import javax.swing.SwingUtilities;
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
  private final AnalyzerHistory history;

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
    // The analyzer's own history: its edits are not project actions, so Edit > Undo (Ctrl+Z) in
    // this window undoes the last change of the table, expressions or variables.
    history = new AnalyzerHistory(model, SwingUtilities::invokeLater);
    final ActionListener undoRedo =
        event -> {
          if (event.getSource() == LogisimMenuBar.UNDO) history.undo();
          else if (event.getSource() == LogisimMenuBar.REDO) history.redo();
        };
    menubar.addActionListener(LogisimMenuBar.UNDO, undoRedo);
    menubar.addActionListener(LogisimMenuBar.REDO, undoRedo);
    history.addListener(this::updateUndoItems);
    updateUndoItems();
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
    // When the groups do not fit next to each other (a large interface scale, long labels), the
    // later groups move onto rows of their own instead of being cut off.
    final var tableGroup = new JPanel();
    tableGroup.setLayout(new BoxLayout(tableGroup, BoxLayout.X_AXIS));
    tableGroup.add(importTable);
    tableGroup.add(Box.createHorizontalStrut(Spacing.sm()));
    tableGroup.add(minimizeMinterms);
    tableGroup.add(Box.createHorizontalStrut(Spacing.sm()));
    tableGroup.add(minimizeMaxterms);
    final var outputGroup = new JPanel();
    outputGroup.setLayout(new BoxLayout(outputGroup, BoxLayout.X_AXIS));
    outputGroup.add(exportTable);
    outputGroup.add(Box.createHorizontalStrut(Spacing.sm()));
    outputGroup.add(exportTex);
    outputGroup.add(Box.createHorizontalStrut(Spacing.lg()));
    // Building the circuit is what the window is for, and the only one that changes the project.
    buildCircuit.putClientProperty("JButton.buttonType", "default");
    outputGroup.add(buildCircuit);
    final var buttonPanel = new JPanel(new ButtonRowLayout(Spacing.lg()));
    buttonPanel.setBorder(Spacing.panelBorder());
    buttonPanel.add(tableGroup);
    buttonPanel.add(outputGroup);
    buttonPanel.addComponentListener(
        new ComponentAdapter() {
          @Override
          public void componentResized(ComponentEvent event) {
            // One row or several depends on the width, so the height follows it.
            if (buttonPanel.getPreferredSize().height != buttonPanel.getHeight()) {
              buttonPanel.revalidate();
            }
          }
        });

    contents.add(tabbedPane, BorderLayout.CENTER);
    contents.add(buttonPanel, BorderLayout.SOUTH);
    getRootPane().setDefaultButton(buildCircuit);

    // A new interface scale (or theme) changes every font: grow to fit, as for a new language,
    // instead of cutting off the expression area and the buttons.
    Theme.addListener(getRootPane(), this::resizeForScaleChange);

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
    // This scroll pane is the whole tab. With a border, the look and feel drew a focus ring round
    // the entire window content whenever the tab's panel held the focus; the focused control inside
    // shows its own.
    pane.setBorder(BorderFactory.createEmptyBorder());
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

  /** The undo history of this window's content. */
  public AnalyzerHistory getHistory() {
    return history;
  }

  private void updateUndoItems() {
    menubar.setEnabled(LogisimMenuBar.UNDO, history.canUndo());
    menubar.setEnabled(LogisimMenuBar.REDO, history.canRedo());
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

  private void resizeForScaleChange() {
    setMinimumSize(
        new Dimension(AppPreferences.getScaled(450), AppPreferences.getScaled(300)));
    resizeForLocaleChange();
  }

  private void resizeForLocaleChange() {
    getContentPane().invalidate();
    invalidate();

    final var current = getSize();
    if (current.width <= 0 || current.height <= 0) return;

    var expanded = expandedSizeForLocaleChange(current, getPreferredSize());
    final var config = getGraphicsConfiguration();
    if (config != null) {
      final var screen = config.getBounds();
      expanded =
          new Dimension(
              Math.min(expanded.width, Math.max(current.width, screen.width * 9 / 10)),
              Math.min(expanded.height, Math.max(current.height, screen.height * 9 / 10)));
    }
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

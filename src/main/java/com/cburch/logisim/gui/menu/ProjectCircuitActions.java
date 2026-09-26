/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.menu;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.contracts.BaseWindowFocusListenerContract;
import com.cburch.logisim.analyze.gui.Analyzer;
import com.cburch.logisim.analyze.gui.AnalyzerManager;
import com.cburch.logisim.analyze.model.AnalyzerModel;
import com.cburch.logisim.analyze.model.Var;
import com.cburch.logisim.circuit.Analyze;
import com.cburch.logisim.circuit.AnalyzeException;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitNameValidator;
import com.cburch.logisim.circuit.EditLockedException;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.file.LogisimFileActions;
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.util.JFileChoosers;
import com.cburch.logisim.vhdl.base.VerilogContent;
import com.cburch.logisim.vhdl.base.VhdlContent;
import com.cburch.logisim.vhdl.base.VhdlEntity;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class ProjectCircuitActions {
  private ProjectCircuitActions() {
    // dummy, private
  }

  private static void analyzeError(Project proj, String message) {
    OptionPane.showMessageDialog(
        proj.getFrame(), message, S.get("analyzeErrorTitle"), OptionPane.ERROR_MESSAGE);
  }

  private static void configureAnalyzer(
      Project proj,
      Circuit circuit,
      Analyzer analyzer,
      Map<Instance, String> pinNames,
      ArrayList<Var> inputVars,
      ArrayList<Var> outputVars) {
    analyzer.getModel().setVariables(inputVars, outputVars);

    // If there are no inputs or outputs, we stop with that tab selected
    if (inputVars.size() == 0 || outputVars.size() == 0) {
      analyzer.setSelectedTab(Analyzer.IO_TAB);
      return;
    }

    // Attempt to show the corresponding expression
    try {
      Analyze.computeExpression(analyzer.getModel(), circuit, pinNames);
      analyzer.setSelectedTab(Analyzer.EXPRESSION_TAB);
      return;
    } catch (AnalyzeException ex) {
      OptionPane.showMessageDialog(
          proj.getFrame(),
          ex.getMessage(),
          S.get("analyzeNoExpressionTitle"),
          OptionPane.INFORMATION_MESSAGE);
    }

    // As a backup measure, we compute a truth table.
    Analyze.computeTable(analyzer.getModel(), proj, circuit, pinNames);
    analyzer.setSelectedTab(Analyzer.TABLE_TAB);
  }

  public static void doAddCircuit(Project proj) {
    String initialValue = "";
    while (true) {
      final var name = promptForCircuitName(proj.getFrame(), proj.getLogisimFile(), initialValue);
      if (name == null) break;

      final var error = CircuitNameValidator.problemWith(proj.getLogisimFile(), name, null);
      if (error != null) {
        OptionPane.showMessageDialog(
            proj.getFrame(), error, S.get("circuitCreateTitle"), OptionPane.ERROR_MESSAGE);
        initialValue = name;
      } else {
        final var circuit = new Circuit(name, proj.getLogisimFile(), proj);
        proj.doAction(LogisimFileActions.addCircuit(circuit));
        proj.setCurrentCircuit(circuit);
        break;
      }
    }
  }

  public static void doAddVhdl(Project proj) {
    String initialValue = "";
    while (true) {
      final var name = promptForNewName(proj.getFrame(), proj.getLogisimFile(), initialValue, true);
      if (name == null) break;

      if (VhdlContent.labelVHDLInvalidNotify(name, proj.getLogisimFile())) {
        initialValue = name;
        continue;
      }

      final var content = VhdlContent.create(name, proj.getLogisimFile());
      if (content != null) {
        proj.doAction(LogisimFileActions.addVhdl(content));
        proj.setCurrentHdlModel(content);
        break;
      } else {
        initialValue = name;
      }
    }
  }

  public static void doImportVhdl(Project proj) {
    final var vhdl = proj.getLogisimFile().getLoader().vhdlImportChooser(proj.getFrame());
    if (vhdl == null) return;

    final var content = VhdlContent.parse(null, vhdl, proj.getLogisimFile());
    if (content == null) return;
    if (VhdlContent.labelVHDLInvalidNotify(content.getName(), proj.getLogisimFile())) return;

    proj.doAction(LogisimFileActions.addVhdl(content));
    proj.setCurrentHdlModel(content);
  }

  /** Project > New Verilog Module: asks for a name and opens the template in the HDL editor. */
  public static void doAddVerilog(Project proj) {
    final var file = proj.getLogisimFile();
    var name = "";
    while (true) {
      name =
          promptForNewName(
              proj.getFrame(),
              name,
              S.get("verilogNameDialogTitle"),
              S.get("verilogNamePrompt"),
              candidate ->
                  candidate.isEmpty() ? null : VerilogContent.nameProblem(candidate, file));
      if (name == null || name.isEmpty()) return;
      // The reason is already shown under the field: ask again, keeping the typed name.
      if (VerilogContent.nameProblem(name, file) == null) break;
    }
    final var content = VerilogContent.create(name, file);
    proj.doAction(LogisimFileActions.addVhdl(content));
    proj.setCurrentHdlModel(content);
  }

  /** Project > Import Verilog Module: reads one module from a .v file. */
  public static void doImportVerilog(Project proj) {
    final var file = proj.getLogisimFile();
    final var verilog = file.getLoader().verilogImportChooser(proj.getFrame());
    if (verilog == null) return;
    final var content = VerilogContent.parse(null, verilog, file);
    // An unreadable header was already reported; a module without a usable name cannot be added.
    if (!content.isValid()) return;
    proj.doAction(LogisimFileActions.addVhdl(content));
    proj.setCurrentHdlModel(content);
  }

  public static void doAnalyze(Project proj, Circuit circuit) {
    final var pinNames = Analyze.getPinLabels(circuit);
    final var inputVars = new ArrayList<Var>();
    final var outputVars = new ArrayList<Var>();
    var numInputs = 0;
    var numOutputs = 0;
    for (final var entry : pinNames.entrySet()) {
      final var pin = entry.getKey();
      final var isInput = Pin.FACTORY.isInputPin(pin);
      final var width = pin.getAttributeValue(StdAttr.WIDTH).getWidth();
      final var v = new Var(entry.getValue(), width);
      if (isInput) {
        inputVars.add(v);
        numInputs += width;
      } else {
        outputVars.add(v);
        numOutputs += width;
      }
    }
    if (numInputs > AnalyzerModel.MAX_INPUTS) {
      analyzeError(proj, S.get("analyzeTooManyInputsError", "" + AnalyzerModel.MAX_INPUTS));
      return;
    }
    if (numOutputs > AnalyzerModel.MAX_OUTPUTS) {
      analyzeError(proj, S.get("analyzeTooManyOutputsError", "" + AnalyzerModel.MAX_OUTPUTS));
      return;
    }
    try {
      Analyze.checkWidths(circuit);
    } catch (AnalyzeException ex) {
      analyzeError(proj, ex.getMessage());
      return;
    }

    final var analyzer = AnalyzerManager.getAnalyzer(proj.getFrame());
    final var model = analyzer.getModel();
    if (model.isEdited()) {
      // Analysing replaces the table the user built by hand, and the analyzer has no undo.
      final var choice =
          OptionPane.showConfirmDialog(
              analyzer.isVisible() ? analyzer : proj.getFrame(),
              S.get("analyzeReplaceEditedMessage", circuit.getName()),
              S.get("analyzeReplaceEditedTitle"),
              OptionPane.YES_NO_OPTION,
              OptionPane.WARNING_MESSAGE);
      if (choice != OptionPane.YES_OPTION) {
        if (analyzer.isVisible()) analyzer.toFront();
        return;
      }
    }
    model.setCurrentCircuit(proj, circuit);
    model.beginReplacement();
    try {
      configureAnalyzer(proj, circuit, analyzer, pinNames, inputVars, outputVars);
    } finally {
      model.endReplacement();
    }
    if (!analyzer.isVisible()) {
      analyzer.setVisible(true);
    }
    analyzer.toFront();
  }

  public static void doMoveCircuit(Project proj, Circuit cur, int delta) {
    final var tool = proj.getLogisimFile().getAddTool(cur);
    if (tool != null) {
      final var oldPos = proj.getLogisimFile().indexOfCircuit(cur);
      final var newPos = oldPos + delta;
      final var toolsCount = proj.getLogisimFile().getTools().size();
      if (newPos >= 0 && newPos < toolsCount) {
        proj.doAction(LogisimFileActions.moveCircuit(tool, newPos));
      }
    }
  }

  /**
   * Whether {@code circuit} can be removed: it belongs to the project, is not the last circuit and
   * no other circuit uses it. Every place offering removal asks this, so they agree.
   */
  public static boolean canRemoveCircuit(Project proj, Circuit circuit) {
    final var file = proj.getLogisimFile();
    return circuit != null
        && file.contains(circuit)
        && file.getCircuitCount() > 1
        && proj.getDependencies().canRemove(circuit);
  }

  public static void doRemoveCircuit(Project proj, Circuit circuit) {
    if (circuit.isEditLocked()) {
      // Said in the status bar, before any question is asked about a removal that cannot happen.
      proj.reportRefusedEdit(new EditLockedException(circuit, null));
    } else if (proj.getLogisimFile().getCircuits().size() == 1) {
      OptionPane.showMessageDialog(
          proj.getFrame(),
          S.get("circuitRemoveLastError"),
          S.get("circuitRemoveErrorTitle"),
          OptionPane.ERROR_MESSAGE);
    } else if (!proj.getDependencies().canRemove(circuit)) {
      OptionPane.showMessageDialog(
          proj.getFrame(),
          S.get("circuitRemoveUsedError", circuit.getName()),
          S.get("circuitRemoveErrorTitle"),
          OptionPane.ERROR_MESSAGE);
    } else {
      // Removing the main circuit silently makes another one main; say which.
      final var file = proj.getLogisimFile();
      final var newMain = file.mainCircuitAfterRemoving(circuit);
      final var message =
          circuit == file.getMainCircuit() && newMain != null
              ? S.get("circuitRemoveMainConfirm", circuit.getName(), newMain.getName())
              : S.get("circuitRemoveConfirm", circuit.getName());
      int result = OptionPane.showConfirmDialog(
          proj.getFrame(),
          message,
          S.get("circuitRemoveConfirmTitle"),
          OptionPane.YES_NO_OPTION);
      if (result == OptionPane.YES_OPTION) {
        proj.doAction(LogisimFileActions.removeCircuit(circuit));
      }
    }
  }

  public static void doRemoveVhdl(Project proj, VhdlContent vhdl) {
    if (!proj.getDependencies().canRemove(vhdl)) {
      OptionPane.showMessageDialog(
          proj.getFrame(),
          S.get(vhdl.isVerilog() ? "verilogRemoveUsedError" : "vhdlRemoveUsedError", vhdl.getName()),
          S.get(vhdl.isVerilog() ? "verilogRemoveErrorTitle" : "vhdlRemoveErrorTitle"),
          OptionPane.ERROR_MESSAGE);
    } else {
      int result = OptionPane.showConfirmDialog(
          proj.getFrame(),
          S.get(vhdl.isVerilog() ? "verilogRemoveConfirm" : "vhdlRemoveConfirm", vhdl.getName()),
          S.get(vhdl.isVerilog() ? "verilogRemoveConfirmTitle" : "vhdlRemoveConfirmTitle"),
          OptionPane.YES_NO_OPTION);
      if (result == OptionPane.YES_OPTION) {
        proj.doAction(LogisimFileActions.removeVhdl(vhdl));
      }
    }
  }

  public static void doSetAsMainCircuit(Project proj, Circuit circuit) {
    proj.doAction(LogisimFileActions.setMainCircuit(circuit));
  }

  /**
   * Ask the user for the name of the new circuit to create. If the name is valid, then it returns
   * it, otherwise it displays an error message and returns null.
   *
   * @param frame Project's frame
   * @param lib Project's logisim file
   * @param initialValue Default suggested value (can be empty if no initial value)
   */
  private static String promptForCircuitName(
      JFrame frame, LogisimFile file, String initialValue) {
    return promptForNewName(
        frame, file, initialValue, false,
        name -> CircuitNameValidator.problemWith(file, name, null));
  }

  private static String promptForVhdlName(JFrame frame, LogisimFile file, String initialValue) {
    final var name = promptForNewName(frame, file, initialValue, true);
    if (name == null) return null;
    if (VhdlContent.labelVHDLInvalidNotify(name, file)) return null;
    return name;
  }

  private static String promptForNewName(
      JFrame frame, Library lib, String initialValue, boolean vhdl) {
    return promptForNewName(frame, lib, initialValue, vhdl, null);
  }

  /**
   * @param validator explains why a name is refused, or returns null; shown under the field while
   *     typing, so a refusal never costs the typed text. May be null.
   */
  private static String promptForNewName(
      JFrame frame,
      Library lib,
      String initialValue,
      boolean vhdl,
      Function<String, String> validator) {
    return vhdl
        ? promptForNewName(
            frame, initialValue, S.get("vhdlNameDialogTitle"), S.get("vhdlNamePrompt"), validator)
        : promptForNewName(
            frame,
            initialValue,
            S.get("circuitNameDialogTitle"),
            S.get("circuitNamePrompt"),
            validator);
  }

  private static String promptForNewName(
      JFrame frame,
      String initialValue,
      String title,
      String prompt,
      Function<String, String> validator) {
    final var field = new JTextField(15);
    field.setText(initialValue);
    final var gbl = new GridBagLayout();
    final var gbc = new GridBagConstraints();
    final var strut = new JPanel(null);
    strut.setPreferredSize(new Dimension(3 * field.getPreferredSize().width / 2, 0));
    gbc.gridx = 0;
    gbc.gridy = GridBagConstraints.RELATIVE;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.NONE;
    gbc.anchor = GridBagConstraints.LINE_START;
    final var label = new JLabel(prompt);
    gbl.setConstraints(label, gbc);
    final var panel = new JPanel(gbl);
    panel.add(label);
    gbl.setConstraints(field, gbc);
    panel.add(field);
    final var error = new JLabel(" ");
    gbl.setConstraints(error, gbc);
    panel.add(error);
    if (validator != null) {
      final Runnable check =
          () -> {
            final var problem = validator.apply(field.getText().trim());
            // Wrapped to the field's width; a blank line keeps the dialog from jumping.
            error.setText(
                problem == null
                    ? " "
                    : "<html><body style='width:"
                        + (3 * field.getPreferredSize().width / 2)
                        + "px'>"
                        + OptionPane.escapeHtml(problem)
                        + "</body></html>");
            final var window = SwingUtilities.getWindowAncestor(panel);
            if (window != null) window.pack();
          };
      field.getDocument().addDocumentListener(
          new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
              check.run();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
              check.run();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
              check.run();
            }
          });
      if (!initialValue.isEmpty()) check.run();
    }
    gbl.setConstraints(strut, gbc);
    panel.add(strut);
    final var pane =
        new JOptionPane(panel, OptionPane.QUESTION_MESSAGE, OptionPane.OK_CANCEL_OPTION);
    pane.setInitialValue(field);
    final var dlog = pane.createDialog(frame, title);
    dlog.addWindowFocusListener(
        new BaseWindowFocusListenerContract() {
          @Override
          public void windowGainedFocus(WindowEvent arg0) {
            field.requestFocus();
          }
        });

    field.selectAll();
    dlog.pack();
    dlog.setVisible(true);
    field.requestFocusInWindow();

    final var action = pane.getValue();
    if (!(action instanceof Integer) || (Integer) action != OptionPane.OK_OPTION) {
      return null;
    }

    return field.getText().trim();
  }

  public static void collectDependencies(
      LogisimFile file, Circuit circuit, Set<Circuit> visitedCircuits, Set<VhdlContent> visitedVhdl) {
    if (file == null || circuit == null) return;
    for (final var comp : circuit.getNonWires()) {
      final var factory = comp.getFactory();
      if (factory instanceof SubcircuitFactory circFact) {
        final var sub = circFact.getSubcircuit();
        if (sub != null && file.contains(sub) && !visitedCircuits.contains(sub) && sub != circuit) {
          visitedCircuits.add(sub);
          collectDependencies(file, sub, visitedCircuits, visitedVhdl);
        }
      } else if (factory instanceof VhdlEntity vhdlFact) {
        final var vhdl = vhdlFact.getContent();
        if (vhdl != null && file.contains(vhdl) && !visitedVhdl.contains(vhdl)) {
          visitedVhdl.add(vhdl);
        }
      }
    }
  }

  public static void doExportCircuit(Project proj, Circuit targetCircuit) {
    if (proj == null || targetCircuit == null) return;

    final var file = proj.getLogisimFile();
    final var dependentCircuits = new LinkedHashSet<Circuit>();
    final var dependentVhdl = new LinkedHashSet<VhdlContent>();
    collectDependencies(file, targetCircuit, dependentCircuits, dependentVhdl);

    Map<Circuit, String> renamedMap = null;
    if (!dependentCircuits.isEmpty()) {
      final var dialog =
          new ExportSubcircuitDialog(
              proj.getFrame(), targetCircuit, new ArrayList<>(dependentCircuits));
      dialog.setVisible(true);
      if (!dialog.isApproved()) return;
      renamedMap = dialog.getRenamedCircuits();
    }

    final var defaultFile = new File(targetCircuit.getName() + ".circ");
    final var chooser = JFileChoosers.createSelected(defaultFile);
    chooser.setFileFilter(Loader.LOGISIM_FILTER);
    chooser.setDialogTitle(S.get("exportSubcircuitTitle", targetCircuit.getName()));

    final var check = chooser.showSaveDialog(proj.getFrame());
    if (check != JFileChooser.APPROVE_OPTION) return;

    var dest = chooser.getSelectedFile();
    if (dest == null) return;
    if (!dest.getName().toLowerCase().endsWith(".circ")) {
      dest = new File(dest.getParentFile(), dest.getName() + ".circ");
    }

    if (dest.exists()) {
      final var confirm =
          OptionPane.showConfirmDialog(
              proj.getFrame(),
              S.get("confirmOverwriteMessage", dest.getName()),
              S.get("confirmOverwriteTitle"),
              OptionPane.YES_NO_OPTION);
      if (confirm != OptionPane.YES_OPTION) return;
    }

    final var originalNames = new HashMap<Circuit, String>();
    if (renamedMap != null) {
      for (final var entry : renamedMap.entrySet()) {
        final var circ = entry.getKey();
        final var newName = entry.getValue();
        originalNames.put(circ, circ.getName());
        circ.setName(newName);
      }
    }

    try {
      final var loader = proj.getLogisimFile().getLoader();
      final var exportFile = LogisimFile.createEmpty(loader);

      for (final var lib : proj.getLogisimFile().getLibraries()) {
        exportFile.addLibrary(lib);
      }
      exportFile.addCircuit(targetCircuit);
      for (final var circ : dependentCircuits) {
        exportFile.addCircuit(circ);
      }
      for (final var vhdl : dependentVhdl) {
        exportFile.addVhdlContent(vhdl);
      }
      exportFile.setMainCircuit(targetCircuit);
      exportFile.getOptions().copyFrom(proj.getLogisimFile().getOptions(), exportFile);

      try (final var out = new FileOutputStream(dest)) {
        exportFile.write(out, loader, dest);
      }

      OptionPane.showMessageDialog(
          proj.getFrame(),
          String.format(S.get("exportSubcircuitSuccess"), dest.getName()),
          S.get("exportSubcircuitTitle", targetCircuit.getName()),
          OptionPane.INFORMATION_MESSAGE);
    } catch (Exception ex) {
      OptionPane.showMessageDialog(
          proj.getFrame(),
          ex.getMessage(),
          S.get("exportSubcircuitTitle", targetCircuit.getName()),
          OptionPane.ERROR_MESSAGE);
    } finally {
      if (!originalNames.isEmpty()) {
        for (final var entry : originalNames.entrySet()) {
          entry.getKey().setName(entry.getValue());
        }
      }
    }
  }
}

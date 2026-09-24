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

import com.cburch.logisim.analyze.model.AnalyzerModel;
import com.cburch.logisim.analyze.model.Expression;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.file.LogisimFileActions;
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectActions;
import com.cburch.logisim.proj.Projects;
import com.cburch.logisim.std.gates.CircuitBuilder;
import com.cburch.logisim.util.SyntaxChecker;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashSet;
import java.util.Set;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.AncestorEvent;
import javax.swing.event.AncestorListener;

class BuildCircuitButton extends JButton {
  private class DialogPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final JLabel projectLabel = new JLabel();
    private final JComboBox<Object> project;
    private final JLabel nameLabel = new JLabel();
    private final JTextField name = new JTextField(10);
    private final JCheckBox twoInputs = new JCheckBox();
    private final JCheckBox nands = new JCheckBox();

    DialogPanel() {
      final var projects = Projects.getOpenProjects();
      final var options = new Object[projects.size() + 1];
      Object initialSelection = null;
      options[0] = new ProjectItem(null);
      for (int i = 1; i < options.length; i++) {
        final var proj = projects.get(i - 1);
        options[i] = new ProjectItem(proj);
        if (proj == model.getCurrentProject()) {
          initialSelection = options[i];
        }
      }
      project = new JComboBox<>(options);
      if (options.length == 1) {
        project.setSelectedItem(options[0]);
        project.setEnabled(false);
      } else if (initialSelection != null) {
        project.setSelectedItem(initialSelection);
      } else {
        project.setSelectedItem(options[options.length - 1]);
      }

      final var defaultCircuit = model.getCurrentCircuit();
      final var reserved = new HashSet<String>();
      final var destination = ((ProjectItem) project.getSelectedItem()).project;
      if (destination != null) {
        for (final var circuit : destination.getLogisimFile().getCircuits()) {
          reserved.add(circuit.getName());
        }
      }
      reserved.addAll(model.getInputs().getNames());
      reserved.addAll(model.getOutputs().getNames());
      final var baseName = defaultCircuit == null
          ? S.get("buildNewCircuitName") : S.get("buildGeneratedCircuitName", defaultCircuit.getName());
      name.setText(uniqueCircuitName(baseName, reserved));
      name.selectAll();
      name.addAncestorListener(new AncestorListener() {
        @Override
        public void ancestorAdded(AncestorEvent event) {
          SwingUtilities.invokeLater(() -> {
            name.requestFocusInWindow();
            name.selectAll();
          });
        }

        @Override
        public void ancestorRemoved(AncestorEvent event) {}

        @Override
        public void ancestorMoved(AncestorEvent event) {}
      });

      final var outputs = model.getOutputs();
      var enableNands = true;
      for (String output : outputs.bits) {
        final var expr = model.getOutputExpressions().getExpression(output);
        if (expr != null && (expr.contains(Expression.Op.XOR) || expr.contains(Expression.Op.EQ))) {
          enableNands = false;
          break;
        }
      }
      nands.setEnabled(enableNands);

      final var gbl = new GridBagLayout();
      final var gbc = new GridBagConstraints();
      setLayout(gbl);
      gbc.anchor = GridBagConstraints.LINE_START;
      gbc.fill = GridBagConstraints.NONE;

      gbc.gridx = 0;
      gbc.gridy = 0;
      gbl.setConstraints(projectLabel, gbc);
      add(projectLabel);
      gbc.gridx = 1;
      gbl.setConstraints(project, gbc);
      add(project);
      gbc.gridy++;
      gbc.gridx = 0;
      gbl.setConstraints(nameLabel, gbc);
      add(nameLabel);
      gbc.gridx = 1;
      gbl.setConstraints(name, gbc);
      add(name);
      gbc.gridy++;
      gbl.setConstraints(twoInputs, gbc);
      add(twoInputs);
      gbc.gridy++;
      gbl.setConstraints(nands, gbc);
      add(nands);

      projectLabel.setText(S.get("buildProjectLabel"));
      nameLabel.setText(S.get("buildNameLabel"));
      twoInputs.setText(S.get("buildTwoInputsLabel"));
      nands.setText(S.get("buildNandsLabel"));
    }
  }

  private class MyListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent event) {
      Project dest = null;
      String name = null;
      var twoInputs = false;
      var useNands = false;
      var replace = false;

      if (!model.getOutputExpressions().hasExpressions()) {
        OptionPane.showMessageDialog(
                parent,
                S.get("zeroOrNotOptimizedMessage"),
                S.get("buildDialogTitle"),
                OptionPane.ERROR_MESSAGE);
        return;
      }

      var ok = false;
      while (!ok) {
        DialogPanel dlog = new DialogPanel();
        var action =
            OptionPane.showConfirmDialog(
                parent,
                dlog,
                S.get("buildDialogTitle"),
                OptionPane.OK_CANCEL_OPTION,
                OptionPane.QUESTION_MESSAGE);
        if (action != OptionPane.OK_OPTION) return;

        final var projectItem = (ProjectItem) dlog.project.getSelectedItem();
        if (projectItem == null) {
          OptionPane.showMessageDialog(
              parent,
              S.get("buildNeedProjectError"),
              S.get("buildDialogErrorTitle"),
              OptionPane.ERROR_MESSAGE);
          continue;
        }
        dest = projectItem.project;

        name = dlog.name.getText().trim();
        if (name.equals("")) {
          OptionPane.showMessageDialog(
              parent,
              S.get("buildNeedCircuitError"),
              S.get("buildDialogErrorTitle"),
              OptionPane.ERROR_MESSAGE);
          continue;
        }

        if (!SyntaxChecker.isVariableNameAcceptable(name, true)) continue;

        /* Check for name collisions with input and output names */
        final var labels = new HashSet<String>();
        for (final var label : model.getInputs().getNames()) labels.add(label.toUpperCase());
        for (final var label : model.getOutputs().getNames()) labels.add(label.toUpperCase());
        if (labels.contains(name.toUpperCase())) {
          OptionPane.showMessageDialog(
              parent,
              S.get("buildDuplicatedNameError"),
              S.get("buildDialogErrorTitle"),
              OptionPane.ERROR_MESSAGE);
          continue;
        }

        if (dest != null) {
          /* prevent upper-case lower-case mismatch */
          for (final var circ : dest.getLogisimFile().getCircuits()) {
            if (circ.getName().equalsIgnoreCase(name)) name = circ.getName();
          }
        }

        if (dest != null && dest.getLogisimFile().getCircuit(name) != null) {
          final String[] choices = {S.get("buildReplaceNamedCircuit", name), S.get("buildCancel")};
          final var choice =
              OptionPane.showOptionDialog(
                  parent,
                  S.get("buildConfirmReplaceMessage", name),
                  S.get("buildConfirmReplaceTitle"),
                  OptionPane.YES_NO_OPTION,
                  OptionPane.WARNING_MESSAGE,
                  null,
                  choices,
                  choices[1]);
          if (choice != 0) {
            continue;
          }
          replace = true;
        }

        twoInputs = dlog.twoInputs.isSelected();
        useNands = dlog.nands.isSelected();
        ok = true;
      }

      performAction(dest, name, replace, twoInputs, useNands);
    }
  }

  private static class ProjectItem {
    final Project project;

    ProjectItem(Project project) {
      this.project = project;
    }

    @Override
    public String toString() {
      if (project == null) return S.get("buildNewProject");
      else return project.getLogisimFile().getDisplayName();
    }
  }

  private static final long serialVersionUID = 1L;

  private final MyListener myListener = new MyListener();
  private final JFrame parent;
  private final AnalyzerModel model;

  static String uniqueCircuitName(String base, Set<String> reserved) {
    final var names = new HashSet<String>();
    for (final var name : reserved) names.add(name.toLowerCase(java.util.Locale.ROOT));
    var candidate = base;
    for (var suffix = 2; names.contains(candidate.toLowerCase(java.util.Locale.ROOT)); suffix++) {
      candidate = base + "_" + suffix;
    }
    return candidate;
  }

  BuildCircuitButton(JFrame parent, AnalyzerModel model) {
    super();
    this.parent = parent;
    this.model = model;
    addActionListener(myListener);
  }

  void localeChanged() {
    setText(S.get("buildCircuitButton"));
  }

  private void performAction(
      Project dest, String name, boolean replace, final boolean twoInputs, final boolean useNands) {
    if (replace) {
      final var circuit = dest.getLogisimFile().getCircuit(name);
      if (circuit == null) {
        OptionPane.showMessageDialog(
            parent,
            "Internal error prevents replacing circuit.",
            "Internal Error",
            OptionPane.ERROR_MESSAGE);
        return;
      }

      final var xn = CircuitBuilder.build(circuit, model, twoInputs, useNands);
      dest.doAction(xn.toAction(S.getter("replaceCircuitAction")));
    } else {
      // create new project if necessary
      if (dest == null) {
        dest = ProjectActions.doNew(dest);
      }
      // add the circuit
      final var circuit = new Circuit(name, dest.getLogisimFile(), dest);
      final var xn = CircuitBuilder.build(circuit, model, twoInputs, useNands);
      xn.execute();
      dest.doAction(LogisimFileActions.addCircuit(circuit));
      dest.setCurrentCircuit(circuit);
    }
  }
}

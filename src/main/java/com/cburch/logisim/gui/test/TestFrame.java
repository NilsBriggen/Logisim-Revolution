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

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.data.TestException;
import com.cburch.logisim.data.TestVector;
import com.cburch.logisim.gui.generic.LFrame;
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.util.LocaleListener;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.UiScale;
import com.cburch.logisim.util.WindowMenuItemManager;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class TestFrame extends LFrame.SubWindowWithSimulation {

  private static final long serialVersionUID = 1L;
  private final MyListener myListener = new MyListener();
  private final ModelHistory modelHistory =
      new ModelHistory(circuit -> new Model(project, circuit), myListener);
  private final WindowMenuManager windowManager;
  private final JFileChooser chooser = new JFileChooser();
  private final TestPanel panel;
  private final JButton load = new JButton();
  private final JButton run = new JButton();
  private final JButton stop = new JButton();
  private final JButton reset = new JButton();
  private final JButton close = new JButton();
  private final JLabel pass = new JLabel();
  private final JLabel fail = new JLabel();
  private File curFile;

  /**
   * The contents, for the drawer to hold.
   *
   * <p>The window's own Close button goes with it: the drawer tab closes the drawer, and a button
   * that closes the thing it is sitting inside reads as a mistake.
   */
  public javax.swing.JComponent dockedContent() {
    close.setVisible(false);
    if (body.getParent() != null) body.getParent().remove(body);
    return body;
  }

  private final JPanel body = new JPanel(new BorderLayout());

  public TestFrame(Project project) {
    super(project);
    this.windowManager = new WindowMenuManager();
    project.addProjectListener(myListener);
    updateWithProject(project);

    chooser.addChoosableFileFilter(chooser.getAcceptAllFileFilter());
    chooser.addChoosableFileFilter(TestVector.FILE_FILTER);
    chooser.setFileFilter(TestVector.FILE_FILTER);

    panel = new TestPanel(this);

    JPanel statusPanel = new JPanel();
    statusPanel.add(pass);
    statusPanel.add(fail);

    JPanel buttonPanel = new JPanel();
    buttonPanel.add(load);
    buttonPanel.add(run);
    buttonPanel.add(stop);
    buttonPanel.add(reset);
    buttonPanel.add(close);
    load.addActionListener(myListener);
    run.addActionListener(myListener);
    stop.addActionListener(myListener);
    reset.addActionListener(myListener);
    close.addActionListener(myListener);

    run.setEnabled(false);
    stop.setEnabled(false);
    reset.setEnabled(false);

    panel.setPreferredSize(
        new Dimension(UiScale.scaled(450), UiScale.scaled(300)));
    body.add(statusPanel, BorderLayout.NORTH);
    body.add(panel, BorderLayout.CENTER);
    body.add(buttonPanel, BorderLayout.SOUTH);
    getContentPane().add(body, BorderLayout.CENTER);

    LocaleManager.addLocaleListener(myListener);
    myListener.localeChanged();
    pack();
  }

  private static String computeTitle(Model data, Project proj) {
    var name = data == null ? "???" : data.getCircuit().getName();
    return S.get("testFrameTitle", name, proj.getLogisimFile().getDisplayName());
  }

  Model getModel() {
    return modelHistory.current;
  }

  /** Test results belong to circuits, not to the nullable active HDL editor state. */
  static final class ModelHistory {
    private final Map<Circuit, Model> models = new HashMap<>();
    private final Function<Circuit, Model> factory;
    private final ModelListener listener;
    private volatile Model current;

    ModelHistory(Function<Circuit, Model> factory, ModelListener listener) {
      this.factory = factory;
      this.listener = listener;
    }

    Model select(Circuit circuit) {
      if (circuit == (current == null ? null : current.getCircuit())) return current;
      final var old = current;
      current = null;
      if (old != null) {
        old.removeModelListener(listener);
        old.setSelected(false);
      }
      if (circuit != null) {
        current = models.computeIfAbsent(circuit, factory);
        current.addModelListener(listener);
        current.setSelected(true);
      }
      return current;
    }
  }

  private void updateWithProject(Project theProject) {
    final var simulator = theProject.getSimulator();
    final var circuitState = theProject.getCircuitState();
    final var circuit = circuitState == null ? null : circuitState.getCircuit();
    menubar.setCircuitState(simulator, circuitState);
    final var oldModel = getModel();
    final var model = modelHistory.select(circuit);
    setTitle(computeTitle(model, project));
    if (panel != null && oldModel != model) panel.modelChanged(oldModel, model);
    myListener.testingChanged();
    repaint();
  }

  @Override
  public void setVisible(boolean value) {
    if (value) windowManager.frameOpened(this);
    super.setVisible(value);
  }

  private class MyListener
      implements ActionListener,
          ProjectListener,
          LocaleListener,
          ModelListener {

    @Override
    public void actionPerformed(ActionEvent event) {
      Object src = event.getSource();
      if (src == close) {
        requestClose();
        return;
      }
      final var model = getModel();
      if (model == null) return;
      if (src == load) {
        int result = chooser.showOpenDialog(TestFrame.this);
        if (result != JFileChooser.APPROVE_OPTION) return;
        if (getModel() != model) return;
        File file = chooser.getSelectedFile();
        if (!file.exists() || !file.canRead() || file.isDirectory()) {
          OptionPane.showMessageDialog(
              TestFrame.this,
              S.get("fileCannotReadMessage", file.getName()),
              S.get("fileCannotReadTitle"),
              OptionPane.OK_OPTION);
          return;
        }
        try {
          TestVector vec = new TestVector(file);
          model.setVector(vec);
          curFile = file;
          model.setPaused(true);
          model.start();
        } catch (IOException e) {
          OptionPane.showMessageDialog(
              TestFrame.this,
              S.get("fileCannotParseMessage", file.getName(), e.getMessage()),
              S.get("fileCannotReadTitle"),
              OptionPane.OK_OPTION);
        } catch (TestException e) {
          OptionPane.showMessageDialog(
              TestFrame.this,
              S.get("fileWrongPinsMessage", file.getName(), e.getMessage()),
              S.get("fileWrongPinsTitle"),
              OptionPane.OK_OPTION);
        }
      } else if (src == run) {
        try {
          model.start();
        } catch (TestException e) {
          OptionPane.showMessageDialog(
              TestFrame.this,
              S.get("fileWrongPinsMessage", curFile.getName(), e.getMessage()),
              S.get("fileWrongPinsTitle"),
              OptionPane.OK_OPTION);
        }
      } else if (src == stop) {
        model.setPaused(true);
      } else if (src == reset) {
        model.clearResults();
        testingChanged();
      }
    }

    @Override
    public void localeChanged() {
      setTitle(computeTitle(getModel(), project));
      panel.localeChanged();
      load.setText(S.get("loadButton"));
      run.setText(S.get("runButton"));
      stop.setText(S.get("stopButton"));
      reset.setText(S.get("resetButton"));
      close.setText(S.get("closeButton"));
      testingChanged();
      windowManager.localeChanged();
    }

    @Override
    public void projectChanged(ProjectEvent event) {
      int action = event.getAction();
      if (action == ProjectEvent.ACTION_SET_STATE) {
        updateWithProject(event.getProject());
      } else if (action == ProjectEvent.ACTION_SET_FILE) {
        setTitle(computeTitle(getModel(), project));
      }
    }

    @Override
    public void testingChanged() {
      if (!SwingUtilities.isEventDispatchThread()) {
        SwingUtilities.invokeLater(this::testingChanged);
        return;
      }
      final var model = getModel();
      updateControls(model, load, run, stop, reset);
      pass.setText(S.get("passMessage", Integer.toString(model == null ? 0 : model.getPass())));
      fail.setText(S.get("failMessage", Integer.toString(model == null ? 0 : model.getFail())));
    }

    @Override
    public void testResultsChanged(int numPass, int numFail) {
      // Re-read the active model; a queued result can belong to the circuit just deselected.
      testingChanged();
    }

    @Override
    public void vectorChanged() {
      // do nothing
    }
  }

  static void updateControls(Model model, JButton load, JButton run, JButton stop, JButton reset) {
    final var active = model != null;
    final var running = active && model.isRunning() && !model.isPaused();
    final var hasVector = active && model.getVector() != null;
    final var finished = active ? model.getPass() + model.getFail() : 0;
    load.setEnabled(active);
    run.setEnabled(hasVector && !running && finished < model.getVector().data.size());
    stop.setEnabled(running);
    reset.setEnabled(hasVector && finished > 0);
  }

  private class WindowMenuManager extends WindowMenuItemManager
      implements LocaleListener, ProjectListener {

    WindowMenuManager() {
      super(S.get("logFrameMenuItem"), false);
      project.addProjectListener(this);
    }

    @Override
    public JFrame getJFrame(boolean create, java.awt.Component parent) {
      return TestFrame.this;
    }

    @Override
    public void localeChanged() {
      String title = project.getLogisimFile().getDisplayName();
      setText(S.get("testFrameMenuItem", title));
    }

    @Override
    public void projectChanged(ProjectEvent event) {
      if (event.getAction() == ProjectEvent.ACTION_SET_FILE) {
        localeChanged();
      }
    }
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.log;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.file.LibraryEvent;
import com.cburch.logisim.file.LibraryListener;
import com.cburch.logisim.gui.chrono.ChronoPanel;
import com.cburch.logisim.gui.generic.LFrame;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.util.JDialogOk;
import com.cburch.logisim.util.LocaleListener;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.WindowMenuItemManager;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class LogFrame extends LFrame.SubWindowWithSimulation {
  private final LogMenuListener menuListener;

  private class MyListener
      implements ProjectListener, LibraryListener, Simulator.ProgressListener, LocaleListener {

    @Override
    public void libraryChanged(LibraryEvent event) {
      final var action = event.getAction();
      if (action == LibraryEvent.SET_NAME) {
        setTitle(computeTitle(getModel(), project));
      }
    }

    @Override
    public void localeChanged() {
      setTitle(computeTitle(getModel(), project));
      inactiveLabel.setText(S.get("editorEmptyHint"));
      for (var i = 0; i < panels.length; i++) {
        tabbedPane.setTitleAt(i, panels[i].getTitle());
        tabbedPane.setToolTipTextAt(i, panels[i].getToolTipText());
        panels[i].localeChanged();
      }
      windowManager.localeChanged();
    }

    @Override
    public void projectChanged(ProjectEvent event) {
      final var action = event.getAction();
      if (action == ProjectEvent.ACTION_SET_STATE) {
        setSimulator(event.getProject().getSimulator(), event.getProject().getCircuitState());
      } else if (action == ProjectEvent.ACTION_SET_FILE) {
        setTitle(computeTitle(getModel(), project));
      }
    }

    @Override
    public void simulatorReset(Simulator.Event e) {
      final var model = eventModel(e);
      if (model != null) model.simulatorReset();
    }

    @Override
    public void propagationCompleted(Simulator.Event e) {
      final var model = eventModel(e);
      if (model != null) model.propagationCompleted(e.didTick(), e.didSingleStep(), e.didPropagate());
    }

    @Override
    public boolean wantsProgressEvents() {
      final var model = getModel();
      return model != null && model.isFine();
    }

    @Override
    public void propagationInProgress(Simulator.Event e) {
      final var model = eventModel(e);
      if (model != null) model.propagationCompleted(false, true, false); // treat as a single-step
    }

    @Override
    public void simulatorStateChanged(Simulator.Event e) {
      if (setSimulator(project.getSimulator(), project.getCircuitState())) return;
      final var model = getModel();
      if (model != null) model.checkForClocks();
    }
  }

  // TODO: should automatically repaint icons when component attr change
  // TODO: ? moving a component using Select tool removes it from selection
  private class WindowMenuManager extends WindowMenuItemManager implements LocaleListener, ProjectListener, LibraryListener {
    final Project proj;

    WindowMenuManager(Project p) {
      super(S.get("logFrameMenuItem"), false);
      proj = p;
      proj.addProjectListener(this);
      proj.addLibraryListener(this);
    }

    @Override
    public JFrame getJFrame(boolean create, java.awt.Component parent) {
      return LogFrame.this;
    }

    @Override
    public void libraryChanged(LibraryEvent event) {
      if (event.getAction() == LibraryEvent.SET_NAME) {
        localeChanged();
      }
    }

    @Override
    public void localeChanged() {
      final var title = proj.getLogisimFile().getDisplayName();
      setText(S.get("logFrameMenuItem", title));
    }

    @Override
    public void projectChanged(ProjectEvent event) {
      if (event.getAction() == ProjectEvent.ACTION_SET_FILE) {
        localeChanged();
      }
    }
  }

  private static String computeTitle(Model data, Project proj) {
    final var name = data == null ? "???" : data.getCircuitState().getCircuit().getName();
    return S.get("logFrameTitle", name, proj.getLogisimFile().getDisplayName());
  }

  private static final long serialVersionUID = 1L;
  private Simulator curSimulator = null;
  private final LogModelHistory modelHistory = new LogModelHistory();
  private Model displayedModel;
  private final MyListener myListener = new MyListener();
  private final MyChangeListener myChangeListener = new MyChangeListener();

  private final WindowMenuManager windowManager;
  private LogPanel[] panels = new LogPanel[0];
  // private SelectionPanel selPanel;
  private final JTabbedPane tabbedPane = new JTabbedPane();
  private final JPanel modelView = new JPanel(new CardLayout());
  private final JLabel inactiveLabel = new JLabel();

  /**
   * Set while the drawer is holding this window's contents.
   *
   * <p>The timing diagram and the logging options used to be a second OS window with its own menu
   * bar and taskbar entry, placed below the main window from the raw screen size, so watching a
   * waveform next to the circuit meant tiling two windows by hand.
   */
  private boolean dockedInDrawer;

  static class SelectionDialog extends JDialogOk {
    private static final long serialVersionUID = 1L;
    final SelectionPanel selPanel;

    SelectionDialog(LogFrame logFrame) {
      super("Signal Selection", false);
      selPanel = new SelectionPanel(logFrame);
      selPanel.localeChanged();
      getContentPane().add(selPanel);
      setMinimumSize(new Dimension(AppPreferences.getScaled(350), AppPreferences.getScaled(300)));
      setSize(AppPreferences.getScaled(400), AppPreferences.getScaled(400));
      pack();
      setVisible(true);
    }

    @Override
    public void cancelClicked() {
      okClicked();
    }

    @Override
    public void okClicked() {}
  }

  public JButton makeSelectionButton() {
    final var button = new JButton(S.get("addRemoveSignals"));
    button.addActionListener(event -> {
      if (getModel() != null) SelectionPanel.doDialog(LogFrame.this);
    });
    return button;
  }

  /** The contents, for the drawer to hold. Building this marks the window as docked. */
  public javax.swing.JComponent dockedContent() {
    dockedInDrawer = true;
    if (modelView.getParent() != null) modelView.getParent().remove(modelView);
    return modelView;
  }

  public LogFrame(Project project) {
    super(project);
    windowManager = new WindowMenuManager(project);
    menuListener = new LogMenuListener(menubar);
    modelView.add(tabbedPane, "circuit");
    modelView.add(inactiveLabel, "inactive");
    project.addProjectListener(myListener);
    project.addLibraryListener(myListener);
    setSimulator(project.getSimulator(), project.getCircuitState());

    tabbedPane.addChangeListener(myChangeListener);
    myChangeListener.stateChanged(null);

    final var contents = getContentPane();
    final var w = Math.max(550, project.getFrame().getWidth());
    var h = 300;
    tabbedPane.setPreferredSize(new Dimension(w, h));
    if (!dockedInDrawer) contents.add(modelView, BorderLayout.CENTER);

    LocaleManager.addLocaleListener(myListener);
    myListener.localeChanged();
    pack();
    h = getSize().height;

    // Try to place below circuit window, or at least near bottom of screen,
    // using same width as circuit window.
    final var d = Toolkit.getDefaultToolkit().getScreenSize();
    final var r = project.getFrame().getBounds();
    int x = r.x;
    int y = r.y + r.height;
    if (y + h > d.height) { // too small below circuit
      if (r.y >= h) {
        // plenty of room above circuit
        y = r.y - h;
      } else if (r.y > d.height - h) {
        // circuit is near bottom of screen
        y = 0;
      } else {
        // circuit is near top of screen
        y = d.height - h;
      }
    }
    setLocation(x, y);
    setMinimumSize(new Dimension(300, 200));
    // set initial focus to first panel
    addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowOpened(WindowEvent e) {
            e.getWindow().removeWindowListener(this);
            myChangeListener.stateChanged(null);
          }
        });
  }

  @Override
  public LogisimMenuBar getLogisimMenuBar() {
    return menubar;
  }

  public LogMenuListener getMenuListener() {
    return menuListener;
  }

  public Model getModel() {
    return modelHistory.current();
  }

  LogPanel[] getPrefPanels() {
    return panels;
  }

  private boolean setSimulator(Simulator value, CircuitState state) {
    menubar.setCircuitState(value, state);
    final var simulatorChanged = curSimulator != value;
    if (simulatorChanged) {
      if (curSimulator != null) curSimulator.removeSimulatorListener(myListener);
      curSimulator = value;
      if (curSimulator != null) curSimulator.addSimulatorListener(myListener);
    }
    final var changed = modelHistory.select(value == null ? null : state);
    final var model = getModel();
    // Circuit panels retain their last valid model while hidden. Never feed them a fake/null
    // circuit. This also permits opening the drawer for the first time while editing HDL.
    if (model != null) {
      if (panels.length == 0) {
        panels = new LogPanel[] {new OptionsPanel(this), new ChronoPanel(this)};
        for (final var panel : panels) {
          tabbedPane.addTab(panel.getTitle(), null, panel, panel.getToolTipText());
        }
      } else if (model != displayedModel) {
        for (final var panel : panels) panel.modelChanged(displayedModel, model);
      }
      displayedModel = model;
    }
    ((CardLayout) modelView.getLayout()).show(modelView, model == null ? "inactive" : "circuit");
    setTitle(computeTitle(model, project));
    if (model == null) {
      menuListener.setEditHandler(null);
      menuListener.setPrintHandler(null);
    } else {
      myChangeListener.stateChanged(null);
    }
    return changed || simulatorChanged;
  }

  private Model eventModel(Simulator.Event event) {
    final var model = getModel();
    return event.getSource() == curSimulator && model != null
        && project.getCircuitState() == model.getCircuitState() ? model : null;
  }

  @Override
  public void setVisible(boolean value) {
    if (value) {
      windowManager.frameOpened(this);
    }
    super.setVisible(value);
  }

  @Override
  public void requestClose() {
    super.requestClose();
    dispose();
  }

  private class MyChangeListener implements ChangeListener {
    @Override
    public void stateChanged(ChangeEvent e) {
      if (getModel() == null) return;
      Object selected = tabbedPane.getSelectedComponent();
      if (selected instanceof JScrollPane scrollPane) {
        selected = scrollPane.getViewport().getView();
      }
      if (selected instanceof JPanel panel) {
        panel.requestFocus();
      }
      if (selected instanceof LogPanel tab) {
        menuListener.setEditHandler(tab.getEditHandler());
        menuListener.setPrintHandler(tab.getPrintHandler());
        // menuListener.setSimulationHandler(tab.getSimulationHandler());
        tab.updateTab();
      }
    }
  }
}

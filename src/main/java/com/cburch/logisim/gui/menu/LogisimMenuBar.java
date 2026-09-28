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

import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.fpga.menu.MenuFpga;
import com.cburch.logisim.gui.generic.LFrame;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.LocaleListener;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.WindowMenu;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import javax.swing.JMenuBar;
import javax.swing.KeyStroke;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

@SuppressWarnings("serial")
public class LogisimMenuBar extends JMenuBar {
  public static final LogisimMenuItem PRINT = new LogisimMenuItem("Print");
  public static final LogisimMenuItem EXPORT_IMAGE = new LogisimMenuItem("ExportImage");
  public static final LogisimMenuItem CUT = new LogisimMenuItem("Cut");
  public static final LogisimMenuItem COPY = new LogisimMenuItem("Copy");
  public static final LogisimMenuItem PASTE = new LogisimMenuItem("Paste");
  public static final LogisimMenuItem DELETE = new LogisimMenuItem("Delete");
  public static final LogisimMenuItem DUPLICATE = new LogisimMenuItem("Duplicate");
  public static final LogisimMenuItem SELECT_ALL = new LogisimMenuItem("SelectAll");
  public static final LogisimMenuItem RAISE = new LogisimMenuItem("Raise");
  public static final LogisimMenuItem LOWER = new LogisimMenuItem("Lower");
  public static final LogisimMenuItem RAISE_TOP = new LogisimMenuItem("RaiseTop");
  public static final LogisimMenuItem LOWER_BOTTOM = new LogisimMenuItem("LowerBottom");
  public static final LogisimMenuItem ADD_CONTROL = new LogisimMenuItem("AddControl");
  public static final LogisimMenuItem REMOVE_CONTROL = new LogisimMenuItem("RemoveControl");
  /**
   * Undo and Redo in a window that has no project but keeps its own history; windows with a
   * project undo through the project instead.
   */
  public static final LogisimMenuItem UNDO = new LogisimMenuItem("Undo");

  public static final LogisimMenuItem REDO = new LogisimMenuItem("Redo");
  public static final LogisimMenuItem[] EDIT_ITEMS = {
    // UNDO, REDO,
    CUT,
    COPY,
    PASTE,
    DELETE,
    DUPLICATE,
    SELECT_ALL,
    RAISE,
    LOWER,
    RAISE_TOP,
    LOWER_BOTTOM,
    ADD_CONTROL,
    REMOVE_CONTROL,
  };
  public static final LogisimMenuItem ALIGN_LEFT = new LogisimMenuItem("AlignLeft");
  public static final LogisimMenuItem ALIGN_CENTER = new LogisimMenuItem("AlignCenter");
  public static final LogisimMenuItem ALIGN_RIGHT = new LogisimMenuItem("AlignRight");
  public static final LogisimMenuItem ALIGN_TOP = new LogisimMenuItem("AlignTop");
  public static final LogisimMenuItem ALIGN_MIDDLE = new LogisimMenuItem("AlignMiddle");
  public static final LogisimMenuItem ALIGN_BOTTOM = new LogisimMenuItem("AlignBottom");
  public static final LogisimMenuItem DISTRIBUTE_HORIZONTAL =
      new LogisimMenuItem("DistributeHorizontal");
  public static final LogisimMenuItem DISTRIBUTE_VERTICAL =
      new LogisimMenuItem("DistributeVertical");

  /**
   * The Edit &gt; Arrange commands, in menu order. They are edit commands like {@link #EDIT_ITEMS}
   * but only the circuit layout editor handles them, so they are kept apart from the list that
   * other windows (such as the analyzer) bind keys for.
   */
  public static final LogisimMenuItem[] ARRANGE_ITEMS = {
    ALIGN_LEFT,
    ALIGN_CENTER,
    ALIGN_RIGHT,
    ALIGN_TOP,
    ALIGN_MIDDLE,
    ALIGN_BOTTOM,
    DISTRIBUTE_HORIZONTAL,
    DISTRIBUTE_VERTICAL,
  };

  /** The label of an Edit &gt; Arrange command, shared with the canvas context menu. */
  public static String arrangeItemText(LogisimMenuItem item) {
    if (item == ALIGN_LEFT) return S.get("editAlignLeftItem");
    if (item == ALIGN_CENTER) return S.get("editAlignCenterItem");
    if (item == ALIGN_RIGHT) return S.get("editAlignRightItem");
    if (item == ALIGN_TOP) return S.get("editAlignTopItem");
    if (item == ALIGN_MIDDLE) return S.get("editAlignMiddleItem");
    if (item == ALIGN_BOTTOM) return S.get("editAlignBottomItem");
    if (item == DISTRIBUTE_HORIZONTAL) return S.get("editDistributeHorizontalItem");
    if (item == DISTRIBUTE_VERTICAL) return S.get("editDistributeVerticalItem");
    throw new IllegalArgumentException("not an arrange item: " + item);
  }
  public static final LogisimMenuItem ADD_VHDL = new LogisimMenuItem("AddVhdl");
  public static final LogisimMenuItem IMPORT_VHDL = new LogisimMenuItem("ImportVhdl");
  public static final LogisimMenuItem ADD_VERILOG = new LogisimMenuItem("AddVerilog");
  public static final LogisimMenuItem IMPORT_VERILOG = new LogisimMenuItem("ImportVerilog");
  public static final LogisimMenuItem ADD_CIRCUIT = new LogisimMenuItem("AddCircuit");
  public static final LogisimMenuItem MOVE_CIRCUIT_UP = new LogisimMenuItem("MoveCircuitUp");
  public static final LogisimMenuItem MOVE_CIRCUIT_DOWN = new LogisimMenuItem("MoveCircuitDown");
  public static final LogisimMenuItem SET_MAIN_CIRCUIT = new LogisimMenuItem("SetMainCircuit");
  public static final LogisimMenuItem REMOVE_CIRCUIT = new LogisimMenuItem("RemoveCircuit");
  public static final LogisimMenuItem EDIT_LAYOUT = new LogisimMenuItem("EditLayout");
  public static final LogisimMenuItem EDIT_APPEARANCE = new LogisimMenuItem("EditAppearance");
  public static final LogisimMenuItem TOGGLE_APPEARANCE =
      new LogisimMenuItem("ToggleEditLayoutAppearance");
  public static final LogisimMenuItem REVERT_APPEARANCE = new LogisimMenuItem("RevertAppearance");
  public static final LogisimMenuItem ANALYZE_CIRCUIT = new LogisimMenuItem("AnalyzeCircuit");
  public static final LogisimMenuItem CIRCUIT_STATS = new LogisimMenuItem("GetCircuitStatistics");
  public static final LogisimMenuItem LOCK_CIRCUIT = new LogisimMenuItem("LockCircuit");
  public static final LogisimMenuItem LOCK_SELECTION = new LogisimMenuItem("LockSelection");
  public static final LogisimMenuItem SIMULATE_STOP = new LogisimMenuItem("SimulateStop");
  public static final LogisimMenuItem SIMULATE_RUN = new LogisimMenuItem("SimulateRun");
  public static final LogisimMenuItem SIMULATE_RUN_TOGGLE = new LogisimMenuItem("SimulateRun");
  public static final LogisimMenuItem SIMULATE_STEP = new LogisimMenuItem("SimulateStep");
  public static final LogisimMenuItem SIMULATE_RESET = new LogisimMenuItem("SimulateReset");
  public static final LogisimMenuItem SIMULATE_VHDL_ENABLE =
      new LogisimMenuItem("SimulateVhdlEnable");
  public static final LogisimMenuItem GENERATE_VHDL_SIM_FILES =
      new LogisimMenuItem("GenerateVhdlSimFiles");
  public static final LogisimMenuItem TICK_ENABLE = new LogisimMenuItem("TickEnable");
  public static final LogisimMenuItem TICK_HALF = new LogisimMenuItem("TickHalf");
  public static final LogisimMenuItem TICK_FULL = new LogisimMenuItem("TickFull");
  public final MenuFile file;
  public final MenuEdit edit;
  public final MenuProject project;
  public final MenuSimulate simulate;
  public final MenuHelp help;
  public final MenuFpga fpga;
  private final LFrame parent;
  private final MyListener listener;
  private final Project saveProj;
  private final Project baseProj;
  private final Project simProj;
  private final HashMap<LogisimMenuItem, MenuItem> menuItems = new HashMap<>();
  private final ArrayList<ChangeListener> enableListeners;
  private SimulateListener simulateListener = null;

  public LogisimMenuBar(LFrame parent, Project saveProj, Project baseProj, Project simProj) {
    this.parent = parent;
    this.listener = new MyListener();
    this.saveProj = saveProj;
    this.baseProj = baseProj;
    this.simProj = simProj;
    this.enableListeners = new ArrayList<>();
    add(file = new MenuFile(this));
    add(edit = new MenuEdit(this));
    add(project = new MenuProject(this));
    add(simulate = new MenuSimulate(this));
    add(fpga = new MenuFpga(parent, this, saveProj));
    add(new WindowMenu(parent));
    add(help = new MenuHelp(this));

    LocaleManager.addLocaleListener(this, listener);
    listener.localeChanged();
  }

  /**
   * Opens the help window at a topic, by its map id, such as {@code "features_colors"}.
   *
   * <p>Lets a message about a problem link to the page that explains it.
   */
  public void showHelp(String target) {
    help.showHelp(target);
  }

  public void disableFile() {
    file.setEnabled(false);
  }

  public void disableProject() {
    project.setEnabled(false);
  }

  public void addActionListener(LogisimMenuItem which, ActionListener l) {
    final var item = menuItems.get(which);
    if (item != null) item.addActionListener(l);
  }

  public void addEnableListener(ChangeListener l) {
    enableListeners.add(l);
  }

  public void doAction(LogisimMenuItem which) {
    final var item = menuItems.get(which);
    item.actionPerformed(new ActionEvent(item, ActionEvent.ACTION_PERFORMED, which.toString()));
  }

  public KeyStroke getAccelerator(LogisimMenuItem which) {
    final var item = menuItems.get(which);
    return item == null ? null : item.getAccelerator();
  }

  void fireEnableChanged() {
    final var e = new ChangeEvent(this);
    for (final var listener : enableListeners) {
      listener.stateChanged(e);
    }
  }

  void fireStateChanged(Simulator sim, CircuitState state) {
    if (simulateListener != null) {
      simulateListener.stateChangeRequested(sim, state);
    }
  }

  LFrame getParentFrame() {
    return parent;
  }

  public Project getSaveProject() {
    return saveProj;
  }

  public Project getBaseProject() {
    return baseProj;
  }

  public Project getSimulationProject() {
    return simProj;
  }

  public boolean isEnabled(LogisimMenuItem item) {
    final var menuItem = menuItems.get(item);
    return menuItem != null && menuItem.isEnabled();
  }

  void registerItem(LogisimMenuItem which, MenuItem item) {
    menuItems.put(which, item);
  }

  public void removeActionListener(LogisimMenuItem which, ActionListener l) {
    final var item = menuItems.get(which);
    if (item != null) item.removeActionListener(l);
  }

  public void removeEnableListener(ChangeListener l) {
    enableListeners.remove(l);
  }

  public void refreshEditUndoRedoItems() {
    edit.refreshUndoRedoItems();
  }

  public void setCircuitState(Simulator sim, CircuitState state) {
    simulate.setCurrentState(sim, state);
  }

  public void setEnabled(LogisimMenuItem which, boolean value) {
    final var item = menuItems.get(which);
    if (item != null) item.setEnabled(value);
  }

  public void setSimulateListener(SimulateListener l) {
    simulateListener = l;
  }

  private class MyListener implements LocaleListener {
    @Override
    public void localeChanged() {
      file.localeChanged();
      edit.localeChanged();
      project.localeChanged();
      fpga.localeChanged();
      simulate.localeChanged();
      help.localeChanged();
      MenuMnemonics.assign(LogisimMenuBar.this);
    }
  }
}

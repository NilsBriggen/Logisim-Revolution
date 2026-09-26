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

import com.cburch.logisim.circuit.EditLockAction;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.prefs.PrefMonitor;
import com.cburch.logisim.prefs.PrefMonitorKeyStroke;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;

class MenuProject extends Menu {
  private static final long serialVersionUID = 1L;
  private final LogisimMenuBar menubar;
  private final MyListener myListener = new MyListener();
  private final MyMenuListener myMenuListener = new MyMenuListener();
  private final MenuItemImpl addCircuit = new MenuItemImpl(this, LogisimMenuBar.ADD_CIRCUIT);
  private final MenuItemImpl addVhdl = new MenuItemImpl(this, LogisimMenuBar.ADD_VHDL);
  private final MenuItemImpl importVhdl = new MenuItemImpl(this, LogisimMenuBar.IMPORT_VHDL);
  private final MenuItemImpl addVerilog = new MenuItemImpl(this, LogisimMenuBar.ADD_VERILOG);
  private final MenuItemImpl importVerilog =
      new MenuItemImpl(this, LogisimMenuBar.IMPORT_VERILOG);
  private final JMenu loadLibrary = new JMenu();
  private final JMenu loadBuiltin = new JMenu();
  private final JMenuItem loadLogisim = new JMenuItem();
  private final JMenuItem loadJar = new JMenuItem();
  private final JMenuItem unload = new JMenuItem();
  private final MenuItemImpl moveUp = new MenuItemImpl(this, LogisimMenuBar.MOVE_CIRCUIT_UP);
  private final MenuItemImpl moveDown = new MenuItemImpl(this, LogisimMenuBar.MOVE_CIRCUIT_DOWN);
  private final MenuItemImpl remove = new MenuItemImpl(this, LogisimMenuBar.REMOVE_CIRCUIT);
  private final MenuItemImpl setAsMain = new MenuItemImpl(this, LogisimMenuBar.SET_MAIN_CIRCUIT);
  private final MenuItemImpl revertAppearance =
      new MenuItemImpl(this, LogisimMenuBar.REVERT_APPEARANCE);
  private final MenuItemImpl layout = new MenuItemImpl(this, LogisimMenuBar.EDIT_LAYOUT);
  private final MenuItemImpl appearance = new MenuItemImpl(this, LogisimMenuBar.EDIT_APPEARANCE);
  private final MenuItemImpl toggleLayoutAppearance =
      new MenuItemImpl(this, LogisimMenuBar.TOGGLE_APPEARANCE);
  private final MenuItemImpl analyze = new MenuItemImpl(this, LogisimMenuBar.ANALYZE_CIRCUIT);
  private final MenuItemImpl stats = new MenuItemImpl(this, LogisimMenuBar.CIRCUIT_STATS);
  private final MenuItemCheckImpl lockCircuit =
      new MenuItemCheckImpl(this, LogisimMenuBar.LOCK_CIRCUIT);
  private final MenuItemCheckImpl lockSelection =
      new MenuItemCheckImpl(this, LogisimMenuBar.LOCK_SELECTION);
  private final JMenuItem options = new JMenuItem();

  MenuProject(LogisimMenuBar menubar) {
    this.menubar = menubar;

    hotkeyUpdate();

    menubar.registerItem(LogisimMenuBar.ADD_CIRCUIT, addCircuit);
    menubar.registerItem(LogisimMenuBar.ADD_VHDL, addVhdl);
    menubar.registerItem(LogisimMenuBar.IMPORT_VHDL, importVhdl);
    menubar.registerItem(LogisimMenuBar.ADD_VERILOG, addVerilog);
    menubar.registerItem(LogisimMenuBar.IMPORT_VERILOG, importVerilog);
    loadBuiltin.addMenuListener(myMenuListener);
    loadLogisim.addActionListener(myListener);
    loadJar.addActionListener(myListener);
    unload.addActionListener(myListener);
    menubar.registerItem(LogisimMenuBar.MOVE_CIRCUIT_UP, moveUp);
    menubar.registerItem(LogisimMenuBar.MOVE_CIRCUIT_DOWN, moveDown);
    menubar.registerItem(LogisimMenuBar.SET_MAIN_CIRCUIT, setAsMain);
    menubar.registerItem(LogisimMenuBar.REMOVE_CIRCUIT, remove);
    menubar.registerItem(LogisimMenuBar.REVERT_APPEARANCE, revertAppearance);
    menubar.registerItem(LogisimMenuBar.EDIT_LAYOUT, layout);
    menubar.registerItem(LogisimMenuBar.EDIT_APPEARANCE, appearance);
    menubar.registerItem(LogisimMenuBar.TOGGLE_APPEARANCE, toggleLayoutAppearance);
    menubar.registerItem(LogisimMenuBar.ANALYZE_CIRCUIT, analyze);
    menubar.registerItem(LogisimMenuBar.CIRCUIT_STATS, stats);
    menubar.registerItem(LogisimMenuBar.LOCK_CIRCUIT, lockCircuit);
    menubar.registerItem(LogisimMenuBar.LOCK_SELECTION, lockSelection);
    // The check marks follow the circuit and selection on screen, read as the menu opens.
    addMenuListener(myMenuListener);
    options.addActionListener(myListener);

    loadLibrary.add(loadBuiltin);
    loadLibrary.add(loadLogisim);
    loadLibrary.add(loadJar);

    /* add myself to hotkey sync */
    AppPreferences.gui_sync_objects.add(this);

    add(addCircuit);
    add(addVhdl);
    add(importVhdl);
    add(addVerilog);
    add(importVerilog);
    add(loadLibrary);
    add(unload);
    addSeparator();
    add(moveUp);
    add(moveDown);
    add(setAsMain);
    add(remove);
    add(revertAppearance);
    addSeparator();
    add(layout);
    add(appearance);
    addSeparator();
    add(analyze);
    add(stats);
    addSeparator();
    add(lockCircuit);
    add(lockSelection);
    addSeparator();
    add(options);

    final var known = menubar.getSaveProject() != null;
    loadLibrary.setEnabled(known);
    loadBuiltin.setEnabled(known);
    loadLogisim.setEnabled(known);
    loadJar.setEnabled(known);
    unload.setEnabled(known);
    options.setEnabled(known);
    computeEnabled();
  }

  public void hotkeyUpdate() {
    addCircuit.setAccelerator(accelerator(AppPreferences.HOTKEY_PROJ_ADD_CIRCUIT));
    moveUp.setAccelerator(accelerator(AppPreferences.HOTKEY_PROJ_MOVE_UP));
    moveDown.setAccelerator(accelerator(AppPreferences.HOTKEY_PROJ_MOVE_DOWN));
    analyze.setAccelerator(accelerator(AppPreferences.HOTKEY_PROJ_ANALYZE));
    stats.setAccelerator(accelerator(AppPreferences.HOTKEY_PROJ_STATS));
    options.setAccelerator(accelerator(AppPreferences.HOTKEY_PROJ_OPTIONS));
  }

  private static KeyStroke accelerator(PrefMonitor<KeyStroke> hotkey) {
    return ((PrefMonitorKeyStroke) hotkey).getWithMask(0);
  }

  @Override
  protected void computeEnabled() {
    setEnabled(
        menubar.getSaveProject() != null
            || addCircuit.hasListeners()
            || addVhdl.hasListeners()
            || importVhdl.hasListeners()
            || addVerilog.hasListeners()
            || importVerilog.hasListeners()
            || moveUp.hasListeners()
            || moveDown.hasListeners()
            || setAsMain.hasListeners()
            || remove.hasListeners()
            || layout.hasListeners()
            || revertAppearance.hasListeners()
            || appearance.hasListeners()
            || analyze.hasListeners()
            || stats.hasListeners()
            || lockCircuit.hasListeners()
            || lockSelection.hasListeners());
    menubar.fireEnableChanged();
  }

  public void localeChanged() {
    setText(S.get("projectMenu"));
    addCircuit.setText(S.get("projectAddCircuitItem"));
    addVhdl.setText(S.get("projectAddVhdlItem"));
    importVhdl.setText(S.get("projectImportVhdlItem"));
    addVerilog.setText(S.get("projectAddVerilogItem"));
    importVerilog.setText(S.get("projectImportVerilogItem"));
    loadLibrary.setText(S.get("projectLoadLibraryItem"));
    loadBuiltin.setText(S.get("projectLoadBuiltinItem"));
    loadLogisim.setText(S.get("projectLoadLogisimItem"));
    loadJar.setText(S.get("projectLoadJarItem"));
    unload.setText(S.get("projectUnloadLibrariesItem"));
    moveUp.setText(S.get("projectMoveCircuitUpItem"));
    moveDown.setText(S.get("projectMoveCircuitDownItem"));
    setAsMain.setText(S.get("projectSetAsMainItem"));
    remove.setText(S.get("projectRemoveCircuitItem"));
    revertAppearance.setText(S.get("projectRevertAppearanceItem"));
    layout.setText(S.get("projectEditCircuitLayoutItem"));
    appearance.setText(S.get("projectEditCircuitAppearanceItem"));
    toggleLayoutAppearance.setText(S.get("projectToggleCircuitAppearanceItem"));
    analyze.setText(S.get("projectAnalyzeCircuitItem"));
    stats.setText(S.get("projectGetCircuitStatisticsItem"));
    lockCircuit.setText(S.get("projectLockCircuitItem"));
    lockSelection.setText(S.get("projectLockSelectionItem"));
    options.setText(S.get("projectOptionsItem"));
  }

  /** Ticks the lock items for the circuit on screen and what is selected in it. */
  private void refreshLockItems() {
    final var proj = menubar.getSaveProject();
    final var circuit = proj == null ? null : proj.getCurrentCircuit();
    final var inFile = circuit != null && proj.getLogisimFile().contains(circuit);
    lockCircuit.setSelected(inFile && circuit.isEditLocked());
    final var selected =
        inFile && proj.getSelection() != null ? proj.getSelection().getComponents() : null;
    final var anyLockable =
        selected != null && selected.stream().anyMatch(comp -> !(comp instanceof Wire));
    lockSelection.setSelected(anyLockable && EditLockAction.allLocked(circuit, selected));
    lockSelection.setEnabled(anyLockable);
  }

  private class MyListener implements ActionListener {
    @Override
    public void actionPerformed(ActionEvent event) {
      final var src = event.getSource();
      final var proj = menubar.getSaveProject();
      if (proj == null) {
        return;
      }
      if (src == loadLogisim) {
        ProjectLibraryActions.doLoadLogisimLibrary(proj);
      } else if (src == loadJar) {
        ProjectLibraryActions.doLoadJarLibrary(proj);
      } else if (src == unload) {
        ProjectLibraryActions.doUnloadLibraries(proj);
      } else if (src == options) {
        proj.getOptionsFrame().setVisible(true);
      }
    }
  }

  private class MyMenuListener implements MenuListener {
    @Override
    public void menuSelected(MenuEvent event) {
      if (event.getSource() == loadBuiltin) {
        ProjectLibraryActions.populateBuiltinLibraryMenu(loadBuiltin, menubar.getSaveProject());
      } else if (event.getSource() == MenuProject.this) {
        refreshLockItems();
      }
    }

    @Override
    public void menuDeselected(MenuEvent event) {}

    @Override
    public void menuCanceled(MenuEvent event) {}
  }
}

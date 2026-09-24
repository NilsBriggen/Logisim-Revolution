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

import com.cburch.logisim.gui.prefs.PreferencesFrame;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.prefs.PrefMonitor;
import com.cburch.logisim.prefs.PrefMonitorKeyStroke;
import com.cburch.logisim.proj.ProjectActions;
import com.cburch.logisim.proj.Projects;
import com.cburch.logisim.util.MacCompatibility;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JMenuItem;
import javax.swing.KeyStroke;

class MenuFile extends Menu implements ActionListener {
  private static final long serialVersionUID = 1L;
  private final LogisimMenuBar menubar;
  private final JMenuItem newi = new JMenuItem();
  private final JMenuItem merge = new JMenuItem();
  private final JMenuItem open = new JMenuItem();
  private final OpenRecent openRecent;
  private final JMenuItem close = new JMenuItem();
  private final JMenuItem save = new JMenuItem();
  private final JMenuItem saveAs = new JMenuItem();
  private final JMenuItem exportProj = new JMenuItem();
  private final JMenuItem extractRunProj = new JMenuItem();
  private final MenuItemImpl print = new MenuItemImpl(this, LogisimMenuBar.PRINT);
  private final MenuItemImpl exportImage = new MenuItemImpl(this, LogisimMenuBar.EXPORT_IMAGE);
  private final JMenuItem prefs = new JMenuItem();
  private final JMenuItem quit = new JMenuItem();

  public MenuFile(LogisimMenuBar menubar) {
    this.menubar = menubar;
    openRecent = new OpenRecent(menubar);

    hotkeyUpdate();

    /* add myself to hotkey sync */
    AppPreferences.gui_sync_objects.add(this);

    add(newi);
    add(merge);
    add(open);
    add(openRecent);
    addSeparator();
    add(close);
    add(save);
    add(saveAs);
    addSeparator();
    add(extractRunProj);
    add(exportProj);
    addSeparator();
    add(exportImage);
    add(print);
    if (!MacCompatibility.isPreferencesAutomaticallyPresent()) {
      addSeparator();
      add(prefs);
    }
    if (!MacCompatibility.isQuitAutomaticallyPresent()) {
      addSeparator();
      add(quit);
    }

    final var proj = menubar.getSaveProject();
    newi.addActionListener(this);
    open.addActionListener(this);
    if (proj == null) {
      merge.setEnabled(false);
      close.setEnabled(false);
      save.setEnabled(false);
      saveAs.setEnabled(false);
      exportProj.setEnabled(false);
      extractRunProj.setEnabled(false);
    } else {
      merge.addActionListener(this);
      close.addActionListener(this);
      save.addActionListener(this);
      saveAs.addActionListener(this);
      exportProj.addActionListener(this);
      extractRunProj.addActionListener(this);
    }
    menubar.registerItem(LogisimMenuBar.EXPORT_IMAGE, exportImage);
    menubar.registerItem(LogisimMenuBar.PRINT, print);
    prefs.addActionListener(this);
    quit.addActionListener(this);
  }

  public void hotkeyUpdate() {
    newi.setAccelerator(accelerator(AppPreferences.HOTKEY_FILE_NEW));
    merge.setAccelerator(accelerator(AppPreferences.HOTKEY_FILE_MERGE));
    open.setAccelerator(accelerator(AppPreferences.HOTKEY_FILE_OPEN));
    // Closing the project closes its window, so it shares the window-close shortcut.
    close.setAccelerator(accelerator(AppPreferences.HOTKEY_WINDOW_CLOSE));
    save.setAccelerator(accelerator(AppPreferences.HOTKEY_FILE_SAVE));
    saveAs.setAccelerator(accelerator(AppPreferences.HOTKEY_FILE_SAVE_AS));
    exportProj.setAccelerator(accelerator(AppPreferences.HOTKEY_FILE_EXPORT));
    print.setAccelerator(accelerator(AppPreferences.HOTKEY_FILE_PRINT));
    prefs.setAccelerator(accelerator(AppPreferences.HOTKEY_FILE_PREFERENCES));
    quit.setAccelerator(accelerator(AppPreferences.HOTKEY_FILE_QUIT));
  }

  private static KeyStroke accelerator(PrefMonitor<KeyStroke> hotkey) {
    return ((PrefMonitorKeyStroke) hotkey).getWithMask(0);
  }

  @Override
  public void actionPerformed(ActionEvent e) {
    final var src = e.getSource();
    final var proj = menubar.getSaveProject();
    final var baseProj = menubar.getBaseProject();
    if (src == newi) {
      ProjectActions.doNew(baseProj);
    } else if (src == merge) {
      ProjectActions.doMerge(baseProj == null ? null : baseProj.getFrame().getCanvas(), baseProj);
    } else if (src == open) {
      final var newProj =
          ProjectActions.doOpen(
              baseProj == null ? null : baseProj.getFrame().getCanvas(), baseProj);
      if (newProj != null
          && proj != null
          && !proj.isFileDirty()
          && proj.getLogisimFile().getLoader().getMainFile() == null) {
        proj.getFrame().dispose();
      }
    } else if (src == close && proj != null) {
      final var frame = proj.getFrame();
      if (frame.confirmClose()) {
        // Get the list of open projects
        final var projectList = Projects.getOpenProjects();
        if (projectList.size() == 1) {
          // Since we have a single window open, before closing the
          // current project open a new empty one
          ProjectActions.doNew(proj);
        }

        // Close the current project
        frame.dispose();
      }
    } else if (src == prefs) {
      PreferencesFrame.showPreferences();
    } else if (src == quit) {
      ProjectActions.doQuit();
    } else if (proj != null) {
      if (src == save) {
        ProjectActions.doSave(proj);
      } else if (src == saveAs) {
        ProjectActions.doSaveAs(proj);
      } else if (src == exportProj) {
        ProjectActions.doExportProject(proj);
      } else if (src == extractRunProj) {
        ProjectActions.doExtractAndRunProject(proj);
      }
    }
  }

  @Override
  protected void computeEnabled() {
    setEnabled(true);
    menubar.fireEnableChanged();
  }

  public void localeChanged() {
    this.setText(S.get("fileMenu"));
    newi.setText(S.get("fileNewItem"));
    merge.setText(S.get("fileMergeItem"));
    open.setText(S.get("fileOpenItem"));
    openRecent.localeChanged();
    close.setText(S.get("fileCloseItem"));
    save.setText(S.get("fileSaveItem"));
    saveAs.setText(S.get("fileSaveAsItem"));
    exportProj.setText(S.get("fileExportProject"));
    extractRunProj.setText(S.get("fileExtractRunProject"));
    exportImage.setText(S.get("fileExportImageItem"));
    print.setText(S.get("filePrintItem"));
    prefs.setText(S.get("filePreferencesItem"));
    quit.setText(S.get("fileQuitItem"));
  }
}

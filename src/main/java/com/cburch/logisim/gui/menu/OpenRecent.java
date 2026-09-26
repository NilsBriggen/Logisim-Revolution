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

import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.ProjectActions;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

class OpenRecent extends JMenu implements PropertyChangeListener {
  private static final long serialVersionUID = 1L;
  private static final int MAX_ITEM_LENGTH = 50;
  private final LogisimMenuBar menubar;
  private final List<RecentItem> recentItems;

  OpenRecent(LogisimMenuBar menubar) {
    this.menubar = menubar;
    this.recentItems = new ArrayList<>();
    AppPreferences.addPropertyChangeListener(AppPreferences.RECENT_PROJECTS, this);
    renewItems();
  }

  /** The full path of a recent file, for its tooltip. */
  private static String getFullPath(File file) {
    try {
      return file.getCanonicalPath();
    } catch (IOException e) {
      return file.toString();
    }
  }

  /**
   * The menu text of a recent file: its name first, so it can be found at a glance, then the
   * folder it is in, shortened from the left when long.
   */
  static String getFileText(File file) {
    if (file == null) return S.get("fileOpenRecentNoChoices");
    final var full = new File(getFullPath(file));
    final var parent = full.getParent();
    if (parent == null) return full.getName();
    var dir = parent;
    if (dir.length() > MAX_ITEM_LENGTH) {
      dir = dir.substring(dir.length() - MAX_ITEM_LENGTH + 1);
      final var splitLoc = dir.indexOf(File.separatorChar);
      if (splitLoc >= 0) dir = dir.substring(splitLoc);
      dir = "\u2026" + dir;
    }
    return S.get("fileOpenRecentEntry", full.getName(), dir);
  }

  void localeChanged() {
    setText(S.get("fileOpenRecentItem"));
    for (final var item : recentItems) {
      item.setText(getFileText(item.file));
    }
  }

  @Override
  public void propertyChange(PropertyChangeEvent event) {
    if (event.getPropertyName().equals(AppPreferences.RECENT_PROJECTS)) {
      renewItems();
    }
  }

  private void renewItems() {
    for (var index = recentItems.size() - 1; index >= 0; index--) {
      remove(recentItems.get(index));
    }
    recentItems.clear();

    final var files = AppPreferences.getRecentFiles();
    if (files.isEmpty()) {
      recentItems.add(new RecentItem(null));
    } else {
      for (final var file : files) {
        recentItems.add(new RecentItem(file));
      }
    }

    for (RecentItem item : recentItems) {
      add(item);
    }
  }

  private class RecentItem extends JMenuItem implements ActionListener {
    private static final long serialVersionUID = 1L;
    private final File file;

    RecentItem(File file) {
      super(getFileText(file));
      this.file = file;
      if (file != null) setToolTipText(getFullPath(file));
      setEnabled(file != null);
      addActionListener(this);
    }

    @Override
    public void actionPerformed(ActionEvent event) {
      final var baseProj = menubar.getBaseProject();
      final var parent =
          (baseProj != null) ? baseProj.getFrame().getCanvas() : menubar.getParentFrame();
      // TODO: and has no subwindows or dialogs open?
      ProjectActions.doOpenReplacingBlank(parent, baseProj, menubar.getSaveProject(), file);
    }
  }
}

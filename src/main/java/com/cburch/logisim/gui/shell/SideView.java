/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell;

import com.cburch.logisim.gui.theme.AppIcons;
import javax.swing.JComponent;

/**
 * One of the panels that can occupy the side of the window: the circuits of the project, the
 * component library, the running simulation.
 *
 * <p>Only one is shown at a time, chosen from the activity bar, because they are all answers to
 * "what am I looking at" and stacking them would just make each one shorter.
 */
public interface SideView {

  /** Stable name used to remember which view was showing. Never translated. */
  String id();

  /** The heading shown above the view, in the user's language. */
  String title();

  /** The icon for this view's button in the activity bar. */
  AppIcons.Id icon();

  /** The view itself. Built once and kept. */
  JComponent component();

  /** Buttons for the view's own actions, shown in its heading, or {@code null} for none. */
  default JComponent headerActions() {
    return null;
  }
}

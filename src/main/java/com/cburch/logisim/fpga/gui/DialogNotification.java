/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.fpga.gui;

import static com.cburch.logisim.fpga.Strings.S;

import com.cburch.logisim.gui.generic.OptionPane;
import java.awt.Component;

/**
 * Reports a problem found while working with a board or a pin mapping.
 *
 * <p>This used to build its own modal out of a grid bag, decide which icon to draw by comparing
 * the caller's string against the English literal "Warning", put that same untranslated string in
 * the title bar, and force itself always-on-top. It now says the same thing through the shared
 * message dialog, so it looks and behaves like every other message in the program.
 */
public final class DialogNotification {

  /** How bad the news is. Was a string compared against "Warning", which no translation matched. */
  public enum Severity {
    WARNING,
    ERROR
  }

  private DialogNotification() {
    throw new UnsupportedOperationException("Utility class, do not instantiate.");
  }

  public static void showDialogNotification(Component parent, Severity severity, String message) {
    showDialogNotification(
        parent,
        severity,
        S.get(severity == Severity.WARNING ? "FpgaNotificationWarning" : "FpgaNotificationError"),
        message);
  }

  /** The same, for the one caller that has a more specific heading of its own. */
  public static void showDialogNotification(
      Component parent, Severity severity, String title, String message) {
    if (severity == Severity.WARNING) {
      OptionPane.showMessageDialog(parent, message, title, OptionPane.WARNING_MESSAGE);
    } else {
      OptionPane.showError(parent, title, message);
    }
  }
}

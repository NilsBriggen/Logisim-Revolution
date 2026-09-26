/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.generic;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.Main;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.StringUtil;
import com.cburch.logisim.util.UiFonts;
import com.cburch.logisim.util.UiScale;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.KeyboardFocusManager;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import javax.swing.text.JTextComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Provides a unified interface for displaying various types of dialogs.
 *
 * This class wraps the functionality of {@link JOptionPane} and provides additional logging capabilities when the
 * application runs in a non-GUI (tty) mode. In GUI mode, dialogs are shown as they would be using the
 * {@link JOptionPane}, while in non-GUI mode, relevant messages are logged instead of displaying a dialog.
 */
public class OptionPane {
  public static final int YES_NO_OPTION = JOptionPane.YES_NO_OPTION;
  public static final int YES_NO_CANCEL_OPTION = JOptionPane.YES_NO_CANCEL_OPTION;
  public static final int YES_OPTION = JOptionPane.YES_OPTION;
  public static final int CANCEL_OPTION = JOptionPane.CANCEL_OPTION;
  public static final int OK_CANCEL_OPTION = JOptionPane.OK_CANCEL_OPTION;
  public static final int OK_OPTION = JOptionPane.OK_OPTION;

  public static final int ERROR_MESSAGE = JOptionPane.ERROR_MESSAGE;
  public static final int WARNING_MESSAGE = JOptionPane.WARNING_MESSAGE;
  public static final int QUESTION_MESSAGE = JOptionPane.QUESTION_MESSAGE;
  public static final int INFORMATION_MESSAGE = JOptionPane.INFORMATION_MESSAGE;
  public static final int PLAIN_MESSAGE = JOptionPane.PLAIN_MESSAGE;

  public static final int CLOSED_OPTION = JOptionPane.CLOSED_OPTION;

  static final Logger logger = LoggerFactory.getLogger(OptionPane.class);

  /** Width, in unscaled pixels, at which a long error message is wrapped. */
  private static final int ERROR_MESSAGE_WIDTH = 380;

  /** Size, in unscaled pixels, of the collapsible technical detail area. */
  private static final int DETAILS_WIDTH = 460;

  private static final int DETAILS_HEIGHT = 200;

  /**
   * Reports an error to the user.
   *
   * <p>Prefer this over {@link #showMessageDialog(Component, Object)} for failures: that overload
   * produces a dialog with no title and no icon, so a bare exception message arrives with no
   * indication of what failed.
   *
   * @param parentComponent The component the dialog belongs to; may be null.
   * @param title           A short description of what failed.
   * @param message         What went wrong, in terms the user can act on.
   */
  public static void showError(Component parentComponent, String title, String message) {
    showError(parentComponent, title, message, null);
  }

  /**
   * Reports an error to the user, with the underlying exception available on request.
   *
   * <p>The stack trace is hidden behind a "Details" button rather than shown inline: it is useful
   * in a bug report but is noise to someone building a circuit.
   *
   * @param parentComponent The component the dialog belongs to; may be null.
   * @param title           A short description of what failed.
   * @param message         What went wrong, in terms the user can act on.
   * @param cause           The underlying failure, or null if there is none to show.
   */
  public static void showError(
      Component parentComponent, String title, String message, Throwable cause) {
    if (!Main.hasGui()) {
      if (cause == null) {
        logger.error("{}: {}", title, message);
      } else {
        logger.error("{}: {}", title, message, cause);
      }
      return;
    }

    final var parent = resolveParent(parentComponent);
    final var body = (cause == null) ? messageLabel(message) : detailPanel(message, cause);
    JOptionPane.showMessageDialog(parent, body, title, ERROR_MESSAGE);
  }

  /** Wraps a message so a long line does not stretch the dialog across the screen. */
  private static JLabel messageLabel(String message) {
    final var label =
        new JLabel(
            "<html><body style='width:"
                + UiScale.scaled(ERROR_MESSAGE_WIDTH)
                + "px'>"
                + escapeHtml(message)
                + "</body></html>");
    label.setBorder(Spacing.border(Spacing.XS));
    return label;
  }

  /** Builds the message with a "Details" button revealing the stack trace. */
  private static JPanel detailPanel(String message, Throwable cause) {
    final var panel = new JPanel(new BorderLayout(0, Spacing.sm()));
    panel.add(messageLabel(message), BorderLayout.NORTH);

    final var trace = new JTextArea(stackTraceOf(cause));
    trace.setEditable(false);
    trace.setFont(UiFonts.mono());
    trace.setCaretPosition(0);

    final var scroller = new JScrollPane(trace);
    scroller.setPreferredSize(
        new Dimension(UiScale.scaled(DETAILS_WIDTH), UiScale.scaled(DETAILS_HEIGHT)));
    scroller.setVisible(false);

    final var toggle = new JButton(S.get("errorDetailsShow"));
    toggle.addActionListener(
        event -> {
          final var showing = !scroller.isVisible();
          scroller.setVisible(showing);
          toggle.setText(S.get(showing ? "errorDetailsHide" : "errorDetailsShow"));
          final var window = SwingUtilities.getWindowAncestor(panel);
          if (window != null) window.pack();
        });

    // A bug report needs the text; selecting it inside the collapsed area is awkward.
    final var copy = new JButton(S.get("errorDetailsCopy"));
    copy.addActionListener(
        event ->
            Toolkit.getDefaultToolkit()
                .getSystemClipboard()
                .setContents(new StringSelection(message + "\n\n" + trace.getText()), null));

    final var toggleRow = new JPanel(new FlowLayout(FlowLayout.LEADING, Spacing.xs(), 0));
    toggleRow.add(toggle);
    toggleRow.add(copy);

    final var south = new JPanel(new BorderLayout(0, Spacing.xs()));
    south.add(toggleRow, BorderLayout.NORTH);
    south.add(scroller, BorderLayout.CENTER);
    panel.add(south, BorderLayout.CENTER);
    return panel;
  }

  /**
   * Chooses the window a dialog should be attached to.
   *
   * <p>Applied by every method here, so a call site that passes {@code null} still gets a dialog
   * attached to the focused window rather than one that can hide behind the application.
   *
   * <p>A dialog with no parent can open behind the main window, where the user never sees it while
   * the application appears frozen. Fall back to whichever window currently has focus.
   */
  static Component resolveParent(Component parentComponent) {
    if (parentComponent != null) {
      final var ancestor = SwingUtilities.getWindowAncestor(parentComponent);
      if (ancestor != null) return ancestor;
      return parentComponent;
    }
    return KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow();
  }

  /** Renders a throwable and its causes as text, for the detail area. */
  static String stackTraceOf(Throwable cause) {
    if (cause == null) return "";
    final var writer = new StringWriter();
    try (final var printer = new PrintWriter(writer)) {
      cause.printStackTrace(printer);
    }
    return writer.toString();
  }

  /** Escapes the few characters that would otherwise be read as markup by the HTML label. */
  public static String escapeHtml(String text) {
    if (text == null) return "";
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }

  /**
   * Displays a message dialog with the specified message.
   *
   * @param parentComponent The parent component of the dialog.
   * @param message         The message to be displayed.
   */
  public static void showMessageDialog(Component parentComponent, Object message) {
    if (Main.hasGui()) {
      JOptionPane.showMessageDialog(resolveParent(parentComponent), message);
    } else {
      logHeadless(null, message, INFORMATION_MESSAGE);
    }
  }

  /**
   * Displays a message dialog with the specified message, title, and message type.
   *
   * @param parentComponent The parent component of the dialog.
   * @param message         The message object to be displayed (String, JPanel...)
   * @param title           The title of the dialog.
   * @param messageType     The type of message to be displayed, e.g., ERROR_MESSAGE.
   */
  public static void showMessageDialog(
      Component parentComponent, Object message, String title, int messageType) {
    if (Main.hasGui()) {
      JOptionPane.showMessageDialog(resolveParent(parentComponent), message, title, messageType);
    } else {
      logHeadless(title, message, messageType);
    }
  }

  /**
   * Reports what a dialog would have shown when there is no GUI, so that a command-line run never
   * drops a message silently. Errors and warnings go to the log (standard error).
   */
  static void logHeadless(String title, Object message, int messageType) {
    final var text = messageText(message);
    if (text.isEmpty()) return;
    final var logMessage = StringUtil.isNullOrEmpty(title) ? text : title + ": " + text;
    switch (messageType) {
      case ERROR_MESSAGE -> logger.error(logMessage);
      case WARNING_MESSAGE -> logger.warn(logMessage);
      default -> logger.info(logMessage);
    }
  }

  /**
   * Extracts the readable text of a dialog message, which may be a plain string or a Swing
   * component such as a scrollable text area.
   */
  static String messageText(Object message) {
    if (message == null) return "";
    if (message instanceof String text) return text;
    if (message instanceof JTextComponent text) return text.getText();
    if (message instanceof JLabel label) return label.getText() == null ? "" : label.getText();
    if (message instanceof JScrollPane scroll) return messageText(scroll.getViewport().getView());
    if (message instanceof Container container) {
      final var parts = new ArrayList<String>();
      for (final var child : container.getComponents()) {
        final var text = messageText(child);
        if (!text.isEmpty()) parts.add(text);
      }
      return String.join("\n", parts);
    }
    if (message instanceof Object[] array) {
      final var parts = new ArrayList<String>();
      for (final var item : array) {
        final var text = messageText(item);
        if (!text.isEmpty()) parts.add(text);
      }
      return String.join("\n", parts);
    }
    return message.toString();
  }

  /**
   * Displays a confirmation dialog with the specified message and title.
   *
   * @param parentComponent The parent component of the dialog.
   * @param message         The message to be displayed.
   * @param title           The title of the dialog.
   * @param optionType      Specifies the set of options available on the dialog.
   *
   * @return The option chosen by the user, or CANCEL_OPTION if the GUI is not available.
   */
  public static int showConfirmDialog(Component parentComponent, Object message, String title, int optionType) {
    return Main.hasGui()
        ? JOptionPane.showConfirmDialog(resolveParent(parentComponent), message, title, optionType)
        : CANCEL_OPTION;
  }

  /**
   * Displays a confirmation dialog with the specified message, title, option type, and message type.
   *
   * @param parentComponent The parent component of the dialog.
   * @param message         The message to be displayed.
   * @param title           The title of the dialog.
   * @param optionType      Specifies the set of options available on the dialog.
   * @param messageType     The type of message to be displayed.
   *
   * @return The option chosen by the user, or CANCEL_OPTION if the GUI is not available.
   */
  public static int showConfirmDialog(Component parentComponent, Object message, String title,
                                      int optionType, int messageType) {
    return Main.hasGui()
        ? JOptionPane.showConfirmDialog(resolveParent(parentComponent), message, title, optionType, messageType)
        : CANCEL_OPTION;
  }

  /**
   * Displays an input dialog with the specified message.
   *
   * @param parentComponent The parent component of the dialog.
   * @param message The message to be displayed.
   *
   * @return The input provided by the user, or null if the GUI is not available.
   */
  public static String showInputDialog(Component parentComponent, Object message) {
    return Main.hasGui()
            ? JOptionPane.showInputDialog(resolveParent(parentComponent), message)
            : null;
  }

  /**
   * Displays an input dialog with the specified message, title, and message type.
   *
   * @param parentComponent The parent component of the dialog.
   * @param message         The message to be displayed.
   * @param title           The title of the dialog.
   * @param messageType     The type of message to be displayed.
   *
   * @return The input provided by the user, or null if the GUI is not available.
   */
  public static String showInputDialog(Component parentComponent, Object message, String title, int messageType) {
    return Main.hasGui()
        ? JOptionPane.showInputDialog(resolveParent(parentComponent), message, title, messageType)
        : null;
  }

  /**
   * Displays an input dialog with the specified message, title, message type, icon, selection values, and initial
   * selection value.
   *
   * @param parentComponent       The parent component of the dialog.
   * @param message               The message to be displayed.
   * @param title                 The title of the dialog.
   * @param messageType           The type of message to be displayed.
   * @param icon                  The icon to be displayed.
   * @param selectionValues       The array of values the user can select from.
   * @param initialSelectionValue The initial value selected.
   *
   * @return The input provided by the user, or null if the GUI is not available.
   */
  public static Object showInputDialog(Component parentComponent,
                                       Object message,
                                       String title,
                                       int messageType,
                                       Icon icon,
                                       Object[] selectionValues,
                                       Object initialSelectionValue) {
    return Main.hasGui()
        ? JOptionPane.showInputDialog(resolveParent(parentComponent), message, title, messageType,
            icon, selectionValues, initialSelectionValue)
        : null;
  }

  /**
   * Displays an option dialog with the specified message, title, option type, message type, icon, options, and
   * initial value.
   *
   * @param parentComponent The parent component of the dialog.
   * @param message         The message to be displayed.
   * @param title           The title of the dialog.
   * @param optionType      Specifies the set of options available on the dialog.
   * @param messageType     The type of message to be displayed.
   * @param icon            The icon to be displayed.
   * @param options         The array of options the user can select from.
   * @param initialValue    The initial value selected.
   *
   * @return The option chosen by the user, or CLOSED_OPTION if the GUI is not available. Without
   *     a GUI the message is logged instead, so that errors reported this way are not lost.
   */
  public static int showOptionDialog(Component parentComponent,
                                     Object message,
                                     String title,
                                     int optionType,
                                     int messageType,
                                     Icon icon,
                                     Object[] options,
                                     Object initialValue) {
    if (Main.hasGui()) {
      return JOptionPane.showOptionDialog(resolveParent(parentComponent), message, title,
          optionType, messageType, icon, options, initialValue);
    }
    logHeadless(title, message, messageType);
    return CLOSED_OPTION;
  }

  public static Frame getFrameForComponent(Component parentComponent) {
    return Main.hasGui()
            ? JOptionPane.getFrameForComponent(parentComponent)
            : null;
  }
}

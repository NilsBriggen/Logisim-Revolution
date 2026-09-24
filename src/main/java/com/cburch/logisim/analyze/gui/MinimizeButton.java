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
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.util.UiFonts;
import java.awt.BorderLayout;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultCaret;

public class MinimizeButton extends JButton {
  private static final int MAX_PROGRESS_CHARACTERS = 100_000;
  private final JFrame parent;
  private final AnalyzerModel model;
  private final int format;

  public MinimizeButton(JFrame parent, AnalyzerModel model, int format) {
    this.parent = parent;
    this.model = model;
    this.format = format;
    addActionListener(event -> doOptimize());
  }

  void doOptimize() {
    if (!SwingUtilities.isEventDispatchThread()) {
      SwingUtilities.invokeLater(this::doOptimize);
      return;
    }
    final var choice = OptionPane.showConfirmDialog(parent, S.get("OptimizeLongTimeWarning"),
        S.get("minimizeFunctionTitle"), OptionPane.YES_NO_OPTION);
    if (choice != OptionPane.YES_OPTION) return;
    final var info = new JTextArea(20, 80);
    info.setEditable(false);
    info.setFont(UiFonts.mono());
    ((DefaultCaret) info.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE);
    final var dialog = new JDialog(parent, S.get("minimizeFunctionTitle"), true);
    dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
    final var close = new JButton(com.cburch.logisim.util.Strings.S.get("dlogCancelButton"));
    dialog.add(new JScrollPane(info), BorderLayout.CENTER);
    dialog.add(close, BorderLayout.SOUTH);

    final var worker = new OptimizationWorker(model.getOutputExpressions(), format,
        text -> {
          // Keep long optimizations from accumulating an unbounded Swing document.
          final var retained = text.length() > MAX_PROGRESS_CHARACTERS
              ? text.substring(text.length() - MAX_PROGRESS_CHARACTERS)
              : text;
          final var excess =
              info.getDocument().getLength() + retained.length() - MAX_PROGRESS_CHARACTERS;
          if (excess > 0) info.replaceRange("", 0, excess);
          info.append(retained);
        },
        completion -> {
          setEnabled(true);
          if (completion.status() == OptimizationWorker.Status.APPLIED) {
            close.setText(S.get("minimizeDone"));
          } else {
            dialog.dispose();
            if (completion.status() == OptimizationWorker.Status.FAILED) {
              OptionPane.showMessageDialog(parent, S.get("minimizeFailed"),
                  S.get("minimizeFunctionTitle"), OptionPane.ERROR_MESSAGE);
            } else if (completion.status() == OptimizationWorker.Status.STALE) {
              OptionPane.showMessageDialog(parent, S.get("minimizeStale"),
                  S.get("minimizeFunctionTitle"), OptionPane.WARNING_MESSAGE);
            }
          }
        });
    final Runnable dismiss = () -> {
      worker.requestCancellation();
      dialog.dispose();
    };
    close.addActionListener(event -> dismiss.run());
    dialog.addWindowListener(new WindowAdapter() {
      @Override
      public void windowClosing(WindowEvent event) {
        dismiss.run();
      }
    });
    dialog.getRootPane().registerKeyboardAction(event -> dismiss.run(),
        KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);
    dialog.getRootPane().setDefaultButton(close);
    dialog.pack();
    dialog.setLocationRelativeTo(parent);
    setEnabled(false);
    worker.execute();
    // The modal nested event loop keeps Cancel, Escape, close and progress responsive on the EDT.
    dialog.setVisible(true);
  }
}

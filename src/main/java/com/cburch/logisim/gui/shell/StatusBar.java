/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell;

import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.UiFonts;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The strip along the bottom of the window reporting what the simulation is doing and where the
 * pointer is.
 *
 * <p>These were painted onto the canvas itself: the tick rate in 28-point monospace over the
 * user's circuit, and errors in the middle of the drawing. A status bar keeps them legible without
 * covering the work.
 */
public class StatusBar extends JPanel {

  private static final long serialVersionUID = 1L;

  private final JLabel circuitLabel = new JLabel();
  private final JLabel coordsLabel = new JLabel();
  private final JLabel messageLabel = new JLabel();
  private final JLabel simulationLabel = new JLabel();
  private final JLabel tickRateLabel = new JLabel();
  private final JLabel zoomLabel = new JLabel();

  public StatusBar() {
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
    setBorder(
        javax.swing.BorderFactory.createEmptyBorder(
            Spacing.xs(), Spacing.sm(), Spacing.xs(), Spacing.sm()));
    setOpaque(true);

    add(circuitLabel);
    add(gap());
    add(coordsLabel);
    add(gap());
    add(messageLabel);
    add(Box.createHorizontalGlue());
    add(simulationLabel);
    add(gap());
    add(tickRateLabel);
    add(gap());
    add(zoomLabel);

    applyTheme();
    Theme.addListener(this::applyTheme);
  }

  private javax.swing.Box.Filler gap() {
    final var width = Spacing.md();
    return (javax.swing.Box.Filler)
        Box.createRigidArea(new Dimension(width, 1));
  }

  private void applyTheme() {
    setBackground(Tokens.statusBarBackground());
    final var font = UiFonts.small();
    for (final var label :
        new JLabel[] {
          circuitLabel, coordsLabel, messageLabel, simulationLabel, tickRateLabel, zoomLabel
        }) {
      label.setFont(font);
      label.setForeground(Tokens.statusBarForeground());
    }
    messageLabel.setForeground(Tokens.mutedForeground());
  }

  /** The name of the circuit being edited. */
  public void setCircuitName(String name) {
    circuitLabel.setText(name == null ? "" : name);
  }

  /** The pointer's position on the canvas, in circuit coordinates. */
  public void setCoordinates(String text) {
    coordsLabel.setText(text == null ? "" : text);
  }

  /**
   * A short remark about what just happened, such as why the simulation stopped.
   *
   * @param error whether it reports a failure, which is shown in the error colour
   */
  public void setMessage(String text, boolean error) {
    messageLabel.setText(text == null ? "" : text);
    messageLabel.setForeground(error ? Tokens.error() : Tokens.mutedForeground());
  }

  public void setSimulationState(String text) {
    simulationLabel.setText(text == null ? "" : text);
  }

  public void setTickRate(String text) {
    tickRateLabel.setText(text == null ? "" : text);
  }

  public void setZoom(String text) {
    zoomLabel.setText(text == null ? "" : text);
  }

  /** Makes the zoom reading clickable, so it can offer the zoom levels. */
  public void setZoomAction(Runnable action) {
    zoomLabel.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
    zoomLabel.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent event) {
            action.run();
          }
        });
  }

  @Override
  public Dimension getMaximumSize() {
    return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
  }
}

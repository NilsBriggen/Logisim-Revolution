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
import java.awt.Insets;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
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

  /** The chain from the top-level circuit down to the one on screen. */
  private final JPanel breadcrumb = new JPanel();

  private final JLabel circuitLabel = new JLabel();
  private final JLabel coordsLabel = new JLabel();
  private final JLabel messageLabel = new JLabel();
  private final JLabel simulationLabel = new JLabel();
  private final JLabel tickRateLabel = new JLabel();
  private final JButton zoomButton = statusButton("");
  private Runnable zoomAction;
  private boolean messageError;

  public StatusBar() {
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
    setBorder(
        javax.swing.BorderFactory.createEmptyBorder(
            Spacing.xs(), Spacing.sm(), Spacing.xs(), Spacing.sm()));
    setOpaque(true);

    breadcrumb.setLayout(new BoxLayout(breadcrumb, BoxLayout.X_AXIS));
    breadcrumb.setOpaque(false);
    add(breadcrumb);
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
    add(zoomButton);
    zoomButton.setFocusable(false);
    zoomButton.addActionListener(event -> {
      if (zoomAction != null) zoomAction.run();
    });

    applyTheme();
    Theme.addListener(this, this::applyTheme);
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
          circuitLabel, coordsLabel, messageLabel, simulationLabel, tickRateLabel
        }) {
      label.setFont(font);
      label.setForeground(Tokens.statusBarForeground());
    }
    zoomButton.setFont(font);
    zoomButton.setForeground(Tokens.statusBarForeground());
    zoomButton.setMargin(new Insets(0, Spacing.XS, 0, Spacing.XS));
    messageLabel.setForeground(messageError ? Tokens.error() : Tokens.mutedForeground());
    for (final var component : breadcrumb.getComponents()) {
      component.setFont(font);
      if (component instanceof JButton button) {
        button.setForeground(Tokens.accent());
        button.setMargin(new Insets(0, Spacing.XS, 0, Spacing.XS));
      } else {
        component.setForeground(Tokens.mutedForeground());
      }
    }
    revalidate();
    repaint();
  }

  /** The name of the circuit being edited. */
  public void setCircuitName(String name) {
    circuitLabel.setText(name == null ? "" : name);
  }

  /**
   * The chain of circuits the user has descended through, each one clickable.
   *
   * <p>Descending into a subcircuit used to show its name and nothing else, identical to what is
   * shown when that circuit is opened on its own. Nothing said the user was inside a particular
   * instance three levels down, and the only way back up was a submenu of the Simulate menu.
   *
   * @param trail the names from the top down to, but not including, the circuit on screen
   * @param ascend called with a position in {@code trail} to go back up to it
   */
  public void setCircuitTrail(java.util.List<String> trail, java.util.function.IntConsumer ascend) {
    breadcrumb.removeAll();
    if (trail != null) {
      for (var index = 0; index < trail.size(); index++) {
        final var step = index;
        breadcrumb.add(crumb(trail.get(index), () -> ascend.accept(step)));
        breadcrumb.add(separator());
      }
    }
    breadcrumb.setVisible(breadcrumb.getComponentCount() > 0);
    breadcrumb.revalidate();
    breadcrumb.repaint();
  }

  private JButton crumb(String name, Runnable onClick) {
    final var button = statusButton(name);
    button.setForeground(Tokens.accent());
    button.setToolTipText(name);
    button.addActionListener(event -> onClick.run());
    return button;
  }

  private static JButton statusButton(String text) {
    final var button = new JButton(text);
    button.putClientProperty("JButton.buttonType", "toolBarButton");
    button.setFont(UiFonts.small());
    button.setMargin(new Insets(0, Spacing.XS, 0, Spacing.XS));
    button.setOpaque(false);
    button.setContentAreaFilled(false);
    button.setFocusPainted(true);
    return button;
  }

  private JLabel separator() {
    final var chevron = new JLabel("\u203a");
    chevron.setFont(UiFonts.small());
    chevron.setForeground(Tokens.mutedForeground());
    chevron.setBorder(
        javax.swing.BorderFactory.createEmptyBorder(0, Spacing.xs(), 0, Spacing.xs()));
    return chevron;
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
    messageError = error;
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
    zoomButton.setText(text == null ? "" : text);
  }

  /** Makes the zoom reading clickable, so it can offer the zoom levels. */
  public void setZoomAction(Runnable action) {
    zoomAction = action;
    zoomButton.setFocusable(action != null);
  }

  @Override
  public Dimension getMaximumSize() {
    return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
  }
}

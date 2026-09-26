/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.gui.theme.AppIcons;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.UiFonts;
import com.cburch.logisim.util.UiScale;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.util.function.IntConsumer;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.SwingConstants;

/**
 * Floats over the bottom of the canvas while the circuit has problems, and leads to them.
 *
 * <p>"Incompatible widths" and "Oscillation apparent" used to be painted onto the canvas as bare
 * text: nothing said where the problem was, how many there were, or what to do. The pill counts
 * them, steps from one to the next, links to the help page that explains the wire colours, and
 * offers a reset once an oscillation has stopped the simulation.
 *
 * <p>It only presents; what a problem is and how to show it belong to whoever drives it.
 */
public class ProblemsPill extends JPanel {

  private static final long serialVersionUID = 1L;

  /** Corner radius, unscaled; the same as the zoom pill beside it. */
  private static final int ARC = 18;

  private final JButton widthButton = new JButton();
  private final JButton previous = new JButton();
  private final JButton next = new JButton();
  private final JButton learnMore = new JButton();
  private final JSeparator divider = new JSeparator(SwingConstants.VERTICAL);
  private final JLabel oscillationLabel = new JLabel();
  private final JButton reset = new JButton();

  private int widthCount;
  private int current = -1;
  private boolean oscillating;
  private IntConsumer navigator = step -> {};
  private Runnable learnMoreAction = () -> {};
  private Runnable resetAction = () -> {};

  public ProblemsPill() {
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
    setOpaque(false);
    add(widthButton);
    add(previous);
    add(next);
    add(learnMore);
    add(divider);
    add(oscillationLabel);
    add(reset);
    widthButton.addActionListener(event -> navigator.accept(current < 0 ? 1 : 0));
    previous.addActionListener(event -> navigator.accept(-1));
    next.addActionListener(event -> navigator.accept(1));
    learnMore.addActionListener(event -> learnMoreAction.run());
    reset.addActionListener(event -> resetAction.run());
    refresh();
    setVisible(false);
    Theme.addListener(this, this::refresh);
  }

  /**
   * Called with -1 or +1 to step through the width problems, or 0 to show the current one again.
   */
  public void setNavigator(IntConsumer navigator) {
    this.navigator = navigator == null ? step -> {} : navigator;
  }

  public void setLearnMoreAction(Runnable action) {
    learnMoreAction = action == null ? () -> {} : action;
  }

  public void setResetAction(Runnable action) {
    resetAction = action == null ? () -> {} : action;
  }

  /**
   * Shows what is wrong now.
   *
   * @param widthProblems how many nets have incompatible widths
   * @param currentProblem which of them is being shown, from zero, or -1 for none yet
   * @param oscillation whether an oscillation stopped the simulation
   */
  public void setProblems(int widthProblems, int currentProblem, boolean oscillation) {
    final var changed =
        widthProblems != widthCount || currentProblem != current || oscillation != oscillating;
    widthCount = widthProblems;
    current = currentProblem;
    oscillating = oscillation;
    if (changed) refresh();
    setVisible(widthCount > 0 || oscillating);
  }

  public int getWidthProblemCount() {
    return widthCount;
  }

  /** What the pill says, for tests and assistive technology. */
  public String getSummary() {
    final var summary = new StringBuilder();
    if (widthButton.isVisible()) summary.append(widthButton.getText());
    if (oscillationLabel.isVisible()) {
      if (summary.length() > 0) summary.append("; ");
      summary.append(oscillationLabel.getText());
    }
    return summary.toString();
  }

  private void refresh() {
    setBorder(Spacing.border(Spacing.XS, Spacing.SM, Spacing.XS, Spacing.SM));
    final var hasWidth = widthCount > 0;
    final var widthText = S.get("canvasWidthError")
        + (current >= 0
            ? "  " + S.get("problemsPosition", current + 1, widthCount)
            : widthCount > 1 ? " (" + widthCount + ")" : "");
    PanelHeader.configureChromeButton(widthButton, S.get("problemsShowTip"));
    widthButton.setText(widthText);
    widthButton.setIcon(AppIcons.colored(AppIcons.Id.WARNING, 14, Tokens.warning()));
    widthButton.setForeground(Tokens.statusBarForeground());
    widthButton.setVisible(hasWidth);

    PanelHeader.configureChromeButton(previous, S.get("problemsPrevious"));
    previous.setIcon(AppIcons.get(AppIcons.Id.CHEVRON_LEFT, 14));
    PanelHeader.configureChromeButton(next, S.get("problemsNext"));
    next.setIcon(AppIcons.get(AppIcons.Id.CHEVRON_RIGHT, 14));
    previous.setVisible(widthCount > 1);
    next.setVisible(widthCount > 1);

    PanelHeader.configureChromeButton(learnMore, S.get("problemsLearnMoreTip"));
    learnMore.setText(S.get("problemsLearnMore"));
    learnMore.setForeground(Tokens.accent());
    learnMore.setVisible(hasWidth);

    divider.setVisible(hasWidth && oscillating);
    divider.setMaximumSize(new Dimension(UiScale.scaled(8), UiScale.scaled(18)));
    divider.setForeground(Tokens.divider());

    oscillationLabel.setText(S.get("canvasOscillationError"));
    oscillationLabel.setToolTipText(S.get("statusOscillationStopped"));
    oscillationLabel.setFont(UiFonts.small());
    oscillationLabel.setIcon(AppIcons.colored(AppIcons.Id.ERROR, 14, Tokens.error()));
    oscillationLabel.setIconTextGap(Spacing.xs());
    oscillationLabel.setBorder(Spacing.border(0, Spacing.XS, 0, Spacing.XS));
    oscillationLabel.setForeground(Tokens.statusBarForeground());
    oscillationLabel.setVisible(oscillating);
    PanelHeader.configureChromeButton(reset, S.get("problemsResetTip"));
    reset.setText(S.get("problemsReset"));
    reset.setIcon(AppIcons.get(AppIcons.Id.RESET, 14));
    reset.setForeground(Tokens.accent());
    reset.setVisible(oscillating);

    getAccessibleContext().setAccessibleName(getSummary());
    revalidate();
    repaint();
  }

  @Override
  protected void paintComponent(Graphics gfx) {
    final var g2 = (Graphics2D) gfx.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      final var arc = UiScale.scaled(ARC);
      g2.setColor(Tokens.toastBackground());
      g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
      g2.setColor(oscillating ? Tokens.error() : Tokens.warning());
      g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
    } finally {
      g2.dispose();
    }
    super.paintComponent(gfx);
  }

  @Override
  public Dimension getMaximumSize() {
    return getPreferredSize();
  }
}

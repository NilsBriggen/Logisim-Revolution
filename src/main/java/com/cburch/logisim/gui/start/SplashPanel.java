/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.start;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.generated.BuildInfo;
import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.UiFonts;
import com.cburch.logisim.util.UiScale;
import com.formdev.flatlaf.extras.FlatSVGIcon;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;

/** The loading surface; it can be rendered and tested without constructing a window. */
final class SplashPanel extends JPanel {
  private static final int WIDTH = 560;
  private static final int HEIGHT = 300;

  private final JLabel title = new JLabel("Logisim Revolution");
  private final JLabel subtitle = new JLabel(S.get("splashSubtitle"));
  private final JLabel version = new JLabel(BuildInfo.version.toString());
  private final JLabel stage = new JLabel(S.get("splashStarting"));
  private final JProgressBar progress = new JProgressBar();

  SplashPanel() {
    super(new BorderLayout());
    setBorder(BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(Tokens.divider()),
        Spacing.border(32)));

    final var masthead = new JPanel(new BorderLayout(Spacing.lg(), 0));
    masthead.setOpaque(false);
    masthead.add(new JLabel(new FlatSVGIcon(
        "resources/logisim/brand/logisim-revolution-mark.svg", 88, 88)),
        BorderLayout.WEST);

    final var words = new JPanel();
    words.setLayout(new BoxLayout(words, BoxLayout.Y_AXIS));
    words.setOpaque(false);
    title.setFont(UiFonts.heading().deriveFont(
        Font.BOLD, UiFonts.heading().getSize2D() * 1.45f));
    title.setAlignmentX(LEFT_ALIGNMENT);
    subtitle.setFont(UiFonts.body());
    subtitle.setAlignmentX(LEFT_ALIGNMENT);
    version.setFont(UiFonts.small());
    version.setAlignmentX(LEFT_ALIGNMENT);
    words.add(Box.createVerticalGlue());
    words.add(title);
    words.add(Box.createVerticalStrut(Spacing.xs()));
    words.add(subtitle);
    words.add(Box.createVerticalStrut(Spacing.sm()));
    words.add(version);
    words.add(Box.createVerticalGlue());
    masthead.add(words, BorderLayout.CENTER);

    final var footer = new JPanel(new BorderLayout(0, Spacing.sm()));
    footer.setOpaque(false);
    stage.setFont(UiFonts.body());
    stage.getAccessibleContext().setAccessibleName(S.get("splashLoadingStage"));
    progress.setIndeterminate(true);
    progress.setStringPainted(false);
    progress.setBorderPainted(false);
    progress.setPreferredSize(new Dimension(0, UiScale.scaled(4)));
    footer.add(stage, BorderLayout.NORTH);
    footer.add(progress, BorderLayout.SOUTH);

    add(masthead, BorderLayout.NORTH);
    add(footer, BorderLayout.SOUTH);
    applyTheme();
  }

  void setStage(String message) {
    stage.setText(message);
  }

  String stageText() {
    return stage.getText();
  }

  JProgressBar progressBar() {
    return progress;
  }

  private void applyTheme() {
    setBackground(Tokens.color("Logisim.sidePanel.background", getBackground()));
    title.setForeground(Tokens.color("Label.foreground", getForeground()));
    subtitle.setForeground(Tokens.mutedForeground());
    version.setForeground(Tokens.mutedForeground());
    stage.setForeground(Tokens.color("Label.foreground", getForeground()));
  }

  @Override
  public Dimension getPreferredSize() {
    return new Dimension(UiScale.scaled(WIDTH), UiScale.scaled(HEIGHT));
  }
}

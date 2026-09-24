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
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.KeyEvent;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

/**
 * The heading of a panel: a quiet title, with room for the panel's own buttons.
 *
 * <p>It replaces the titled borders and centred bold labels used elsewhere. A box drawn around a
 * panel that already has edges only adds a line; a small heading says the same thing and leaves
 * the space to the contents.
 */
public class PanelHeader extends JPanel {

  private static final long serialVersionUID = 1L;

  private final JLabel titleLabel = new JLabel();
  private final JLabel subtitleLabel = new JLabel();
  private final JPanel actions =
      new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));

  public PanelHeader(String title) {
    super(new BorderLayout());
    setOpaque(false);
    actions.setOpaque(false);
    final var titles = new JPanel();
    titles.setOpaque(false);
    titles.setLayout(new javax.swing.BoxLayout(titles, javax.swing.BoxLayout.Y_AXIS));
    titleLabel.setAlignmentX(LEFT_ALIGNMENT);
    subtitleLabel.setAlignmentX(LEFT_ALIGNMENT);
    subtitleLabel.setVisible(false);
    titles.add(titleLabel);
    titles.add(subtitleLabel);
    add(titles, BorderLayout.CENTER);
    add(actions, BorderLayout.LINE_END);
    setTitle(title);
    applyTheme();
    Theme.addListener(this, this::applyTheme);
  }

  private void applyTheme() {
    setBorder(Spacing.border(Spacing.XS, Spacing.SM, Spacing.XS, Spacing.XS));
    titleLabel.setFont(UiFonts.small());
    titleLabel.setForeground(Tokens.sidePanelHeaderForeground());
    subtitleLabel.setFont(UiFonts.small());
    subtitleLabel.setForeground(Tokens.mutedForeground());
    revalidate();
    repaint();
  }

  /** Sets the heading without changing user-entered circuit or component identifiers. */
  public final void setTitle(String title) {
    titleLabel.setText(title == null ? "" : title);
    titleLabel.setToolTipText(title);
  }

  /** Compact native buttons keep their look-and-feel focus indicator and accessible action. */
  static void configureChromeButton(AbstractButton button, String name) {
    button.putClientProperty("JButton.buttonType", "toolBarButton");
    button.putClientProperty("JComponent.minimumWidth", 0);
    button.setFont(UiFonts.small());
    // FlatButtonBorder scales margins itself. These values must remain logical pixels.
    button.setMargin(new Insets(Spacing.XS / 2, Spacing.XS, Spacing.XS / 2, Spacing.XS));
    button.setFocusable(true);
    button.setFocusPainted(true);
    button.setToolTipText(name);
    button.getAccessibleContext().setAccessibleName(name);
    final var input = button.getInputMap(JComponent.WHEN_FOCUSED);
    input.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, false), "pressed");
    input.put(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0, true), "released");
  }

  /**
   * A sentence under the heading saying what the page is for.
   *
   * <p>Every preferences and options page has carried one of these, written and translated into
   * twelve languages, and nothing has ever rendered it.
   */
  public void setSubtitle(String subtitle) {
    final var text = subtitle == null ? "" : subtitle.trim();
    subtitleLabel.setText(text);
    subtitleLabel.setVisible(!text.isEmpty());
    revalidate();
  }

  /** Replaces the buttons shown at the end of the heading. */
  public void setActions(JComponent component) {
    actions.removeAll();
    if (component != null) actions.add(component);
    actions.revalidate();
    actions.repaint();
  }

  @Override
  public Dimension getMaximumSize() {
    final var preferred = getPreferredSize();
    return new Dimension(Integer.MAX_VALUE, preferred.height);
  }
}

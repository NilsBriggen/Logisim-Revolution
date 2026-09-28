/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell;

import com.cburch.logisim.gui.generic.SettingsForm;
import com.cburch.logisim.gui.theme.AppIcons;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.UiFonts;
import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;

/**
 * A padlock and a sentence above the properties, saying why they cannot be changed.
 *
 * <p>Without it a locked component's properties simply refuse to open, which looks like a fault.
 * The sentence also says plainly that anyone can unlock, since a lock only guards against slips.
 */
public class LockNotice extends JPanel {
  private static final long serialVersionUID = 1L;

  private final JLabel icon = new JLabel();
  private final JTextArea text = SettingsForm.createWrappingText("");

  public LockNotice() {
    super(new BorderLayout());
    final var iconHolder = new JPanel(new BorderLayout());
    iconHolder.setOpaque(false);
    iconHolder.add(icon, BorderLayout.NORTH);
    add(iconHolder, BorderLayout.WEST);
    add(text, BorderLayout.CENTER);
    setVisible(false);
    applyTheme();
    Theme.addListener(this, this::applyTheme);
  }

  private void applyTheme() {
    setOpaque(true);
    setBackground(Tokens.badgeBackground());
    setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, Tokens.divider()),
            BorderFactory.createEmptyBorder(
                Spacing.sm(), Spacing.sm(), Spacing.sm(), Spacing.sm())));
    icon.setIcon(AppIcons.colored(AppIcons.Id.LOCK, 14, Tokens.warning()));
    icon.setBorder(BorderFactory.createEmptyBorder(1, 0, 0, Spacing.sm()));
    text.setFont(UiFonts.small());
    text.setForeground(javax.swing.UIManager.getColor("Label.foreground"));
    text.setOpaque(false);
    revalidate();
    repaint();
  }

  /** Shows {@code note}, or hides the notice when it is {@code null} or blank. */
  public void setNote(String note) {
    final var show = note != null && !note.isBlank();
    if (show) text.setText(note);
    if (show != isVisible() || show) {
      setVisible(show);
      revalidate();
      repaint();
    }
  }

  /** The sentence shown, or an empty string when the notice is hidden; for tests. */
  public String getNote() {
    return isVisible() ? text.getText() : "";
  }
}

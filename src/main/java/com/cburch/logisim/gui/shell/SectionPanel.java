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
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.UiFonts;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * A named group of controls that can be folded away.
 *
 * <p>It replaces the titled borders and the tabs used to stack unrelated things in one panel: a
 * box drawn around a group adds a line without saying anything, and a tab hides its contents
 * completely. A section says what it holds and gets out of the way when it is not wanted.
 */
public class SectionPanel extends JPanel {

  private static final long serialVersionUID = 1L;

  private final JLabel titleLabel = new JLabel();
  private final JPanel headerRow = new JPanel(new BorderLayout());
  private final JComponent content;

  private boolean expanded = true;

  public SectionPanel(String title, JComponent content, boolean expanded) {
    super(new BorderLayout());
    this.content = content;

    headerRow.setOpaque(false);
    headerRow.setBorder(
        BorderFactory.createEmptyBorder(Spacing.xs(), Spacing.sm(), Spacing.xs(), Spacing.sm()));
    headerRow.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    headerRow.add(titleLabel, BorderLayout.CENTER);
    headerRow.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent event) {
            setExpanded(!SectionPanel.this.expanded);
          }
        });

    add(headerRow, BorderLayout.NORTH);
    add(content, BorderLayout.CENTER);

    setTitle(title);
    setExpanded(expanded);
    applyTheme();
    Theme.addListener(
        () -> {
          applyTheme();
          refreshHeader();
        });
  }

  private void applyTheme() {
    titleLabel.setFont(UiFonts.small());
    titleLabel.setForeground(Tokens.sidePanelHeaderForeground());
  }

  /** Whether the contents are showing. */
  public boolean isExpanded() {
    return expanded;
  }

  public final void setExpanded(boolean expanded) {
    this.expanded = expanded;
    content.setVisible(expanded);
    refreshHeader();
    revalidate();
    repaint();
  }

  public final void setTitle(String title) {
    titleLabel.setText(title == null ? "" : title.toUpperCase(Locale.getDefault()));
    refreshHeader();
  }

  private void refreshHeader() {
    titleLabel.setIcon(
        AppIcons.colored(
            expanded ? AppIcons.Id.CHEVRON_DOWN : AppIcons.Id.CHEVRON_RIGHT,
            12,
            Tokens.sidePanelHeaderForeground()));
  }

  @Override
  public Dimension getMaximumSize() {
    return expanded ? super.getMaximumSize()
        : new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
  }
}

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
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Insets;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleState;
import javax.accessibility.AccessibleStateSet;
import javax.swing.JButton;
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

  /** Fired when the section is folded or unfolded, so a panel can remember the state. */
  public static final String EXPANDED_PROPERTY = "expanded";

  /**
   * The heading, a plain button rather than a toggle.
   *
   * <p>It was a toggle button showing "expanded" as "selected", which the look and feel paints as a
   * grey fill: muted heading text on that fill fell to 3.8:1. The state is carried by the chevron
   * and, for assistive technology, by the expandable/expanded accessible states.
   */
  private final JButton titleButton = new HeadingButton();
  private final JLabel badgeLabel = new JLabel();
  private final JPanel headerRow = new JPanel(new BorderLayout());
  private final JComponent content;

  private boolean expanded = true;
  private AppIcons.Id badge;

  public SectionPanel(String title, JComponent content, boolean expanded) {
    super(new BorderLayout());
    this.content = content;

    headerRow.setOpaque(false);
    badgeLabel.setVisible(false);
    headerRow.add(badgeLabel, BorderLayout.WEST);
    titleButton.setHorizontalAlignment(JButton.LEADING);
    titleButton.addActionListener(event -> setExpanded(!isExpanded()));
    headerRow.add(titleButton, BorderLayout.CENTER);

    add(headerRow, BorderLayout.NORTH);
    add(content, BorderLayout.CENTER);

    setTitle(title);
    setExpanded(expanded);
    applyTheme();
    Theme.addListener(this, this::applyTheme);
  }

  private void applyTheme() {
    headerRow.setBorder(Spacing.border(0, Spacing.SM, 0, Spacing.SM));
    badgeLabel.setBorder(Spacing.border(0, 0, 0, Spacing.XS));
    PanelHeader.configureChromeButton(titleButton, titleButton.getText());
    titleButton.setMargin(new Insets(Spacing.XS / 2, 0, Spacing.XS / 2, 0));
    titleButton.setForeground(Tokens.sidePanelHeaderForeground());
    titleButton.setIconTextGap(Spacing.xs());
    refreshHeader();
    setBadge(badge);
    revalidate();
    repaint();
  }

  /** Whether the contents are showing. */
  public boolean isExpanded() {
    return expanded;
  }

  public final void setExpanded(boolean expanded) {
    final var previous = this.expanded;
    this.expanded = expanded;
    content.setVisible(expanded);
    refreshHeader();
    revalidate();
    repaint();
    firePropertyChange(EXPANDED_PROPERTY, previous, expanded);
    if (previous != expanded) {
      titleButton
          .getAccessibleContext()
          .firePropertyChange(
              AccessibleContext.ACCESSIBLE_STATE_PROPERTY,
              previous ? AccessibleState.EXPANDED : AccessibleState.COLLAPSED,
              expanded ? AccessibleState.EXPANDED : AccessibleState.COLLAPSED);
    }
  }

  /**
   * Puts a small icon before the title.
   *
   * <p>For the few sections that are not simply a name — the pinned components and the ones used
   * most recently — where a symbol says at a glance why the group exists.
   */
  public void setBadge(AppIcons.Id icon) {
    badge = icon;
    badgeLabel.setVisible(icon != null);
    badgeLabel.setIcon(icon == null ? null : AppIcons.colored(icon, 12, Tokens.mutedForeground()));
  }

  /** The heading text. */
  public String getTitle() {
    return titleButton.getText();
  }

  /** The folded part, without the heading. */
  public JComponent getContent() {
    return content;
  }

  public final void setTitle(String title) {
    titleButton.setText(title == null ? "" : title);
    titleButton.getAccessibleContext().setAccessibleName(titleButton.getText());
    titleButton.setToolTipText(title);
    refreshHeader();
  }

  private void refreshHeader() {
    titleButton.setIcon(
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

  /** A button that reports the section's expanded or collapsed state to assistive technology. */
  private final class HeadingButton extends JButton {
    private static final long serialVersionUID = 1L;

    @Override
    public AccessibleContext getAccessibleContext() {
      if (accessibleContext == null) {
        accessibleContext =
            new AccessibleJButton() {
              private static final long serialVersionUID = 1L;

              @Override
              public AccessibleStateSet getAccessibleStateSet() {
                final var states = super.getAccessibleStateSet();
                states.add(AccessibleState.EXPANDABLE);
                states.add(expanded ? AccessibleState.EXPANDED : AccessibleState.COLLAPSED);
                return states;
              }
            };
      }
      return accessibleContext;
    }
  }
}

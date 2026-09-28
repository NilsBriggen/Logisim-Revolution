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
import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JButton;
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
  private final JButton more = new JButton();
  private final JPanel titles = new JPanel();

  public PanelHeader(String title) {
    setLayout(new HeaderLayout());
    setOpaque(false);
    configureChromeButton(more, S.get("panelHeaderMoreTip"));
    more.addActionListener(event -> showOverflow());
    more.setVisible(false);
    actions.setOpaque(false);
    titles.setOpaque(false);
    titles.setLayout(new javax.swing.BoxLayout(titles, javax.swing.BoxLayout.Y_AXIS));
    titleLabel.setAlignmentX(LEFT_ALIGNMENT);
    subtitleLabel.setAlignmentX(LEFT_ALIGNMENT);
    subtitleLabel.setVisible(false);
    titles.add(titleLabel);
    titles.add(subtitleLabel);
    add(titles);
    add(actions);
    add(more);
    setTitle(title);
    applyTheme();
    Theme.addListener(this, this::applyTheme);
  }

  private void applyTheme() {
    setBorder(Spacing.border(Spacing.XS, Spacing.SM, Spacing.XS, Spacing.XS));
    more.setIcon(AppIcons.colored(AppIcons.Id.MORE, 14, Tokens.iconForeground()));
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

  /** The buttons among the actions, in the order they are drawn. */
  private List<AbstractButton> actionButtons() {
    final var buttons = new ArrayList<AbstractButton>();
    collectButtons(actions, buttons);
    return buttons;
  }

  private static void collectButtons(Container container, List<AbstractButton> buttons) {
    for (final var child : container.getComponents()) {
      if (child instanceof AbstractButton button) buttons.add(button);
      else if (child instanceof Container nested) collectButtons(nested, buttons);
    }
  }

  /** Lists the actions that did not fit, by name, under the "more" button. */
  private void showOverflow() {
    final var menu = new javax.swing.JPopupMenu();
    for (final var button : actionButtons()) {
      if (button.isVisible() || !button.isFocusable()) continue;
      final var name = button.getAccessibleContext().getAccessibleName();
      if (name == null || name.isBlank()) continue;
      final var entry = new javax.swing.JMenuItem(name, button.getIcon());
      entry.setEnabled(button.isEnabled());
      entry.addActionListener(event -> button.doClick(0));
      menu.add(entry);
    }
    if (menu.getComponentCount() > 0) menu.show(more, 0, more.getHeight());
  }

  /**
   * The title on the left and the actions on the right, as before, except that the title keeps a
   * few characters and the actions that no longer fit, at a high interface scale in a narrow panel,
   * move behind a "more" button instead of being pushed off the edge.
   */
  private final class HeaderLayout implements LayoutManager {
    @Override
    public void addLayoutComponent(String name, Component component) {}

    @Override
    public void removeLayoutComponent(Component component) {}

    @Override
    public Dimension preferredLayoutSize(Container target) {
      final var insets = target.getInsets();
      final var title = titles.getPreferredSize();
      final var buttons = actions.getPreferredSize();
      final var extra = more.isVisible() ? more.getPreferredSize() : new Dimension();
      return new Dimension(
          insets.left + insets.right + title.width + buttons.width + extra.width,
          insets.top + insets.bottom
              + Math.max(title.height, Math.max(buttons.height, extra.height)));
    }

    @Override
    public Dimension minimumLayoutSize(Container target) {
      final var preferred = preferredLayoutSize(target);
      final var insets = target.getInsets();
      return new Dimension(insets.left + insets.right + more.getPreferredSize().width,
          preferred.height);
    }

    @Override
    public void layoutContainer(Container target) {
      final var insets = target.getInsets();
      final var width = target.getWidth() - insets.left - insets.right;
      final var height = target.getHeight() - insets.top - insets.bottom;
      final var titleMinimum = Math.min(titles.getPreferredSize().width,
          titleLabel.getFontMetrics(titleLabel.getFont()).charWidth('m') * 4);
      final var buttons = actionButtons();
      final var widths = new int[buttons.size()];
      var shownWidth = 0;
      var allWidth = 0;
      for (var i = 0; i < widths.length; i++) {
        widths[i] = buttons.get(i).getPreferredSize().width;
        allWidth += widths[i];
        if (buttons.get(i).isVisible()) shownWidth += widths[i];
      }
      final var fixed = Math.max(0, actions.getPreferredSize().width - shownWidth);
      final var room = width - titleMinimum - fixed;
      var keep = widths.length;
      if (allWidth > room) {
        var kept = allWidth + more.getPreferredSize().width;
        while (keep > 0 && kept > room) kept -= widths[--keep];
      }
      for (var i = 0; i < widths.length; i++) {
        final var visible = i < keep;
        if (buttons.get(i).isVisible() != visible) buttons.get(i).setVisible(visible);
      }
      final var overflowing = keep < widths.length;
      if (more.isVisible() != overflowing) more.setVisible(overflowing);

      var right = insets.left + width;
      if (overflowing) {
        final var size = more.getPreferredSize();
        right -= size.width;
        more.setBounds(right, insets.top + (height - size.height) / 2, size.width, size.height);
      }
      final var actionsWidth = Math.min(actions.getPreferredSize().width,
          Math.max(0, right - insets.left));
      right -= actionsWidth;
      actions.setBounds(right, insets.top, actionsWidth, height);
      titles.setBounds(insets.left, insets.top, Math.max(0, right - insets.left), height);
    }
  }

  @Override
  public Dimension getMaximumSize() {
    final var preferred = getPreferredSize();
    return new Dimension(Integer.MAX_VALUE, preferred.height);
  }
}

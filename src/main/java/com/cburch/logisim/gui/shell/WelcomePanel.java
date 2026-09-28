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

import com.cburch.logisim.generated.BuildInfo;
import com.cburch.logisim.gui.theme.AppIcons;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.UiFonts;
import com.cburch.logisim.util.UiScale;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Rectangle;
import java.beans.PropertyChangeListener;
import java.io.File;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JViewport;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * What the window shows when Logisim-evolution is started without a file.
 *
 * <p>It used to open straight onto an empty grid, which says nothing about what the application
 * is, offers no way back to yesterday's work, and leaves a beginner looking at a blank page. This
 * offers the three things somebody actually wants at that moment: start something, open something,
 * or go back to what they had open last. Somebody with nothing to go back to is most likely new, so
 * in place of the empty recent list they are offered the tutorial and the user's guide.
 */
public class WelcomePanel extends JPanel implements Scrollable {

  private static final long serialVersionUID = 1L;

  /** How many recent projects to offer before the list stops being a shortcut. */
  private static final int MAX_RECENT = 8;

  /** Help-set targets for the learning routes, as the Help menu names them. */
  public static final String HELP_TUTORIAL = "tutorial";
  public static final String HELP_GUIDE = "guide";

  private final JLabel titleLabel = new JLabel();
  private final JLabel subtitleLabel = new JLabel();
  private final JPanel actions = new JPanel();
  private final JPanel recent = new JPanel();
  private final JLabel recentHeading = new JLabel();

  private final Runnable onNew;
  private final Runnable onOpen;
  private final Consumer<File> onOpenRecent;
  private final Consumer<String> onShowHelp;

  /**
   * Refreshes the recent list when a project is opened or saved while this panel exists.
   *
   * <p>Held in a field because the preference support only keeps weak references to listeners.
   */
  private final PropertyChangeListener recentListener =
      event -> {
        if (SwingUtilities.isEventDispatchThread()) {
          refresh();
        } else {
          SwingUtilities.invokeLater(this::refresh);
        }
      };

  public WelcomePanel(Runnable onNew, Runnable onOpen, Consumer<File> onOpenRecent) {
    this(onNew, onOpen, onOpenRecent, null);
  }

  /**
   * Creates the panel.
   *
   * @param onShowHelp opens the help window at the given target; {@code null} leaves the learning
   *     routes out
   */
  public WelcomePanel(
      Runnable onNew,
      Runnable onOpen,
      Consumer<File> onOpenRecent,
      Consumer<String> onShowHelp) {
    super(new GridBagLayout());
    this.onNew = onNew;
    this.onOpen = onOpen;
    this.onOpenRecent = onOpenRecent;
    this.onShowHelp = onShowHelp;

    final var column = new JPanel();
    column.setLayout(new BoxLayout(column, BoxLayout.Y_AXIS));
    column.setOpaque(false);

    titleLabel.setAlignmentX(LEFT_ALIGNMENT);
    subtitleLabel.setAlignmentX(LEFT_ALIGNMENT);
    actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
    actions.setOpaque(false);
    actions.setAlignmentX(LEFT_ALIGNMENT);
    recent.setLayout(new BoxLayout(recent, BoxLayout.Y_AXIS));
    recent.setOpaque(false);
    recent.setAlignmentX(LEFT_ALIGNMENT);
    recentHeading.setAlignmentX(LEFT_ALIGNMENT);

    column.add(titleLabel);
    column.add(Box.createVerticalStrut(Spacing.xs()));
    column.add(subtitleLabel);
    column.add(Box.createVerticalStrut(Spacing.xl()));
    column.add(actions);
    column.add(Box.createVerticalStrut(Spacing.xl()));
    column.add(recentHeading);
    column.add(Box.createVerticalStrut(Spacing.xs()));
    column.add(recent);

    // The column keeps its own width and sits in the middle of the editor area; stretching it
    // across the whole area left every line hard against the left edge of a wide window.
    final var constraints = new GridBagConstraints();
    constraints.anchor = GridBagConstraints.CENTER;
    constraints.fill = GridBagConstraints.NONE;
    constraints.weightx = 1;
    constraints.weighty = 1;
    constraints.insets = Spacing.formGaps();
    add(column, constraints);

    refresh();
    Theme.addListener(this, this::refresh);
    AppPreferences.addPropertyChangeListener(AppPreferences.RECENT_PROJECTS, recentListener);
  }

  /** Rebuilds the contents, after a change of theme, language, or recent files. */
  public final void refresh() {
    setBackground(Tokens.color("Logisim.sidePanel.background", getBackground()));
    titleLabel.setText(BuildInfo.name);
    titleLabel.setFont(UiFonts.display());
    subtitleLabel.setText(S.get("welcomeSubtitle"));
    subtitleLabel.setFont(UiFonts.body());
    subtitleLabel.setForeground(Tokens.mutedForeground());

    actions.removeAll();
    actions.add(action(AppIcons.Id.NEW, S.get("welcomeNew"), S.get("welcomeNewHint"), onNew));
    actions.add(Box.createVerticalStrut(Spacing.xs()));
    actions.add(action(AppIcons.Id.OPEN, S.get("welcomeOpen"), S.get("welcomeOpenHint"), onOpen));

    recent.removeAll();
    final var files = recentFiles();
    recentHeading.setFont(UiFonts.small());
    recentHeading.setForeground(Tokens.sidePanelHeaderForeground());
    if (!files.isEmpty()) {
      recentHeading.setText(S.get("welcomeRecent"));
      recentHeading.setVisible(true);
      for (final var file : files) {
        final var parent = file.getParentFile();
        recent.add(
            action(
                AppIcons.Id.CIRCUIT,
                file.getName(),
                parent == null ? "" : parent.getPath(),
                () -> onOpenRecent.accept(file)));
      }
    } else if (onShowHelp != null) {
      recentHeading.setText(S.get("welcomeLearn"));
      recentHeading.setVisible(true);
      recent.add(
          action(
              AppIcons.Id.INFO,
              S.get("welcomeTutorial"),
              S.get("welcomeTutorialHint"),
              () -> onShowHelp.accept(HELP_TUTORIAL)));
      recent.add(
          action(
              AppIcons.Id.QUESTION,
              S.get("welcomeGuide"),
              S.get("welcomeGuideHint"),
              () -> onShowHelp.accept(HELP_GUIDE)));
    } else {
      recentHeading.setVisible(false);
    }
    revalidate();
    repaint();
  }

  private static List<File> recentFiles() {
    final var files = AppPreferences.getRecentFiles();
    final var kept = new java.util.ArrayList<File>();
    for (final var file : files) {
      if (file != null && kept.size() < MAX_RECENT) kept.add(file);
    }
    return kept;
  }

  /** A native keyboard-accessible action with its explanation on a separate line. */
  private JComponent action(AppIcons.Id icon, String title, String hint, Runnable onClick) {
    final var row = new JPanel(new BorderLayout(0, Spacing.xs()));
    row.setOpaque(false);
    row.setAlignmentX(LEFT_ALIGNMENT);
    row.setBorder(
        BorderFactory.createEmptyBorder(Spacing.xs(), Spacing.xs(), Spacing.xs(), Spacing.xs()));

    final var name = new JButton(title, AppIcons.colored(icon, AppIcons.SIZE, Tokens.accent()));
    name.setFont(UiFonts.body());
    name.setForeground(Tokens.accent());
    name.setHorizontalAlignment(JButton.LEADING);
    name.setToolTipText(hint);
    name.setBorderPainted(false);
    name.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,
        com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
    name.addActionListener(event -> onClick.run());
    name.getAccessibleContext().setAccessibleDescription(hint);
    row.add(name, BorderLayout.NORTH);
    if (hint != null && !hint.isEmpty()) {
      final var hintLabel = new JLabel(hint);
      hintLabel.setFont(UiFonts.small());
      hintLabel.setForeground(Tokens.mutedForeground());
      hintLabel.setToolTipText(hint);
      row.add(hintLabel, BorderLayout.CENTER);
    }
    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, row.getPreferredSize().height));
    return row;
  }

  @Override
  public Component add(Component component) {
    return super.add(component);
  }

  // Scrollable: fills the viewport while it fits, and scrolls instead of clipping at large
  // interface scales in small windows.

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return getPreferredSize();
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle visible, int orientation, int direction) {
    return UiScale.scaled(16);
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle visible, int orientation, int direction) {
    return orientation == SwingConstants.VERTICAL ? visible.height : visible.width;
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return getParent() instanceof JViewport viewport
        && viewport.getWidth() >= getPreferredSize().width;
  }

  @Override
  public boolean getScrollableTracksViewportHeight() {
    return getParent() instanceof JViewport viewport
        && viewport.getHeight() >= getPreferredSize().height;
  }
}

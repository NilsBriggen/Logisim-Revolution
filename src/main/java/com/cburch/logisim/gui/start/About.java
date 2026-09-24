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

import com.cburch.logisim.Main;
import com.cburch.logisim.generated.BuildInfo;
import com.cburch.logisim.gui.generic.Dialogs;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.util.LineBuffer;
import com.cburch.logisim.util.UiScale;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.HierarchyEvent;
import java.awt.event.KeyEvent;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;

public class About {
  static final int PADDING = 20;
  static final int PANEL_WIDTH = 600;
  static final int LOGO_HEIGHT = 200;
  static final int SCROLLER_HEIGHT = 200;

  private static final String LOGO_IMG = "resources/logisim/img/logisim-revolution-logo.png";
  private static JDialog activeDialog;

  private About() {}

  public static AboutPanel getImagePanel() {
    return new AboutPanel();
  }

  public static void showAboutDialog(JFrame owner) {
    if (!Main.hasGui()) {
      return;
    }
    if (activeDialog != null && activeDialog.isDisplayable()) {
      activeDialog.toFront();
      activeDialog.requestFocus();
      return;
    }
    activeDialog = createAboutDialog(owner);
    activeDialog.setVisible(true);
  }

  static JDialog createAboutDialog(JFrame owner) {
    final var content = new JPanel(new BorderLayout());
    content.add(new AboutPanel(true));
    content.setBorder(BorderFactory.createLineBorder(Tokens.divider(), 1));

    // A modal transient dialog crashes on some Xwayland/KWin combinations while blocking its
    // owner. About has no transaction to protect, so a single modeless window is sufficient.
    final var dialog = new JDialog(owner, S.get("aboutDialogTitle"), false);
    dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
    Dialogs.installEscapeToClose(dialog);
    final var optionPane = new JOptionPane(content, JOptionPane.PLAIN_MESSAGE);
    final var copyDetailsButton = new JButton(S.get("aboutDialogCopyDetails"));
    copyDetailsButton.addActionListener(event -> {
      final var info = new StringBuilder();
      info.append("Product: " + BuildInfo.displayName)
              .append("\n")
              .append(LineBuffer.format(
                      "Runs on: {{1}} v{{2}}\n",
                      System.getProperty("java.vm.name"),
                      System.getProperty("java.version")))
              .append("\n")
              .append(LineBuffer.format("Compiled: {{1}}\n", BuildInfo.dateIso8601))
              .append(LineBuffer.format("Build ID: {{1}}\n", BuildInfo.buildId))
              .append(LineBuffer.format("Built on: {{1}}\n", BuildInfo.jvm_version))
              ;

      final var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
      final var stringSelection = new StringSelection(info.toString());
      clipboard.setContents(stringSelection, null);
    });

    final var closeButton = new JButton(S.get("aboutDialogClose"));
    closeButton.addActionListener(e -> {
      dialog.dispose();
    });

    optionPane.setOptions(new JButton[]{copyDetailsButton, closeButton});

    dialog.setContentPane(optionPane);
    // JOptionPane's ancestor binding otherwise consumes Escape by changing its value. This
    // custom dialog has no value listener, so use the same window-closing action as its root.
    final var root = dialog.getRootPane();
    final var escape = KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0);
    final var closeAction = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(escape);
    optionPane.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        .put(escape, closeAction);
    optionPane.getActionMap().put(closeAction, root.getActionMap().get(closeAction));
    dialog.getRootPane().setDefaultButton(closeButton);
    dialog.pack();
    dialog.setLocationRelativeTo(owner);
    return dialog;
  }

  static class AboutPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private static final int FRAME_MILLIS = 20;
    private final AboutCredits credits;
    private final Timer animation;

    public AboutPanel() {
      this(false);
    }

    public AboutPanel(boolean includeCredits) {
      setLayout(new BorderLayout());
      add(new LogoPanel(), BorderLayout.NORTH);
      credits = includeCredits ? new AboutCredits(PANEL_WIDTH, SCROLLER_HEIGHT) : null;
      if (includeCredits) {
        add(credits, BorderLayout.CENTER);
      }
      animation = new Timer(FRAME_MILLIS, event -> {
        if (credits != null) {
          credits.advance(FRAME_MILLIS);
          credits.repaint();
        }
      });
      animation.setCoalesce(true);
      addHierarchyListener(event -> {
        if ((event.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) updateAnimation();
      });
      applyTheme();
      Theme.addListener(this, this::applyTheme);
    }

    private void applyTheme() {
      final var padding = UiScale.scaled(PADDING);
      setBorder(BorderFactory.createEmptyBorder(padding, padding, padding, padding));
      setBackground(AboutCredits.surfaceColor());
      revalidate();
      repaint();
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(
          UiScale.scaled(PANEL_WIDTH + 2 * PADDING),
          UiScale.scaled(LOGO_HEIGHT + 2 * PADDING + (credits == null ? 0 : SCROLLER_HEIGHT)));
    }

    @Override
    public void addNotify() {
      super.addNotify();
      updateAnimation();
    }

    @Override
    public void removeNotify() {
      animation.stop();
      super.removeNotify();
    }

    private void updateAnimation() {
      if (credits != null && isDisplayable() && isShowing()) animation.start();
      else animation.stop();
    }

    boolean isAnimationRunning() {
      return animation.isRunning();
    }
  }

  /** Preserve the original logo and its aspect ratio at the current UI scale and available width. */
  private static class LogoPanel extends JPanel {
    private static final long serialVersionUID = 1L;
    private final ImageIcon logo =
        new ImageIcon(About.class.getClassLoader().getResource(LOGO_IMG));

    private LogoPanel() {
      setOpaque(false);
    }

    @Override
    public Dimension getPreferredSize() {
      return new Dimension(UiScale.scaled(PANEL_WIDTH), UiScale.scaled(LOGO_HEIGHT));
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      super.paintComponent(graphics);
      final var g = (Graphics2D) graphics.create();
      try {
        final var ratio = Math.min(
            (double) getWidth() / logo.getIconWidth(),
            (double) getHeight() / logo.getIconHeight());
        final var width = (int) Math.round(logo.getIconWidth() * ratio);
        final var height = (int) Math.round(logo.getIconHeight() * ratio);
        g.setRenderingHint(
            RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(logo.getImage(), (getWidth() - width) / 2, (getHeight() - height) / 2,
            width, height, this);
      } finally {
        g.dispose();
      }
    }
  }
}

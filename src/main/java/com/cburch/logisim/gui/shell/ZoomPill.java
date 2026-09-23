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

import com.cburch.logisim.gui.generic.ZoomControl;
import com.cburch.logisim.gui.generic.ZoomModel;
import com.cburch.logisim.gui.theme.AppIcons;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.UiFonts;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * The zoom controls, floating over the bottom corner of the canvas.
 *
 * <p>They used to sit in the left column under the attribute table, where they took a fixed strip
 * of a panel meant for something else and were nowhere near the drawing they act on. Over the
 * canvas they are beside the work and cost no layout at all.
 */
public class ZoomPill extends JPanel {

  private static final long serialVersionUID = 1L;

  /** Corner radius, unscaled. */
  private static final int ARC = 18;

  /** How far the pill floats from the canvas corner, unscaled. */
  public static final int MARGIN = 12;

  private final ZoomControl control;
  private final JLabel percent = new JLabel();
  private final JButton gridButton;

  private ZoomModel model;
  private final PropertyChangeListener zoomListener = event -> refresh();
  private final PropertyChangeListener gridListener = event -> refresh();

  public ZoomPill(ZoomControl control, ZoomModel model) {
    this.control = control;
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
    setOpaque(false);
    setBorder(
        BorderFactory.createEmptyBorder(Spacing.xs(), Spacing.sm(), Spacing.xs(), Spacing.sm()));

    add(button(AppIcons.Id.ZOOM_OUT, S.get("zoomOutTip"), control::zoomOut));
    percent.setFont(UiFonts.small());
    percent.setHorizontalAlignment(JLabel.CENTER);
    percent.setBorder(
        BorderFactory.createEmptyBorder(0, Spacing.xs(), 0, Spacing.xs()));
    percent.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    percent.setToolTipText(S.get("zoomResetTip"));
    percent.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent event) {
            if (ZoomPill.this.model != null) ZoomPill.this.model.setZoomFactorCenter(1.0);
          }
        });
    add(percent);
    add(button(AppIcons.Id.ZOOM_IN, S.get("zoomInTip"), control::zoomIn));
    add(button(AppIcons.Id.ZOOM_FIT, S.get("zoomAuto"), this::fit));
    gridButton = button(AppIcons.Id.GRID, S.get("zoomGridTip"), this::toggleGrid);
    add(gridButton);

    setModel(model);
    Theme.addListener(this::refresh);
  }

  private JButton button(AppIcons.Id icon, String tooltip, Runnable action) {
    final var button = new JButton(AppIcons.get(icon, 14));
    button.setToolTipText(tooltip);
    button.setFocusable(false);
    button.setBorderPainted(false);
    button.setContentAreaFilled(false);
    button.setBorder(BorderFactory.createEmptyBorder(Spacing.xs(), Spacing.xs(), Spacing.xs(), Spacing.xs()));
    button.addActionListener(event -> action.run());
    return button;
  }

  /** Points the controls at another zoom model, when the editor changes. */
  public final void setModel(ZoomModel model) {
    if (this.model != null) {
      this.model.removePropertyChangeListener(ZoomModel.ZOOM, zoomListener);
      this.model.removePropertyChangeListener(ZoomModel.SHOW_GRID, gridListener);
    }
    this.model = model;
    if (model != null) {
      model.addPropertyChangeListener(ZoomModel.ZOOM, zoomListener);
      model.addPropertyChangeListener(ZoomModel.SHOW_GRID, gridListener);
    }
    setVisible(model != null);
    refresh();
  }

  private void fit() {
    if (model != null) control.zoomButton.doClick();
  }

  private void toggleGrid() {
    if (model != null) model.setShowGrid(!model.getShowGrid());
  }

  /** Re-reads the zoom level and the grid state. */
  public final void refresh() {
    if (model == null) return;
    percent.setText(Math.round(model.getZoomFactor() * 100) + "%");
    percent.setForeground(Tokens.statusBarForeground());
    gridButton.setIcon(
        AppIcons.colored(
            AppIcons.Id.GRID, 14, model.getShowGrid() ? Tokens.accent() : Tokens.iconForeground()));
    revalidate();
    repaint();
  }

  @Override
  protected void paintComponent(Graphics gfx) {
    final var g2 = (Graphics2D) gfx.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      final var arc = com.cburch.logisim.util.UiScale.scaled(ARC);
      g2.setColor(Tokens.toastBackground());
      g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
      g2.setColor(Tokens.divider());
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

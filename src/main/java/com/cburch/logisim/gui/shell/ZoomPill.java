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
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.beans.PropertyChangeListener;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import javax.swing.BoxLayout;
import javax.swing.JButton;
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
  private final JButton percent = new JButton();
  private final JButton gridButton;

  private record ButtonSpec(AppIcons.Id icon, String tooltipKey) {}

  private final Map<JButton, ButtonSpec> buttons = new LinkedHashMap<>();

  private ZoomModel model;
  private boolean listening;
  private Consumer<String> zoomTextListener = text -> {};
  private String lastZoomText;
  private final PropertyChangeListener zoomListener = event -> refresh();
  private final PropertyChangeListener gridListener = event -> refresh();

  public ZoomPill(ZoomControl control, ZoomModel model) {
    this.control = control;
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
    setOpaque(false);
    add(button(AppIcons.Id.ZOOM_OUT, "zoomOutTip", control::zoomOut));
    percent.addActionListener(event -> {
      if (ZoomPill.this.model != null) ZoomPill.this.model.setZoomFactorCenter(1.0);
    });
    add(percent);
    add(button(AppIcons.Id.ZOOM_IN, "zoomInTip", control::zoomIn));
    add(button(AppIcons.Id.ZOOM_FIT, "zoomAuto", this::fit));
    gridButton = button(AppIcons.Id.GRID, "zoomGridTip", this::toggleGrid);
    add(gridButton);

    setModel(model);
    Theme.addListener(this, this::refresh);
  }

  private JButton button(AppIcons.Id icon, String tooltipKey, Runnable action) {
    final var button = new JButton(AppIcons.get(icon, 14));
    buttons.put(button, new ButtonSpec(icon, tooltipKey));
    PanelHeader.configureChromeButton(button, S.get(tooltipKey));
    button.addActionListener(event -> action.run());
    return button;
  }

  /** Points the controls at another zoom model, when the editor changes. */
  public final void setModel(ZoomModel model) {
    detachModelListeners();
    this.model = model;
    if (isDisplayable()) attachModelListeners();
    setVisible(model != null);
    refresh();
  }

  /** A status readout bound to the active model, including appearance and the no-model state. */
  public void setZoomTextListener(Consumer<String> listener) {
    zoomTextListener = listener == null ? text -> {} : listener;
    zoomTextListener.accept(zoomText());
  }

  @Override
  public void addNotify() {
    super.addNotify();
    attachModelListeners();
    refresh();
  }

  @Override
  public void removeNotify() {
    detachModelListeners();
    super.removeNotify();
  }

  private void detachModelListeners() {
    if (listening && model != null) {
      this.model.removePropertyChangeListener(ZoomModel.ZOOM, zoomListener);
      this.model.removePropertyChangeListener(ZoomModel.SHOW_GRID, gridListener);
    }
    listening = false;
  }

  private void attachModelListeners() {
    if (!listening && model != null) {
      model.addPropertyChangeListener(ZoomModel.ZOOM, zoomListener);
      model.addPropertyChangeListener(ZoomModel.SHOW_GRID, gridListener);
      listening = true;
    }
  }

  private void fit() {
    if (model != null) control.zoomButton.doClick();
  }

  private void toggleGrid() {
    if (model != null) model.setShowGrid(!model.getShowGrid());
  }

  /** Re-reads the zoom level and the grid state. */
  public final void refresh() {
    setBorder(Spacing.border(Spacing.XS, Spacing.SM, Spacing.XS, Spacing.SM));
    for (final var entry : buttons.entrySet()) {
      final var button = entry.getKey();
      final var spec = entry.getValue();
      PanelHeader.configureChromeButton(button, S.get(spec.tooltipKey()));
      button.setIcon(AppIcons.get(spec.icon(), 14));
      button.setEnabled(model != null);
    }
    PanelHeader.configureChromeButton(percent, S.get("zoomResetTip"));
    percent.setEnabled(model != null);
    final var text = zoomText();
    percent.setText(text);
    percent.setForeground(Tokens.statusBarForeground());
    gridButton.setIcon(
        AppIcons.colored(
            AppIcons.Id.GRID, 14,
            model != null && model.getShowGrid() ? Tokens.accent() : Tokens.iconForeground()));
    gridButton.setSelected(model != null && model.getShowGrid());
    if (!text.equals(lastZoomText)) {
      lastZoomText = text;
      zoomTextListener.accept(text);
    }
    revalidate();
    repaint();
  }

  private String zoomText() {
    return model == null ? "" : Math.round(model.getZoomFactor() * 100) + "%";
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

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
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JPanel;

/**
 * The column beside the canvas, showing one {@link SideView} at a time.
 *
 * <p>Which one is chosen from the activity bar. The panel owns the heading so every view gets the
 * same one rather than each inventing its own.
 */
public class SidePanel extends JPanel {

  private static final long serialVersionUID = 1L;

  private final Map<String, SideView> views = new LinkedHashMap<>();
  private final CardLayout cards = new CardLayout();
  private final JPanel body = new JPanel(cards);
  private final PanelHeader header = new PanelHeader("");

  private String activeId;

  public SidePanel() {
    super(new BorderLayout());
    add(header, BorderLayout.NORTH);
    add(body, BorderLayout.CENTER);
    applyTheme();
    Theme.addListener(this, this::applyTheme);
  }

  private void applyTheme() {
    final var background = Tokens.color("Logisim.sidePanel.background", getBackground());
    setBackground(background);
    body.setBackground(background);
    revalidate();
    repaint();
  }

  /** Registers a view. The first one registered is shown until another is chosen. */
  public void addView(SideView view) {
    views.put(view.id(), view);
    body.add(view.component(), view.id());
    if (activeId == null) show(view.id());
  }

  /** Shows the view with this id, if it is registered. */
  public void show(String id) {
    final var view = views.get(id);
    if (view == null) return;
    activeId = id;
    cards.show(body, id);
    header.setTitle(view.title());
    header.setActions(view.headerActions());
  }

  public String activeId() {
    return activeId;
  }

  public Iterable<SideView> views() {
    return views.values();
  }

  /** Re-reads every view's title, after a change of language. */
  public void localeChanged() {
    final var view = views.get(activeId);
    if (view != null) header.setTitle(view.title());
  }
}

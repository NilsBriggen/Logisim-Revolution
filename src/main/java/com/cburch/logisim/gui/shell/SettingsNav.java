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
import java.awt.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;

/**
 * The list of pages down the side of a settings window.
 *
 * <p>Ten tabs in a row is a poor way to hold ten pages: the strip wraps or scrolls, the names get
 * cut, and there is nowhere to search. A list has room for each name, keeps the reading order, and
 * can be filtered.
 */
public class SettingsNav extends JPanel {

  private static final long serialVersionUID = 1L;

  /** A page: its name, and where it sits in the window's own order. */
  private record Page(int index, String title) {}

  private final DefaultListModel<Page> model = new DefaultListModel<>();
  private final JList<Page> list = new JList<>(model);
  private final FilterField filter;
  private final IntConsumer onSelect;

  private List<String> titles = List.of();
  private String filterText = "";
  private boolean selecting;

  public SettingsNav(String filterHint, IntConsumer onSelect) {
    super(new BorderLayout());
    this.onSelect = onSelect;

    filter =
        new FilterField(
            filterHint,
            text -> {
              filterText = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
              rebuild();
            });
    final var filterHolder = new JPanel(new BorderLayout());
    filterHolder.setBorder(
        BorderFactory.createEmptyBorder(Spacing.sm(), Spacing.sm(), Spacing.xs(), Spacing.sm()));
    filterHolder.add(filter, BorderLayout.CENTER);
    add(filterHolder, BorderLayout.NORTH);

    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    list.setCellRenderer(new PageRenderer());
    list.addListSelectionListener(
        event -> {
          if (event.getValueIsAdjusting() || selecting) return;
          final var page = list.getSelectedValue();
          if (page != null) onSelect.accept(page.index());
        });
    final var scroll = new JScrollPane(list);
    scroll.setBorder(null);
    add(scroll, BorderLayout.CENTER);

    Theme.addListener(list::repaint);
  }

  /** Sets the page names, in the window's own order. */
  public void setTitles(List<String> titles) {
    this.titles = List.copyOf(titles);
    rebuild();
  }

  /** Marks a page as the one showing, without asking for it to be shown again. */
  public void setSelectedIndex(int index) {
    selecting = true;
    try {
      for (var row = 0; row < model.size(); row++) {
        if (model.get(row).index() == index) {
          list.setSelectedIndex(row);
          list.ensureIndexIsVisible(row);
          return;
        }
      }
      list.clearSelection();
    } finally {
      selecting = false;
    }
  }

  /** The pages the filter currently allows, in order. */
  public List<Integer> visibleIndexes() {
    final var visible = new ArrayList<Integer>();
    for (var row = 0; row < model.size(); row++) visible.add(model.get(row).index());
    return visible;
  }

  /** Whether {@code title} matches {@code text}; the rule the filter uses. */
  public static boolean matches(String title, String text) {
    if (text == null || text.isBlank()) return true;
    if (title == null) return false;
    return title.toLowerCase(Locale.ROOT).contains(text.trim().toLowerCase(Locale.ROOT));
  }

  private void rebuild() {
    final var previous = list.getSelectedValue();
    selecting = true;
    try {
      model.clear();
      for (var index = 0; index < titles.size(); index++) {
        if (matches(titles.get(index), filterText)) model.addElement(new Page(index, titles.get(index)));
      }
    } finally {
      selecting = false;
    }
    if (previous != null) setSelectedIndex(previous.index());
    if (list.getSelectedIndex() < 0 && !model.isEmpty()) {
      // The page that was showing has been filtered out; show the first that is left.
      list.setSelectedIndex(0);
    }
  }

  public void setFilterHint(String hint) {
    filter.setPlaceholder(hint);
  }

  /** Draws a page name, with a little air around it so the list does not read as a table. */
  private static final class PageRenderer extends DefaultListCellRenderer {
    private static final long serialVersionUID = 1L;

    @Override
    public Component getListCellRendererComponent(
        JList<?> source, Object value, int index, boolean selected, boolean focused) {
      super.getListCellRendererComponent(source, value, index, selected, focused);
      setText(((Page) value).title());
      setFont(UiFonts.body());
      if (!selected) setForeground(Tokens.color("List.foreground", getForeground()));
      setBorder(
          BorderFactory.createEmptyBorder(Spacing.xs(), Spacing.md(), Spacing.xs(), Spacing.md()));
      return this;
    }
  }
}

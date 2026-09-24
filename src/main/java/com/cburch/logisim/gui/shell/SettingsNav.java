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

import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.UiFonts;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.IntConsumer;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;

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

  /** Localized page title and searchable setting labels, tooltips and optional aliases. */
  public record SearchPage(String title, String text) {}

  private final DefaultListModel<Page> model = new DefaultListModel<>();
  private final JList<Page> list = new JList<>(model);
  private final FilterField filter;
  private final IntConsumer onSelect;

  private final JPanel emptyState = new JPanel(new GridBagLayout());
  private final JTextArea emptyMessage = new JTextArea(2, 20);
  private final JButton clearButton = new JButton();
  private List<SearchPage> searchPages = List.of();
  private String filterText = "";
  private boolean selecting;
  private int selectedPage = -1;

  public SettingsNav(String filterHint, IntConsumer onSelect) {
    super(new BorderLayout());
    this.onSelect = onSelect;

    filter =
        new FilterField(
            filterHint,
            text -> {
              filterText = text == null ? "" : text.trim();
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
          if (page != null) {
            selectedPage = page.index();
            onSelect.accept(page.index());
          }
        });
    final var scroll = new JScrollPane(list);
    scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
    scroll.setBorder(null);
    add(scroll, BorderLayout.CENTER);

    filter.setKeyboardHandoff(() -> enterResults(1), this::enterResults);
    list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "settings.filter");
    list.getActionMap().put("settings.filter", new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent event) {
        filter.requestFocusInWindow();
      }
    });
    final var gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.weightx = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets = Spacing.formGaps();
    emptyMessage.setEditable(false);
    emptyMessage.setFocusable(false);
    emptyMessage.setLineWrap(true);
    emptyMessage.setWrapStyleWord(true);
    emptyMessage.setOpaque(false);
    emptyState.add(emptyMessage, gbc);
    gbc.gridy++;
    gbc.fill = GridBagConstraints.NONE;
    emptyState.add(clearButton, gbc);
    clearButton.addActionListener(event -> clearFilter());
    setFilterHint(filterHint);

    Theme.addListener(this, () -> {
      emptyMessage.setFont(UiFonts.body());
      filterHolder.setBorder(
          BorderFactory.createEmptyBorder(Spacing.sm(), Spacing.sm(), Spacing.xs(), Spacing.sm()));
      list.revalidate();
      list.repaint();
    });
  }

  /** Sets the page names, in the window's own order. */
  public void setTitles(List<String> titles) {
    setPages(titles.stream().map(title -> new SearchPage(title, "")).toList());
  }

  public void setPages(List<SearchPage> pages) {
    searchPages = List.copyOf(pages);
    rebuild();
  }

  /** Marks a page as the one showing, without asking for it to be shown again. */
  public void setSelectedIndex(int index) {
    if (index >= 0) selectedPage = index;
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
    final var normalized = normalize(title);
    for (final var token : normalize(text).split("\\s+")) {
      if (!normalized.contains(token)) return false;
    }
    return true;
  }

  private static String normalize(String text) {
    return Normalizer.normalize(text, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT)
        .replace("ß", "ss")
        .trim();
  }

  /** Index captions, not user-entered path fields or other setting values. */
  public static String searchableText(Component component) {
    final var text = new StringBuilder(ownText(component));
    if (component instanceof Container container) {
      for (final var child : container.getComponents()) {
        text.append(' ').append(searchableText(child));
      }
    }
    return text.toString();
  }

  private static String ownText(Component component) {
    final var text = new StringBuilder();
    if (component instanceof JLabel label) append(text, label.getText());
    if (component instanceof AbstractButton button) append(text, button.getText());
    if (component instanceof JTextArea area && !area.isEditable()) append(text, area.getText());
    if (component instanceof JComponent control) {
      append(text, control.getToolTipText());
      if (control.getBorder() instanceof TitledBorder border) append(text, border.getTitle());
    }
    return text.toString();
  }

  private static void append(StringBuilder text, String value) {
    if (value != null) text.append(' ').append(value.replaceAll("<[^>]*>", " "));
  }

  /** Scroll the first matching caption into view after the containing card has been laid out. */
  public void revealMatch(JComponent page) {
    if (filterText.isBlank()) return;
    final var match = findMatch(page);
    if (match != null) {
      SwingUtilities.invokeLater(
          () -> match.scrollRectToVisible(new java.awt.Rectangle(match.getSize())));
    }
  }

  private JComponent findMatch(Component component) {
    if (component instanceof JComponent control && matches(ownText(control), filterText)) {
      return control;
    }
    if (component instanceof Container container) {
      for (final var child : container.getComponents()) {
        final var match = findMatch(child);
        if (match != null) return match;
      }
    }
    return null;
  }

  public void clearFilter() {
    filter.clear();
    filter.requestFocusInWindow();
  }

  public JComponent getEmptyState() {
    return emptyState;
  }

  private void enterResults(int direction) {
    if (model.isEmpty()) return;
    final var row = direction < 0 ? model.size() - 1 : Math.max(0, list.getSelectedIndex());
    list.setSelectedIndex(row);
    list.ensureIndexIsVisible(row);
    list.requestFocusInWindow();
  }

  private void rebuild() {
    selecting = true;
    try {
      model.clear();
      for (var index = 0; index < searchPages.size(); index++) {
        final var page = searchPages.get(index);
        if (matches(page.title() + " " + page.text(), filterText)) {
          model.addElement(new Page(index, page.title()));
        }
      }
    } finally {
      selecting = false;
    }
    if (selectedPage >= 0) setSelectedIndex(selectedPage);
    if (model.isEmpty()) {
      onSelect.accept(-1);
      return;
    }
    if (list.getSelectedIndex() < 0 && !model.isEmpty()) {
      // The page that was showing has been filtered out; show the first that is left.
      list.setSelectedIndex(0);
    } else {
      // Reopening the remembered page also replaces the no-results card after clearing.
      onSelect.accept(list.getSelectedValue().index());
    }
  }

  public void setFilterHint(String hint) {
    filter.setPlaceholder(hint);
    emptyMessage.setFont(UiFonts.body());
    emptyMessage.setText(S.get("preferencesNoResults"));
    clearButton.setText(S.get("preferencesClearFilter"));
  }

  /** Draws a page name, with a little air around it so the list does not read as a table. */
  private static final class PageRenderer extends DefaultListCellRenderer {
    private static final long serialVersionUID = 1L;

    @Override
    public Component getListCellRendererComponent(
        JList<?> source, Object value, int index, boolean selected, boolean focused) {
      super.getListCellRendererComponent(source, value, index, selected, focused);
      setText(((Page) value).title());
      setToolTipText(((Page) value).title());
      setFont(UiFonts.body());
      if (!selected) setForeground(Tokens.color("List.foreground", getForeground()));
      setBorder(
          BorderFactory.createEmptyBorder(Spacing.xs(), Spacing.md(), Spacing.xs(), Spacing.md()));
      return this;
    }
  }
}

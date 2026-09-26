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

import com.cburch.logisim.gui.generic.SettingsForm;
import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.util.UiScale;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import javax.swing.AbstractButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.JSpinner;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.TitledBorder;
import javax.swing.text.JTextComponent;

/**
 * Lists the individual controls of a settings page, so that a search can find a setting rather
 * than only the page it is on.
 *
 * <p>The page is read as it is built, so the index always carries the labels in the current
 * language and each control's current value. Pages laid out with a {@link SettingsForm} are read
 * entry by entry, which gives the section a setting sits in and the hint under it; any other
 * layout is walked for labelled controls, check boxes and buttons.
 */
public final class SettingsIndex {

  /** The most options of a drop-down that are indexed; a font list runs to hundreds. */
  static final int MAX_OPTIONS = 40;

  /**
   * One control of a page.
   *
   * @param title its label, or the text of a check box or button
   * @param section the heading it sits under on the page, or the empty string
   * @param value its current value as the page shows it, or the empty string
   * @param keywords further text to find it by: its options, hint and tooltips
   * @param control the control itself, to reveal and focus
   */
  public record Setting(
      String title, String section, String value, String keywords, JComponent control) {}

  /**
   * One page of a settings window.
   *
   * @param title the page title
   * @param text the page's own search text, as the window's page filter uses it
   * @param settings its controls, in reading order
   */
  public record Page(String title, String text, List<Setting> settings) {}

  private SettingsIndex() {
    throw new UnsupportedOperationException("Utility class, do not instantiate.");
  }

  /** The controls of {@code page}, in reading order. */
  public static List<Setting> collect(Component page) {
    final var labels = new IdentityHashMap<Component, JLabel>();
    findLabels(page, labels);
    final var walker = new Walker(labels);
    walker.walk(page, "");
    return List.copyOf(walker.settings);
  }

  /**
   * Brings {@code control} into view on its page: unfolds the sections around it, scrolls to it,
   * gives it the focus when it can take it, and briefly outlines it so the eye finds it.
   *
   * <p>Call it once the page holding the control is showing.
   */
  public static void reveal(JComponent control) {
    for (var parent = control.getParent(); parent != null; parent = parent.getParent()) {
      if (parent instanceof SectionPanel section && !section.isExpanded()) {
        section.setExpanded(true);
      }
    }
    // After the unfolded sections and the newly shown page have been laid out.
    SwingUtilities.invokeLater(
        () -> {
          final var root = SwingUtilities.getRootPane(control);
          if (root != null) root.validate();
          // Room below as well as above, so the control does not end up on the page's last line.
          final var margin = Math.max(UiScale.scaled(REVEAL_MARGIN), 2 * control.getHeight());
          control.scrollRectToVisible(
              new Rectangle(0, -margin, control.getWidth(), control.getHeight() + 2 * margin));
          if (control.isFocusable() && control.isEnabled()) control.requestFocusInWindow();
          Flash.show(control);
        });
  }

  /** Least room kept above and below a revealed control, so it is not left at the page edge. */
  private static final int REVEAL_MARGIN = 24;

  /**
   * The outline drawn around a revealed control, on the window's glass pane so the control and
   * its layout are left untouched. It stays for a moment, then fades.
   */
  private static final class Flash extends JComponent {
    private static final long serialVersionUID = 1L;
    private static final int HOLD_MS = 1200;
    private static final int FADE_MS = 400;
    private static final int TICK_MS = 40;
    private static Flash current;

    private final JComponent target;
    private final Component previousGlass;
    private final boolean previousGlassVisible;
    private final Timer timer;
    private final long start = System.currentTimeMillis();
    private float alpha = 1f;

    private Flash(JComponent target, Component previousGlass) {
      this.target = target;
      this.previousGlass = previousGlass;
      this.previousGlassVisible = previousGlass.isVisible();
      setOpaque(false);
      timer = new Timer(TICK_MS, event -> tick());
    }

    static void show(JComponent target) {
      if (current != null) current.finish();
      final var root = SwingUtilities.getRootPane(target);
      if (root == null || !target.isShowing()) return;
      final var flash = new Flash(target, root.getGlassPane());
      current = flash;
      root.setGlassPane(flash);
      flash.setVisible(true);
      flash.timer.start();
    }

    private void tick() {
      final var elapsed = System.currentTimeMillis() - start;
      if (elapsed >= HOLD_MS + FADE_MS || !target.isShowing()) {
        finish();
        return;
      }
      alpha = elapsed <= HOLD_MS ? 1f : 1f - (elapsed - HOLD_MS) / (float) FADE_MS;
      repaint();
    }

    private void finish() {
      timer.stop();
      if (current == this) current = null;
      final var root = SwingUtilities.getRootPane(this);
      if (root != null && root.getGlassPane() == this) {
        root.setGlassPane(previousGlass);
        previousGlass.setVisible(previousGlassVisible);
        root.repaint();
      }
    }

    @Override
    protected void paintComponent(Graphics graphics) {
      final var visible = target.getVisibleRect();
      if (visible.isEmpty()) return;
      final var bounds = SwingUtilities.convertRectangle(target, visible, this);
      final var inset = UiScale.scaled(3);
      final var g = (Graphics2D) graphics.create();
      try {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.max(0f, alpha)));
        g.setColor(Tokens.accent());
        g.setStroke(new BasicStroke(UiScale.scaled(2)));
        final var arc = UiScale.scaled(8);
        g.drawRoundRect(
            bounds.x - inset, bounds.y - inset,
            bounds.width + 2 * inset, bounds.height + 2 * inset, arc, arc);
      } finally {
        g.dispose();
      }
    }
  }

  /** Maps every labelled control to its label, wherever on the page the label is. */
  private static void findLabels(Component component, Map<Component, JLabel> labels) {
    if (component instanceof JLabel label && label.getLabelFor() != null && hasText(label)) {
      labels.putIfAbsent(label.getLabelFor(), label);
    }
    if (component instanceof Container container) {
      for (final var child : container.getComponents()) findLabels(child, labels);
    }
  }

  private static final class Walker {
    private final Map<Component, JLabel> labels;
    private final List<Setting> settings = new ArrayList<>();

    Walker(Map<Component, JLabel> labels) {
      this.labels = labels;
    }

    void walk(Component component, String section) {
      walk(component, section, false);
    }

    /**
     * @param folded whether {@code component} is the content of a folded section, which is hidden
     *     but still holds settings to be found; revealing one unfolds the section
     */
    private void walk(Component component, String section, boolean folded) {
      if (component instanceof SectionPanel panel) {
        walk(panel.getContent(), panel.getTitle(), true);
        return;
      }
      if (!folded && !component.isVisible()) return;
      if (component instanceof SettingsForm form) {
        walkForm(form, section);
        return;
      }
      if (component instanceof JComponent control
          && control.getBorder() instanceof TitledBorder border
          && border.getTitle() != null
          && !border.getTitle().isBlank()) {
        section = border.getTitle().trim();
      }
      final var label = labels.get(component);
      if (label != null && component instanceof JComponent control) {
        add(label.getText(), section, control, label);
        return;
      }
      if (component instanceof JLabel) return;
      if (component instanceof AbstractButton button) {
        if (hasText(button)) add(button.getText(), section, button, null);
        return;
      }
      if (component instanceof Container container) {
        for (final var child : container.getComponents()) walk(child, section);
      }
    }

    private void walkForm(SettingsForm form, String section) {
      for (final var entry : form.getEntries()) {
        switch (entry.kind()) {
          case SECTION -> {
            if (entry.control() instanceof JLabel heading && hasText(heading)) {
              section = clean(heading.getText());
            }
          }
          case ROW -> {
            final var label = entry.label();
            if (!label.isVisible() || !hasText(label)) continue;
            final var target =
                label.getLabelFor() instanceof JComponent labelled ? labelled : entry.control();
            add(label.getText(), section, target, label);
          }
          case HINT -> {
            if (entry.control().isVisible() && !settings.isEmpty()) {
              addKeywords(settings.size() - 1, textOf(entry.control()));
            }
          }
          default -> walk(entry.control(), section);
        }
      }
    }

    private void add(String title, String section, JComponent control, JLabel label) {
      final var cleanTitle = clean(title);
      if (cleanTitle.isEmpty()) return;
      var value = valueOf(control);
      if (value.equals(cleanTitle)) value = "";
      final var keywords = new StringBuilder(value);
      append(keywords, optionsOf(control));
      append(keywords, control.getToolTipText());
      if (label != null) append(keywords, label.getToolTipText());
      settings.add(new Setting(cleanTitle, section, value, keywords.toString().trim(), control));
    }

    private void addKeywords(int index, String more) {
      final var setting = settings.get(index);
      final var keywords = new StringBuilder(setting.keywords());
      append(keywords, more);
      settings.set(
          index,
          new Setting(
              setting.title(),
              setting.section(),
              setting.value(),
              keywords.toString().trim(),
              setting.control()));
    }
  }

  /**
   * What {@code component} is set to, as the page shows it: a check box's state, a drop-down's
   * choice, a spinner's number, the text of a field or of a key-binding button.
   */
  public static String valueOf(Component component) {
    if (component instanceof JRadioButton radio) {
      return radio.isSelected() ? S.get("searchSettingSelected") : "";
    }
    if (component instanceof JToggleButton toggle) {
      return toggle.isSelected() ? S.get("searchSettingOn") : S.get("searchSettingOff");
    }
    if (component instanceof AbstractButton button) {
      // A button in a row stands for its value: a key binding, or a colour whose tooltip is its hex.
      if (hasText(button)) return clean(button.getText());
      final var tip = button.getToolTipText();
      return tip == null ? "" : clean(tip);
    }
    if (component instanceof JComboBox<?> combo) {
      return itemText(combo, combo.getSelectedItem());
    }
    if (component instanceof JSpinner spinner) {
      final var value = spinner.getValue();
      return value == null ? "" : value.toString();
    }
    if (component instanceof JSlider slider) {
      return String.valueOf(slider.getValue());
    }
    if (component instanceof JTextComponent text) {
      return text.isEditable() ? text.getText().trim() : "";
    }
    if (component instanceof JScrollPane scroll) {
      final var view = scroll.getViewport().getView();
      return view == null || view instanceof JList<?> ? "" : valueOf(view);
    }
    if (component instanceof Container container && !(component instanceof JLabel)) {
      for (final var child : container.getComponents()) {
        if (!child.isVisible()) continue;
        final var value = valueOf(child);
        if (!value.isEmpty()) return value;
      }
    }
    return "";
  }

  /** The choices a drop-down offers, for finding a setting by one of its values. */
  static String optionsOf(Component component) {
    if (component instanceof JComboBox<?> combo) {
      final var options = new StringBuilder();
      final var count = Math.min(combo.getItemCount(), MAX_OPTIONS);
      for (var index = 0; index < count; index++) {
        append(options, itemText(combo, combo.getItemAt(index)));
      }
      return options.toString().trim();
    }
    if (component instanceof Container container && !(component instanceof AbstractButton)) {
      final var options = new StringBuilder();
      for (final var child : container.getComponents()) append(options, optionsOf(child));
      return options.toString().trim();
    }
    return "";
  }

  /** The text the drop-down shows for {@code item}: its renderer's, when that is a label. */
  private static String itemText(JComboBox<?> combo, Object item) {
    if (item == null) return "";
    try {
      @SuppressWarnings("unchecked")
      final var renderer = (javax.swing.ListCellRenderer<Object>) combo.getRenderer();
      if (renderer != null) {
        final var shown =
            renderer.getListCellRendererComponent(new JList<>(), item, -1, false, false);
        if (shown instanceof JLabel label && hasText(label)) return clean(label.getText());
      }
    } catch (RuntimeException e) {
      // A renderer that needs a real list falls back to the item's own text.
    }
    return clean(item.toString());
  }

  private static String textOf(Component component) {
    if (component instanceof JTextComponent text) return text.getText();
    if (component instanceof JLabel label) return label.getText();
    return "";
  }

  private static boolean hasText(JLabel label) {
    return label.getText() != null && !clean(label.getText()).isEmpty();
  }

  private static boolean hasText(AbstractButton button) {
    return button.getText() != null && !clean(button.getText()).isEmpty();
  }

  private static void append(StringBuilder text, String more) {
    if (more == null || more.isBlank()) return;
    text.append(' ').append(clean(more));
  }

  /** Drops markup, collapses white space and a trailing colon, as labels are written "Name:". */
  static String clean(String text) {
    if (text == null) return "";
    var cleaned = text.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
    if (cleaned.endsWith(":")) cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
    return cleaned;
  }
}

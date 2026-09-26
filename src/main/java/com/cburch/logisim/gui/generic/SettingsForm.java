/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.generic;

import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.UiFonts;
import com.cburch.logisim.util.UiScale;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;

/**
 * The one form layout shared by every Preferences and Project Options page.
 *
 * <p>Each page used to lay out its own form: some flush left, some indented, some centred, with
 * labels right- or left-aligned. This lays every page out the same way:
 *
 * <ul>
 *   <li>content is top-aligned and starts at the page's padding, never centred;
 *   <li>labels sit in a left column as wide as the longest label on the page, so every control
 *       starts at one x;
 *   <li>one setting per row; controls keep their natural width unless they hold a path or a list;
 *   <li>check boxes and actions that have no label start at the label column;
 *   <li>hints sit under their control, in the control column;
 *   <li>sections have one heading style and one gap above them.
 * </ul>
 *
 * <p>When the page is too narrow for two columns, as at a large interface scale in a small window,
 * each label moves above its control instead of pushing the controls out of view.
 *
 * <p>A form nested inside a collapsible {@code SectionPanel} is made with {@link #createNested()},
 * which shares the page's label column so its controls line up with the rest of the page.
 *
 * <p>All gaps are logical sizes, rescaled whenever the form is laid out, so they follow a live
 * change of interface scale.
 */
public class SettingsForm extends JPanel {
  private static final long serialVersionUID = 1L;

  /** Gap between a label and its control, in logical pixels. */
  static final int LABEL_GAP = Spacing.MD;

  /** Gap between two rows, split above and below each row, in logical pixels. */
  static final int ROW_GAP = Spacing.SM;

  /** Extra gap above a section heading, in logical pixels. */
  static final int SECTION_GAP = Spacing.LG;

  /** Indent of a control that depends on the check box or radio button above it. */
  static final int DEPENDENT_INDENT = Spacing.XL;

  /** The labels of a page and of the forms nested in it, which share one column width. */
  private static final class LabelColumn {
    private final List<JComponent> labels = new ArrayList<>();

    int width() {
      var width = 0;
      for (final var label : labels) {
        if (label.isVisible()) width = Math.max(width, label.getPreferredSize().width);
      }
      return width;
    }
  }

  /** The kinds of entry in a form. */
  public enum Kind {
    /** A label and its control. */
    ROW,
    /** A control spanning both columns, from the label column. */
    FULL,
    /** A control spanning both columns, indented under the one above. */
    DEPENDENT,
    /** A control in the control column, without a label. */
    CONTROL,
    /** Secondary text under the previous entry, in the control column. */
    HINT,
    /** A section heading. */
    SECTION
  }

  /**
   * One entry: what it is, its components, and whether its control stretches. The label is null
   * for every kind but {@link Kind#ROW}.
   */
  public record Entry(Kind kind, JLabel label, JComponent control, boolean fill) {}

  private final LabelColumn column;
  private final List<Entry> entries = new ArrayList<>();
  private final ColumnStrut strut;
  private final JComponent filler = new JPanel();
  private Boolean stacked;
  private double arrangedScale = -1;

  /** Creates the top-level form of a page. */
  public SettingsForm() {
    this(new LabelColumn());
  }

  private SettingsForm(LabelColumn column) {
    super(new GridBagLayout());
    this.column = column;
    // Row 0 holds a zero-height strut as wide as the shared label column, and a filler that takes
    // up the spare width so that controls keep their natural size and stay on the left.
    strut = new ColumnStrut(this);
    filler.setOpaque(false);
    filler.setPreferredSize(new Dimension(0, 0));
    filler.setMinimumSize(new Dimension(0, 0));
    add(strut);
    add(filler);
  }

  /**
   * A form to put inside a collapsible section of this page, sharing this page's label column.
   */
  public SettingsForm createNested() {
    return new SettingsForm(column);
  }

  /** Adds a labelled setting whose control keeps its natural width. */
  public void addRow(JLabel label, JComponent control) {
    addRow(label, control, false);
  }

  /**
   * Adds a labelled setting.
   *
   * @param fill whether the control stretches to the page width, for paths and lists
   */
  public void addRow(JLabel label, JComponent control, boolean fill) {
    if (label.getLabelFor() == null) {
      label.setLabelFor(
          control instanceof JScrollPane scroll && scroll.getViewport().getView() != null
              ? scroll.getViewport().getView()
              : control);
    }
    column.labels.add(label);
    add(label);
    add(control);
    entries.add(new Entry(Kind.ROW, label, control, fill));
    stacked = null;
  }

  /** Adds a control without a label, such as a check box or an action, at the label column. */
  public void addFull(JComponent component) {
    addFull(component, false);
  }

  /**
   * Adds a control without a label at the label column.
   *
   * @param fill whether it stretches to the page width, for tables, lists and sections
   */
  public void addFull(JComponent component, boolean fill) {
    addEntry(Kind.FULL, component, fill);
  }

  /** Adds a control that depends on the check box or radio button above it, indented under it. */
  public void addDependent(JComponent component, boolean fill) {
    addEntry(Kind.DEPENDENT, component, fill);
  }

  /** Adds an unlabelled control in the control column, such as a button acting on the row above. */
  public void addControl(JComponent component) {
    addEntry(Kind.CONTROL, component, false);
  }

  /** Adds a hint under the previous row, in the control column. */
  public void addHint(JComponent hint) {
    styleHint(hint);
    addEntry(Kind.HINT, hint, true);
  }

  /** Adds a hint, as wrapping text, under the previous row. Returns it so it can be relabelled. */
  public JTextArea addHint(String text) {
    final var hint = createWrappingText(text);
    addHint(hint);
    return hint;
  }

  /** Adds a section heading. Returns it so it can be relabelled. */
  public JLabel addSection(String title) {
    final var heading = new JLabel(title);
    styleHeading(heading);
    addEntry(Kind.SECTION, heading, false);
    return heading;
  }

  /** Read-only, wrapping, unfocusable text whose height follows the width it is given. */
  public static JTextArea createWrappingText(String text) {
    return new WrappingText(text);
  }

  private void addEntry(Kind kind, JComponent component, boolean fill) {
    add(component);
    entries.add(new Entry(kind, null, component, fill));
    stacked = null;
  }

  /**
   * The entries in the order they are shown, for tools that describe the page, such as the search
   * index of the settings windows.
   */
  public List<Entry> getEntries() {
    return List.copyOf(entries);
  }

  /** Whether the labels currently sit above their controls, for a page too narrow for two. */
  public boolean isStacked() {
    return Boolean.TRUE.equals(stacked);
  }

  /** Lays the entries out in two columns, or with each label above its control. */
  private void arrange(boolean stack) {
    if (Boolean.valueOf(stack).equals(stacked) && !scaleChanged()) return;
    stacked = stack;
    arrangedScale = UiScale.factor();
    final var layout = (GridBagLayout) getLayout();
    final var gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    layout.setConstraints(strut, gbc);
    gbc.gridx = 2;
    gbc.weightx = 1.0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    layout.setConstraints(filler, gbc);

    var row = 1;
    for (final var entry : entries) {
      final var first = row == 1;
      final var control = constraints(row, stack ? 0 : 1);
      control.gridwidth = GridBagConstraints.REMAINDER;
      control.fill = entry.fill() ? GridBagConstraints.HORIZONTAL : GridBagConstraints.NONE;
      switch (entry.kind()) {
        case ROW -> {
          final var label = constraints(row, 0);
          label.anchor = GridBagConstraints.BASELINE_LEADING;
          if (stack) {
            label.gridwidth = GridBagConstraints.REMAINDER;
            label.insets = insets(ROW_GAP / 2, 0, 0, 0);
            row++;
            control.gridy = row;
            control.insets = insets(Spacing.XS / 2, 0, ROW_GAP / 2, 0);
          } else {
            label.insets = insets(ROW_GAP / 2, 0, ROW_GAP / 2, LABEL_GAP);
            control.anchor = GridBagConstraints.BASELINE_LEADING;
            control.insets = insets(ROW_GAP / 2, 0, ROW_GAP / 2, 0);
          }
          layout.setConstraints(entry.label(), label);
        }
        case FULL, DEPENDENT -> {
          control.gridx = 0;
          control.insets =
              insets(ROW_GAP / 2, entry.kind() == Kind.DEPENDENT ? DEPENDENT_INDENT : 0,
                  ROW_GAP / 2, 0);
        }
        case CONTROL -> {
          control.gridwidth = 1;
          control.insets = insets(ROW_GAP / 2, 0, ROW_GAP / 2, 0);
        }
        case HINT -> control.insets = insets(0, 0, ROW_GAP / 2, 0);
        case SECTION -> {
          control.gridx = 0;
          control.insets = insets(first ? ROW_GAP / 2 : SECTION_GAP, 0, ROW_GAP / 2, 0);
        }
        default -> throw new IllegalStateException(entry.kind().toString());
      }
      layout.setConstraints(entry.control(), control);
      row++;
    }
  }

  private boolean scaleChanged() {
    return arrangedScale != UiScale.factor();
  }

  private static GridBagConstraints constraints(int row, int column) {
    final var gbc = new GridBagConstraints();
    gbc.gridx = column;
    gbc.gridy = row;
    gbc.anchor = GridBagConstraints.LINE_START;
    return gbc;
  }

  private static Insets insets(int top, int left, int bottom, int right) {
    return new Insets(
        UiScale.scaled(top), UiScale.scaled(left), UiScale.scaled(bottom), UiScale.scaled(right));
  }

  private static void styleHeading(JLabel heading) {
    heading.setFont(UiFonts.header());
  }

  private static void styleHint(JComponent hint) {
    hint.setFont(UiFonts.caption());
    hint.setForeground(Tokens.mutedForeground());
  }

  /** The width the form needs for two columns. */
  private int twoColumnWidth() {
    arrange(false);
    return super.getPreferredSize().width;
  }

  /** Chooses two columns or stacked labels for {@code width}; zero means "not yet laid out". */
  private void arrangeFor(int width) {
    arrange(width > 0 && width < twoColumnWidth());
  }

  @Override
  public Dimension getPreferredSize() {
    if (isPreferredSizeSet()) return super.getPreferredSize();
    arrangeFor(getWidth());
    return super.getPreferredSize();
  }

  /**
   * The narrowest the form can be: its stacked layout, so a narrow page wraps its labels above
   * the controls rather than letting a grid squeeze every control to its minimum and centre it.
   */
  @Override
  public Dimension getMinimumSize() {
    if (isMinimumSizeSet()) return super.getMinimumSize();
    final var wasStacked = stacked;
    arrange(true);
    final var minimum = super.getPreferredSize();
    if (wasStacked != null) {
      arrange(wasStacked);
    } else {
      stacked = null;
    }
    return minimum;
  }

  @Override
  public void doLayout() {
    arrangeFor(getWidth());
    super.doLayout();
    // A wrapped hint only knows its height once it has been given its width; lay out once more
    // when that height has changed. Column widths do not depend on it, so this settles at once.
    for (final var entry : entries) {
      if (entry.control() instanceof WrappingText hint
          && hint.isVisible()
          && hint.getWidth() > 0
          && hint.getPreferredSize().height != hint.getHeight()) {
        SwingUtilities.invokeLater(this::revalidate);
        return;
      }
    }
  }

  @Override
  public void updateUI() {
    super.updateUI();
    if (entries == null) return;
    stacked = null;
    for (final var entry : entries) {
      if (entry.kind() == Kind.SECTION) styleHeading((JLabel) entry.control());
      if (entry.kind() == Kind.HINT) styleHint(entry.control());
    }
  }

  /** The zero-height component that holds the label column at the shared width. */
  private static final class ColumnStrut extends JComponent {
    private static final long serialVersionUID = 1L;
    private final SettingsForm form;

    ColumnStrut(SettingsForm form) {
      this.form = form;
    }

    @Override
    public Dimension getPreferredSize() {
      // The label and the gap after it, as the label cells are, so every form sharing the column
      // puts its controls at the same x.
      final var labels = form.column.width();
      return new Dimension(
          form.isStacked() || labels == 0 ? 0 : labels + UiScale.scaled(LABEL_GAP), 0);
    }

    @Override
    public Dimension getMinimumSize() {
      return getPreferredSize();
    }
  }

  /**
   * Wrapping text that reports the height it needs at the width the layout gave it.
   *
   * <p>Its preferred width is modest, so a long hint wraps instead of widening the page.
   */
  private static final class WrappingText extends JTextArea {
    private static final long serialVersionUID = 1L;

    /**
     * The widest a hint asks to be, in logical pixels. It is given more when the page is wider, but
     * never widens the page itself.
     */
    private static final int PREFERRED_WIDTH = 300;

    WrappingText(String text) {
      super(text);
      setEditable(false);
      setFocusable(false);
      setOpaque(false);
      setLineWrap(true);
      setWrapStyleWord(true);
      setBorder(null);
    }

    @Override
    public Dimension getPreferredSize() {
      final var metrics = getFontMetrics(getFont());
      var textWidth = 0;
      for (final var line : getText().split("\n", -1)) {
        textWidth = Math.max(textWidth, metrics.stringWidth(line));
      }
      final var insets = getInsets();
      final var width =
          Math.min(textWidth + insets.left + insets.right, UiScale.scaled(PREFERRED_WIDTH));
      // A text area measures its wrapped height at its current width.
      if (getWidth() == 0) setSize(Math.max(1, width), Short.MAX_VALUE);
      return new Dimension(width, super.getPreferredSize().height);
    }

    @Override
    public Dimension getMinimumSize() {
      return new Dimension(Math.min(getPreferredSize().width, UiScale.scaled(120)),
          getPreferredSize().height);
    }
  }
}

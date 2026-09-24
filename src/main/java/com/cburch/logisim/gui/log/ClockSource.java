/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.log;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.std.wiring.Clock;
import com.cburch.logisim.util.JDialogOk;
import com.cburch.logisim.util.StringGetter;
import com.cburch.logisim.util.UiFonts;
import com.cburch.logisim.util.UiScale;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Rectangle;
import java.awt.Toolkit;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;

public class ClockSource extends JDialogOk {

  private static final long serialVersionUID = 1L;
  private final ComponentSelector selector;
  private final ChooserContent content;
  private final StringGetter msg;
  SignalInfo item;

  public ClockSource(StringGetter msg, Circuit circ, boolean requireDriveable) {
    super("Clock Source Selection", true);
    this.msg = msg;

    selector =
        new ComponentSelector(
            circ,
            requireDriveable
                ? ComponentSelector.DRIVEABLE_CLOCKS
                : ComponentSelector.OBSERVEABLE_CLOCKS);

    selector.setFont(UiFonts.body());
    selector.setRowHeight(Math.max(UiScale.scaled(24),
        selector.getFontMetrics(selector.getFont()).getHeight() + UiScale.scaled(8)));
    content = new ChooserContent(selector, selector.getRowHeight());
    getContentPane().add(content, BorderLayout.CENTER);

    localeChanged();

    final var configuration = getGraphicsConfiguration();
    final var work = new Rectangle(configuration.getBounds());
    final var screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(configuration);
    work.x += screenInsets.left;
    work.y += screenInsets.top;
    work.width -= screenInsets.left + screenInsets.right;
    work.height -= screenInsets.top + screenInsets.bottom;
    final var width = Math.min(work.width, Math.max(UiScale.scaled(420),
        getRootPane().getContentPane().getMinimumSize().width));
    content.measure(width - UiScale.scaled(24));
    pack();
    final var preferred = getSize();
    preferred.width = Math.max(width, preferred.width);
    setSize(boundedSize(preferred, work.getSize()));
    setMinimumSize(new Dimension(Math.min(getWidth(),
        getRootPane().getContentPane().getMinimumSize().width),
        Math.min(getHeight(), UiScale.scaled(240))));
    setLocation(Math.max(work.x, Math.min(getX(), work.x + work.width - getWidth())),
        Math.max(work.y, Math.min(getY(), work.y + work.height - getHeight())));
  }

  public void localeChanged() {
    selector.localeChanged();
    content.message.setText(msg.toString());
  }

  static Dimension boundedSize(Dimension measured, Dimension available) {
    return new Dimension(Math.max(1, Math.min(measured.width, available.width)),
        Math.max(1, Math.min(measured.height, available.height)));
  }

  /** Wrapping/scrolled explanation cannot consume the space reserved for selectable signal rows. */
  static final class ChooserContent extends JPanel {
    private final JTextArea message = new JTextArea();
    private final JScrollPane explanation = new JScrollPane(message);
    private final JScrollPane signals;
    private final int rowHeight;

    ChooserContent(JComponent selector, int rowHeight) {
      super(new BorderLayout(0, UiScale.scaled(8)));
      this.rowHeight = rowHeight;
      message.setFont(UiFonts.body());
      message.setLineWrap(true);
      message.setWrapStyleWord(true);
      message.setEditable(false);
      message.setFocusable(false);
      message.setOpaque(false);
      explanation.setBorder(null);
      explanation.getViewport().setOpaque(false);
      explanation.setOpaque(false);
      explanation.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
      signals = new JScrollPane(selector, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
          ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
      setBorder(BorderFactory.createEmptyBorder(UiScale.scaled(12), UiScale.scaled(12),
          UiScale.scaled(12), UiScale.scaled(12)));
      add(explanation, BorderLayout.NORTH);
      add(signals, BorderLayout.CENTER);
    }

    void measure(int width) {
      final var insets = getInsets();
      final var textWidth = Math.max(1, width - insets.left - insets.right);
      message.setSize(textWidth, Short.MAX_VALUE);
      explanation.setPreferredSize(new Dimension(textWidth,
          Math.min(message.getPreferredSize().height, 4 * rowHeight)));
      signals.setPreferredSize(new Dimension(textWidth, 6 * rowHeight + signalChromeHeight()));
    }

    private int signalChromeHeight() {
      final var insets = signals.getInsets();
      final var header = signals.getColumnHeader();
      return insets.top + insets.bottom + signals.getHorizontalScrollBar().getPreferredSize().height
          + (header == null ? 0 : header.getPreferredSize().height);
    }

    @Override
    public void doLayout() {
      measure(getWidth());
      final var insets = getInsets();
      final var room = getHeight() - insets.top - insets.bottom - UiScale.scaled(8);
      final var preferred = explanation.getPreferredSize();
      // If the screen/window is short, scroll the explanation and retain at least three rows.
      preferred.height = Math.max(0,
          Math.min(preferred.height, room - 3 * rowHeight - signalChromeHeight()));
      explanation.setPreferredSize(preferred);
      super.doLayout();
    }
  }

  @Override
  public void okClicked() {
    final var list = selector.getSelectedItems();
    if (list == null || list.size() != 1) return;
    item = list.get(0);
  }

  public static Component doClockDriverDialog(Circuit circ) {
    final var dialog = new ClockSource(S.getter("selectClockDriverMessage"), circ, true);
    dialog.setVisible(true);
    return dialog.item == null ? null : dialog.item.getComponent(); // always top-level
  }

  public static SignalInfo doClockMissingObserverDialog(Circuit circ) {
    final var dialog = new ClockSource(S.getter("selectClockMissingMessage"), circ, false);
    dialog.setVisible(true);
    return dialog.item;
  }

  public static SignalInfo doClockMultipleObserverDialog(Circuit circ) {
    final var dialog = new ClockSource(S.getter("selectClockMultipleMessage"), circ, false);
    dialog.setVisible(true);
    return dialog.item;
  }

  public static SignalInfo doClockObserverDialog(Circuit circ) {
    final var dialog = new ClockSource(S.getter("selectClockObserverMessage"), circ, false);
    dialog.setVisible(true);
    return dialog.item;
  }

  public static class CycleInfo {
    public final int hi;
    public final int lo;
    public final int phase;
    public final int ticks;

    public CycleInfo(int h, int l, int p) {
      hi = h;
      lo = l;
      phase = p;
      ticks = hi + lo;
    }
  }

  public static final CycleInfo DEFAULT_CYCLE_INFO = new CycleInfo(1, 1, 0);

  public static CycleInfo getCycleInfo(SignalInfo clockSource) {
    final var clk = clockSource.getComponent();
    if (clk.getFactory() instanceof Clock) {
      final var hi = clk.getAttributeSet().getValue(Clock.ATTR_HIGH);
      final var lo = clk.getAttributeSet().getValue(Clock.ATTR_LOW);
      final var phase = clk.getAttributeSet().getValue(Clock.ATTR_PHASE);
      return new CycleInfo(hi, lo, phase);
    }
    return DEFAULT_CYCLE_INFO;
  }
}

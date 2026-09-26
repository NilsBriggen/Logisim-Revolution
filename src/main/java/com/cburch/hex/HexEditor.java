/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.hex;

import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.prefs.AppPreferences;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Graphics;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JComponent;
import javax.swing.Scrollable;
import javax.swing.SwingConstants;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

public class HexEditor extends JComponent implements Scrollable {
  private static final long serialVersionUID = 1L;

  /** Edits spanning more values than this are not kept for undo (they clear the history). */
  static final long MAX_UNDO_SPAN = 1L << 20;

  /** Number of edits kept for undo. */
  static final int MAX_UNDO_STEPS = 500;

  /** One edit made through the editor: the values from {@code start} before and after it. */
  private record Edit(long start, long[] before, long[] after, boolean typing) {}

  private final Listener listener;
  private final Measures measures;
  private final Caret caret;
  private final Highlighter highlighter;
  private final ArrayDeque<Edit> undoStack = new ArrayDeque<>();
  private final ArrayDeque<Edit> redoStack = new ArrayDeque<>();
  private final List<ChangeListener> historyListeners = new ArrayList<>();
  /** Whether the next typed digit continues the last typing edit (same cell, cursor not moved). */
  private boolean typingRun;
  private HexModel model;

  public HexEditor(HexModel model) {
    this.model = model;
    this.listener = new Listener();
    this.measures = new Measures(this);
    this.caret = new Caret(this);
    this.highlighter = new Highlighter(this);

    setOpaque(true);
    if (model != null) model.addHexModelListener(listener);
    caret.addChangeListener(e -> typingRun = false);

    measures.recompute();
  }

  /**
   * Makes a change to the values from {@code start} to {@code start + length - 1} through the
   * editor, and records it so that {@link #undo} can revert it.
   *
   * <p>The values of the range are read before and after the change, so the change may use any of
   * the model's methods. Changes made to the model directly (such as a running simulation writing
   * to a RAM) are not recorded.
   */
  public void edit(long start, long length, Runnable change) {
    edit(start, length, change, false);
  }

  void edit(long start, long length, Runnable change, boolean typing) {
    if (model == null || length <= 0) {
      change.run();
      return;
    }
    final var first = Math.max(start, model.getFirstOffset());
    final var last = Math.min(start + length - 1, model.getLastOffset());
    if (last < first) {
      change.run();
      return;
    }
    if (last - first + 1 > MAX_UNDO_SPAN) {
      change.run();
      clearHistory();
      return;
    }
    final var before = read(first, last);
    change.run();
    final var after = read(first, last);
    if (Arrays.equals(before, after)) return;
    final var previous = undoStack.peekLast();
    if (typing && typingRun && previous != null && previous.typing() && previous.start() == first) {
      // the digits typed into one cell are one step
      undoStack.removeLast();
      undoStack.addLast(new Edit(first, previous.before(), after, true));
    } else {
      undoStack.addLast(new Edit(first, before, after, typing));
      while (undoStack.size() > MAX_UNDO_STEPS) undoStack.removeFirst();
    }
    typingRun = typing;
    redoStack.clear();
    fireHistoryChanged();
  }

  private long[] read(long first, long last) {
    final var values = new long[(int) (last - first + 1)];
    for (var i = 0; i < values.length; i++) values[i] = model.get(first + i);
    return values;
  }

  public boolean canUndo() {
    return !undoStack.isEmpty();
  }

  public boolean canRedo() {
    return !redoStack.isEmpty();
  }

  /** Reverts the last edit made through the editor. */
  public void undo() {
    final var edit = undoStack.pollLast();
    if (edit == null || model == null) return;
    model.set(edit.start(), edit.before());
    redoStack.addLast(edit);
    caret.setDot(edit.start(), false);
    fireHistoryChanged();
  }

  /** Makes the last undone edit again. */
  public void redo() {
    final var edit = redoStack.pollLast();
    if (edit == null || model == null) return;
    model.set(edit.start(), edit.after());
    undoStack.addLast(edit);
    caret.setDot(edit.start(), false);
    fireHistoryChanged();
  }

  public void clearHistory() {
    if (undoStack.isEmpty() && redoStack.isEmpty()) return;
    undoStack.clear();
    redoStack.clear();
    fireHistoryChanged();
  }

  /** Registers a listener told whenever {@link #canUndo} or {@link #canRedo} may have changed. */
  public void addHistoryListener(ChangeListener l) {
    historyListeners.add(l);
  }

  private void fireHistoryChanged() {
    final var event = new ChangeEvent(this);
    for (final var l : new ArrayList<>(historyListeners)) l.stateChanged(event);
  }

  public Object addHighlight(int start, int end, Color color) {
    return highlighter.add(start, end, color);
  }

  public void delete() {
    long p0 = caret.getMark();
    long p1 = caret.getDot();
    if (p0 < 0 || p1 < 0) return;
    if (p0 > p1) {
      long t = p0;
      p0 = p1;
      p1 = t;
    }
    final var start = p0;
    final var length = p1 - p0 + 1;
    edit(start, length, () -> model.fill(start, length, 0));
  }

  public Caret getCaret() {
    return caret;
  }

  Highlighter getHighlighter() {
    return highlighter;
  }

  Measures getMeasures() {
    return measures;
  }

  public HexModel getModel() {
    return model;
  }

  public void setModel(HexModel value) {
    if (model == value) return;
    if (model != null) model.removeHexModelListener(listener);
    model = value;
    undoStack.clear();
    redoStack.clear();
    fireHistoryChanged();
    highlighter.clear();
    caret.setDot(-1, false);
    if (model != null) model.addHexModelListener(listener);
    measures.recompute();
  }

  //
  // Scrollable methods
  //
  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return getPreferredSize();
  }

  @Override
  public int getScrollableBlockIncrement(Rectangle vis, int orientation, int direction) {
    if (orientation == SwingConstants.VERTICAL) {
      int height = measures.getCellHeight();
      if (height < 1) {
        measures.recompute();
        height = measures.getCellHeight();
        if (height < 1) return 19 * vis.height / 20;
      }
      int lines = Math.max(1, (vis.height / height) - 1);
      return lines * height;
    } else {
      return 19 * vis.width / 20;
    }
  }

  @Override
  public boolean getScrollableTracksViewportHeight() {
    return false;
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return true;
  }

  @Override
  public int getScrollableUnitIncrement(Rectangle vis, int orientation, int direction) {
    if (orientation == SwingConstants.VERTICAL) {
      int ret = measures.getCellHeight();
      if (ret < 1) {
        measures.recompute();
        ret = measures.getCellHeight();
        if (ret < 1) return 1;
      }
      return ret;
    } else {
      return Math.max(1, vis.width / 20);
    }
  }

  @Override
  protected void paintComponent(Graphics gfx) {
    if (AppPreferences.AntiAliassing.getBoolean()) {
      final var g2 = (Graphics2D) gfx;
      g2.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }
    measures.ensureComputed(gfx);

    final var clip = gfx.getClipBounds();
    if (isOpaque()) {
      gfx.setColor(getBackground());
      gfx.fillRect(clip.x, clip.y, clip.width, clip.height);
    }

    var addr0 = model.getFirstOffset();
    var addr1 = model.getLastOffset();

    var xaddr0 = measures.toAddress(0, clip.y);
    if (xaddr0 == addr0) xaddr0 = measures.getBaseAddress(model);
    long xaddr1 = measures.toAddress(getWidth(), clip.y + clip.height) + 1;
    highlighter.paint(gfx, xaddr0, xaddr1);

    gfx.setColor(getForeground());
    final var baseFont = gfx.getFont();
    final var baseFm = gfx.getFontMetrics(baseFont);
    final var labelFont = baseFont;
    final var labelFm = gfx.getFontMetrics(labelFont);
    var cols = measures.getColumnCount();
    var baseX = measures.getBaseX();
    var baseY = measures.toY(xaddr0) + baseFm.getAscent() + baseFm.getLeading() / 2;
    var dy = measures.getCellHeight();
    var labelWidth = measures.getLabelWidth();
    var labelChars = measures.getLabelChars();
    var cellWidth = measures.getCellWidth();
    var cellChars = measures.getCellChars();
    for (var a = xaddr0; a < xaddr1; a += cols, baseY += dy) {
      final var label = toHex(a, labelChars);
      gfx.setFont(labelFont);
      // The address ruler is not data: it is quieter, and upright rather than italic, so the eye
      // finds the first byte column without reading the addresses.
      gfx.setColor(Tokens.mutedForeground());
      gfx.drawString(
          label, baseX - labelWidth + (labelWidth - labelFm.stringWidth(label)) / 2, baseY);
      gfx.setColor(getForeground());
      gfx.setFont(baseFont);
      var b = a;
      for (var j = 0; j < cols; j++, b++) {
        if (b >= addr0 && b <= addr1) {
          final var val = toHex(model.get(b), cellChars);
          final var x = measures.toX(b) + (cellWidth - baseFm.stringWidth(val)) / 2;
          gfx.drawString(val, x, baseY);
        }
      }
    }

    caret.paintForeground(gfx, xaddr0, xaddr1);
  }

  public void removeHighlight(Object tag) {
    highlighter.remove(tag);
  }

  public void scrollAddressToVisible(int start, int end) {
    if (start < 0 || end < 0) return;
    int x0 = measures.toX(start);
    int x1 = measures.toX(end) + measures.getCellWidth();
    int y0 = measures.toY(start);
    int y1 = measures.toY(end);
    int h = measures.getCellHeight();
    if (y0 == y1) {
      scrollRectToVisible(new Rectangle(x0, y0, x1 - x0, h));
    } else {
      scrollRectToVisible(new Rectangle(x0, y0, x1 - x0, (y1 + h) - y0));
    }
  }

  public void selectAll() {
    caret.setDot(model.getLastOffset(), false);
    caret.setDot(0, true);
  }

  //
  // selection methods
  //
  public boolean selectionExists() {
    return caret.getMark() >= 0 && caret.getDot() >= 0;
  }

  @Override
  public void setBounds(int x, int y, int width, int height) {
    super.setBounds(x, y, width, height);
    measures.widthChanged();
  }

  @Override
  public void setFont(Font value) {
    super.setFont(value);
    measures.recompute();
  }

  private String toHex(long value, int chars) {
    final var ret = String.format("%0" + chars + "x", value);
    return (ret.length() > chars) ? ret.substring(ret.length() - chars) : ret;
  }

  private class Listener implements HexModelListener {
    @Override
    public void bytesChanged(HexModel source, long start, long numBytes, long[] oldValues) {
      repaint(
          0,
          measures.toY(start),
          getWidth(),
          measures.toY(start + numBytes) + measures.getCellHeight());
    }

    @Override
    public void metainfoChanged(HexModel source) {
      measures.recompute();
      repaint();
    }
  }
}

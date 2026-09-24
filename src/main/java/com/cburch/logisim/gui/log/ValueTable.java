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

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.UiFonts;
import com.cburch.logisim.util.UiScale;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.MouseEvent;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JScrollPane;
import javax.swing.ToolTipManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/**
 * Code taken from Cornell's version of Logisim: http://www.cs.cornell.edu/courses/cs3410/2015sp/
 */
public class ValueTable extends JPanel {

  private static final long serialVersionUID = 1L;
  /**
   * Returns the font for column headers.
   *
   * <p>Derived per call rather than cached in a constant so that a look and feel change or a new
   * interface scale is picked up without restarting.
   */
  private static Font headFont() {
    return UiFonts.bodyBold();
  }

  /** Returns the font for cell values, monospaced so that columns of values line up. */
  private static Font bodyFont() {
    return UiFonts.mono();
  }

  private static int columnGap() {
    return UiScale.scaled(8);
  }

  private static int headerGap() {
    return UiScale.scaled(4);
  }
  private final TableHeader header;
  private final TableBody body;
  private final VerticalScrollBar vsb;
  private final JScrollPane scrollPane;
  // cached copy of rows that are visible
  private Cell[][] rowData;
  private int rowStart;
  private int rowCount;
  private int[] columnWidth;
  private int cellHeight;
  private int tableWidth;
  private int tableHeight;
  private Model model;

  public ValueTable(Model model) {
    header = new TableHeader();
    body = new TableBody();
    vsb = new VerticalScrollBar();

    scrollPane =
        new JScrollPane(
            body,
            JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scrollPane.setVerticalScrollBar(vsb);
    scrollPane.setColumnHeaderView(header);
    scrollPane.getViewport().setBorder(null);
    final var b = scrollPane.getViewportBorder();
    scrollPane.setViewportBorder(null);
    scrollPane.setBorder(b);
    setLayout(new BorderLayout());
    add(scrollPane);

    ToolTipManager.sharedInstance().registerComponent(header);
    ToolTipManager.sharedInstance().registerComponent(body);

    setModel(model);
    Theme.addListener(this, this::modelChanged);
  }

  private void computePreferredSize() {
    final var oldCellHeight = cellHeight;
    final var oldTableWidth = tableWidth;
    final var oldTableHeight = tableHeight;
    final var columns = model == null ? 0 : model.getColumnCount();
    final int defaultWidth = UiScale.scaled(24);
    final var headerMetric = getFontMetrics(headFont());
    final var bodyMetric = getFontMetrics(bodyFont());
    cellHeight = Math.max(headerMetric.getHeight(), bodyMetric.getHeight()) + UiScale.scaled(4);

    if (columnWidth == null || columnWidth.length < columns) columnWidth = new int[columns];

    if (columns == 0) {
      tableWidth = tableHeight = 0;
    } else {
      var cellsWidth = 0;
      for (var i = 0; i < columns; i++) {
        final var radix = model.getColumnValueRadix(i);
        // column should be at least as wide as 24, as header, and
        // as formatted value
        final var header = model.getColumnName(i);
        var cellWidth = Math.max(defaultWidth, headerMetric.stringWidth(header));
        final var w = model.getColumnValueWidth(i);

        if (w != null) {
          final var val =
              Value.createKnown(
                  w, (radix == 2 ? 0 : (radix == 10 ? (1L << (w.getWidth() - 1)) : w.getMask())));
          final var label = val.toDisplayString(radix);
          cellWidth = Math.max(cellWidth, bodyMetric.stringWidth(label));
        }

        final var special = model.specialColumnEntry(i);
        if (special != null) {
          cellWidth = Math.max(cellWidth, bodyMetric.stringWidth(special));
        }

        // For button columns, ensure width is at least as wide as button text
        if (model.isButtonColumn(i)) {
          // Button columns typically have "Show" or "Set" text, ensure adequate width
          cellWidth = Math.max(cellWidth, bodyMetric.stringWidth(" Show "));
        }

        columnWidth[i] = cellWidth;
        cellsWidth += cellWidth;
      }
      tableWidth = cellsWidth + columnGap() * (columns + 1);
      tableHeight = cellHeight * model.getRowCount();
    }

    if (cellHeight != oldCellHeight
        || tableWidth != oldTableWidth
        || tableHeight != oldTableHeight) {
      final var headSize = new Dimension(tableWidth, cellHeight + headerGap());
      final var bodySize = new Dimension(tableWidth, tableHeight);
      body.setPreferredSize(bodySize);
      header.setPreferredSize(headSize);
      body.revalidate();
      header.revalidate();
    }
  }

  public void dataChanged() {
    rowCount = 0;
    repaint();
  }

  int findColumn(int x, int width) {
    if (columnWidth == null || model == null) {
      computePreferredSize();
    }

    var left = Math.max(0, (width - tableWidth) / 2);
    if (x < left || x >= left + tableWidth) return -1;

    var currentX = left + columnGap();
    final var columns = model.getColumnCount();

    if (columnWidth == null || columnWidth.length < columns) {
      computePreferredSize();
    }

    for (var i = 0; i < columns; i++) {
      final var cellWidth = columnWidth[i];
      final var nextX = currentX + cellWidth + columnGap();

      if (x >= currentX && x < nextX) {
        return i;
      }

      currentX = nextX;
    }
    return -1;
  }

  int findRow(int y, int height) {
    if (y < 0) return -1;
    final var row = y / cellHeight;
    if (model == null || row >= model.getRowCount()) return -1;

    return row;
  }

  public void modelChanged() {
    computePreferredSize();
    dataChanged();
  }

  void refreshData(int top, int bottom) {
    final var columns = model == null ? 0 : model.getColumnCount();

    if (columns == 0) {
      rowCount = 0;
      return;
    }
    final var rows = model.getRowCount();
    if (rows == 0) {
      rowCount = 0;
      return;
    }

    var topRow = Math.min(rows - 1, Math.max(0, top / cellHeight));
    var bottomRow = Math.min(rows - 1, Math.max(0, bottom / cellHeight));

    if (rowData != null
        && rowStart <= topRow
        && topRow < rowStart + rowCount
        && rowStart <= bottomRow
        && bottomRow < rowStart + rowCount) return;

    // we pre-fetch a bit more than strictly visible
    final var rect = scrollPane.getViewport().getViewRect();
    top = rect.y - rect.height / 2;
    bottom = rect.y + rect.height * 2;
    topRow = Math.min(rows - 1, Math.max(0, top / cellHeight - 10));
    bottomRow = Math.min(rows - 1, Math.max(0, bottom / cellHeight + 10));

    rowStart = Math.min(topRow, bottomRow);
    rowCount = Math.max(topRow, bottomRow) - rowStart + 1;

    if (rowCount == 0) return;

    if (rowData == null || rowData.length < rowCount || rowData[0].length != columns)
      rowData = new Cell[rowCount + 1][columns];

    model.getRowData(rowStart, rowCount, rowData);
  }

  public void setModel(Model model) {
    this.model = model;
    modelChanged();
  }

  public interface Model {

    void changeColumnValueRadix(int i);

    int getColumnCount();

    String getColumnName(int i);

    int getColumnValueRadix(int i);

    BitWidth getColumnValueWidth(int i);

    int getRowCount();

    void getRowData(int firstRow, int rowCount, Cell[][] rowData);

    // Optional method for handling row button clicks
    // Returns true if the column is a button column that should handle clicks
    default boolean isButtonColumn(int col) {
      return false;
    }

    String specialColumnEntry(int i);

    // Called when a button in the button column is clicked
    // row is the display row index (after sorting)
    // col is the column index of the button that was clicked
    // modifiersEx is the extended modifiers from the mouse event (can check for Shift, Ctrl, etc.)
    default void handleButtonClick(int row, int col, int modifiersEx) {
      // Default: call the old method for backward compatibility
      handleButtonClick(row, modifiersEx);
    }

    // Called when a button in the button column is clicked (deprecated, use handleButtonClick(int, int, int) instead)
    @Deprecated
    default void handleButtonClick(int row, int modifiersEx) {
      // Default: do nothing (ignore modifiers for backward compatibility)
    }

    // Called when a button in the button column is clicked (deprecated, use handleButtonClick(int, int) instead)
    @Deprecated
    default void handleButtonClick(int row) {
      // Default: do nothing (deprecated method, use handleButtonClick(int, int) instead)
    }
  }

  public static class Cell {

    public final Object value;
    public final Color bg;
    public final Color fg;
    public final String tip;

    public Cell(Object v, Color b, Color f, String t) {
      value = v;
      bg = b;
      fg = f;
      tip = t;
    }
  }

  private class TableBody extends JPanel {

    private static final long serialVersionUID = 1L;

    TableBody() {
      setFocusable(true);
      setRequestFocusEnabled(true);
      addMouseListener(new java.awt.event.MouseAdapter() {
        @Override
        public void mouseClicked(MouseEvent e) {
          if (model == null) return;

          int x = e.getX(); // X is relative to TableBody panel, no adjustment needed
          int y = e.getY(); // The body event already includes the scroll offset.

          // Refresh data to ensure rowStart is current
          refreshData(y, y + cellHeight);

          int col = findColumn(x, getSize().width);
          if (col < 0) return;

          // Check if this is a button column
          if (model.isButtonColumn(col)) {
            // Calculate row from Y coordinate (now adjusted for scroll)
            if (y < 0) return;

            // Calculate absolute row from adjusted Y
            int absoluteRow = y / cellHeight;

            // Check if row is within bounds
            if (absoluteRow >= 0 && absoluteRow < model.getRowCount()) {
              model.handleButtonClick(absoluteRow, col, e.getModifiersEx());
              repaint(); // Refresh display after button click
            }
            return;
          }

          // Otherwise, handle radix change for other columns
          if (col >= 0) model.changeColumnValueRadix(col);
        }
      });
    }

    @Override
    public String getToolTipText(MouseEvent event) {
      int col = model == null ? -1 : findColumn(event.getX(), getSize().width);

      if (col < 0) return null;

      int row = rowData == null ? -1 : findRow(event.getY(), getSize().height);

      if (!(rowStart <= row && row < rowStart + rowCount)) return null;

      final var cell = rowData[row - rowStart][col];

      if (cell == null) return null;

      return cell.tip;
    }

    @Override
    public void paintComponent(Graphics g) {
      super.paintComponent(g);

      final var sz = getSize();

      g.setColor(getForeground());
      g.setFont(bodyFont());

      final var columns = model == null ? 0 : model.getColumnCount();
      if (columns == 0) {
        rowCount = 0;
        GraphicsUtil.drawCenteredText(g, S.get("tableEmptyMessage"), sz.width / 2, sz.height / 2);
        return;
      }

      final var bodyMetric = g.getFontMetrics();
      final var clip = g.getClipBounds();
      refreshData(clip.y, clip.y + clip.height);

      if (rowCount == 0) return;

      final var firstRow = Math.max(0, clip.y / cellHeight);
      final var lastRow = Math.min(model.getRowCount() - 1, (clip.y + clip.height) / cellHeight);

      var top = 0;
      final var left = Math.max(0, (sz.width - tableWidth) / 2);
      var x = left + columnGap();

      final var bg = getBackground();

      for (int col = 0; col < columns; col++) {
        g.setColor(Tokens.divider());
        g.drawLine(x - columnGap() / 2, clip.y, x - columnGap() / 2, clip.y + clip.height);
        g.setColor(getForeground());
        final var cellWidth = columnWidth[col];
        final var radix = model.getColumnValueRadix(col);

        for (var row = firstRow; row <= lastRow; row++) {
          final var y = top + row * cellHeight;
          if (!(rowStart <= row && row < rowStart + rowCount)) continue;
          final var cell = rowData[row - rowStart][col];

          if (cell == null) continue;

          g.setColor(cell.bg == null ? bg : cell.bg);
          g.fillRect(x - columnGap() / 2 + 1, y, cellWidth + columnGap() - 1, cellHeight);
          g.setColor(getForeground());

          if (cell.value != null) {
            final var label =
                (cell.value instanceof Value
                    ? ((Value) cell.value).toDisplayString(radix)
                    : (String) cell.value);
            final var width = bodyMetric.stringWidth(label);

            if (cell.fg != null) g.setColor(cell.fg);

            g.drawString(label, x + (cellWidth - width) / 2,
                y + (cellHeight - bodyMetric.getHeight()) / 2 + bodyMetric.getAscent());

            // Restore the table's own colour, not black: the cell may have set its own.
            if (cell.fg != null) g.setColor(getForeground());
          }
        }
        x += cellWidth + columnGap();
      }
      g.setColor(Tokens.divider());
      g.drawLine(x - columnGap() / 2, clip.y, x - columnGap() / 2, clip.y + clip.height);
    }
  }

  private class TableHeader extends JPanel {

    private static final long serialVersionUID = 1L;

    TableHeader() {
      addMouseListener(new MyListener());
    }

    @Override
    public String getToolTipText(MouseEvent event) {
      int col = model == null ? -1 : findColumn(event.getX(), getSize().width);
      if (col < 0) return null;

      int radix = model.getColumnValueRadix(col);

      if (radix == 0) return null;

      return S.get("tableHeaderHelp", Integer.toString(radix));
    }

    @Override
    public void paintComponent(Graphics g) {
      super.paintComponent(g);

      final var sz = getSize();
      g.setColor(Tokens.divider());

      final var columns = model == null ? 0 : model.getColumnCount();
      if (columns == 0) {
        g.drawLine(0, cellHeight + headerGap() / 2, sz.width, cellHeight + headerGap() / 2);
        return;
      }

      g.setFont(headFont());
      final var headerMetric = g.getFontMetrics();
      final var top = 0;
      final var left = Math.max(0, (sz.width - tableWidth) / 2);

      g.drawLine(left, cellHeight + headerGap() / 2, left + tableWidth, cellHeight + headerGap() / 2);

      var x = left + columnGap();
      final var y = top + (cellHeight - headerMetric.getHeight()) / 2 + headerMetric.getAscent();

      for (var i = 0; i < columns; i++) {
        g.setColor(Tokens.divider());
        g.drawLine(x - columnGap() / 2, 0, x - columnGap() / 2, cellHeight);
        g.setColor(getForeground());
        final var label = model.getColumnName(i);
        final var cellWidth = columnWidth[i];
        final var width = headerMetric.stringWidth(label);
        g.drawString(label, x + (cellWidth - width) / 2, y);
        x += cellWidth + columnGap();
      }

      g.setColor(Tokens.divider());
      g.drawLine(x - columnGap() / 2, 0, x - columnGap() / 2, cellHeight);
    }

    class MyListener extends java.awt.event.MouseAdapter {
      @Override
      public void mouseClicked(MouseEvent e) {
        // X coordinate is relative to TableHeader panel
        final var col = model == null ? -1 : findColumn(e.getX(), getSize().width);
        if (col >= 0) model.changeColumnValueRadix(col);
      }
    }
  }

  private class VerticalScrollBar extends JScrollBar implements ChangeListener {

    private static final long serialVersionUID = 1L;
    private int oldMaximum = -1;
    private int oldExtent = -1;

    public VerticalScrollBar() {
      getModel().addChangeListener(this);
    }

    @Override
    public int getBlockIncrement(int direction) {
      final var curHeight = getVisibleAmount();
      var numCells = curHeight / cellHeight - 1;
      if (numCells <= 0) numCells = 1;
      return numCells * cellHeight;
    }

    @Override
    public int getUnitIncrement(int direction) {
      return cellHeight;
    }

    @Override
    public void stateChanged(ChangeEvent event) {
      final var newMaximum = getMaximum();
      final var newExtent = getVisibleAmount();
      if (oldMaximum != newMaximum || oldExtent != newExtent) {
        if (getValue() + oldExtent >= oldMaximum) {
          setValue(newMaximum - newExtent);
        }
        oldMaximum = newMaximum;
        oldExtent = newExtent;
      }
    }
  }
}

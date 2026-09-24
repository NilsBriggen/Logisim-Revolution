/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell;

import com.cburch.logisim.util.UiFonts;
import com.cburch.logisim.util.UiScale;
import java.awt.BorderLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSplitPane;

/**
 * How the main window is put together: a toolbar across the top, an activity bar and side panel on
 * the left, the editor in the middle with a drawer beneath it, an inspector on the right, and a
 * status bar along the bottom.
 *
 * <p>A panel is hidden by taking it out of its split rather than by making it invisible, because a
 * Swing split pane still reserves space for a child that is merely invisible. Its size is
 * remembered so that bringing it back puts it where it was.
 */
public class ShellLayout extends JPanel {

  private static final long serialVersionUID = 1L;

  /** Thickness of a divider, in pixels, when the panel beside it is showing. */
  private static final int DIVIDER_SIZE = 5;

  private final ActivityBar activityBar;
  private final SidePanel sidePanel;
  private final EditorArea editorArea;
  private final Inspector inspector;
  private final BottomPanel bottomPanel;
  private final StatusBar statusBar;

  private final SizedSplit sideSplit;
  private final SizedSplit inspectorSplit;
  private final SizedSplit bottomSplit;

  public ShellLayout(
      MainToolbar toolbar,
      ActivityBar activityBar,
      SidePanel sidePanel,
      EditorArea editorArea,
      Inspector inspector,
      BottomPanel bottomPanel,
      StatusBar statusBar) {
    super(new BorderLayout());
    this.activityBar = activityBar;
    this.sidePanel = sidePanel;
    this.editorArea = editorArea;
    this.inspector = inspector;
    this.bottomPanel = bottomPanel;
    this.statusBar = statusBar;

    bottomSplit = split(
        JSplitPane.VERTICAL_SPLIT,
        editorArea,
        bottomPanel,
        true,
        LayoutPrefs::bottomHeight,
        LayoutPrefs::setBottomHeight);
    bottomSplit.setResizeWeight(1.0);
    inspectorSplit = split(
        JSplitPane.HORIZONTAL_SPLIT,
        bottomSplit,
        inspector,
        true,
        LayoutPrefs::inspectorWidth,
        LayoutPrefs::setInspectorWidth);
    inspectorSplit.setResizeWeight(1.0);
    sideSplit = split(
        JSplitPane.HORIZONTAL_SPLIT,
        sidePanel,
        inspectorSplit,
        false,
        LayoutPrefs::sideWidth,
        LayoutPrefs::setSideWidth);
    sideSplit.setResizeWeight(0.0);
    sideSplit.beforeAllocation = this::allocateHorizontalSpace;

    // Minimum sizes keep a panel from being dragged away to nothing; the stored sizes are applied
    // on the first layout, once the split panes have a width to place a divider within.
    sidePanel.setMinimumSize(new java.awt.Dimension(UiScale.scaled(LayoutPrefs.MIN_PANEL), 0));
    inspector.setMinimumSize(new java.awt.Dimension(UiScale.scaled(LayoutPrefs.MIN_PANEL), 0));

    final var middle = new JPanel(new BorderLayout());
    middle.add(activityBar, BorderLayout.LINE_START);
    middle.add(sideSplit, BorderLayout.CENTER);

    add(toolbar, BorderLayout.NORTH);
    add(middle, BorderLayout.CENTER);
    add(statusBar, BorderLayout.SOUTH);

    restoreFromPreferences();
  }

  /** Puts every divider back where the user left it, next time each pane lays itself out. */
  private void applyStoredSizes() {
    sideSplit.reapply();
    inspectorSplit.reapply();
    bottomSplit.reapply();
  }

  @Override
  public void doLayout() {
    updatePanelMinimum(sidePanel);
    updatePanelMinimum(inspector);
    sideSplit.setDividerSize(isSideVisible() ? UiScale.scaled(DIVIDER_SIZE) : 0);
    inspectorSplit.setDividerSize(isInspectorVisible() ? UiScale.scaled(DIVIDER_SIZE) : 0);
    bottomSplit.setDividerSize(isBottomVisible() ? UiScale.scaled(DIVIDER_SIZE) : 0);
    super.doLayout();
  }

  private static void updatePanelMinimum(JComponent panel) {
    final var textWidth = panel.getFontMetrics(panel.getFont()).charWidth('m') * 14;
    final var size = new java.awt.Dimension(
        Math.max(UiScale.scaled(LayoutPrefs.MIN_PANEL), textWidth + UiScale.scaled(16)), 0);
    if (!size.equals(panel.getMinimumSize())) panel.setMinimumSize(size);
  }

  private void allocateHorizontalSpace() {
    final var textWidth = editorArea.getFontMetrics(UiFonts.mono()).charWidth('m') * 32;
    final var centerMinimum = Math.max(UiScale.scaled(320), textWidth + UiScale.scaled(48));
    allocateHorizontalSpace(sideSplit, inspectorSplit, centerMinimum);
  }

  /** Reserve a useful editor viewport without changing the user's desired widths or visibility. */
  private static void allocateHorizontalSpace(
      SizedSplit side, SizedSplit inspector, int centerMinimum) {
    final var leftWidth = side.hasBothChildren() ? side.requestedSize() : 0;
    final var rightWidth = inspector.hasBothChildren() ? inspector.requestedSize() : 0;
    final var insets = side.getInsets();
    final var available = Math.max(0, side.getWidth() - insets.left - insets.right
        - (side.hasBothChildren() ? side.dividerExtent() : 0)
        - (inspector.hasBothChildren() ? inspector.dividerExtent() : 0));
    final var budget = Math.max(0, available - centerMinimum);
    final var requested = (long) leftWidth + rightWidth;
    if (requested > budget) {
      final var left = (int) (budget * (long) leftWidth / requested);
      side.allocate(left);
      inspector.allocate(budget - left);
    } else {
      side.allocate(-1);
      inspector.allocate(-1);
    }
  }

  private static SizedSplit split(
      int orientation,
      JComponent first,
      JComponent second,
      boolean storedIsSecond,
      java.util.function.IntSupplier storedSize,
      java.util.function.IntConsumer sizeChanged) {
    final var pane =
        new SizedSplit(orientation, first, second, storedIsSecond, storedSize, sizeChanged);
    pane.setBorder(null);
    pane.setDividerSize(UiScale.scaled(DIVIDER_SIZE));
    pane.setContinuousLayout(true);
    return pane;
  }

  /**
   * A split pane that keeps one of its children at a remembered size.
   *
   * <p>Restore on initialization, explicit restore and extent changes. Ordinary layout passes
   * must leave the divider where the user is dragging it. Temporary window constraints do not
   * replace the user's chosen size.
   */
  static final class SizedSplit extends JSplitPane {
    private static final long serialVersionUID = 1L;

    private final boolean storedIsSecond;
    private final java.util.function.IntSupplier storedSize;
    private final java.util.function.IntConsumer sizeChanged;

    private boolean restorePending = true;
    private boolean dragging;
    private int lastExtent = -1;
    private int wantedSize;
    private double lastScale = UiScale.factor();
    private java.awt.Component watchedDivider;
    private Runnable beforeAllocation;
    private int allocatedSize = -1;
    private boolean allocationChanged;
    private int dragStartLocation;

    SizedSplit(
        int orientation,
        JComponent first,
        JComponent second,
        boolean storedIsSecond,
        java.util.function.IntSupplier storedSize,
        java.util.function.IntConsumer sizeChanged) {
      super(orientation, true, first, second);
      this.storedIsSecond = storedIsSecond;
      this.storedSize = storedSize;
      this.sizeChanged = sizeChanged;
    }

    /**
     * Records the size only when the user has dragged the divider.
     *
     * <p>Laying the window out moves dividers too, at sizes that have nothing to do with what the
     * user chose, so listening for the position to change would let the remembered size drift
     * every time the window was built. Watching the divider itself is exact.
     */
    private void watchForDrags() {
      if (!(getUI() instanceof javax.swing.plaf.basic.BasicSplitPaneUI splitUi)) return;
      final var divider = splitUi.getDivider();
      if (divider == null || divider == watchedDivider) return;
      watchedDivider = divider;
      divider.addMouseListener(
          new java.awt.event.MouseAdapter() {
            @Override
            public void mousePressed(java.awt.event.MouseEvent event) {
              dragging = javax.swing.SwingUtilities.isLeftMouseButton(event);
              dragStartLocation = getDividerLocation();
            }

            @Override
            public void mouseReleased(java.awt.event.MouseEvent event) {
              if (!dragging) return;
              doLayout();
              dragging = false;
              if (extent() <= 0 || !hasBothChildren()) return;
              if (getDividerLocation() == dragStartLocation) return;
              wantedSize = sizeOfStoredChild();
              sizeChanged.accept(wantedSize);
            }
          });
    }

    /** Asks for the remembered size to be applied again. */
    void reapply() {
      restorePending = true;
      revalidate();
    }

    private int requestedSize() {
      return Math.max(0, restorePending ? storedSize.getAsInt()
          : (int) Math.round(wantedSize * UiScale.factor() / lastScale));
    }

    private void allocate(int size) {
      if (allocatedSize == size) return;
      allocatedSize = size;
      allocationChanged = true;
    }

    private int dividerExtent() {
      if (getUI() instanceof javax.swing.plaf.basic.BasicSplitPaneUI ui
          && ui.getDivider() != null) return ui.getDivider().getDividerSize();
      return getDividerSize();
    }

    private boolean hasBothChildren() {
      return getLeftComponent() != null && getRightComponent() != null;
    }

    private int extent() {
      return getOrientation() == HORIZONTAL_SPLIT ? getWidth() : getHeight();
    }

    /** How big the remembered child is right now, according to the divider. */
    private int sizeOfStoredChild() {
      final var child = storedIsSecond ? getRightComponent() : getLeftComponent();
      return getOrientation() == HORIZONTAL_SPLIT ? child.getWidth() : child.getHeight();
    }

    void rememberSize() {
      if (lastExtent > 0 && hasBothChildren() && sizeOfStoredChild() > 0) {
        // Save the chosen size, even if a narrow window currently constrains the child.
        sizeChanged.accept(wantedSize);
      }
    }

    private int minimum(java.awt.Component child) {
      final var size = child.getMinimumSize();
      return getOrientation() == HORIZONTAL_SPLIT ? size.width : size.height;
    }

    @Override
    public void doLayout() {
      super.doLayout();
      watchForDrags();
      if (beforeAllocation != null) beforeAllocation.run();
      if (extent() <= 0 || !hasBothChildren()) return;
      final var insets = getInsets();
      final var start = getOrientation() == HORIZONTAL_SPLIT ? insets.left : insets.top;
      final var end = getOrientation() == HORIZONTAL_SPLIT ? insets.right : insets.bottom;
      // FlatLaf scales the divider independently of JSplitPane's requested dividerSize.
      // Persistence and minimums describe child bounds, so use the laid-out divider extent.
      final var dividerSize = watchedDivider == null ? getDividerSize()
          : getOrientation() == HORIZONTAL_SPLIT
              ? watchedDivider.getWidth() : watchedDivider.getHeight();
      final var available = Math.max(0, extent() - start - end - dividerSize);
      final var child = storedIsSecond ? getRightComponent() : getLeftComponent();
      final var other = storedIsSecond ? getLeftComponent() : getRightComponent();
      final var maximum = allocatedSize >= 0 ? available
          : Math.max(0, available - minimum(other));
      final var minimum = allocatedSize >= 0 ? 0 : Math.min(minimum(child), maximum);
      final var scale = UiScale.factor();
      final var scaleChanged = scale != lastScale;
      if (restorePending) wantedSize = storedSize.getAsInt();
      else if (scaleChanged) wantedSize = (int) Math.round(wantedSize * scale / lastScale);
      final var restore = restorePending || lastExtent != extent() || scaleChanged
          || allocationChanged;
      lastScale = scale;
      restorePending = false;
      lastExtent = extent();
      if (dragging) return;
      allocationChanged = false;
      final var wanted = Math.max(minimum,
          Math.min(maximum, allocatedSize >= 0 ? allocatedSize
              : restore ? wantedSize : sizeOfStoredChild()));
      final var target = start + (storedIsSecond ? available - wanted : wanted);
      // Reattaching a child resets BasicSplitPaneUI's layout sizes even when the divider
      // property still equals the restored target. Compare the actual child bounds as well.
      if (getDividerLocation() != target || sizeOfStoredChild() != wanted) {
        setDividerLocation(target);
        super.doLayout();
      }
    }
  }

  private void restoreFromPreferences() {
    setSideVisible(LayoutPrefs.sideVisible());
    setInspectorVisible(LayoutPrefs.inspectorVisible());
    setBottomVisible(LayoutPrefs.bottomVisible() && bottomPanel.hasPanels());
    final var active = LayoutPrefs.activeSideView();
    if (!active.isEmpty()) sidePanel.show(active);
    activityBar.setSelected(isSideVisible() ? sidePanel.activeId() : null);
  }

  public boolean isSideVisible() {
    return sideSplit.getLeftComponent() == sidePanel;
  }

  public void setSideVisible(boolean visible) {
    if (visible == isSideVisible()) return;
    if (visible) {
      sideSplit.setLeftComponent(sidePanel);
      sideSplit.setDividerSize(UiScale.scaled(DIVIDER_SIZE));
      sideSplit.reapply();
    } else {
      rememberSideWidth();
      sideSplit.setLeftComponent(null);
      sideSplit.setDividerSize(0);
    }
    LayoutPrefs.setSideVisible(visible);
    activityBar.setSelected(visible ? sidePanel.activeId() : null);
    revalidate();
    repaint();
  }

  public boolean isInspectorVisible() {
    return inspectorSplit.getRightComponent() == inspector;
  }

  public void setInspectorVisible(boolean visible) {
    if (visible == isInspectorVisible()) return;
    if (visible) {
      inspectorSplit.setRightComponent(inspector);
      inspectorSplit.setDividerSize(UiScale.scaled(DIVIDER_SIZE));
      inspectorSplit.reapply();
    } else {
      rememberInspectorWidth();
      inspectorSplit.setRightComponent(null);
      inspectorSplit.setDividerSize(0);
    }
    LayoutPrefs.setInspectorVisible(visible);
    revalidate();
    repaint();
  }

  public boolean isBottomVisible() {
    return bottomSplit.getBottomComponent() == bottomPanel;
  }

  public void setBottomVisible(boolean visible) {
    if (visible == isBottomVisible()) return;
    if (visible) {
      bottomSplit.setBottomComponent(bottomPanel);
      bottomSplit.setDividerSize(UiScale.scaled(DIVIDER_SIZE));
      bottomSplit.reapply();
    } else {
      rememberBottomHeight();
      bottomSplit.setBottomComponent(null);
      bottomSplit.setDividerSize(0);
    }
    LayoutPrefs.setBottomVisible(visible);
    revalidate();
    repaint();
  }

  /**
   * Shows the side view with this id, or collapses the panel if it is already the one showing.
   *
   * <p>Clicking the active entry to get the panel out of the way is how every editor with an
   * activity bar behaves, and it is the quickest way to give the canvas the whole window.
   */
  public void toggleSideView(String id) {
    if (isSideVisible() && id.equals(sidePanel.activeId())) {
      setSideVisible(false);
      return;
    }
    sidePanel.show(id);
    LayoutPrefs.setActiveSideView(id);
    setSideVisible(true);
    activityBar.setSelected(id);
  }

  /** Shows the side view with this id, opening the panel if it was collapsed. */
  public void showSideView(String id) {
    sidePanel.show(id);
    LayoutPrefs.setActiveSideView(id);
    setSideVisible(true);
    activityBar.setSelected(id);
  }

  /** Brings a drawer panel to the front, opening the drawer if it was closed. */
  public void showBottomPanel(String id) {
    if (!bottomPanel.showPanel(id)) return;
    setBottomVisible(true);
  }

  /** Writes the current sizes down, so the next run opens the same way. */
  public void savePreferences() {
    rememberSideWidth();
    rememberInspectorWidth();
    rememberBottomHeight();
    LayoutPrefs.setActiveSideView(sidePanel.activeId());
  }

  /** Puts the panels back where they started. */
  public void resetLayout() {
    LayoutPrefs.reset();
    setSideVisible(true);
    setInspectorVisible(true);
    setBottomVisible(false);
    applyStoredSizes();
  }

  private void rememberSideWidth() {
    if (isSideVisible()) sideSplit.rememberSize();
  }

  private void rememberInspectorWidth() {
    if (isInspectorVisible()) inspectorSplit.rememberSize();
  }

  private void rememberBottomHeight() {
    if (isBottomVisible()) bottomSplit.rememberSize();
  }

  public ActivityBar getActivityBar() {
    return activityBar;
  }

  public SidePanel getSidePanel() {
    return sidePanel;
  }

  public EditorArea getEditorArea() {
    return editorArea;
  }

  public Inspector getInspector() {
    return inspector;
  }

  public BottomPanel getBottomPanel() {
    return bottomPanel;
  }

  public StatusBar getStatusBar() {
    return statusBar;
  }
}

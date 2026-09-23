/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell;

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

    // Minimum sizes keep a panel from being dragged away to nothing; the stored sizes are applied
    // on the first layout, once the split panes have a width to place a divider within.
    sidePanel.setMinimumSize(new java.awt.Dimension(LayoutPrefs.MIN_PANEL, 0));
    inspector.setMinimumSize(new java.awt.Dimension(LayoutPrefs.MIN_PANEL, 0));
    bottomPanel.setMinimumSize(new java.awt.Dimension(0, LayoutPrefs.MIN_PANEL));

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
    pane.setDividerSize(DIVIDER_SIZE);
    pane.setContinuousLayout(true);
    return pane;
  }

  /**
   * A split pane that keeps one of its children at a remembered size.
   *
   * <p>The size is enforced on every layout rather than set once: a window is laid out several
   * times while it is being built, at sizes that have nothing to do with its final one, and a
   * divider placed during one of those passes ends up in the wrong place or off the edge. When
   * the user drags the divider the remembered size is updated, so enforcing it then changes
   * nothing.
   */
  private static final class SizedSplit extends JSplitPane {
    private static final long serialVersionUID = 1L;

    private final boolean storedIsSecond;
    private final java.util.function.IntSupplier storedSize;
    private final java.util.function.IntConsumer sizeChanged;

    /** Set while this class is moving the divider, so its own move is not read as a drag. */
    private boolean adjusting;

    /** Whether the divider is already being watched for drags. */
    private boolean dragWatched;

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
      if (dragWatched) return;
      if (!(getUI() instanceof javax.swing.plaf.basic.BasicSplitPaneUI splitUi)) return;
      final var divider = splitUi.getDivider();
      if (divider == null) return;
      dragWatched = true;
      divider.addMouseListener(
          new java.awt.event.MouseAdapter() {
            @Override
            public void mouseReleased(java.awt.event.MouseEvent event) {
              if (extent() <= 0 || !hasBothChildren()) return;
              sizeChanged.accept(sizeOfStoredChild());
            }
          });
    }

    /** Asks for the remembered size to be applied again. */
    void reapply() {
      revalidate();
    }

    private boolean hasBothChildren() {
      return getLeftComponent() != null && getRightComponent() != null;
    }

    private int extent() {
      return getOrientation() == HORIZONTAL_SPLIT ? getWidth() : getHeight();
    }

    /** How big the remembered child is right now, according to the divider. */
    private int sizeOfStoredChild() {
      final var location = getDividerLocation();
      return storedIsSecond ? extent() - getDividerSize() - location : location;
    }

    @Override
    public void doLayout() {
      super.doLayout();
      watchForDrags();
      if (extent() <= 0 || !hasBothChildren()) return;
      final var wanted = storedSize.getAsInt();
      final var target = storedIsSecond ? extent() - getDividerSize() - wanted : wanted;
      if (target < 0 || Math.abs(getDividerLocation() - target) <= 1) return;
      adjusting = true;
      try {
        setDividerLocation(target);
      } finally {
        adjusting = false;
      }
      super.doLayout();
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
      sideSplit.setDividerSize(DIVIDER_SIZE);
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
      inspectorSplit.setDividerSize(DIVIDER_SIZE);
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
      bottomSplit.setDividerSize(DIVIDER_SIZE);
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
    if (isSideVisible() && sideSplit.getDividerLocation() > 0) {
      LayoutPrefs.setSideWidth(sideSplit.getDividerLocation());
    }
  }

  private void rememberInspectorWidth() {
    if (!isInspectorVisible()) return;
    final var width = inspectorSplit.getWidth() - inspectorSplit.getDividerLocation();
    if (width > 0) LayoutPrefs.setInspectorWidth(width);
  }

  private void rememberBottomHeight() {
    if (!isBottomVisible()) return;
    final var height = bottomSplit.getHeight() - bottomSplit.getDividerLocation();
    if (height > 0) LayoutPrefs.setBottomHeight(height);
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

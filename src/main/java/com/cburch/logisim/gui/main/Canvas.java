/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.main;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.contracts.BaseMouseInputListenerContract;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.circuit.WireSet;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentUserEvent;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.file.LibraryEvent;
import com.cburch.logisim.file.LibraryListener;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.file.MouseMappings;
import com.cburch.logisim.file.Options;
import com.cburch.logisim.gui.canvas.CanvasStyle;
import com.cburch.logisim.gui.generic.CanvasPane;
import com.cburch.logisim.gui.generic.CanvasPaneContents;
import com.cburch.logisim.gui.generic.GridPainter;
import com.cburch.logisim.gui.menu.ComponentHelp;
import com.cburch.logisim.gui.menu.LogisimMenuBar;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectEvent;
import com.cburch.logisim.proj.ProjectListener;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.EditTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.PokeTool;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.tools.ToolTipMaker;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.LocaleListener;
import com.cburch.logisim.util.LocaleManager;
import com.cburch.logisim.util.StringGetter;
import com.cburch.logisim.vhdl.base.HdlModel;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Point2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.List;
import javax.accessibility.AccessibleContext;
import javax.accessibility.AccessibleRole;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JViewport;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.event.PopupMenuEvent;
import javax.swing.event.PopupMenuListener;

public class Canvas extends JPanel implements LocaleListener, CanvasPaneContents, AdjustmentListener {

  // don't bother to update the size if it hasn't changed more than this
  static final double SQRT_2 = Math.sqrt(2.0);
  private static final long serialVersionUID = 1L;
  // pixels shown in canvas beyond outermost boundaries
  private static final int THRESH_SIZE_UPDATE = 10;
  private static final int BUTTONS_MASK =
      InputEvent.BUTTON1_DOWN_MASK | InputEvent.BUTTON2_DOWN_MASK | InputEvent.BUTTON3_DOWN_MASK;
  private static final int AUTO_PAN_MARGIN = 32;
  private static final int AUTO_PAN_MIN_STEP = 4;
  private static final int AUTO_PAN_MAX_STEP = 16;
  private static final int AUTO_PAN_DELAY = 40;
  /** Empty space kept beyond content that lies above or left of the document origin. */
  private static final int ORIGIN_MARGIN = 20;
  /** The canvas origin moves in whole steps so the grid and small label edits do not jitter it. */
  private static final int ORIGIN_STEP = 100;
  private static final Font ERR_MSG_FONT =
      AppPreferences.getScaledFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
  /** F1 opens context help for the selected component, the armed add-tool, or the library index. */
  private static final String CONTEXT_HELP_ACTION_KEY = "logisimCanvasContextHelp";
  // public static BufferedImage image;
  private final Project proj;
  private final Selection selection;
  private final MyListener myListener = new MyListener();
  private final MyViewport viewport = new MyViewport();
  private final MyProjectListener myProjectListener = new MyProjectListener();
  private final TickCounter tickCounter;
  private final CanvasPaintCoordinator paintCoordinator;
  private final CanvasPainter painter;
  private final Timer autoPanTimer;
  private final Object repaintLock = new Object(); // for waitForRepaintDone
  private int autoPanDeltaX;
  private int autoPanDeltaY;
  private Tool dragTool;
  private Tool tempTool;
  private MouseMappings mappings;
  private CanvasPane canvasPane;
  private Bounds oldPreferredSize;
  /**
   * Circuit coordinates of the canvas's top-left pixel. Zero unless something (a label, or a
   * component loaded from an older file) lies at negative coordinates; then the canvas extends
   * that far so the content can be scrolled to instead of being clipped.
   */
  private int originX;
  private int originY;
  private volatile boolean inPaint = false; // only for within paintComponent

  public Canvas(Project proj) {
    this.proj = proj;
    this.selection = new Selection(proj, this);
    this.painter = new CanvasPainter(this);
    this.oldPreferredSize = null;
    this.paintCoordinator = new CanvasPaintCoordinator(this);
    this.mappings = proj.getOptions().getMouseMappings();
    this.canvasPane = null;
    this.tickCounter = new TickCounter();
    this.autoPanTimer = new Timer(AUTO_PAN_DELAY, event -> autoPanPreview());
    this.autoPanTimer.setCoalesce(true);

    setBackground(new Color(AppPreferences.CANVAS_BG_COLOR.get()));
    // Internal captions share the document's coordinate system, not the surrounding controls.
    // Explicit label/font attributes still override this base when a component paints them.
    setFont(CanvasStyle.documentFont());
    addMouseListener(myListener);
    addMouseMotionListener(myListener);
    addMouseWheelListener(myListener);
    addKeyListener(myListener);

    getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
        .put(KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), CONTEXT_HELP_ACTION_KEY);
    getActionMap().put(CONTEXT_HELP_ACTION_KEY, new ContextHelpAction());

    proj.addProjectListener(myProjectListener);
    proj.addLibraryListener(myProjectListener);
    proj.addCircuitListener(myProjectListener);
    proj.getSimulator().addSimulatorListener(tickCounter);
    selection.addListener(myProjectListener);
    LocaleManager.addLocaleListener(this, this);

    final var options = proj.getOptions().getAttributeSet();
    options.addAttributeListener(myProjectListener);
    AppPreferences.COMPONENT_TIPS.addPropertyChangeListener(myListener);
    AppPreferences.GATE_SHAPE.addPropertyChangeListener(myListener);
    AppPreferences.SHOW_TICK_RATE.addPropertyChangeListener(myListener);
    AppPreferences.CANVAS_BG_COLOR.addPropertyChangeListener(myListener);
    loadOptions(options);
  }

  /**
   * Hands the tick rate and the simulation's own message to the status bar.
   *
   * <p>Called while painting, because that is when the canvas already knows both, but it only
   * writes two labels; nothing is drawn.
   */
  private void reportSimulationStatus() {
    final var frame = proj.getFrame();
    if (frame == null) return;
    final var rate =
        AppPreferences.SHOW_TICK_RATE.getBoolean() ? tickCounter.getTickRate() : null;
    frame.setStatusTickRate(rate == null || rate.isEmpty() ? "" : rate);
    final var simulator = proj.getSimulator();
    // An oscillation stops the simulation too, and the step counts it left behind ("no signals
    // changed") read as if nothing were wrong. Say why it stopped and what to do next instead.
    frame.setStatusSimulationState(
        simulator.isOscillating()
            ? S.get("statusOscillationStopped")
            : simulator.isAutoPropagating() ? "" : simulator.getSingleStepMessage());
  }

  public static void snapToGrid(final MouseEvent e) {
    final var oldX = e.getX();
    final var oldY = e.getY();
    final var newX = snapXToGrid(oldX);
    final var newY = snapYToGrid(oldY);
    e.translatePoint(newX - oldX, newY - oldY);
  }

  //
  // static methods
  //
  public static int snapXToGrid(int x) {
    return x < 0
        ? -((-x + 5) / 10) * 10
        : ((x + 5) / 10) * 10;
  }

  public static int snapYToGrid(int y) {
    return y < 0
        ? -((-y + 5) / 10) * 10
        : ((y + 5) / 10) * 10;
  }

  public CanvasPane getCanvasPane() {
    return canvasPane;
  }

  //
  // CanvasPaneContents methods
  //
  @Override
  public void setCanvasPane(final CanvasPane value) {
    canvasPane = value;
    canvasPane.setViewport(viewport);
    canvasPane.getHorizontalScrollBar().addAdjustmentListener(this);
    canvasPane.getVerticalScrollBar().addAdjustmentListener(this);
    viewport.setView(this);
    setOpaque(false);
    computeSize(true);
  }

  @Override
  public void center() {
    final var g = getGraphics();
    final var bounds = (g != null)
        ? proj.getCurrentCircuit().getBounds(getGraphics())
        : proj.getCurrentCircuit().getBounds();
    if (bounds.getHeight() == 0 || bounds.getWidth() == 0) {
      setScrollBar(0, 0);
      return;
    }
    final var xpos =
        (int)
            (Math.round(
                (bounds.getX() - originX) * getZoomFactor()
                    - (canvasPane.getViewport().getSize().getWidth()
                            - bounds.getWidth() * getZoomFactor())
                        / 2));
    final var ypos =
        (int)
            (Math.round(
                (bounds.getY() - originY) * getZoomFactor()
                    - (canvasPane.getViewport().getSize().getHeight()
                            - bounds.getHeight() * getZoomFactor())
                        / 2));
    setScrollBar(xpos, ypos);
  }

  /**
   * Scrolls {@code bounds}, in circuit coordinates, into view: centred on it, unless all of it is
   * already on screen.
   */
  public void revealBounds(Bounds bounds) {
    if (canvasPane == null || bounds == null) return;
    final var zoom = getZoomFactor();
    final var view = getViewableBaseRect();
    final var x = (int) Math.round((bounds.getX() - originX) * zoom);
    final var y = (int) Math.round((bounds.getY() - originY) * zoom);
    final var width = (int) Math.round(bounds.getWidth() * zoom);
    final var height = (int) Math.round(bounds.getHeight() * zoom);
    if (view.contains(x, y, width, height)) return;
    setScrollBar(
        Math.max(0, x + width / 2 - view.width / 2), Math.max(0, y + height / 2 - view.height / 2));
  }

  public void closeCanvas() {
    stopAutoPan();
    // paintCoordinator.requestStop();
  }

  private void completeAction() {
    if (proj.getCurrentCircuit() == null) return;
    computeSize(false);
    // After any interaction, nudge the simulator, which in autoPropagate mode
    // will (if needed) eventually, fire a propagateCompleted event, which will
    // cause a repaint. If not in autoPropagate mode, do the repaint here
    // instead.
    if (!proj.getSimulator().nudge()) paintCoordinator.requestRepaint();
  }

  public void computeSize(final boolean immediate) {
    if (proj.getCurrentCircuit() == null) return;
    final var g = getGraphics();
    final var bounds = (g != null)
            ? proj.getCurrentCircuit().getBounds(getGraphics())
            : proj.getCurrentCircuit().getBounds();
    var height = 0;
    var width = 0;
    final var empty = bounds == null || bounds == Bounds.EMPTY_BOUNDS;
    final var newOriginX = empty ? 0 : originFor(bounds.getX());
    final var newOriginY = empty ? 0 : originFor(bounds.getY());
    if (bounds != null && viewport != null) {
      width = bounds.getX() + bounds.getWidth() - newOriginX + viewport.getWidth();
      height = bounds.getY() + bounds.getHeight() - newOriginY + viewport.getHeight();
    }
    final var dim = (canvasPane == null)
            ? new Dimension(width, height)
            : canvasPane.supportPreferredSize(width, height);
    if (newOriginX != originX || newOriginY != originY) {
      moveOrigin(newOriginX, newOriginY, dim);
      return;
    }
    if (!immediate) {
      final var old = oldPreferredSize;
      if (old != null
          && Math.abs(old.getWidth() - dim.width) < THRESH_SIZE_UPDATE
          && Math.abs(old.getHeight() - dim.height) < THRESH_SIZE_UPDATE) {
        return;
      }
    }
    oldPreferredSize = Bounds.create(0, 0, dim.width, dim.height);
    setPreferredSize(dim);
    revalidate();
  }

  /**
   * The circuit coordinate the canvas starts at for content whose smallest coordinate is {@code
   * contentMin}: zero for ordinary circuits, otherwise a whole {@link #ORIGIN_STEP} with at least
   * {@link #ORIGIN_MARGIN} of room before the content.
   */
  static int originFor(int contentMin) {
    if (contentMin >= 0) return 0;
    return Math.floorDiv(contentMin - ORIGIN_MARGIN, ORIGIN_STEP) * ORIGIN_STEP;
  }

  /** Moves the canvas origin, resizing at once and keeping the same part of the circuit in view. */
  private void moveOrigin(int newOriginX, int newOriginY, Dimension dim) {
    final var zoom = getZoomFactor();
    final var shiftX = (int) Math.round((originX - newOriginX) * zoom);
    final var shiftY = (int) Math.round((originY - newOriginY) * zoom);
    originX = newOriginX;
    originY = newOriginY;
    oldPreferredSize = Bounds.create(0, 0, dim.width, dim.height);
    setPreferredSize(dim);
    if (canvasPane == null) {
      revalidate();
    } else {
      final var view = canvasPane.getViewport().getViewPosition();
      canvasPane.validate();
      setScrollBar(view.x + shiftX, view.y + shiftY);
    }
    repaint();
  }

  /** Circuit x-coordinate of the canvas's left edge (zero unless content lies left of zero). */
  public int getOriginX() {
    return originX;
  }

  /** Circuit y-coordinate of the canvas's top edge (zero unless content lies above zero). */
  public int getOriginY() {
    return originY;
  }

  /** Converts a point in canvas pixels to circuit coordinates. */
  public Point toCircuitPoint(Point pixel) {
    final var zoom = getZoomFactor();
    return new Point(
        (int) Math.round(pixel.x / zoom) + originX, (int) Math.round(pixel.y / zoom) + originY);
  }

  public Rectangle getViewableBaseRect() {
    Rectangle viewableBase;
    if (canvasPane != null) {
      viewableBase = canvasPane.getViewport().getViewRect();
    } else {
      final var bds = proj.getCurrentCircuit().getBounds();
      viewableBase = new Rectangle(0, 0, bds.getWidth(), bds.getHeight());
    }
    return viewableBase;
  }

  public Rectangle getViewableRect() {
    Rectangle viewable;
    Rectangle viewableBase = getViewableBaseRect();
    final var zoom = getZoomFactor();
    if (zoom == 1.0) {
      viewable = new Rectangle(viewableBase);
    } else {
      viewable =
          new Rectangle(
              (int) (viewableBase.x / zoom),
              (int) (viewableBase.y / zoom),
              (int) (viewableBase.width / zoom),
              (int) (viewableBase.height / zoom));
    }
    viewable.translate(originX, originY);
    return viewable;
  }

  /**
   * Points the edge arrows at width errors that are off screen.
   *
   * <p>The errors themselves are listed by the problems pill over the canvas ({@link
   * CircuitProblems}); the text painted here was inert. Each error adds its own arrows: clearing
   * them per error left only the last one's.
   *
   * @return whether the circuit has width errors
   */
  private boolean computeViewportContents() {
    final var exceptions = proj.getCurrentCircuit().getWidthIncompatibilityData();
    if (exceptions == null || exceptions.isEmpty()) return false;
    final var viewable = getViewableRect();
    viewport.clearArrows();
    for (final var ex : exceptions) {
      final var p = ex.getPoint(0);
      addArrows(viewable, p.getX(), p.getY(), p.getX(), p.getY());
    }
    return true;
  }

  //
  // access methods
  //
  public Circuit getCircuit() {
    return proj.getCurrentCircuit();
  }

  /**
   * The centre of the part of the circuit currently in view, in circuit coordinates; where a
   * component placed from the keyboard lands when the pointer is not over the canvas.
   */
  public Point getVisibleCircuitCenter() {
    final var visible = getVisibleRect();
    return toCircuitPoint(
        new Point((int) Math.round(visible.getCenterX()), (int) Math.round(visible.getCenterY())));
  }

  @Override
  public AccessibleContext getAccessibleContext() {
    if (accessibleContext == null) accessibleContext = new AccessibleCanvas();
    return accessibleContext;
  }

  /**
   * Describes the drawing to assistive technology: which circuit it shows, the active tool, how
   * much is selected and the keys that work without a mouse. Read live, so it never goes stale.
   */
  protected class AccessibleCanvas extends AccessibleJPanel {
    private static final long serialVersionUID = 1L;

    @Override
    public AccessibleRole getAccessibleRole() {
      return AccessibleRole.CANVAS;
    }

    @Override
    public String getAccessibleName() {
      if (accessibleName != null) return accessibleName;
      final var circuit = getCircuit();
      return S.get("canvasAccessibleName", circuit == null ? "" : circuit.getName());
    }

    @Override
    public String getAccessibleDescription() {
      if (accessibleDescription != null) return accessibleDescription;
      final var tool = proj.getTool();
      final var count = selection.getComponents().size();
      return S.get("canvasAccessibleDescription",
          tool == null ? "" : tool.getDisplayName(), Integer.toString(count));
    }
  }

  public HdlModel getCurrentHdl() {
    return proj.getCurrentHdl();
  }

  public CircuitState getCircuitState() {
    return proj.getCircuitState();
  }

  Tool getDragTool() {
    return dragTool;
  }

  public StringGetter getErrorMessage() {
    return viewport.errorMessage;
  }

  public void updatePreviewAutoPan(int x, int y) {
    if (canvasPane == null) {
      return;
    }

    final var zoom = getZoomFactor();
    final var view = canvasPane.getViewport().getViewRect();
    final var deltaX = autoPanDelta((int) Math.round((x - originX) * zoom), view.x, view.width);
    final var deltaY = autoPanDelta((int) Math.round((y - originY) * zoom), view.y, view.height);
    autoPanDeltaX = deltaX;
    autoPanDeltaY = deltaY;

    if (deltaX == 0 && deltaY == 0) {
      stopAutoPan();
    } else if (!autoPanTimer.isRunning()) {
      autoPanTimer.start();
    }
  }

  public void stopAutoPan() {
    autoPanTimer.stop();
    autoPanDeltaX = 0;
    autoPanDeltaY = 0;
  }

  static int autoPanDelta(int pointer, int viewStart, int viewExtent) {
    final var offset = pointer - viewStart;
    if (offset < AUTO_PAN_MARGIN) {
      return -autoPanStep(AUTO_PAN_MARGIN - offset);
    }

    final var trailingEdge = viewExtent - AUTO_PAN_MARGIN;
    if (offset >= trailingEdge) {
      return autoPanStep(offset - trailingEdge + 1);
    }
    return 0;
  }

  private static int autoPanStep(int depth) {
    final var boundedDepth = Math.min(AUTO_PAN_MARGIN, Math.max(1, depth));
    return AUTO_PAN_MIN_STEP
        + (AUTO_PAN_MAX_STEP - AUTO_PAN_MIN_STEP) * boundedDepth / AUTO_PAN_MARGIN;
  }

  private void autoPanPreview() {
    if (canvasPane == null || !(proj.getTool() instanceof AddTool tool)) {
      stopAutoPan();
      return;
    }

    final var horizontal = canvasPane.getHorizontalScrollBar();
    final var vertical = canvasPane.getVerticalScrollBar();
    final var oldX = horizontal.getValue();
    final var oldY = vertical.getValue();
    horizontal.setValue(oldX + autoPanDeltaX);
    vertical.setValue(oldY + autoPanDeltaY);
    if (horizontal.getValue() == oldX && vertical.getValue() == oldY) {
      stopAutoPan();
      return;
    }

    final var pointer = getMousePosition();
    if (pointer == null) {
      stopAutoPan();
      return;
    }

    final var event =
        new MouseEvent(
            this,
            MouseEvent.MOUSE_MOVED,
            System.currentTimeMillis(),
            0,
            pointer.x,
            pointer.y,
            0,
            false,
            MouseEvent.NOBUTTON);
    repairMouseEvent(event);
    tool.mouseMoved(this, getGraphics(), event);
  }

  public void setErrorMessage(final StringGetter message) {
    viewport.setErrorMessage(message, null);
  }

  public void setErrorMessage(final StringGetter message, final Color color) {
    viewport.setErrorMessage(message, color);
  }

  GridPainter getGridPainter() {
    return painter.getGridPainter();
  }

  Component getHaloedComponent() {
    return painter.getHaloedComponent();
  }

  public int getHorizontalScrollBar() {
    return canvasPane.getHorizontalScrollBar().getValue();
  }

  public int getVerticalScrollBar() {
    return canvasPane.getVerticalScrollBar().getValue();
  }

  public void setVerticalScrollBar(int posY) {
    canvasPane.getVerticalScrollBar().setValue(posY);
  }

  @Override
  public Dimension getPreferredScrollableViewportSize() {
    return getPreferredSize();
  }

  public Project getProject() {
    return proj;
  }

  @Override
  public int getScrollableBlockIncrement(final Rectangle visibleRect,
                                         final int orientation, final int direction) {
    return canvasPane.supportScrollableBlockIncrement(visibleRect, orientation, direction);
  }

  @Override
  public boolean getScrollableTracksViewportHeight() {
    return false;
  }

  @Override
  public boolean getScrollableTracksViewportWidth() {
    return false;
  }

  @Override
  public int getScrollableUnitIncrement(final Rectangle visibleRect,
                                        final int orientation, final int direction) {
    return canvasPane.supportScrollableUnitIncrement(visibleRect, orientation, direction);
  }

  public Selection getSelection() {
    return selection;
  }

  @Override
  public String getToolTipText(final MouseEvent event) {
    boolean showTips = AppPreferences.COMPONENT_TIPS.getBoolean();
    if (showTips) {
      // Tooltip queries arrive in pixels, not through processMouseEvent.
      repairMouseEvent(event);
      Canvas.snapToGrid(event);
      final var loc = Location.create(event.getX(), event.getY(), false);
      ComponentUserEvent e = null;
      for (final var comp : getCircuit().getAllContaining(loc)) {
        Object makerObj = comp.getFeature(ToolTipMaker.class);
        if (makerObj instanceof ToolTipMaker maker) {
          if (e == null) {
            e = new ComponentUserEvent(this, loc.getX(), loc.getY());
          }
          final var ret = maker.getToolTip(e);
          if (ret != null) {
            unrepairMouseEvent(event);
            return ret;
          }
        }
      }
      unrepairMouseEvent(event);
    }
    return null;
  }

  //
  // graphics methods
  //
  double getZoomFactor() {
    final var pane = canvasPane;
    return pane == null ? 1.0 : pane.getZoomFactor();
  }

  boolean isPopupMenuUp() {
    return myListener.menuOn;
  }

  private void loadOptions(final AttributeSet options) {
    final var showTips = AppPreferences.COMPONENT_TIPS.getBoolean();
    setToolTipText(showTips ? "" : null);

    proj.getSimulator().removeSimulatorListener(myProjectListener);
    proj.getSimulator().addSimulatorListener(myProjectListener);
  }

  @Override
  public void localeChanged() {
    paintCoordinator.requestRepaint();
  }

  @Override
  public void paintComponent(final Graphics g) {
    // Also update register values showing in the state registers tab
    getProject().getFrame().getRegTabContent().writeValuesToLabels();

    if (AppPreferences.AntiAliassing.getBoolean()) {
      final var g2 = (Graphics2D) g;
      g2.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    }

    inPaint = true; // volatile
    try {
      super.paintComponent(g);
      painter.paintContents(g, proj);
      if (canvasPane == null) {
        viewport.paintContents(g);
      }
    } finally {
      synchronized (repaintLock) {
        inPaint = false;
        repaintLock.notifyAll();
      }
      paintCoordinator.repaintCompleted();
    }
  }

  @Override
  protected void processMouseEvent(final MouseEvent e) {
    repairMouseEvent(e);
    super.processMouseEvent(e);
  }

  @Override
  protected void processMouseMotionEvent(final MouseEvent e) {
    repairMouseEvent(e);
    super.processMouseMotionEvent(e);
  }

  @Override
  public void recomputeSize() {
    computeSize(true);
  }

  @Override
  public void repaint(int x, int y, int width, int height) {
    x -= originX;
    y -= originY;
    final var zoom = getZoomFactor();
    if (zoom < 1.0) {
      final var newX = (int) Math.floor(x * zoom);
      final var newY = (int) Math.floor(y * zoom);
      width += x - newX;
      height += y - newY;
      x = newX;
      y = newY;
    } else if (zoom > 1.0) {
      final var x1 = (int) Math.ceil((x + width) * zoom);
      final var y1 = (int) Math.ceil((y + height) * zoom);
      width = x1 - x;
      height = y1 - y;
    }
    super.repaint(x, y, width, height);
  }

  @Override
  public void repaint(Rectangle r) {
    final var zoom = getZoomFactor();
    if (zoom == 1.0 && originX == 0 && originY == 0) {
      super.repaint(r);
    } else {
      this.repaint(r.x, r.y, r.width, r.height);
    }
  }

  private void repairMouseEvent(MouseEvent e) {
    final var zoom = getZoomFactor();
    if (zoom != 1.0) {
      zoomEvent(e, zoom);
    }
    e.translatePoint(originX, originY);
  }

  public void updateArrows() {
    /* Disable for VHDL content */
    if (proj.getCurrentCircuit() == null) {
      viewport.clearArrows();
      viewport.repaint();
      return;
    }
    final var g = getGraphics();
    final var circBds = (g != null)
            ? proj.getCurrentCircuit().getBounds(getGraphics())
            : proj.getCurrentCircuit().getBounds();
    // no circuit
    if (circBds == null || circBds.getHeight() == 0 || circBds.getWidth() == 0) {
      viewport.clearArrows();
      viewport.repaint();
      return;
    }
    final var x = circBds.getX();
    final var y = circBds.getY();
    setArrows(x, y, x + circBds.getWidth(), y + circBds.getHeight());
  }

  public void setArrows(int x0, int y0, int x1, int y1) {
    /* Disable for VHDL content */
    if (proj.getCurrentCircuit() == null) return;
    viewport.clearArrows();
    Rectangle viewableBase;
    Rectangle viewable;
    if (canvasPane != null) {
      viewableBase = canvasPane.getViewport().getViewRect();
    } else {
      final var g = getGraphics();
      final var bds = (g != null)
            ? proj.getCurrentCircuit().getBounds(getGraphics())
            : proj.getCurrentCircuit().getBounds();
      viewableBase = new Rectangle(0, 0, bds.getWidth(), bds.getHeight());
    }
    double zoom = getZoomFactor();
    if (zoom == 1.0) {
      viewable = viewableBase;
    } else {
      viewable =
          new Rectangle(
              (int) (viewableBase.x / zoom),
              (int) (viewableBase.y / zoom),
              (int) (viewableBase.width / zoom),
              (int) (viewableBase.height / zoom));
    }
    viewable = new Rectangle(viewable);
    viewable.translate(originX, originY);
    addArrows(viewable, x0, y0, x1, y1);
    viewport.repaint();
  }

  /** Adds the arrows pointing from {@code viewable} towards a rectangle, keeping those set. */
  private void addArrows(Rectangle viewable, int x0, int y0, int x1, int y1) {
    final var isWest = x0 < viewable.x;
    final var isEast = x1 >= viewable.x + viewable.width;
    final var isNorth = y0 < viewable.y;
    final var isSouth = y1 >= viewable.y + viewable.height;

    if (isNorth) {
      if (isEast) viewport.setNortheast(true);
      if (isWest) viewport.setNorthwest(true);
      if (!isWest && !isEast) viewport.setNorth(true);
    }
    if (isSouth) {
      if (isEast) viewport.setSoutheast(true);
      if (isWest) viewport.setSouthwest(true);
      if (!isWest && !isEast) viewport.setSouth(true);
    }
    if (isEast && !isNorth && !isSouth) viewport.setEast(true);
    if (isWest && !isNorth && !isSouth) viewport.setWest(true);
  }

  void setHaloedComponent(Circuit circ, Component comp) {
    painter.setHaloedComponent(circ, comp);
  }

  public void setHighlightedWires(WireSet value) {
    painter.setHighlightedWires(value);
  }

  public void setHorizontalScrollBar(int posX) {
    canvasPane.getHorizontalScrollBar().setValue(posX);
  }

  public void setScrollBar(int posX, int posY) {
    setHorizontalScrollBar(posX);
    setVerticalScrollBar(posY);
  }

  public void showPopupMenu(JPopupMenu menu, int x, int y) {
    final var zoom = getZoomFactor();
    x = (int) Math.round((x - originX) * zoom);
    y = (int) Math.round((y - originY) * zoom);
    myListener.menuOn = true;
    menu.addPopupMenuListener(myListener);
    menu.show(this, x, y);
  }

  private void unrepairMouseEvent(MouseEvent e) {
    e.translatePoint(-originX, -originY);
    final var zoom = getZoomFactor();
    if (zoom != 1.0) {
      zoomEvent(e, 1.0 / zoom);
    }
  }

  private void waitForRepaintDone() {
    synchronized (repaintLock) {
      try {
        while (inPaint) {
          repaintLock.wait();
        }
      } catch (InterruptedException ignored) {
      }
    }
  }

  private void zoomEvent(MouseEvent e, double zoom) {
    final var oldx = e.getX();
    final var oldy = e.getY();
    final var newx = (int) Math.round(e.getX() / zoom);
    final var newy = (int) Math.round(e.getY() / zoom);
    e.translatePoint(newx - oldx, newy - oldy);
  }

  @Override
  public void adjustmentValueChanged(AdjustmentEvent e) {
    updateArrows();
  }

  private void doZoom(Point mouseLocation, boolean zoomIn) {
    var zoomControl = proj.getFrame().getZoomControl();
    final var oldView = getViewableBaseRect();
    final var oldZoom = getZoomFactor();
    if (zoomIn) {
      zoomControl.zoomIn();
    } else {
      zoomControl.zoomOut();
    }
    if (mouseLocation != null) {
      final var newZoom = getZoomFactor();

      final var mouseX = mouseLocation.x - originX;
      final var mouseY = mouseLocation.y - originY;
      final var mouseInViewX = ((mouseX * oldZoom) - oldView.x) / oldView.width;
      final var mouseInViewY = ((mouseY * oldZoom) - oldView.y) / oldView.height;

      final var newView = getViewableBaseRect();
      final var newViewOffsetX = (mouseX * newZoom) - (newView.width * mouseInViewX);
      final var newViewOffsetY = (mouseY * newZoom) - (newView.height * mouseInViewY);

      viewport.doLayout();
      setHorizontalScrollBar((int) Math.round(newViewOffsetX));
      setVerticalScrollBar((int) Math.round(newViewOffsetY));
    }
  }

  private class MyListener
      implements BaseMouseInputListenerContract,
          KeyListener,
          PopupMenuListener,
          PropertyChangeListener,
          MouseWheelListener {

    boolean menuOn = false;

    private Tool getToolFor(MouseEvent e) {
      if (menuOn) {
        return null;
      }

      Tool ret = mappings.getToolFor(e);
      if (ret == null) {
        return proj.getTool();
      } else {
        return ret;
      }
    }

    //
    // KeyListener methods
    //
    @Override
    public void keyPressed(KeyEvent e) {
      if (e.isControlDown()) { // If CTRL is pressed, check for + or -
        final var pointer = Canvas.this.getMousePosition(); // Determine mouse location
        // The pointer may be off the canvas.
        final var ml = pointer == null ? null : toCircuitPoint(pointer);
        switch (e.getKeyCode()) {
          case KeyEvent.VK_PLUS: // Accept keycode for plus on main block
          case KeyEvent.VK_ADD: // Also accept for the plus on the num-pad
            doZoom(ml, true);
            return;
          case KeyEvent.VK_MINUS: // Keycode for minus on main block
          case KeyEvent.VK_SUBTRACT: // Keycode for minus on num-pad
            doZoom(ml, false); // For - zoom out
            return;
          default: // If another key was pressed do nothing
        }
      }
      final var tool = proj.getTool();
      if (tool != null) {
        tool.keyPressed(Canvas.this, e);
      }
    }

    @Override
    public void keyReleased(KeyEvent e) {
      final var tool = proj.getTool();
      if (tool != null) {
        tool.keyReleased(Canvas.this, e);
      }
    }

    @Override
    public void keyTyped(KeyEvent e) {
      final var tool = proj.getTool();
      if (tool != null) {
        tool.keyTyped(Canvas.this, e);
      }
    }

    //
    // MouseListener methods
    //
    @Override
    public void mouseClicked(MouseEvent mouseEvent) {
      // do nothing
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      if (dragTool != null) {
        dragTool.mouseDragged(Canvas.this, getGraphics(), e);
        final var zoomModel = proj.getFrame().getZoomModel();
        double zoomFactor = zoomModel.getZoomFactor();
        scrollRectToVisible(
            new Rectangle(
                (int) ((e.getX() - originX) * zoomFactor),
                (int) ((e.getY() - originY) * zoomFactor),
                1,
                1));
      }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
      if (dragTool != null) {
        dragTool.mouseEntered(Canvas.this, getGraphics(), e);
      } else {
        final var tool = getToolFor(e);
        if (tool != null) {
          tool.mouseEntered(Canvas.this, getGraphics(), e);
        }
      }
    }

    @Override
    public void mouseExited(MouseEvent e) {
      if (dragTool != null) {
        dragTool.mouseExited(Canvas.this, getGraphics(), e);
      } else {
        final var tool = getToolFor(e);
        if (tool != null) {
          tool.mouseExited(Canvas.this, getGraphics(), e);
        }
      }
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      if ((e.getModifiersEx() & BUTTONS_MASK) != 0) {
        // If the control key is down while the mouse is being
        // dragged, mouseMoved is called instead. This may well be
        // an issue specific to the MacOS Java implementation,
        // but it exists there in the 1.4 and 5.0 versions.
        mouseDragged(e);
        return;
      }

      final var tool = getToolFor(e);
      if (tool != null) {
        tool.mouseMoved(Canvas.this, getGraphics(), e);
      }
    }

    @Override
    public void mousePressed(MouseEvent e) {
      int button = e.getButton();
      if (button >= 4 && button <= 7) {
        if (canvasPane != null) {
          final var bar = canvasPane.getHorizontalScrollBar();
          int direction = (button == 4 || button == 6) ? -1 : 1;
          int scrollAmount = bar.getUnitIncrement() * 10 * direction;
          bar.setValue(bar.getValue() + scrollAmount);
        }
        return;
      }

      viewport.setErrorMessage(null, null);
      if (proj.isStartupScreen()) {
        final var g = getGraphics();
        final var bounds = (g != null)
              ? proj.getCurrentCircuit().getBounds(getGraphics())
              : proj.getCurrentCircuit().getBounds();
        if (bounds.getHeight() != 0 || bounds.getWidth() != 0) proj.setStartupScreen(false);
      }
      Canvas.this.requestFocus();
      dragTool = getToolFor(e);
      if (dragTool != null) {
        dragTool.mousePressed(Canvas.this, getGraphics(), e);
        if (e.getButton() != MouseEvent.BUTTON1) {
          tempTool = proj.getTool();
          proj.setTool(dragTool);
        }
        completeAction();
      }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      // A middle-button double-click still centres the view; the floating zoom disc that used to
      // share this handler is gone.
      if (e.getButton() == MouseEvent.BUTTON2 && e.getClickCount() == 2) {
        center();
        setCursor(proj.getTool().getCursor());
      }
      if (dragTool != null) {
        dragTool.mouseReleased(Canvas.this, getGraphics(), e);
        dragTool = null;
      }
      if (tempTool != null) {
        proj.setTool(tempTool);
        tempTool = null;
      }
      final var tool = proj.getTool();
      if (tool != null && !(tool instanceof EditTool)) {
        tool.mouseMoved(Canvas.this, getGraphics(), e);
        setCursor(tool.getCursor());
      }
      completeAction();
    }

    @Override
    public void mouseWheelMoved(MouseWheelEvent mwe) {
      final var tool = proj.getTool();
      if (mwe.isControlDown()) {
        repairMouseEvent(mwe);
        doZoom(mwe.getPoint(), mwe.getWheelRotation() < 0);
      } else if (tool instanceof PokeTool && ((PokeTool) tool).isScrollable()) {
        final var id = (mwe.getWheelRotation() < 0) ? KeyEvent.VK_UP : KeyEvent.VK_DOWN;
        final var e = new KeyEvent(mwe.getComponent(), KeyEvent.KEY_PRESSED, mwe.getWhen(), 0, id, '\0');
        tool.keyPressed(Canvas.this, e);
      } else {
        if (mwe.isShiftDown()) {
          canvasPane.getHorizontalScrollBar().setValue(scrollValue(canvasPane.getHorizontalScrollBar(), mwe.getWheelRotation()));
        } else {
          canvasPane.getVerticalScrollBar().setValue(scrollValue(canvasPane.getVerticalScrollBar(), mwe.getWheelRotation()));
        }
      }
    }

    //
    // PopupMenuListener mtehods
    //
    @Override
    public void popupMenuCanceled(PopupMenuEvent e) {
      menuOn = false;
    }

    @Override
    public void popupMenuWillBecomeInvisible(PopupMenuEvent e) {
      menuOn = false;
    }

    @Override
    public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
      // do nothing
    }

    @Override
    public void propertyChange(PropertyChangeEvent event) {
      if (AppPreferences.GATE_SHAPE.isSource(event)
          || AppPreferences.SHOW_TICK_RATE.isSource(event)
          || AppPreferences.AntiAliassing.isSource(event)) {
        paintCoordinator.requestRepaint();
      } else if (AppPreferences.COMPONENT_TIPS.isSource(event)) {
        final var showTips = AppPreferences.COMPONENT_TIPS.getBoolean();
        setToolTipText(showTips ? "" : null);
      } else if (AppPreferences.CANVAS_BG_COLOR.isSource(event)) {
        setBackground(new Color(AppPreferences.CANVAS_BG_COLOR.get()));
      }
    }

    private int scrollValue(JScrollBar bar, int val) {
      if (val > 0) {
        if (bar.getValue() < bar.getMaximum() + val * 2 * bar.getBlockIncrement()) {
          return bar.getValue() + val * 2 * bar.getBlockIncrement();
        }
      } else {
        if (bar.getValue() > bar.getMinimum() + val * 2 * bar.getBlockIncrement()) {
          return bar.getValue() + val * 2 * bar.getBlockIncrement();
        }
      }
      return 0;
    }
  }

  private class MyProjectListener
      implements ProjectListener,
          LibraryListener,
          CircuitListener,
          AttributeListener,
          Simulator.Listener,
          Selection.Listener {

    @Override
    public void attributeValueChanged(AttributeEvent e) {
      Attribute<?> attr = e.getAttribute();
      if (attr == Options.ATTR_GATE_UNDEFINED) {
        final var circState = getCircuitState();
        circState.markComponentsDirty(getCircuit().getNonWires());
        // TODO: actually, we'd want to mark all components in
        // subcircuits as dirty as well
      }
    }

    @Override
    public void circuitChanged(CircuitEvent event) {
      int act = event.getAction();
      if (act == CircuitEvent.ACTION_REMOVE) {
        final var c = (Component) event.getData();
        if (c == painter.getHaloedComponent()) {
          proj.getFrame().viewComponentAttributes(null, null);
        }
        completeAction();
      } else if (act == CircuitEvent.ACTION_CLEAR) {
        if (painter.getHaloedComponent() != null) {
          proj.getFrame().viewComponentAttributes(null, null);
        }
        completeAction();
      } else if (act == CircuitEvent.ACTION_INVALIDATE) {
        completeAction();
      }
      updateArrows();
      viewport.repaint();
    }

    private Tool findTool(List<? extends Tool> opts) {
      Tool ret = null;
      for (Tool o : opts) {
        if (ret == null && o != null) {
          ret = o;
        } else if (o instanceof EditTool) {
          ret = o;
        }
      }
      return ret;
    }

    @Override
    public void libraryChanged(LibraryEvent event) {
      if (event.getAction() == LibraryEvent.REMOVE_TOOL) {
        Object t = event.getData();
        Circuit circ = null;
        if (t instanceof AddTool) {
          t = ((AddTool) t).getFactory();
          if (t instanceof SubcircuitFactory subFact) {
            circ = subFact.getSubcircuit();
          }
        }

        if (t == proj.getCurrentCircuit() && t != null) {
          proj.setCurrentCircuit(proj.getLogisimFile().getMainCircuit());
        }

        if (proj.getTool() == event.getData()) {
          var next = findTool(proj.getLogisimFile().getOptions().getToolbarData().getContents());
          if (next == null) {
            for (Library lib : proj.getLogisimFile().getLibraries()) {
              next = findTool(lib.getTools());
              if (next != null) {
                break;
              }
            }
          }
          proj.setTool(next);
        }

        if (circ != null) {
          var state = getCircuitState();
          var last = state;
          while (state != null && state.getCircuit() != circ) {
            last = state;
            state = state.getParentState();
          }
          if (state != null) {
            getProject().setCircuitState(last.cloneAsNewRootState());
          }
        }
      }
    }

    @Override
    public void projectChanged(ProjectEvent event) {
      int act = event.getAction();
      if (act == ProjectEvent.ACTION_SET_CURRENT) {
        viewport.setErrorMessage(null, null);
        if (painter.getHaloedComponent() != null) {
          proj.getFrame().viewComponentAttributes(null, null);
        }
      } else if (act == ProjectEvent.ACTION_SET_FILE) {
        final var old = (LogisimFile) event.getOldData();
        if (old != null) {
          old.getOptions().getAttributeSet().removeAttributeListener(this);
        }
        final var file = (LogisimFile) event.getData();
        if (file != null) {
          AttributeSet attrs = file.getOptions().getAttributeSet();
          attrs.addAttributeListener(this);
          loadOptions(attrs);
          mappings = file.getOptions().getMouseMappings();
        }
      } else if (act == ProjectEvent.ACTION_SET_TOOL) {
        viewport.setErrorMessage(null, null);

        final var t = event.getTool();
        if (t == null) {
          setCursor(Cursor.getDefaultCursor());
        } else {
          setCursor(t.getCursor());
        }
      } else if (act == ProjectEvent.ACTION_SET_STATE) {
        final var oldState = (CircuitState) event.getOldData();
        final var newState = (CircuitState) event.getData();
        if (oldState != null && newState != null) {
          final var oldProp = oldState.getPropagator();
          final var newProp = newState.getPropagator();
          if (oldProp != newProp) {
            tickCounter.clear(proj.getSimulator());
          }
        }
      }

      if (act != ProjectEvent.ACTION_SELECTION
          && act != ProjectEvent.ACTION_START
          && act != ProjectEvent.UNDO_START) {
        completeAction();
      }
    }

    @Override
    public void selectionChanged(Selection.Event event) {
      repaint();
    }

    @Override
    public void propagationCompleted(Simulator.Event e) {
      paintCoordinator.requestRepaint();
    }

    @Override
    public void simulatorStateChanged(Simulator.Event e) {
      paintCoordinator.requestRepaint();
    }

    @Override
    public void simulatorReset(Simulator.Event e) {
      waitForRepaintDone();
    }
  }

  /**
   * F1 on the canvas: prefers a single selected component, then the component wrapped by the
   * currently armed add-tool, and otherwise falls back to the Library Reference index.
   */
  private class ContextHelpAction extends AbstractAction {

    private static final long serialVersionUID = 1L;

    @Override
    public void actionPerformed(ActionEvent event) {
      if (!(proj.getFrame().getJMenuBar() instanceof LogisimMenuBar menuBar)) return;
      menuBar.help.showHelp(resolveTarget());
    }

    private String resolveTarget() {
      final var selected = selection.getComponents();
      if (selected.size() == 1) {
        final var factory = selected.iterator().next().getFactory();
        return ComponentHelp.getHelpTarget(factory, proj.getLogisimFile());
      }
      final var tool = proj.getTool();
      if (tool instanceof AddTool) {
        return ComponentHelp.getHelpTarget(tool, proj.getLogisimFile());
      }
      return ComponentHelp.LIBRARY_REFERENCE_TARGET;
    }
  }

  private class MyViewport extends JViewport {

    private static final long serialVersionUID = 1L;
    StringGetter errorMessage = null;
    Color errorColor = null;
    boolean isNorth = false;
    boolean isSouth = false;
    boolean isWest = false;
    boolean isEast = false;
    boolean isNortheast = false;
    boolean isNorthwest = false;
    boolean isSoutheast = false;
    boolean isSouthwest = false;

    MyViewport() {
      // dummy
    }

    void clearArrows() {
      isNorth = false;
      isSouth = false;
      isWest = false;
      isEast = false;
      isNortheast = false;
      isNorthwest = false;
      isSoutheast = false;
      isSouthwest = false;
    }

    @Override
    public Color getBackground() {
      return getView() == null ? super.getBackground() : getView().getBackground();
    }

    @Override
    public void paintChildren(Graphics g) {
      super.paintChildren(g);
      paintContents(g);
    }

    void paintContents(Graphics g) {
      /*
       * TODO this is for the SimulatorPrototype class int speed =
       * proj.getSimulator().getSimulationSpeed(); String speedStr; if
       * (speed >= 10000000) { speedStr = (speed / 1000000) + " MHz"; }
       * else if (speed >= 1000000) { speedStr = (speed / 100000) / 10.0 +
       * " MHz"; } else if (speed >= 10000) { speedStr = (speed / 1000) +
       * " kHz"; } else if (speed >= 10000) { speedStr = (speed / 100) /
       * 10.0 + " kHz"; } else { speedStr = speed + " Hz"; } FontMetrics
       * fm = g.getFontMetrics(); g.drawString(speedStr, getWidth() - 10 -
       * fm.stringWidth(speedStr), getHeight() - 10);
       */
      if (AppPreferences.AntiAliassing.getBoolean()) {
        final var g2 = (Graphics2D) g;
        g2.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      }

      int msgY = getHeight() - 23;
      final var message = errorMessage;
      if (message != null) {
        // No colour set by the caller means the ordinary error colour, which follows the theme.
        g.setColor(errorColor == null ? Value.errorColor() : errorColor);
        msgY = paintString(g, msgY, message.toString());
      }

      if (proj.getSimulator().isExceptionEncountered()) {
        g.setColor(Value.errorColor());
        final var exceptionMessage = proj.getSimulator().getExceptionMessage();
        msgY =
            paintString(
                g,
                msgY,
                exceptionMessage == null || exceptionMessage.isBlank()
                    ? S.get("canvasExceptionError")
                    : exceptionMessage);
      }

      // Oscillation and width errors are reported by the problems pill over the canvas, which can
      // also find them and reset; only the arrows towards off-screen errors are painted here.
      final var widthErrors = computeViewportContents();
      final var sz = getSize();

      if (widthErrors) {
        g.setColor(Value.widthErrorColor());
      } else {
        // The arrows point at work that is off screen, which is the same class of hint as a
        // selection marker; they used to be a navy so dark and so translucent as to be a smudge.
        g.setColor(CanvasStyle.marker(AppPreferences.inPrintView()));
      }

      if (isNorth
          || isSouth
          || isEast
          || isWest
          || isNortheast
          || isNorthwest
          || isSoutheast
          || isSouthwest) {
        // The arrows stay: they point at work that is off screen. The floating disc that used to
        // sit in the corner is gone, because the zoom controls now live there and carry a fit
        // button of their own.
        if (isNorth)
          GraphicsUtil.drawArrow2(g, sz.width / 2 - 20, 15, sz.width / 2, 5, sz.width / 2 + 20, 15);
        if (isSouth)
          GraphicsUtil.drawArrow2(
              g,
              sz.width / 2 - 20,
              sz.height - 15,
              sz.width / 2,
              sz.height - 5,
              sz.width / 2 + 20,
              sz.height - 15);
        if (isEast)
          GraphicsUtil.drawArrow2(
              g,
              sz.width - 15,
              sz.height / 2 + 20,
              sz.width - 5,
              sz.height / 2,
              sz.width - 15,
              sz.height / 2 - 20);
        if (isWest)
          GraphicsUtil.drawArrow2(
              g, 15, sz.height / 2 + 20, 5, sz.height / 2, 15, sz.height / 2 + (-20));
        if (isNortheast)
          GraphicsUtil.drawArrow2(g, sz.width - 30, 5, sz.width - 5, 5, sz.width - 5, 30);
        if (isNorthwest) GraphicsUtil.drawArrow2(g, 30, 5, 5, 5, 5, 30);
        if (isSoutheast)
          GraphicsUtil.drawArrow2(
              g,
              sz.width - 30,
              sz.height - 5,
              sz.width - 5,
              sz.height - 5,
              sz.width - 5,
              sz.height - 30);
        if (isSouthwest)
          GraphicsUtil.drawArrow2(g, 30, sz.height - 5, 5, sz.height - 5, 5, sz.height - 30);
      }
      // The tick rate and the single-step message used to be painted here, over the user's
      // circuit, the tick rate in 28-point monospace. They are reported in the status bar now.
      reportSimulationStatus();

      g.setColor(Color.BLACK);
    }

    private int paintString(Graphics g, int y, String msg) {
      final var old = g.getFont();
      g.setFont(ERR_MSG_FONT);
      final var fm = g.getFontMetrics();
      var x = (getWidth() - fm.stringWidth(msg)) / 2;
      if (x < 0) {
        x = 0;
      }
      g.drawString(msg, x, y);
      g.setFont(old);
      return y - 23;
    }

    void setEast(boolean value) {
      isEast = value;
    }

    void setErrorMessage(StringGetter msg, Color color) {
      if (errorMessage != msg) {
        errorMessage = msg;
        errorColor = color;
        paintCoordinator.requestRepaint();
      }
    }

    void setNorth(boolean value) {
      isNorth = value;
    }

    void setNortheast(boolean value) {
      isNortheast = value;
    }

    void setNorthwest(boolean value) {
      isNorthwest = value;
    }

    void setSouth(boolean value) {
      isSouth = value;
    }

    void setSoutheast(boolean value) {
      isSoutheast = value;
    }

    void setSouthwest(boolean value) {
      isSouthwest = value;
    }

    void setWest(boolean value) {
      isWest = value;
    }
  }
}

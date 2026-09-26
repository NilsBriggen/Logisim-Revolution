/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.main;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.WidthIncompatibilityData;
import com.cburch.logisim.circuit.WireSet;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.canvas.CanvasStyle;
import com.cburch.logisim.gui.generic.GridPainter;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.PokeTool;
import com.cburch.logisim.util.CollectionUtil;
import com.cburch.logisim.util.GraphicsUtil;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

class CanvasPainter implements PropertyChangeListener {
  private static final Set<Component> NO_COMPONENTS = Collections.emptySet();

  private final Canvas canvas;
  private final GridPainter grid;
  private Component haloedComponent = null;
  private Circuit haloedCircuit = null;
  private WireSet highlightedWires = WireSet.EMPTY;

  CanvasPainter(Canvas canvas) {
    this.canvas = canvas;
    this.grid = new GridPainter(canvas);

    AppPreferences.ATTRIBUTE_HALO.addPropertyChangeListener(this);
    AppPreferences.CANVAS_BG_COLOR.addPropertyChangeListener(this);
    AppPreferences.GRID_BG_COLOR.addPropertyChangeListener(this);
    AppPreferences.GRID_DOT_COLOR.addPropertyChangeListener(this);
    AppPreferences.GRID_ZOOMED_DOT_COLOR.addPropertyChangeListener(this);
    AppPreferences.COMPONENT_COLOR.addPropertyChangeListener(this);
    AppPreferences.COMPONENT_SECONDARY_COLOR.addPropertyChangeListener(this);
    AppPreferences.COMPONENT_GHOST_COLOR.addPropertyChangeListener(this);
    AppPreferences.COMPONENT_ICON_COLOR.addPropertyChangeListener(this);
  }

  private void drawWidthIncompatibilityData(Graphics base, Graphics g, Project proj) {
    final var circuit = proj.getCurrentCircuit();
    final var exceptions = circuit.getWidthIncompatibilityData();
    if (CollectionUtil.isNullOrEmpty(exceptions)) return;

    final var fm = base.getFontMetrics(g.getFont());
    for (final var ex : exceptions) {
      final var common = ex.getCommonBitWidth();
      for (int i = 0; i < ex.size(); i++) {
        final var p = ex.getPoint(i);
        final var w = ex.getBitWidth(i);

        // ensure it hasn't already been drawn
        var drawn = false;
        for (var j = 0; j < i; j++) {
          if (ex.getPoint(j).equals(p)) {
            drawn = true;
            break;
          }
        }
        if (drawn) continue;

        final var caption = widthCaption(ex, i);
        GraphicsUtil.switchToWidth(g, 2);
        if (common != null && !w.equals(common)) {
          g.setColor(Value.widthErrorHighlightColor());
          g.drawOval(p.getX() - 5, p.getY() - 5, 10, 10);
        }
        g.setColor(Value.widthErrorColor());
        g.drawOval(p.getX() - 4, p.getY() - 4, 8, 8);
        GraphicsUtil.switchToWidth(g, 3);
        // Put the caption on the side of the point away from the components there, so it does not
        // cover their text (a splitter's bit labels sit right next to its ends).
        var awayX = 1;
        var awayY = 1;
        for (final var comp : circuit.getNonWires(p)) {
          final var bds = comp.getBounds(g);
          final var cx = bds.getX() + bds.getWidth() / 2;
          final var cy = bds.getY() + bds.getHeight() / 2;
          if (cx > p.getX()) awayX = -1;
          if (cy > p.getY()) awayY = -1;
        }
        final var text = caption.toString();
        final var textX = awayX > 0 ? p.getX() + 4 : p.getX() - 4 - fm.stringWidth(text);
        final var textY = awayY > 0 ? p.getY() + 1 + fm.getAscent() : p.getY() - 1 - fm.getDescent();
        GraphicsUtil.outlineText(
            g,
            text,
            textX,
            textY,
            Value.widthErrorCaptionColor(),
            common != null && !w.equals(common)
                ? Value.widthErrorHighlightColor()
                : Value.widthErrorCaptionBgcolor());
      }
    }
    g.setColor(componentColor());
    GraphicsUtil.switchToWidth(g, 1);
  }

  /**
   * The badge for one end of a width conflict.
   *
   * <p>Two widths at the same point read "8/4", as before. An end that agrees with its own
   * neighbours now also names the widths it clashes with, "8≠4": through a tunnel the two
   * halves of a net each looked self-consistent (8 and 8, 4 and 4), with nothing pointing at the
   * other side.
   */
  static String widthCaption(WidthIncompatibilityData ex, int index) {
    final var p = ex.getPoint(index);
    final var w = ex.getBitWidth(index);
    final var caption = new StringBuilder(String.valueOf(w.getWidth()));
    for (var j = index + 1; j < ex.size(); j++) {
      if (ex.getPoint(j).equals(p)) {
        return caption.append('/').append(ex.getBitWidth(j).getWidth()).toString();
      }
    }
    final var others = new TreeSet<Integer>();
    for (var j = 0; j < ex.size(); j++) {
      final var other = ex.getBitWidth(j).getWidth();
      if (other != w.getWidth()) others.add(other);
    }
    if (!others.isEmpty()) {
      caption.append('\u2260');
      caption.append(others.stream().map(String::valueOf).collect(Collectors.joining("/")));
    }
    return caption.toString();
  }

  private void drawWithUserState(Graphics base, Graphics g, Project proj) {
    final var circ = proj.getCurrentCircuit();
    final var sel = proj.getSelection();
    final var dragTool = canvas.getDragTool();
    var hidden = (dragTool == null)
            ? NO_COMPONENTS
            : dragTool.getHiddenComponents(canvas);
    if (hidden == null) hidden = NO_COMPONENTS;

    // draw halo around component whose attributes we are viewing
    final var showHalo = AppPreferences.ATTRIBUTE_HALO.getBoolean();
    if (showHalo
        && haloedComponent != null
        && haloedCircuit == circ
        && !hidden.contains(haloedComponent)) {
      // A ring that follows the component's own shape. It used to be a magenta ellipse scaled by
      // the square root of two, which swallowed everything around a small component.
      GraphicsUtil.switchToWidth(g, 2);
      g.setColor(CanvasStyle.halo(false));
      final var bds = haloedComponent.getBounds(g).expand(4);
      final var radius = CanvasStyle.bodyRadius() + 4;
      g.drawRoundRect(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight(), radius, radius);
      GraphicsUtil.switchToWidth(g, 1);
      g.setColor(componentColor());
    }
    TunnelPartners.draw(g, circ, sel.getComponents(), haloedCircuit == circ ? haloedComponent : null);

    // draw circuit and selection
    final var circState = proj.getCircuitState();
    final var context = new ComponentDrawContext(canvas, circ, circState, base, g, false);
    context.setHighlightedWires(highlightedWires);
    final var showSelection = !(proj.getTool() instanceof PokeTool);
    if (showSelection) sel.drawUnderlay(context, hidden);
    circ.draw(context, hidden);
    drawEditLockBadges(g, circ, hidden);
    if (showSelection) sel.draw(context, hidden);

    // draw tool
    final var tool = dragTool != null ? dragTool : proj.getTool();
    if (tool != null && !canvas.isPopupMenuUp()) {
      final var gfxCopy = g.create();
      context.setGraphics(gfxCopy);
      tool.draw(canvas, context);
      gfxCopy.dispose();
    }
  }

  /**
   * A small padlock on the top-right corner of every locked component, so the user can tell before
   * trying to move one. Only on screen: printing and image export do not come through here.
   */
  private static void drawEditLockBadges(Graphics g, Circuit circ, Set<Component> hidden) {
    final var locked = circ.getEditLockedComponents();
    if (locked.isEmpty()) return;
    final var g2 = (Graphics2D) g.create();
    try {
      g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      for (final var comp : locked) {
        if (hidden.contains(comp)) continue;
        final var bds = comp.getBounds(g);
        // Just outside the corner, clear of the selection handle that sits on it.
        paintLockBadge(g2, bds.getX() + bds.getWidth() + 6, bds.getY() - 6);
      }
    } finally {
      g2.dispose();
    }
  }

  /** Paints the padlock centred on ({@code x}, {@code y}), in circuit coordinates. */
  static void paintLockBadge(Graphics2D g, int x, int y) {
    // A disc in the canvas colour keeps the glyph legible over wires and component outlines.
    g.setColor(new Color(AppPreferences.CANVAS_BG_COLOR.get()));
    g.fill(new Ellipse2D.Double(x - 6.5, y - 6.5, 13, 13));
    final var ink = CanvasStyle.lockBadge();
    g.setColor(ink);
    g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    g.draw(new Ellipse2D.Double(x - 6.5, y - 6.5, 13, 13));
    // Shackle, then body.
    g.draw(new Arc2D.Double(x - 2.3, y - 4.6, 4.6, 5.2, 0, 180, Arc2D.OPEN));
    g.draw(new Line2D.Double(x - 2.3, y - 2.0, x - 2.3, y - 0.8));
    g.draw(new Line2D.Double(x + 2.3, y - 2.0, x + 2.3, y - 0.8));
    g.fill(new RoundRectangle2D.Double(x - 3.6, y - 0.9, 7.2, 5.2, 1.6, 1.6));
  }

  private void exposeHaloedComponent(Graphics gfx) {
    final var comp = haloedComponent;
    if (comp == null) return;
    final var bds = comp.getBounds(gfx).expand(7);
    final var width = bds.getWidth();
    final var height = bds.getHeight();
    final var a = Canvas.SQRT_2 * width;
    final var b = Canvas.SQRT_2 * height;
    canvas.repaint(
        (int) Math.round(bds.getX() + width / 2.0 - a / 2.0),
        (int) Math.round(bds.getY() + height / 2.0 - b / 2.0),
        (int) Math.round(a),
        (int) Math.round(b));
  }

  //
  // accessor methods
  //
  GridPainter getGridPainter() {
    return grid;
  }

  Component getHaloedComponent() {
    return haloedComponent;
  }

  /**
   * Returns the colour components are drawn in.
   *
   * <p>Used where the painter restores a default after drawing something in its own colour. Black
   * was hardcoded here, which left stray black strokes on a dark canvas.
   */
  private static Color componentColor() {
    return new Color(AppPreferences.COMPONENT_COLOR.get());
  }

  //
  // painting methods
  //
  void paintContents(Graphics g, Project proj) {
    final var size = canvas.getSize();
    final double zoomFactor = canvas.getZoomFactor();

    final var originX = canvas.getOriginX();
    final var originY = canvas.getOriginY();
    grid.paintGrid(
        g, (int) Math.round(-originX * zoomFactor), (int) Math.round(-originY * zoomFactor));
    g.setColor(componentColor());

    final var gfxScaled = g.create();
    gfxScaled.setFont(CanvasStyle.documentFont());
    if (zoomFactor != 1.0 && gfxScaled instanceof Graphics2D g2d) {
      g2d.scale(zoomFactor, zoomFactor);
    }
    // Content above or left of zero extends the canvas instead of being clipped (see Canvas).
    gfxScaled.translate(-originX, -originY);
    final var circ = proj.getCurrentCircuit();
    if (circ == null) {
      gfxScaled.dispose();
      return;
    }
    drawWithUserState(g, gfxScaled, proj);
    drawWidthIncompatibilityData(g, gfxScaled, proj);

    final var circState = proj.getCircuitState();
    final var ptContext = new ComponentDrawContext(canvas, circ, circState, g, gfxScaled);
    ptContext.setHighlightedWires(highlightedWires);
    final var printing = ptContext.isPrintView();
    gfxScaled.setColor(CanvasStyle.oscillationMarker(printing));
    circState.drawOscillatingPoints(ptContext);
    gfxScaled.setColor(CanvasStyle.stepMarker(printing));
    proj.getSimulator().drawStepPoints(ptContext);
    gfxScaled.setColor(CanvasStyle.pendingInputMarker(printing));
    proj.getSimulator().drawPendingInputs(ptContext);
    gfxScaled.dispose();
  }

  @Override
  public void propertyChange(PropertyChangeEvent event) {
    if (AppPreferences.GRID_BG_COLOR.isSource(event)
        || AppPreferences.GRID_DOT_COLOR.isSource(event)
        || AppPreferences.GRID_ZOOMED_DOT_COLOR.isSource(event)
        || AppPreferences.COMPONENT_COLOR.isSource(event)
        || AppPreferences.COMPONENT_SECONDARY_COLOR.isSource(event)
        || AppPreferences.COMPONENT_GHOST_COLOR.isSource(event)
        || AppPreferences.COMPONENT_ICON_COLOR.isSource(event)) {
      canvas.repaint();
    }
  }

  void setHaloedComponent(Circuit circ, Component comp) {
    if (comp == haloedComponent) return;
    final var g = canvas.getGraphics();
    exposeHaloedComponent(g);
    haloedCircuit = circ;
    haloedComponent = comp;
    exposeHaloedComponent(g);
  }

  //
  // mutator methods
  //
  void setHighlightedWires(WireSet value) {
    highlightedWires = value == null ? WireSet.EMPTY : value;
  }
}

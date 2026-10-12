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

import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.CircuitTransaction;
import com.cburch.logisim.circuit.SplitterAttributes;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Rotates or flips the selection as a whole (Edit &gt; Arrange), about the grid point nearest the
 * centre of its bounding box.
 *
 * <p>Wires in the selection are transformed end by end, and each component is turned to face the
 * transformed direction. A flip cannot mirror a component that has no mirrored form, so a flipped
 * component gets whichever orientation puts its connections where the mirror image has them, if
 * one does; a symmetric gate, for example, simply turns around. Wires outside the selection are
 * not rerouted. One command is one undoable action.
 */
public final class SelectionTransform {

  private static final int GRID = 10;

  /** The transform commands. */
  public enum Mode {
    ROTATE_CW("editRotateClockwiseItem"),
    ROTATE_CCW("editRotateCounterclockwiseItem"),
    FLIP_HORIZONTAL("editFlipHorizontalItem"),
    FLIP_VERTICAL("editFlipVerticalItem");

    private final String nameKey;

    Mode(String nameKey) {
      this.nameKey = nameKey;
    }

    /** Where this mode takes a point, relative to a pivot. */
    Location apply(Location p, int px, int py) {
      final var dx = p.getX() - px;
      final var dy = p.getY() - py;
      return switch (this) {
        // Screen coordinates: y grows downwards.
        case ROTATE_CW -> Location.create(px - dy, py + dx, false);
        case ROTATE_CCW -> Location.create(px + dy, py - dx, false);
        case FLIP_HORIZONTAL -> Location.create(px - dx, py + dy, false);
        case FLIP_VERTICAL -> Location.create(px + dx, py - dy, false);
      };
    }

    double[] apply(double x, double y, int px, int py) {
      final var dx = x - px;
      final var dy = y - py;
      return switch (this) {
        case ROTATE_CW -> new double[] {px - dy, py + dx};
        case ROTATE_CCW -> new double[] {px + dy, py - dx};
        case FLIP_HORIZONTAL -> new double[] {px - dx, py + dy};
        case FLIP_VERTICAL -> new double[] {px + dx, py - dy};
      };
    }

    Direction apply(Direction d) {
      return switch (this) {
        case ROTATE_CW -> d.getRight();
        case ROTATE_CCW -> d.getLeft();
        case FLIP_HORIZONTAL -> d == Direction.EAST || d == Direction.WEST ? d.reverse() : d;
        case FLIP_VERTICAL -> d == Direction.NORTH || d == Direction.SOUTH ? d.reverse() : d;
      };
    }
  }

  private SelectionTransform() {}

  /** Whether there is anything to transform. */
  public static boolean isApplicable(Selection sel) {
    return !sel.getComponents().isEmpty();
  }

  /** The grid point nearest the centre of the components' bounding box. */
  static Location pivot(Collection<Component> comps) {
    Bounds bds = Bounds.EMPTY_BOUNDS;
    for (final var comp : comps) bds = bds.add(comp.getBounds());
    final var cx = (int) Math.round((bds.getX() + bds.getWidth() / 2.0) / GRID) * GRID;
    final var cy = (int) Math.round((bds.getY() + bds.getHeight() / 2.0) / GRID) * GRID;
    return Location.create(cx, cy, false);
  }

  /** Returns the replacement for every component, transformed about {@code pivot}. */
  static Map<Component, Component> computeReplacements(
      Collection<Component> comps, Mode mode, Location pivot) {
    final var ret = new LinkedHashMap<Component, Component>();
    final var px = pivot.getX();
    final var py = pivot.getY();
    for (final var comp : comps) {
      if (comp instanceof Wire wire) {
        ret.put(
            comp,
            Wire.create(mode.apply(wire.getEnd0(), px, py), mode.apply(wire.getEnd1(), px, py)));
      } else {
        ret.put(comp, transform(comp, mode, px, py));
      }
    }
    return ret;
  }

  @SuppressWarnings("unchecked")
  private static Attribute<Direction> facingAttribute(Component comp) {
    return (Attribute<Direction>)
        comp.getFactory()
            .getFeature(ComponentFactory.FACING_ATTRIBUTE_KEY, comp.getAttributeSet());
  }

  private static List<Location> sortedEnds(Component comp) {
    final var ends = new ArrayList<Location>();
    for (final var end : comp.getEnds()) ends.add(end.getLocation());
    Collections.sort(ends);
    return ends;
  }

  private static Component transform(Component comp, Mode mode, int px, int py) {
    final var factory = comp.getFactory();
    final var facing = facingAttribute(comp);
    final var base = (AttributeSet) comp.getAttributeSet().clone();
    if (base.containsAttribute(StdAttr.LABEL_LOC)
        && base.getValue(StdAttr.LABEL_LOC) instanceof Direction labelLoc) {
      base.setValue(StdAttr.LABEL_LOC, mode.apply(labelLoc));
    }
    if (facing == null) {
      // No orientation: keep the shape, move it so its centre lands where the centre goes.
      final var bds = comp.getBounds();
      final var c = mode.apply(bds.getCenterX(), bds.getCenterY(), px, py);
      final var loc = comp.getLocation();
      final var x = loc.getX() + c[0] - bds.getCenterX();
      final var y = loc.getY() + c[1] - bds.getCenterY();
      return factory.createComponent(
          Location.create(snap(x), snap(y), false), base);
    }

    final var anchor = mode.apply(comp.getLocation(), px, py);
    final var expected = new ArrayList<Location>();
    for (final var end : comp.getEnds()) expected.add(mode.apply(end.getLocation(), px, py));
    Collections.sort(expected);

    // Try the mapped facing first, then the other orientations, each also with the other
    // handedness (a splitter's fan side, a plexer's select side), keeping the first whose
    // connections land where the transform puts them.
    final var oldFacing = base.getValue(facing);
    final var facings = new ArrayList<Direction>();
    facings.add(mode.apply(oldFacing));
    for (final var d : Direction.cardinals) if (!facings.contains(d)) facings.add(d);
    final var variants = new ArrayList<AttributeSet>();
    for (final var d : facings) {
      final var attrs = (AttributeSet) base.clone();
      attrs.setValue(facing, d);
      variants.add(attrs);
      final var mirrored = otherHandedness(attrs);
      if (mirrored != null) variants.add(mirrored);
    }
    Component first = null;
    for (final var attrs : variants) {
      final var candidate = factory.createComponent(anchor, attrs);
      if (first == null) first = candidate;
      if (sortedEnds(candidate).equals(expected)) return candidate;
    }
    return first;
  }

  /** A copy with the other handedness, or null if the component has no such choice. */
  private static AttributeSet otherHandedness(AttributeSet attrs) {
    if (attrs instanceof SplitterAttributes) {
      final var appear = attrs.getValue(SplitterAttributes.ATTR_APPEARANCE);
      if (appear != SplitterAttributes.APPEAR_LEFT && appear != SplitterAttributes.APPEAR_RIGHT) {
        return null;
      }
      final var copy = (AttributeSet) attrs.clone();
      copy.setValue(
          SplitterAttributes.ATTR_APPEARANCE,
          appear == SplitterAttributes.APPEAR_LEFT
              ? SplitterAttributes.APPEAR_RIGHT
              : SplitterAttributes.APPEAR_LEFT);
      return copy;
    }
    if (attrs.containsAttribute(StdAttr.SELECT_LOC)) {
      final var copy = (AttributeSet) attrs.clone();
      copy.setValue(
          StdAttr.SELECT_LOC,
          attrs.getValue(StdAttr.SELECT_LOC) == StdAttr.SELECT_BOTTOM_LEFT
              ? StdAttr.SELECT_TOP_RIGHT
              : StdAttr.SELECT_BOTTOM_LEFT);
      return copy;
    }
    return null;
  }

  private static int snap(double v) {
    return (int) Math.round(v / GRID) * GRID;
  }

  /** Creates the undoable action; the result is worked out when it first runs. */
  public static Action createAction(Selection sel, Mode mode) {
    return new Transform(sel, mode);
  }

  private static final class Transform extends Action {
    private final Selection sel;
    private final Mode mode;
    private final List<CircuitTransaction> forward = new ArrayList<>();
    private final List<CircuitTransaction> reverse = new ArrayList<>();
    private boolean done;

    Transform(Selection sel, Mode mode) {
      this.sel = sel;
      this.mode = mode;
    }

    @Override
    public void doIt(Project proj) {
      if (done) {
        for (final var xn : forward) xn.execute();
        return;
      }
      final var circuit = proj.getCurrentCircuit();
      SelectionActions.checkEditLocks(circuit, sel, true);
      done = true;
      if (!sel.getFloatingComponents().isEmpty()) {
        // A paste still floating becomes part of the circuit first, as when it is dragged.
        final var drop = new CircuitMutation(circuit);
        sel.dropAll(drop);
        run(drop);
      }
      final var comps = new ArrayList<>(sel.getComponents());
      final var replacements = computeReplacements(comps, mode, pivot(comps));
      final var xn = new CircuitMutation(circuit);
      for (final var entry : replacements.entrySet()) xn.replace(entry.getKey(), entry.getValue());
      run(xn);
    }

    private void run(CircuitTransaction xn) {
      forward.add(xn);
      reverse.add(xn.execute().getReverseTransaction());
    }

    @Override
    public void undo(Project proj) {
      final var steps = new ArrayList<>(reverse);
      Collections.reverse(steps);
      for (final var xn : steps) xn.execute();
    }

    @Override
    public String getName() {
      return S.get(mode.nameKey);
    }
  }
}

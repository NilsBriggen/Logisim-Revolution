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

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.CircuitTransaction;
import com.cburch.logisim.circuit.ReplacementMap;
import com.cburch.logisim.circuit.Wire;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.tools.move.MoveGesture;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Align and distribute for the circuit layout editor (Edit &gt; Arrange).
 *
 * <p>The commands act on the selected components, not on wires, and use each component's bounding
 * box without its label. Every moved component lands on the grid, nothing is pushed to negative
 * coordinates, and wires attached to a moved component follow it the way they do when the
 * selection is dragged. One command is one undoable action.
 */
public final class SelectionArrange {

  /** Grid pitch the arranged components snap to. */
  private static final int GRID = 10;

  /** The arrange commands. */
  public enum Mode {
    ALIGN_LEFT(2, "editAlignLeftItem"),
    ALIGN_CENTER(2, "editAlignCenterItem"),
    ALIGN_RIGHT(2, "editAlignRightItem"),
    ALIGN_TOP(2, "editAlignTopItem"),
    ALIGN_MIDDLE(2, "editAlignMiddleItem"),
    ALIGN_BOTTOM(2, "editAlignBottomItem"),
    DISTRIBUTE_HORIZONTAL(3, "editDistributeHorizontalItem"),
    DISTRIBUTE_VERTICAL(3, "editDistributeVerticalItem");

    private final int minimumComponents;
    private final String nameKey;

    Mode(int minimumComponents, String nameKey) {
      this.minimumComponents = minimumComponents;
      this.nameKey = nameKey;
    }

    /** How many components must be selected for the command to make sense. */
    public int getMinimumComponents() {
      return minimumComponents;
    }

    boolean isHorizontal() {
      return this == ALIGN_LEFT
          || this == ALIGN_CENTER
          || this == ALIGN_RIGHT
          || this == DISTRIBUTE_HORIZONTAL;
    }
  }

  private SelectionArrange() {}

  /** The components of {@code comps} that arrange commands move: everything but wires. */
  static List<Component> arrangeable(Collection<Component> comps) {
    final var ret = new ArrayList<Component>();
    for (final var comp : comps) {
      if (!(comp instanceof Wire)) ret.add(comp);
    }
    // Selections are hash sets; a stable order keeps the result and the wire routing repeatable.
    ret.sort(Comparator.comparing(Component::getLocation));
    return ret;
  }

  /** Whether {@code mode} applies to the selection: enough components besides wires. */
  public static boolean isApplicable(Selection sel, Mode mode) {
    var count = 0;
    for (final var comp : sel.getComponents()) {
      if (!(comp instanceof Wire) && ++count >= mode.getMinimumComponents()) return true;
    }
    return false;
  }

  /**
   * Computes where {@code mode} puts each component.
   *
   * <p>Alignment lines components up on the matching edge or centre of the bounding box around
   * all of them. Distribution keeps the two outermost components (by centre) where they are and
   * spaces the others so the gaps between neighbouring bounding boxes are equal.
   *
   * @return the new location of every component that moves; components already in place are left
   *     out, so an empty map means there is nothing to do
   */
  static Map<Component, Location> computeTargets(Collection<Component> components, Mode mode) {
    final var comps = arrangeable(components);
    final var ret = new LinkedHashMap<Component, Location>();
    if (comps.size() < mode.getMinimumComponents()) return ret;

    final var horizontal = mode.isHorizontal();
    final var desired = new HashMap<Component, Double>();
    switch (mode) {
      case ALIGN_LEFT, ALIGN_TOP -> {
        var edge = Integer.MAX_VALUE;
        for (final var comp : comps) edge = Math.min(edge, start(comp.getBounds(), horizontal));
        for (final var comp : comps) {
          desired.put(comp, (double) (edge - start(comp.getBounds(), horizontal)));
        }
      }
      case ALIGN_RIGHT, ALIGN_BOTTOM -> {
        var edge = Integer.MIN_VALUE;
        for (final var comp : comps) edge = Math.max(edge, end(comp.getBounds(), horizontal));
        for (final var comp : comps) {
          desired.put(comp, (double) (edge - end(comp.getBounds(), horizontal)));
        }
      }
      case ALIGN_CENTER, ALIGN_MIDDLE -> {
        var min = Integer.MAX_VALUE;
        var max = Integer.MIN_VALUE;
        for (final var comp : comps) {
          min = Math.min(min, start(comp.getBounds(), horizontal));
          max = Math.max(max, end(comp.getBounds(), horizontal));
        }
        final var centre = (min + max) / 2.0;
        for (final var comp : comps) desired.put(comp, centre - centre(comp.getBounds(), horizontal));
      }
      case DISTRIBUTE_HORIZONTAL, DISTRIBUTE_VERTICAL -> {
        final var order = new ArrayList<>(comps);
        order.sort(
            Comparator.<Component>comparingDouble(c -> centre(c.getBounds(), horizontal))
                .thenComparingDouble(c -> centre(c.getBounds(), !horizontal))
                .thenComparing(Component::getLocation));
        final var first = order.get(0).getBounds();
        final var last = order.get(order.size() - 1).getBounds();
        var totalSize = 0;
        for (final var comp : order) totalSize += size(comp.getBounds(), horizontal);
        final var span = end(last, horizontal) - start(first, horizontal);
        final var gap = (span - totalSize) / (double) (order.size() - 1);
        var pos = (double) start(first, horizontal);
        for (final var comp : order) {
          final var bds = comp.getBounds();
          desired.put(comp, pos - start(bds, horizontal));
          pos += size(bds, horizontal) + gap;
        }
        // The outer components are the anchors; rounding must not move them.
        desired.put(order.get(0), 0.0);
        desired.put(order.get(order.size() - 1), 0.0);
      }
      default -> throw new IllegalArgumentException("unknown mode " + mode);
    }

    for (final var comp : comps) {
      final var delta = desired.get(comp);
      final var loc = comp.getLocation();
      final var bds = comp.getBounds();
      final var snap = shouldSnap(comp);
      final int newX;
      final int newY;
      if (horizontal) {
        newX = place(loc.getX(), bds.getX(), delta, snap);
        newY = loc.getY();
      } else {
        newX = loc.getX();
        newY = place(loc.getY(), bds.getY(), delta, snap);
      }
      if (newX != loc.getX() || newY != loc.getY()) {
        ret.put(comp, Location.create(newX, newY, false));
      }
    }
    return ret;
  }

  /**
   * Moves a coordinate by {@code delta}, rounded to the grid, but never to a negative location and
   * never so that the bounding box goes further below zero than it already was.
   */
  static int place(int coord, int boundsStart, double delta, boolean snap) {
    final var minDelta = Math.max(-coord, Math.min(0, boundsStart) - boundsStart);
    var target = snap
        ? (int) Math.round((coord + delta) / GRID) * GRID
        : (int) Math.round(coord + delta);
    if (target - coord < minDelta) {
      target = coord + minDelta;
      if (snap) target = Math.ceilDiv(target, GRID) * GRID;
    }
    return target;
  }

  private static boolean shouldSnap(Component comp) {
    final var snap =
        comp.getFactory().getFeature(ComponentFactory.SHOULD_SNAP, comp.getAttributeSet());
    return snap == null || (Boolean) snap;
  }

  private static int start(Bounds bds, boolean horizontal) {
    return horizontal ? bds.getX() : bds.getY();
  }

  private static int size(Bounds bds, boolean horizontal) {
    return horizontal ? bds.getWidth() : bds.getHeight();
  }

  private static int end(Bounds bds, boolean horizontal) {
    return start(bds, horizontal) + size(bds, horizontal);
  }

  private static double centre(Bounds bds, boolean horizontal) {
    return start(bds, horizontal) + size(bds, horizontal) / 2.0;
  }

  /**
   * Whether putting the components at {@code targets} would stack two components exactly on top
   * of each other or put two exclusive ends (such as two outputs) on the same point.
   *
   * @param arranged every component taking part, moved or not
   */
  static boolean hasConflict(
      Circuit circuit, Collection<Component> arranged, Map<Component, Location> targets) {
    final var parts = new HashSet<>(arranged);
    final var exclusiveEnds = new HashSet<Location>();
    final var placements = new HashSet<List<Object>>();
    for (final var comp : arranged) {
      final var target = targets.getOrDefault(comp, comp.getLocation());
      final var dx = target.getX() - comp.getLocation().getX();
      final var dy = target.getY() - comp.getLocation().getY();
      final var bounds = comp.getBounds().translate(dx, dy);
      if (!placements.add(List.of(target, bounds))) return true;
      for (final var end : comp.getEnds()) {
        if (end == null || !end.isExclusive()) continue;
        final var endLoc = end.getLocation().translate(dx, dy);
        if (!exclusiveEnds.add(endLoc)) return true;
        final var other = circuit == null ? null : circuit.getExclusive(endLoc);
        if (other != null && !parts.contains(other)) return true;
      }
      if (circuit != null && targets.containsKey(comp)) {
        for (final var other : circuit.getAllContaining(target)) {
          if (!parts.contains(other)
              && !(other instanceof Wire)
              && other.getLocation().equals(target)
              && other.getBounds().equals(bounds)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /** Outcome of planning an arrange command. */
  public enum Outcome {
    /** The action does something. */
    OK,
    /** Everything is already in place. */
    NOTHING_TO_DO,
    /** The result would stack components or short exclusive ends. */
    CONFLICT
  }

  /** Checks what running {@code mode} on the selection would do, without changing anything. */
  public static Outcome check(Selection sel, Circuit circuit, Mode mode) {
    final var comps = arrangeable(sel.getComponents());
    final var targets = computeTargets(comps, mode);
    if (targets.isEmpty()) return Outcome.NOTHING_TO_DO;
    return hasConflict(circuit, comps, targets) ? Outcome.CONFLICT : Outcome.OK;
  }

  /**
   * Creates the undoable action for {@code mode}. The positions are worked out when the action
   * first runs; call {@link #check} first to find out whether it will do anything.
   */
  public static Action createAction(Selection sel, Mode mode) {
    return createAction(sel, mode, AppPreferences.MOVE_KEEP_CONNECT.getBoolean());
  }

  /**
   * As {@link #createAction(Selection, Mode)}.
   *
   * @param keepConnected whether wires attached to a moved component are rerouted to follow it
   */
  static Action createAction(Selection sel, Mode mode, boolean keepConnected) {
    return new Arrange(sel, mode, keepConnected);
  }

  private static final class Arrange extends Action {
    private final Selection sel;
    private final Mode mode;
    private final boolean keepConnected;
    private final List<CircuitTransaction> forward = new ArrayList<>();
    private final List<CircuitTransaction> reverse = new ArrayList<>();
    private boolean done;

    Arrange(Selection sel, Mode mode, boolean keepConnected) {
      this.sel = sel;
      this.mode = mode;
      this.keepConnected = keepConnected;
    }

    @Override
    public void doIt(Project proj) {
      if (done) {
        for (final var xn : forward) xn.execute();
        return;
      }
      done = true;
      final var circuit = proj.getCurrentCircuit();
      if (!sel.getFloatingComponents().isEmpty()) {
        // A paste still floating becomes part of the circuit first, as when it is dragged.
        final var drop = new CircuitMutation(circuit);
        sel.dropAll(drop);
        run(drop);
      }
      final var comps = arrangeable(sel.getComponents());
      final var targets = computeTargets(comps, mode);
      if (hasConflict(circuit, comps, targets)) return;

      // Components moving by the same offset move together, so wires between them stay as they
      // are; each group is one move of the selection, wires following as when dragging.
      final var groups = new LinkedHashMap<Location, List<Component>>();
      for (final var entry : targets.entrySet()) {
        final var comp = entry.getKey();
        final var delta =
            Location.create(
                entry.getValue().getX() - comp.getLocation().getX(),
                entry.getValue().getY() - comp.getLocation().getY(),
                false);
        groups.computeIfAbsent(delta, k -> new ArrayList<>()).add(comp);
      }
      for (final var group : groups.entrySet()) {
        final var dx = group.getKey().getX();
        final var dy = group.getKey().getY();
        ReplacementMap wires = null;
        if (keepConnected) {
          final var result = new MoveGesture(null, circuit, group.getValue()).forceRequest(dx, dy);
          if (result != null) wires = result.getReplacementMap();
        }
        final var xn = new CircuitMutation(circuit);
        for (final var comp : group.getValue()) {
          final var moved =
              comp.getFactory()
                  .createComponent(
                      comp.getLocation().translate(dx, dy), comp.getAttributeSet());
          xn.replace(comp, moved);
        }
        if (wires != null) xn.replace(wires);
        run(xn);
      }
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

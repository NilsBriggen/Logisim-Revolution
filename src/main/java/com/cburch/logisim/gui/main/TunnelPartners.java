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
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.gui.canvas.CanvasStyle;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.wiring.Tunnel;
import java.awt.BasicStroke;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

/**
 * Outlines the tunnels that share a label with the selected one.
 *
 * <p>Tunnels join by name, invisibly. Selecting one now shows where the rest of its net comes out,
 * which is also where to look when the widths on the two sides disagree.
 */
final class TunnelPartners {

  private TunnelPartners() {}

  /** The other tunnels in the circuit named like any tunnel among {@code chosen}. */
  static List<Component> partnersOf(Circuit circuit, Collection<Component> chosen, Component extra) {
    final var labels = new HashSet<String>();
    for (final var component : chosen) addLabel(labels, component);
    if (extra != null) addLabel(labels, extra);
    if (labels.isEmpty()) return List.of();
    final var partners = new ArrayList<Component>();
    for (final var component : circuit.getNonWires()) {
      if (component == extra || chosen.contains(component)) continue;
      final var label = labelOf(component);
      if (label != null && labels.contains(label)) partners.add(component);
    }
    return partners;
  }

  private static void addLabel(Collection<String> labels, Component component) {
    final var label = labelOf(component);
    if (label != null) labels.add(label);
  }

  /** The label a tunnel connects by, trimmed as the wiring does, or null for anything else. */
  private static String labelOf(Component component) {
    if (!(component.getFactory() instanceof Tunnel)) return null;
    final var label = component.getAttributeSet().getValue(StdAttr.LABEL);
    if (label == null || label.isBlank()) return null;
    return label.trim();
  }

  static void draw(Graphics g, Circuit circuit, Collection<Component> chosen, Component extra) {
    if (circuit == null) return;
    final var partners = partnersOf(circuit, chosen, extra);
    if (partners.isEmpty()) return;
    final var g2 = (Graphics2D) g.create();
    try {
      g2.setColor(CanvasStyle.marker(false));
      g2.setStroke(
          new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 1f,
              new float[] {4f, 3f}, 0f));
      final var radius = CanvasStyle.bodyRadius() + 3;
      for (final var partner : partners) {
        final var bounds = partner.getBounds(g2).expand(3);
        g2.drawRoundRect(
            bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight(), radius, radius);
      }
    } finally {
      g2.dispose();
    }
  }
}

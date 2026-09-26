/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.circuit;

import static com.cburch.logisim.circuit.Strings.S;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.prefs.AppPreferences;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * What can be said about a wire beyond its geometry: its width, the value on it, and whether the
 * components it joins disagree about how wide it is.
 *
 * <p>A wire's colour was the only way to learn any of this, and orange ("the widths do not agree")
 * said nothing about which widths, or where. The tooltip and the inspector both read it from here.
 */
public final class WireInfo {

  private WireInfo() {}

  /**
   * The different widths the ends of this wire's net ask for, smallest first.
   *
   * @return an empty list when they agree, or when nothing on the net sets a width
   */
  public static List<Integer> conflictingWidths(Circuit circuit, Wire wire) {
    final var exceptions = circuit.getWidthIncompatibilityData();
    if (exceptions == null || exceptions.isEmpty()) return List.of();
    final var net = circuit.getWireSet(wire);
    final var widths = new TreeSet<Integer>();
    for (final var exception : exceptions) {
      if (touches(exception, net, wire)) {
        for (var i = 0; i < exception.size(); i++) {
          widths.add(exception.getBitWidth(i).getWidth());
        }
      }
    }
    return widths.size() > 1 ? List.copyOf(widths) : List.of();
  }

  private static boolean touches(WidthIncompatibilityData exception, WireSet net, Wire wire) {
    for (var i = 0; i < exception.size(); i++) {
      final Location point = exception.getPoint(i);
      if (net.containsLocation(point) || wire.endsAt(point)) return true;
    }
    return false;
  }

  /** The width in bits, or {@link BitWidth#UNKNOWN} when it is unknown or in conflict. */
  public static BitWidth width(Circuit circuit, Wire wire) {
    if (!conflictingWidths(circuit, wire).isEmpty()) return BitWidth.UNKNOWN;
    return circuit.getWidth(wire.getEnd0());
  }

  /** The width as a reader would say it: "8 bits", "1 bit", or what the trouble is. */
  public static String widthText(Circuit circuit, Wire wire) {
    final var conflict = conflictingWidths(circuit, wire);
    if (!conflict.isEmpty()) return S.get("wireInfoWidthConflict", joinWidths(conflict));
    return bitsText(circuit.getWidth(wire.getEnd0()));
  }

  static String bitsText(BitWidth width) {
    if (width == null || width == BitWidth.UNKNOWN || width.getWidth() <= 0) {
      return S.get("wireInfoWidthUnknown");
    }
    return width.getWidth() == 1 ? S.get("wireInfoOneBit") : S.get("wireInfoBits", width.getWidth());
  }

  /** Widths as "4 ≠ 8", the way the canvas badges write them. */
  public static String joinWidths(List<Integer> widths) {
    return widths.stream().map(String::valueOf).collect(Collectors.joining(" ≠ "));
  }

  /**
   * The value on the wire, in the radixes the Poke tool uses for its wire callout.
   *
   * @return a short explanation instead when the wire has no usable width
   */
  public static String valueText(Circuit circuit, CircuitState state, Wire wire) {
    if (state == null) return "";
    if (width(circuit, wire) == BitWidth.UNKNOWN) return S.get("wireInfoNoValue");
    final var value = state.getValue(wire.getEnd0());
    var first = RadixOption.decode(AppPreferences.POKE_WIRE_RADIX1.get());
    if (first == null) first = RadixOption.RADIX_2;
    var text = first.toString(value);
    final var second = RadixOption.decode(AppPreferences.POKE_WIRE_RADIX2.get());
    if (second != null && second != first && value.getWidth() > 1) {
      text += " / " + second.toString(value);
    }
    return text;
  }

  /** A few lines for the tooltip shown while hovering over the wire. */
  public static String toolTip(Circuit circuit, CircuitState state, Wire wire) {
    final var conflict = conflictingWidths(circuit, wire);
    if (!conflict.isEmpty()) {
      return "<html>" + S.get("wireTipConflict", joinWidths(conflict))
          + "<br>" + S.get("wireTipConflictHint") + "</html>";
    }
    final var width = circuit.getWidth(wire.getEnd0());
    if (width == BitWidth.UNKNOWN) {
      return "<html>" + S.get("wireTipUnknown") + "</html>";
    }
    return "<html>" + S.get("wireTipWidth", bitsText(width))
        + "<br>" + S.get("wireTipValue", escape(valueText(circuit, state, wire))) + "</html>";
  }

  private static String escape(String text) {
    return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
  }
}

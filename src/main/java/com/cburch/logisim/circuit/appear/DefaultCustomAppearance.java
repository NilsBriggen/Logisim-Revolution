/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.circuit.appear;

import java.awt.Font;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

import com.cburch.draw.model.CanvasObject;
import com.cburch.draw.shapes.DrawAttr;
import com.cburch.draw.shapes.Rectangle;
import com.cburch.draw.shapes.Text;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.wiring.Pin;

public class DefaultCustomAppearance {

  private static final int OFFS = 50;

  /** Font of the pin names in a labelled default; small enough for the 10-unit port pitch. */
  private static final Font LABEL_FONT = new Font(Font.MONOSPACED, Font.PLAIN, 10);

  /** Longest pin name, in characters, that fits on one side of the box. */
  private static final int MAX_LABEL_LENGTH = 14;

  /**
   * The default custom appearance as it was before pin names were added: a plain box with the
   * anchor on the first east port. Circuits read from a file keep it, so a file whose unedited
   * custom appearance was never saved (it is regenerated on load) looks exactly as before.
   */
  public static List<CanvasObject> build(Collection<Instance> pins) {
    return build(pins, false);
  }

  /**
   * Builds the default custom appearance. A labelled one names every port inside the box and puts
   * the anchor on the box's top-left corner, where it covers no port and so cannot steal a click
   * meant for the first output.
   */
  public static List<CanvasObject> build(Collection<Instance> pins, boolean labelled) {
    final var edge = new HashMap<Direction, List<Instance>>();
    edge.put(Direction.EAST, new ArrayList<>());
    edge.put(Direction.WEST, new ArrayList<>());
    var maxLeftLabelLength = 0;
    var maxRightLabelLength = 0;

    if (!pins.isEmpty()) {
      for (final var pin : pins) {
        Direction pinEdge;
        final var label = new Text(0, 0, pin.getAttributeValue(StdAttr.LABEL));
        final var labelWidth = label.getText().length() * DrawAttr.FIXED_FONT_CHAR_WIDTH;
        if (pin.getAttributeValue(Pin.ATTR_TYPE) == Pin.OUTPUT) {
          pinEdge = Direction.EAST;
          if (labelWidth > maxRightLabelLength) maxRightLabelLength = labelWidth;
        } else {
          pinEdge = Direction.WEST;
          if (labelWidth > maxLeftLabelLength) maxLeftLabelLength = labelWidth;
        }
        final var e = edge.get(pinEdge);
        e.add(pin);
      }
    }

    for (final var entry : edge.entrySet()) {
      DefaultAppearance.sortPinList(entry.getValue(), entry.getKey());
    }

    final var numEast = edge.get(Direction.EAST).size();
    final var numWest = edge.get(Direction.WEST).size();
    final var maxVert = Math.max(numEast, numWest);

    final var textWidth = 25 * DrawAttr.FIXED_FONT_CHAR_WIDTH;
    final var width = (textWidth / 10) * 10 + 20;
    final var height = (maxVert > 0) ? maxVert * 10 + 10 : 10;

    // compute position of anchor relative to top left corner of box
    int ax = 0;
    int ay = 0;
    if (labelled) { // anchor is the top left corner
      ax = 0;
      ay = 0;
    } else if (numEast > 0) { // anchor is on east side
      ax = width;
      ay = 10;
    } else if (numWest > 0) { // anchor is on west side
      ax = 0;
      ay = 10;
    }

    // place rectangle so anchor is on the grid
    final var rx = OFFS + (9 - (ax + 9) % 10);
    final var ry = OFFS + (9 - (ay + 9) % 10);

    final var ret = new ArrayList<CanvasObject>();
    placePins(ret, edge.get(Direction.WEST), rx, ry + 10, 0, 10);
    placePins(ret, edge.get(Direction.EAST), rx + width, ry + 10, 0, 10);
    if (labelled) {
      placeLabels(ret, edge.get(Direction.WEST), rx + 4, ry + 10, true);
      placeLabels(ret, edge.get(Direction.EAST), rx + width - 4, ry + 10, false);
    }
    final var rect = new Rectangle(rx, ry, width, height);
    rect.setValue(DrawAttr.STROKE_WIDTH, 1);
    ret.add(rect);
    ret.add(new AppearanceAnchor(Location.create(rx + ax, ry + ay, true)));
    return ret;
  }

  private static void placeLabels(List<CanvasObject> dest, List<Instance> pins, int x, int y, boolean left) {
    for (final var pin : pins) {
      var name = pin.getAttributeValue(StdAttr.LABEL);
      if (name != null && !name.isEmpty()) {
        if (name.length() > MAX_LABEL_LENGTH) name = name.substring(0, MAX_LABEL_LENGTH - 1) + "\u2026";
        final var text = new Text(x, y, name);
        text.setValue(DrawAttr.FONT, LABEL_FONT);
        text.setValue(DrawAttr.HALIGNMENT, left ? DrawAttr.HALIGN_LEFT : DrawAttr.HALIGN_RIGHT);
        text.setValue(DrawAttr.VALIGNMENT, DrawAttr.VALIGN_MIDDLE);
        dest.add(text);
      }
      y += 10;
    }
  }

  private static void placePins(
      List<CanvasObject> dest, List<Instance> pins, int x, int y, int dX, int dY) {
    for (final var pin : pins) {
      dest.add(new AppearancePort(Location.create(x, y, true), pin));
      x += dX;
      y += dY;
    }
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.fpga.data;

import com.cburch.logisim.fpga.gui.BoardManipulator;
import com.cburch.logisim.prefs.AppPreferences;
import java.awt.Frame;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.swing.JPanel;

public class IoComponentsInformation {

  private Frame parent;
  private final ArrayList<FpgaIoInformationContainer> ioComps;
  private int defaultStandard = 0;
  private int defaultDriveStrength = 0;
  private int defaultPullSelection = 0;
  private int defaultActivity = 1;
  private final boolean mapMode;
  private final int imageHeight;
  private final FpgaIoInformationContainer[][] lookup;
  private FpgaIoInformationContainer highlighted;
  private ArrayList<IoComponentsListener> listeners;

  public IoComponentsInformation(Frame parentFrame, boolean mapMode) {
    parent = parentFrame;
    imageHeight =
        mapMode
            ? BoardManipulator.IMAGE_HEIGHT + BoardManipulator.CONSTANT_BAR_HEIGHT
            : BoardManipulator.IMAGE_HEIGHT;
    ioComps = new ArrayList<>();
    lookup = new FpgaIoInformationContainer[BoardManipulator.IMAGE_WIDTH][imageHeight];
    this.mapMode = mapMode;
    clear();
  }

  public void clear() {
    ioComps.clear();
    for (var x = 0; x < BoardManipulator.IMAGE_WIDTH; x++)
      for (var y = 0; y < imageHeight; y++) lookup[x][y] = null;
    highlighted = null;
  }

  public Frame getParentFrame() {
    return parent;
  }

  public void setParentFrame(Frame parent) {
    this.parent = parent;
  }

  public boolean hasOverlap(BoardRectangle rect) {
    var overlap = false;
    for (var io : ioComps) overlap |= io.getRectangle().overlap(rect);
    return overlap;
  }

  public boolean hasOverlap(BoardRectangle orig, BoardRectangle update) {
    var overlap = false;
    for (var io : ioComps)
      if (!io.getRectangle().equals(orig)) overlap |= io.getRectangle().overlap(update);
    return overlap;
  }

  public boolean hasComponents() {
    return !ioComps.isEmpty();
  }

  public List<FpgaIoInformationContainer> getComponents() {
    return ioComps;
  }

  public boolean hasHighlighted() {
    return highlighted != null;
  }

  public FpgaIoInformationContainer getHighligted() {
    return highlighted;
  }

  public void setHighligted(FpgaIoInformationContainer comp) {
    highlighted = comp;
  }

  public boolean tryMap(JPanel parent) {
    if (!mapMode) return false;
    if (highlighted == null) return false;
    final var bus = busTargets(highlighted, ioComps);
    if (bus != null) return mapBus(highlighted.getSelectedMapInfo().getMap(), bus);
    return highlighted.tryMap(parent);
  }

  /**
   * The board components a whole multi-bit component can be spread over in one click: the clicked
   * single-pin I/O for bit 0 and further free I/Os of the same kind in the same row for the other
   * bits, going left (the usual LSB-right order of LED rows and switch banks), or right when there
   * are not enough on the left. Null when that does not apply, so the per-bit dialog is used.
   */
  static List<FpgaIoInformationContainer> busTargets(
      FpgaIoInformationContainer clicked, List<FpgaIoInformationContainer> all) {
    final var info = clicked.getSelectedMapInfo();
    if (info == null || info.getPin() >= 0 || clicked.getNrOfPins() != 1) return null;
    final var map = info.getMap();
    final var bits = map.getNrOfPins();
    if (bits < 2 || !clicked.isFree()) return null;
    final var here = clicked.getRectangle();
    final var left = new ArrayList<FpgaIoInformationContainer>();
    final var right = new ArrayList<FpgaIoInformationContainer>();
    for (final var io : all) {
      if (io == clicked || io.getType() != clicked.getType() || io.getNrOfPins() != 1) continue;
      if (!io.isSelectable() || !io.isFree()) continue;
      final var rect = io.getRectangle();
      final var sameRow =
          Math.abs(
                  (rect.getYpos() + rect.getHeight() / 2) - (here.getYpos() + here.getHeight() / 2))
              <= Math.max(rect.getHeight(), here.getHeight()) / 2;
      if (!sameRow) continue;
      if (rect.getXpos() < here.getXpos()) left.add(io);
      else if (rect.getXpos() > here.getXpos()) right.add(io);
    }
    left.sort((a, b) -> Integer.compare(b.getRectangle().getXpos(), a.getRectangle().getXpos()));
    right.sort(Comparator.comparingInt(io -> io.getRectangle().getXpos()));
    final var others = left.size() >= bits - 1 ? left : right.size() >= bits - 1 ? right : null;
    if (others == null) return null;
    final var ret = new ArrayList<FpgaIoInformationContainer>(bits);
    ret.add(clicked);
    ret.addAll(others.subList(0, bits - 1));
    return ret;
  }

  private static boolean mapBus(MapComponent map, List<FpgaIoInformationContainer> targets) {
    map.unmap();
    var ok = true;
    for (var bit = 0; bit < targets.size() && ok; bit++) ok = map.tryMap(bit, targets.get(bit), 0);
    if (!ok) map.unmap();
    return ok;
  }

  public void setSelectable(MapListModel.MapInfo comp, float scale) {
    for (var io : ioComps) {
      if (io.setSelectable(comp)) this.fireRedraw(io.getRectangle(), scale);
    }
  }

  public void removeSelectable(float scale) {
    for (final FpgaIoInformationContainer io : ioComps) {
      if (io.removeSelectable()) this.fireRedraw(io.getRectangle(), scale);
    }
  }

  public void addComponent(FpgaIoInformationContainer comp, float scale) {
    if (!ioComps.contains(comp)) {
      ioComps.add(comp);
      var rect = comp.getRectangle();
      for (var x = rect.getXpos(); x < rect.getXpos() + rect.getWidth(); x++)
        for (var y = rect.getYpos(); y < rect.getYpos() + rect.getHeight(); y++)
          if (x < BoardManipulator.IMAGE_WIDTH && y < imageHeight) lookup[x][y] = comp;
      if (mapMode) return;
      fireRedraw(comp.getRectangle(), scale);
    }
  }

  public void removeComponent(FpgaIoInformationContainer comp, float scale) {
    if (ioComps.contains(comp)) {
      if (highlighted == comp) highlighted = null;
      ioComps.remove(comp);
      var rect = comp.getRectangle();
      for (var x = rect.getXpos(); x < rect.getXpos() + rect.getWidth(); x++)
        for (var y = rect.getYpos(); y < rect.getYpos() + rect.getHeight(); y++)
          lookup[x][y] = null;
      fireRedraw(comp.getRectangle(), scale);
    }
  }

  public void replaceComponent(
      FpgaIoInformationContainer oldI, FpgaIoInformationContainer newI, MouseEvent e, float scale) {
    if (!ioComps.contains(oldI)) return;
    removeComponent(oldI, scale);
    addComponent(newI, scale);
    mouseMoved(e, scale);
  }

  public void mouseMoved(MouseEvent e, float scale) {
    var xpos = AppPreferences.getDownScaled(e.getX(), scale);
    var ypos = AppPreferences.getDownScaled(e.getY(), scale);
    xpos = Math.max(xpos, 0);
    xpos = Math.min(xpos, BoardManipulator.IMAGE_WIDTH - 1);
    ypos = Math.max(ypos, 0);
    ypos = Math.min(ypos, imageHeight - 1);
    var selected = lookup[xpos][ypos];
    if (selected == null && mapMode) {
      // Board LEDs and switches are often a dozen pixels wide: a near miss still picks the
      // closest one, instead of silently doing nothing.
      selected = nearest(ioComps, xpos, ypos, HIT_SLOP);
      if (selected != null) {
        final var rect = selected.getRectangle();
        xpos = Math.max(rect.getXpos(), Math.min(xpos, rect.getXpos() + rect.getWidth() - 1));
        ypos = Math.max(rect.getYpos(), Math.min(ypos, rect.getYpos() + rect.getHeight() - 1));
      }
    }
    if (selected == highlighted) {
      if (highlighted != null && highlighted.selectedPinChanged(xpos, ypos))
        fireRedraw(highlighted.getRectangle(), scale);
      return;
    }
    if (highlighted != null) {
      highlighted.unsetHighlighted();
      fireRedraw(highlighted.getRectangle(), scale);
    }
    if (selected != null) {
      selected.setHighlighted();
      fireRedraw(selected.getRectangle(), scale);
    }
    highlighted = selected;
  }

  /** How far (in board image pixels) outside a component a click still reaches it. */
  static final int HIT_SLOP = 8;

  /** The component whose rectangle is closest to (x, y), if no farther than {@code slop}. */
  static FpgaIoInformationContainer nearest(
      List<FpgaIoInformationContainer> comps, int x, int y, int slop) {
    FpgaIoInformationContainer best = null;
    var bestDistance = Long.MAX_VALUE;
    for (final var comp : comps) {
      final var rect = comp.getRectangle();
      if (rect == null) continue;
      final long dx =
          Math.max(0, Math.max(rect.getXpos() - x, x - (rect.getXpos() + rect.getWidth() - 1)));
      final long dy =
          Math.max(0, Math.max(rect.getYpos() - y, y - (rect.getYpos() + rect.getHeight() - 1)));
      final var distance = dx * dx + dy * dy;
      if (distance <= (long) slop * slop && distance < bestDistance) {
        best = comp;
        bestDistance = distance;
      }
    }
    return best;
  }

  public void mouseExited(float scale) {
    if (highlighted != null) {
      highlighted.unsetHighlighted();
      fireRedraw(highlighted.getRectangle(), scale);
      highlighted = null;
    }
  }

  public void addListener(IoComponentsListener l) {
    if (listeners == null) {
      listeners = new ArrayList<>();
      listeners.add(l);
    } else if (!listeners.contains(l)) listeners.add(l);
  }

  public void removeListener(IoComponentsListener l) {
    if (listeners != null) listeners.remove(l);
  }

  public int getDefaultActivity() {
    return defaultActivity;
  }

  public int getDefaultDriveStrength() {
    return defaultDriveStrength;
  }

  public int getDefaultPullSelection() {
    return defaultPullSelection;
  }

  public int getDefaultStandard() {
    return defaultStandard;
  }

  public void setDefaultActivity(int value) {
    defaultActivity = value;
  }

  public void setDefaultDriveStrength(int value) {
    defaultDriveStrength = value;
  }

  public void setDefaultPullSelection(int value) {
    defaultPullSelection = value;
  }

  public void setDefaultStandard(int value) {
    defaultStandard = value;
  }

  public void paint(Graphics2D g, float scale) {
    for (var c : ioComps) c.paint(g, scale);
  }

  private void fireRedraw(BoardRectangle rect, float scale) {
    if (listeners == null) return;
    var area =
        new Rectangle(
            AppPreferences.getScaled(rect.getXpos() - 2, scale),
            AppPreferences.getScaled(rect.getYpos() - 2, scale),
            AppPreferences.getScaled(rect.getWidth() + 4, scale),
            AppPreferences.getScaled(rect.getHeight() + 4, scale));
    for (var l : listeners) l.repaintRequest(area);
  }
}

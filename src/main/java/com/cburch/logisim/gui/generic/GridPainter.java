/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.generic;

import com.cburch.logisim.prefs.AppPreferences;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.MemoryImageSource;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Arrays;

public class GridPainter implements PropertyChangeListener {
  public static final String ZOOM_PROPERTY = "zoom";
  public static final String SHOW_GRID_PROPERTY = "showgrid";
  private final Component destination;
  private final PropertyChangeSupport support;
  private final int gridSize = 10;
  private Listener listener;
  private ZoomModel zoomModel;
  private boolean showGrid = true;
  private double zoomFactor = 1.0;
  private Image gridImage;
  private int gridImageWidth;

  public GridPainter(Component destination) {
    this.destination = destination;
    support = new PropertyChangeSupport(this);
    createGridImage(gridSize, zoomFactor);

    AppPreferences.GRID_BG_COLOR.addPropertyChangeListener(this);
    AppPreferences.GRID_DOT_COLOR.addPropertyChangeListener(this);
    AppPreferences.GRID_ZOOMED_DOT_COLOR.addPropertyChangeListener(this);
  }

  public void addPropertyChangeListener(String prop, PropertyChangeListener listener) {
    support.addPropertyChangeListener(prop, listener);
  }

  public boolean getShowGrid() {
    return showGrid;
  }

  public void setShowGrid(boolean value) {
    if (showGrid != value) {
      showGrid = value;
      support.firePropertyChange(SHOW_GRID_PROPERTY, !value, value);
    }
  }

  public double getZoomFactor() {
    return zoomFactor;
  }

  public void setZoomFactor(double value) {
    final var oldValue = zoomFactor;
    if (oldValue != value) {
      zoomFactor = value;
      createGridImage(gridSize, value);
      support.firePropertyChange(ZOOM_PROPERTY, oldValue, value);
    }
  }

  public ZoomModel getZoomModel() {
    return zoomModel;
  }

  public void setZoomModel(ZoomModel model) {
    final var old = zoomModel;
    if (model != old) {
      if (listener == null) {
        listener = new Listener();
      }
      if (old != null) {
        old.removePropertyChangeListener(ZoomModel.ZOOM, listener);
        old.removePropertyChangeListener(ZoomModel.SHOW_GRID, listener);
      }
      zoomModel = model;
      if (model != null) {
        model.addPropertyChangeListener(ZoomModel.ZOOM, listener);
        model.addPropertyChangeListener(ZoomModel.SHOW_GRID, listener);
      }
      if (model != null) {
        setShowGrid(model.getShowGrid());
        setZoomFactor(model.getZoomFactor());
      }
      destination.repaint();
    }
  }

  public void paintGrid(Graphics g) {
    if (!showGrid) return;

    final var clip = g.getClipBounds();
    final var x0 = (clip.x / gridImageWidth) * gridImageWidth; // round down to multiple of w
    final var y0 = (clip.y / gridImageWidth) * gridImageWidth;
    for (var x = 0; x < clip.width + gridImageWidth; x += gridImageWidth) {
      for (var y = 0; y < clip.height + gridImageWidth; y += gridImageWidth) {
        g.drawImage(gridImage, x0 + x, y0 + y, destination);
      }
    }
  }

  public void removePropertyChangeListener(String prop, PropertyChangeListener listener) {
    support.removePropertyChangeListener(prop, listener);
  }

  private void createGridImage(int size, double f) {
    final var w = tileWidth(size, f);
    gridImage = destination.createImage(new MemoryImageSource(w, w, gridPixels(size, f, w), 0, w));
    gridImageWidth = w;
  }

  /**
   * The edge length of the repeating tile the grid is drawn from.
   *
   * <p>Five grid steps, doubled until the tile is large enough that blitting it across the canvas
   * is not dominated by the per-tile cost.
   */
  static int tileWidth(int size, double f) {
    if (size <= 0 || !Double.isFinite(f) || f <= 0) {
      throw new IllegalArgumentException("Grid size and zoom must be finite and positive");
    }
    var ww = f * size * 5;
    while (2 * ww < 150) ww *= 2;
    return (int) Math.round(ww);
  }

  /** The pixels of that tile. Separated from the image so it can be examined without a screen. */
  static int[] gridPixels(int size, double f, int w) {
    final var pix = new int[w * w];
    Arrays.fill(pix, AppPreferences.GRID_BG_COLOR.get());

    // The same document-space hierarchy at every zoom. Suppress minor points when they
    // become too dense, without changing snapping or circuit coordinates.
    final var step = size * f;
    final var dotSize = Math.max(1, (int) f);
    final var offset = -(dotSize / 2);
    for (var row = 0; Math.round(row * step) < w; row++) {
      final var y = (int) Math.round(row * step);
      for (var column = 0; Math.round(column * step) < w; column++) {
        final var major = row % 5 == 0 && column % 5 == 0;
        if (!major && step < 4) continue;
        final var color = major ? AppPreferences.GRID_DOT_COLOR.get()
            : AppPreferences.GRID_ZOOMED_DOT_COLOR.get();
        final var x = (int) Math.round(column * step);
        for (var dy = 0; dy < dotSize; dy++) {
          for (var dx = 0; dx < dotSize; dx++) {
            // Wrap the point on a tile edge so adjacent tiles retain a complete dot.
            pix[Math.floorMod(y + offset + dy, w) * w + Math.floorMod(x + offset + dx, w)] = color;
          }
        }
      }
    }
    return pix;
  }

  @Override
  public void propertyChange(PropertyChangeEvent event) {
    if (AppPreferences.GRID_BG_COLOR.isSource(event)
        || AppPreferences.GRID_DOT_COLOR.isSource(event)
        || AppPreferences.GRID_ZOOMED_DOT_COLOR.isSource(event)) {
      createGridImage(gridSize, zoomFactor);
      destination.repaint();
    }
  }

  private class Listener implements PropertyChangeListener {
    @Override
    public void propertyChange(PropertyChangeEvent event) {
      final var prop = event.getPropertyName();
      final var val = event.getNewValue();
      if (prop.equals(ZoomModel.ZOOM)) {
        setZoomFactor((Double) val);
        destination.repaint();
      } else if (prop.equals(ZoomModel.SHOW_GRID)) {
        setShowGrid((Boolean) val);
        destination.repaint();
      }
    }
  }
}

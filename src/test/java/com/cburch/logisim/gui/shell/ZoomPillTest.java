/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.shell;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.cburch.logisim.gui.generic.ZoomControl;
import com.cburch.logisim.gui.generic.ZoomModel;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.UiFonts;
import com.cburch.logisim.util.UiScale;
import java.awt.Font;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

class ZoomPillTest {

  @Test
  void keyboardZoomControlsAndActiveStatusBindingFollowModelAndDisplayability() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var layout = new Model(1.0);
      final var appearance = new Model(2.0);
      final var control = mock(ZoomControl.class);
      final var pill = new ZoomPill(control, layout);
      final var readings = new ArrayList<String>();
      pill.setZoomTextListener(readings::add);
      assertEquals(List.of("100%"), readings);
      assertEquals(0, layout.listenerCount(), "undisplayed pill must not retain model listeners");
      pill.addNotify();
      try {
        assertEquals(2, layout.listenerCount());
        final var buttons = ShellChromeTest.components(pill, AbstractButton.class);
        assertEquals(5, buttons.size());
        for (final var button : buttons) {
          assertTrue(button.isFocusable());
          assertTrue(button.isFocusPainted());
          assertFalse(button.getAccessibleContext().getAccessibleName().isBlank());
        }
        ShellChromeTest.activate(buttons.get(0), KeyEvent.VK_ENTER);
        verify(control).zoomOut();
        ShellChromeTest.activate(buttons.get(2), KeyEvent.VK_SPACE);
        verify(control).zoomIn();
        ShellChromeTest.activate(buttons.get(4), KeyEvent.VK_SPACE);
        assertTrue(layout.getShowGrid());

        pill.setModel(appearance);
        assertEquals(0, layout.listenerCount());
        assertEquals(2, appearance.listenerCount());
        assertEquals("200%", readings.getLast());
        layout.setZoomFactor(3.0);
        assertEquals("200%", readings.getLast(), "inactive model changed the status");
        ShellChromeTest.activate(buttons.get(1), KeyEvent.VK_ENTER);
        assertEquals(1.0, appearance.getZoomFactor());
        assertEquals(1, appearance.centerRequests);
        assertEquals("100%", readings.getLast());

        pill.setModel(appearance);
        assertEquals(2, appearance.listenerCount(), "same-model refresh duplicated subscriptions");
        pill.removeNotify();
        assertEquals(0, appearance.listenerCount());
        appearance.setZoomFactor(4.0);
        assertEquals("100%", readings.getLast(), "disposed pill still updated the status");
        pill.addNotify();
        assertEquals(2, appearance.listenerCount());
        assertEquals("400%", readings.getLast());
        pill.setModel(null);
        assertEquals(0, appearance.listenerCount());
        assertEquals("", readings.getLast());
        assertFalse(pill.isVisible());
      } finally {
        pill.removeNotify();
      }
    });
  }

  @Test
  void existingPercentButtonRefreshesItsFontAndPaddingWithoutDoubleScaling() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var scale = UiScale.factor();
      final var font = UIManager.get("Label.font");
      final var pill = new ZoomPill(mock(ZoomControl.class), new Model(1.25));
      pill.addNotify();
      try {
        for (final var nextScale : new double[] {1.0, 1.6, 2.0, 1.0}) {
          UiScale.setFactor(nextScale);
          UIManager.put("Label.font",
              new Font(Font.DIALOG, Font.PLAIN, (int) Math.round(13 * nextScale)));
          Theme.fireChanged();
          final var percent = ShellChromeTest.components(pill, AbstractButton.class).get(1);
          assertEquals("125%", percent.getText());
          assertEquals(UiFonts.small(), percent.getFont());
          assertEquals(Spacing.XS, percent.getMargin().left);
          assertEquals(Spacing.xs(), pill.getInsets().top);
          assertEquals(Spacing.sm(), pill.getInsets().left);
          assertTrue(percent.getPreferredSize().height
              >= percent.getFontMetrics(percent.getFont()).getHeight());
        }
      } finally {
        pill.removeNotify();
        UIManager.put("Label.font", font);
        UiScale.setFactor(scale);
      }
    });
  }

  private static final class Model implements ZoomModel {
    private final PropertyChangeSupport changes = new PropertyChangeSupport(this);
    private double zoom;
    private boolean grid;
    private int centerRequests;

    Model(double zoom) {
      this.zoom = zoom;
    }

    int listenerCount() {
      return changes.getPropertyChangeListeners().length;
    }

    @Override
    public boolean getShowGrid() {
      return grid;
    }

    @Override
    public void setShowGrid(boolean value) {
      final var old = grid;
      grid = value;
      changes.firePropertyChange(SHOW_GRID, old, value);
    }

    @Override
    public double getZoomFactor() {
      return zoom;
    }

    @Override
    public List<Double> getZoomOptions() {
      return List.of(0.5, 1.0, 2.0, 4.0);
    }

    @Override
    public void setZoomFactor(double value) {
      final var old = zoom;
      zoom = value;
      changes.firePropertyChange(ZOOM, old, value);
    }

    @Override
    public void setZoomFactor(double value, MouseEvent event) {
      setZoomFactor(value);
    }

    @Override
    public void setZoomFactorCenter(double value) {
      centerRequests++;
      setZoomFactor(value);
    }

    @Override
    public void addPropertyChangeListener(String property, PropertyChangeListener listener) {
      changes.addPropertyChangeListener(property, listener);
    }

    @Override
    public void removePropertyChangeListener(String property, PropertyChangeListener listener) {
      changes.removePropertyChangeListener(property, listener);
    }
  }
}


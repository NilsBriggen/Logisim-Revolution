/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.chrono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.log.Model;
import com.cburch.logisim.gui.log.Signal;
import com.cburch.logisim.gui.log.SignalInfo;
import com.cburch.logisim.util.UiFonts;
import com.cburch.logisim.util.UiScale;
import com.formdev.flatlaf.FlatLightLaf;
import com.formdev.flatlaf.util.UIScale;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import javax.swing.DefaultListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;

class RightPanelTest {

  @Test
  void scaledRowsHeadersAndHitTestingStayAlignedWithoutChangingTimeMapping() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var oldLaf = UIManager.getLookAndFeel();
      final var oldZoom = UIScale.getZoomFactor();
      try {
        FlatLightLaf.setup();
        final var info = mock(SignalInfo.class);
        when(info.getWidth()).thenReturn(8);
        when(info.format(any(Value.class))).thenAnswer(inv -> inv.getArgument(0).toString());
        final var first = new Signal(0, info, Value.createError(BitWidth.create(8)), 100, 0, 0);
        final var second = new Signal(1, info, Value.createUnknown(BitWidth.create(8)), 100, 0, 0);
        final var model = mock(Model.class);
        when(model.getSignalCount()).thenReturn(2);
        when(model.getSignal(0)).thenReturn(first);
        when(model.getSignal(1)).thenReturn(second);
        when(model.getTimeScale()).thenReturn(10L);
        when(model.getEndTime()).thenReturn(100L);
        final var chrono = mock(ChronoPanel.class);
        when(chrono.getModel()).thenReturn(model);
        when(chrono.rowColors(any(SignalInfo.class), anyBoolean())).thenReturn(new Color[] {
            Color.WHITE, Color.GRAY, Color.BLACK, Color.PINK, Color.BLACK, Color.ORANGE, Color.BLACK
        });
        final var left = new LeftPanel(chrono);
        final var right = new RightPanel(chrono, left.getSelectionModel());
        when(chrono.getRightPanel()).thenReturn(right);
        when(chrono.getLeftPanel()).thenReturn(left);
        right.setSignalCursorX(20);
        final var time = right.getCurrentTime();
        final var width = right.getPreferredSize().width;
        for (final var scale : new double[] {1.0, 1.6, 2.0, 1.0}) {
          UiScale.setFactor(scale);
          left.refreshAppearance();
          right.refreshAppearance();
          assertEquals(2 * left.getRowHeight(), right.getPreferredSize().height);
          assertEquals(left.getTableHeader().getPreferredSize().height,
              right.getTimelineHeader().getPreferredSize().height);
          final var metrics = left.getFontMetrics(UiFonts.mono());
          assertTrue(left.getRowHeight() >= metrics.getHeight() + 2 * ChronoPanel.gap());
          assertTrue(right.getTimelineHeader().getPreferredSize().height
              >= metrics.getHeight() + UiScale.scaled(10));
          assertEquals(width, right.getPreferredSize().width);
          assertEquals(time, right.getCurrentTime());
          final var image = new BufferedImage(width, right.getPreferredSize().height,
              BufferedImage.TYPE_INT_RGB);
          final var graphics = image.createGraphics();
          right.setSize(right.getPreferredSize());
          right.paint(graphics);
          graphics.dispose();
          final var event = new MouseEvent(right, MouseEvent.MOUSE_MOVED, 0, 0,
              5, left.getRowHeight() + 1, 0, false);
          for (final var listener : right.getMouseMotionListeners()) listener.mouseMoved(event);
          verify(chrono, atLeastOnce()).changeSpotlight(second);
        }
      } finally {
        try {
          UIManager.setLookAndFeel(oldLaf);
        } catch (javax.swing.UnsupportedLookAndFeelException e) {
          throw new AssertionError(e);
        }
        UIScale.setZoomFactor(oldZoom);
      }
    });
  }

  @Test
  void vectorExportPaintsWaveformsDirectly() {
    final var signalInfo = mock(SignalInfo.class);
    when(signalInfo.getWidth()).thenReturn(1);
    when(signalInfo.format(any(Value.class))).thenAnswer(inv -> inv.getArgument(0).toString());
    final var signal = new Signal(0, signalInfo, Value.FALSE, 10, 0, 0);
    signal.extend(Value.TRUE, 10);

    final var model = mock(Model.class);
    when(model.getSignalCount()).thenReturn(1);
    when(model.getSignal(0)).thenReturn(signal);
    when(model.getStartTime()).thenReturn(0L);
    when(model.getEndTime()).thenReturn(20L);
    when(model.getTimeScale()).thenReturn(10L);

    final var chronoPanel = mock(ChronoPanel.class);
    when(chronoPanel.getModel()).thenReturn(model);
    when(chronoPanel.rowColors(any(SignalInfo.class), anyBoolean()))
        .thenReturn(
            new Color[] {
              Color.LIGHT_GRAY,
              Color.GRAY,
              Color.BLACK,
              Color.PINK,
              Color.BLACK,
              Color.ORANGE,
              Color.BLACK
            });

    final var rightPanel = new RightPanel(chronoPanel, new DefaultListSelectionModel());
    final var exportGraphics = mock(Graphics2D.class);
    final var waveformGraphics = mock(Graphics2D.class);
    final var metricsGraphics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
    when(exportGraphics.create()).thenReturn(waveformGraphics);
    when(waveformGraphics.getFontMetrics()).thenReturn(metricsGraphics.getFontMetrics());

    rightPanel.paintExportImage(exportGraphics);

    verify(waveformGraphics, atLeastOnce()).drawLine(anyInt(), anyInt(), anyInt(), anyInt());
    metricsGraphics.dispose();
  }
}

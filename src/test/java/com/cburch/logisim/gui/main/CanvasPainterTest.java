/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.main;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Simulator;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.gui.canvas.CanvasStyle;
import com.cburch.logisim.proj.Project;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.ImageProducer;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CanvasPainterTest {
  @Test
  void paintContentsReturnsWhenProjectHasNoCurrentCircuit() {
    final var canvas = mock(Canvas.class);
    when(canvas.createImage(any(ImageProducer.class)))
        .thenReturn(new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB));
    when(canvas.getSize()).thenReturn(new Dimension(100, 100));
    when(canvas.getZoomFactor()).thenReturn(1.0);
    final var project = mock(Project.class);
    when(project.getCurrentCircuit()).thenReturn(null);
    final var painter = new CanvasPainter(canvas);
    final var image = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
    final var graphics = image.createGraphics();
    try {
      graphics.setClip(0, 0, 100, 100);
      assertDoesNotThrow(() -> painter.paintContents(graphics, project));
      verify(project, never()).getCircuitState();
    } finally {
      graphics.dispose();
    }
  }

  @Test
  void interfaceFontDoesNotChangeDocumentTextAndDrawingDoesNotMutateCallerFont() {
    final var canvas = mock(Canvas.class);
    final var project = mock(Project.class);
    final var circuit = mock(Circuit.class);
    when(canvas.getSize()).thenReturn(new Dimension(320, 240));
    when(canvas.getZoomFactor()).thenReturn(1.0);
    when(project.getCurrentCircuit()).thenReturn(circuit);
    when(project.getCircuitState()).thenReturn(mock(CircuitState.class));
    when(project.getSelection()).thenReturn(mock(Selection.class));
    when(project.getSimulator()).thenReturn(mock(Simulator.class));
    final var draws = new AtomicInteger();
    doAnswer(invocation -> {
      final ComponentDrawContext context = invocation.getArgument(0);
      assertEquals(CanvasStyle.documentFont(), context.getGraphics().getFont());
      assertEquals(canvas.getZoomFactor(),
          ((Graphics2D) context.getGraphics()).getTransform().getScaleX());
      draws.incrementAndGet();
      return null;
    }).when(circuit).draw(any(), any());
    final var painter = new CanvasPainter(canvas);
    final var image = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
    final var graphics = image.createGraphics();
    try {
      graphics.setClip(0, 0, 320, 240);
      for (final var zoom : new double[] {0.5, 1.0, 2.0}) {
        when(canvas.getZoomFactor()).thenReturn(zoom);
        for (final var size : new int[] {13, 21, 26, 32}) {
          final var interfaceFont = new Font(Font.DIALOG, Font.PLAIN, size);
          graphics.setFont(interfaceFont);
          painter.paintContents(graphics, project);
          assertEquals(interfaceFont, graphics.getFont());
        }
      }
      assertEquals(12, draws.get());
    } finally {
      graphics.dispose();
    }
  }
}

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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.gui.canvas.CanvasStyle;
import com.cburch.logisim.gui.generic.TikZWriter;
import com.cburch.logisim.proj.Project;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class DocumentOutputFontTest {
  @Test
  void printMeasuresAndDrawsWithDocumentFontWithoutChangingPrinterFont() {
    final var project = mock(Project.class);
    final var frame = mock(Frame.class);
    final var circuit = mock(Circuit.class);
    when(project.getFrame()).thenReturn(frame);
    when(circuit.getName()).thenReturn("fixture");
    when(circuit.getBounds(any(Graphics.class))).thenAnswer(invocation -> {
      final Graphics graphics = invocation.getArgument(0);
      assertEquals(CanvasStyle.documentFont(), graphics.getFont());
      return Bounds.create(0, 0, 100, 100);
    });
    final var draws = new AtomicInteger();
    doAnswer(invocation -> {
      final ComponentDrawContext context = invocation.getArgument(0);
      assertEquals(CanvasStyle.documentFont(), context.getGraphics().getFont());
      draws.incrementAndGet();
      return null;
    }).when(circuit).draw(any(), any());
    final var image = new BufferedImage(800, 1000, BufferedImage.TYPE_INT_RGB);
    final var graphics = image.createGraphics();
    try {
      graphics.setClip(0, 0, 800, 1000);
      final var printerFont = new Font(Font.SERIF, Font.BOLD, 36);
      graphics.setFont(printerFont);
      final var printable = new Print.MyPrintable(
          project, List.of(circuit), "%n", false, true);
      assertEquals(Printable.PAGE_EXISTS, printable.print(graphics, new PageFormat(), 0));
      assertEquals(printerFont, graphics.getFont());
      assertEquals(1, draws.get());
    } finally {
      graphics.dispose();
    }
  }

  @Test
  void exportMeasuresDocumentFontAndVectorWriterUsesSameMappedFamily() {
    final var circuit = mock(Circuit.class);
    when(circuit.getBounds(any(Graphics.class))).thenAnswer(invocation -> {
      final Graphics graphics = invocation.getArgument(0);
      assertEquals(CanvasStyle.documentFont(), graphics.getFont());
      return Bounds.create(0, 0, 100, 100);
    });
    assertEquals(Bounds.create(-5, -5, 110, 110), ExportImage.exportBounds(circuit));
    final var vector = new TikZWriter();
    vector.setFont(CanvasStyle.documentFont());
    assertEquals(Font.SANS_SERIF, vector.getFont().getFamily());
    assertEquals(CanvasStyle.documentFont(), vector.getFont());
  }
}

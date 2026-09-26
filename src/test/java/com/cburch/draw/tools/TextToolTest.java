/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.draw.tools;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cburch.draw.canvas.Canvas;
import com.cburch.draw.canvas.Selection;
import com.cburch.draw.model.CanvasModel;
import com.cburch.draw.undo.UndoAction;
import java.awt.Component;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class TextToolTest {

  @Test
  void clickingEmptySpaceWhileEditingEndsTheEditWithoutStartingAnotherText() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var canvas = mock(Canvas.class);
      final var model = mock(CanvasModel.class);
      when(canvas.getModel()).thenReturn(model);
      when(canvas.getSelection()).thenReturn(mock(Selection.class));
      when(canvas.getZoomFactor()).thenReturn(1.0);
      when(model.getObjectsFromTop()).thenReturn(List.of());
      final var tool = new TextTool(new DrawingAttributeSet());

      tool.mousePressed(canvas, press(40, 40));
      verify(canvas, times(1)).add(any(Component.class));

      tool.mousePressed(canvas, press(200, 200));
      verify(canvas, times(1)).add(any(Component.class));
      verify(canvas, never()).doAction(any(UndoAction.class));

      tool.mousePressed(canvas, press(200, 200));
      verify(canvas, times(2)).add(any(Component.class));
    });
  }

  private static MouseEvent press(int x, int y) {
    return new MouseEvent(
        new JPanel(), MouseEvent.MOUSE_PRESSED, 0, 0, x, y, 1, false, MouseEvent.BUTTON1);
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.tools;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.gui.main.Canvas;
import com.cburch.logisim.gui.main.Selection;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Action;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Clock;
import java.awt.event.KeyEvent;
import java.util.Set;
import javax.swing.JPanel;
import org.junit.jupiter.api.Test;

class EditToolRefaceTest {

  @Test
  void refacingToTheCurrentDirectionAddsNoUndoStepButStillConsumesTheKey() {
    final Component gate =
        Clock.FACTORY.createComponent(
            Location.create(100, 100, true), Clock.FACTORY.createAttributeSet());
    final var current = gate.getAttributeSet().getValue(StdAttr.FACING);
    final var canvas = mock(Canvas.class);
    final var selection = mock(Selection.class);
    final var project = mock(Project.class);
    when(canvas.getSelection()).thenReturn(selection);
    when(canvas.getProject()).thenReturn(project);
    when(selection.getComponents()).thenReturn(Set.of(gate));
    final var tool = new EditTool(new SelectTool(), new WiringTool());

    final var same = key();
    tool.attemptReface(canvas, current, same);
    verify(project, never()).doAction(any(Action.class));
    assertTrue(same.isConsumed(), "the arrow key must not fall through to scrolling");

    final var other = current == Direction.NORTH ? Direction.SOUTH : Direction.NORTH;
    tool.attemptReface(canvas, other, key());
    verify(project).doAction(any(Action.class));
  }

  private static KeyEvent key() {
    return new KeyEvent(
        new JPanel(), KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_UP, KeyEvent.CHAR_UNDEFINED);
  }
}

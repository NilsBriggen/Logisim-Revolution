/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.appear;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.cburch.draw.actions.ModelAddAction;
import com.cburch.draw.canvas.CanvasTool;
import com.cburch.draw.shapes.Rectangle;
import com.cburch.draw.tools.DrawingAttributeSet;
import com.cburch.draw.tools.RectangleTool;
import com.cburch.draw.tools.SelectTool;
import com.cburch.draw.tools.TextTool;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.EditLockAction;
import com.cburch.logisim.circuit.EditLockedException;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.StringUtil;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AppearanceEditLockTest {
  private Project project;
  private Circuit circuit;
  private AppearanceCanvas canvas;
  private Rectangle rectangle;

  @BeforeEach
  void setUp() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var file = LogisimFile.createNew(new Loader(null), null);
      file.retireAutosaveThread();
      project = new Project(file);
      circuit = file.getMainCircuit();
      circuit.setProject(project);
      project.setCurrentCircuit(circuit);
      canvas = new AppearanceCanvas(new SelectTool());
      canvas.setCircuit(project, project.getCircuitState());
      rectangle = new Rectangle(20, 20, 30, 30);
      canvas.getModel().addObjects(0, List.of(rectangle));
    });
  }

  @AfterEach
  void tearDown() {
    project.getSimulator().shutDown();
  }

  @Test
  void lockRefusesMouseAndKeyInputBeforeCallingTheTool() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var tool = mock(CanvasTool.class);
      canvas.setTool(tool);
      circuit.setEditLocked(true);
      final var press = mouse(MouseEvent.MOUSE_PRESSED, 100, 100);
      canvas.processMouseEvent(press);
      canvas.processMouseMotionEvent(mouse(MouseEvent.MOUSE_DRAGGED, 140, 140));
      canvas.processMouseEvent(mouse(MouseEvent.MOUSE_RELEASED, 140, 140));
      final var key = new KeyEvent(canvas, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_DELETE, '\0');
      for (final var listener : canvas.getKeyListeners()) listener.keyPressed(key);
      assertTrue(press.isConsumed());
      assertTrue(key.isConsumed());
      verify(tool, never()).mousePressed(any(), any());
      verify(tool, never()).mouseDragged(any(), any());
      verify(tool, never()).mouseReleased(any(), any());
      verify(tool, never()).keyPressed(any(), any());
      assertFalse(project.isFileDirty());

      circuit.setEditLocked(false);
      canvas.processMouseEvent(mouse(MouseEvent.MOUSE_PRESSED, 100, 100));
      verify(tool).mousePressed(any(), any());
    });
  }

  @Test
  void lockingCancelsAnAlreadyOpenTextEditorWithoutCommittingIt() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      canvas.setTool(new TextTool(new DrawingAttributeSet()));
      canvas.processMouseEvent(mouse(MouseEvent.MOUSE_PRESSED, 100, 100));
      assertEquals(1, canvas.getComponentCount());
      ((javax.swing.JTextField) canvas.getComponent(0)).setText("pending text");
      final var before = new ArrayList<>(canvas.getModel().getObjectsFromBottom());
      project.doAction(EditLockAction.setCircuitLocked(circuit, true));
      assertEquals(0, canvas.getComponentCount());
      assertEquals(before, canvas.getModel().getObjectsFromBottom());
      project.undoAction();
      assertFalse(circuit.isEditLocked());
      assertFalse(project.isFileDirty());
      assertEquals(before, canvas.getModel().getObjectsFromBottom());
    });
  }

  @Test
  void lockingCancelsAnInProgressDrawingEvenIfUnlockedBeforeRelease() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      canvas.setTool(new RectangleTool(new DrawingAttributeSet()));
      final var before = new ArrayList<>(canvas.getModel().getObjectsFromBottom());
      canvas.processMouseEvent(mouse(MouseEvent.MOUSE_PRESSED, 100, 100));
      canvas.processMouseMotionEvent(mouse(MouseEvent.MOUSE_DRAGGED, 140, 140));
      circuit.setEditLocked(true);
      circuit.setEditLocked(false);
      canvas.processMouseEvent(mouse(MouseEvent.MOUSE_RELEASED, 140, 140));
      assertEquals(before, canvas.getModel().getObjectsFromBottom());
      assertFalse(project.isFileDirty());
    });
  }

  @Test
  void preparedSelectionAndCutActionsCannotBypassALaterLock() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      canvas.getSelection().setSelected(rectangle, true);
      final var selection = new SelectionAction(canvas, StringUtil.constantGetter("delete"),
          List.of(rectangle), null, List.of(), null, null);
      final var cut = ClipboardActions.cut(canvas);
      circuit.setEditLocked(true);
      canvas.getSelection().setSelected(rectangle, true);
      final var clipboard = Clipboard.get();
      final var history = project.getLastAction();
      project.doAction(selection);
      project.doAction(cut);
      assertTrue(canvas.getModel().getObjectsFromBottom().contains(rectangle));
      assertTrue(canvas.getSelection().isSelected(rectangle));
      assertSame(clipboard, Clipboard.get());
      assertSame(history, project.getLastAction());
      assertFalse(project.isFileDirty());
    });
  }

  @Test
  void handlerPreflightAndCanvasActionsPreserveHistoryAndSelection() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      final var handler = new AppearanceEditHandler(canvas);
      circuit.setEditLocked(true);
      canvas.getSelection().setSelected(rectangle, true);
      final var before = new ArrayList<>(canvas.getModel().getObjectsFromBottom());
      final var history = project.getLastAction();
      handler.delete();
      handler.duplicate();
      handler.cut();
      handler.paste();
      canvas.doAction(new ModelAddAction(canvas.getModel(), new Rectangle(80, 80, 20, 20)));
      assertEquals(before, canvas.getModel().getObjectsFromBottom());
      assertTrue(canvas.getSelection().isSelected(rectangle));
      assertSame(history, project.getLastAction());
      assertFalse(project.isFileDirty());
      // Copying is still available on a locked appearance.
      project.doAction(ClipboardActions.copy(canvas));
      assertEquals(1, Clipboard.get().getElements().size());
    });
  }

  @Test
  void lockedToolbarCommandsRefuseBeforeOpeningDialogs() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      circuit.setEditLocked(true);
      final var before = new ArrayList<>(canvas.getModel().getObjectsFromBottom());
      new ResetAppearanceTool(canvas, true).clicked();
      new ResetAppearanceTool(canvas, false).clicked();
      new ShowStateTool(null, canvas, new DrawingAttributeSet()).clicked();
      assertEquals(before, canvas.getModel().getObjectsFromBottom());
      assertFalse(project.isFileDirty());
    });
  }

  @Test
  void appearanceResetChecksLockAtMutationBoundary() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      circuit.setEditLocked(true);
      final var appearance = circuit.getAppearance();
      final var before = new ArrayList<>(appearance.getCustomObjectsFromBottom());
      assertThrows(EditLockedException.class, appearance::resetDefaultCustomAppearance);
      assertThrows(EditLockedException.class, appearance::loadDefaultLogisimAppearance);
      assertEquals(before, appearance.getCustomObjectsFromBottom());
    });
  }

  @Test
  void lockedAppearanceAllowsMouseSelectionCopyAndEscapeWithoutMovingShapes() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      circuit.setEditLocked(true);
      final var bounds = rectangle.getBounds();
      canvas.processMouseEvent(mouse(MouseEvent.MOUSE_PRESSED, 20, 35));
      canvas.processMouseMotionEvent(mouse(MouseEvent.MOUSE_DRAGGED, 80, 90));
      canvas.processMouseEvent(mouse(MouseEvent.MOUSE_RELEASED, 80, 90));
      assertTrue(canvas.getSelection().isSelected(rectangle));
      assertEquals(bounds, rectangle.getBounds());
      // Selection handles are inspectable, but dragging them must not resize the shape either.
      canvas.processMouseEvent(mouse(MouseEvent.MOUSE_PRESSED, 20, 20));
      canvas.processMouseMotionEvent(mouse(MouseEvent.MOUSE_DRAGGED, 10, 10));
      canvas.processMouseEvent(mouse(MouseEvent.MOUSE_RELEASED, 10, 10));
      assertEquals(bounds, rectangle.getBounds());
      final var delete = new KeyEvent(canvas, KeyEvent.KEY_TYPED, 0, 0, 0, '\u007F');
      for (final var listener : canvas.getKeyListeners()) listener.keyTyped(delete);
      assertTrue(canvas.getSelection().isSelected(rectangle));
      assertTrue(canvas.getModel().getObjectsFromBottom().contains(rectangle));
      final var handler = new AppearanceEditHandler(canvas);
      handler.copy();
      assertEquals(1, Clipboard.get().getElements().size());
      assertFalse(project.isFileDirty());
      final var escape = new KeyEvent(canvas, KeyEvent.KEY_TYPED, 0, 0, 0, '\u001b');
      for (final var listener : canvas.getKeyListeners()) listener.keyTyped(escape);
      assertTrue(canvas.getSelection().isEmpty());
    });
  }

  @Test
  void lockedAppearanceAllowsRectangleSelection() throws Exception {
    SwingUtilities.invokeAndWait(() -> {
      circuit.setEditLocked(true);
      canvas.processMouseEvent(mouse(MouseEvent.MOUSE_PRESSED, 10, 10));
      canvas.processMouseMotionEvent(mouse(MouseEvent.MOUSE_DRAGGED, 60, 60));
      canvas.processMouseEvent(mouse(MouseEvent.MOUSE_RELEASED, 60, 60));
      assertTrue(canvas.getSelection().isSelected(rectangle));
      assertFalse(project.isFileDirty());
    });
  }

  private MouseEvent mouse(int id, int x, int y) {
    return new MouseEvent(canvas, id, 0, MouseEvent.BUTTON1_DOWN_MASK, x, y, 1, false,
        id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1);
  }
}

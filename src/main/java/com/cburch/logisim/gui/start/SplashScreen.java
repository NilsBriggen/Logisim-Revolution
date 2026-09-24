/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.start;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.generated.BuildInfo;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.MouseInfo;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;

/** Lightweight loading window with honest startup stages and no historical timing weights. */
public class SplashScreen extends JWindow {
  public static final int LIBRARIES = 0;
  public static final int TEMPLATE_CREATE = 1;
  public static final int TEMPLATE_OPEN = 2;
  public static final int TEMPLATE_LOAD = 3;
  public static final int TEMPLATE_CLOSE = 4;
  public static final int GUI_INIT = 5;
  public static final int FILE_CREATE = 6;
  public static final int FILE_LOAD = 7;
  public static final int PROJECT_CREATE = 8;
  public static final int FRAME_CREATE = 9;
  private static final long serialVersionUID = 1L;

  private static final String[] STAGES = {
      "progressLibraries", "progressTemplateCreate", "progressTemplateOpen",
      "progressTemplateLoad", "progressTemplateClose", "progressGuiInitialize",
      "progressFileCreate", "progressFileLoad", "progressProjectCreate",
      "progressFrameCreate"
  };

  private final AtomicBoolean closed = new AtomicBoolean();
  private final SplashPanel panel = new SplashPanel();

  public SplashScreen() {
    setName(BuildInfo.displayName);
    setContentPane(panel);
    setBackground(panel.getBackground());
  }

  /** Ignores callbacks that were queued before the window finished closing. */
  public void close() {
    if (!closed.compareAndSet(false, true)) return;
    onEdt(() -> {
      super.setVisible(false);
      dispose();
    });
  }

  /** Existing startup callers report an actual stage, not a guessed percentage. */
  public void setProgress(int markerId) {
    if (closed.get() || markerId < 0 || markerId >= STAGES.length) return;
    final var key = STAGES[markerId];
    onEdt(() -> {
      if (!closed.get()) panel.setStage(S.get(key));
    });
  }

  @Override
  public void setVisible(boolean value) {
    if (!SwingUtilities.isEventDispatchThread()) {
      try {
        SwingUtilities.invokeAndWait(() -> setVisible(value));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted opening splash", e);
      } catch (InvocationTargetException e) {
        throw new IllegalStateException("Could not open splash", e.getCause());
      }
      return;
    }
    if (value && !closed.get()) {
      pack();
      centerOnActiveMonitor();
      super.setVisible(true);
    } else if (!value) {
      super.setVisible(false);
    }
  }

  private void centerOnActiveMonitor() {
    final var pointer = MouseInfo.getPointerInfo();
    final GraphicsConfiguration config = pointer == null
        ? getGraphicsConfiguration() : pointer.getDevice().getDefaultConfiguration();
    final Rectangle available = new Rectangle(config.getBounds());
    final Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(config);
    available.x += insets.left;
    available.y += insets.top;
    available.width -= insets.left + insets.right;
    available.height -= insets.top + insets.bottom;
    setSize(Math.min(getWidth(), available.width), Math.min(getHeight(), available.height));
    setLocation(
        available.x + (available.width - getWidth()) / 2,
        available.y + (available.height - getHeight()) / 2);
  }

  private static void onEdt(Runnable action) {
    if (SwingUtilities.isEventDispatchThread()) action.run();
    else SwingUtilities.invokeLater(action);
  }
}

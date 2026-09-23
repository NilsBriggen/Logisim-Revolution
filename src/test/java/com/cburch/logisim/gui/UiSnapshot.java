/*
 * Logisim-evolution - digital logic design tool and simulator
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui;

import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.ProjectActions;
import com.cburch.logisim.util.UiScale;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/**
 * Renders the main window to a PNG so its appearance can be reviewed without a person watching a
 * screen.
 *
 * <p>This is a development tool, not part of the application: it lives in the test sources and
 * never ships. It exists because the window manager cannot reliably be asked to bring a particular
 * window to the front for a screenshot, and because comparing two PNGs is a better way to judge a
 * change to the interface than describing it.
 *
 * <p>Run it with {@code ./gradlew uiSnapshot -Ptheme=dark -Pout=build/snapshot.png}.
 */
public final class UiSnapshot {

  private static final int DEFAULT_WIDTH = 1440;
  private static final int DEFAULT_HEIGHT = 900;

  /** How long to wait for the project window to be built on the event thread. */
  private static final long FRAME_TIMEOUT_MS = 30_000;

  private UiSnapshot() {
    throw new UnsupportedOperationException("Utility class, do not instantiate.");
  }

  public static void main(String[] args) throws Exception {
    final var mode = Theme.Mode.fromKey(System.getProperty("snapshot.theme", "dark"));
    final var output = new File(System.getProperty("snapshot.out", "build/ui-snapshot.png"));
    final var width = Integer.getInteger("snapshot.width", DEFAULT_WIDTH);
    final var height = Integer.getInteger("snapshot.height", DEFAULT_HEIGHT);

    // Judge the design at its reference size: this machine's own interface scale would otherwise
    // decide how large everything in the picture looks.
    final var scale = Double.parseDouble(System.getProperty("snapshot.scale", "1.0"));
    AppPreferences.SCALE_FACTOR.set(scale);
    final var scaleDeadline = System.currentTimeMillis() + 5_000;
    while (Math.abs(UiScale.factor() - scale) > 0.001
        && System.currentTimeMillis() < scaleDeadline) {
      Thread.sleep(25);
    }

    AppPreferences.THEME_MODE.set(mode.key());
    Theme.install();
    AppPreferences.applyThemeColors();

    final var project = ProjectActions.doNew((com.cburch.logisim.gui.start.SplashScreen) null);
    final var deadline = System.currentTimeMillis() + FRAME_TIMEOUT_MS;
    while (project.getFrame() == null && System.currentTimeMillis() < deadline) {
      Thread.sleep(50);
    }
    final var frame = project.getFrame();
    if (frame == null) throw new IllegalStateException("the project window was never created");

    SwingUtilities.invokeAndWait(
        () -> {
          // Makes the frame displayable and lays it out without ever putting it on screen, so the
          // capture does not depend on the desktop, focus, or which window happens to be on top.
          frame.pack();
          frame.setSize(width, height);
          frame.getContentPane().setSize(width, height);
          frame.invalidate();
          frame.validate();
          frame.getContentPane().doLayout();
        });

    // The picture is the size of the window's contents, not of the window: the frame's own border
    // and title bar are drawn by the desktop and are not part of what is being reviewed.
    final var size = frame.getContentPane().getSize();
    final var image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
    // Two passes: several panels only paint their contents once they have been laid out at their
    // final size, which the first pass is what establishes.
    for (var pass = 0; pass < 2; pass++) {
      SwingUtilities.invokeAndWait(
          () -> {
            final var graphics = image.createGraphics();
            try {
              frame.getContentPane().printAll(graphics);
            } finally {
              graphics.dispose();
            }
          });
    }

    final var parent = output.getAbsoluteFile().getParentFile();
    if (parent != null) parent.mkdirs();
    ImageIO.write(image, "png", output);
    System.out.println(
        String.format(
            Locale.ROOT,
            "wrote %s (%dx%d, %s theme)",
            output,
            size.width,
            size.height,
            mode.key()));
    System.exit(0);
  }
}

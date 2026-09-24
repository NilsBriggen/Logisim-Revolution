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

  /** Renders any window's contents, for reviewing a dialog rather than the main window. */
  private static void captureWindow(javax.swing.JFrame window, File output, Theme.Mode mode)
      throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          window.pack();
          window.validate();
          window.getContentPane().doLayout();
        });
    // The contents, not the frame: an unshown frame paints nothing of its own.
    final var target = window.getContentPane();
    final var size = target.getSize();
    final var image = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
    for (var pass = 0; pass < 2; pass++) {
      SwingUtilities.invokeAndWait(
          () -> {
            final var graphics = image.createGraphics();
            try {
              target.printAll(graphics);
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
            Locale.ROOT, "wrote %s (%dx%d, %s theme)", output, size.width, size.height, mode.key()));
  }

  /**
   * Renders a circuit the way exporting an image does, in the print palette.
   *
   * <p>This is the check that matters most for a canvas change: the theme's colours are chosen
   * against the window's background, and a dark one leaking into an exported figure makes it
   * unreadable on a white page.
   */
  private static void capturePrintView(
      com.cburch.logisim.proj.Project project, File output) throws Exception {
    final var canvas = project.getFrame().getCanvas();
    final var circuit = project.getCurrentCircuit();
    final var bounds = circuit.getBounds(canvas.getGraphics()).expand(10);
    final var image =
        new BufferedImage(bounds.getWidth(), bounds.getHeight(), BufferedImage.TYPE_INT_RGB);
    final var graphics = image.createGraphics();
    try {
      graphics.setColor(java.awt.Color.WHITE);
      graphics.fillRect(0, 0, bounds.getWidth(), bounds.getHeight());
      graphics.translate(-bounds.getX(), -bounds.getY());
      final var context =
          new com.cburch.logisim.comp.ComponentDrawContext(
              canvas,
              circuit,
              project.getCircuitState(circuit),
              graphics,
              graphics,
              true);
      circuit.draw(context, null);
    } finally {
      graphics.dispose();
    }
    final var parent = output.getAbsoluteFile().getParentFile();
    if (parent != null) parent.mkdirs();
    ImageIO.write(image, "png", output);
    System.out.println(
        String.format(
            Locale.ROOT,
            "wrote %s (%dx%d, print view)",
            output,
            bounds.getWidth(),
            bounds.getHeight()));
  }

  public static void main(String[] args) throws Exception {
    final var mode = Theme.Mode.fromKey(System.getProperty("snapshot.theme", "dark"));
    final var output = new File(System.getProperty("snapshot.out", "build/ui-snapshot.png"));
    final var width = Integer.getInteger("snapshot.width", DEFAULT_WIDTH);
    final var height = Integer.getInteger("snapshot.height", DEFAULT_HEIGHT);

    // Judge the design at its reference size: this machine's own interface scale would otherwise
    // decide how large everything in the picture looks.
    //
    // This writes to the developer's own preference store, so the previous value is put back when
    // the picture has been taken. Without that, every snapshot left the interface scale pinned at
    // the reference 1.0 and the application came up uncomfortably small afterwards.
    final var scale = Double.parseDouble(System.getProperty("snapshot.scale", "1.0"));
    final var storedScale = AppPreferences.getPrefs().getDouble("Scale", Double.NaN);
    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  if (Double.isNaN(storedScale)) {
                    AppPreferences.getPrefs().remove("Scale");
                  } else {
                    AppPreferences.getPrefs().putDouble("Scale", storedScale);
                  }
                  try {
                    AppPreferences.getPrefs().flush();
                  } catch (java.util.prefs.BackingStoreException ignored) {
                    // Nothing useful to do while the process is going away.
                  }
                }));
    AppPreferences.SCALE_FACTOR.set(scale);
    final var scaleDeadline = System.currentTimeMillis() + 5_000;
    while (Math.abs(UiScale.factor() - scale) > 0.001
        && System.currentTimeMillis() < scaleDeadline) {
      Thread.sleep(25);
    }

    AppPreferences.THEME_MODE.set(mode.key());
    Theme.install();
    AppPreferences.applyThemeColors();

    // A named file lets a real circuit be reviewed, which is the only way to judge canvas work.
    final var circuitFile = System.getProperty("snapshot.file", "");
    final var project =
        circuitFile.isEmpty()
            ? ProjectActions.doNew(
                (com.cburch.logisim.gui.start.SplashScreen) null,
                Boolean.getBoolean("snapshot.welcome"))
            : ProjectActions.doOpen(null, null, new File(circuitFile));
    final var deadline = System.currentTimeMillis() + FRAME_TIMEOUT_MS;
    while (project.getFrame() == null && System.currentTimeMillis() < deadline) {
      Thread.sleep(50);
    }
    final var frame = project.getFrame();
    if (frame == null) throw new IllegalStateException("the project window was never created");

    final var selectionName = System.getProperty("snapshot.select", "");
    if (!selectionName.isBlank()) {
      final var component = project.getCurrentCircuit().getComponents().stream()
          .filter(item -> selectionName.equals(item.getFactory().getName()))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException(
              "No component named " + selectionName + " in the current circuit"));
      SwingUtilities.invokeAndWait(() -> project.getSelection().add(component));
    }

    final var canvasZoom = Double.parseDouble(System.getProperty("snapshot.canvasZoom", "0"));
    if (canvasZoom > 0) {
      SwingUtilities.invokeAndWait(() -> frame.getZoomModel().setZoomFactor(canvasZoom));
    }

    if (Boolean.getBoolean("snapshot.print")) {
      capturePrintView(project, output);
      System.exit(0);
    }

    if (Boolean.getBoolean("snapshot.preferences")) {
      final var preferences = com.cburch.logisim.gui.prefs.PreferencesFrame.buildForSnapshot();
      final var page = Integer.getInteger("snapshot.page", -1);
      if (page >= 0) preferences.showPageForSnapshot(page);
      captureWindow(preferences, output, mode);
      System.exit(0);
    }

    final var sideView = System.getProperty("snapshot.side", "");
    if (!sideView.isEmpty()) {
      SwingUtilities.invokeAndWait(() -> frame.showSideView(sideView));
    }

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

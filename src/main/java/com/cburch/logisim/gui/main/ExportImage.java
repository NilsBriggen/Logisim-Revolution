/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.main;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.gui.canvas.CanvasStyle;
import com.cburch.logisim.gui.generic.SettingsForm;
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.gui.generic.TikZWriter;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.StringGetter;
import com.cburch.logisim.util.UniquelyNamedThread;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ItemListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSlider;
import javax.swing.ProgressMonitor;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.filechooser.FileFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExportImage {

  public static final int FORMAT_GIF = 0;
  public static final int FORMAT_PNG = 1;
  public static final int FORMAT_JPG = 2;
  public static final int FORMAT_TIKZ = 3;
  public static final int FORMAT_SVG = 4;
  public static final int FORMAT_WAVEDROM = 5;
  static final Logger logger = LoggerFactory.getLogger(ExportImage.class);

  private static final int SLIDER_DIVISIONS = 6;
  private static final int BORDER_SIZE = 5;

  private ExportImage() {}

  public static ImageFileFilter getFilter(int fmt) {
    switch (fmt) {
      case FORMAT_GIF:
        return new ImageFileFilter(fmt, S.getter("exportGifFilter"), new String[] {"gif"});
      case FORMAT_PNG:
        return new ImageFileFilter(fmt, S.getter("exportPngFilter"), new String[] {"png"});
      case FORMAT_JPG:
        return new ImageFileFilter(
            fmt,
            S.getter("exportJpgFilter"),
            new String[] {"jpg", "jpeg", "jpe", "jfi", "jfif", "jfi"});
      case FORMAT_TIKZ:
        return new ImageFileFilter(fmt, S.getter("exportTikZFilter"), new String[] {"tex"});
      case FORMAT_SVG:
        return new ImageFileFilter(fmt, S.getter("exportSvgFilter"), new String[] {"svg"});
      case FORMAT_WAVEDROM:
        return new ImageFileFilter(fmt, S.getter("exportWaveDromFilter"), new String[] {"json"});
      default:
        logger.error("Unexpected image format; aborted!");
        return null;
    }
  }

  public static void doExport(Project proj) {
    // First display circuit/parameter selection dialog
    final var frame = proj.getFrame();
    final var list = new CircuitJList(proj, true);
    if (list.getModel().getSize() == 0) {
      OptionPane.showMessageDialog(
          proj.getFrame(),
          S.get("exportEmptyCircuitsMessage"),
          S.get("exportEmptyCircuitsTitle"),
          OptionPane.YES_NO_OPTION);
      return;
    }
    final var options = new OptionsPanel(proj, list);
    int action =
        OptionPane.showConfirmDialog(
            frame,
            options,
            S.get("exportImageSelect"),
            OptionPane.OK_CANCEL_OPTION,
            OptionPane.PLAIN_MESSAGE);
    if (action != OptionPane.OK_OPTION) return;
    final var circuits = list.getSelectedCircuits();
    final var scale = options.getScale();
    final var printerView = options.getPrinterView();
    if (circuits.isEmpty()) return;

    final var fmt = options.getImageFormat();
    final var filter = getFilter(fmt);
    if (filter == null) return;

    // Then display file chooser
    final var loader = proj.getLogisimFile().getLoader();
    final var chooser = loader.createChooser();
    chooser.setAcceptAllFileFilterUsed(false);
    if (circuits.size() > 1) {
      chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
      chooser.setDialogTitle(S.get("exportImageDirectorySelect"));
    } else {
      chooser.setFileFilter(filter);
      chooser.setDialogTitle(S.get("exportImageFileSelect"));
      chooser.setSelectedFile(new File(circuits.get(0).getName() + filter.extensions[0]));
    }
    final var returnVal = chooser.showDialog(frame, S.get("exportImageButton"));
    if (returnVal != JFileChooser.APPROVE_OPTION) return;

    // Determine whether destination is valid
    final var dest = chooser.getSelectedFile();
    chooser.setCurrentDirectory(dest.isDirectory() ? dest : dest.getParentFile());
    if (dest.exists()) {
      if (!dest.isDirectory()) {
        final var confirm =
            OptionPane.showConfirmDialog(
                proj.getFrame(),
                S.get("confirmOverwriteMessage"),
                S.get("confirmOverwriteTitle"),
                OptionPane.YES_NO_OPTION);
        if (confirm != OptionPane.YES_OPTION) return;
      }
    } else {
      if (circuits.size() > 1) {
        final var created = dest.mkdir();
        if (!created) {
          OptionPane.showMessageDialog(
              proj.getFrame(),
              S.get("exportNewDirectoryErrorMessage"),
              S.get("exportNewDirectoryErrorTitle"),
              OptionPane.YES_NO_OPTION);
          return;
        }
      }
    }

    // Create the progress monitor
    final var monitor = new ProgressMonitor(frame, S.get("exportImageProgress"), null, 0, 10000);
    monitor.setMillisToDecideToPopup(100);
    monitor.setMillisToPopup(200);
    monitor.setProgress(0);

    // And start a thread to actually perform the operation
    // (This is run in a thread so that Swing will update the
    // monitor.)
    new ExportThread(
            frame,
            frame.getCanvas(),
            dest,
            filter,
            circuits,
            scale,
            printerView,
            frame.getCanvas().getBackground(),
            monitor)
        .start();
  }

  /**
   * Builds the export options, as the Export Image dialog shows them, for reviewing the dialog
   * without showing it.
   *
   * @param allCircuits whether to select every circuit, so that the preview can step through them
   * @param printerView whether printer view starts checked
   */
  public static JComponent buildForSnapshot(
      Project proj, boolean allCircuits, boolean printerView) {
    final var list = new CircuitJList(proj, true);
    if (allCircuits) list.setSelectionInterval(0, list.getModel().getSize() - 1);
    final var options = new OptionsPanel(proj, list);
    options.printerView.setSelected(printerView);
    return options;
  }

  /**
   * Returns the state whose values an export of {@code circuit} shows. The circuit on the canvas
   * is exported as the user sees it. Any other circuit has only a background state that the
   * simulator never propagates, so its values are shown from a settled temporary copy instead of
   * the never-simulated defaults. A returned state other than {@code proj.getCircuitState(circuit)}
   * is temporary and owned by the calling thread; the caller detaches it when done.
   */
  static CircuitState stateForExport(Project proj, Circuit circuit, boolean printerView) {
    final var state = proj.getCircuitState(circuit);
    if (printerView || state == proj.getCircuitState()) return state;
    final var temp = state.cloneAsNewRootState(Thread.currentThread());
    temp.getPropagator().propagate();
    return temp;
  }

  static void paintExportBackground(
      Graphics g,
      int width,
      int height,
      int format,
      boolean printerView,
      Color canvasBackground) {
    final var vectorFormat = format == FORMAT_TIKZ || format == FORMAT_SVG;
    if (!printerView || !vectorFormat) {
      g.setColor(printerView ? Color.WHITE : canvasBackground);
      g.fillRect(0, 0, width, height);
    }
    g.setColor(Color.BLACK);
  }

  /** Measures in document units without borrowing an on-screen graphics object from a worker. */
  static Bounds exportBounds(Circuit circuit) {
    final var metrics = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
    try {
      metrics.setFont(CanvasStyle.documentFont());
      return circuit.getBounds(metrics).expand(BORDER_SIZE);
    } finally {
      metrics.dispose();
    }
  }

  /**
   * The size, in pixels, of the image an export of a circuit with bounds {@code bds} produces at
   * {@code scale}. The export and its preview both size the image from here.
   */
  static Dimension imageSize(Bounds bds, double scale) {
    return new Dimension(
        Math.max(1, (int) Math.round(bds.getWidth() * scale)),
        Math.max(1, (int) Math.round(bds.getHeight() * scale)));
  }

  /** The scale factor a position of the dialog's scale slider stands for: a power of two. */
  static double scaleForSlider(int value) {
    return Math.pow(2.0, (double) value / SLIDER_DIVISIONS);
  }

  /**
   * The scale a preview draws at so that the whole image fits in {@code maxWidth} by {@code
   * maxHeight} pixels, never larger than the export itself.
   */
  static double previewScale(Bounds bds, double scale, int maxWidth, int maxHeight) {
    final var fit =
        Math.min(
            (double) maxWidth / Math.max(1, bds.getWidth()),
            (double) maxHeight / Math.max(1, bds.getHeight()));
    return Math.min(scale, fit);
  }

  /**
   * Draws {@code circuit} as an export does: the background, then the circuit at {@code scale}
   * with the bounds' corner at the origin. Used by the export itself and by its preview, so that
   * the preview shows exactly what is written.
   *
   * @param g a graphics object created from {@code base}, which receives the drawing
   * @param dest the canvas the circuit is shown on, or null
   * @return false if {@code g} cannot be scaled, which no export can then use
   */
  static boolean paintExport(
      Graphics base,
      Graphics g,
      java.awt.Component dest,
      Project proj,
      Circuit circuit,
      Bounds bds,
      double scale,
      int format,
      boolean printerView,
      Color canvasBackground) {
    final var size = imageSize(bds, scale);
    base.setFont(CanvasStyle.documentFont());
    g.setFont(CanvasStyle.documentFont());
    paintExportBackground(g, size.width, size.height, format, printerView, canvasBackground);
    if (!(g instanceof Graphics2D g2d)) return false;
    if (AppPreferences.AntiAliassing.getBoolean()) {
      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g2d.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }
    g2d.scale(scale, scale);
    g.translate(-bds.getX(), -bds.getY());
    final var circuitState = proj == null ? null : stateForExport(proj, circuit, printerView);
    try {
      final var context =
          new ComponentDrawContext(dest, circuit, circuitState, base, g, printerView);
      circuit.draw(context, null);
    } finally {
      if (circuitState != null && circuitState != proj.getCircuitState(circuit)) {
        circuitState.detachFromCircuits();
      }
    }
    return true;
  }

  /**
   * Renders the preview of exporting {@code circuit}: the image the export writes, reduced if
   * needed to fit {@code maxWidth} by {@code maxHeight}, and a caption giving the size of the
   * file's image.
   */
  static PreviewPane.Result renderPreview(
      java.awt.Component dest,
      Project proj,
      Circuit circuit,
      int format,
      double scale,
      boolean printerView,
      Color canvasBackground,
      int maxWidth,
      int maxHeight) {
    final var bds = exportBounds(circuit);
    final var full = imageSize(bds, scale);
    final var shownScale = previewScale(bds, scale, maxWidth, maxHeight);
    final var shown = imageSize(bds, shownScale);
    final var vector = format == FORMAT_TIKZ || format == FORMAT_SVG;
    // A vector export in printer view has no background at all, which the preview shows.
    final var image =
        new BufferedImage(
            shown.width,
            shown.height,
            vector ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
    final var base = image.createGraphics();
    final var g = base.create();
    try {
      paintExport(
          base, g, dest, proj, circuit, bds, shownScale, format, printerView, canvasBackground);
    } finally {
      g.dispose();
      base.dispose();
    }
    final var caption =
        vector
            ? S.get("exportPreviewVectorSize", full.width, full.height)
            : S.get("exportPreviewPixelSize", full.width, full.height);
    return new PreviewPane.Result(image, caption);
  }

  private static class ExportThread extends UniquelyNamedThread {
    final Frame frame;
    final Canvas canvas;
    final File dest;
    final ImageFileFilter filter;
    final List<Circuit> circuits;
    final double scale;
    final boolean printerView;
    final Color canvasBackground;
    final ProgressMonitor monitor;

    ExportThread(
        Frame frame,
        Canvas canvas,
        File dest,
        ImageFileFilter f,
        List<Circuit> circuits,
        double scale,
        boolean printerView,
        Color canvasBackground,
        ProgressMonitor monitor) {
      super("ExportThread");
      this.frame = frame;
      this.canvas = canvas;
      this.dest = dest;
      this.filter = f;
      this.circuits = circuits;
      this.scale = scale;
      this.printerView = printerView;
      this.canvasBackground = canvasBackground;
      this.monitor = monitor;
    }

    private boolean export(Circuit circuit) {
      final var bds = exportBounds(circuit);
      final var size = imageSize(bds, scale);
      Graphics base;
      // Only the raster formats need a raster: a vector export of a large circuit must not fail
      // for want of memory it never uses.
      BufferedImage img = null;
      if (filter.type == FORMAT_TIKZ || filter.type == FORMAT_SVG) {
        final var tz = new TikZWriter();
        tz.setSvgMode(filter.type == FORMAT_SVG);
        base = tz;
      } else {
        img = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB);
        base = img.getGraphics();
      }
      final var g = base.create();
      if (!paintExport(
          base,
          g,
          canvas,
          canvas.getProject(),
          circuit,
          bds,
          scale,
          filter.type,
          printerView,
          canvasBackground)) {
        OptionPane.showMessageDialog(frame, S.get("couldNotCreateImage"));
        monitor.close();
        return false;
      }

      final File where;
      if (dest.isDirectory()) {
        where = new File(dest, circuit.getName() + filter.extensions[0]);
      } else if (filter.accept(dest)) {
        where = dest;
      } else {
        String newName = dest.getName() + filter.extensions[0];
        where = new File(dest.getParentFile(), newName);
      }
      try {
        switch (filter.type) {
          case FORMAT_GIF -> ImageIO.write(img, "GIF", where);
          case FORMAT_PNG -> ImageIO.write(img, "PNG", where);
          case FORMAT_JPG -> ImageIO.write(img, "JPEG", where);
          case FORMAT_TIKZ -> ((TikZWriter) g).writeFile(where);
          case FORMAT_SVG -> ((TikZWriter) g).writeSvg(size.width, size.height, where);
        }
      } catch (Exception e) {
        OptionPane.showMessageDialog(frame, S.get("couldNotCreateFile"));
        logger.error("Could not write exported image to {}", where, e);
        monitor.close();
        return false;
      }
      g.dispose();
      monitor.close();
      return true;
    }

    @Override
    public void run() {
      var allSucceeded = true;
      for (final var circ : circuits) {
        try {
          allSucceeded &= export(circ);
        } catch (RuntimeException | OutOfMemoryError e) {
          // Too large a raster, or a component that fails to draw: say so rather than stopping
          // silently with the progress monitor still up.
          logger.error("Could not export circuit {}", circ.getName(), e);
          monitor.close();
          SwingUtilities.invokeLater(
              () ->
                  OptionPane.showMessageDialog(
                      frame, S.get("couldNotCreateImage") + "\n" + circ.getName()));
          return;
        }
      }
      if (allSucceeded) {
        SwingUtilities.invokeLater(
            () -> OptionPane.showMessageDialog(frame, S.get("exportImageCompleteMessage")));
      }
    }
  }

  public static class ImageFileFilter extends FileFilter {
    private final int type;
    private final String[] extensions;
    private final StringGetter desc;

    public ImageFileFilter(int type, StringGetter desc, String[] exts) {
      this.type = type;
      this.desc = desc;
      extensions = new String[exts.length];
      for (var i = 0; i < exts.length; i++) {
        extensions[i] = "." + exts[i].toLowerCase();
      }
    }

    @Override
    public boolean accept(File f) {
      final var name = f.getName().toLowerCase();
      for (final var extension : extensions) {
        if (name.endsWith(extension)) return true;
      }
      return f.isDirectory();
    }

    @Override
    public String getDescription() {
      return desc.toString();
    }

    public String getDefaultExtension() {
      return extensions[0];
    }

    public int getType() {
      return type;
    }
  }

  /** Everything an export preview depends on, so that a page already rendered is reused. */
  private record PreviewSettings(
      List<Circuit> circuits, int format, double scale, boolean printerView, Color background) {}

  /** The export options beside a live preview of the image they produce. */
  static class OptionsPanel extends JPanel implements ChangeListener {
    private static final long serialVersionUID = 1L;
    final JSlider slider;
    final JLabel curScale;
    final JCheckBox printerView;
    final JRadioButton formatPng;
    final JRadioButton formatGif;
    final JRadioButton formatJpg;
    final JRadioButton formatTikZ;
    final JRadioButton formatSvg;
    final Dimension curJim;
    final CircuitJList list;
    final Project proj;
    final PreviewPane preview;
    private List<Circuit> previewed = List.of();

    OptionsPanel(Project proj, CircuitJList list) {
      super(new BorderLayout(Spacing.lg(), 0));
      this.proj = proj;
      this.list = list;
      // set up components
      formatPng = new JRadioButton("PNG");
      formatGif = new JRadioButton("GIF");
      formatJpg = new JRadioButton("JPEG");
      formatTikZ = new JRadioButton("TikZ");
      formatSvg = new JRadioButton("SVG");
      ButtonGroup bgroup = new ButtonGroup();
      bgroup.add(formatPng);
      bgroup.add(formatGif);
      bgroup.add(formatJpg);
      bgroup.add(formatTikZ);
      bgroup.add(formatSvg);
      formatTikZ.addChangeListener(this);
      formatSvg.addChangeListener(this);
      formatPng.setSelected(true);

      slider = new JSlider(JSlider.HORIZONTAL, -3 * SLIDER_DIVISIONS, 3 * SLIDER_DIVISIONS, 0);
      slider.setMajorTickSpacing(10);
      curScale = new JLabel("222%");
      curScale.setHorizontalAlignment(SwingConstants.RIGHT);
      curScale.setVerticalAlignment(SwingConstants.CENTER);
      final var d = curScale.getPreferredSize();
      curJim =
          new Dimension(
              AppPreferences.getScaled(d.width + (d.width >> 1)),
              AppPreferences.getScaled(d.height));
      curJim.height = Math.max(curJim.height, slider.getPreferredSize().height);
      printerView = new JCheckBox();
      printerView.setSelected(true);
      preview =
          new PreviewPane(
              index ->
                  index < previewed.size()
                      ? S.get(
                          "exportPreviewPosition",
                          previewed.get(index).getName(),
                          index + 1,
                          previewed.size())
                      : " ");
      stateChanged(null);
      slider.addChangeListener(this);

      final var form = new SettingsForm();
      form.addRow(new JLabel(S.get("labelCircuits")), new JScrollPane(list), true);
      final var formatsPanel = new Box(BoxLayout.Y_AXIS);
      formatsPanel.add(formatPng);
      formatsPanel.add(formatGif);
      formatsPanel.add(formatJpg);
      formatsPanel.add(formatTikZ);
      formatsPanel.add(formatSvg);
      form.addRow(new JLabel(S.get("labelImageFormat")), formatsPanel);
      final var scaleRow = new JPanel(new BorderLayout(Spacing.sm(), 0));
      scaleRow.setOpaque(false);
      scaleRow.add(slider, BorderLayout.CENTER);
      scaleRow.add(curScale, BorderLayout.EAST);
      form.addRow(new JLabel(S.get("labelScale")), scaleRow);
      form.addRow(new JLabel(S.get("labelPrinterView")), printerView);
      form.addHint(S.get("exportPrinterViewHint"));

      final var column = new JPanel(new BorderLayout());
      column.setOpaque(false);
      column.add(form, BorderLayout.NORTH);
      add(column, BorderLayout.CENTER);
      add(preview, BorderLayout.EAST);

      final ItemListener refresh = e -> refreshPreview();
      formatPng.addItemListener(refresh);
      formatGif.addItemListener(refresh);
      formatJpg.addItemListener(refresh);
      formatTikZ.addItemListener(refresh);
      formatSvg.addItemListener(refresh);
      printerView.addItemListener(refresh);
      list.addListSelectionListener(
          e -> {
            if (!e.getValueIsAdjusting()) refreshPreview();
          });
      refreshPreview();
    }

    int getImageFormat() {
      if (formatGif.isSelected()) return FORMAT_GIF;
      if (formatJpg.isSelected()) return FORMAT_JPG;
      if (formatTikZ.isSelected()) return FORMAT_TIKZ;
      if (formatSvg.isSelected()) return FORMAT_SVG;
      return FORMAT_PNG;
    }

    boolean getPrinterView() {
      return printerView.isSelected();
    }

    double getScale() {
      return scaleForSlider(slider.getValue());
    }

    /** Shows the selected circuits with the chosen options. */
    void refreshPreview() {
      final var circuits = List.copyOf(list.getSelectedCircuits());
      final var format = getImageFormat();
      final var scale = getScale();
      final var printer = getPrinterView();
      final var frame = proj.getFrame();
      final Canvas canvas = frame == null ? null : frame.getCanvas();
      final var background = canvas == null ? Color.WHITE : canvas.getBackground();
      previewed = circuits;
      preview.setContent(
          new PreviewSettings(circuits, format, scale, printer, background),
          circuits.size(),
          (index, maxWidth, maxHeight) ->
              renderPreview(
                  canvas,
                  proj,
                  circuits.get(index),
                  format,
                  scale,
                  printer,
                  background,
                  maxWidth,
                  maxHeight));
    }

    @Override
    public void stateChanged(ChangeEvent e) {
      final var scale = getScale();
      curScale.setText((int) Math.round(100.0 * scale) + "%");
      if (curJim != null) curScale.setPreferredSize(curJim);
      if (e == null) return;
      if (e.getSource().equals(formatTikZ) || e.getSource().equals(formatSvg)) {
        if (formatTikZ.isSelected() || formatSvg.isSelected()) {
          curScale.setEnabled(false);
          slider.setEnabled(false);
          slider.setValue(0);
          curScale.setText(100 + "%");
          if (curJim != null) curScale.setPreferredSize(curJim);
        } else {
          curScale.setEnabled(true);
          slider.setEnabled(true);
        }
      } else if (e.getSource() == slider) {
        refreshPreview();
      }
    }
  }
}

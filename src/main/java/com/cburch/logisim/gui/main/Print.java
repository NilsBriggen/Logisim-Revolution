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
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentDrawContext;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.gui.canvas.CanvasStyle;
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.gui.generic.SettingsForm;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.util.Spacing;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ItemListener;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.awt.print.PrinterException;
import java.awt.print.PrinterJob;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Print {
  private static final Logger logger = LoggerFactory.getLogger(Print.class);

  /** The space, in circuit units, left around a circuit's bounds on the page. */
  private static final int BORDER_SIZE = 4;

  private Print() {}

  public static void doPrint(Project proj) {
    final var list = new CircuitJList(proj, true);
    final var frame = proj.getFrame();
    if (list.getModel().getSize() == 0) {
      OptionPane.showMessageDialog(
          proj.getFrame(),
          S.get("printEmptyCircuitsMessage"),
          S.get("printEmptyCircuitsTitle"),
          OptionPane.YES_NO_OPTION);
      return;
    }
    final var options = new OptionsPanel(proj, list);
    int action =
        OptionPane.showConfirmDialog(
            frame,
            options,
            S.get("printParmsTitle"),
            OptionPane.OK_CANCEL_OPTION,
            OptionPane.PLAIN_MESSAGE);
    if (action != OptionPane.OK_OPTION) return;
    List<Circuit> circuits = list.getSelectedCircuits();
    if (circuits.isEmpty()) return;

    final var format = options.getPageFormat();
    final var print = options.createPrintable(circuits);
    final var job = options.getPrinterJob();
    job.setPrintable(print, format);
    if (!job.printDialog()) return;
    try {
      job.print();
    } catch (PrinterException e) {
      OptionPane.showMessageDialog(
          proj.getFrame(),
          S.get("printError", e.toString()),
          S.get("printErrorTitle"),
          OptionPane.ERROR_MESSAGE);
    }
  }

  /**
   * Builds the print options, as the Print dialog shows them, for reviewing the dialog without
   * showing it.
   *
   * @param allCircuits whether to select every circuit, so that the preview has several pages
   */
  public static JComponent buildForSnapshot(Project proj, boolean allCircuits) {
    final var list = new CircuitJList(proj, true);
    if (allCircuits) list.setSelectionInterval(0, list.getModel().getSize() - 1);
    return new OptionsPanel(proj, list);
  }

  /** Expands the header's tokens: %n circuit name, %p page, %P page count, %% a percent sign. */
  static String format(String header, int index, int max, String circName) {
    int mark = header.indexOf('%');
    if (mark < 0) return header;
    final var ret = new StringBuilder();
    int start = 0;
    for (;
        mark >= 0 && mark + 1 < header.length();
        start = mark + 2, mark = header.indexOf('%', start)) {
      ret.append(header, start, mark);
      switch (header.charAt(mark + 1)) {
        case 'n' -> ret.append(circName);
        case 'p' -> ret.append("").append(index);
        case 'P' -> ret.append("").append(max);
        case '%' -> ret.append("%");
        default -> ret.append("%").append(header.charAt(mark + 1));
      }
    }
    if (start < header.length()) {
      ret.append(header.substring(start));
    }
    return ret.toString();
  }

  /**
   * Renders page {@code index} of {@code printable} as the print preview shows it: the whole
   * sheet, reduced to fit {@code maxWidth} by {@code maxHeight} pixels, with the margins marked,
   * and a caption giving the scale the circuit prints at.
   */
  static PreviewPane.Result renderPreview(
      MyPrintable printable, PageFormat format, int index, int maxWidth, int maxHeight) {
    final var paperWidth = format.getWidth();
    final var paperHeight = format.getHeight();
    final var zoom = Math.min(maxWidth / paperWidth, maxHeight / paperHeight);
    final var width = Math.max(1, (int) Math.round(paperWidth * zoom));
    final var height = Math.max(1, (int) Math.round(paperHeight * zoom));
    final var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    final var g = image.createGraphics();
    final PrintLayout layout;
    try {
      g.setColor(Color.WHITE);
      g.fillRect(0, 0, width, height);
      g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      g.setRenderingHint(
          RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      g.scale(zoom, zoom);
      // The margins: nothing is printed outside this line.
      final var margin =
          new Rectangle2D.Double(
              format.getImageableX(),
              format.getImageableY(),
              format.getImageableWidth(),
              format.getImageableHeight());
      g.setColor(new Color(0xC8CDD6));
      g.setStroke(
          new BasicStroke(
              (float) (1 / zoom),
              BasicStroke.CAP_BUTT,
              BasicStroke.JOIN_MITER,
              10f,
              new float[] {(float) (4 / zoom), (float) (3 / zoom)},
              0f));
      g.draw(margin);
      g.setStroke(new BasicStroke());
      g.setColor(Color.BLACK);
      // A printer's graphics clip to the sheet; the preview's must too.
      g.setClip(new Rectangle2D.Double(0, 0, paperWidth, paperHeight));
      layout = printable.layout(g, format, index);
      printable.print(g, format, index);
    } finally {
      g.dispose();
    }
    final var percent = (int) Math.round(layout.scale() * 100);
    final String caption;
    if (!layout.fits()) {
      caption = S.get("printPreviewScaleClipped", percent);
    } else if (layout.rotated()) {
      caption = S.get("printPreviewScaleRotated", percent);
    } else {
      caption = S.get("printPreviewScale", percent);
    }
    return new PreviewPane.Result(image, caption);
  }

  static final class MyPrintable implements Printable {
    final Project proj;
    final List<Circuit> circuits;
    final String header;
    final boolean rotateToFit;
    final boolean printerView;
    final double fixedScale;

    MyPrintable(
        Project proj,
        List<Circuit> circuits,
        String header,
        boolean rotateToFit,
        boolean printerView) {
      this(proj, circuits, header, rotateToFit, printerView, PrintLayout.FIT_TO_PAGE);
    }

    /**
     * @param fixedScale the scale to print every circuit at, in points per circuit unit, or
     *     {@link PrintLayout#FIT_TO_PAGE} to fill each page
     */
    MyPrintable(
        Project proj,
        List<Circuit> circuits,
        String header,
        boolean rotateToFit,
        boolean printerView,
        double fixedScale) {
      this.proj = proj;
      this.circuits = circuits;
      this.header = header;
      this.rotateToFit = rotateToFit;
      this.printerView = printerView;
      this.fixedScale = fixedScale;
    }

    /** The header line of page {@code pageIndex}, or null when there is none. */
    String headerText(int pageIndex) {
      if (header == null || header.isEmpty()) return null;
      return format(header, pageIndex + 1, circuits.size(), circuits.get(pageIndex).getName());
    }

    /** The bounds of the circuit on page {@code pageIndex}, measured with the document font. */
    private Bounds circuitBounds(Graphics g, int pageIndex) {
      final var saved = g.getFont();
      g.setFont(CanvasStyle.documentFont());
      try {
        return circuits.get(pageIndex).getBounds(g).expand(BORDER_SIZE);
      } finally {
        g.setFont(saved);
      }
    }

    /**
     * Where the circuit of page {@code pageIndex} goes on the page. {@code g} must still have the
     * font the header is printed in.
     */
    PrintLayout layout(Graphics g, PageFormat format, int pageIndex) {
      final var headHeight = headerText(pageIndex) == null ? 0 : g.getFontMetrics().getHeight();
      final var bds = circuitBounds(g, pageIndex);
      return PrintLayout.compute(
          format.getImageableWidth(),
          format.getImageableHeight(),
          bds.getWidth(),
          bds.getHeight(),
          headHeight,
          rotateToFit,
          fixedScale);
    }

    @Override
    public int print(Graphics base, PageFormat format, int pageIndex) {
      if (pageIndex >= circuits.size()) return Printable.NO_SUCH_PAGE;

      final var circ = circuits.get(pageIndex);
      final var g = base.create();
      final var g2 = g instanceof Graphics2D ? (Graphics2D) g : null;
      final var headerFont = g.getFont();
      final var fm = g.getFontMetrics();
      final var head = headerText(pageIndex);
      final var headHeight = (head == null ? 0 : fm.getHeight());
      final var layout = layout(g, format, pageIndex);
      final var bds = circuitBounds(g, pageIndex);

      // Compute image size
      var imWidth = format.getImageableWidth();
      var imHeight = format.getImageableHeight();
      final var scale = layout.scale();

      // Correct coordinate system for page, including
      // translation and possible rotation.
      if (g2 != null) {
        g2.translate(format.getImageableX(), format.getImageableY());
        if (layout.rotated()) {
          if (imHeight > imWidth) { // portrait -> landscape
            g2.translate(0, imHeight);
            g2.rotate(-Math.PI / 2);
          } else { // landscape -> portrait
            g2.translate(imWidth, 0);
            g2.rotate(Math.PI / 2);
          }
          var t = imHeight;
          imHeight = imWidth;
          imWidth = t;
        }
      }

      // Draw the header line if appropriate
      if (head != null) {
        g.setFont(headerFont);
        g.drawString(head, (int) Math.round((imWidth - fm.stringWidth(head)) / 2), fm.getAscent());
        if (g2 != null) {
          imHeight -= headHeight;
          g2.translate(0, headHeight);
        }
      }
      g.setFont(CanvasStyle.documentFont());

      // Now change coordinate system for circuit, including
      // translation and possible scaling
      if (g2 != null) {
        if (scale != 1.0) {
          g2.scale(scale, scale);
          imWidth /= scale;
          imHeight /= scale;
        }
        double dx = Math.max(0.0, (imWidth - bds.getWidth()) / 2);
        g2.translate(-bds.getX() + dx, -bds.getY());
      }

      // Ensure that the circuit is eligible to be drawn
      final var clip = g.getClipBounds();
      if (clip != null) {
        clip.add(bds.getX(), bds.getY());
        clip.add(bds.getX() + bds.getWidth(), bds.getY() + bds.getHeight());
        g.setClip(clip);
      }

      // And finally draw the circuit onto the page
      final var frame = proj.getFrame();
      final var circState = ExportImage.stateForExport(proj, circ, printerView);
      try {
        final var context =
            new ComponentDrawContext(
                frame == null ? null : frame.getCanvas(), circ, circState, base, g, printerView);
        Collection<Component> noComps = Collections.emptySet();
        circ.draw(context, noComps);
      } finally {
        if (circState != proj.getCircuitState(circ)) circState.detachFromCircuits();
        g.dispose();
      }
      return Printable.PAGE_EXISTS;
    }
  }

  /** Everything a print preview depends on, so that a page already rendered is reused. */
  private record PreviewSettings(
      List<Circuit> circuits,
      String header,
      boolean rotateToFit,
      boolean printerView,
      double fixedScale,
      int orientation,
      double paperWidth,
      double paperHeight,
      Rectangle2D imageable) {}

  /** The print options beside a live preview of the pages they produce. */
  static class OptionsPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    /** Custom scales, in percent. */
    private static final int MIN_PERCENT = 10;

    private static final int MAX_PERCENT = 1000;
    private static final int PERCENT_STEP = 10;

    final Project proj;
    final CircuitJList list;
    final JTextField header = new JTextField(20);
    final JComboBox<String> orientation =
        new JComboBox<>(
            new String[] {S.get("printOrientationPortrait"), S.get("printOrientationLandscape")});
    final JRadioButton fitToPage = new JRadioButton(S.get("printScaleFit"));
    final JRadioButton customScale = new JRadioButton(S.get("printScaleCustom"));
    final JSpinner percent =
        new JSpinner(new SpinnerNumberModel(100, MIN_PERCENT, MAX_PERCENT, PERCENT_STEP));
    final JCheckBox rotateToFit = new JCheckBox();
    final JCheckBox printerView = new JCheckBox();
    final JButton pageSetup = new JButton(S.get("printPageSetup"));
    final PreviewPane preview;

    private PageFormat pageFormat = new PageFormat();
    private PrinterJob job;
    private boolean pageChosen;
    private List<Circuit> previewed = List.of();

    OptionsPanel(Project proj, CircuitJList list) {
      super(new BorderLayout(Spacing.lg(), 0));
      this.proj = proj;
      this.list = list;
      rotateToFit.setSelected(true);
      printerView.setSelected(true);
      header.setText("%n (%p of %P)");
      header.setToolTipText(S.get("labelHeaderTip"));
      fitToPage.setSelected(true);
      final var scaleGroup = new ButtonGroup();
      scaleGroup.add(fitToPage);
      scaleGroup.add(customScale);
      percent.setEnabled(false);
      preview =
          new PreviewPane(
              index -> S.get("printPreviewPage", index + 1, Math.max(1, previewed.size())));

      final var form = new SettingsForm();
      form.addRow(new JLabel(S.get("labelCircuits")), new JScrollPane(list), true);
      form.addRow(new JLabel(S.get("labelHeader")), header, true);
      form.addHint(S.get("labelHeaderTip"));
      form.addRow(new JLabel(S.get("printOrientation")), orientation);
      final var scaleChoice = new Box(BoxLayout.Y_AXIS);
      final var customRow = new JPanel(new FlowLayout(FlowLayout.LEADING, 0, 0));
      customRow.setOpaque(false);
      customRow.add(customScale);
      customRow.add(Box.createHorizontalStrut(Spacing.xs()));
      customRow.add(percent);
      customRow.add(Box.createHorizontalStrut(Spacing.xs()));
      customRow.add(new JLabel("%"));
      fitToPage.setAlignmentX(LEFT_ALIGNMENT);
      customRow.setAlignmentX(LEFT_ALIGNMENT);
      scaleChoice.add(fitToPage);
      scaleChoice.add(customRow);
      form.addRow(new JLabel(S.get("printScale")), scaleChoice);
      form.addRow(new JLabel(S.get("labelRotateToFit")), rotateToFit);
      form.addRow(new JLabel(S.get("labelPrinterView")), printerView);
      form.addControl(pageSetup);

      final var column = new JPanel(new BorderLayout());
      column.setOpaque(false);
      column.add(form, BorderLayout.NORTH);
      add(column, BorderLayout.CENTER);
      add(preview, BorderLayout.EAST);

      final ItemListener refresh = e -> refreshPreview();
      orientation.addItemListener(
          e -> {
            pageFormat.setOrientation(
                orientation.getSelectedIndex() == 0 ? PageFormat.PORTRAIT : PageFormat.LANDSCAPE);
            refreshPreview();
          });
      // Both, since the group deselects one button before it selects the other.
      final ItemListener scaleMode =
          e -> {
            percent.setEnabled(customScale.isSelected());
            refreshPreview();
          };
      fitToPage.addItemListener(scaleMode);
      customScale.addItemListener(scaleMode);
      percent.addChangeListener(e -> refreshPreview());
      rotateToFit.addItemListener(refresh);
      printerView.addItemListener(refresh);
      header.getDocument().addDocumentListener(
          new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
              refreshPreview();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
              refreshPreview();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
              refreshPreview();
            }
          });
      list.addListSelectionListener(
          e -> {
            if (!e.getValueIsAdjusting()) refreshPreview();
          });
      pageSetup.addActionListener(e -> choosePage());
      refreshPreview();
      loadDefaultPage();
    }

    /**
     * Asks the printer system for the default printer's paper in the background, since that can
     * take a moment, and previews on that paper unless the user has already chosen one.
     */
    private void loadDefaultPage() {
      final var loader =
          new Thread(
              () -> {
                try {
                  final var printerJob = PrinterJob.getPrinterJob();
                  final var page = printerJob.defaultPage();
                  SwingUtilities.invokeLater(
                      () -> {
                        if (job == null) job = printerJob;
                        if (pageChosen) return;
                        final var chosen = pageFormat.getOrientation();
                        pageFormat = page;
                        pageFormat.setOrientation(chosen);
                        refreshPreview();
                      });
                } catch (RuntimeException e) {
                  logger.debug("No default page from the printer system", e);
                }
              },
              "PrintDefaultPage");
      loader.setDaemon(true);
      loader.start();
    }

    /** Opens the printer system's page setup, for paper size and margins. */
    private void choosePage() {
      final var chosen = getPrinterJob().pageDialog(pageFormat);
      if (chosen == null || chosen == pageFormat) return;
      pageChosen = true;
      pageFormat = chosen;
      orientation.setSelectedIndex(chosen.getOrientation() == PageFormat.PORTRAIT ? 0 : 1);
      refreshPreview();
    }

    PrinterJob getPrinterJob() {
      if (job == null) job = PrinterJob.getPrinterJob();
      return job;
    }

    PageFormat getPageFormat() {
      return (PageFormat) pageFormat.clone();
    }

    double getFixedScale() {
      return customScale.isSelected()
          ? ((Number) percent.getValue()).doubleValue() / 100.0
          : PrintLayout.FIT_TO_PAGE;
    }

    MyPrintable createPrintable(List<Circuit> circuits) {
      return new MyPrintable(
          proj,
          circuits,
          header.getText(),
          rotateToFit.isSelected(),
          printerView.isSelected(),
          getFixedScale());
    }

    /** Shows the selected circuits, one per page, with the chosen options. */
    void refreshPreview() {
      final var circuits = List.copyOf(list.getSelectedCircuits());
      final var printable = createPrintable(circuits);
      final var format = getPageFormat();
      previewed = circuits;
      preview.setContent(
          new PreviewSettings(
              circuits,
              printable.header,
              printable.rotateToFit,
              printable.printerView,
              printable.fixedScale,
              format.getOrientation(),
              format.getWidth(),
              format.getHeight(),
              new Rectangle2D.Double(
                  format.getImageableX(),
                  format.getImageableY(),
                  format.getImageableWidth(),
                  format.getImageableHeight())),
          circuits.size(),
          (index, maxWidth, maxHeight) ->
              renderPreview(printable, format, index, maxWidth, maxHeight));
    }
  }
}

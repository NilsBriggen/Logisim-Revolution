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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.gates.GatesLibrary;
import com.cburch.logisim.tools.AddTool;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.awt.print.PageFormat;
import java.awt.print.Printable;
import java.util.List;
import org.junit.jupiter.api.Test;

class PrintTest {

  @Test
  void headerTokensAreExpanded() {
    assertEquals("main (2 of 3)", Print.format("%n (%p of %P)", 2, 3, "main"));
    assertEquals("100% done", Print.format("100%% done", 1, 1, "x"));
    assertEquals("keep %q", Print.format("keep %q", 1, 1, "x"));
    assertEquals("plain", Print.format("plain", 1, 1, "x"));
  }

  @Test
  void previewShowsTheWholeSheetInItsProportions() {
    final var project = projectWithGate();
    final var printable = printable(project, PrintLayout.FIT_TO_PAGE);
    final var portrait = new PageFormat();

    final var result = Print.renderPreview(printable, portrait, 0, 300, 300);

    final var image = result.image();
    assertEquals(300, image.getHeight());
    assertEquals(
        (int) Math.round(portrait.getWidth() * 300 / portrait.getHeight()), image.getWidth());
    assertTrue(hasInk(image), "the circuit is drawn on the page");
    assertEquals(Color.WHITE.getRGB(), image.getRGB(1, 1), "the paper is white");

    final var landscape = new PageFormat();
    landscape.setOrientation(PageFormat.LANDSCAPE);
    final var turned = Print.renderPreview(printable, landscape, 0, 300, 300).image();
    assertEquals(300, turned.getWidth());
    assertTrue(turned.getHeight() < turned.getWidth());
  }

  @Test
  void previewCaptionGivesThePrintedScale() {
    final var project = projectWithGate();
    final var format = new PageFormat();

    final var fixed = Print.renderPreview(printable(project, 1.5), format, 0, 200, 200);
    assertEquals(S.get("printPreviewScale", 150), fixed.caption());

    final var fitted =
        Print.renderPreview(printable(project, PrintLayout.FIT_TO_PAGE), format, 0, 200, 200);
    final var probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB).createGraphics();
    final var layout = printable(project, PrintLayout.FIT_TO_PAGE).layout(probe, format, 0);
    probe.dispose();
    assertEquals(
        S.get("printPreviewScale", Math.round(layout.scale() * 100)), fitted.caption());
    assertTrue(layout.scale() > 1, "a small circuit is scaled up to fill the page");

    final var tooLarge = Print.renderPreview(printable(project, 50), format, 0, 200, 200);
    assertEquals(S.get("printPreviewScaleClipped", 5000), tooLarge.caption());
  }

  @Test
  void printingTheSamePageDrawsWhatThePreviewShows() {
    final var project = projectWithGate();
    final var printable = printable(project, PrintLayout.FIT_TO_PAGE);
    final var format = new PageFormat();
    final var width = (int) format.getWidth();
    final var height = (int) format.getHeight();
    final var page = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    final var g = page.createGraphics();
    g.setColor(Color.WHITE);
    g.fillRect(0, 0, width, height);
    g.setClip(0, 0, width, height);
    assertEquals(Printable.PAGE_EXISTS, printable.print(g, format, 0));
    assertEquals(Printable.NO_SUCH_PAGE, printable.print(g, format, 1));
    g.dispose();
    assertTrue(hasInk(page));
  }

  private static Print.MyPrintable printable(Project project, double scale) {
    final List<Circuit> circuits = List.of(project.getLogisimFile().getMainCircuit());
    return new Print.MyPrintable(project, circuits, "%n (%p of %P)", true, true, scale);
  }

  private static Project projectWithGate() {
    final var file = LogisimFile.createNew(new Loader(null), null);
    final var project = new Project(file);
    final var circuit = file.getMainCircuit();
    circuit.setProject(project);
    project.setCurrentCircuit(circuit);
    final var and = ((AddTool) new GatesLibrary().getTool("AND Gate")).getFactory();
    final var mutation = new CircuitMutation(circuit);
    mutation.add(and.createComponent(Location.create(100, 100, false), and.createAttributeSet()));
    mutation.execute();
    return project;
  }

  static boolean hasInk(BufferedImage image) {
    for (var y = 0; y < image.getHeight(); y++) {
      for (var x = 0; x < image.getWidth(); x++) {
        final var rgb = image.getRGB(x, y) & 0xFFFFFF;
        // Dark enough to be drawing rather than the paper or the margin guide.
        if (((rgb >> 16) & 0xFF) < 0x80 && ((rgb >> 8) & 0xFF) < 0x80 && (rgb & 0xFF) < 0x80) {
          return true;
        }
      }
    }
    return false;
  }
}

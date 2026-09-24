/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.gui.generic.FormLayoutTestSupport;
import com.cburch.logisim.util.UiScale;
import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.Color;
import java.net.URI;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.text.html.CSS;
import javax.swing.text.html.HTMLDocument;
import javax.swing.text.html.HTMLEditorKit;
import org.junit.jupiter.api.Test;

class HelpPresentationTest {
  @Test
  void darkPresentationOverridesLegacyHeadingsWithoutReplacingDocumentOrLinks() throws Exception {
    FormLayoutTestSupport.atScale(1, () -> {
      FlatDarkLaf.setup();
      final var editor = editor();
      final var document = (HTMLDocument) editor.getDocument();
      try {
        document.setBase(URI.create("https://example.invalid/help/").toURL());
      } catch (java.net.MalformedURLException exception) {
        throw new AssertionError(exception);
      }
      final var base = document.getBase();
      HelpPresentation.apply(editor);
      final var styles = document.getStyleSheet();
      assertSame(document, editor.getDocument());
      assertEquals(base, document.getBase());
      assertEquals(editor.getForeground(), styles.getForeground(styles.getRule("h1")));
      assertEquals(editor.getBackground(), styles.getBackground(styles.getRule("body")));
      assertTrue(editor.getForeground().getRed() > editor.getBackground().getRed());
      final var link = document.getIterator(javax.swing.text.html.HTML.Tag.A);
      assertEquals("next.html", link.getAttributes().getAttribute(javax.swing.text.html.HTML.Attribute.HREF));
    });
  }

  @Test
  void newPagesAndReplacedViewerChildrenReceivePresentation() throws Exception {
    FormLayoutTestSupport.atScale(1, () -> {
      final var viewer = new JPanel();
      final var editor = editor();
      viewer.add(editor);
      HelpPresentation.install(viewer);
      final var nextDocument = (HTMLDocument) new HTMLEditorKit().createDefaultDocument();
      editor.setDocument(nextDocument);
      assertEquals(editor.getForeground(), nextDocument.getStyleSheet().getForeground(
          nextDocument.getStyleSheet().getRule("body")));
      viewer.removeAll();
      final var replacement = editor();
      viewer.add(replacement);
      assertEquals(Boolean.TRUE,
          replacement.getClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES));
    });
  }

  @Test
  void liveScaleDownReplacesCssMetricsInsteadOfAccumulatingStylesheets() throws Exception {
    FormLayoutTestSupport.atScale(2, () -> {
      final var editor = editor();
      HelpPresentation.apply(editor);
      final var largeFont = editor.getFont().getSize();
      final var document = (HTMLDocument) editor.getDocument();
      final var sheets = document.getStyleSheet().getStyleSheets().length;
      UiScale.setFactor(1);
      SwingUtilities.updateComponentTreeUI(editor);
      HelpPresentation.apply(editor);
      assertTrue(editor.getFont().getSize() < largeFont);
      assertEquals(editor.getFont().getSize() + "px", document.getStyleSheet().getRule("body")
          .getAttribute(CSS.Attribute.FONT_SIZE).toString());
      assertEquals(Math.round(editor.getFont().getSize() * 1.6) + "px",
          document.getStyleSheet().getRule("h1").getAttribute(CSS.Attribute.FONT_SIZE).toString());
      assertEquals(sheets, document.getStyleSheet().getStyleSheets().length);
    });
  }

  @Test
  void lowContrastAccentFallsBackToReadableUnderlinedText() {
    assertEquals(Color.BLACK, HelpPresentation.readableLink(Color.YELLOW, Color.BLACK, Color.WHITE));
    assertEquals(Color.WHITE, HelpPresentation.readableLink(Color.BLUE, Color.WHITE, Color.BLACK));
    assertEquals(Color.BLUE, HelpPresentation.readableLink(Color.BLUE, Color.BLACK, Color.WHITE));
  }

  private static JEditorPane editor() {
    return new JEditorPane("text/html", "<html><head><style>h1 { color: #00137f; }</style>"
        + "</head><body><h1>Guide</h1><p><a href='next.html'>Next</a></p></body></html>");
  }
}

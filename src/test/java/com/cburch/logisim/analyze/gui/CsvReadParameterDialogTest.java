/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.analyze.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.analyze.data.CsvParameter;
import com.cburch.logisim.gui.generic.FormLayoutTestSupport;
import com.cburch.logisim.util.UiScale;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CsvReadParameterDialogTest {
  @TempDir Path directory;

  @Test
  void changingDraftDoesNotMutateTargetUntilContinue() {
    final var target = new CsvParameter();
    final var oldQuote = target.quote();
    final var oldSeparator = target.seperator();
    final var draft = new CsvReadParameterDialog.ImportSettings(target);
    draft.setQuote('\'');
    draft.setSeperator(';');
    assertEquals(oldQuote, target.quote());
    assertEquals(oldSeparator, target.seperator());
    assertFalse(target.isValid());
    draft.approve();
    assertEquals('\'', target.quote());
    assertEquals(';', target.seperator());
    assertTrue(target.isValid());
  }

  @Test
  void previewKeepsSpacesAndUsesChosenDelimiterWithoutApprovingImport() throws Exception {
    final var file = directory.resolve("preview.csv");
    Files.writeString(file, "'input name';'output name'\n0;1\n1;0\n0;0\nignored;row\n");
    final var parameters = new CsvParameter();
    parameters.setQuote('\'');
    parameters.setSeperator(';');
    final var rows = CsvReadParameterDialog.readPreview(file.toFile(), parameters);
    assertEquals(4, rows.length);
    assertEquals("input name", rows[0][0]);
    assertEquals("output name", rows[0][1]);
    assertEquals("1", rows[1][1]);
    assertEquals("0", rows[3][0]);
    assertEquals("", rows[3][3]);
    assertFalse(parameters.isValid());
  }

  @Test
  void missingFileReportsFailureAndEmptyFileHasBlankPreview() throws Exception {
    final var parameters = new CsvParameter();
    assertThrows(FileNotFoundException.class, () -> CsvReadParameterDialog.readPreview(
        directory.resolve("missing.csv").toFile(), parameters));
    final var file = Files.createFile(directory.resolve("empty.csv"));
    for (final var row : CsvReadParameterDialog.readPreview(file.toFile(), parameters)) {
      for (final var cell : row) assertEquals("", cell);
    }
  }

  @Test
  void scaledPreviewIsReadOnlyScrollableAndShrinksAfterLiveScaleChange() throws Exception {
    FormLayoutTestSupport.atScale(2, () -> {
      final var table = new CsvReadParameterDialog.PreviewTable();
      final var scroll = new JScrollPane(table);
      scroll.setSize(800, 300);
      FormLayoutTestSupport.layoutTree(scroll);
      assertFalse(table.isCellEditable(0, 0));
      assertTrue(table.getRowHeight() > table.getFontMetrics(table.getFont()).getHeight());
      assertTrue(scroll.getHorizontalScrollBar().isVisible());
      final var largeHeight = table.getRowHeight();
      UiScale.setFactor(1);
      SwingUtilities.updateComponentTreeUI(scroll);
      assertTrue(table.getRowHeight() < largeHeight);
    });
  }
}

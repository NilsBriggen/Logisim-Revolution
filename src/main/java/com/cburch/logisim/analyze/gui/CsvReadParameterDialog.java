/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.analyze.gui;

import static com.cburch.logisim.analyze.Strings.S;

import com.cburch.logisim.analyze.data.CsvInterpretor;
import com.cburch.logisim.analyze.data.CsvParameter;
import com.cburch.logisim.gui.generic.Dialogs;
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.gui.generic.ScrollableForm;
import com.cburch.logisim.util.UiScale;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Scanner;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableModel;

public class CsvReadParameterDialog extends JDialog implements ActionListener {
  private static final long serialVersionUID = 1L;
  private final JComboBox<String> quotes;
  private final JComboBox<String> seperators;
  private final JButton okButton;
  private final PreviewTable preview = new PreviewTable();
  private final File file;
  private final ImportSettings draft;

  public CsvReadParameterDialog(CsvParameter sel, File file, JFrame parentFrame) {
    super(parentFrame, S.get("csvImportTitle"), true);
    this.file = file;
    draft = new ImportSettings(sel);
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    Dialogs.installEscapeToClose(this);
    final var gap = UiScale.scaled(12);
    final var content = new JPanel(new BorderLayout(gap, gap));
    content.setBorder(BorderFactory.createEmptyBorder(gap, gap, gap, gap));
    setContentPane(content);

    quotes = new JComboBox<>(new String[] {"\"", "'"});
    quotes.setSelectedItem(String.valueOf(draft.quote()));
    seperators = new JComboBox<>(new String[] {",", ";", ":", S.get("seperatorSpace"),
        S.get("SeperatorTab")});
    final var separatorIndex = ",;: \t".indexOf(draft.seperator());
    seperators.setSelectedIndex(Math.max(0, separatorIndex));
    quotes.addActionListener(this);
    seperators.addActionListener(this);
    final var form = new JPanel(new GridBagLayout());
    final var gbc = new GridBagConstraints();
    gbc.insets = new Insets(0, 0, UiScale.scaled(8), gap);
    gbc.anchor = GridBagConstraints.LINE_START;
    gbc.gridx = 0;
    gbc.gridy = 0;
    final var quoteLabel = new JLabel(S.get("UsedQuotesInFile"));
    quoteLabel.setLabelFor(quotes);
    form.add(quoteLabel, gbc);
    gbc.gridy++;
    final var separatorLabel = new JLabel(S.get("UsedSeperatorInFile"));
    separatorLabel.setLabelFor(seperators);
    form.add(separatorLabel, gbc);
    gbc.gridx = 1;
    gbc.gridy = 0;
    gbc.weightx = 1;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    form.add(quotes, gbc);
    gbc.gridy++;
    form.add(seperators, gbc);
    content.add(form, BorderLayout.NORTH);

    final var previewPanel = new JPanel(new BorderLayout(0, UiScale.scaled(8)));
    final var previewLabel = new JLabel(S.get("cvsFilePreview"));
    previewLabel.setLabelFor(preview);
    preview.getAccessibleContext().setAccessibleName(S.get("cvsFilePreview"));
    previewPanel.add(previewLabel, BorderLayout.NORTH);
    previewPanel.add(new JScrollPane(preview), BorderLayout.CENTER);
    content.add(previewPanel, BorderLayout.CENTER);

    okButton = new JButton(S.get("ConfirmCsvParameters"));
    okButton.addActionListener(this);
    getRootPane().setDefaultButton(okButton);
    final var cancel = new JButton(S.get("buildCancel"));
    cancel.addActionListener(event -> dispose());
    final var buttons = new JPanel(new FlowLayout(FlowLayout.TRAILING, gap, 0));
    buttons.add(cancel);
    buttons.add(okButton);
    content.add(buttons, BorderLayout.SOUTH);
    if (!updatePreview()) {
      dispose();
      return;
    }
    ScrollableForm.sizeWindow(this, new Dimension(620, 340), new Dimension(420, 280), true);
    setLocationRelativeTo(parentFrame);
    setVisible(true);
  }

  /** Match the import parser, but limit preview work to the advertised four rows/columns. */
  static String[][] readPreview(File file, CsvParameter parameters) throws FileNotFoundException {
    final var values = new String[4][4];
    for (final var row : values) Arrays.fill(row, "");
    try (final var scanner = new Scanner(file)) {
      for (var y = 0; y < values.length && scanner.hasNextLine(); y++) {
        final var row = CsvInterpretor.parseCsvLine(
            scanner.nextLine(), parameters.seperator(), parameters.quote());
        for (var x = 0; x < Math.min(row.size(), values[y].length); x++) {
          values[y][x] = row.get(x) == null ? "" : row.get(x);
        }
      }
    }
    return values;
  }

  private boolean updatePreview() {
    try {
      preview.setValues(readPreview(file, draft));
      return true;
    } catch (FileNotFoundException exception) {
      OptionPane.showMessageDialog(this, S.get("cantReadMessage", file.getName()),
          S.get("csvImportTitle"), OptionPane.ERROR_MESSAGE);
      dispose();
      return false;
    }
  }

  @Override
  public void actionPerformed(ActionEvent event) {
    if (event.getSource() == okButton) {
      draft.approve();
      dispose();
    } else {
      draft.setQuote(((String) quotes.getSelectedItem()).charAt(0));
      draft.setSeperator(",;: \t".charAt(seperators.getSelectedIndex()));
      updatePreview();
    }
  }

  static class ImportSettings extends CsvParameter {
    private final CsvParameter target;

    ImportSettings(CsvParameter target) {
      this.target = target;
      setQuote(target.quote());
      setSeperator(target.seperator());
    }

    void approve() {
      target.setQuote(quote());
      target.setSeperator(seperator());
      target.setValid();
    }
  }

  static class PreviewTable extends JTable {
    private static final long serialVersionUID = 1L;

    PreviewTable() {
      setValues(new String[4][4]);
      setAutoResizeMode(AUTO_RESIZE_OFF);
      getTableHeader().setReorderingAllowed(false);
      setFillsViewportHeight(true);
    }

    void setValues(String[][] values) {
      setModel(new DefaultTableModel(values, new String[] {"1", "2", "3", "4"}) {
        @Override
        public boolean isCellEditable(int row, int column) {
          return false;
        }
      });
      updateMetrics();
    }

    @Override
    public void updateUI() {
      super.updateUI();
      updateMetrics();
    }

    private void updateMetrics() {
      if (getFont() == null || getColumnModel() == null) return;
      setRowHeight(getFontMetrics(getFont()).getHeight() + UiScale.scaled(10));
      for (var i = 0; i < getColumnCount(); i++) {
        getColumnModel().getColumn(i).setPreferredWidth(UiScale.scaled(180));
      }
      setPreferredScrollableViewportSize(new Dimension(UiScale.scaled(560), 4 * getRowHeight()));
    }

    @Override
    public String getToolTipText(MouseEvent event) {
      final var row = rowAtPoint(event.getPoint());
      final var column = columnAtPoint(event.getPoint());
      final var value = row < 0 || column < 0 ? null : getValueAt(row, column);
      return value == null ? null : value.toString();
    }
  }
}

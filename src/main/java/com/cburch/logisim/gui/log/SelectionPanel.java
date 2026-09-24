/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.log;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.data.Direction;
import com.cburch.logisim.gui.generic.ScrollableForm;
import com.cburch.logisim.gui.icons.FatArrowIcon;
import com.cburch.logisim.gui.theme.Theme;
import com.cburch.logisim.gui.theme.Tokens;
import com.cburch.logisim.util.JDialogOk;
import com.cburch.logisim.util.UiScale;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ScrollPaneConstants;

public class SelectionPanel extends LogPanel {
  private static final long serialVersionUID = 1L;
  private final ComponentSelector selector;
  private final SelectionList list;
  private final JLabel selectDesc;
  private final JLabel exploreLabel;
  private final JLabel listLabel;
  private final JLabel emptyLabel = new JLabel();
  private final TransferControls controls;

  public SelectionPanel(LogFrame window) {
    super(window);
    selector = new ComponentSelector(getModel().getCircuit(), ComponentSelector.ANY_SIGNAL);
    list = new SelectionList();
    list.setLogModel(getModel());

    final var explorerPane =
        new JScrollPane(
            selector,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    final var listPane =
        new JScrollPane(
            list,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);

    final var gridbag = new GridBagLayout();
    final var gbc = new GridBagConstraints();
    setLayout(gridbag);

    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.weightx = gbc.weighty = 0.0;
    gbc.insets = new Insets(UiScale.scaled(12), UiScale.scaled(10), 0, UiScale.scaled(10));

    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 3;
    selectDesc = new JLabel();
    gridbag.setConstraints(selectDesc, gbc);
    add(selectDesc);
    gbc.gridwidth = 1;

    gbc.gridx = 0;
    gbc.gridy = 1;
    exploreLabel = new JLabel();
    exploreLabel.setLabelFor(selector);
    gridbag.setConstraints(exploreLabel, gbc);
    add(exploreLabel);

    gbc.gridx = 2;
    gbc.gridy = 1;
    listLabel = new JLabel();
    listLabel.setLabelFor(list);
    gridbag.setConstraints(listLabel, gbc);
    add(listLabel);

    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = gbc.weighty = 1.0;
    final var gap = UiScale.scaled(10);
    gbc.insets = new Insets(gap, gap, gap, gap);
    gbc.gridx = 0;
    gbc.gridy = 2;
    gridbag.setConstraints(explorerPane, gbc);
    add(explorerPane);
    explorerPane.setPreferredSize(new Dimension(UiScale.scaled(220), UiScale.scaled(240)));

    controls = new TransferControls(
        () -> list.add(selector.getSelectedItems()), list::removeSelected);
    selector
        .getSelectionModel()
        .addListSelectionListener(
            e -> controls.addButton.setEnabled(!selector.getSelectionModel().isSelectionEmpty()));
    list.getSelectionModel()
        .addListSelectionListener(
            e -> controls.removeButton.setEnabled(!list.getSelectionModel().isSelectionEmpty()));
    list.getModel().addTableModelListener(event -> updateEmptyState());

    gbc.fill = GridBagConstraints.NONE;
    gbc.weightx = gbc.weighty = 0.0;
    gbc.insets = new Insets(0, 0, 0, 0);
    gbc.gridx = 1;
    gbc.gridy = 2;
    gridbag.setConstraints(controls, gbc);
    add(controls);

    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = gbc.weighty = 1.0;
    gbc.insets = new Insets(gap, gap, gap, gap);
    gbc.gridx = 2;
    gbc.gridy = 2;
    final var selectedPanel = new JPanel(new BorderLayout(0, UiScale.scaled(8)));
    selectedPanel.add(listPane, BorderLayout.CENTER);
    selectedPanel.add(emptyLabel, BorderLayout.SOUTH);
    gridbag.setConstraints(selectedPanel, gbc);
    add(selectedPanel);
    listPane.setPreferredSize(
        new Dimension(UiScale.scaled(240), UiScale.scaled(240)));
    updateMetrics();
    updateEmptyState();
    Theme.addListener(this, this::updateMetrics);
    localeChanged();
  }

  private void updateMetrics() {
    list.setRowHeight(list.getFontMetrics(list.getFont()).getHeight() + UiScale.scaled(10));
    emptyLabel.setForeground(Tokens.mutedForeground());
    revalidate();
  }

  private void updateEmptyState() {
    emptyLabel.setVisible(list.getRowCount() == 0);
  }

  @Override
  public String getHelpText() {
    return S.get("selectionHelp");
  }

  @Override
  public String getTitle() {
    return S.get("selectionTab");
  }

  @Override
  public void localeChanged() {
    selectDesc.setText(S.get("selectionDesc"));
    exploreLabel.setText(S.get("exploreLabel"));
    listLabel.setText(S.get("listLabel"));
    emptyLabel.setText(S.get("selectionEmpty"));
    controls.localeChanged();
    selector.localeChanged();
    list.localeChanged();
  }

  @Override
  public void modelChanged(Model oldModel, Model newModel) {
    selector.setRootCircuit(newModel.getCircuit());
    list.setLogModel(newModel);
    updateEmptyState();
  }

  static class TransferControls extends JPanel {
    private static final long serialVersionUID = 1L;
    final JButton addButton = new JButton(new FatArrowIcon(Direction.EAST));
    final JButton removeButton = new JButton(new FatArrowIcon(Direction.WEST));

    TransferControls(Runnable addSelection, Runnable removeSelection) {
      setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
      addButton.setEnabled(false);
      removeButton.setEnabled(false);
      addButton.addActionListener(event -> addSelection.run());
      removeButton.addActionListener(event -> removeSelection.run());
      add(addButton);
      add(Box.createVerticalStrut(UiScale.scaled(8)));
      add(removeButton);
      localeChanged();
    }

    void localeChanged() {
      addButton.setText(S.get("selectionAdd"));
      removeButton.setText(S.get("selectionRemove"));
      addButton.setToolTipText(S.get("selectionAddTip"));
      removeButton.setToolTipText(S.get("selectionRemoveTip"));
    }
  }

  static class SelectionDialog extends JDialogOk {
    private static final long serialVersionUID = 1L;
    final SelectionPanel selPanel;

    SelectionDialog(LogFrame logFrame) {
      super(S.get("selectionDialogTitle"), false);
      selPanel = new SelectionPanel(logFrame);
      selPanel.localeChanged();
      getContentPane().add(selPanel);
      ok.setText(S.get("statsCloseButton"));
      ScrollableForm.sizeWindow(this, new Dimension(680, 420), new Dimension(480, 300), true);
    }

    @Override
    public void cancelClicked() {
      okClicked();
    }

    @Override
    public void okClicked() {
      // do nothing
    }
  }

  public static void doDialog(LogFrame logFrame) {
    SelectionDialog d = new SelectionDialog(logFrame);
    d.setVisible(true);
  }
}

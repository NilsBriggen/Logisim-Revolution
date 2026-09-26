/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.fpga.prefs;

import static com.cburch.logisim.fpga.Strings.S;

import com.cburch.logisim.fpga.hdlgenerator.HdlGeneratorFactory;
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.gui.prefs.ColorChooserButton;
import com.cburch.logisim.gui.prefs.OptionsPanel;
import com.cburch.logisim.gui.prefs.PrefOption;
import com.cburch.logisim.gui.prefs.PrefOptionList;
import com.cburch.logisim.gui.prefs.PreferencesFrame;
import com.cburch.logisim.gui.shell.SectionPanel;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.prefs.PrefMonitorBoolean;
import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

@SuppressWarnings("serial")
public class FpgaOptions extends OptionsPanel {

  private class MyListener implements ActionListener, PreferenceChangeListener {

    @Override
    public void actionPerformed(ActionEvent ae) {
      Object source = ae.getSource();
      if (source == WorkSpaceButton) {
        selectWorkSpace(frame);
      }
    }

    @Override
    public void preferenceChange(PreferenceChangeEvent pce) {
      String property = pce.getKey();
      if (property.equals(AppPreferences.FPGA_Workspace.getIdentifier())) {
        showWorkspace();
      } else if (property.equals(AppPreferences.HdlType.getIdentifier())) {
        final var isVhdl = AppPreferences.HdlType.get().equals(HdlGeneratorFactory.VHDL);
        vhdlPan.setEnabled(isVhdl);
        vhdlKeywordUpperCase.setEnabled(isVhdl);
      }
    }
  }

  private final MyListener myListener = new MyListener();
  private final JLabel WorkspaceLabel = new JLabel();
  private final JTextField WorkSpacePath;
  private final JButton WorkSpaceButton;
  private final JLabel EditSelectLabel = new JLabel();
  private ColorChooserButton EditSelectColor;
  private final JLabel EditHighligtLabel = new JLabel();
  private ColorChooserButton EditHighligtColor;
  private final JLabel EditMoveLabel = new JLabel();
  private ColorChooserButton EditMoveColor;
  private final JLabel EditResizeLabel = new JLabel();
  private ColorChooserButton EditResizeColor;
  private final JLabel MappedLabel = new JLabel();
  private ColorChooserButton MappedColor;
  private final JLabel SelMapLabel = new JLabel();
  private ColorChooserButton SelMapColor;
  private final JLabel SelectMapLabel = new JLabel();
  private ColorChooserButton SelectMapColor;
  private final JLabel SelectLabel = new JLabel();
  private ColorChooserButton SelectColor;
  private JPanel editPan;
  private JPanel mapPan;
  private JPanel ReportPan;
  private JPanel vhdlPan;

  /**
   * The four groups on this page.
   *
   * <p>They were etched titled boxes -- a line drawn round a group, which adds a rule without
   * saying anything -- inside a window whose other pages had already moved to the shell's
   * collapsible sections.
   */
  private SectionPanel vhdlSection;

  private SectionPanel reportSection;
  private SectionPanel editSection;
  private SectionPanel mapSection;
  private JCheckBox SuppressGated;
  private JCheckBox SuppressOpen;
  private JCheckBox vhdlKeywordUpperCase;
  private final PreferencesFrame frame;
  private final PrefOptionList HDL_Used;

  public FpgaOptions(PreferencesFrame frame) {
    super(frame);
    this.frame = frame;
    AppPreferences.getPrefs().addPreferenceChangeListener(myListener);

    WorkSpacePath = new JTextField(32);
    WorkSpacePath.setEditable(false);
    showWorkspace();
    WorkSpaceButton = new JButton();
    WorkSpaceButton.addActionListener(myListener);
    HDL_Used =
        new PrefOptionList(
            AppPreferences.HdlType,
            S.getter("HDLLanguageUsed"),
            new PrefOption[] {
              new PrefOption(HdlGeneratorFactory.VHDL, S.getter("VHDL")),
              new PrefOption(HdlGeneratorFactory.VERILOG, S.getter("Verilog")),
              new PrefOption(HdlGeneratorFactory.NONE, S.getter("None"))
            });

    GridBagLayout layout = new GridBagLayout();
    GridBagConstraints gbc = new GridBagConstraints();
    setLayout(layout);

    gbc.insets = new Insets(2, 4, 4, 2);
    gbc.anchor = GridBagConstraints.BASELINE_LEADING;

    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.fill = GridBagConstraints.NONE;
    add(WorkspaceLabel, gbc);
    gbc.gridx = 2;
    gbc.gridy = 0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    add(WorkSpaceButton, gbc);
    gbc.gridx = 1;
    gbc.gridy = 0;
    gbc.weightx = 1.0;
    add(WorkSpacePath, gbc);
    gbc.gridx = 0;
    gbc.gridy = 1;
    gbc.gridwidth = 2;
    add(HDL_Used.getJLabel(), gbc);
    gbc.gridx = 2;
    gbc.gridy = 1;
    gbc.gridwidth = 1;
    add(HDL_Used.getJComboBox(), gbc);
    gbc.gridx = 0;
    gbc.gridy = 3;
    gbc.gridwidth = 3;
    vhdlSection = new SectionPanel(S.get("VhdlOptions"), getVhdlOptions(), true);
    add(vhdlSection, gbc);
    gbc.gridy++;
    add(AppPreferences.Boards.addRemovePanel(), gbc);
    gbc.gridy++;
    reportSection = new SectionPanel(S.get("ReporterOptions"), getReporterOptions(), true);
    add(reportSection, gbc);
    gbc.gridy++;
    editSection = new SectionPanel(S.get("EditColors"), getEditCols(), true);
    add(editSection, gbc);
    gbc.gridy++;
    mapSection = new SectionPanel(S.get("MapColors"), getMapCols(), true);
    add(mapSection, gbc);
    localeChanged();
  }

  private JPanel getVhdlOptions() {
    final var isVhdl = AppPreferences.HdlType.get().equals(HdlGeneratorFactory.VHDL);
    vhdlPan = new JPanel();
    vhdlPan.setLayout(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    vhdlKeywordUpperCase =
        ((PrefMonitorBoolean) AppPreferences.VhdlKeywordsUpperCase).getCheckBox();
    vhdlPan.add(vhdlKeywordUpperCase, gbc);
    vhdlPan.setEnabled(isVhdl);
    vhdlKeywordUpperCase.setEnabled(isVhdl);
    return vhdlPan;
  }

  private JPanel getReporterOptions() {
    ReportPan = new JPanel();
    ReportPan.setLayout(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    SuppressGated = ((PrefMonitorBoolean) AppPreferences.SuppressGatedClockWarnings).getCheckBox();
    ReportPan.add(SuppressGated, gbc);
    gbc.gridy++;
    SuppressOpen = ((PrefMonitorBoolean) AppPreferences.SuppressOpenPinWarnings).getCheckBox();
    ReportPan.add(SuppressOpen, gbc);
    return ReportPan;
  }

  /**
   * Shows the workspace from its start, with the full path as tooltip: a long path used to show a
   * fragment from its middle with nothing saying it was cut.
   */
  private void showWorkspace() {
    final var path = AppPreferences.FPGA_Workspace.get();
    WorkSpacePath.setText(path);
    WorkSpacePath.setToolTipText(path);
    WorkSpacePath.setCaretPosition(0);
  }

  private JPanel getEditCols() {
    editPan = new JPanel(new GridBagLayout());
    EditSelectColor = new ColorChooserButton(frame, AppPreferences.FPGA_DEFINE_COLOR);
    EditHighligtColor = new ColorChooserButton(frame, AppPreferences.FPGA_DEFINE_HIGHLIGHT_COLOR);
    EditMoveColor = new ColorChooserButton(frame, AppPreferences.FPGA_DEFINE_MOVE_COLOR);
    EditResizeColor = new ColorChooserButton(frame, AppPreferences.FPGA_DEFINE_RESIZE_COLOR);
    addColorRow(editPan, 0, EditSelectLabel, EditSelectColor);
    addColorRow(editPan, 1, EditHighligtLabel, EditHighligtColor);
    addColorRow(editPan, 2, EditMoveLabel, EditMoveColor);
    addColorRow(editPan, 3, EditResizeLabel, EditResizeColor);
    return editPan;
  }

  private JPanel getMapCols() {
    mapPan = new JPanel(new GridBagLayout());
    MappedColor = new ColorChooserButton(frame, AppPreferences.FPGA_MAPPED_COLOR);
    SelMapColor = new ColorChooserButton(frame, AppPreferences.FPGA_SELECTED_MAPPED_COLOR);
    SelectMapColor = new ColorChooserButton(frame, AppPreferences.FPGA_SELECTABLE_MAPPED_COLOR);
    SelectColor = new ColorChooserButton(frame, AppPreferences.FPGA_SELECT_COLOR);
    addColorRow(mapPan, 0, MappedLabel, MappedColor);
    addColorRow(mapPan, 1, SelMapLabel, SelMapColor);
    addColorRow(mapPan, 2, SelectMapLabel, SelectMapColor);
    addColorRow(mapPan, 3, SelectLabel, SelectColor);
    return mapPan;
  }

  /**
   * One colour per row, caption left and swatch right, as on the Colors page. Two pairs per row
   * left each swatch pressed against the next caption, with ragged columns.
   */
  private static void addColorRow(JPanel panel, int row, JLabel caption, ColorChooserButton swatch) {
    final var gbc = new GridBagConstraints();
    gbc.gridy = row;
    gbc.gridx = 0;
    gbc.weightx = 1.0;
    gbc.anchor = GridBagConstraints.LINE_START;
    gbc.insets = new Insets(2, 0, 2, 12);
    panel.add(caption, gbc);
    gbc.gridx = 1;
    gbc.weightx = 0.0;
    gbc.anchor = GridBagConstraints.LINE_END;
    gbc.insets = new Insets(2, 0, 2, 0);
    panel.add(swatch, gbc);
  }

  @Override
  public String getHelpText() {
    return S.get("FPGAHelp");
  }

  @Override
  public String getTitle() {
    return S.get("FPGATitle");
  }

  @Override
  public void localeChanged() {
    WorkspaceLabel.setText(S.get("FPGAWorkSpace"));
    WorkSpaceButton.setText(S.get("Browse"));
    EditSelectLabel.setText(S.get("EditColSel"));
    EditHighligtLabel.setText(S.get("EditColHighlight"));
    EditMoveLabel.setText(S.get("EditColMove"));
    EditResizeLabel.setText(S.get("EditColResize"));
    MappedLabel.setText(S.get("MapColor"));
    SelMapLabel.setText(S.get("SelMapCol"));
    SelectMapLabel.setText(S.get("SelectMapCol"));
    SelectLabel.setText(S.get("SelectCol"));
    SuppressGated.setText(S.get("SuppressGatedClock"));
    SuppressOpen.setText(S.get("SuppressOpenInput"));
    vhdlKeywordUpperCase.setText(S.get("VhdlKeywordUpperCase"));
    if (editSection != null) editSection.setTitle(S.get("EditColors"));
    if (mapSection != null) mapSection.setTitle(S.get("MapColors"));
    if (reportSection != null) reportSection.setTitle(S.get("ReporterOptions"));
    if (vhdlSection != null) vhdlSection.setTitle(S.get("VhdlOptions"));
    HDL_Used.getJLabel().setText(S.get("HDLLanguageUsed"));
    AppPreferences.Boards.localeChanged();
  }

  private void selectWorkSpace(Component parentComponent) {
    JFileChooser fc = new JFileChooser(AppPreferences.FPGA_Workspace.get());
    fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
    File test = new File(AppPreferences.FPGA_Workspace.get());
    if (test.exists()) {
      fc.setSelectedFile(test);
    }
    fc.setDialogTitle(S.get("FpgaGuiWorkspacePath"));
    boolean ValidWorkpath = false;
    while (!ValidWorkpath) {
      int retval = fc.showOpenDialog(null);
      if (retval != JFileChooser.APPROVE_OPTION) return;
      if (fc.getSelectedFile().getAbsolutePath().contains(" ")) {
        OptionPane.showMessageDialog(
            parentComponent,
            S.get("FpgaGuiWorkspaceError"),
            S.get("FpgaGuiWorkspacePath"),
            OptionPane.ERROR_MESSAGE);
      } else {
        ValidWorkpath = true;
      }
    }
    File file = fc.getSelectedFile();
    if (file.getPath().endsWith(File.separator)) {
      AppPreferences.FPGA_Workspace.set(file.getPath());
    } else {
      AppPreferences.FPGA_Workspace.set(file.getPath() + File.separator);
    }
  }
}

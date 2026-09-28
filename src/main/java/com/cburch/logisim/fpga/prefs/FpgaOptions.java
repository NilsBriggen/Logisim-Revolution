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
import com.cburch.logisim.gui.generic.SettingsForm;
import com.cburch.logisim.gui.prefs.ColorChooserButton;
import com.cburch.logisim.gui.prefs.OptionsPanel;
import com.cburch.logisim.gui.prefs.PrefOption;
import com.cburch.logisim.gui.prefs.PrefOptionList;
import com.cburch.logisim.gui.prefs.PreferencesFrame;
import com.cburch.logisim.gui.shell.SectionPanel;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.prefs.PrefMonitorBoolean;
import com.cburch.logisim.util.Spacing;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
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
  private final SettingsForm form = new SettingsForm();

  public FpgaOptions(PreferencesFrame frame) {
    super(frame);
    this.frame = frame;
    AppPreferences.getPrefs().addPreferenceChangeListener(myListener);

    WorkSpacePath = new JTextField(16);
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

    // The shared settings form: one label column for the page and for the rows inside its
    // sections, one colour per row rather than two label/swatch pairs to a row.
    final var workspace = new JPanel(new BorderLayout(Spacing.sm(), 0));
    workspace.add(WorkSpacePath, BorderLayout.CENTER);
    workspace.add(WorkSpaceButton, BorderLayout.LINE_END);
    form.addRow(WorkspaceLabel, workspace, true);
    form.addRow(HDL_Used.getJLabel(), HDL_Used.getJComboBox());
    vhdlSection = new SectionPanel(S.get("VhdlOptions"), getVhdlOptions(), true);
    form.addFull(vhdlSection, true);
    form.addFull(AppPreferences.Boards.addRemovePanel(), true);
    reportSection = new SectionPanel(S.get("ReporterOptions"), getReporterOptions(), true);
    form.addFull(reportSection, true);
    editSection = new SectionPanel(S.get("EditColors"), getEditCols(), true);
    form.addFull(editSection, true);
    mapSection = new SectionPanel(S.get("MapColors"), getMapCols(), true);
    form.addFull(mapSection, true);
    setLayout(new BorderLayout());
    add(form, BorderLayout.NORTH);
    localeChanged();
  }

  private JPanel getVhdlOptions() {
    final var isVhdl = AppPreferences.HdlType.get().equals(HdlGeneratorFactory.VHDL);
    vhdlPan = form.createNested();
    vhdlKeywordUpperCase =
        ((PrefMonitorBoolean) AppPreferences.VhdlKeywordsUpperCase).getCheckBox();
    ((SettingsForm) vhdlPan).addFull(vhdlKeywordUpperCase);
    vhdlPan.setEnabled(isVhdl);
    vhdlKeywordUpperCase.setEnabled(isVhdl);
    return vhdlPan;
  }

  private JPanel getReporterOptions() {
    final var nested = form.createNested();
    ReportPan = nested;
    SuppressGated = ((PrefMonitorBoolean) AppPreferences.SuppressGatedClockWarnings).getCheckBox();
    nested.addFull(SuppressGated);
    SuppressOpen = ((PrefMonitorBoolean) AppPreferences.SuppressOpenPinWarnings).getCheckBox();
    nested.addFull(SuppressOpen);
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
    final var nested = form.createNested();
    editPan = nested;
    EditSelectColor = new ColorChooserButton(frame, AppPreferences.FPGA_DEFINE_COLOR);
    nested.addRow(EditSelectLabel, EditSelectColor);
    EditHighligtColor = new ColorChooserButton(frame, AppPreferences.FPGA_DEFINE_HIGHLIGHT_COLOR);
    nested.addRow(EditHighligtLabel, EditHighligtColor);
    EditMoveColor = new ColorChooserButton(frame, AppPreferences.FPGA_DEFINE_MOVE_COLOR);
    nested.addRow(EditMoveLabel, EditMoveColor);
    EditResizeColor = new ColorChooserButton(frame, AppPreferences.FPGA_DEFINE_RESIZE_COLOR);
    nested.addRow(EditResizeLabel, EditResizeColor);
    return editPan;
  }

  private JPanel getMapCols() {
    final var nested = form.createNested();
    mapPan = nested;
    MappedColor = new ColorChooserButton(frame, AppPreferences.FPGA_MAPPED_COLOR);
    nested.addRow(MappedLabel, MappedColor);
    SelMapColor = new ColorChooserButton(frame, AppPreferences.FPGA_SELECTED_MAPPED_COLOR);
    nested.addRow(SelMapLabel, SelMapColor);
    SelectMapColor = new ColorChooserButton(frame, AppPreferences.FPGA_SELECTABLE_MAPPED_COLOR);
    nested.addRow(SelectMapLabel, SelectMapColor);
    SelectColor = new ColorChooserButton(frame, AppPreferences.FPGA_SELECT_COLOR);
    nested.addRow(SelectLabel, SelectColor);
    return mapPan;
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

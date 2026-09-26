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

import com.cburch.logisim.fpga.gui.FpgaCommander;
import com.cburch.logisim.fpga.settings.VendorSoftware;
import com.cburch.logisim.gui.generic.SettingsForm;
import com.cburch.logisim.gui.prefs.OptionsPanel;
import com.cburch.logisim.gui.prefs.PrefOption;
import com.cburch.logisim.gui.prefs.PrefOptionList;
import com.cburch.logisim.gui.prefs.PreferencesFrame;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.util.Softwares;
import com.cburch.logisim.util.Spacing;
import com.cburch.logisim.util.StringGetter;
import com.cburch.logisim.util.UiFonts;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;

public class SoftwaresOptions extends OptionsPanel {

  private class MyListener implements ActionListener, PreferenceChangeListener {

    @Override
    public void actionPerformed(ActionEvent ae) {
      final var source = ae.getSource();

      if (source == questaPathButton) {
        Softwares.setQuestaPath(getPreferencesFrame());
      } else if (source == questaValidationCheckBox) {
        AppPreferences.QUESTA_VALIDATION.setBoolean(questaValidationCheckBox.isSelected());
      } else if (source == quartusPathButton) {
        FpgaCommander.selectToolPath(VendorSoftware.VENDOR_ALTERA);
      } else if (source == isePathButton) {
        FpgaCommander.selectToolPath(VendorSoftware.VENDOR_XILINX);
      } else if (source == vivadoPathButton) {
        FpgaCommander.selectToolPath(VendorSoftware.VENDOR_VIVADO);
      } else if (source == openfpgaPathButton) {
        FpgaCommander.selectToolPath(VendorSoftware.VENDOR_OPENFPGA);
      }
    }

    @Override
    public void preferenceChange(PreferenceChangeEvent pce) {
      String property = pce.getKey();

      if (property.equals(AppPreferences.QUESTA_PATH.getIdentifier())) {
        questaPathField.setText(AppPreferences.QUESTA_PATH.get());
      } else if (property.equals(AppPreferences.QUESTA_VALIDATION.getIdentifier())) {
        questaValidationCheckBox.setSelected(AppPreferences.QUESTA_VALIDATION.getBoolean());
      } else if (property.equals(AppPreferences.QuartusToolPath.getIdentifier())) {
        quartusPathField.setText(AppPreferences.QuartusToolPath.get());
      } else if (property.equals(AppPreferences.ISEToolPath.getIdentifier())) {
        isePathField.setText(AppPreferences.ISEToolPath.get());
      } else if (property.equals(AppPreferences.VivadoToolPath.getIdentifier())) {
        vivadoPathField.setText(AppPreferences.VivadoToolPath.get());
      } else if (property.equals(AppPreferences.OpenFpgaToolPath.getIdentifier())) {
        openfpgaPathField.setText(AppPreferences.OpenFpgaToolPath.get());
      }
    }
  }

  private static final long serialVersionUID = 1L;

  private final MyListener myListener = new MyListener();

  private final JCheckBox questaValidationCheckBox = new JCheckBox();
  // The check box's caption wraps, so a long translation or a large scale never clips it.
  private final JTextArea questaValidationText = SettingsForm.createWrappingText("");
  private final PrefOptionList vhdlStandard =
      new PrefOptionList(
          AppPreferences.VHDL_STANDARD,
          S.getter("softwaresVhdlStandardLabel"),
          new PrefOption[] {
              new PrefOption(AppPreferences.VHDL_STANDARD_1993, fixedString("VHDL-1993")),
              new PrefOption(AppPreferences.VHDL_STANDARD_2002, fixedString("VHDL-2002")),
              new PrefOption(AppPreferences.VHDL_STANDARD_2008, fixedString("VHDL-2008")),
          });
  private final JLabel questaPathLabel = new JLabel();
  private final JTextField questaPathField = new JTextField(16);
  private final JButton questaPathButton = new JButton();
  private final JLabel quartusPathLabel = new JLabel();
  private final JTextField quartusPathField = new JTextField(16);
  private final JButton quartusPathButton = new JButton();
  private final JLabel isePathLabel = new JLabel();
  private final JTextField isePathField = new JTextField(16);
  private final JButton isePathButton = new JButton();
  private final JLabel vivadoPathLabel = new JLabel();
  private final JTextField vivadoPathField = new JTextField(16);
  private final JButton vivadoPathButton = new JButton();
  private final JLabel openfpgaPathLabel = new JLabel();
  private final JTextField openfpgaPathField = new JTextField(16);
  private final JButton openfpgaPathButton = new JButton();

  public SoftwaresOptions(PreferencesFrame window) {
    super(window);

    questaValidationCheckBox.addActionListener(myListener);
    questaValidationText.setFont(UiFonts.body());
    questaValidationText.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent event) {
            if (javax.swing.SwingUtilities.isLeftMouseButton(event)) {
              questaValidationCheckBox.doClick();
            }
          }
        });
    final var validation = new JPanel(new BorderLayout(Spacing.xs(), 0));
    validation.add(questaValidationCheckBox, BorderLayout.WEST);
    validation.add(questaValidationText, BorderLayout.CENTER);
    questaPathButton.addActionListener(myListener);
    quartusPathButton.addActionListener(myListener);
    isePathButton.addActionListener(myListener);
    vivadoPathButton.addActionListener(myListener);
    openfpgaPathButton.addActionListener(myListener);
    AppPreferences.getPrefs().addPreferenceChangeListener(myListener);

    final var form = new SettingsForm();
    form.addFull(validation, true);
    form.addRow(vhdlStandard.getJLabel(), vhdlStandard.getJComboBox());
    addPathRow(form, questaPathLabel, questaPathField, questaPathButton);
    addPathRow(form, quartusPathLabel, quartusPathField, quartusPathButton);
    addPathRow(form, isePathLabel, isePathField, isePathButton);
    addPathRow(form, vivadoPathLabel, vivadoPathField, vivadoPathButton);
    addPathRow(form, openfpgaPathLabel, openfpgaPathField, openfpgaPathButton);
    setLayout(new BorderLayout());
    add(form, BorderLayout.NORTH);

    questaValidationCheckBox.setSelected(AppPreferences.QUESTA_VALIDATION.getBoolean());

    quartusPathField.setText(AppPreferences.QuartusToolPath.get());
    quartusPathField.setEditable(false);
    isePathField.setText(AppPreferences.ISEToolPath.get());
    isePathField.setEditable(false);
    vivadoPathField.setText(AppPreferences.VivadoToolPath.get());
    vivadoPathField.setEditable(false);
    questaPathField.setText(AppPreferences.QUESTA_PATH.get());
    questaPathField.setEditable(false);
    openfpgaPathField.setText(AppPreferences.OpenFpgaToolPath.get());
    openfpgaPathField.setEditable(false);
  }

  @Override
  public void updateUI() {
    super.updateUI();
    if (questaValidationText != null) questaValidationText.setFont(UiFonts.body());
  }

  /** A path: its label, then the field and its browse button in the control column. */
  private static void addPathRow(
      SettingsForm form, JLabel label, JTextField field, JButton browse) {
    final var row = new JPanel(new BorderLayout(Spacing.sm(), 0));
    row.add(field, BorderLayout.CENTER);
    row.add(browse, BorderLayout.LINE_END);
    label.setLabelFor(field);
    form.addRow(label, row, true);
  }

  @Override
  public String getHelpText() {
    return S.get("softwaresHelp");
  }

  @Override
  public String getTitle() {
    return S.get("softwaresTitle");
  }

  @Override
  public void localeChanged() {
    final var validationLabel = S.get("softwaresQuestaValidationLabel");
    questaValidationText.setText(validationLabel);
    questaValidationCheckBox.getAccessibleContext().setAccessibleName(validationLabel);
    vhdlStandard.getJLabel().setText(S.get("softwaresVhdlStandardLabel"));
    questaPathButton.setText(S.get("softwaresQuestaPathButton"));
    questaPathLabel.setText(S.get("softwaresQuestaPathLabel"));
    quartusPathButton.setText(S.get("softwaresQuestaPathButton"));
    quartusPathLabel.setText(S.get("QuartusToolPath"));
    isePathButton.setText(S.get("softwaresQuestaPathButton"));
    isePathLabel.setText(S.get("ISEToolPath"));
    vivadoPathButton.setText(S.get("softwaresQuestaPathButton"));
    vivadoPathLabel.setText(S.get("VivadoToolPath"));
    openfpgaPathButton.setText(S.get("softwaresQuestaPathButton"));
    openfpgaPathLabel.setText(S.get("openfpgaToolPath"));
  }

  private static StringGetter fixedString(String value) {
    return new StringGetter() {
      @Override
      public String toString() {
        return value;
      }
    };
  }
}

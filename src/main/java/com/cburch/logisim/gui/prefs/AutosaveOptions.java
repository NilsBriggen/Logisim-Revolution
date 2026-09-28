package com.cburch.logisim.gui.prefs;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.gui.generic.SettingsForm;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.prefs.PrefMonitorBoolean;
import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JTextField;

public class AutosaveOptions extends OptionsPanel {
  private static final long serialVersionUID = 1L;
  private final MyListener myListener = new MyListener();
  private final JCheckBox enableAutosaves;
  private final JLabel autosaveIntervalText = new JLabel();
  private final JTextField autosaveInterval = new JTextField(6);

  public AutosaveOptions(PreferencesFrame window) {
    super(window);

    enableAutosaves = ((PrefMonitorBoolean) AppPreferences.AUTOSAVE_ENABLED).getCheckBox();
    autosaveInterval.addActionListener(myListener);
    // Committed when the field is left too, not only on Enter: a value typed and then left by
    // closing the window or clicking elsewhere used to be silently dropped.
    autosaveInterval.addFocusListener(
        new FocusAdapter() {
          @Override
          public void focusLost(FocusEvent event) {
            myListener.commit();
          }
        });

    final var form = new SettingsForm();
    form.addFull(enableAutosaves);
    form.addRow(autosaveIntervalText, autosaveInterval);
    setLayout(new BorderLayout());
    add(form, BorderLayout.NORTH);

    myListener.computeEnable();
    myListener.setContent();

    AppPreferences.addPropertyChangeListener(AppPreferences.AUTOSAVE_ENABLE, myListener);
  }

  /** Commits the typed interval, as leaving the field does; for tests. */
  void commitInterval() {
    myListener.commit();
  }

  JTextField getIntervalField() {
    return autosaveInterval;
  }

  @Override
  public String getHelpText() {
    return S.get("autosaveHelp");
  }

  @Override
  public String getTitle() {
    return S.get("autosaveTitle");
  }

  @Override
  public void localeChanged() {
    enableAutosaves.setText(S.get("autosaveEnabled"));
    autosaveIntervalText.setText(S.get("autosaveInterval"));
  }

  private class MyListener implements ActionListener, PropertyChangeListener {

    @Override
    public void propertyChange(PropertyChangeEvent evt) {
      if (evt.getPropertyName().equals(AppPreferences.AUTOSAVE_ENABLE)) {
        computeEnable();
      }
    }

    @Override
    public void actionPerformed(ActionEvent e) {
      commit();
    }

    /** Saves the typed interval, or puts the saved one back if the text is not a valid one. */
    void commit() {
      var val = -1;
      try {
        val = Integer.parseInt(autosaveInterval.getText());
      } catch (NumberFormatException ignored) {
      }
      if (val <= 0 || val > 10000) {
        setContent();
      } else if (val != AppPreferences.AUTOSAVE_INTERVAL.get()) {
        AppPreferences.AUTOSAVE_INTERVAL.set(val);
      }
    }

    private void computeEnable() {
      final var enable = AppPreferences.AUTOSAVE_ENABLED.getBoolean();
      autosaveIntervalText.setEnabled(enable);
      autosaveInterval.setEnabled(enable);
    }

    private void setContent() {
      autosaveInterval.setText(AppPreferences.AUTOSAVE_INTERVAL.get().toString());
    }
  }
}

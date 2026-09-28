/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.prefs;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.gui.generic.SettingsForm;
import com.cburch.logisim.prefs.AppPreferences;
import java.awt.BorderLayout;
import javax.swing.JTextArea;

class ExperimentalOptions extends OptionsPanel {
  private static final long serialVersionUID = 1L;
  private final JTextArea accelRestart;
  private final PrefOptionList accel;
  private final JTextArea simRestart;
  private final PrefOptionList simQueue;

  public ExperimentalOptions(PreferencesFrame window) {
    super(window);

    accel = new PrefOptionList(
        AppPreferences.GRAPHICS_ACCELERATION,
        S.getter("accelLabel"),
        new PrefOption[]{
            new PrefOption(AppPreferences.ACCEL_DEFAULT, S.getter("accelDefault")),
            new PrefOption(AppPreferences.ACCEL_NONE, S.getter("accelNone")),
            new PrefOption(AppPreferences.ACCEL_OPENGL, S.getter("accelOpenGL")),
            new PrefOption(AppPreferences.ACCEL_D3D, S.getter("accelD3D")),
            new PrefOption(AppPreferences.ACCEL_METAL, S.getter("accelMetal")),
        }
    );

    simQueue = new PrefOptionList(
        AppPreferences.SIMULATION_QUEUE,
        S.getter("simQueueLabel"),
        new PrefOption[]{
            new PrefOption(AppPreferences.SIM_QUEUE_DEFAULT, S.getter("simQueueDefault")),
            new PrefOption(AppPreferences.SIM_QUEUE_PRIORITY, S.getter("simQueuePriority")),
            new PrefOption(AppPreferences.SIM_QUEUE_SPLAY, S.getter("simQueueSplay")),
            new PrefOption(AppPreferences.SIM_QUEUE_LINKED, S.getter("simQueueLinked")),
            new PrefOption(AppPreferences.SIM_QUEUE_LIST_OF_QUEUES, S.getter("simQueueListOfQueues")),
            new PrefOption(AppPreferences.SIM_QUEUE_TREE_OF_QUEUES, S.getter("simQueueTreeOfQueues"))
        }
    );
    final var form = new SettingsForm();
    form.addRow(accel.getJLabel(), accel.getJComboBox());
    accelRestart = form.addHint(S.get("accelRestartLabel"));
    form.addRow(simQueue.getJLabel(), simQueue.getJComboBox());
    simRestart = form.addHint(S.get("simRestartLabel"));
    setLayout(new BorderLayout());
    add(form, BorderLayout.NORTH);
  }

  @Override
  public String getHelpText() {
    return S.get("experimentHelp");
  }

  @Override
  public String getTitle() {
    return S.get("experimentTitle");
  }

  @Override
  public void localeChanged() {
    accel.localeChanged();
    simQueue.localeChanged();
    accelRestart.setText(S.get("accelRestartLabel"));
    simRestart.setText(S.get("simRestartLabel"));
  }
}

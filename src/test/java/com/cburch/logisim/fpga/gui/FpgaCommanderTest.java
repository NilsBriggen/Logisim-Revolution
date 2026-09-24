/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.fpga.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.fpga.hdlgenerator.HdlGeneratorFactory;
import com.cburch.logisim.gui.theme.AppIcons;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

class FpgaCommanderTest {
  @Test
  void svgStopIconSurvivesStartCancelCompleteAndRetry() throws Exception {
    SwingUtilities.invokeAndWait(
        () -> {
          final var icon = AppIcons.get(AppIcons.Id.CLOSE);
          final var stopButton = new JButton(icon);
          final var executeButton = new JButton();
          final var actionCommands = new JComboBox<>();
          for (var run = 0; run < 2; run++) {
            FpgaCommander.setDownloadControlsRunning(
                stopButton, true, executeButton, actionCommands);
            assertTrue(stopButton.isEnabled());
            assertFalse(executeButton.isEnabled());
            assertFalse(actionCommands.isEnabled());

            // A stop request disables cancellation while the worker finishes.
            stopButton.setEnabled(false);
            FpgaCommander.setDownloadControlsRunning(
                stopButton, false, executeButton, actionCommands);
            assertFalse(stopButton.isEnabled());
            assertTrue(executeButton.isEnabled());
            assertTrue(actionCommands.isEnabled());
            assertSame(icon, stopButton.getIcon());
          }
        });
  }

  @Test
  void failedWorkerReportsErrorAndCompletesOnEdt() throws Exception {
    final var events = new ArrayList<String>();
    final var failure = new IllegalStateException("Cannot prepare HDL output");
    FpgaCommander.runDownloadTask(
        () -> {
          throw failure;
        },
        error -> {
          assertTrue(SwingUtilities.isEventDispatchThread());
          assertSame(failure, error);
          events.add("failure");
        },
        () -> {
          assertTrue(SwingUtilities.isEventDispatchThread());
          events.add("complete");
        });
    SwingUtilities.invokeAndWait(() -> assertEquals(List.of("failure", "complete"), events));
  }

  @Test
  void successfulWorkerCompletesExactlyOnceOnEdt() throws Exception {
    final var events = new ArrayList<String>();
    FpgaCommander.runDownloadTask(
        () -> {},
        error -> events.add("failure"),
        () -> {
          assertTrue(SwingUtilities.isEventDispatchThread());
          events.add("complete");
        });
    SwingUtilities.invokeAndWait(() -> assertEquals(List.of("complete"), events));
  }

  @Test
  void hdlControlsCanBeReenabledAfterSelectingConcreteHdl() {
    final var actionCommands = new JComboBox<>();
    final var executeButton = new JButton();

    FpgaCommander.setHdlControlsEnabled(
        actionCommands, executeButton, true, HdlGeneratorFactory.NONE);
    assertFalse(actionCommands.isEnabled());
    assertFalse(executeButton.isEnabled());

    FpgaCommander.setHdlControlsEnabled(
        actionCommands, executeButton, true, HdlGeneratorFactory.VERILOG);
    assertTrue(actionCommands.isEnabled());
    assertTrue(executeButton.isEnabled());

    FpgaCommander.setHdlControlsEnabled(
        actionCommands, executeButton, true, HdlGeneratorFactory.VHDL);
    assertTrue(actionCommands.isEnabled());
    assertTrue(executeButton.isEnabled());

    FpgaCommander.setHdlControlsEnabled(
        actionCommands, executeButton, false, HdlGeneratorFactory.VERILOG);
    assertFalse(actionCommands.isEnabled());
    assertFalse(executeButton.isEnabled());
  }
}

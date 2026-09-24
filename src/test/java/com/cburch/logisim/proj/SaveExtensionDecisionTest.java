/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.proj;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.File;
import javax.swing.JOptionPane;
import org.junit.jupiter.api.Test;

class SaveExtensionDecisionTest {
  private final Object[] options = {new Object(), new Object(), new Object()};
  private final File selected = new File("scratch.txt");

  @Test
  void dismissalAndUnknownChoicesNeverProduceASaveDestination() {
    assertNull(ProjectActions.resolveSaveExtension(selected, null, options));
    assertNull(ProjectActions.resolveSaveExtension(selected, JOptionPane.UNINITIALIZED_VALUE, options));
    assertNull(ProjectActions.resolveSaveExtension(selected, JOptionPane.CLOSED_OPTION, options));
    assertNull(ProjectActions.resolveSaveExtension(selected, new Object(), options));
  }

  @Test
  void onlyExplicitReplaceAddAndKeepChoicesProduceDestinations() {
    assertEquals(new File("scratch.circ"),
        ProjectActions.resolveSaveExtension(selected, options[0], options));
    assertEquals(new File("scratch.txt.circ"),
        ProjectActions.resolveSaveExtension(selected, options[1], options));
    assertSame(selected, ProjectActions.resolveSaveExtension(selected, options[2], options));
  }
}

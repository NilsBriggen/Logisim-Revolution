/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.proj;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;

class ProjectCloseConfirmationTest {
  @Test
  void cleanProjectNeedsNeitherPromptNorSave() {
    assertTrue(ProjectCloseConfirmation.confirm(false, () -> fail("prompted"), () -> fail("saved")));
  }

  @Test
  void dismissalAndCancelNeverSaveOrPermitClosing() {
    for (final var result : new int[] {-1, 2, 3}) {
      assertFalse(ProjectCloseConfirmation.confirm(true, () -> result, () -> fail("saved")));
    }
  }

  @Test
  void cancelledOrFailedSaveKeepsTheProjectOpen() {
    assertFalse(ProjectCloseConfirmation.confirm(true, () -> 0, () -> false));
  }

  @Test
  void successfulSavePermitsClosing() {
    assertTrue(ProjectCloseConfirmation.confirm(true, () -> 0, () -> true));
  }

  @Test
  void explicitDiscardPermitsClosingWithoutSaving() {
    assertTrue(ProjectCloseConfirmation.confirm(true, () -> 1, () -> fail("saved")));
  }
}

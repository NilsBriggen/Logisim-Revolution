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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import org.junit.jupiter.api.Test;

class ProjectActionsNewTest {

  @Test
  void newProjectFromTheWelcomeScreenReusesTheUntouchedBlankProject() {
    final var file = LogisimFile.createNew(new Loader(null), null);
    final var project = new Project(file);
    try {
      project.setStartupScreen(true);
      assertTrue(ProjectActions.isUntouchedBlank(project));

      assertSame(project, ProjectActions.doNewReplacingBlank(project));
      assertFalse(project.isStartupScreen(), "a later Open must not load over the new project");
    } finally {
      file.retireAutosaveThread();
      project.getSimulator().shutDown();
    }
  }

  @Test
  void changedProjectIsNotBlank() {
    final var file = LogisimFile.createNew(new Loader(null), null);
    final var project = new Project(file);
    try {
      project.setForcedDirty();
      assertFalse(ProjectActions.isUntouchedBlank(project));
      assertFalse(ProjectActions.isUntouchedBlank(null));
    } finally {
      file.retireAutosaveThread();
      project.getSimulator().shutDown();
    }
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.start;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.file.LoadFailedException;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.ProjectActions;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loads a project for a command-line run and remembers whether loading reported any error.
 *
 * <p>A project that loads "with errors" (unknown components, bad attribute values, missing
 * libraries...) silently loses parts of the circuit, so its simulation results cannot be trusted.
 * The command-line modes use this loader to refuse such a project with {@link
 * ExitCode#LOAD_ERROR} instead of printing wrong answers. The errors themselves still reach
 * standard error through {@link Loader#showError}, which logs instead of opening a dialog when
 * there is no GUI.
 */
class CliLoader extends Loader {
  private static final Logger logger = LoggerFactory.getLogger(CliLoader.class);

  private final List<String> errors = new ArrayList<>();

  CliLoader() {
    super(null);
  }

  @Override
  public void showError(String description) {
    synchronized (errors) {
      errors.add(description);
    }
    super.showError(description);
  }

  /** Returns whether any error was reported while loading. */
  boolean hasErrors() {
    synchronized (errors) {
      return !errors.isEmpty();
    }
  }

  /**
   * Loads {@code file} into a project without opening a window.
   *
   * <p>Failures are logged with their cause. The caller should stop with {@link
   * ExitCode#LOAD_ERROR} when this returns null.
   *
   * @return The project, or null if the file could not be loaded or loading reported errors.
   */
  Project openProject(File file, Map<File, File> substitutions) {
    final Project project;
    try {
      project = ProjectActions.doOpenNoWindow(this, file, substitutions);
    } catch (LoadFailedException e) {
      logger.error("{}", S.get("cliLoadFailed", file.getPath(), e.getMessage()));
      return null;
    }
    if (hasErrors()) {
      logger.error("{}", S.get("cliLoadHadErrors", file.getPath()));
      return null;
    }
    return project;
  }
}

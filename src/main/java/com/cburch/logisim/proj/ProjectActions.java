/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.proj;

import static com.cburch.logisim.proj.Strings.S;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.file.LoadFailedException;
import com.cburch.logisim.file.LoadedLibrary;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LoaderException;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.file.LogisimFileActions;
import com.cburch.logisim.file.ProjectBundlePaths;
import com.cburch.logisim.generated.BuildInfo;
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.gui.generic.WaitCursor;
import com.cburch.logisim.gui.main.Frame;
import com.cburch.logisim.gui.start.SplashScreen;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.tools.LibraryTools;
import com.cburch.logisim.util.JFileChoosers;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Component;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;

public final class ProjectActions {
  private ProjectActions() {}

  private static class CreateFrame implements Runnable {
    private final Loader loader;
    private final Project proj;
    private final boolean isStartupScreen;

    public CreateFrame(Loader loader, Project proj, boolean isStartup) {
      this.loader = loader;
      this.proj = proj;
      this.isStartupScreen = isStartup;
    }

    @Override
    public void run() {
      try {
        final var frame = createFrame(null, proj);
        frame.setVisible(true);
        frame.toFront();
        frame.getCanvas().requestFocus();
        loader.setParent(frame);
        if (isStartupScreen) {
          proj.setStartupScreen(true);
        }
      } catch (Exception e) {
        // The stack trace belongs behind the Details button, not in front of the user as the
        // entire message.
        OptionPane.showError(null, S.get("windowCreateFailedTitle"), S.get("windowCreateFailed"), e);
        // Quitting would also discard the unsaved work of every other open window.
        final var othersOpen =
            Projects.getOpenProjects().stream().anyMatch(other -> other != proj);
        if (!othersOpen) System.exit(-1);
      }
    }
  }

  private static Project completeProject(
      SplashScreen monitor, Loader loader, LogisimFile file, boolean isStartup) {
    if (monitor != null) monitor.setProgress(SplashScreen.PROJECT_CREATE);
    final var ret = new Project(file);
    if (monitor != null) monitor.setProgress(SplashScreen.FRAME_CREATE);
    SwingUtilities.invokeLater(new CreateFrame(loader, ret, isStartup));
    updatecircs(file, ret);
    return ret;
  }

  private static LogisimFile createEmptyFile(Loader loader, Project proj) {
    InputStream templReader = AppPreferences.getEmptyTemplate().createStream();
    LogisimFile file;
    try {
      file = loader.openLogisimFile(templReader);
    } catch (Exception t) {
      file = LogisimFile.createNew(loader, proj);
      file.addCircuit(new Circuit("main", file, proj));
    } finally {
      try {
        templReader.close();
      } catch (IOException ignored) {
      }
    }
    return file;
  }

  private static Frame createFrame(Project sourceProject, Project newProject) {
    if (sourceProject != null) {
      final var frame = sourceProject.getFrame();
      if (frame != null) {
        frame.savePreferences();
      }
    }
    final var newFrame = new Frame(newProject);
    newProject.setFrame(newFrame);
    return newFrame;
  }

  public static LogisimFile createNewFile(Project baseProject) {
    final var parent = (baseProject == null) ? null : baseProject.getFrame();
    final var loader = new Loader(parent);
    final var templReader = AppPreferences.getTemplate().createStream();
    LogisimFile file;
    try {
      file = loader.openLogisimFile(templReader);
    } catch (IOException ex) {
      displayException(parent, ex);
      file = null;
    } finally {
      try {
        templReader.close();
      } catch (IOException ignored) {
        // Do nothing.
      }
    }
    // A template that cannot be parsed yields null (its error was already shown).
    if (file == null) file = createEmptyFile(loader, baseProject);
    return file;
  }

  private static void displayException(Component parent, Exception ex) {
    String msg = S.get("templateOpenError", ex.toString());
    String ttl = S.get("templateOpenErrorTitle");
    OptionPane.showMessageDialog(parent, msg, ttl, OptionPane.ERROR_MESSAGE);
  }

  public static Project doNew(Project baseProject) {
    final var file = createNewFile(baseProject);
    final var newProj = new Project(file);
    final var frame = createFrame(baseProject, newProj);
    frame.setVisible(true);
    frame.getCanvas().requestFocus();
    newProj.getLogisimFile().getLoader().setParent(frame);
    updatecircs(file, newProj);
    return newProj;
  }

  public static Project doNew(SplashScreen monitor) {
    return doNew(monitor, false);
  }

  public static Project doNew(SplashScreen monitor, boolean isStartupScreen) {
    if (monitor != null) monitor.setProgress(SplashScreen.FILE_CREATE);
    final var loader = new Loader(monitor);
    final var templReader = AppPreferences.getTemplate().createStream();
    LogisimFile file = null;
    try {
      file = loader.openLogisimFile(templReader);
    } catch (IOException ex) {
      displayException(monitor, ex);
    } finally {
      try {
        templReader.close();
      } catch (IOException ignored) {
      }
    }
    if (file == null) file = createEmptyFile(loader, null);
    return completeProject(monitor, loader, file, isStartupScreen);
  }

  public static void doMerge(Component parent, Project baseProject) {
    JFileChooser chooser;
    if (baseProject != null) {
      final var oldLoader = baseProject.getLogisimFile().getLoader();
      chooser = oldLoader.createChooser();
      if (oldLoader.getMainFile() != null) {
        chooser.setSelectedFile(oldLoader.getMainFile());
      }
    } else {
      chooser = JFileChoosers.create();
    }
    chooser.setFileFilter(Loader.LOGISIM_FILTER);
    chooser.setDialogTitle(S.get("FileMergeItem"));

    LogisimFile mergelib;
    int returnVal = chooser.showOpenDialog(parent);
    if (returnVal != JFileChooser.APPROVE_OPTION) return;
    final var selected = chooser.getSelectedFile();
    final var loader = new Loader(baseProject == null ? parent : baseProject.getFrame());
    try {
      mergelib = loader.openLogisimFile(selected);
      if (mergelib == null) return;
    } catch (LoadFailedException ex) {
      if (!ex.isShown()) {
        OptionPane.showError(parent, S.get("FileMergeErrorItem"), ex.getMessage(), ex.getCause());
      }
      return;
    }
    final var circuits = mergelib.getCircuits();
    List<Circuit> circuitsToMerge = null;
    boolean includeDependencies = true;

    if (!circuits.isEmpty()) {
      final var depMap = new HashMap<Circuit, Set<Circuit>>();
      for (final var circ : circuits) {
        depMap.put(circ, LogisimFileActions.getCircuitDependencies(circ, mergelib));
      }

      final var list = new JList<>(circuits.toArray(new Circuit[0]));
      list.setCellRenderer(new DefaultListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(
            JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
          super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
          if (value instanceof Circuit circ) {
            final var deps = depMap.get(circ);
            if (deps != null && !deps.isEmpty()) {
              final var names = deps.stream().map(Circuit::getName).collect(Collectors.joining(", "));
              setText(circ.getName() + " " + S.get("FileMergeDepsCount", names));
              setToolTipText(S.get("FileMergeDepsCount", names));
            } else {
              setText(circ.getName());
              setToolTipText(null);
            }
          }
          return this;
        }
      });
      list.setSelectionInterval(0, circuits.size() - 1);
      final var scrollPane = new JScrollPane(list);
      scrollPane.setPreferredSize(new Dimension(520, 220));

      final var panel = new JPanel(new BorderLayout(5, 5));
      panel.add(new JLabel(S.get("FileMergeSelectPrompt")), BorderLayout.NORTH);
      panel.add(scrollPane, BorderLayout.CENTER);

      final var infoLabel = new JLabel(" ");
      infoLabel.setFont(infoLabel.getFont().deriveFont(Font.ITALIC));

      final var depsPanel = new JPanel(new BorderLayout(5, 5));
      final var depsCheck = new JCheckBox(S.get("FileMergeIncludeDeps"), true);
      depsPanel.add(depsCheck, BorderLayout.NORTH);
      depsPanel.add(infoLabel, BorderLayout.SOUTH);

      final Runnable updateInfoLabel = () -> {
        final var selectedInList = list.getSelectedValuesList();
        if (selectedInList.isEmpty()) {
          infoLabel.setText(" ");
          return;
        }
        if (depsCheck.isSelected()) {
          final var allToMerge = new LinkedHashSet<Circuit>();
          for (final var c : selectedInList) {
            allToMerge.add(c);
            allToMerge.addAll(depMap.getOrDefault(c, Collections.emptySet()));
          }
          final int mainCount = selectedInList.size();
          final int totalCount = allToMerge.size();
          final int depsCount = totalCount - mainCount;
          if (depsCount > 0) {
            infoLabel.setText(S.get("FileMergeSummaryWithDeps", totalCount, mainCount, depsCount));
          } else {
            infoLabel.setText(S.get("FileMergeSummarySelected", totalCount));
          }
        } else {
          infoLabel.setText(S.get("FileMergeSummarySelected", selectedInList.size()));
        }
      };

      list.addListSelectionListener(e -> updateInfoLabel.run());
      depsCheck.addItemListener(e -> updateInfoLabel.run());
      updateInfoLabel.run();

      final var mainPanel = new JPanel(new BorderLayout(0, 10));
      mainPanel.setPreferredSize(new Dimension(520, 300));
      mainPanel.add(panel, BorderLayout.CENTER);
      mainPanel.add(depsPanel, BorderLayout.SOUTH);

      final var parentWindow = (baseProject != null && baseProject.getFrame() != null)
          ? baseProject.getFrame()
          : parent;

      final var result = OptionPane.showConfirmDialog(
          parentWindow,
          mainPanel,
          S.get("FileMergeItem"),
          OptionPane.OK_CANCEL_OPTION,
          OptionPane.PLAIN_MESSAGE);

      if (result != OptionPane.OK_OPTION) return;

      includeDependencies = depsCheck.isSelected();
      circuitsToMerge = list.getSelectedValuesList();
      if (circuitsToMerge.isEmpty()) return;
    }

    try {
      baseProject.doAction(LogisimFileActions.mergeFile(
          mergelib,
          baseProject.getLogisimFile(),
          circuitsToMerge,
          includeDependencies));
    } catch (LoaderException ex) {
      if (!ex.isShown()) {
        OptionPane.showMessageDialog(
            parent,
            S.get("fileMergeError", ex.getMessage()),
            S.get("FileMergeErrorItem"),
            OptionPane.ERROR_MESSAGE);
      }
    }
  }

  private static void updatecircs(LogisimFile lib, Project proj) {
    for (final var circ : lib.getCircuits()) {
      circ.setProject(proj);
    }
    for (final var libs : lib.getLibraries()) {
      if (libs instanceof LoadedLibrary test) {
        if (test.getBase() instanceof LogisimFile lsFile) {
          updatecircs(lsFile, proj);
        }
      }
    }
  }

  public static Project doOpen(Component parent, Project baseProject) {
    JFileChooser chooser;
    if (baseProject != null) {
      final var oldLoader = baseProject.getLogisimFile().getLoader();
      chooser = oldLoader.createChooser();
      if (oldLoader.getMainFile() != null) {
        chooser.setSelectedFile(oldLoader.getMainFile());
      }
    } else {
      chooser = JFileChoosers.create();
    }
    chooser.setFileFilter(Loader.LOGISIM_FILTER);
    chooser.setDialogTitle(S.get("FileOpenItem"));

    final var returnVal = chooser.showOpenDialog(parent);
    if (returnVal != JFileChooser.APPROVE_OPTION) return null;
    final var selected = chooser.getSelectedFile();
    if (selected == null) return null;
    return doOpen(parent, baseProject, selected);
  }

  /**
   * Opens {@code file} the way the recent-files menu does: in a new window, closing {@code
   * current} afterwards if it is an untouched, unsaved blank project, so that opening a file from
   * the empty window Logisim starts with does not leave that empty window behind.
   *
   * @param parent component the open dialogs belong to, or {@code null}
   * @param baseProject project whose libraries seed the loader, or {@code null}
   * @param current project whose window is closed if it is blank, or {@code null}
   * @return the opened project, or {@code null} if it could not be opened
   */
  public static Project doOpenReplacingBlank(
      Component parent, Project baseProject, Project current, File file) {
    final var opened = doOpen(parent, baseProject, file);
    if (opened != null && opened != current && isUntouchedBlank(current)) {
      current.getFrame().dispose();
    }
    return opened;
  }

  /**
   * Starts a new project the way the welcome screen does: in {@code current}'s own window when it
   * holds only the untouched, unsaved blank project Logisim starts with, and in a new window
   * otherwise. The blank project was made from the same template, so reusing it gives the same
   * result without leaving an empty window behind.
   *
   * @param current project whose window asked for a new project
   * @return {@code current} if it was reused, otherwise the project in the new window
   */
  public static Project doNewReplacingBlank(Project current) {
    if (!isUntouchedBlank(current)) return doNew(current);
    // It is the user's project now, so a later Open must not load over it.
    current.setStartupScreen(false);
    return current;
  }

  /** Whether {@code project} is an unsaved project that nobody has changed yet. */
  static boolean isUntouchedBlank(Project project) {
    if (project == null || project.isFileDirty()) return false;
    final var loader = project.getLogisimFile().getLoader();
    return loader != null && loader.getMainFile() == null;
  }

  public static Project doOpen(Component parent, Project baseProject, File f) {
    var proj = Projects.findProjectFor(f);
    Loader loader = null;
    if (proj != null) {
      proj.getFrame().toFront();
      loader = proj.getLogisimFile().getLoader();
      if (proj.isFileDirty()) {
        String message = S.get("openAlreadyMessage", proj.getLogisimFile().getName());
        String[] options = {
          S.get("openAlreadyLoseChangesOption"),
          S.get("openAlreadyNewWindowOption"),
          S.get("openAlreadyCancelOption"),
        };
        int result =
            OptionPane.showOptionDialog(
                proj.getFrame(),
                message,
                S.get("openAlreadyTitle"),
                0,
                OptionPane.QUESTION_MESSAGE,
                null,
                options,
                options[2]);
        if (result == 0) { // keep proj as is, so that load happens into the window
        } else if (result == 1) {
          proj = null; // we'll create a new project
        } else {
          return proj;
        }
      }
    }

    if (proj == null && baseProject != null && baseProject.isStartupScreen()) {
      proj = baseProject;
      proj.setStartupScreen(false);
      loader = baseProject.getLogisimFile().getLoader();
    } else {
      loader = new Loader(baseProject == null ? parent : baseProject.getFrame());
    }

    try {
      final var lib = loader.openLogisimFile(f);
      AppPreferences.updateRecentFile(f);
      if (lib == null) return null;
      LibraryTools.removePresentLibraries(lib, new HashMap<>(), true);
      if (proj == null) {
        proj = new Project(lib);
        updatecircs(lib, proj);
      } else {
        updatecircs(lib, proj);
        proj.setLogisimFile(lib);
      }
    } catch (LoadFailedException ex) {
      if (!ex.isShown()) {
        // The message names the file and the reason; the exception is only for the details.
        OptionPane.showError(parent, S.get("fileOpenErrorTitle"), ex.getMessage(), ex.getCause());
      }
      return null;
    }

    var frame = proj.getFrame();
    if (frame == null) {
      frame = createFrame(baseProject, proj);
    }
    frame.setVisible(true);
    frame.toFront();
    frame.getCanvas().requestFocus();
    proj.getLogisimFile().getLoader().setParent(frame);
    return proj;
  }

  public static Project doOpen(SplashScreen monitor, File source, Map<File, File> substitutions)
      throws LoadFailedException {
    if (monitor != null) monitor.setProgress(SplashScreen.FILE_LOAD);
    final var loader = new Loader(monitor);
    final var file = loader.openLogisimFile(source, substitutions);
    AppPreferences.updateRecentFile(source);

    return completeProject(monitor, loader, file, false);
  }

  /**
   * Opens the unsaved work in the recovery file {@code autosave} as a new, unsaved project. The
   * recovery file is kept until that project is saved or its changes are discarded.
   */
  public static Project doOpenRecovered(SplashScreen monitor, File autosave)
      throws LoadFailedException {
    if (monitor != null) monitor.setProgress(SplashScreen.FILE_LOAD);
    final var loader = new Loader(monitor);
    final var file = loader.openRecoveredFile(autosave);
    return completeProject(monitor, loader, file, false);
  }

  public static Project doOpenNoWindow(SplashScreen monitor, File source)
      throws LoadFailedException {
    return doOpenNoWindow(new Loader(monitor), source, Collections.emptyMap());
  }

  /**
   * Loads a project with the given loader without creating a window, for command-line use.
   *
   * @param loader        The loader to use; it receives any load errors.
   * @param source        The project file.
   * @param substitutions Library files to replace while loading.
   */
  public static Project doOpenNoWindow(Loader loader, File source, Map<File, File> substitutions)
      throws LoadFailedException {
    final var file = loader.openLogisimFile(source, substitutions);
    final var ret = new Project(file);
    updatecircs(file, ret);
    return ret;
  }

  public static void doQuit() {
    final var top = Projects.getTopFrame();
    if (top != null) top.savePreferences();

    final var projects = new ArrayList<>(Projects.getOpenProjects());
    for (final var proj : projects) {
      if (!proj.confirmClose(S.get("confirmQuitTitle"))) return;
    }
    // Do not delete recovery files or dispose any window until every project agrees to quit.
    for (final var proj : projects) {
      proj.getLogisimFile().stopAutosaveThread(true);
    }
    System.exit(0);
  }

  public static boolean doSave(Project proj) {
    final var loader = proj.getLogisimFile().getLoader();
    final var f = loader.getMainFile();
    if (f == null) return doSaveAs(proj);
    if (proj.getLogisimFile().isLoadedWithErrors() && proj.getFrame() != null) {
      final String[] options = {
        S.get("saveAfterLoadErrorsOverwrite"),
        S.get("saveAfterLoadErrorsSaveAs"),
        S.get("saveAfterLoadErrorsCancel"),
      };
      final var choice =
          OptionPane.showOptionDialog(
              proj.getFrame(),
              S.get("saveAfterLoadErrorsMessage", f.getName()),
              S.get("saveAfterLoadErrorsTitle"),
              OptionPane.YES_NO_CANCEL_OPTION,
              OptionPane.WARNING_MESSAGE,
              null,
              options,
              options[1]);
      if (choice == 1) return doSaveAs(proj);
      if (choice != 0) return false;
    }
    if (loader.isMainFileChangedExternally() && proj.getFrame() != null) {
      final String[] options = {
        S.get("saveAfterLoadErrorsOverwrite"),
        S.get("saveAfterLoadErrorsSaveAs"),
        S.get("saveAfterLoadErrorsCancel"),
      };
      final var choice =
          OptionPane.showOptionDialog(
              proj.getFrame(),
              S.get("saveChangedExternallyMessage", f.getName()),
              S.get("saveChangedExternallyTitle"),
              OptionPane.YES_NO_CANCEL_OPTION,
              OptionPane.WARNING_MESSAGE,
              null,
              options,
              options[1]);
      if (choice == 1) return doSaveAs(proj);
      if (choice != 0) return false;
    }
    return doSave(proj, f);
  }

  public static boolean doSave(Project proj, File f) {
    // Saving runs on the event thread, so the window stops repainting until it finishes. Show a
    // busy pointer rather than letting a large project look like a freeze.
    return WaitCursor.get(
        proj.getFrame(),
        () -> {
          final var loader = proj.getLogisimFile().getLoader();
          final var oldTool = proj.getTool();
          proj.setTool(null);
          try {
            final var ret = loader.save(proj.getLogisimFile(), f);
            if (ret) {
              AppPreferences.updateRecentFile(f);
              proj.getLogisimFile().clearLoadedWithErrors();
              proj.setFileAsClean();
            }
            return ret;
          } finally {
            proj.setTool(oldTool);
          }
        });
  }

  /**
   * Imports a Logisim project in a zip file
   *
   * <p>It is the action listener for the File->Import project bundle... menu option.
   *
   * @param proj the current project to perform the file->open action afterwards
   * @return true if success, false otherwise
   */
  public static boolean doExtractAndRunProject(Project proj) {
    var ret = true;
    final var loader = proj.getLogisimFile().getLoader();
    final var chooser = loader.createChooser();
    var isCorrectFile = true;
    do {
      chooser.setFileFilter(Loader.LOGISIM_BUNDLE_FILTER);
      chooser.setAcceptAllFileFilterUsed(false);
      chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
      chooser.setDialogTitle(S.get("projImportBundle"));
      ret &= chooser.showOpenDialog(proj.getFrame()) == JFileChooser.APPROVE_OPTION;
      if (!ret) return ret;
      final var zipFileName = chooser.getSelectedFile().getAbsolutePath();
      isCorrectFile = Files.exists(Paths.get(zipFileName));
      if (isCorrectFile) {
        try {
          final var zipFile = new ZipFile(zipFileName);
          final var bundleInfo = ProjectBundleManifest.getManifestInfo(zipFile, proj.getFrame());
          if (bundleInfo == null) return false;
          final var mainFileEntry = zipFile.getEntry(bundleInfo.getMainLogisimFilename());
          if (mainFileEntry == null) {
            OptionPane.showMessageDialog(proj.getFrame(), S.fmt("projBundleReadError", S.get("projBundleMainNotFound")));
            return false;
          }
          final var readmeFileEntry = zipFile.getEntry(ProjectBundleReadme.README_FILE_NAME);
          if (readmeFileEntry != null) {
            final var readmeInStream = zipFile.getInputStream(readmeFileEntry);
            final var dialog = new ProjectBundleReadme(proj, "");
            dialog.showReadme(readmeInStream);
            readmeInStream.close();
          }
          chooser.setFileFilter(Loader.LOGISIM_DIRECTORY);
          chooser.setAcceptAllFileFilterUsed(false);
          chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
          chooser.setDialogTitle(S.get("projBundleDirectory"));
          var isCorrectDirectory = true;
          do {
            ret &= chooser.showOpenDialog(proj.getFrame()) == JFileChooser.APPROVE_OPTION;
            if (!ret) return ret;
            final var exportDirectory = chooser.getSelectedFile().getAbsolutePath();
            final var extractionDirectory =
                Paths.get(exportDirectory).toAbsolutePath().normalize();
            final var mainProjectFile =
                ProjectBundlePaths.resolveMainFile(
                    extractionDirectory, bundleInfo.getMainLogisimFilename());
            if (mainProjectFile == null) {
              OptionPane.showMessageDialog(
                  proj.getFrame(),
                  S.fmt("projBundleReadError", S.get("projBundleMainNotFound")));
              return false;
            }
            final var mainProjectFileName = mainProjectFile.toString();
            var filename = mainProjectFileName;
            final var libDir = extractionDirectory.resolve(Loader.LOGISIM_LIBRARY_DIR);
            if (Files.exists(mainProjectFile) || Files.exists(libDir)) {
              isCorrectDirectory = false;
              OptionPane.showMessageDialog(proj.getFrame(), S.fmt("projContainsFileDir", bundleInfo.getMainLogisimFilename(), Loader.LOGISIM_LIBRARY_DIR));
            } else {
              isCorrectDirectory = true;
              // extract the main file
              var zipInput = zipFile.getInputStream(mainFileEntry);
              var fileOutput = new FileOutputStream(filename);
              var data = zipInput.read();
              while (data > 0) {
                fileOutput.write(data);
                data = zipInput.read();
              }
              zipInput.close();
              fileOutput.close();
              final var zipFileEntries = zipFile.entries();
              final var extractedLibraryFiles = new HashSet<Path>();
              while (zipFileEntries.hasMoreElements()) {
                final var entry = zipFileEntries.nextElement();
                if (ProjectBundlePaths.isLibraryDirectory(entry.getName())) {
                  Files.createDirectories(libDir);
                  continue;
                }
                final var libraryFile =
                    ProjectBundlePaths.resolveLibraryFile(extractionDirectory, entry.getName());
                if (libraryFile == null
                    || !extractedLibraryFiles.add(libraryFile)
                    || (!libraryFile.toString().endsWith(Loader.LOGISIM_EXTENSION)
                        && !libraryFile.toString().toLowerCase().endsWith(".jar"))) {
                  continue;
                }
                Files.createDirectories(libDir);
                filename = libraryFile.toString();
                zipInput = zipFile.getInputStream(entry);
                fileOutput = new FileOutputStream(filename);
                final var bytes = new byte[1024];
                var length = 0;
                while (((length = zipInput.read(bytes)) >= 0)) {
                  fileOutput.write(bytes, 0, length);
                }
                fileOutput.close();
                zipInput.close();
              }
              ProjectActions.doOpen(proj.getFrame().getCanvas(), proj, new File(mainProjectFileName));
            }
          } while (!isCorrectDirectory);
          zipFile.close();
        } catch (IOException e) {
          isCorrectFile = false;
          OptionPane.showMessageDialog(proj.getFrame(), S.fmt("fileOpenError",
              String.format("%s\n%s", zipFileName, e.getMessage())));
        }
      } else {
        OptionPane.showMessageDialog(proj.getFrame(), S.fmt("fileOpenError", zipFileName));
      }
    } while (!isCorrectFile);
    return ret;
  }

  /**
   * Exports a Logisim project in a zip file
   *
   * <p>It is the action listener for the File->Export project bundle... menu option.
   *
   * @param proj Project to be exported
   * @return true if success, false otherwise
   */
  public static boolean doExportProject(Project proj) {
    // Every way out, including cancelling a dialog, must give the user back their tool.
    final var oldTool = proj.getTool();
    proj.setTool(null);
    try {
      return exportProjectBundle(proj);
    } finally {
      proj.setTool(oldTool);
    }
  }

  private static boolean exportProjectBundle(Project proj) {
    var ret = true;
    final var loader = proj.getLogisimFile().getLoader();
    var mainFileName = loader.getMainFile() == null ? "Untitled.circ" : loader.getMainFile().getName();
    var zipFile = mainFileName.replace(Loader.LOGISIM_EXTENSION, Loader.LOGISIM_PROJECT_BUNDLE_EXTENSION);
    final var chooser = loader.createChooser();
    chooser.setFileFilter(Loader.LOGISIM_BUNDLE_FILTER);
    chooser.setAcceptAllFileFilterUsed(false);
    chooser.setSelectedFile(new File(zipFile));
    chooser.setDialogTitle(S.get("projExportBundle"));
    var isCorrectFile = true;
    do {
      ret &= chooser.showSaveDialog(proj.getFrame()) == JFileChooser.APPROVE_OPTION;
      if (!ret) {
        return false;
      }
      try {
        zipFile = chooser.getSelectedFile().getAbsolutePath();
        if (!zipFile.endsWith(Loader.LOGISIM_PROJECT_BUNDLE_EXTENSION)) {
          zipFile = zipFile.concat(Loader.LOGISIM_PROJECT_BUNDLE_EXTENSION);
        }
        final var path = Paths.get(zipFile);
        if (Files.exists(path)) {
          isCorrectFile = OptionPane.showConfirmDialog(proj.getFrame(), S.fmt("projExistsOverwrite",
              new File(zipFile).getName()), S.get("projExportBundle"), OptionPane.YES_NO_OPTION) == OptionPane.YES_OPTION;
        } else {
          isCorrectFile = true;
        }
        if (isCorrectFile) {
          final var dialog = new ProjectBundleReadme(proj, mainFileName.replace(Loader.LOGISIM_EXTENSION, ""));
          final var readmeInfo = dialog.getReadmeInfo();
          if (readmeInfo == null) return false;
          final var projectFile = new FileOutputStream(zipFile);
          final var projectZipFile = new ZipOutputStream(projectFile);
          ProjectBundleReadme.writeReadmeFile(projectZipFile, readmeInfo);
          projectZipFile.putNextEntry(
              new ZipEntry(ProjectBundlePaths.libraryDirectoryEntry()));
          mainFileName = chooser.getSelectedFile().getName().replace(Loader.LOGISIM_PROJECT_BUNDLE_EXTENSION, "").concat(Loader.LOGISIM_EXTENSION);
          ret &= loader.export(proj.getLogisimFile(), projectZipFile, mainFileName);
          final var info = ProjectBundleManifest.getInfoContainer(BuildInfo.displayName, mainFileName);
          ProjectBundleManifest.writeManifest(projectZipFile, info);
          projectZipFile.close();
          projectFile.close();
        }
      } catch (IOException e) {
        OptionPane.showMessageDialog(proj.getFrame(), S.get("ProjUnableToCreate", e.getMessage()));
        return false;
      }
    } while (!isCorrectFile);
    return ret;
  }


  /**
   * Saves a Logisim project in a .circ file.
   *
   * <p>It is the action listener for the File->Save as... menu option.
   *
   * @param proj project to be saved
   * @return true if success, false otherwise
   */
  public static boolean doSaveAs(Project proj) {
    var loader = proj.getLogisimFile().getLoader();
    var chooser = loader.createChooser();
    chooser.setFileFilter(Loader.LOGISIM_FILTER);
    chooser.setSelectedFile(suggestedSaveFile(proj, chooser.getCurrentDirectory()));

    // Every rejection below returns to the chooser rather than abandoning Save As. Any file name
    // the system accepts is fine: the FPGA flow checks the project name itself when it needs to.
    while (true) {
      final var returnVal = chooser.showSaveDialog(proj.getFrame());
      if (returnVal != JFileChooser.APPROVE_OPTION) {
        return false;
      }

      var selectedFile = chooser.getSelectedFile();
      if (!selectedFile.getName().endsWith(Loader.LOGISIM_EXTENSION)) {
        var old = selectedFile.getName();
        int ext0 = old.lastIndexOf('.');
        if (ext0 < 0 || !Pattern.matches("\\.\\p{L}{2,}\\d?", old.substring(ext0))) {
          selectedFile = new File(selectedFile.getParentFile(), old + Loader.LOGISIM_EXTENSION);
        } else {
          var ext = old.substring(ext0);
          var ttl = S.get("replaceExtensionTitle");
          var msg = S.get("replaceExtensionMessage", ext);
          Object[] options = {
            S.get("replaceExtensionReplaceOpt", ext),
            S.get("replaceExtensionAddOpt", Loader.LOGISIM_EXTENSION),
            S.get("replaceExtensionKeepOpt")
          };
          var dlog = new JOptionPane(msg);
          dlog.setMessageType(OptionPane.QUESTION_MESSAGE);
          dlog.setOptions(options);
          dlog.createDialog(proj.getFrame(), ttl).setVisible(true);

          selectedFile = resolveSaveExtension(selectedFile, dlog.getValue(), options);
          if (selectedFile == null) return false;
        }
      }

      final var rejection = saveTargetRejection(proj, selectedFile);
      if (rejection != null) {
        OptionPane.showMessageDialog(
            proj.getFrame(), rejection, S.get("FileSaveAsItem"), OptionPane.ERROR_MESSAGE);
        continue;
      }
      if (selectedFile.exists()) {
        final var confirm =
            OptionPane.showConfirmDialog(
                proj.getFrame(),
                S.get("confirmOverwriteMessage"),
                S.get("confirmOverwriteTitle"),
                OptionPane.YES_NO_OPTION);
        if (confirm != OptionPane.YES_OPTION) continue;
      }
      return doSave(proj, selectedFile);
    }
  }

  /**
   * The file Save As starts from: the current file, or for a project never saved the project's
   * name in the chooser's folder, so that the name field is never empty.
   */
  static File suggestedSaveFile(Project proj, File directory) {
    final var current = proj.getLogisimFile().getLoader().getMainFile();
    if (current != null) return current;
    return new File(directory, proj.getLogisimFile().getName() + Loader.LOGISIM_EXTENSION);
  }

  /**
   * Why {@code target} cannot receive a Save As of {@code proj}, or {@code null} if it can. A
   * folder cannot be overwritten, and a file another window has open would later be overwritten
   * again by that window, silently discarding this save.
   */
  static String saveTargetRejection(Project proj, File target) {
    if (target.isDirectory()) {
      return S.get("saveAsDirectoryError", target.getName());
    }
    final var owner = Projects.findProjectFor(target);
    if (owner != null && owner != proj) {
      return S.get("saveAsOpenElsewhereError", target.getName());
    }
    return null;
  }

  static File resolveSaveExtension(File selected, Object result, Object[] options) {
    final var name = selected.getName();
    if (result == options[0]) {
      return new File(selected.getParentFile(),
          name.substring(0, name.lastIndexOf('.')) + Loader.LOGISIM_EXTENSION);
    }
    if (result == options[1]) {
      return new File(selected.getParentFile(), name + Loader.LOGISIM_EXTENSION);
    }
    return result == options[2] ? selected : null;
  }
}

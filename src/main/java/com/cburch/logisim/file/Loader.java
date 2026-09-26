/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.file;

import static com.cburch.logisim.file.Strings.S;
import com.cburch.logisim.AppIdentity;
import com.cburch.logisim.Main;
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.std.Builtin;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.util.JFileChoosers;
import com.cburch.logisim.util.XmlUtil;
import com.cburch.logisim.util.ZipClassLoader;
import com.cburch.logisim.vhdl.file.HdlFile;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Stack;
import java.util.jar.JarFile;
import com.cburch.logisim.util.LineBuffer;
import java.util.zip.ZipOutputStream;

import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.filechooser.FileFilter;
import javax.xml.parsers.ParserConfigurationException;
import org.xml.sax.SAXException;

public class Loader implements LibraryLoader {
  private static class JarFileFilter extends FileFilter {
    @Override
    public boolean accept(File f) {
      return f.isDirectory() || f.getName().endsWith(".jar");
    }

    @Override
    public String getDescription() {
      return S.get("jarFileFilter");
    }
  }

  private static class TxtFileFilter extends FileFilter {
    @Override
    public boolean accept(File f) {
      return f.isDirectory() || f.getName().endsWith(".txt");
    }

    @Override
    public String getDescription() {
      return S.get("txtFileFilter");
    }
  }

  private static class VhdlFileFilter extends FileFilter {
    @Override
    public boolean accept(File f) {
      return f.isDirectory() || f.getName().endsWith(".vhd") || f.getName().endsWith(".vhdl");
    }

    @Override
    public String getDescription() {
      return S.get("vhdlFileFilter");
    }
  }

  private static class VerilogFileFilter extends FileFilter {
    @Override
    public boolean accept(File f) {
      return f.isDirectory() || f.getName().endsWith(".v");
    }

    @Override
    public String getDescription() {
      return S.get("verilogFileFilter");
    }
  }

  private static class LogisimFileFilter extends FileFilter {
    @Override
    public boolean accept(File f) {
      return f.isDirectory() || f.getName().endsWith(LOGISIM_EXTENSION);
    }

    @Override
    public String getDescription() {
      return S.get("logisimFileFilter");
    }
  }

  private static class LogisimProjectBundleFilter extends FileFilter {
    @Override
    public boolean accept(File f) {
      return f.isDirectory() || f.getName().endsWith(LOGISIM_PROJECT_BUNDLE_EXTENSION);
    }

    @Override
    public String getDescription() {
      return S.get("logisimProjectBundleFilter");
    }
  }

  private static class LogisimDirectoryFilter extends FileFilter {
    @Override
    public boolean accept(File f) {
      return f.isDirectory();
    }

    @Override
    public String getDescription() {
      return S.get("logisimDirectoryFilter");
    }
  }

  private static class TclFileFilter extends FileFilter {
    @Override
    public boolean accept(File f) {
      return f.isDirectory() || f.getName().endsWith(".tcl");
    }

    @Override
    public String getDescription() {
      return S.get("tclFileFilter");
    }
  }

  public static final String LOGISIM_EXTENSION = ".circ";
  public static final String LOGISIM_PROJECT_BUNDLE_EXTENSION = ".lsebdl";
  public static final String LOGISIM_PROJECT_BUNDLE_INFO_FILE = "LogisimEvolutionBundle.info";
  public static final String LOGISIM_LIBRARY_DIR = "library";
  public static final String LOGISIM_CIRCUIT_DIR = "circuit";
  public static final String LOGISIM_UNNAMED_AUTOSAVE_PREFIX = "unnamed-";
  public static final String LOGISIM_UNNAMED_AUTOSAVE_SUFFIX = ".circ.autosave";

  public static File getUnnamedAutosaveDirectory() {
    return AppIdentity.unnamedRecoveryDirectory();
  }
  public static final FileFilter LOGISIM_FILTER = new LogisimFileFilter();
  public static final FileFilter LOGISIM_BUNDLE_FILTER = new LogisimProjectBundleFilter();
  public static final FileFilter LOGISIM_DIRECTORY = new LogisimDirectoryFilter();
  public static final FileFilter JAR_FILTER = new JarFileFilter();
  public static final FileFilter TXT_FILTER = new TxtFileFilter();
  public static final FileFilter TCL_FILTER = new TclFileFilter();
  public static final FileFilter VHDL_FILTER = new VhdlFileFilter();
  public static final FileFilter VERILOG_FILTER = new VerilogFileFilter();

  private Component parent;
  private final Builtin builtin = new Builtin();
  // to be cleared with each new file
  private File mainFile = null;
  private File autosaveFile = null;
  private FileTime mainFileTimestamp = null;
  private static final int MESSAGE_COLUMNS = 50;
  private static final int MESSAGE_MAX_ROWS = 16;
  private final Stack<File> filesOpening = new Stack<>();
  private Map<File, File> substitutions = new HashMap<>();
  private ZipOutputStream zipFile;

  public Loader(Component parent) {
    this.parent = parent;
    clear();
  }

  private static File determineBackupName(File base) {
    final var dir = base.getParentFile();
    var name = base.getName();
    if (name.endsWith(LOGISIM_EXTENSION)) {
      name = name.substring(0, name.length() - LOGISIM_EXTENSION.length());
    }
    for (var i = 1; i <= 20; i++) {
      final var ext = i == 1 ? ".bak" : (".bak" + i);
      final var candidate = new File(dir, name + ext);
      if (!candidate.exists()) return candidate;
    }
    return null;
  }

  // Revolution sidecars cannot be mistaken for Evolution recovery of the same .circ file.
  private static File determineAutosaveName(File base) {
    if (base == null) {
      return new File(getUnnamedAutosaveDirectory(),
          LOGISIM_UNNAMED_AUTOSAVE_PREFIX + UUID.randomUUID()
              + LOGISIM_UNNAMED_AUTOSAVE_SUFFIX);
    }
    final var extension = base.getName().endsWith(LOGISIM_EXTENSION)
        ? ".revolution.autosave" : ".circ.revolution.autosave";
    return new File(base.getParentFile(), "." + base.getName() + extension);
  }

  static Optional<File> findAutosaveFile(File base) {
    final var as = determineAutosaveName(base);
    if (as == null || !as.exists()) return Optional.empty();
    return Optional.of(as);
  }

  private static void recoverBackup(File backup, File dest) {
    if (backup != null && backup.exists()) {
      // FIXME: recovery will fail if delete() failed
      if (dest.exists()) dest.delete();
      // FIXME: renameTo() can fail. We need to tell the user if so
      backup.renameTo(dest);
    }
  }

  //
  // more substantive methods accessed from outside this package
  //
  public void clear() {
    filesOpening.clear();
    mainFile = null;
  }

  public JFileChooser createChooser() {
    return JFileChoosers.createAt(getCurrentDirectory());
  }

  public Builtin getBuiltin() {
    return builtin;
  }

  // used here and in LibraryManager only, also in MemMenu
  public File getCurrentDirectory() {
    final var ref = (!filesOpening.empty()) ? filesOpening.peek() : mainFile;
    return ref == null ? null : ref.getParentFile();
  }

  @Override
  public String getDescriptor(Library lib) {
    return LibraryManager.instance.getDescriptor(this, lib);
  }

  //
  // helper methods
  //
  File getFileFor(String name, FileFilter filter) {
    // Determine the actual file name.
    final var normalizedName = ProjectBundlePaths.normalizeLibraryDescriptorPath(name);
    var file = new File(normalizedName);
    if (!file.isAbsolute()) {
      final var currentDirectory = getCurrentDirectory();
      if (currentDirectory != null) file = new File(currentDirectory, normalizedName);
    }
    while (!file.canRead()) {
      if (!Main.hasGui()) {
        // Nobody can answer a file chooser on the command line: fail with the cause instead.
        throw new LoaderException(
            String.format(S.get("fileLibraryMissingHeadlessError"), file.getPath()));
      }
      // It doesn't exist. Figure it out from the user.
      OptionPane.showMessageDialog(
          parent, String.format(S.get("fileLibraryMissingError"), file.getName()));
      final var chooser = createChooser();
      chooser.setFileFilter(filter);
      chooser.setDialogTitle(S.get("fileLibraryMissingTitle", file.getName()));
      int action = chooser.showDialog(parent, S.get("fileLibraryMissingButton"));
      if (action != JFileChooser.APPROVE_OPTION) {
        throw new LoaderException(S.get("fileLoadCanceledError"));
      }
      file = chooser.getSelectedFile();
    }
    return file;
  }

  //
  // file chooser related methods
  //
  public File getMainFile() {
    return mainFile;
  }

  private File getSubstitution(File source) {
    final var ret = substitutions.get(source);
    return ret == null ? source : ret;
  }

  Library loadJarFile(File request, String className) throws LoadFailedException {
    final var actual = getSubstitution(request);

    // Anyway, here's the line for this new version:
    final var loader = new ZipClassLoader(actual);

    // load library class from loader
    Class<?> retClass;
    try {
      retClass = loader.loadClass(className);
    } catch (ClassNotFoundException e) {
      throw new LoadFailedException(S.get("jarClassNotFoundError", className));
    }
    if (!(Library.class.isAssignableFrom(retClass))) {
      throw new LoadFailedException(S.get("jarClassNotLibraryError", className));
    }

    // instantiate library
    Library ret;
    try {
      ret = (Library) retClass.getDeclaredConstructor().newInstance();
    } catch (Exception e) {
      throw new LoadFailedException(S.get("jarLibraryNotCreatedError", className));
    }
    return ret;
  }

  public Library loadJarLibrary(File file, String className) {
    final var actual = getSubstitution(file);
    return LibraryManager.instance.loadJarLibrary(this, actual, className);
  }

  /**
   * What a JAR file offers to load as a library.
   *
   * @param manifestClass the manifest's {@code Library-Class} attribute, or null if the JAR has no
   *     manifest or the manifest does not name one
   * @param classNames the JAR's top-level classes, sorted, as candidates for the library class
   */
  record JarLibraryClasses(String manifestClass, List<String> classNames) {}

  /**
   * Reads a JAR file without loading any of its classes.
   *
   * @throws IOException if the file cannot be read or is not a JAR (ZIP) file
   */
  static JarLibraryClasses inspectJar(File file) throws IOException {
    try (final var jar = new JarFile(file)) {
      String manifestClass = null;
      final var manifest = jar.getManifest();
      if (manifest != null) {
        manifestClass = manifest.getMainAttributes().getValue("Library-Class");
      }
      final var classNames = new ArrayList<String>();
      final var entries = jar.entries();
      while (entries.hasMoreElements()) {
        final var name = entries.nextElement().getName();
        if (!name.endsWith(".class") || name.contains("$") || name.startsWith("META-INF/")) {
          continue;
        }
        classNames.add(name.substring(0, name.length() - ".class".length()).replace('/', '.'));
      }
      Collections.sort(classNames);
      return new JarLibraryClasses(manifestClass, classNames);
    }
  }

  /**
   * Determines the library class to load from a JAR: the manifest's {@code Library-Class}
   * attribute, or else the user's choice among the classes the JAR contains.
   *
   * @return the class name, or null when the file is not a readable JAR, contains no classes, or
   *     the user cancels (the user has then been told why, except on cancel)
   */
  public String askJarLibraryClass(File file) {
    final JarLibraryClasses info;
    try {
      info = inspectJar(getSubstitution(file));
    } catch (IOException e) {
      OptionPane.showMessageDialog(
          parent,
          S.get("jarOpenError", file.getName(), e.getLocalizedMessage()),
          S.get("fileErrorTitle"),
          OptionPane.ERROR_MESSAGE);
      return null;
    }
    if (info.manifestClass() != null) return info.manifestClass();
    if (info.classNames().isEmpty()) {
      OptionPane.showMessageDialog(
          parent,
          S.get("jarNoClassesError", file.getName()),
          S.get("fileErrorTitle"),
          OptionPane.ERROR_MESSAGE);
      return null;
    }
    final var candidates = info.classNames();
    var initial = candidates.get(0);
    for (final var name : candidates) {
      if (name.endsWith("Library")) {
        initial = name;
        break;
      }
    }
    final var choice =
        OptionPane.showInputDialog(
            parent,
            S.get("jarClassChoicePrompt", file.getName()),
            S.get("jarClassNameTitle"),
            OptionPane.QUESTION_MESSAGE,
            null,
            candidates.toArray(),
            initial);
    return choice == null ? null : choice.toString();
  }

  //
  // Library methods
  //
  @Override
  public Library loadLibrary(String desc) {
    return LibraryManager.instance.loadLibrary(this, desc);
  }

  //
  // methods for LibraryManager
  //
  LogisimFile loadLogisimFile(File request) throws LoadFailedException {
    final var actual = getSubstitution(request);
    for (final var fileOpening : filesOpening) {
      if (fileOpening.equals(actual)) {
        throw new LoadFailedException(S.get("logisimCircularError", toProjectName(actual)));
      }
    }

    LogisimFile ret = null;
    filesOpening.push(actual);
    try {
      ret = LogisimFile.load(actual, this);
    } catch (IOException e) {
      final var reason = LogisimFile.describeLoadFailure(actual, e);
      throw new LoadFailedException(S.get("fileOpenFailed", actual.getName(), reason), e);
    } finally {
      filesOpening.pop();
    }
    if (ret != null) ret.setName(toProjectName(actual));
    return ret;
  }

  public Library loadLogisimLibrary(File file) {
    final var actual = getSubstitution(file);
    final var ret = LibraryManager.instance.loadLogisimLibrary(this, actual);
    if (ret != null) {
      LogisimFile retBase = (LogisimFile) ret.getBase();
      showMessages(retBase);
    }
    return ret;
  }

  public LogisimFile openLogisimFile(File file) throws LoadFailedException {
    try {
      final var ret = loadLogisimFile(file);
      // Only a dismissed recovery prompt yields nothing; the user needs no message for that.
      if (ret == null) throw new LoadFailedException(S.get("fileLoadCanceledError"), true);
      setMainFile(file);
      recordMainFileTimestamp();
      showMessages(ret);
      return ret;
    } catch (LoaderException e) {
      throw new LoadFailedException(e.getMessage(), e.isShown());
    }
  }

  /**
   * Opens a recovery file left by an unsaved project as a new unsaved project. The recovery file
   * stays this project's autosave, so it disappears only once the work is saved, superseded by a
   * newer autosave, or deliberately discarded.
   */
  public LogisimFile openRecoveredFile(File autosave) throws LoadFailedException {
    final LogisimFile ret;
    try (final var in = new FileInputStream(autosave)) {
      ret = LogisimFile.loadSub(in, this, autosave);
    } catch (LoaderException e) {
      throw new LoadFailedException(e.getMessage(), e.isShown());
    } catch (IOException | SAXException e) {
      final var reason = LogisimFile.describeLoadFailure(autosave, e);
      throw new LoadFailedException(S.get("fileOpenFailed", autosave.getName(), reason), e);
    }
    ret.markAutosaveLoaded();
    setAutosavePath(autosave);
    showMessages(ret);
    return ret;
  }

  /**
   * The names of the circuits in the project file {@code file}, in file order, for describing a
   * file without opening it. Empty if the file cannot be read.
   */
  public static List<String> circuitNamesIn(File file) {
    final var names = new ArrayList<String>();
    try (final var in = new FileInputStream(file)) {
      final var root =
          XmlUtil.getHardenedBuilderFactory().newDocumentBuilder().parse(in).getDocumentElement();
      for (final var circuit : XmlIterator.forChildElements(root, "circuit")) {
        names.add(circuit.getAttribute("name"));
      }
    } catch (IOException | SAXException | ParserConfigurationException | RuntimeException e) {
      names.clear();
    }
    return names;
  }

  public LogisimFile openLogisimFile(File file, Map<File, File> substitutions)
      throws LoadFailedException {
    this.substitutions = substitutions;
    try {
      return openLogisimFile(file);
    } finally {
      this.substitutions = Collections.emptyMap();
    }
  }

  public LogisimFile openLogisimFile(InputStream reader) throws IOException {
    LogisimFile ret = null;
    try {
      ret = LogisimFile.load(reader, this);
    } catch (LoaderException e) {
      return null;
    }
    showMessages(ret);
    return ret;
  }

  // Loads all the custom logisim (.circ) libraries found in the default library folder
  public Library[] loadCustomStartupLibraries(String customLibraryDirectoryPath) {
    File directory = new File(customLibraryDirectoryPath);

    if (!directory.exists() || !LOGISIM_DIRECTORY.accept(directory)) {
      return new Library[0];
    }

    var files = directory.listFiles();

    if (files == null) {
      return new Library[0];
    }

    List<Library> loadedLibraries = new ArrayList<>();
    for (File file : files) {
      if (!LOGISIM_FILTER.accept(file)) continue;

      try {
        var library = loadLogisimLibrary(file);

        if (library != null && !library.getLibraries().isEmpty())
          loadedLibraries.add(library);
      } catch (NullPointerException e) {
        continue;
      }
    }

    return loadedLibraries.toArray(new Library[0]);
  }

  public void reload(LoadedLibrary lib) {
    LibraryManager.instance.reload(this, lib);
  }

  public boolean export(LogisimFile file, ZipOutputStream zipFile, String mainFileName) {
    final var previous = this.zipFile;
    this.zipFile = zipFile;
    try {
      file.write(zipFile, this, mainFileName);
      return true;
    } catch (IOException e) {
      showError(e.getMessage());
      return false;
    } finally {
      this.zipFile = previous;
    }
  }

  public boolean export(LogisimFile file, String homeDirectory) {
    try {
      final var mainCircFile =
          LineBuffer.format(
              "{{1}}{{2}}{{3}}{{2}}{{4}}",
              homeDirectory, File.separator, LOGISIM_CIRCUIT_DIR, getMainFile().getName());
      final var libraryHome =
          String.format("%s%s%s", homeDirectory, File.separator, LOGISIM_LIBRARY_DIR);
      try (final var fwrite = new FileOutputStream(mainCircFile)) {
        file.write(fwrite, this, libraryHome);
      }
    } catch (IOException e) {
      // TODO: give an error message to the user #1136
      System.err.println("Unable to export file");
      return false;
    }
    return true;
  }

  public ZipOutputStream getZipFile() {
    return zipFile;
  }

  public void setZipFile(ZipOutputStream file) {
    zipFile = file;
  }

  public boolean save(LogisimFile file, File dest) {
    file.interruptAutosaveThread(); // Notify autosave thread of save
    final var reference = LibraryManager.instance.findReference(file, dest);
    if (reference != null) {
      OptionPane.showMessageDialog(
          parent,
          S.get("fileCircularError", reference.getDisplayName()),
          S.get("fileSaveErrorTitle"),
          OptionPane.ERROR_MESSAGE);
      return false;
    }

    if (dest.isDirectory()) {
      // Never create a backup copy of a folder or try to replace it with a file.
      showError(S.get("fileSaveError", S.get("fileSaveDirectoryError", dest.getName())));
      return false;
    }

    final var oldFile = getMainFile();
    File backup = null;
    var backupCreated = false;
    var publishing = false;
    Path staged = null;
    Path target = null;
    try {
      // Write through a symbolic link rather than replacing the link itself.
      target = resolveWriteTarget(dest);
      if (Files.exists(target) && !Files.isWritable(target)) {
        // Replacing the file would succeed in a writable folder, silently defeating read-only.
        throw new IOException(S.get("fileSaveReadOnlyError", dest.getName()));
      }
      setMainFile(dest);
      staged = stageWrite(file, dest, target, true);
      if (Files.exists(target)) {
        backup = determineBackupName(dest);
        if (backup == null) throw new IOException("No available backup filename");
        Files.copy(target, backup.toPath());
        backupCreated = true;
      }
      publishing = true;
      publishWrite(staged, target);
      staged = null;
    } catch (IOException e) {
      setMainFile(oldFile);
      if (publishing) {
        try {
          if (backupCreated) {
            // Keep the backup as well if publication failed; never discard the last good copy.
            Files.copy(backup.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
          } else {
            Files.deleteIfExists(target);
          }
        } catch (IOException recoveryError) {
          e.addSuppressed(recoveryError);
        }
      }
      showError(S.get("fileSaveError", e.toString()));
      return false;
    } finally {
      discardStagedWrite(staged);
    }

    recordMainFileTimestamp();
    file.setName(toProjectName(dest));
    LibraryManager.instance.fileSaved(this, dest, oldFile, file);
    if (backupCreated && backup.exists()) {
      // FIXME: delete can fail. Ensure we will not have snowball effect here!
      backup.delete();
    }
    if (autosaveFile != null && autosaveFile.exists()) {
      deleteAutosave();
    }
    return true;
  }

  /**
   * Method to perform autosaves. Essentially does the same as save()
   * but without any failsafes, if saving fails it simply fails.
   *
   * @param file The file that should be autosaved
   *
   * @return True if writing was successful, else false;
   */
  public boolean autosave(LogisimFile file) {
    final var oldAutosave = autosaveFile;
    final var destination = determineAutosaveName(mainFile);
    if (destination == null) {
      return false;
    }
    Path staged = null;
    try {
      // Taken where the editor makes its changes; see LogisimFile.autosaveSnapshot.
      final var content = file.autosaveSnapshot(this, destination);
      if (content == null) return true; // saved in the meantime: nothing left to recover
      if (mainFile == null) Files.createDirectories(destination.toPath().getParent());
      final var target = destination.toPath().toAbsolutePath();
      staged = stageWrite(output -> output.write(content), target, false);
      publishWrite(staged, target);
      staged = null;
    } catch (IOException e) {
      return false;
    } finally {
      discardStagedWrite(staged);
    }
    autosaveFile = destination;
    if (oldAutosave != null && !oldAutosave.equals(autosaveFile)) {
      oldAutosave.delete();
    }
    return true;
  }

  /** The file a save really replaces: the end of any symbolic link chain at {@code dest}. */
  static Path resolveWriteTarget(File dest) throws IOException {
    final var path = dest.toPath().toAbsolutePath();
    try {
      return path.toRealPath();
    } catch (NoSuchFileException e) {
      return path; // a new file (or a dangling link, which is replaced)
    }
  }

  /**
   * Writes {@code file} next to {@code target} for an atomic replace. {@code destination} is the
   * path the user chose, which relative library references are computed from.
   *
   * @param keepFileMode give the staged file the permissions the replaced file had, or for a new
   *     file the ordinary ones for a newly created file, rather than a private temporary file's
   */
  private Path stageWrite(LogisimFile file, File destination, Path target, boolean keepFileMode)
      throws IOException {
    // Relative library paths describe the final file, not its staging filename.
    return stageWrite(output -> file.write(output, this, destination, null), target, keepFileMode);
  }

  /** Writes a whole file's content to a stream. */
  @FunctionalInterface
  private interface ContentWriter {
    void writeTo(OutputStream output) throws IOException;
  }

  private Path stageWrite(ContentWriter content, Path target, boolean keepFileMode)
      throws IOException {
    final var directory = target.getParent();
    final Path staged;
    if (keepFileMode) {
      staged = Files.createFile(directory.resolve(".logisim-save-" + UUID.randomUUID() + ".tmp"));
      copyPermissions(target, staged);
    } else {
      staged = Files.createTempFile(directory, ".logisim-save-", ".tmp");
    }
    var complete = false;
    try {
      try (final var output = Files.newOutputStream(staged)) {
        content.writeTo(output);
      }
      if (Files.size(staged) == 0) throw new IOException(S.get("fileSaveZeroError"));
      complete = true;
      return staged;
    } finally {
      if (!complete) discardStagedWrite(staged);
    }
  }

  private static void copyPermissions(Path from, Path to) throws IOException {
    if (!Files.exists(from)) return;
    try {
      Files.setPosixFilePermissions(to, Files.getPosixFilePermissions(from));
    } catch (UnsupportedOperationException e) {
      // Not a POSIX file system: the replaced file's attributes cannot be expressed this way.
    }
  }

  private static void publishWrite(Path staged, Path destination) throws IOException {
    try {
      Files.move(staged, destination,
          StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException e) {
      Files.move(staged, destination, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void discardStagedWrite(Path staged) {
    if (staged == null) return;
    try {
      Files.deleteIfExists(staged);
    } catch (IOException e) {
      org.slf4j.LoggerFactory.getLogger(Loader.class).warn("Unable to remove staged save {}", staged, e);
    }
  }

  /**
   * Method to delete the latest autosave.
   *
   * @return True if deletion was successful,
   *     false if the file is null or deletion failed
   */
  public boolean deleteAutosave() {
    return autosaveFile != null ? autosaveFile.delete() : false;
  }

  private void setMainFile(File value) {
    mainFile = value;
  }

  /** Remembers when the main file was last read or written by this project. */
  private void recordMainFileTimestamp() {
    mainFileTimestamp = timestampOf(mainFile);
  }

  private static FileTime timestampOf(File file) {
    if (file == null) return null;
    try {
      return Files.getLastModifiedTime(file.toPath());
    } catch (IOException e) {
      return null;
    }
  }

  /**
   * Whether the main file on disk was changed by another program since this project last read or
   * wrote it, so that saving would discard those changes.
   */
  public boolean isMainFileChangedExternally() {
    if (mainFile == null || mainFileTimestamp == null) return false;
    final var current = timestampOf(mainFile);
    return current != null && !current.equals(mainFileTimestamp);
  }

  public void setParent(Component value) {
    parent = value;
  }

  @Override
  public void showError(String description) {
    if (!filesOpening.empty()) {
      final var top = filesOpening.peek();
      final var init = toProjectName(top) + ":";
      final var sep = description.contains("\n") ? "\n" : " ";
      description = init + sep + description;
    }

    final var copyButtonText = S.get("fileErrorCopyButton");
    final var okButtonText = javax.swing.UIManager.getString("OptionPane.okButtonText") != null
        ? javax.swing.UIManager.getString("OptionPane.okButtonText")
        : "OK";
    final Object[] options = new Object[] {copyButtonText, okButtonText};

    final var result = OptionPane.showOptionDialog(
        parent,
        messageComponent(description),
        S.get("fileErrorTitle"),
        JOptionPane.DEFAULT_OPTION,
        OptionPane.ERROR_MESSAGE,
        null,
        options,
        okButtonText);

    if (result == 0) {
      final var selection = new StringSelection(description);
      Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
    }
  }

  /**
   * Shows {@code text} wrapped at a readable width. Short messages are shown as they are; a long
   * list scrolls rather than growing the dialog past the screen.
   */
  static java.awt.Component messageComponent(String text) {
    final var area = new JTextArea(text);
    area.setEditable(false);
    area.setLineWrap(true);
    area.setWrapStyleWord(true);
    area.setColumns(MESSAGE_COLUMNS);
    area.setOpaque(false);
    area.setBorder(null);
    area.setCaretPosition(0);
    if (!Main.hasGui()) return area;
    var font = javax.swing.UIManager.getFont("OptionPane.messageFont");
    if (font == null) font = javax.swing.UIManager.getFont("Label.font");
    if (font != null) area.setFont(font);
    // Lay the text out at the chosen width to learn how many rows it really needs.
    area.setSize(area.getPreferredSize().width, Short.MAX_VALUE);
    if (area.getPreferredSize().height <= area.getFontMetrics(area.getFont()).getHeight()
        * MESSAGE_MAX_ROWS) {
      return area;
    }
    area.setRows(MESSAGE_MAX_ROWS);
    final var scroll = new JScrollPane(area);
    scroll.setBorder(null);
    return scroll;
  }

  private void showMessages(LogisimFile source) {
    if (source == null) return;
    var message = source.getMessage();
    while (message != null) {
      OptionPane.showMessageDialog(
          parent,
          messageComponent(message),
          S.get("fileMessageTitle"),
          OptionPane.WARNING_MESSAGE);
      message = source.getMessage();
    }
  }

  /**
   * Method to show an OptionDialog showing Options and returning the result.
   *
   * @param message The message to be shown in the dialog
   * @param title The title of the dialog
   * @param options The Options to be available for selection
   * @param initialSelection The index of the default selected Option
   *
   * @return The index of the selected Option
   */
  public int showOptions(String message, String title, String[] options, int initialSelection) {
    return OptionPane.showOptionDialog(
            parent,
            message,
            title,
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[initialSelection % options.length]);
  }

  private String toProjectName(File file) {
    final var ret = file.getName();

    return (ret.endsWith(LOGISIM_EXTENSION))
        ? ret.substring(0, ret.length() - LOGISIM_EXTENSION.length())
        : ret;
  }

  public String vhdlImportChooser(Component window) {
    return hdlImportChooser(window, VHDL_FILTER, S.get("hdlOpenDialog"));
  }

  /** Asks for a Verilog file and returns its text, or null when cancelled or unreadable. */
  public String verilogImportChooser(Component window) {
    return hdlImportChooser(window, VERILOG_FILTER, S.get("verilogOpenDialog"));
  }

  private String hdlImportChooser(Component window, FileFilter filter, String title) {
    final var chooser = createChooser();
    chooser.setFileFilter(filter);
    chooser.setDialogTitle(title);
    final var returnVal = chooser.showOpenDialog(window);
    if (returnVal != JFileChooser.APPROVE_OPTION) return null;
    final var selected = chooser.getSelectedFile();
    if (selected == null) return null;
    try {
      return HdlFile.load(selected);
    } catch (IOException e) {
      OptionPane.showMessageDialog(
          window, e.getMessage(), S.get("hexOpenErrorTitle"), OptionPane.ERROR_MESSAGE);
      return null;
    }
  }

  public void setAutosavePath(File f) {
    autosaveFile = new File(f.getParent(), f.getName());
  }
}

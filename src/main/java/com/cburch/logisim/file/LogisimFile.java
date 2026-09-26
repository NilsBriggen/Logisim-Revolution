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

import com.cburch.logisim.Main;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitAttributes;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.gui.generic.OptionPane;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.proj.Projects;
import com.cburch.logisim.std.base.BaseLibrary;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import com.cburch.logisim.tools.Tool;
import com.cburch.logisim.util.EventSourceWeakSupport;
import com.cburch.logisim.util.StringUtil;
import com.cburch.logisim.util.SyntaxChecker;
import com.cburch.logisim.util.UniquelyNamedThread;
import com.cburch.logisim.vhdl.base.VhdlContent;
import com.cburch.logisim.vhdl.base.VhdlEntity;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

public class LogisimFile extends Library implements LibraryEventSource, CircuitListener {
  private static final Logger logger = LoggerFactory.getLogger(LogisimFile.class);


  private static class WritingThread extends UniquelyNamedThread {
    final OutputStream out;
    final LogisimFile file;

    WritingThread(OutputStream out, LogisimFile file) {
      super("WritingThread");
      this.out = out;
      this.file = file;
    }

    @Override
    public void run() {
      try (out) {
        file.write(out, file.loader);
      } catch (IOException e) {
        file.loader.showError(S.get("fileDuplicateError", e.toString()));
      }
    }
  }

  private static class AutosaveThread extends UniquelyNamedThread {
    private static int threadCount = 0;

    private volatile boolean run;
    private volatile boolean retired;
    private final LogisimFile file;
    private final LongSupplier intervalMillis;

    AutosaveThread(LogisimFile file, LongSupplier intervalMillis) {
      super("AutosaveThread-" + threadCount++);
      this.file = file;
      this.intervalMillis = intervalMillis;
      run = true;
    }

    @Override
    public void run() {
      while (run) {
        try {
          sleep(intervalMillis.getAsLong());
        } catch (InterruptedException ignored) {
          continue; // If thread is interrupted go to beginning of loop immediately
        }
        if (!run) return;
        if (!file.isAutosaveDirty) continue;
        // Clear first, so an edit made while this write runs is picked up next time.
        file.isAutosaveDirty = false;
        boolean saved;
        try {
          saved = file.getLoader().autosave(file);
        } catch (RuntimeException e) {
          // Serialising while the circuit is edited can fail transiently; try again next time
          // rather than ending autosave for the rest of the session.
          logger.warn("Autosave of {} failed; retrying at the next interval", file.name, e);
          file.isAutosaveDirty = true;
          continue;
        }
        if (!saved) {
          file.isAutosaveDirty = true;
          reportError(file.loader, S.get("autosaveError", file.name));
          run = false;
        }
        // Clear interrupted status before next iteration
        interrupted();
      }
    }

    void abort() {
      retired = true;
      run = false; // Prepare the thread to stop
      this.interrupt(); // Notify the thread of it
    }

    void reportError(LibraryLoader loader, String message) {
      // Never wait for a modal dialog: the EDT may be joining this worker during replacement.
      SwingUtilities.invokeLater(() -> {
        if (!retired) loader.showError(message);
      });
    }

    LibraryLoader nonBlockingErrors(LibraryLoader delegate) {
      return new LibraryLoader() {
        @Override
        public String getDescriptor(Library library) {
          return delegate.getDescriptor(library);
        }

        @Override
        public Library loadLibrary(String descriptor) {
          return delegate.loadLibrary(descriptor);
        }

        @Override
        public void showError(String description) {
          reportError(delegate, description);
        }
      };
    }
  }

  private static final long SNAPSHOT_POLL_MILLIS = 100;
  private final EventSourceWeakSupport<LibraryListener> listeners = new EventSourceWeakSupport<>();
  private final LinkedList<String> messages = new LinkedList<>();
  private final Options options = new Options();
  private final List<AddTool> tools = new LinkedList<>();
  private final List<Library> libraries = new LinkedList<>();
  private Loader loader;
  private Circuit main = null;
  private String name;
  private boolean isDirty = false;
  private volatile boolean isAutosaveDirty = false;
  private AutosaveThread autosaveThread = null;
  private boolean autosaveLoaded = false;
  // Set when reading reported problems: the project may lack content the file on disk still has.
  private boolean loadedWithErrors = false;

  LogisimFile(Loader loader) {
    this(loader, AppPreferences.AUTOSAVE_ENABLED.getBoolean(),
        () -> AppPreferences.AUTOSAVE_INTERVAL.get() * 1000L);
  }

  // Explicit scheduling allows lifecycle tests without changing global/user preferences.
  LogisimFile(Loader loader, boolean autosaveEnabled, LongSupplier intervalMillis) {
    this.loader = loader;

    // Creates the default project name, adding an underscore if needed
    name = S.get("defaultProjectName");
    if (Projects.windowNamed(name)) {
      for (var i = 2; true; i++) {
        if (!Projects.windowNamed(name + "_" + i)) {
          name += "_" + i;
          break;
        }
      }
    }
    if (autosaveEnabled) {
      this.autosaveThread = new AutosaveThread(this, intervalMillis);
      autosaveThread.start();
    }
  }

  @Override
  public void circuitChanged(CircuitEvent event) {
    final var act = event.getAction();
    if (act == CircuitEvent.ACTION_CHECK_NAME) {
      final var oldname = (String) event.getData();
      final var newname = event.getCircuit().getName();
      if (isNameInUse(newname, event.getCircuit())) {
        OptionPane.showMessageDialog(
                null,
                "\"" + newname + "\": " + S.get("circuitNameExists"),
                "",
                OptionPane.ERROR_MESSAGE);
        event.getCircuit().getStaticAttributes().setValue(CircuitAttributes.NAME_ATTR, oldname);
      }
    }
  }

  // Name check Methods
  private boolean isNameInUse(String name, Circuit changed) {
    if (name.isEmpty()) return false;
    for (final var mylib : getLibraries()) {
      if (isNameInLibraries(mylib, name)) return true;
    }
    for (final var mytool : this.getCircuits()) {
      if (SyntaxChecker.namesEqualForCurrentHdl(name, mytool.getName()) && !mytool.equals(changed))
        return true;
    }
    return false;
  }

  private boolean isNameInLibraries(Library lib, String name) {
    if (name.isEmpty()) return false;
    for (final var mylib : lib.getLibraries()) {
      if (isNameInLibraries(mylib, name)) return true;
    }
    for (final var mytool : lib.getTools()) {
      if (SyntaxChecker.namesEqualForCurrentHdl(name, mytool.getName())) return true;
    }
    return false;
  }

  //
  // creation methods
  //
  public static LogisimFile createNew(Loader loader, Project proj) {
    final var ret = new LogisimFile(loader);
    ret.main = new Circuit("main", ret, proj);
    // The name will be changed in LogisimPreferences
    ret.tools.add(new AddTool(ret.main.getSubcircuitFactory()));
    return ret;
  }

  private static String getFirstLine(BufferedInputStream in) throws IOException {
    final var first = new byte[512];
    in.mark(first.length - 1);
    in.read(first);
    in.reset();

    int lineBreak = first.length;
    for (var i = 0; i < lineBreak; i++) {
      if (first[i] == '\n') {
        lineBreak = i;
      }
    }
    return new String(first, 0, lineBreak, StandardCharsets.UTF_8);
  }

  public static LogisimFile load(File file, Loader loader) throws IOException {
    // Get the Path of this file's autosave if it exists
    final var autosave = Loader.findAutosaveFile(file);

    // Without a GUI nobody can answer the prompt: open the real file and keep the recovery file.
    if (autosave.isPresent() && Main.hasGui()) {
      final var res = loader.showOptions(S.get("contentHandleAutosave", file.getName()),
          S.get("titleHandleAutosave"), new String[] {S.get("loadOption"), S.get("discardOption")},
          0);

      if (res == JOptionPane.CLOSED_OPTION) { // If the prompt was closed do nothing and fail
        return null;
      } else if (res == 0) { // If load is selected select the autosave to be loaded
        loader.setAutosavePath(autosave.get());
        try {
          final var recovered = loadFrom(autosave.get(), loader, file);
          // Remember the autosave was loaded: its edits are not in the real file yet.
          recovered.autosaveLoaded = true;
          return recovered;
        } catch (LoaderException e) {
          throw e;
        } catch (Exception e) {
          // An unreadable recovery file must not block the real one, nor be blamed on it.
          logger.warn("Unable to read autosave {}", autosave.get(), e);
          loader.showError(
              S.get("autosaveUnreadable", file.getName(), describeLoadFailure(autosave.get(), e)));
        }
      } else if (res == 1) {
        autosave.get().delete();
      }
    }

    try {
      return loadFrom(file, loader, file);
    } catch (LoaderException | IOException e) {
      throw e;
    } catch (Exception e) {
      // Reported once, by whoever asked for the file, as "could not open": see describeLoadFailure.
      logger.warn("Unable to read {}", file, e);
      throw new LoadFailure(e);
    }
  }

  /**
   * Explains in plain words why {@code file} could not be opened, for a message of the form
   * "Could not open “name”: reason". The exception itself is for the details of an error report.
   */
  static String describeLoadFailure(File file, Throwable failure) {
    var cause = failure;
    while (cause instanceof LoadFailure && cause.getCause() != null) cause = cause.getCause();
    if (cause instanceof FileNotFoundException || cause instanceof NoSuchFileException) {
      return file != null && !file.exists()
          ? S.get("fileReasonNotFound")
          : S.get("fileReasonUnreadable");
    }
    if (file != null && file.isFile() && file.length() == 0) return S.get("fileReasonEmpty");
    if (cause instanceof SAXParseException parse) {
      return parse.getLineNumber() > 0
          ? S.get("fileReasonMalformedXmlAt", parse.getLineNumber())
          : S.get("fileReasonMalformedXml");
    }
    if (cause instanceof SAXException) return S.get("fileReasonNotProject");
    if (cause instanceof IOException && StringUtil.isNotEmpty(cause.getMessage())) {
      final var message = cause.getMessage();
      return message.endsWith(".") ? message.substring(0, message.length() - 1) : message;
    }
    return S.get("fileReasonInvalid");
  }

  /**
   * Reads {@code source} as the project {@code base}. Falls back to a Latin-1 reader for files from
   * Logisim versions prior to 2.5.1, which were not saved as UTF-8 although the XML declared it.
   * Library loading failures ({@link LoaderException}) have already been reported and abort; a
   * file that cannot be opened at all surfaces as an {@link IOException}.
   */
  private static LogisimFile loadFrom(File source, Loader loader, File base) throws Exception {
    Throwable firstExcept;
    final var inputStream = new FileInputStream(source);
    try (inputStream) {
      return loadSub(inputStream, loader, base);
    } catch (LoaderException e) {
      throw e;
    } catch (Throwable t) {
      firstExcept = t;
    }
    // Such files are Latin-1; the platform default (UTF-8 since JDK 18) would mangle them.
    try (final var legacy =
        new ReaderInputStream(new FileReader(source, StandardCharsets.ISO_8859_1), "UTF8")) {
      return loadSub(legacy, loader, base);
    } catch (LoaderException e) {
      throw e;
    } catch (Exception ignored) {
      // Report the original failure: the legacy reader is only a compatibility attempt.
    }
    // Parse errors are reported to the user; wrap them so they are not taken for I/O failures.
    throw new LoadFailure(firstExcept);
  }

  /** Carries the original parse failure, reported with its own message. */
  private static final class LoadFailure extends IOException {
    private static final long serialVersionUID = 1L;

    LoadFailure(Throwable cause) {
      super(cause);
    }

    @Override
    public String toString() {
      return getCause().toString();
    }
  }

  public static LogisimFile load(InputStream in, Loader loader) throws IOException {
    try {
      return loadSub(in, loader);
    } catch (SAXException e) {
      e.printStackTrace();
      loader.showError(S.get("xmlFormatError", e.toString()));
      return null;
    }
  }

  public static LogisimFile loadSub(InputStream in, Loader loader) throws IOException, SAXException {
    return (loadSub(in, loader, null));
  }

  public static LogisimFile loadSub(InputStream in, Loader loader, File file) throws IOException, SAXException {
    // fetch first line and then reset
    final var inBuffered = new BufferedInputStream(in);
    final var firstLine = getFirstLine(inBuffered);

    if (firstLine == null) {
      throw new IOException(S.get("fileReasonEmpty"));
    } else if (firstLine.equals("Logisim v1.0")) {
      // if this is a 1.0 file, then set up a pipe to translate to
      // 2.0 and then interpret as a 2.0 file
      throw new IOException(S.get("fileReasonVersion1"));
    }

    final var xmlReader = new XmlReader(loader, file);
    /* Can set the project pointer to zero as it is fixed later */
    final var ret = xmlReader.readLibrary(inBuffered, null);
    ret.loader = loader;
    return ret;
  }

  public void addCircuit(Circuit circuit) {
    addCircuit(circuit, tools.size());
  }

  public void addCircuit(Circuit circuit, int index) {
    circuit.addCircuitListener(this);
    final var tool = new AddTool(circuit.getSubcircuitFactory());
    tools.add(index, tool);
    if (tools.size() == 1) setMainCircuit(circuit);
    fireEvent(LibraryEvent.ADD_TOOL, tool);
  }

  public void addVhdlContent(VhdlContent content) {
    addVhdlContent(content, tools.size());
  }

  /** Adds a VHDL entity or a Verilog module ({@link VhdlContent#isVerilog()}) to the project. */
  public void addVhdlContent(VhdlContent content, int index) {
    final var tool = new AddTool(content.createFactory());
    tools.add(index, tool);
    fireEvent(LibraryEvent.ADD_TOOL, tool);
  }

  public void addLibrary(Library lib) {
    if (!lib.getName().equals(BaseLibrary._ID)) {
      for (final var tool : lib.getTools()) {
        if (tool instanceof AddTool addTool) {
          final var atrs = addTool.getAttributeSet();
          for (final var attr : atrs.getAttributes()) {
            if (attr == CircuitAttributes.NAME_ATTR) atrs.setReadOnly(attr, true);
          }
        }
      }
    }
    libraries.add(lib);
    fireEvent(LibraryEvent.ADD_LIBRARY, lib);
  }

  //
  // listener methods
  //
  @Override
  public void addLibraryListener(LibraryListener what) {
    listeners.add(what);
  }

  //
  // modification actions
  //
  public void addMessage(String msg) {
    messages.addLast(msg);
  }

  public LogisimFile cloneLogisimFile(Loader newloader) {
    final var reader = new PipedInputStream();
    final var writer = new PipedOutputStream();
    try {
      reader.connect(writer);
    } catch (IOException e) {
      newloader.showError(S.get("fileDuplicateError", e.toString()));
      return null;
    }
    new WritingThread(writer, this).start();
    try {
      return LogisimFile.load(reader, newloader);
    } catch (IOException e) {
      newloader.showError(S.get("fileDuplicateError", e.toString()));
      try {
        reader.close();
      } catch (IOException ignored) {
      }
      return null;
    }
  }

  public boolean contains(Circuit circ) {
    for (final var tool : tools) {
      if (tool.getFactory() instanceof SubcircuitFactory factory) {
        if (factory.getSubcircuit() == circ) return true;
      }
    }
    return false;
  }

  public boolean contains(VhdlContent content) {
    for (final var tool : tools) {
      if (tool.getFactory() instanceof VhdlEntity factory) {
        if (factory.getContent() == content) return true;
      }
    }
    return false;
  }

  public boolean containsFactory(String name) {
    for (final var tool : tools) {
      if (tool.getFactory() instanceof VhdlEntity factory) {
        if (factory.getContent().getName().equals(name)) return true;
      } else if (tool.getFactory() instanceof SubcircuitFactory factory) {
        if (factory.getSubcircuit().getName().equals(name)) return true;
      }
    }
    return false;
  }

  private Tool findTool(Library lib, Tool query) {
    for (final var tool : lib.getTools()) {
      if (tool.equals(query)) return tool;
    }
    return null;
  }

  Tool findTool(Tool query) {
    for (final var lib : getLibraries()) {
      final var ret = findTool(lib, query);
      if (ret != null) return ret;
    }
    return null;
  }

  private void fireEvent(int action, Object data) {
    final var e = new LibraryEvent(this, action, data);
    for (final var l : listeners) {
      l.libraryChanged(e);
    }
  }

  public AddTool getAddTool(Circuit circ) {
    for (final var tool : tools) {
      if (tool.getFactory() instanceof SubcircuitFactory factory) {
        if (factory.getSubcircuit() == circ) {
          return tool;
        }
      }
    }
    return null;
  }

  public AddTool getAddTool(VhdlContent content) {
    for (final var tool : tools) {
      if (tool.getFactory() instanceof VhdlEntity factory) {
        if (factory.getContent() == content) {
          return tool;
        }
      }
    }
    return null;
  }

  public Circuit getCircuit(String name) {
    if (name == null) return null;
    for (final var tool : tools) {
      if (tool.getFactory() instanceof SubcircuitFactory factory) {
        if (name.equals(factory.getName())) return factory.getSubcircuit();
      }
    }
    return null;
  }

  public VhdlContent getVhdlContent(String name) {
    if (name == null) return null;
    for (final var tool : tools) {
      if (tool.getFactory() instanceof VhdlEntity factory) {
        if (name.equals(factory.getName())) return factory.getContent();
      }
    }
    return null;
  }

  public int getCircuitCount() {
    return getCircuits().size();
  }

  public List<Circuit> getCircuits() {
    final var ret = new ArrayList<Circuit>(tools.size());
    for (final var tool : tools) {
      if (tool.getFactory() instanceof SubcircuitFactory factory) {
        ret.add(factory.getSubcircuit());
      }
    }
    return ret;
  }

  public int indexOfCircuit(Circuit circ) {
    for (var i = 0; i < tools.size(); i++) {
      final var tool = tools.get(i);
      if (tool.getFactory() instanceof SubcircuitFactory factory) {
        if (factory.getSubcircuit() == circ) {
          return i;
        }
      }
    }
    return -1;
  }

  /** The project's HDL components: VHDL entities and Verilog modules, in toolbox order. */
  public List<VhdlContent> getVhdlContents() {
    final var ret = new ArrayList<VhdlContent>(tools.size());
    for (final var tool : tools) {
      if (tool.getFactory() instanceof VhdlEntity factory) {
        ret.add(factory.getContent());
      }
    }
    return ret;
  }

  public int indexOfVhdl(VhdlContent vhdl) {
    for (var i = 0; i < tools.size(); i++) {
      final var tool = tools.get(i);
      if (tool.getFactory() instanceof VhdlEntity factory) {
        if (factory.getContent() == vhdl) {
          return i;
        }
      }
    }
    return -1;
  }

  @Override
  public List<Library> getLibraries() {
    return libraries;
  }

  public Loader getLoader() {
    return loader;
  }

  public Circuit getMainCircuit() {
    return main;
  }

  public String getMessage() {
    return (messages.isEmpty()) ? null : messages.removeFirst();
  }

  //
  // access methods
  //
  @Override
  public String getName() {
    return name;
  }

  public Options getOptions() {
    return options;
  }

  @Override
  public List<AddTool> getTools() {
    return tools;
  }

  public String getUnloadLibraryMessage(Library lib) {
    final var factories = new HashSet<ComponentFactory>();
    for (final var tool : lib.getTools()) {
      if (tool instanceof AddTool addTool) {
        factories.add(addTool.getFactory());
      }
    }

    for (final var circuit : getCircuits()) {
      for (final var comp : circuit.getNonWires()) {
        if (factories.contains(comp.getFactory())) {
          return S.get("unloadUsedError", circuit.getName());
        }
      }
    }

    final var tb = options.getToolbarData();
    final var mm = options.getMouseMappings();
    for (final var t : lib.getTools()) {
      if (tb.usesToolFromSource(t)) {
        return S.get("unloadToolbarError");
      }
      if (mm.usesToolFromSource(t)) {
        return S.get("unloadMappingError");
      }
    }

    return null;
  }

  @Override
  public boolean isDirty() {
    return isDirty;
  }

  public void moveCircuit(AddTool tool, int index) {
    int oldIndex = tools.indexOf(tool);
    if (oldIndex < 0) {
      tools.add(index, tool);
      fireEvent(LibraryEvent.ADD_TOOL, tool);
    } else {
      AddTool value = tools.remove(oldIndex);
      tools.add(index, value);
      fireEvent(LibraryEvent.MOVE_TOOL, tool);
    }
  }

  public void removeCircuit(Circuit circuit) {
    if (getCircuitCount() <= 1) {
      throw new RuntimeException("Cannot remove last circuit");
    }

    int index = indexOfCircuit(circuit);
    if (index >= 0) {
      final var successor = mainCircuitAfterRemoving(circuit);
      final Tool circuitTool = tools.remove(index);

      if (main == circuit) setMainCircuit(successor);
      fireEvent(LibraryEvent.REMOVE_TOOL, circuitTool);
    }
  }

  /**
   * The circuit that is main once {@code circuit} is removed: the current main circuit, or if
   * that is the one removed, the first remaining circuit in the list (never a VHDL entity).
   */
  public Circuit mainCircuitAfterRemoving(Circuit circuit) {
    if (main != circuit) return main;
    for (final var candidate : getCircuits()) {
      if (candidate != circuit) return candidate;
    }
    return null;
  }

  public void removeVhdl(VhdlContent vhdl) {
    final var index = indexOfVhdl(vhdl);
    if (index >= 0) {
      final Tool vhdlTool = tools.remove(index);
      fireEvent(LibraryEvent.REMOVE_TOOL, vhdlTool);
    }
  }

  @Override
  public boolean removeLibrary(String name) {
    int index = -1;
    for (final var lib : libraries)
      if (lib.getName().equals(name))
        index = libraries.indexOf(lib);
    if (index < 0) return false;
    libraries.remove(index);
    return true;
  }

  public void removeLibrary(Library lib) {
    libraries.remove(lib);
    fireEvent(LibraryEvent.REMOVE_LIBRARY, lib);
  }

  @Override
  public void removeLibraryListener(LibraryListener what) {
    listeners.remove(what);
  }

  public void setDirty(boolean value) {
    if (isDirty != value) {
      isDirty = value;
      fireEvent(LibraryEvent.DIRTY_STATE, value ? Boolean.TRUE : Boolean.FALSE);
    }
    // The autosave dirty value must be set to dirty at the same time as for normal
    // saves, and at a normal save the autosave will also become clean
    if (isAutosaveDirty != value) {
      isAutosaveDirty = value;
    }
  }

  public void setMainCircuit(Circuit circuit) {
    if (circuit == null) return;
    this.main = circuit;
    fireEvent(LibraryEvent.SET_MAIN, circuit);
  }

  public void setName(String name) {
    this.name = name;
    fireEvent(LibraryEvent.SET_NAME, name);
  }

  public static LogisimFile createEmpty(Loader loader) {
    return new LogisimFile(loader);
  }

  //
  // other methods
  //
  public void write(OutputStream out, LibraryLoader loader) throws IOException {
    write(out, loader, null, null, false);
  }

  public void write(OutputStream out, LibraryLoader loader, File dest) throws IOException {
    write(out, loader, dest, null, false);
  }

  void write(OutputStream out, LibraryLoader loader, String mainCircFile) throws IOException {
    write(out, loader, null, mainCircFile, false);
  }

  void write(OutputStream out, LibraryLoader loader, String mainCircFile, boolean recurse)
      throws IOException {
    write(out, loader, null, mainCircFile, recurse);
  }

  void write(OutputStream out, LibraryLoader loader, File dest, String mainCircFile)
      throws IOException {
    write(out, loader, dest, mainCircFile, false);
  }

  void write(OutputStream out, LibraryLoader loader, File dest, String mainCircFile, boolean recurse)
      throws IOException {
    if (Thread.currentThread() instanceof AutosaveThread worker) {
      // XmlWriter reports some errors directly; catching exceptions alone is not sufficient.
      loader = worker.nonBlockingErrors(loader);
    }
    final var errors = new WriteErrors(loader);
    try {
      XmlWriter.write(this, out, errors, dest, mainCircFile, recurse);
    } catch (TransformerConfigurationException e) {
      throw new IOException("internal error configuring transformer", e);
    } catch (ParserConfigurationException e) {
      throw new IOException("internal error configuring parser", e);
    } catch (TransformerException e) {
      org.slf4j.LoggerFactory.getLogger(LogisimFile.class).error("XML Transformation Exception during save", e);
      final var msg = e.getMessage();
      var err = S.get("xmlConversionError");
      if (msg != null) err += ": " + msg;
      throw new IOException(err, e);
    } catch (LoadFailedException | LoaderException e) {
      throw new IOException(e.getMessage(), e);
    }
    if (errors.firstError != null) throw new IOException(errors.firstError);
  }

  /**
   * The content an autosave writes to {@code destination}, or {@code null} if the project has been
   * saved since the autosave was due.
   *
   * <p>The editor changes circuits on the event thread without taking the circuit locks, so an
   * autosave worker must never walk them itself: it has the event thread take the snapshot and
   * waits for it, giving up if the worker is stopped meanwhile (the event thread may be waiting
   * for the worker to end). Problems are logged rather than shown, as nobody asked for this save.
   */
  byte[] autosaveSnapshot(LibraryLoader loader, File destination) throws IOException {
    if (!(Thread.currentThread() instanceof AutosaveThread worker)) {
      return serialize(loader, destination);
    }
    final var quiet = quietLoader(loader);
    final var snapshot = new CompletableFuture<byte[]>();
    SwingUtilities.invokeLater(
        () -> {
          if (!worker.run) {
            snapshot.cancel(false);
            return;
          }
          try {
            snapshot.complete(isDirty ? serialize(quiet, destination) : null);
          } catch (Throwable t) {
            snapshot.completeExceptionally(t);
          }
        });
    return awaitSnapshot(snapshot, () -> worker.run);
  }

  /**
   * Waits for {@code snapshot} while {@code keepWaiting} holds. An interrupt (a save waking the
   * worker) does not end the wait; stopping does.
   */
  static <T> T awaitSnapshot(CompletableFuture<T> snapshot, BooleanSupplier keepWaiting)
      throws IOException {
    var interrupted = false;
    try {
      while (true) {
        if (!keepWaiting.getAsBoolean()) {
          throw new InterruptedIOException("autosave stopped before the snapshot was taken");
        }
        try {
          return snapshot.get(SNAPSHOT_POLL_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
          // check keepWaiting again
        } catch (InterruptedException e) {
          interrupted = true;
        } catch (CancellationException e) {
          throw new InterruptedIOException("autosave stopped before the snapshot was taken");
        } catch (ExecutionException e) {
          final var cause = e.getCause();
          if (cause instanceof IOException io) throw io;
          if (cause instanceof RuntimeException runtime) throw runtime;
          if (cause instanceof Error error) throw error;
          throw new IOException(cause);
        }
      }
    } finally {
      if (interrupted) Thread.currentThread().interrupt();
    }
  }

  private byte[] serialize(LibraryLoader loader, File destination) throws IOException {
    final var out = new ByteArrayOutputStream();
    write(out, loader, destination, null);
    return out.toByteArray();
  }

  /** {@code delegate}, except that problems are logged instead of shown. */
  private static LibraryLoader quietLoader(LibraryLoader delegate) {
    return new LibraryLoader() {
      @Override
      public String getDescriptor(Library library) {
        return delegate.getDescriptor(library);
      }

      @Override
      public Library loadLibrary(String descriptor) {
        return delegate.loadLibrary(descriptor);
      }

      @Override
      public void showError(String description) {
        logger.warn("Autosave: {}", description);
      }
    };
  }

  /** XmlWriter can report missing content without throwing; that still invalidates the save. */
  private static final class WriteErrors implements LibraryLoader {
    private final LibraryLoader delegate;
    private String firstError;

    WriteErrors(LibraryLoader delegate) {
      this.delegate = delegate;
    }

    @Override
    public String getDescriptor(Library library) {
      return delegate.getDescriptor(library);
    }

    @Override
    public Library loadLibrary(String descriptor) {
      return delegate.loadLibrary(descriptor);
    }

    @Override
    public void showError(String description) {
      if (firstError == null) firstError = description;
      delegate.showError(description);
    }
  }

  void interruptAutosaveThread() {
    if (autosaveThread == null) return;
    autosaveThread.interrupt();
  }

  public void stopAutosaveThread(boolean delete) {
    if (autosaveThread == null) return;
    if (delete) {
      retireAutosaveThread();
      loader.deleteAutosave();
    } else {
      autosaveThread.abort();
    }
  }

  /** Stops and joins this file's writer without deleting any loader-owned recovery file. */
  public void retireAutosaveThread() {
    final var worker = autosaveThread;
    if (worker == null) return;
    worker.abort();
    var interrupted = false;
    while (worker.isAlive()) {
      try {
        worker.join();
      } catch (InterruptedException ex) {
        // Replacement cannot proceed while the old writer can still overwrite recovery.
        interrupted = true;
      }
    }
    if (interrupted) Thread.currentThread().interrupt();
  }

  /** Records that this project was read from a recovery file, not from the file it belongs to. */
  void markAutosaveLoaded() {
    autosaveLoaded = true;
  }

  public boolean isAutosaveLoaded() {
    return autosaveLoaded;
  }

  /**
   * Whether problems were reported while reading this file, meaning some of its content (unknown
   * components, unreadable settings) may be missing here. Saving over the original then loses it.
   */
  public boolean isLoadedWithErrors() {
    return loadedWithErrors;
  }

  void setLoadedWithErrors(boolean value) {
    loadedWithErrors = value;
  }

  /** Called once this project's content has been written, so the original is no longer at risk. */
  public void clearLoadedWithErrors() {
    loadedWithErrors = false;
  }
}

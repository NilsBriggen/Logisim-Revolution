/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.std.wiring.WiringLibrary;
import com.cburch.logisim.tools.AddTool;
import com.cburch.logisim.tools.Library;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoadedLibraryReloadTest {

  @TempDir Path tempDir;

  @Test
  void reloadRebindsExistingSubcircuitInstancesToNewFactory() throws Exception {
    final var libraryPath = tempDir.resolve("library.circ");
    final var replacementPath = tempDir.resolve("replacement.circ");
    final var hostPath = tempDir.resolve("host.circ");

    final var originalLoader = new RecordingLoader();
    save(originalLoader, newProject(originalLoader, "Child"), libraryPath.toFile());

    final var replacementLoader = new RecordingLoader();
    final var replacementFile = newProject(replacementLoader, "Child");
    replacementFile.addLibrary(
        replacementLoader.getBuiltin().getLibrary(WiringLibrary._ID));
    addPin(replacementFile.getMainCircuit());
    save(replacementLoader, replacementFile, replacementPath.toFile());

    final var hostLoader = new RecordingLoader();
    final var hostFile = newProject(hostLoader, "Host");
    final var authoringChildLibrary =
        assertInstanceOf(
            LoadedLibrary.class, hostLoader.loadLogisimLibrary(libraryPath.toFile()));
    hostFile.addLibrary(authoringChildLibrary);
    addSubcircuit(hostFile.getMainCircuit(), tool(authoringChildLibrary, "Child"));
    save(hostLoader, hostFile, hostPath.toFile());

    final var loadedHost =
        assertInstanceOf(LoadedLibrary.class, hostLoader.loadLogisimLibrary(hostPath.toFile()));
    final var loadedHostFile = assertInstanceOf(LogisimFile.class, loadedHost.getBase());
    final var loadedHostProject = new Project(loadedHostFile);
    loadedHostFile.getMainCircuit().setProject(loadedHostProject);
    final var loadedChildLibrary =
        assertInstanceOf(LoadedLibrary.class, loadedHostFile.getLibraries().get(0));
    final var originalComponent = onlyComponent(loadedHostFile.getMainCircuit());
    final var originalFactory = originalComponent.getFactory();
    assertSame(tool(loadedChildLibrary, "Child").getFactory(), originalFactory);

    final LibraryListener listener = event -> {};
    loadedChildLibrary.addLibraryListener(listener);
    try {
      Files.copy(replacementPath, libraryPath, StandardCopyOption.REPLACE_EXISTING);
      hostLoader.reload(loadedChildLibrary);
    } finally {
      loadedChildLibrary.removeLibraryListener(listener);
    }

    final var replacementComponent = onlyComponent(loadedHostFile.getMainCircuit());
    final var replacementFactory = tool(loadedChildLibrary, "Child").getFactory();
    assertNotSame(originalComponent, replacementComponent);
    assertNotSame(originalFactory, replacementComponent.getFactory());
    assertSame(replacementFactory, replacementComponent.getFactory());
    assertEquals(
        1,
        assertInstanceOf(SubcircuitFactory.class, replacementFactory)
            .getSubcircuit()
            .getNonWires()
            .size());
    assertFalse(hostLoader.hasErrors(), hostLoader.errors());
  }

  @Test
  void reloadTellsListenersThatTheToolsChanged() throws Exception {
    // The explorer tree rebuilds a library's children on ADD_TOOL/REMOVE_TOOL. A reloaded tool
    // has the same name but a new factory, and no event was fired for it, so the tree kept
    // showing the old tools.
    final var libraryPath = tempDir.resolve("library.circ");
    final var loader = new RecordingLoader();
    save(loader, newProject(loader, "Child"), libraryPath.toFile());

    final var hostLoader = new RecordingLoader();
    final var library =
        assertInstanceOf(LoadedLibrary.class, hostLoader.loadLogisimLibrary(libraryPath.toFile()));
    final var oldTool = tool(library, "Child");

    final var events = new ArrayList<LibraryEvent>();
    final LibraryListener listener = events::add;
    library.addLibraryListener(listener);
    try {
      hostLoader.reload(library);
    } finally {
      library.removeLibraryListener(listener);
    }

    final var newTool = tool(library, "Child");
    assertNotSame(oldTool, newTool);
    assertTrue(
        events.stream()
            .anyMatch(e -> e.getAction() == LibraryEvent.REMOVE_TOOL && e.getData() == oldTool));
    assertTrue(
        events.stream()
            .anyMatch(e -> e.getAction() == LibraryEvent.ADD_TOOL && e.getData() == newTool));
    assertFalse(hostLoader.hasErrors(), hostLoader.errors());
  }

  private static void addPin(Circuit circuit) {
    final var component =
        Pin.FACTORY.createComponent(
            Location.create(100, 100, true), Pin.FACTORY.createAttributeSet());
    final var mutation = new CircuitMutation(circuit);
    mutation.add(component);
    mutation.execute();
  }

  private static void addSubcircuit(Circuit circuit, AddTool tool) {
    final var factory = tool.getFactory();
    final var component =
        factory.createComponent(Location.create(100, 100, true), factory.createAttributeSet());
    final var mutation = new CircuitMutation(circuit);
    mutation.add(component);
    mutation.execute();
  }

  private static Component onlyComponent(Circuit circuit) {
    final var components = circuit.getNonWires();
    assertEquals(1, components.size());
    return components.iterator().next();
  }

  private static LogisimFile newProject(RecordingLoader loader, String circuitName) {
    final var file = LogisimFile.createNew(loader, null);
    file.getMainCircuit().setName(circuitName);
    return file;
  }

  private static void save(RecordingLoader loader, LogisimFile file, File path) {
    assertTrue(loader.save(file, path), loader.errors());
    assertFalse(loader.hasErrors(), loader.errors());
  }

  private static AddTool tool(Library library, String name) {
    final var tool = library.getTool(name);
    assertNotNull(tool, "tool not found: " + name);
    return assertInstanceOf(AddTool.class, tool);
  }

  private static class RecordingLoader extends Loader {
    private final List<String> errors = new ArrayList<>();

    RecordingLoader() {
      super(null);
    }

    String errors() {
      return String.join("\n", errors);
    }

    boolean hasErrors() {
      return !errors.isEmpty();
    }

    @Override
    public void showError(String description) {
      errors.add(description);
    }
  }
}

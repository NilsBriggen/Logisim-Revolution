/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.file;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.tools.Library;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SaveFailureTest {
  @TempDir Path directory;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void failedSavePreservesOriginalBackupRecoveryAndDirtyState(boolean reportedError)
      throws Exception {
    final var loader = new RecordingLoader();
    final var destination = directory.resolve("project.circ").toFile();
    final var good = newFile(loader);
    assertTrue(loader.save(good, destination));
    assertTrue(loader.autosave(good));
    final var recovery = Loader.findAutosaveFile(destination).orElseThrow().toPath();
    final var originalBytes = Files.readAllBytes(destination.toPath());
    final var recoveryBytes = Files.readAllBytes(recovery);
    final var backup = directory.resolve("project.bak");
    Files.writeString(backup, "older backup");
    final var bad = failingFile(loader, reportedError);
    bad.setName("unsaved name");
    bad.setDirty(true);

    assertFalse(loader.save(bad, destination));

    assertArrayEquals(originalBytes, Files.readAllBytes(destination.toPath()));
    assertArrayEquals(recoveryBytes, Files.readAllBytes(recovery));
    assertEquals("older backup", Files.readString(backup));
    assertEquals(destination, loader.getMainFile());
    assertEquals("unsaved name", bad.getName());
    assertTrue(bad.isDirty());
    assertFalse(loader.errors.isEmpty());
    assertNoStagedFiles();
  }

  @Test
  void failedSaveAsDoesNotPublishPartialFileOrChangeLoaderTarget() throws Exception {
    final var loader = new RecordingLoader();
    final var original = directory.resolve("original.circ").toFile();
    assertTrue(loader.save(newFile(loader), original));
    final var destination = directory.resolve("new.circ");

    assertFalse(loader.save(failingFile(loader, false), destination.toFile()));

    assertFalse(Files.exists(destination));
    assertEquals(original, loader.getMainFile());
    assertNoStagedFiles();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void failedAutosaveKeepsLastGoodRecovery(boolean reportedError) throws Exception {
    final var loader = new RecordingLoader();
    final var destination = directory.resolve("project.circ").toFile();
    final var good = newFile(loader);
    assertTrue(loader.save(good, destination));
    assertTrue(loader.autosave(good));
    final var recovery = Loader.findAutosaveFile(destination).orElseThrow();
    final var bytes = Files.readAllBytes(recovery.toPath());

    assertFalse(loader.autosave(failingFile(loader, reportedError)));

    assertEquals(recovery, Loader.findAutosaveFile(destination).orElseThrow());
    assertArrayEquals(bytes, Files.readAllBytes(recovery.toPath()));
    assertNoStagedFiles();
  }

  @Test
  void xmlWriterStreamFailureEscapesEvenAfterWritingBytes() {
    final var file = newFile(new RecordingLoader());
    final var written = new ByteArrayOutputStream();
    final var broken = new OutputStream() {
      @Override
      public void write(int value) throws IOException {
        if (written.size() >= 32) throw new IOException("injected output failure");
        written.write(value);
      }
    };

    assertThrows(IOException.class, () -> file.write(broken, file.getLoader()));
    assertEquals(32, written.size());
  }

  @Test
  void reportedXmlErrorIsForwardedAndMakesWriteFail() {
    final var loader = new RecordingLoader();
    final var file = failingFile(loader, true);
    final var bytes = new ByteArrayOutputStream();

    final var error = assertThrows(IOException.class, () -> file.write(bytes, loader));

    assertTrue(error.getMessage().contains("library location unknown"));
    assertEquals(List.of(error.getMessage()), loader.errors);
    assertTrue(bytes.size() > 0, "A nonempty incomplete document must still count as failure");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void bundleExportReturnsFalseAndRestoresWriterAfterFailure(boolean reportedError)
      throws Exception {
    final var loader = new RecordingLoader();
    try (final var previous = new ZipOutputStream(new ByteArrayOutputStream());
        final var output = new ZipOutputStream(new ByteArrayOutputStream())) {
      loader.setZipFile(previous);
      assertFalse(loader.export(failingFile(loader, reportedError), output, "project.circ"));
      assertSame(previous, loader.getZipFile());
    }
  }

  @Test
  void successfulSavePublishesAndThenRemovesRecovery() throws Exception {
    final var loader = new RecordingLoader();
    final var file = newFile(loader);
    final var destination = directory.resolve("project.circ").toFile();
    assertNull(loader.getMainFile());
    assertTrue(loader.save(file, destination));
    assertTrue(loader.autosave(file));
    final var recovery = Loader.findAutosaveFile(destination).orElseThrow();
    file.getMainCircuit().setName("renamed");

    assertTrue(loader.save(file, destination));

    assertTrue(Files.readString(destination.toPath()).contains("name=\"renamed\""));
    assertFalse(recovery.exists());
    assertTrue(loader.errors.isEmpty());
    assertNoStagedFiles();
  }

  private void assertNoStagedFiles() throws IOException {
    try (final var files = Files.list(directory)) {
      assertFalse(files.anyMatch(path -> path.getFileName().toString().startsWith(".logisim-save-")));
    }
  }

  private static LogisimFile newFile(Loader loader) {
    final var file = new LogisimFile(loader, false, () -> 10L);
    file.addCircuit(new Circuit("main", file, null));
    return file;
  }

  private static LogisimFile failingFile(Loader loader, boolean reportedError) {
    if (reportedError) {
      final var file = newFile(loader);
      final var library = mock(Library.class);
      when(library.getName()).thenReturn("missing");
      file.addLibrary(library);
      return file;
    }
    final var file = new LogisimFile(loader, false, () -> 10L) {
      @Override
      void write(OutputStream output, LibraryLoader writer, File destination, String mainCircuit,
          boolean recurse) throws IOException {
        output.write("<project>partial".getBytes(StandardCharsets.UTF_8));
        throw new IOException("injected partial serializer failure");
      }
    };
    file.addCircuit(new Circuit("main", file, null));
    return file;
  }

  private static final class RecordingLoader extends Loader {
    final List<String> errors = new ArrayList<>();

    RecordingLoader() {
      super(null);
    }

    @Override
    public String getDescriptor(Library library) {
      return "missing".equals(library.getName()) ? null : super.getDescriptor(library);
    }

    @Override
    public void showError(String description) {
      errors.add(description);
    }
  }
}

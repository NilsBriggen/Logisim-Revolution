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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LoaderJarInspectionTest {

  @TempDir Path tempDir;

  @Test
  void jarWithoutManifestListsItsTopLevelClasses() throws IOException {
    // Before the fix, a JAR without a manifest made "Load JAR Library" throw a
    // NullPointerException on the event thread, and the user saw nothing.
    final var jar = tempDir.resolve("plain.jar");
    try (final var out = new JarOutputStream(new FileOutputStream(jar.toFile()))) {
      entry(out, "com/example/Zeta.class");
      entry(out, "com/example/MyLibrary.class");
      entry(out, "com/example/MyLibrary$Inner.class");
      entry(out, "com/example/readme.txt");
    }

    final var info = Loader.inspectJar(jar.toFile());

    assertNull(info.manifestClass());
    assertEquals(List.of("com.example.MyLibrary", "com.example.Zeta"), info.classNames());
  }

  @Test
  void manifestLibraryClassIsUsed() throws IOException {
    final var manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest.getMainAttributes().putValue("Library-Class", "com.example.MyLibrary");
    final var jar = tempDir.resolve("lib.jar");
    try (final var out = new JarOutputStream(new FileOutputStream(jar.toFile()), manifest)) {
      entry(out, "com/example/MyLibrary.class");
    }

    assertEquals("com.example.MyLibrary", Loader.inspectJar(jar.toFile()).manifestClass());
  }

  @Test
  void fileThatIsNotAJarIsReportedAsUnreadable() throws IOException {
    final var notJar = tempDir.resolve("notes.jar");
    Files.writeString(notJar, "this is not a zip file");

    assertThrows(IOException.class, () -> Loader.inspectJar(notJar.toFile()));
  }

  private static void entry(JarOutputStream out, String name) throws IOException {
    out.putNextEntry(new ZipEntry(name));
    out.write(new byte[] {0});
    out.closeEntry();
  }
}

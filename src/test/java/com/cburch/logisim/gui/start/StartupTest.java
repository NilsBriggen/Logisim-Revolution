/*
 * Logisim-evolution - digital logic design tool
 * Copyright by the logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.start;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.Main;
import com.cburch.logisim.util.LocaleManager;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class StartupTest {
  private boolean originalHeadless;
  private Locale originalLocale;

  @BeforeEach
  void recordGlobalState() {
    originalHeadless = Main.headless;
    originalLocale = LocaleManager.getLocale();
  }

  @AfterEach
  void restoreGlobalState() {
    Main.headless = originalHeadless;
    LocaleManager.setLocale(originalLocale);
  }

  @Test
  void localeOptionAppliesToHelpText() {
    final var originalOut = System.out;
    final var output = new ByteArrayOutputStream();
    try {
      System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));

      Startup.parseArgs(new String[] {"--tty", "table", "--locale", "zh", "--help"});

      final var helpText = output.toString(StandardCharsets.UTF_8);
      assertTrue(helpText.contains("显示本参数摘要帮助页。"));
      assertFalse(helpText.contains("Displays this argument summary help page."));
    } finally {
      System.setOut(originalOut);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"--help", "--version"})
  void informationalOptionsRequestSuccessfulTermination(String option) {
    final var startup = parseWithoutOutput("--tty", "table", option);

    assertNotNull(startup);
    assertTrue(startup.shallQuit());
  }

  @ParameterizedTest(name = "{0}")
  @MethodSource("invalidOptionArguments")
  void invalidOptionValuesFailParsing(String description, String[] args) {
    assertNull(Startup.parseArgs(args), description);
  }

  @Test
  void parsingResultBelongsToItsStartupInstance() {
    final var regularStartup = Startup.parseArgs(new String[] {"--tty", "table", "test.circ"});
    final var helpStartup = parseWithoutOutput("--tty", "table", "--help");

    assertNotNull(regularStartup);
    assertFalse(regularStartup.shallQuit());
    assertNotNull(helpStartup);
    assertTrue(helpStartup.shallQuit());
    assertFalse(regularStartup.shallQuit());
  }

  @Test
  void fpgaCablePreservesExactQuartusNameForFpgaDownload() {
    final var cableName = "USB-Blaster II [3-2]";

    final var startup =
        Startup.parseArgs(
            new String[] {
              "--fpga-cable", cableName,
              "--test-fpga", "test.circ", "main", "board"
            });

    assertNotNull(startup);
    assertEquals(cableName, startup.getFpgaCableName());

    final var cableAfterFpgaOption =
        Startup.parseArgs(
            new String[] {
              "--test-fpga", "test.circ", "main", "board",
              "--fpga-cable", cableName
            });
    assertNotNull(cableAfterFpgaOption);
    assertEquals(cableName, cableAfterFpgaOption.getFpgaCableName());
  }

  @Test
  void fpgaCableRequiresFpgaDownload() {
    assertNull(
        Startup.parseArgs(
            new String[] {"--tty", "table", "--fpga-cable", "USB-Blaster [1-1]", "test.circ"}));
  }

  /** "--load FILE" used to take the project file that followed it for a memory label. */
  @Test
  void loadOptionDoesNotSwallowTheProjectFile() {
    final var unlabeled =
        Startup.parseArgs(new String[] {"--tty", "table", "--load", "memory.hex", "cpu.circ"});
    assertNotNull(unlabeled);
    assertEquals(List.of(new File("cpu.circ")), unlabeled.getFilesToOpen());
    assertEquals(Map.of("", new File("memory.hex")), unlabeled.getMemoryLoadFiles());

    final var labeled =
        Startup.parseArgs(
            new String[] {"--tty", "table", "--load", "memory.hex", "rom", "cpu.circ"});
    assertNotNull(labeled);
    assertEquals(List.of(new File("cpu.circ")), labeled.getFilesToOpen());
    assertEquals(Map.of("rom", new File("memory.hex")), labeled.getMemoryLoadFiles());
  }

  @ParameterizedTest
  @ValueSource(strings = {"--test-vector", "--new-file-format", "--test-circuit"})
  void batchModesRunHeadless(String option) {
    Main.headless = false;
    final var args =
        switch (option) {
          case "--test-vector" -> new String[] {option, "main", "vectors.txt", "test.circ"};
          case "--new-file-format" -> new String[] {option, "in.circ", "out.circ"};
          default -> new String[] {option, "test.circ"};
        };

    assertNotNull(Startup.parseArgs(args));
    assertTrue(Main.headless, option + " must not need a display");
  }

  @Test
  void testVectorExitCodeReflectsTheResult(@TempDir File dir) throws IOException {
    final var circuit = writeInverter(dir, "inv.circ", "");
    final var passing = new File(dir, "pass.txt");
    Files.writeString(passing.toPath(), "a y\n0 1\n1 0\n");
    final var failing = new File(dir, "fail.txt");
    Files.writeString(failing.toPath(), "a y\n0 1\n1 1\n");

    assertEquals(ExitCode.SUCCESS, runBatch("--test-vector", "main", passing.getPath(), circuit.getPath()));
    assertEquals(
        ExitCode.SIMULATION_FAILED,
        runBatch("--test-vector", "main", failing.getPath(), circuit.getPath()));
    assertEquals(
        ExitCode.FAILURE,
        runBatch("--test-vector", "nosuch", passing.getPath(), circuit.getPath()));
  }

  @Test
  void testVectorRefusesProjectsThatLoadWithErrors(@TempDir File dir) throws IOException {
    final var broken =
        writeInverter(dir, "broken.circ", "<comp lib=\"1\" loc=\"(400,400)\" name=\"No Such Gate\"/>");
    final var vectors = new File(dir, "pass.txt");
    Files.writeString(vectors.toPath(), "a y\n0 1\n1 0\n");

    assertEquals(
        ExitCode.LOAD_ERROR,
        runBatch("--test-vector", "main", vectors.getPath(), broken.getPath()));
    assertEquals(
        ExitCode.LOAD_ERROR,
        runBatch("--test-vector", "main", vectors.getPath(), new File(dir, "missing.circ").getPath()));
  }

  @Test
  void newFileFormatWritesTheProjectHeadless(@TempDir File dir) throws IOException {
    final var input = writeInverter(dir, "inv.circ", "");
    final var output = new File(dir, "out.circ");

    assertEquals(ExitCode.SUCCESS, runBatch("--new-file-format", input.getPath(), output.getPath()));
    assertTrue(Files.readString(output.toPath()).contains("NOT Gate"));
  }

  private static int runBatch(String... args) {
    final var startup = parseWithoutOutput(args);
    assertNotNull(startup);
    final var originalOut = System.out;
    try {
      System.setOut(
          new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
      return startup.runBatch();
    } finally {
      System.setOut(originalOut);
    }
  }

  private static File writeInverter(File dir, String name, String extra) throws IOException {
    final var file = new File(dir, name);
    Files.writeString(
        file.toPath(),
        """
        <?xml version="1.0" encoding="UTF-8" standalone="no"?>
        <project source="5.0.0" version="1.0">
          <lib desc="#Wiring" name="0"/>
          <lib desc="#Gates" name="1"/>
          <main name="main"/>
          <circuit name="main">
            <a name="circuit" val="main"/>
            <comp lib="0" loc="(100,100)" name="Pin">
              <a name="label" val="a"/>
            </comp>
            <comp lib="1" loc="(200,100)" name="NOT Gate"/>
            <comp lib="0" loc="(300,100)" name="Pin">
              <a name="facing" val="west"/>
              <a name="output" val="true"/>
              <a name="label" val="y"/>
            </comp>
            <wire from="(100,100)" to="(170,100)"/>
            <wire from="(200,100)" to="(300,100)"/>
        """
            + extra
            + """
              </circuit>
            </project>
            """);
    return file;
  }

  private static Stream<Arguments> invalidOptionArguments() {
    return Stream.of(
        Arguments.of(
            "invalid locale", new String[] {"--tty", "table", "--locale", "not-a-locale"}),
        Arguments.of("invalid TTY format", new String[] {"--tty", "invalid"}),
        Arguments.of(
            "duplicate substitution",
            new String[] {
              "--tty", "table", "--substitute", "old.circ", "new.circ", "--substitute",
              "old.circ", "other.circ"
            }),
        Arguments.of(
            "invalid load arity",
            new String[] {"--tty", "table", "--load", "memory.hex", "label", "extra"}),
        Arguments.of(
            "duplicate load label",
            new String[] {
              "test.circ", "--tty", "table", "--load", "first.hex", "label", "--load",
              "second.hex", "label"
            }),
        Arguments.of(
            "invalid gate style", new String[] {"--tty", "table", "--gates", "invalid"}),
        Arguments.of(
            "invalid geometry", new String[] {"--tty", "table", "--geometry", "invalid"}),
        Arguments.of(
            "invalid template",
            new String[] {"--tty", "table", "--user-template", "missing-template.circ"}),
        Arguments.of(
            "invalid FPGA flag",
            new String[] {"--test-fpga", "test.circ", "main", "board", "invalid"}));
  }

  private static Startup parseWithoutOutput(String... args) {
    final var originalOut = System.out;
    try {
      System.setOut(
          new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));
      return Startup.parseArgs(args);
    } finally {
      System.setOut(originalOut);
    }
  }
}

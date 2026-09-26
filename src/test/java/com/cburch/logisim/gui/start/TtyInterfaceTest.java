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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cburch.logisim.Main;
import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.Propagator;
import com.cburch.logisim.data.TestVector;
import com.cburch.logisim.file.Loader;
import com.cburch.logisim.file.LogisimFile;
import com.cburch.logisim.proj.Project;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class TtyInterfaceTest {

  @TempDir File tempDir;

  private boolean originalHeadless;

  @BeforeEach
  void runHeadless() {
    // Command-line runs are headless: load errors must be reported, not shown in a dialog.
    originalHeadless = Main.headless;
    Main.headless = true;
  }

  @AfterEach
  void restoreHeadless() {
    Main.headless = originalHeadless;
  }

  @Test
  public void testVectorHeaderIncludesBitWidth() {
    assertEquals("a", TtyInterface.formatTestVectorHeader("a", 1));
    assertEquals("x[4]", TtyInterface.formatTestVectorHeader("x", 4));
  }

  @Test
  public void testVectorHeaderCanBeParsedByTestVector() throws IOException {
    File testFile = new File(tempDir, "tty-table-output.txt");
    try (FileWriter writer = new FileWriter(testFile)) {
      writer.write(
          TtyInterface.formatTestVectorHeader("a", 2)
              + " "
              + TtyInterface.formatTestVectorHeader("x", 4)
              + "\n");
      writer.write("00 0001\n");
      writer.write("11 1000\n");
    }

    TestVector vector = new TestVector(testFile);

    assertEquals("a", vector.columnName[0]);
    assertEquals(2, vector.columnWidth[0].getWidth());
    assertEquals("x", vector.columnName[1]);
    assertEquals(4, vector.columnWidth[1].getWidth());
    assertEquals(2, vector.data.size());
  }

  @Test
  void runReturnsFailureWhenInputCannotBeOpened() {
    final var args = startupFor(new File(tempDir, "missing.circ"), TtyInterface.FORMAT_TABLE);

    assertEquals(TtyInterface.EXIT_LOAD_ERROR, TtyInterface.run(args));
  }

  /** A component dropped while loading would silently change the truth table. */
  @Test
  void loadErrorsStopTheRunWithLoadError() throws IOException {
    final var circuit =
        writeCircuit(
            "broken.circ",
            INVERTER + "    <comp lib=\"1\" loc=\"(400,400)\" name=\"No Such Gate\"/>\n");
    final var args = startupFor(circuit, TtyInterface.FORMAT_TABLE);

    final var err = captureStderr(() -> assertEquals(TtyInterface.EXIT_LOAD_ERROR, TtyInterface.run(args)));

    assertTrue(err.contains("No Such Gate"), err);
  }

  /** Nobody can answer the "locate the library" file chooser on the command line. */
  @Test
  void missingLibraryFailsInsteadOfPrompting() throws IOException {
    final var circuit =
        writeCircuit(
            "missing-lib.circ",
            INVERTER,
            "  <lib desc=\"file#does-not-exist.circ\" name=\"7\"/>\n");
    final var args = startupFor(circuit, TtyInterface.FORMAT_TABLE);

    final var err = captureStderr(() -> assertEquals(TtyInterface.EXIT_LOAD_ERROR, TtyInterface.run(args)));

    assertTrue(err.contains("does-not-exist.circ"), err);
  }

  @Test
  void truthTableOfWorkingCircuitSucceeds() throws IOException {
    final var args = startupFor(writeCircuit("inv.circ", INVERTER), TtyInterface.FORMAT_TABLE);

    assertEquals(TtyInterface.EXIT_SUCCESS, TtyInterface.run(args));
  }

  @Test
  void oscillatingTruthTableReturnsOscillation() throws IOException {
    final var args = startupFor(writeCircuit("osc.circ", OSCILLATOR), TtyInterface.FORMAT_TABLE);

    final var err = captureStderr(() -> assertEquals(TtyInterface.EXIT_OSCILLATION, TtyInterface.run(args)));

    assertTrue(err.contains(S.get("ttyTableOscillationError")), err);
  }

  /** {@code 1 << 31} is negative, so a wider table used to print nothing and succeed. */
  @Test
  void truthTableWithTooManyInputBitsFails() throws IOException {
    final var wide =
        """
            <comp lib="0" loc="(100,100)" name="Pin">
              <a name="width" val="31"/>
              <a name="label" val="a"/>
            </comp>
        """;
    final var args = startupFor(writeCircuit("wide.circ", wide), TtyInterface.FORMAT_TABLE);

    assertEquals(TtyInterface.EXIT_FAILURE, TtyInterface.run(args));
  }

  /** Without a halt pin such a run used to loop forever. */
  @Test
  void simulationWithoutHaltPinFails() throws IOException {
    final var args = startupFor(writeCircuit("nohalt.circ", INVERTER), TtyInterface.FORMAT_HALT);

    final var err = captureStderr(() -> assertEquals(TtyInterface.EXIT_FAILURE, TtyInterface.run(args)));

    assertTrue(err.contains(S.get("ttyNoHaltPinError")), err);
  }

  /** A rejected image leaves the memory empty, so running on would give wrong results. */
  @Test
  void rejectedMemoryImageFails() throws IOException {
    final var circuit =
        writeCircuit("ram.circ", "    <comp lib=\"5\" loc=\"(300,300)\" name=\"RAM\"/>\n");
    final var image = new File(tempDir, "too-long.hex");
    // 300 words do not fit the default 256-word RAM, which the reader rejects with a warning.
    Files.writeString(image.toPath(), "v3.0 hex words plain\n" + "1 ".repeat(300) + "\n");
    final var args = startupFor(circuit, TtyInterface.FORMAT_HALT);
    final var loads = new HashMap<String, File>();
    loads.put("", image);
    when(args.getMemoryLoadFiles()).thenReturn(loads);

    final var err = captureStderr(() -> assertEquals(TtyInterface.EXIT_FAILURE, TtyInterface.run(args)));

    assertTrue(err.contains(image.getPath()), err);
  }

  /** A truth table never uses a memory image, so asking for one must not pass silently. */
  @Test
  void truthTableRefusesMemoryImages() throws IOException {
    final var args = startupFor(writeCircuit("inv.circ", INVERTER), TtyInterface.FORMAT_TABLE);
    final var loads = new HashMap<String, File>();
    loads.put("", new File(tempDir, "memory.hex"));
    when(args.getMemoryLoadFiles()).thenReturn(loads);

    assertEquals(TtyInterface.EXIT_FAILURE, TtyInterface.run(args));
  }

  @Test
  void keyboardReaderStopsAtEndOfInput() throws InterruptedException {
    final var reader = new TtyInterface.StdinThread(new StringReader("ab"));
    reader.start();
    reader.join(10_000);

    assertFalse(reader.isAlive(), "the reader kept running after end of input");
    assertEquals("ab", new String(reader.getBuffer()));
    assertNull(reader.getBuffer());
  }

  @ParameterizedTest
  @ValueSource(ints = {TtyInterface.FORMAT_STATISTICS, TtyInterface.FORMAT_TABLE})
  void analysisOnlyModesReturnSuccess(int format) {
    final var args = startupFor(saveCircuit("analysis.circ"), format);

    assertEquals(TtyInterface.EXIT_SUCCESS, TtyInterface.run(args));
  }

  @Test
  void missingMemoryTargetReturnsFailure() {
    final var args = startupFor(saveCircuit("load.circ"), TtyInterface.FORMAT_HALT);
    final var loads = new HashMap<String, File>();
    loads.put("missing", new File(tempDir, "memory.hex"));
    when(args.getMemoryLoadFiles()).thenReturn(loads);

    assertEquals(TtyInterface.EXIT_FAILURE, TtyInterface.run(args));
  }

  @Test
  void missingTtyComponentReturnsFailure() {
    final var args = startupFor(saveCircuit("tty.circ"), TtyInterface.FORMAT_TTY);

    assertEquals(TtyInterface.EXIT_FAILURE, TtyInterface.run(args));
  }

  @Test
  void failedFpgaDownloadReturnsFailure() {
    final var args = startupFor(saveCircuit("fpga.circ"), TtyInterface.FORMAT_HALT);
    when(args.isFpgaDownload()).thenReturn(true);
    when(args.fpgaDownload(any(Project.class))).thenReturn(false);

    assertEquals(TtyInterface.EXIT_FAILURE, TtyInterface.run(args));
  }

  @Test
  void missingRamForSaveOverridesSuccessfulSimulationStatus() {
    final var state = mock(CircuitState.class);
    final var circuit = mock(Circuit.class);
    when(state.getCircuit()).thenReturn(circuit);
    when(circuit.getNonWires()).thenReturn(Set.of());
    when(state.getSubstates()).thenReturn(Set.of());

    assertEquals(
        TtyInterface.EXIT_FAILURE,
        TtyInterface.finishSimulation(
            state, new File(tempDir, "memory.hex"), TtyInterface.EXIT_SUCCESS));
  }

  @Test
  void simulationStatusIsReturnedWhenNoSaveWasRequested() {
    assertEquals(
        TtyInterface.EXIT_SUCCESS,
        TtyInterface.finishSimulation(null, null, TtyInterface.EXIT_SUCCESS));
    assertEquals(
        TtyInterface.EXIT_OSCILLATION,
        TtyInterface.finishSimulation(null, null, TtyInterface.EXIT_OSCILLATION));
    assertEquals(
        TtyInterface.EXIT_FAILURE,
        TtyInterface.finishSimulation(null, null, TtyInterface.EXIT_FAILURE));
  }

  @Test
  void oscillationReturnsItsExistingStatus() {
    final var state = mock(CircuitState.class);
    final var propagator = mock(Propagator.class);
    when(state.getPropagator()).thenReturn(propagator);
    when(propagator.isOscillating()).thenReturn(true);

    assertEquals(
        TtyInterface.EXIT_OSCILLATION,
        TtyInterface.runSimulation(
            state, new ArrayList<>(), null, TtyInterface.FORMAT_HALT));
  }

  private static final String INVERTER =
      """
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
      """;

  /**
   * A NAND gate feeding back into itself: stable for a = 0, oscillating for a = 1. The pull
   * resistor gives the loop a defined start value; without it the loop settles at E.
   */
  private static final String OSCILLATOR =
      """
          <comp lib="0" loc="(100,80)" name="Pin">
            <a name="label" val="a"/>
          </comp>
          <comp lib="1" loc="(200,100)" name="NAND Gate"/>
          <comp lib="0" loc="(130,140)" name="Pull Resistor"/>
          <comp lib="0" loc="(300,100)" name="Pin">
            <a name="facing" val="west"/>
            <a name="output" val="true"/>
            <a name="label" val="y"/>
          </comp>
          <wire from="(100,80)" to="(140,80)"/>
          <wire from="(200,100)" to="(220,100)"/>
          <wire from="(220,100)" to="(300,100)"/>
          <wire from="(220,100)" to="(220,140)"/>
          <wire from="(130,140)" to="(220,140)"/>
          <wire from="(130,120)" to="(130,140)"/>
          <wire from="(130,120)" to="(140,120)"/>
      """;

  private File writeCircuit(String name, String components) throws IOException {
    return writeCircuit(name, components, "");
  }

  private File writeCircuit(String name, String components, String extraLibraries)
      throws IOException {
    final var file = new File(tempDir, name);
    Files.writeString(
        file.toPath(),
        """
        <?xml version="1.0" encoding="UTF-8" standalone="no"?>
        <project source="5.0.0" version="1.0">
          <lib desc="#Wiring" name="0"/>
          <lib desc="#Gates" name="1"/>
          <lib desc="#Memory" name="5"/>
        """
            + extraLibraries
            + """
              <main name="main"/>
              <circuit name="main">
                <a name="circuit" val="main"/>
            """
            + components
            + """
              </circuit>
            </project>
            """);
    return file;
  }

  private static String captureStderr(Runnable action) {
    final var originalErr = System.err;
    final var err = new ByteArrayOutputStream();
    try {
      System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
      action.run();
    } finally {
      System.setErr(originalErr);
    }
    return err.toString(StandardCharsets.UTF_8);
  }

  private Startup startupFor(File circuitFile, int format) {
    final var args = mock(Startup.class);
    when(args.getFilesToOpen()).thenReturn(List.of(circuitFile));
    when(args.getSubstitutions()).thenReturn(Map.of());
    when(args.getMemoryLoadFiles()).thenReturn(new HashMap<>());
    when(args.getTtyFormat()).thenReturn(format);
    return args;
  }

  private File saveCircuit(String name) {
    final var loader = new RecordingLoader();
    final var file = LogisimFile.createNew(loader, null);
    final var path = new File(tempDir, name);
    assertTrue(loader.save(file, path), loader.errors());
    return path;
  }

  private static class RecordingLoader extends Loader {
    private final List<String> errors = new ArrayList<>();

    private RecordingLoader() {
      super(null);
    }

    private String errors() {
      return String.join("\n", errors);
    }

    @Override
    public void showError(String description) {
      errors.add(description);
    }
  }
}

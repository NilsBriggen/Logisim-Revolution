/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.start;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.gui.test.TestRunResult;
import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import org.junit.jupiter.api.Test;

class TestReportWriterTest {

  private static final List<TestRunResult> RESULTS =
      List.of(
          new TestRunResult(
              "adder.circ",
              "main",
              "v.txt",
              List.of(
                  new TestRunResult.Row(1, List.of()),
                  new TestRunResult.Row(2, List.of("y = 0 (expected 1)", "a<b & \"c\""))),
              null,
              1500),
          TestRunResult.error("adder.circ", "nosuch", "w.txt", "Circuit 'nosuch' not found."));

  private static String write(TestReportWriter.Format format) throws IOException {
    final var out = new StringWriter();
    TestReportWriter.write(format, RESULTS, out);
    return out.toString();
  }

  @Test
  void junitHasOneCasePerRowAndEscapes() throws IOException {
    final var xml = write(TestReportWriter.Format.JUNIT);
    assertTrue(xml.contains("<testsuites tests=\"3\" failures=\"1\" errors=\"1\">"), xml);
    assertTrue(
        xml.contains(
            "<testsuite name=\"adder.circ:main:v.txt\" tests=\"2\" failures=\"1\" errors=\"0\""
                + " time=\"1.500\">"),
        xml);
    assertTrue(xml.contains("<testcase classname=\"main\" name=\"row 1\"/>"), xml);
    assertTrue(
        xml.contains("message=\"y = 0 (expected 1); a&lt;b &amp; &quot;c&quot;\""), xml);
    assertTrue(xml.contains("<error message=\"Circuit 'nosuch' not found.\"/>"), xml);
  }

  @Test
  void tapNumbersEveryRow() throws IOException {
    assertEquals(
        """
        TAP version 13
        1..3
        ok 1 - adder.circ:main:v.txt row 1
        not ok 2 - adder.circ:main:v.txt row 2
          ---
          message: "y = 0 (expected 1); a<b & \\"c\\""
          ...
        not ok 3 - adder.circ:nosuch:w.txt setup
          ---
          message: "Circuit 'nosuch' not found."
          ...
        """,
        write(TestReportWriter.Format.TAP));
  }

  @Test
  void jsonListsFailuresOnly() throws IOException {
    final var json = write(TestReportWriter.Format.JSON);
    assertTrue(json.contains("\"passed\": 1,"), json);
    assertTrue(json.contains("\"failed\": 1,"), json);
    assertTrue(
        json.contains(
            "{\"row\": 2, \"messages\": [\"y = 0 (expected 1)\", \"a<b & \\\"c\\\"\"]}"),
        json);
    assertTrue(json.contains("\"error\": \"Circuit 'nosuch' not found.\""), json);
    assertTrue(json.contains("\"error\": null"), json);
  }

  @Test
  void formatsParseCaseInsensitively() {
    assertEquals(TestReportWriter.Format.JUNIT, TestReportWriter.Format.parse("JUnit"));
    assertEquals(TestReportWriter.Format.TAP, TestReportWriter.Format.parse("tap"));
    assertNull(TestReportWriter.Format.parse("xml"));
  }

  @Test
  void escapingDropsCharactersXmlCannotCarry() {
    assertEquals("ab&#10;c", TestReportWriter.xml("a\u0001b\nc"));
    assertEquals("\"\\u0001\\n\"", TestReportWriter.json("\u0001\n"));
  }
}

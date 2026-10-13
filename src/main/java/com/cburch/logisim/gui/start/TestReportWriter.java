/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.start;

import com.cburch.logisim.gui.test.TestRunResult;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Locale;

/**
 * Writes the results of command-line test vector runs for CI systems and graders: JUnit XML (one
 * test suite per vector run, one test case per row), TAP version 13, or JSON.
 */
final class TestReportWriter {

  /** A report format accepted by {@code --report}. */
  enum Format {
    JUNIT,
    TAP,
    JSON;

    /** Returns the format named (case-insensitively), or null. */
    static Format parse(String name) {
      for (final var format : values()) {
        if (format.name().equalsIgnoreCase(name)) return format;
      }
      return null;
    }
  }

  private TestReportWriter() {}

  static void write(Format format, List<TestRunResult> results, Writer out) throws IOException {
    switch (format) {
      case JUNIT -> writeJunit(results, out);
      case TAP -> writeTap(results, out);
      case JSON -> writeJson(results, out);
    }
  }

  private static String suiteName(TestRunResult result) {
    return result.project() + ":" + result.circuit() + ":" + result.vectorFile();
  }

  private static void writeJunit(List<TestRunResult> results, Writer out) throws IOException {
    var tests = 0;
    var failures = 0;
    var errors = 0;
    for (final var result : results) {
      tests += result.error() == null ? result.rows().size() : 1;
      failures += result.failedCount();
      if (result.error() != null) errors++;
    }
    out.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
    out.write("<testsuites tests=\"" + tests + "\" failures=\"" + failures + "\" errors=\""
        + errors + "\">\n");
    for (final var result : results) {
      final var name = xml(suiteName(result));
      final var count = result.error() == null ? result.rows().size() : 1;
      out.write("  <testsuite name=\"" + name + "\" tests=\"" + count + "\" failures=\""
          + result.failedCount() + "\" errors=\"" + (result.error() == null ? 0 : 1)
          + "\" time=\"" + seconds(result.millis()) + "\">\n");
      if (result.error() != null) {
        out.write("    <testcase classname=\"" + xml(result.circuit()) + "\" name=\"setup\">\n");
        out.write("      <error message=\"" + xml(result.error()) + "\"/>\n");
        out.write("    </testcase>\n");
      }
      for (final var row : result.rows()) {
        out.write("    <testcase classname=\"" + xml(result.circuit()) + "\" name=\"row "
            + row.row() + "\"");
        if (row.passed()) {
          out.write("/>\n");
        } else {
          final var message = String.join("; ", row.failures());
          out.write(">\n      <failure message=\"" + xml(message) + "\">" + xml(message)
              + "</failure>\n    </testcase>\n");
        }
      }
      out.write("  </testsuite>\n");
    }
    out.write("</testsuites>\n");
  }

  private static void writeTap(List<TestRunResult> results, Writer out) throws IOException {
    var total = 0;
    for (final var result : results) total += result.error() == null ? result.rows().size() : 1;
    out.write("TAP version 13\n1.." + total + "\n");
    var n = 0;
    for (final var result : results) {
      final var suite = tapText(suiteName(result));
      if (result.error() != null) {
        out.write("not ok " + ++n + " - " + suite + " setup\n");
        out.write("  ---\n  message: " + yaml(result.error()) + "\n  ...\n");
      }
      for (final var row : result.rows()) {
        out.write((row.passed() ? "ok " : "not ok ") + ++n + " - " + suite + " row " + row.row()
            + "\n");
        if (!row.passed()) {
          out.write("  ---\n  message: " + yaml(String.join("; ", row.failures())) + "\n  ...\n");
        }
      }
    }
  }

  private static void writeJson(List<TestRunResult> results, Writer out) throws IOException {
    out.write("{\n  \"results\": [");
    var firstResult = true;
    for (final var result : results) {
      out.write(firstResult ? "\n" : ",\n");
      firstResult = false;
      out.write("    {\n");
      out.write("      \"project\": " + json(result.project()) + ",\n");
      out.write("      \"circuit\": " + json(result.circuit()) + ",\n");
      out.write("      \"vectorFile\": " + json(result.vectorFile()) + ",\n");
      out.write("      \"passed\": " + result.passedCount() + ",\n");
      out.write("      \"failed\": " + result.failedCount() + ",\n");
      out.write("      \"error\": " + (result.error() == null ? "null" : json(result.error()))
          + ",\n");
      out.write("      \"seconds\": " + seconds(result.millis()) + ",\n");
      out.write("      \"failures\": [");
      var firstRow = true;
      for (final var row : result.rows()) {
        if (row.passed()) continue;
        out.write(firstRow ? "\n" : ",\n");
        firstRow = false;
        out.write("        {\"row\": " + row.row() + ", \"messages\": [");
        for (var i = 0; i < row.failures().size(); i++) {
          if (i > 0) out.write(", ");
          out.write(json(row.failures().get(i)));
        }
        out.write("]}");
      }
      out.write(firstRow ? "]\n" : "\n      ]\n");
      out.write("    }");
    }
    out.write(firstResult ? "]\n}\n" : "\n  ]\n}\n");
  }

  private static String seconds(long millis) {
    return String.format(Locale.ROOT, "%.3f", millis / 1000.0);
  }

  static String xml(String text) {
    final var out = new StringBuilder(text.length());
    for (final var c : text.toCharArray()) {
      switch (c) {
        case '&' -> out.append("&amp;");
        case '<' -> out.append("&lt;");
        case '>' -> out.append("&gt;");
        case '"' -> out.append("&quot;");
        case '\n' -> out.append("&#10;");
        default -> {
          // XML 1.0 cannot carry other control characters, even escaped.
          if (c >= ' ' || c == '\t') out.append(c);
        }
      }
    }
    return out.toString();
  }

  static String json(String text) {
    final var out = new StringBuilder(text.length() + 2).append('"');
    for (final var c : text.toCharArray()) {
      switch (c) {
        case '"' -> out.append("\\\"");
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        default -> {
          if (c < ' ') out.append(String.format("\\u%04x", (int) c));
          else out.append(c);
        }
      }
    }
    return out.append('"').toString();
  }

  /** A TAP description may not contain '#' (a directive) or line breaks. */
  private static String tapText(String text) {
    return text.replace('#', '_').replace('\n', ' ');
  }

  /** A single-line YAML scalar, quoted. */
  private static String yaml(String text) {
    return json(text);
  }
}

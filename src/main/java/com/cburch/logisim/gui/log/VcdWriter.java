/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.log;

import com.cburch.logisim.data.Value;
import com.cburch.logisim.generated.BuildInfo;
import java.io.IOException;
import java.io.Writer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Writes logged signal histories as a Value Change Dump (IEEE 1364 VCD), which waveform viewers
 * such as GTKWave and Surfer open.
 *
 * <p>Times in the log model are nanoseconds. Defined bits are written as 0 and 1, floating bits as
 * z and error bits as x; times before a signal was first recorded are x.
 */
public final class VcdWriter {

  private VcdWriter() {}

  /**
   * Writes the given signals over the interval [t0, t1].
   *
   * @param signals the signals to write, in order
   * @param scope the module scope name, usually the circuit name
   * @param t0 the first time to write, in nanoseconds
   * @param t1 the last time to write, in nanoseconds
   * @param out where to write; not closed
   */
  public static void write(List<Signal> signals, String scope, long t0, long t1, Writer out)
      throws IOException {
    out.write("$date\n  " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        + "\n$end\n");
    out.write("$version\n  " + BuildInfo.displayName + "\n$end\n");
    out.write("$timescale 1ns $end\n");
    out.write("$scope module " + vcdName(scope) + " $end\n");
    final var ids = new ArrayList<String>();
    for (var i = 0; i < signals.size(); i++) {
      final var signal = signals.get(i);
      final var id = identifier(i);
      ids.add(id);
      out.write("$var wire " + signal.getWidth() + " " + id + " " + vcdName(signal.getName())
          + " $end\n");
    }
    out.write("$upscope $end\n$enddefinitions $end\n");

    // Changes per time, in signal order within a time.
    final Map<Long, StringBuilder> changes = new TreeMap<>();
    for (var i = 0; i < signals.size(); i++) {
      collectChanges(signals.get(i), ids.get(i), t0, t1, changes);
    }
    var first = true;
    for (final var entry : changes.entrySet()) {
      out.write("#" + (entry.getKey() - t0) + "\n");
      if (first) {
        out.write("$dumpvars\n" + entry.getValue() + "$end\n");
        first = false;
      } else {
        out.write(entry.getValue().toString());
      }
    }
  }

  private static void collectChanges(
      Signal signal, String id, long t0, long t1, Map<Long, StringBuilder> changes) {
    final var width = signal.getWidth();
    String last = null;
    // Before the first recorded value the signal is undefined.
    final var it = signal.new Iterator();
    if (it.time > t0) {
      last = encode(null, width, id);
      changes.computeIfAbsent(t0, k -> new StringBuilder()).append(last);
    } else if (t0 > it.time) {
      it.advance(t0 - it.time);
    }
    while (it.value != null && it.time <= t1) {
      final var line = encode(it.value, width, id);
      if (!line.equals(last)) {
        changes.computeIfAbsent(Math.max(it.time, t0), k -> new StringBuilder()).append(line);
        last = line;
      }
      if (!it.advance()) break;
    }
  }

  /** Returns one value-change line for a signal: {@code 1!} for one bit, {@code b0101 !} wider. */
  static String encode(Value value, int width, String id) {
    final var bits = new StringBuilder(width);
    for (var i = width - 1; i >= 0; i--) {
      bits.append(value == null || i >= value.getWidth() ? 'x' : bit(value.get(i)));
    }
    if (width == 1) return bits + id + "\n";
    // Leading zeros may be dropped; keep the full width so viewers show every bit.
    return "b" + bits + " " + id + "\n";
  }

  private static char bit(Value bit) {
    if (bit == Value.TRUE) return '1';
    if (bit == Value.FALSE) return '0';
    if (bit == Value.UNKNOWN) return 'z';
    return 'x';
  }

  /**
   * Returns the identifier code for the signal at this index: printable ASCII from {@code !} to
   * {@code ~}, base 94, as VCD allows.
   */
  static String identifier(int index) {
    final var id = new StringBuilder();
    var n = index;
    do {
      id.append((char) ('!' + n % 94));
      n = n / 94 - 1;
    } while (n >= 0);
    return id.toString();
  }

  /** Returns a name without whitespace, which would end a VCD token. */
  static String vcdName(String name) {
    if (name == null || name.isBlank()) return "unnamed";
    final var out = new StringBuilder();
    for (final var c : name.trim().toCharArray()) {
      out.append(c <= ' ' || c > '~' ? '_' : c);
    }
    return out.toString();
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.hex;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.std.memory.MemContents;
import java.io.IOException;
import java.io.Writer;
import java.util.ArrayList;

/**
 * Memory image formats written by toolchains rather than by Logisim: Intel HEX and Motorola
 * S-records (as produced by {@code objcopy} and most assemblers) and Verilog {@code $readmemh}
 * files.
 *
 * <p>Intel HEX and S-records address bytes. Each memory word takes {@code ceil(width / 8)} bytes,
 * most significant byte first, so byte address {@code b} is word {@code b / bytesPerWord}.
 * {@code $readmemh} files hold one hexadecimal word per token and address words.
 */
final class RecordFormats {
  static final String INTEL_HEX = "Intel HEX";
  static final String S_RECORD = "Motorola S-record";
  static final String READMEMH = "Verilog $readmemh hex words";

  /** Data bytes per Intel HEX or S-record line when writing. */
  private static final int BYTES_PER_RECORD = 16;

  private RecordFormats() {}

  static boolean handles(String desc) {
    return INTEL_HEX.equals(desc) || S_RECORD.equals(desc) || READMEMH.equals(desc);
  }

  /**
   * Recognizes a record format from the first non-blank, non-comment line, or returns null if the
   * text is none of them (it may still be a Logisim format).
   */
  static String detect(String text) {
    for (final var raw : text.split("\n", 64)) {
      final var line = raw.trim();
      if (line.isEmpty()) continue;
      if (line.startsWith(":") && line.length() >= 11 && isHex(line.substring(1))) {
        return INTEL_HEX;
      }
      if (line.length() >= 4
          && (line.charAt(0) == 'S' || line.charAt(0) == 's')
          && Character.isDigit(line.charAt(1))
          && isHex(line.substring(2))) {
        return S_RECORD;
      }
      if (line.startsWith("@") || line.startsWith("//")) return READMEMH;
      return null;
    }
    return null;
  }

  private static boolean isHex(String s) {
    for (final var c : s.toCharArray()) {
      if (Character.digit(c, 16) < 0) return false;
    }
    return !s.isEmpty();
  }

  private static int bytesPerWord(MemContents mem) {
    return (mem.getValueWidth() + 7) / 8;
  }

  private static long wordMask(MemContents mem) {
    final var width = mem.getValueWidth();
    return width >= 64 ? -1L : (1L << width) - 1;
  }

  /** Collects bytes into words, most significant byte first. */
  private static final class ByteSink {
    private final MemContents mem;
    private final int bytesPerWord;
    private final long wordCount;

    ByteSink(MemContents mem) {
      this.mem = mem;
      this.bytesPerWord = bytesPerWord(mem);
      this.wordCount = 1L << mem.getLogLength();
    }

    void put(long byteAddr, int value, int lineNo) throws IOException {
      final var word = byteAddr / bytesPerWord;
      if (word >= wordCount) {
        throw new IOException(S.get("hexRecordAddressError", lineNo, Long.toHexString(byteAddr)));
      }
      final var shift = 8 * (bytesPerWord - 1 - (int) (byteAddr % bytesPerWord));
      final var old = mem.get(word);
      final var updated = (old & ~(0xFFL << shift)) | ((long) value << shift);
      mem.set(word, updated & wordMask(mem));
    }
  }

  /** Decodes text in the given format into {@code dst}, which must start cleared. */
  static void read(String desc, String text, MemContents dst) throws IOException {
    switch (desc) {
      case INTEL_HEX -> readIntelHex(text, dst);
      case S_RECORD -> readSRecord(text, dst);
      default -> readReadmemh(text, dst);
    }
  }

  private static int[] hexBytes(String hex, int lineNo) throws IOException {
    if (hex.length() % 2 != 0 || !isHex(hex)) {
      throw new IOException(S.get("hexRecordSyntaxError", lineNo));
    }
    final var bytes = new int[hex.length() / 2];
    for (var i = 0; i < bytes.length; i++) {
      bytes[i] = Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
    }
    return bytes;
  }

  private static void readIntelHex(String text, MemContents dst) throws IOException {
    final var sink = new ByteSink(dst);
    var base = 0L;
    final var lines = text.split("\n");
    for (var n = 0; n < lines.length; n++) {
      final var lineNo = n + 1;
      final var line = lines[n].trim();
      if (line.isEmpty()) continue;
      if (!line.startsWith(":")) throw new IOException(S.get("hexRecordSyntaxError", lineNo));
      final var b = hexBytes(line.substring(1), lineNo);
      if (b.length < 5 || b.length != b[0] + 5) {
        throw new IOException(S.get("hexRecordSyntaxError", lineNo));
      }
      var sum = 0;
      for (final var x : b) sum += x;
      if ((sum & 0xFF) != 0) throw new IOException(S.get("hexRecordChecksumError", lineNo));
      final var count = b[0];
      final var offset = (b[1] << 8) | b[2];
      switch (b[3]) {
        case 0x00 -> {
          for (var i = 0; i < count; i++) sink.put(base + offset + i, b[4 + i], lineNo);
        }
        case 0x01 -> {
          return;
        }
        case 0x02 -> base = (long) ((b[4] << 8) | b[5]) << 4;
        case 0x04 -> base = (long) ((b[4] << 8) | b[5]) << 16;
        case 0x03, 0x05 -> {
          // Start addresses: nothing to load.
        }
        default -> throw new IOException(S.get("hexRecordTypeError", lineNo, b[3]));
      }
    }
  }

  private static void readSRecord(String text, MemContents dst) throws IOException {
    final var sink = new ByteSink(dst);
    final var lines = text.split("\n");
    for (var n = 0; n < lines.length; n++) {
      final var lineNo = n + 1;
      final var line = lines[n].trim();
      if (line.isEmpty()) continue;
      if (line.length() < 4 || Character.toUpperCase(line.charAt(0)) != 'S') {
        throw new IOException(S.get("hexRecordSyntaxError", lineNo));
      }
      final var type = line.charAt(1) - '0';
      final var b = hexBytes(line.substring(2), lineNo);
      if (b.length < 1 || b.length != b[0] + 1) {
        throw new IOException(S.get("hexRecordSyntaxError", lineNo));
      }
      var sum = 0;
      for (var i = 0; i < b.length - 1; i++) sum += b[i];
      if (((~sum) & 0xFF) != b[b.length - 1]) {
        throw new IOException(S.get("hexRecordChecksumError", lineNo));
      }
      final var addrLen =
          switch (type) {
            case 1, 9 -> 2;
            case 2, 8 -> 3;
            case 3, 7 -> 4;
            default -> 0;
          };
      if (type == 0 || type == 5 || type == 6 || type >= 7) continue; // header, count, start
      if (addrLen == 0) throw new IOException(S.get("hexRecordTypeError", lineNo, type));
      var addr = 0L;
      for (var i = 0; i < addrLen; i++) addr = (addr << 8) | b[1 + i];
      for (var i = 1 + addrLen; i < b.length - 1; i++) sink.put(addr++, b[i], lineNo);
    }
  }

  private static void readReadmemh(String text, MemContents dst) throws IOException {
    final var wordCount = 1L << dst.getLogLength();
    final var mask = wordMask(dst);
    var addr = 0L;
    var inBlockComment = false;
    final var lines = text.split("\n");
    for (var n = 0; n < lines.length; n++) {
      final var lineNo = n + 1;
      var line = lines[n];
      final var out = new StringBuilder();
      for (var i = 0; i < line.length(); i++) {
        if (inBlockComment) {
          if (line.startsWith("*/", i)) {
            inBlockComment = false;
            i++;
          }
        } else if (line.startsWith("//", i)) {
          break;
        } else if (line.startsWith("/*", i)) {
          inBlockComment = true;
          i++;
        } else {
          out.append(line.charAt(i));
        }
      }
      for (final var token : out.toString().trim().split("\\s+")) {
        if (token.isEmpty()) continue;
        if (token.startsWith("@")) {
          try {
            addr = Long.parseUnsignedLong(token.substring(1).replace("_", ""), 16);
          } catch (NumberFormatException e) {
            throw new IOException(S.get("hexRecordSyntaxError", lineNo));
          }
          continue;
        }
        final var digits = token.replace("_", "").toLowerCase();
        long value;
        if (digits.matches("[xz?]+")) {
          value = 0; // undefined words load as 0
        } else {
          try {
            value = Long.parseUnsignedLong(digits, 16);
          } catch (NumberFormatException e) {
            throw new IOException(S.get("hexRecordSyntaxError", lineNo));
          }
        }
        if (addr >= wordCount) {
          throw new IOException(S.get("hexRecordAddressError", lineNo, Long.toHexString(addr)));
        }
        dst.set(addr++, value & mask);
      }
    }
  }

  /** Writes {@code src} in the given format, up to its last non-zero word. */
  static void write(String desc, MemContents src, Writer out) throws IOException {
    switch (desc) {
      case INTEL_HEX -> writeIntelHex(src, out);
      case S_RECORD -> writeSRecord(src, out);
      default -> writeReadmemh(src, out);
    }
  }

  /** Calls {@code chunk} for each run of up to 16 bytes containing a non-zero byte. */
  private interface ChunkWriter {
    void write(long byteAddr, int[] bytes) throws IOException;
  }

  private static void forEachChunk(MemContents src, ChunkWriter chunk) throws IOException {
    final var bytesPerWord = bytesPerWord(src);
    final var end = (src.getLastOffset() + 1) * bytesPerWord;
    for (var start = 0L; start < end; start += BYTES_PER_RECORD) {
      final var len = (int) Math.min(BYTES_PER_RECORD, end - start);
      final var bytes = new int[len];
      var any = false;
      for (var i = 0; i < len; i++) {
        final var byteAddr = start + i;
        final var word = src.get(byteAddr / bytesPerWord);
        final var shift = 8 * (bytesPerWord - 1 - (int) (byteAddr % bytesPerWord));
        bytes[i] = (int) ((word >>> shift) & 0xFF);
        any |= bytes[i] != 0;
      }
      if (any) chunk.write(start, bytes);
    }
  }

  private static String hex2(int b) {
    return String.format("%02X", b & 0xFF);
  }

  private static void writeIntelHex(MemContents src, Writer out) throws IOException {
    final var upper = new long[] {0};
    forEachChunk(
        src,
        (byteAddr, bytes) -> {
          // A chunk never crosses a 64 KiB boundary: both are multiples of 16.
          final var hi = byteAddr >>> 16;
          if (hi != upper[0]) {
            upper[0] = hi;
            writeIntelRecord(out, 0, 0x04, new int[] {(int) (hi >> 8), (int) hi});
          }
          writeIntelRecord(out, (int) (byteAddr & 0xFFFF), 0x00, bytes);
        });
    writeIntelRecord(out, 0, 0x01, new int[0]);
  }

  private static void writeIntelRecord(Writer out, int offset, int type, int[] data)
      throws IOException {
    final var line = new StringBuilder(":");
    var sum = data.length + (offset >> 8) + (offset & 0xFF) + type;
    line.append(hex2(data.length)).append(hex2(offset >> 8)).append(hex2(offset)).append(hex2(type));
    for (final var b : data) {
      line.append(hex2(b));
      sum += b;
    }
    line.append(hex2(-sum)).append('\n');
    out.write(line.toString());
  }

  private static void writeSRecord(MemContents src, Writer out) throws IOException {
    final var bytesPerWord = bytesPerWord(src);
    final var end = (src.getLastOffset() + 1) * bytesPerWord;
    final var addrLen = end > 0x1000000 ? 4 : end > 0x10000 ? 3 : 2;
    writeSRecordLine(out, 0, 2, 0, new int[] {'H', 'D', 'R'});
    final var count = new int[] {0};
    forEachChunk(
        src,
        (byteAddr, bytes) -> {
          writeSRecordLine(out, addrLen - 1, addrLen, byteAddr, bytes);
          count[0]++;
        });
    if (count[0] <= 0xFFFF) writeSRecordLine(out, 5, 2, count[0], new int[0]);
    writeSRecordLine(out, 11 - addrLen, addrLen, 0, new int[0]);
  }

  private static void writeSRecordLine(Writer out, int type, int addrLen, long addr, int[] data)
      throws IOException {
    final var line = new StringBuilder("S").append(type);
    final var byteCount = addrLen + data.length + 1;
    var sum = byteCount;
    line.append(hex2(byteCount));
    for (var i = addrLen - 1; i >= 0; i--) {
      final var b = (int) (addr >>> (8 * i)) & 0xFF;
      line.append(hex2(b));
      sum += b;
    }
    for (final var b : data) {
      line.append(hex2(b));
      sum += b;
    }
    line.append(hex2(~sum)).append('\n');
    out.write(line.toString());
  }

  private static void writeReadmemh(MemContents src, Writer out) throws IOException {
    final var digits = (src.getValueWidth() + 3) / 4;
    final var end = src.getLastOffset() + 1;
    out.write("// " + S.get("hexReadmemhHeader", src.getValueWidth(), end) + "\n");
    final var line = new ArrayList<String>();
    var needAddress = true;
    for (var addr = 0L; addr < end; addr++) {
      final var value = src.get(addr);
      if (value == 0) {
        // Zero words are skipped; the next non-zero word restates its address.
        needAddress = true;
        continue;
      }
      if (needAddress || line.size() == 8) {
        if (!line.isEmpty()) out.write(String.join(" ", line) + "\n");
        line.clear();
        if (needAddress) out.write("@" + Long.toHexString(addr) + "\n");
        needAddress = false;
      }
      final var word = Long.toHexString(value);
      line.add("0".repeat(Math.max(0, digits - word.length())) + word);
    }
    if (!line.isEmpty()) out.write(String.join(" ", line) + "\n");
  }
}

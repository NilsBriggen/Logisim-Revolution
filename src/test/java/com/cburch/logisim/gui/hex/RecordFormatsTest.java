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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.std.memory.MemContents;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecordFormatsTest {

  @TempDir File tempDir;

  private MemContents load(String text, int addrBits, int width) throws IOException {
    final var file = new File(tempDir, "image");
    Files.writeString(file.toPath(), text);
    final var mem = MemContents.create(addrBits, width, false);
    assertTrue(HexFile.open(mem, file));
    return mem;
  }

  @Test
  void readsIntelHexWithExtendedAddressesIntoWideWords() throws IOException {
    // 3 bytes at 0x0030, then 2 bytes at 0x0001_0000 via an extended linear address record.
    final var mem =
        load(":0300300002337A1E\n:020000040001F9\n:02000000ABCD86\n:00000001FF\n", 16, 16);
    assertEquals(0x0233, mem.get(0x18));
    assertEquals(0x7A00, mem.get(0x19));
    assertEquals(0xABCD, mem.get(0x8000));
  }

  @Test
  void rejectsBadChecksumsWithTheLine() {
    final var e =
        assertThrows(IOException.class, () -> load(":0300300002337A1F\n:00000001FF\n", 8, 8));
    assertEquals(S.get("hexRecordChecksumError", 1), e.getMessage());
  }

  @Test
  void rejectsAddressesBeyondTheMemory() {
    final var e = assertThrows(IOException.class, () -> load(":01010000AA54\n:00000001FF\n", 4, 8));
    assertEquals(S.get("hexRecordAddressError", 1, "100"), e.getMessage());
  }

  @Test
  void readsSRecords() throws IOException {
    final var mem = load("S00600004844521B\nS1070010DEADBEEFB0\nS9030000FC\n", 8, 8);
    assertEquals(0xDE, mem.get(0x10));
    assertEquals(0xEF, mem.get(0x13));
  }

  @Test
  void readsReadmemhWithCommentsAndAddressJumps() throws IOException {
    final var mem =
        load("// program\n@0 1f 2_0 /* skip */ 3\n@10\nff // last\nxx\n", 6, 8);
    assertEquals(0x1f, mem.get(0));
    assertEquals(0x20, mem.get(1));
    assertEquals(3, mem.get(2));
    assertEquals(0xff, mem.get(0x10));
    assertEquals(0, mem.get(0x11));
  }

  @Test
  void writersSkipEmptyRangesAndRestateAddresses() throws IOException {
    final var mem = MemContents.create(8, 8, false);
    mem.set(0, 0x11);
    mem.set(0x40, 0x22);

    final var hex = new StringWriter();
    RecordFormats.write(RecordFormats.INTEL_HEX, mem, hex);
    assertEquals(
        ":1000000011000000000000000000000000000000DF\n"
            + ":10004000220000000000000000000000000000008E\n"
            + ":00000001FF\n",
        hex.toString());

    final var readmemh = new StringWriter();
    RecordFormats.write(RecordFormats.READMEMH, mem, readmemh);
    assertTrue(readmemh.toString().endsWith("@0\n11\n@40\n22\n"), readmemh.toString());
  }

  @Test
  void detectsOnlyRecordFormats() {
    assertEquals(RecordFormats.INTEL_HEX, RecordFormats.detect("\n:00000001FF\n"));
    assertEquals(RecordFormats.S_RECORD, RecordFormats.detect("S9030000FC"));
    assertEquals(RecordFormats.READMEMH, RecordFormats.detect("@100\n"));
    assertNull(RecordFormats.detect("v3.0 hex words addressed\n00: 1 2\n"));
    assertNull(RecordFormats.detect("v2.0 raw\n"));
  }
}

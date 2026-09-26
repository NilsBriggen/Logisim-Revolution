/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cburch.hex.HexModel;
import com.cburch.hex.HexModelListener;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class MemContentsTest {

  @Test
  void setManyValuesStartingMidPageAcrossSeveralPages() {
    // Pages are 4096 words. The middle and last pages were compared at the first page's offset,
    // which read past the end of the page.
    final var memory = MemContents.create(14, 8, false);
    final var values = new long[9000];
    for (var i = 0; i < values.length; i++) values[i] = (i % 250) + 1;

    memory.set(100, values);

    for (var i = 0; i < values.length; i++) assertEquals(values[i], memory.get(100 + i));
    assertEquals(0, memory.get(99));
    assertEquals(0, memory.get(9100));
  }

  @Test
  void fillWithAValueAcrossPagesIntoAnEmptyLastPage() {
    final var memory = MemContents.create(14, 8, false);

    memory.fill(4000, 5000, 0x7f);

    assertEquals(0x7f, memory.get(4000));
    assertEquals(0x7f, memory.get(8999));
    assertEquals(0, memory.get(9000));
  }

  @Test
  void removedListenerIsNoLongerCalled() {
    final var memory = MemContents.create(4, 8, false);
    final var calls = new AtomicInteger();
    final var listener =
        new HexModelListener() {
          @Override
          public void bytesChanged(HexModel source, long start, long numBytes, long[] oldValues) {
            calls.incrementAndGet();
          }

          @Override
          public void metainfoChanged(HexModel source) {}
        };
    memory.addHexModelListener(listener);
    memory.set(1, 1);
    memory.removeHexModelListener(listener);
    memory.set(2, 2);

    assertEquals(1, calls.get());
  }
}

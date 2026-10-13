/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.log;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cburch.logisim.circuit.PauseReason;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.log.Watchpoint.Condition;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class WatchpointSetTest {

  private static Value v4(long value) {
    return Value.createKnown(BitWidth.create(4), value);
  }

  @Test
  void valueConditionsFireOnlyWhenTheyStartToMatch() {
    assertTrue(Watchpoint.fires(Condition.EQUALS, 5, v4(4), v4(5)));
    assertFalse(Watchpoint.fires(Condition.EQUALS, 5, v4(5), v4(5)));
    assertFalse(Watchpoint.fires(Condition.EQUALS, 5, v4(5), v4(6)));
    assertTrue(Watchpoint.fires(Condition.NOT_EQUALS, 5, v4(5), v4(6)));
    assertFalse(Watchpoint.fires(Condition.NOT_EQUALS, 5, v4(6), v4(7)));
    // The operand is compared within the signal's width.
    assertTrue(Watchpoint.fires(Condition.EQUALS, 0x15, v4(4), v4(5)));
    // An undefined value never equals anything.
    assertTrue(Watchpoint.fires(Condition.NOT_EQUALS, 5, v4(5), Value.createUnknown(BitWidth.create(4))));
  }

  @Test
  void edgeConditions() {
    assertTrue(Watchpoint.fires(Condition.RISING, 0, Value.FALSE, Value.TRUE));
    assertFalse(Watchpoint.fires(Condition.RISING, 0, Value.TRUE, Value.TRUE));
    assertFalse(Watchpoint.fires(Condition.RISING, 0, Value.UNKNOWN, Value.TRUE));
    assertTrue(Watchpoint.fires(Condition.FALLING, 0, Value.TRUE, Value.FALSE));
    assertFalse(Watchpoint.fires(Condition.FALLING, 0, Value.FALSE, Value.FALSE));
    assertTrue(Watchpoint.fires(Condition.CHANGES, 0, v4(3), v4(4)));
    assertFalse(Watchpoint.fires(Condition.CHANGES, 0, v4(4), v4(4)));
  }

  @Test
  void setReportsHitsAndRespectsBaselineAndEnable() {
    final var signal = mock(SignalInfo.class);
    when(signal.getDisplayName()).thenReturn("count");
    final var values = new HashMap<SignalInfo, Value>();
    final var set = new WatchpointSet();
    final var changes = new AtomicInteger();
    set.addListener(changes::incrementAndGet);
    final var watchpoint = new Watchpoint(signal, Condition.EQUALS, 3);
    set.add(watchpoint);

    // The first sample only sets the baseline, even if it already matches.
    values.put(signal, v4(3));
    assertNull(set.check(values::get, 1));
    values.put(signal, v4(2));
    assertNull(set.check(values::get, 2));
    values.put(signal, v4(3));
    final PauseReason reason = set.check(values::get, 3);
    assertNotNull(reason);
    assertEquals(PauseReason.Kind.WATCHPOINT, reason.kind());
    assertEquals(3, reason.tick());
    assertTrue(reason.message().contains("count = 3"), reason.message());
    assertEquals(1, watchpoint.getHits());

    watchpoint.setEnabled(false);
    values.put(signal, v4(2));
    set.check(values::get, 4);
    values.put(signal, v4(3));
    assertNull(set.check(values::get, 5));

    // A reset starts a fresh baseline; a signal no longer sampled is ignored.
    watchpoint.setEnabled(true);
    set.reset();
    values.clear();
    assertNull(set.check(values::get, 6));
    assertTrue(changes.get() >= 2);
  }

  @Test
  void operandParsingAcceptsCommonRadixes() {
    assertEquals(12L, WatchpointsPanel.parseOperand(" 12 "));
    assertEquals(255L, WatchpointsPanel.parseOperand("0xFF"));
    assertEquals(5L, WatchpointsPanel.parseOperand("0b0101"));
    assertEquals(-1L, WatchpointsPanel.parseOperand("-1"));
    assertNull(WatchpointsPanel.parseOperand("ten"));
  }
}

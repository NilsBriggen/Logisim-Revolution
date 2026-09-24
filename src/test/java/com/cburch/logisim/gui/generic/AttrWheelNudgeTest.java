/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.generic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.std.wiring.DurationAttribute;
import com.cburch.logisim.util.StringUtil;
import org.junit.jupiter.api.Test;

/** Turning the wheel over a numeric property. */
class AttrWheelNudgeTest {
  /**
   * AWT reports a negative rotation for a push away from the user, which should raise the value.
   */
  private static final int UP = -1;

  private static final int DOWN = 1;

  private static String nudged(String value, int clicks, Attribute<?> attr) {
    return AttrWheelNudge.nudge(value, clicks, attr).orElse(null);
  }

  @Test
  void wheelUpRaisesAndWheelDownLowers() {
    assertEquals("8", nudged("7", UP, null));
    assertEquals("6", nudged("7", DOWN, null));
  }

  @Test
  void hexStaysHexAndDecimalStaysDecimal() {
    assertEquals("0x2B", nudged("0x2a", UP, null));
    assertEquals("0x29", nudged("0X2A", DOWN, null));
    assertEquals("43", nudged("42", UP, null));
  }

  @Test
  void valueThatIsNotANumberIsLeftAlone() {
    assertTrue(AttrWheelNudge.nudge("east", UP, null).isEmpty());
    assertTrue(AttrWheelNudge.nudge("", UP, null).isEmpty());
    assertTrue(AttrWheelNudge.nudge(null, UP, null).isEmpty());
  }

  @Test
  void stillWheelChangesNothing() {
    assertTrue(AttrWheelNudge.nudge("7", 0, null).isEmpty());
  }

  @Test
  void multipleDetentsArePreservedAndClamped() {
    assertEquals("12", nudged("7", -5, null));
    assertEquals("6", nudged("4", -5, Attributes.forIntegerRange("r", 3, 6)));
  }

  @Test
  void unboundedLongEndpointsDoNotWrap() {
    assertTrue(AttrWheelNudge.nudge(Long.toString(Long.MAX_VALUE), -1, null).isEmpty());
    assertTrue(AttrWheelNudge.nudge(Long.toString(Long.MIN_VALUE), 1, null).isEmpty());
  }

  /** A bit width knows it cannot go below one; nothing has to guess from its name. */
  @Test
  void bitWidthStopsAtItsOwnLimits() {
    final Attribute<BitWidth> width = Attributes.forBitWidth("w", StringUtil.constantGetter("w"));
    assertTrue(AttrWheelNudge.nudge("1", DOWN, width).isEmpty(), "must not go below one");
    assertEquals("2", nudged("1", UP, width));
    assertTrue(AttrWheelNudge.nudge(String.valueOf(BitWidth.MAXWIDTH), UP, width).isEmpty(),
        "must not go above the widest value");
  }

  @Test
  void integerRangeStopsAtItsOwnLimits() {
    final Attribute<Integer> ranged = Attributes.forIntegerRange("r", 3, 6);
    assertTrue(AttrWheelNudge.nudge("3", DOWN, ranged).isEmpty());
    assertTrue(AttrWheelNudge.nudge("6", UP, ranged).isEmpty());
    assertEquals("4", nudged("3", UP, ranged));
  }

  @Test
  void durationStopsAtItsOwnLimits() {
    final var duration = new DurationAttribute("d", StringUtil.constantGetter("d"), 2, 9, false);
    assertTrue(AttrWheelNudge.nudge("2", DOWN, duration).isEmpty());
    assertTrue(AttrWheelNudge.nudge("9", UP, duration).isEmpty());
    assertEquals("3", nudged("2", UP, duration));
  }

  /**
   * The old rule clamped at one whenever the property's TRANSLATED name contained "width", so it
   * did nothing outside English and clamped unrelated properties whose name happened to match.
   */
  @Test
  void unboundedPropertyIsNotClampedBecauseOfItsName() {
    final Attribute<Integer> named =
        Attributes.forInteger("offset", StringUtil.constantGetter("Width of the gap"));
    assertEquals("-1", nudged("0", DOWN, named));
    assertEquals("0", nudged("1", DOWN, named));
  }

  /** And a bounded property is clamped whatever its name is translated to. */
  @Test
  void boundedPropertyIsClampedWhateverItIsCalled() {
    final Attribute<BitWidth> width =
        Attributes.forBitWidth("w", StringUtil.constantGetter("Anzahl Bits"));
    assertTrue(AttrWheelNudge.nudge("1", DOWN, width).isEmpty());
  }
}

/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.wiring;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cburch.logisim.circuit.RadixOption;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.instance.StdAttr;
import org.junit.jupiter.api.Test;

/** A pin's reset value is shown in the pin's radix but still saved in hexadecimal. */
class PinResetValueAttributeTest {

  private static PinAttributes pin(int width, RadixOption radix) {
    final var attrs = new PinAttributes();
    attrs.setValue(StdAttr.WIDTH, BitWidth.create(width));
    attrs.setValue(RadixOption.ATTRIBUTE, radix);
    return attrs;
  }

  @Test
  void showsTheValueInThePinsRadix() {
    final var attr = Pin.ATTR_INITIAL;
    assertEquals("0b101", attr.toDisplayString(5L, pin(4, RadixOption.RADIX_2)));
    assertEquals("017", attr.toDisplayString(15L, pin(4, RadixOption.RADIX_8)));
    assertEquals("0", attr.toDisplayString(0L, pin(4, RadixOption.RADIX_8)));
    assertEquals("12", attr.toDisplayString(12L, pin(4, RadixOption.RADIX_10_UNSIGNED)));
    assertEquals("-4", attr.toDisplayString(12L, pin(4, RadixOption.RADIX_10_SIGNED)));
    assertEquals("0xc", attr.toDisplayString(12L, pin(4, RadixOption.RADIX_16)));
  }

  @Test
  void whatIsShownParsesBackToTheSameBits() {
    final var attr = Pin.ATTR_INITIAL;
    for (final var radix : RadixOption.OPTIONS) {
      final var attrs = pin(4, radix);
      final var shown = attr.toDisplayString(11L, attrs);
      assertEquals(11L, attr.parse(shown) & 0xf, radix + " showed " + shown);
    }
  }

  @Test
  void savedFormIsStillHexadecimal() {
    assertEquals("0x5", Pin.ATTR_INITIAL.toStandardString(5L));
    assertEquals(5L, Pin.ATTR_INITIAL.parse("0x5"));
  }
}

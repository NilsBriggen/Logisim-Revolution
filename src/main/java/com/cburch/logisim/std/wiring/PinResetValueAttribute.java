/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.wiring;

import com.cburch.logisim.circuit.RadixOption;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.util.StringGetter;
import java.awt.Window;
import javax.swing.JTextField;

/**
 * A pin's reset value. It is stored in hexadecimal like before, but shown and edited in the pin's
 * own radix, so a binary pin does not show its reset value as {@code 0x0}.
 */
class PinResetValueAttribute extends Attribute<Long> {
  private final Attribute<Long> hex;

  PinResetValueAttribute(String name, StringGetter disp) {
    super(name, disp);
    hex = Attributes.forHexLong(name, disp);
  }

  @Override
  public Long parse(String value) {
    // Accepts 0x…, 0b…, 0… (octal), and decimal with an optional sign.
    return hex.parse(value.trim());
  }

  @Override
  public String toDisplayString(Long value) {
    return hex.toDisplayString(value);
  }

  @Override
  public String toStandardString(Long value) {
    return hex.toStandardString(value);
  }

  @Override
  public String toDisplayString(Long value, AttributeSet context) {
    if (value == null || context == null || !context.containsAttribute(RadixOption.ATTRIBUTE)) {
      return toDisplayString(value);
    }
    final var radix = context.getValue(RadixOption.ATTRIBUTE);
    final var width =
        context.containsAttribute(StdAttr.WIDTH) ? context.getValue(StdAttr.WIDTH).getWidth() : 64;
    return format(value, radix, width);
  }

  @Override
  public java.awt.Component getCellEditor(Window source, Long value, AttributeSet context) {
    return new JTextField(toDisplayString(value, context));
  }

  static String format(long value, RadixOption radix, int width) {
    final var mask = width >= 64 ? -1L : (1L << width) - 1;
    final var bits = value & mask;
    if (radix == RadixOption.RADIX_2) {
      return "0b" + Long.toBinaryString(bits);
    } else if (radix == RadixOption.RADIX_8) {
      return bits == 0 ? "0" : "0" + Long.toOctalString(bits);
    } else if (radix == RadixOption.RADIX_10_UNSIGNED) {
      return Long.toUnsignedString(bits);
    } else if (radix == RadixOption.RADIX_10_SIGNED) {
      final var signed = width >= 64 || width <= 0 ? bits : (bits << (64 - width)) >> (64 - width);
      return Long.toString(signed);
    }
    return "0x" + Long.toHexString(value);
  }
}

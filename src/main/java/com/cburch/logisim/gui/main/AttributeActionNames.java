/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.main;

import static com.cburch.logisim.gui.Strings.S;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.util.StringGetter;
import java.util.Collection;

/** Names for property edits in the undo history, so they say which property changed. */
final class AttributeActionNames {
  private AttributeActionNames() {}

  /**
   * "Change Data Bits" when one property changes, otherwise the general name under {@code
   * fallbackKey}. Resolved when shown, so it follows the current language.
   */
  static StringGetter forAttributes(Collection<? extends Attribute<?>> attrs, String fallbackKey) {
    if (attrs.size() != 1) return S.getter(fallbackKey);
    final var attr = attrs.iterator().next();
    return new StringGetter() {
      @Override
      public String toString() {
        return S.get("changeNamedAttributeAction", attr.getDisplayName());
      }
    };
  }
}

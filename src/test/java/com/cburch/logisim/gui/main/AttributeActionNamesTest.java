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
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.cburch.logisim.instance.StdAttr;
import java.util.List;
import org.junit.jupiter.api.Test;

class AttributeActionNamesTest {

  @Test
  void undoEntryNamesTheSinglePropertyThatChanged() {
    final var name =
        AttributeActionNames.forAttributes(List.of(StdAttr.WIDTH), "changeAttributeAction");
    assertEquals(
        S.get("changeNamedAttributeAction", StdAttr.WIDTH.getDisplayName()), name.toString());
  }

  @Test
  void undoEntryForSeveralPropertiesKeepsTheGeneralName() {
    final var name =
        AttributeActionNames.forAttributes(
            List.of(StdAttr.WIDTH, StdAttr.LABEL), "changeAttributeAction");
    assertEquals(S.get("changeAttributeAction"), name.toString());
  }
}

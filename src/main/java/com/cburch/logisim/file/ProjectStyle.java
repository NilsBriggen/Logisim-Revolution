/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.file;

import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.generated.BuildInfo;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.std.base.Text;
import java.util.Objects;

/**
 * A project's own defaults for how new components look: label font, label colour and the font of
 * new text.
 *
 * <p>They are kept in the project's {@link Options} and saved with it. They apply only when a
 * component is placed, and only to an attribute the tool still has at its shipped value, so a font
 * the user picked on the tool itself wins. Components already in the project keep their style.
 * With nothing set, placing a component works exactly as it always has.
 */
public final class ProjectStyle {

  private ProjectStyle() {
    throw new UnsupportedOperationException("Utility class, do not instantiate.");
  }

  /**
   * Gives the attributes of a component about to be placed the project's style.
   *
   * @param options the project's options; {@code null} leaves the attributes alone
   * @param factory what is being placed, whose defaults tell a shipped value from a chosen one
   * @param attrs the new component's attributes, changed in place
   */
  public static void applyToNewComponent(
      Options options, ComponentFactory factory, AttributeSet attrs) {
    if (options == null || factory == null || attrs == null) return;
    final var style = options.getAttributeSet();
    if (factory instanceof Text) {
      apply(factory, attrs, Text.ATTR_FONT, style.getValue(Options.ATTR_TEXT_FONT));
    } else {
      apply(factory, attrs, StdAttr.LABEL_FONT, style.getValue(Options.ATTR_LABEL_FONT));
      apply(factory, attrs, StdAttr.LABEL_COLOR, style.getValue(Options.ATTR_LABEL_COLOR));
    }
  }

  /** Gives new text from the Text tool the project's text font, if it has one. */
  public static void applyToNewText(Options options, AttributeSet attrs) {
    applyToNewComponent(options, Text.FACTORY, attrs);
  }

  private static <V> void apply(
      ComponentFactory factory, AttributeSet attrs, Attribute<V> attr, V projectValue) {
    if (projectValue == null || !attrs.containsAttribute(attr) || attrs.isReadOnly(attr)) return;
    final var shipped = factory.getDefaultAttributeValue(attr, BuildInfo.version);
    if (shipped != null && !Objects.equals(attrs.getValue(attr), shipped)) return;
    attrs.setValue(attr, projectValue);
  }
}

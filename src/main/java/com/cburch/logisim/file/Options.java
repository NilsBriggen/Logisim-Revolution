/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.file;

import static com.cburch.logisim.file.Strings.S;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.AttributeSets;
import com.cburch.logisim.data.Attributes;
import java.awt.Color;
import java.awt.Font;

public class Options {
  public static final AttributeOption GATE_UNDEFINED_IGNORE =
      new AttributeOption("ignore", S.getter("gateUndefinedIgnore"));
  public static final AttributeOption GATE_UNDEFINED_ERROR =
      new AttributeOption("error", S.getter("gateUndefinedError"));

  public static final Attribute<Integer> ATTR_SIM_LIMIT =
      Attributes.forInteger("simlimit", S.getter("simLimitOption"));
  public static final Attribute<Integer> ATTR_SIM_RAND =
      Attributes.forInteger("simrand", S.getter("simRandomOption"));
  public static final Attribute<AttributeOption> ATTR_GATE_UNDEFINED =
      Attributes.forOption(
          "gateUndefined",
          S.getter("gateUndefinedOption"),
          new AttributeOption[] {GATE_UNDEFINED_IGNORE, GATE_UNDEFINED_ERROR});

  /**
   * The label font new components get in this project, or {@code null} (the default, and never
   * saved) to keep the component's own. See {@link ProjectStyle}.
   */
  public static final Attribute<Font> ATTR_LABEL_FONT =
      Attributes.forFont("styleLabelFont", S.getter("styleLabelFontOption"));
  /** The label colour new components get in this project, or {@code null} for their own. */
  public static final Attribute<Color> ATTR_LABEL_COLOR =
      Attributes.forColor("styleLabelColor", S.getter("styleLabelColorOption"));
  /** The font new text from the Text tool gets in this project, or {@code null} for the usual. */
  public static final Attribute<Font> ATTR_TEXT_FONT =
      Attributes.forFont("styleTextFont", S.getter("styleTextFontOption"));

  public static final Integer SIM_RAND_DFLT = 32;

  private static final Attribute<?>[] ATTRIBUTES = {
    ATTR_GATE_UNDEFINED,
    ATTR_SIM_LIMIT,
    ATTR_SIM_RAND,
    ATTR_LABEL_FONT,
    ATTR_LABEL_COLOR,
    ATTR_TEXT_FONT
  };
  // The style settings default to "not set": an unset value is not written to the file, and a
  // file without them - every file older than them - reads as it always did.
  private static final Object[] DEFAULTS = {GATE_UNDEFINED_IGNORE, 1000, 0, null, null, null};

  private final AttributeSet attrs;
  private final MouseMappings mmappings;
  private final ToolbarData toolbar;

  public Options() {
    attrs = AttributeSets.fixedSet(ATTRIBUTES, DEFAULTS);
    mmappings = new MouseMappings();
    toolbar = new ToolbarData();
  }

  public void copyFrom(Options other, LogisimFile dest) {
    AttributeSets.copy(other.attrs, this.attrs);
    this.toolbar.copyFrom(other.toolbar, dest);
    this.mmappings.copyFrom(other.mmappings, dest);
  }

  public AttributeSet getAttributeSet() {
    return attrs;
  }

  public MouseMappings getMouseMappings() {
    return mmappings;
  }

  public ToolbarData getToolbarData() {
    return toolbar;
  }
}

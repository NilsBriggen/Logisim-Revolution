/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.wiring;

import static com.cburch.logisim.std.Strings.S;

import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.instance.ComponentCaption;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.prefs.AppPreferences;
import com.cburch.logisim.tools.ToolTipMaker;
import com.cburch.logisim.tools.key.BitWidthConfigurator;
import com.cburch.logisim.tools.key.JoinedConfigurator;
import java.awt.Color;
import java.awt.Font;

public class BitExtender extends InstanceFactory {
  /**
   * Unique identifier of the tool, used as reference in project files.
   * Do NOT change as it will prevent project files from loading.
   *
   * Identifier value must MUST be unique string among all tools.
   */
  public static final String _ID = "Bit Extender";

  private static final Attribute<BitWidth> ATTR_IN_WIDTH =
      Attributes.forBitWidth("in_width", S.getter("extenderInAttr"));
  private static final Attribute<BitWidth> ATTR_OUT_WIDTH =
      Attributes.forBitWidth("out_width", S.getter("extenderOutAttr"));
  static final Attribute<AttributeOption> ATTR_TYPE =
      Attributes.forOption(
          "type",
          S.getter("extenderTypeAttr"),
          new AttributeOption[] {
            new AttributeOption("zero", "zero", S.getter("extenderZeroType")),
            new AttributeOption("one", "one", S.getter("extenderOneType")),
            new AttributeOption("sign", "sign", S.getter("extenderSignType")),
            new AttributeOption("input", "input", S.getter("extenderInputType")),
          });

  public static final BitExtender FACTORY = new BitExtender();
  private static final Font CAPTION_FONT = ComponentCaption.FONT.deriveFont(10f);

  public BitExtender() {
    super(_ID, S.getter("extenderComponent"), new BitExtenderHdlGeneratorFactory());
    setIconName("extender.gif");
    setAttributes(
        new Attribute[] {ATTR_IN_WIDTH, ATTR_OUT_WIDTH, ATTR_TYPE},
        new Object[] {BitWidth.create(8), BitWidth.create(16), ATTR_TYPE.parse("sign")});
    setFacingAttribute(StdAttr.FACING);
    setKeyConfigurator(
        JoinedConfigurator.create(
            new BitWidthConfigurator(ATTR_OUT_WIDTH),
            new BitWidthConfigurator(ATTR_IN_WIDTH, 1, Value.MAX_WIDTH, 0)));
    setOffsetBounds(Bounds.create(-40, -20, 40, 40));
  }

  //
  // methods for instances
  //
  @Override
  protected void configureNewInstance(Instance instance) {
    configurePorts(instance);
    instance.addAttributeListener();
  }

  private void configurePorts(Instance instance) {
    Port p0 = new Port(0, 0, Port.OUTPUT, ATTR_OUT_WIDTH);
    Port p1 = new Port(-40, 0, Port.INPUT, ATTR_IN_WIDTH);
    String type = getType(instance.getAttributeSet());
    if (type.equals("input")) {
      instance.setPorts(new Port[] {p0, p1, new Port(-20, -20, Port.INPUT, 1)});
    } else {
      instance.setPorts(new Port[] {p0, p1});
    }
  }

  private String getType(AttributeSet attrs) {
    AttributeOption topt = attrs.getValue(ATTR_TYPE);
    return (String) topt.getValue();
  }

  private String modeCaption(AttributeSet attrs) {
    return switch (getType(attrs)) {
      case "zero" -> S.get("extenderZeroLabel");
      case "one" -> S.get("extenderOneLabel");
      case "sign" -> S.get("extenderSignLabel");
      case "input" -> S.get("extenderInputLabel");
      default -> "???";
    };
  }

  private String widthCaption(AttributeSet attrs) {
    return attrs.getValue(ATTR_IN_WIDTH).getWidth() + "→"
        + attrs.getValue(ATTR_OUT_WIDTH).getWidth();
  }

  @Override
  protected Object getInstanceFeature(Instance instance, Object key) {
    if (key == ToolTipMaker.class) {
      return (ToolTipMaker) event -> modeCaption(instance.getAttributeSet()) + " "
          + S.get("extenderMainLabel") + " " + widthCaption(instance.getAttributeSet());
    }
    return super.getInstanceFeature(instance, key);
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == ATTR_TYPE) {
      configurePorts(instance);
    }
    instance.fireInvalidated();
  }

  //
  // graphics methods
  //
  @Override
  public void paintInstance(InstancePainter painter) {
    final var g = painter.getGraphics();
    g.setColor(new Color(AppPreferences.COMPONENT_COLOR.get()));
    painter.drawBounds();
    final var b = painter.getBounds();
    // Two rows leave room for the top control port and side pin markers. The arrow expresses
    // input/output width without three competing text rows in the unchanged 40-unit body.
    ComponentCaption.draw(g, modeCaption(painter.getAttributeSet()),
        Bounds.create(b.getX() + 3, b.getY() + 6, 34, 13), CAPTION_FONT);
    ComponentCaption.draw(g, widthCaption(painter.getAttributeSet()),
        Bounds.create(b.getX() + 1, b.getY() + 24, 38, 13), CAPTION_FONT);
    painter.drawPorts();
  }

  @Override
  public void propagate(InstanceState state) {
    Value in = state.getPortValue(1);
    BitWidth wout = state.getAttributeValue(ATTR_OUT_WIDTH);
    String type = getType(state.getAttributeSet());
    Value extend;
    switch (type) {
      case "one" -> extend = Value.TRUE;
      case "sign" -> {
        int win = in.getWidth();
        extend = win > 0 ? in.get(win - 1) : Value.ERROR;
      }
      case "input" -> {
        extend = state.getPortValue(2);
        if (extend.getWidth() != 1)
          extend = Value.ERROR;
      }
      default -> extend = Value.FALSE;
    }

    Value out = in.extendWidth(wout.getWidth(), extend);
    state.setPort(0, out, 1);
  }
}

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

import com.cburch.logisim.circuit.PauseReason;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeOption;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.BitWidth;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.gui.canvas.CanvasStyle;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceData;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.tools.key.DirectionConfigurator;
import com.cburch.logisim.util.GraphicsUtil;
import java.awt.Font;
import java.awt.event.KeyEvent;

/**
 * Checks an invariant while the circuit simulates: whenever its enable input is high (or
 * unconnected) and its condition input is low, the assertion fails. A failure pauses the
 * simulator's clock, or only logs, and always fails command-line runs and test vectors.
 *
 * <p>It is a simulation aid with no hardware meaning, so HDL generation leaves it out, as it does
 * a probe.
 */
public class Assertion extends InstanceFactory {
  /**
   * Unique identifier of the tool, used as reference in project files. Do NOT change as it will
   * prevent project files from loading.
   *
   * <p>Identifier value must MUST be unique string among all tools.
   */
  public static final String _ID = "Assertion";

  static final int CONDITION = 0;
  static final int ENABLE = 1;

  static final AttributeOption ACTION_PAUSE =
      new AttributeOption("pause", S.getter("assertionActionPause"));
  static final AttributeOption ACTION_LOG =
      new AttributeOption("log", S.getter("assertionActionLog"));
  static final Attribute<AttributeOption> ATTR_ACTION =
      Attributes.forOption(
          "action", S.getter("assertionActionAttr"), new AttributeOption[] {ACTION_PAUSE, ACTION_LOG});
  static final Attribute<String> ATTR_MESSAGE =
      Attributes.forString("message", S.getter("assertionMessageAttr"));
  static final Attribute<Boolean> ATTR_STRICT =
      Attributes.forBoolean("strict", S.getter("assertionStrictAttr"));

  public static final Assertion FACTORY = new Assertion();

  private static final int SIZE = 40;

  /** Remembers whether the assertion was failing, so a failure is reported once when it starts. */
  private static final class State implements InstanceData, Cloneable {
    boolean failing;

    @Override
    public State clone() {
      try {
        return (State) super.clone();
      } catch (CloneNotSupportedException e) {
        return null;
      }
    }
  }

  public Assertion() {
    super(_ID, S.getter("assertionComponent"));
    setAttributes(
        new Attribute[] {
          StdAttr.FACING,
          ATTR_MESSAGE,
          ATTR_ACTION,
          ATTR_STRICT,
          StdAttr.LABEL,
          StdAttr.LABEL_LOC,
          StdAttr.LABEL_FONT,
        },
        new Object[] {
          Direction.WEST, "", ACTION_PAUSE, false, "", Direction.EAST, StdAttr.DEFAULT_LABEL_FONT,
        });
    setFacingAttribute(StdAttr.FACING);
    setKeyConfigurator(new DirectionConfigurator(StdAttr.LABEL_LOC, KeyEvent.ALT_DOWN_MASK));
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    // As for an LED, the condition input is on the facing side and the body extends away from it.
    final var facing = attrs.getValue(StdAttr.FACING);
    return Bounds.create(0, -SIZE / 2, SIZE, SIZE).rotate(Direction.WEST, facing, 0, 0);
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    instance.addAttributeListener();
    updatePorts(instance);
    instance.computeLabelTextField(Instance.AVOID_LEFT);
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.FACING) {
      instance.recomputeBounds();
      updatePorts(instance);
      instance.computeLabelTextField(Instance.AVOID_LEFT);
    } else if (attr == StdAttr.LABEL_LOC) {
      instance.computeLabelTextField(Instance.AVOID_LEFT);
    }
  }

  private void updatePorts(Instance instance) {
    final var facing = instance.getAttributeValue(StdAttr.FACING);
    // Enable sits in the middle of the body's top side, or of its left side when the body is
    // vertical.
    final var half = SIZE / 2;
    final var enableOffset =
        facing == Direction.EAST
            ? new int[] {-half, -half}
            : facing == Direction.NORTH
                ? new int[] {-half, half}
                : facing == Direction.SOUTH ? new int[] {-half, -half} : new int[] {half, -half};
    final var condition = new Port(0, 0, Port.INPUT, BitWidth.ONE);
    condition.setToolTip(S.getter("assertionConditionTip"));
    final var enable = new Port(enableOffset[0], enableOffset[1], Port.INPUT, BitWidth.ONE);
    enable.setToolTip(S.getter("assertionEnableTip"));
    instance.setPorts(new Port[] {condition, enable});
  }

  @Override
  public boolean isHDLSupportedComponent(AttributeSet attrs) {
    // Simulation only: HDL generation skips it.
    return true;
  }

  /**
   * Returns whether the assertion fails for these inputs: enabled (high or unconnected) and the
   * condition is low, or, in strict mode, the condition is not a defined 0 or 1.
   */
  static boolean fails(Value condition, Value enable, boolean strict) {
    if (enable == Value.FALSE) return false;
    if (condition == Value.FALSE) return true;
    return strict && condition != Value.TRUE;
  }

  @Override
  public void propagate(InstanceState state) {
    var data = (State) state.getData();
    if (data == null) {
      data = new State();
      state.setData(data);
    }
    final var enable =
        state.isPortConnected(ENABLE) ? state.getPortValue(ENABLE) : Value.TRUE;
    final var failing =
        fails(state.getPortValue(CONDITION), enable, state.getAttributeValue(ATTR_STRICT));
    if (failing && !data.failing) {
      state.reportFault(
          new PauseReason(
              PauseReason.Kind.ASSERTION,
              describe(state),
              state.getTickCount(),
              state.getInstance() == null ? null : state.getInstance().getComponent(),
              state.getAttributeValue(ATTR_ACTION) != ACTION_LOG));
    }
    if (failing != data.failing) {
      data.failing = failing;
      state.fireInvalidated();
    }
  }

  private static String describe(InstanceState state) {
    final var message = state.getAttributeValue(ATTR_MESSAGE);
    final var label = state.getAttributeValue(StdAttr.LABEL);
    final var text = message == null || message.isBlank() ? S.get("assertionDefaultMessage") : message;
    return label == null || label.isBlank()
        ? S.get("assertionFailed", text)
        : S.get("assertionFailedLabeled", label, text);
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    final var g = painter.getGraphics();
    final var bds = painter.getBounds();
    final var data = (State) painter.getData();
    final var failing = painter.getShowState() && data != null && data.failing;
    if (failing) {
      g.setColor(Value.errorColor());
      g.fillRect(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight());
    }
    g.setColor(failing ? CanvasStyle.contrastInk(Value.errorColor()) : CanvasStyle.componentColor());
    GraphicsUtil.switchToWidth(g, 2);
    g.drawRect(bds.getX(), bds.getY(), bds.getWidth(), bds.getHeight());
    GraphicsUtil.switchToWidth(g, 1);
    g.setFont(g.getFont().deriveFont(Font.BOLD, 18f));
    GraphicsUtil.drawCenteredText(g, "!", bds.getCenterX(), bds.getCenterY() - 2);
    painter.drawLabel();
    painter.drawPorts();
  }
}

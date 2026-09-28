/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.vhdl.base;

import static com.cburch.logisim.vhdl.Strings.S;

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.data.AttributeSet;
import com.cburch.logisim.data.Attributes;
import com.cburch.logisim.data.Bounds;
import com.cburch.logisim.data.Direction;
import com.cburch.logisim.data.Location;
import com.cburch.logisim.data.Value;
import com.cburch.logisim.fpga.hdlgenerator.HdlGeneratorFactory;
import com.cburch.logisim.gui.canvas.CanvasStyle;
import com.cburch.logisim.gui.icons.ArithmeticIcon;
import com.cburch.logisim.instance.Instance;
import com.cburch.logisim.instance.InstanceComponent;
import com.cburch.logisim.instance.InstanceFactory;
import com.cburch.logisim.instance.InstancePainter;
import com.cburch.logisim.instance.InstanceState;
import com.cburch.logisim.instance.Port;
import com.cburch.logisim.instance.StdAttr;
import com.cburch.logisim.proj.Project;
import com.cburch.logisim.std.wiring.Pin;
import com.cburch.logisim.util.GraphicsUtil;
import com.cburch.logisim.util.StringGetter;
import com.cburch.logisim.util.StringUtil;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.WeakHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VhdlEntity extends InstanceFactory implements HdlModelListener {

  static final Logger logger = LoggerFactory.getLogger(VhdlEntity.class);
  static final Attribute<String> nameAttr = Attributes.forString("vhdlEntity", S.getter("vhdlEntityName"));
  static final ArithmeticIcon icon = new ArithmeticIcon("VHDL");

  static final int WIDTH = 140;
  static final int HEIGHT = 40;
  static final int PORT_GAP = 10;

  static final int X_PADDING = 5;

  private final VhdlContent content;
  private final ArrayList<Instance> myInstances;
  private final ArithmeticIcon typeIcon;

  public VhdlEntity(VhdlContent content) {
    this(content, new VhdlHdlGeneratorFactory(), icon);
  }

  protected VhdlEntity(
      VhdlContent content, HdlGeneratorFactory generator, ArithmeticIcon typeIcon) {
    super("", null, generator, true);
    this.content = content;
    this.typeIcon = typeIcon;
    this.content.addHdlModelListener(this);
    this.setIcon(typeIcon);
    typeIcon.setInvalid(!content.isValid());
    setFacingAttribute(StdAttr.FACING);
    appearance = VhdlAppearance.create(getPins(), getName(), StdAttr.APPEAR_EVOLUTION);
    myInstances = new ArrayList<>();
  }

  public void setSimName(AttributeSet attrs, String sName) {
    if (attrs == null) return;
    final var atrs = (VhdlEntityAttributes) attrs;
    if (atrs.containsAttribute(VhdlSimConstants.SIM_NAME_ATTR))
      atrs.setValue(VhdlSimConstants.SIM_NAME_ATTR, sName);
  }

  public String getSimName(AttributeSet attrs) {
    if (attrs == null) return null;
    final var atrs = (VhdlEntityAttributes) attrs;
    return atrs.getValue(VhdlSimConstants.SIM_NAME_ATTR);
  }

  @Override
  public String getName() {
    if (content == null) return "VHDL Entity";
    else return content.getName();
  }

  @Override
  public StringGetter getDisplayGetter() {
    if (content == null) return S.getter("vhdlComponent");
    else return StringUtil.constantGetter(content.getName());
  }

  public VhdlContent getContent() {
    return content;
  }

  @Override
  protected void configureNewInstance(Instance instance) {
    final var attrs = (VhdlEntityAttributes) instance.getAttributeSet();
    attrs.setInstance(instance);
    instance.addAttributeListener();
    updatePorts(instance);
    if (!myInstances.contains(instance)) myInstances.add(instance);
  }

  @Override
  public AttributeSet createAttributeSet() {
    return new VhdlEntityAttributes(content);
  }

  @Override
  public String getHDLName(AttributeSet attrs) {
    return content.getName().toLowerCase();
  }

  @Override
  public String getHDLTopName(AttributeSet attrs) {
    var label = "";
    final var l = attrs.getValue(StdAttr.LABEL);
    if (!("".equals(l)))
      label = "_" + l.toLowerCase();
    return getHDLName(attrs) + label;
  }

  @Override
  public Bounds getOffsetBounds(AttributeSet attrs) {
    if (appearance == null) return Bounds.create(0, 0, 100, 100);
    final var facing = attrs.getValue(StdAttr.FACING);
    return appearance.getOffsetBounds().rotate(Direction.EAST, facing, 0, 0);
  }

  @Override
  protected void instanceAttributeChanged(Instance instance, Attribute<?> attr) {
    if (attr == StdAttr.FACING) {
      updatePorts(instance);
    } else if (attr == StdAttr.APPEARANCE) {
      for (final var j : myInstances) {
        updatePorts(j);
      }
    }
  }

  @Override
  public void paintInstance(InstancePainter painter) {
    final var attrs = (VhdlEntityAttributes) painter.getAttributeSet();
    final var facing = attrs.getFacing();
    final var gfx = painter.getGraphics();

    final var loc = painter.getLocation();
    gfx.translate(loc.getX(), loc.getY());
    appearance.paintSubcircuit(painter, gfx, facing);
    gfx.translate(-loc.getX(), -loc.getY());

    final var label = painter.getAttributeValue(StdAttr.LABEL);
    if (label != null && painter.getAttributeValue(StdAttr.LABEL_VISIBILITY)) {
      final var bds = painter.getBounds();
      final var oldFont = gfx.getFont();
      final var color = gfx.getColor();
      gfx.setFont(painter.getAttributeValue(StdAttr.LABEL_FONT));
      gfx.setColor(CanvasStyle.labelColor(null));
      GraphicsUtil.drawCenteredText(gfx, label, bds.getX() + bds.getWidth() / 2, bds.getY() - gfx.getFont().getSize());
      gfx.setFont(oldFont);
      gfx.setColor(color);
    }
    painter.drawPorts();
    VhdlSimulationFallback.paintBadge(painter, isSimulatedExternally(painter.getProject()));
  }

  /**
   * Propagate signals through the VHDL component. Logisim doesn't have a VHDL simulation tool. So
   * we need to use an external tool. We send signals to Questasim/Modelsim through a socket and a
   * tcl binder. Then, a simulation step is done and the tcl server sends the output signals back to
   * Logisim. Then we can set the VHDL component output properly.
   *
   * <p>This can be done only if Logisim could connect to the tcl server (socket). This is done in
   * Simulation.java.
   */
  @Override
  public void propagate(InstanceState state) {

    if (isSimulatedExternally(state.getProject())) {

      final var vhdlSimulator = state.getProject().getVhdlSimulator();

      for (final var singlePort : state.getInstance().getPorts()) {
        final var index = state.getPortIndex(singlePort);
        final var val = state.getPortValue(index);
        final var vhdlEntityName = getSimName(state.getAttributeSet());

        String message =
            singlePort.getType()
                + ":"
                + vhdlEntityName
                + "_"
                + singlePort.getToolTip()
                + ":"
                + val.toBinaryString()
                + ":"
                + index;

        vhdlSimulator.send(message);
      }

      vhdlSimulator.send("sync");

      /* Get response from tcl server */
      String serverResponse;
      while ((serverResponse = vhdlSimulator.receive()) != null
          && (serverResponse.length() > 0 && !"sync".equals(serverResponse))) {

        final var parameters = serverResponse.split(":");
        final var busValue = parameters[1];
        final var vectorValues = new Value[busValue.length()];

        var idx = busValue.length() - 1;
        for (final var bit : busValue.toCharArray()) {
          try {
            vectorValues[idx] = switch (Character.getNumericValue(bit)) {
              case 0 -> Value.FALSE;
              case 1 -> Value.TRUE;
              default -> Value.UNKNOWN;
            };
          } catch (NumberFormatException e) {
            vectorValues[idx] = Value.UNKNOWN;
          }
          idx--;
        }

        state.setPort(Integer.parseInt(parameters[2]), Value.create(vectorValues), 1);
      }

      /* VhdlSimulation stopped/disabled */
    } else {
      // Not an error of the circuit: keep the rest of it simulating (see VhdlSimulationFallback).
      VhdlSimulationFallback.driveOutputsUnknown(state);
    }
  }

  /**
   * True when the external simulator computes this component's outputs; otherwise they are
   * unknown and the component shows the fallback badge.
   */
  protected boolean isSimulatedExternally(Project project) {
    return VhdlSimulationFallback.isSimulatorActive(project);
  }

  /**
   * Save the VHDL entity in a file. The file is used for VHDL components simulation by
   * QUestasim/Modelsim
   */
  public void saveFile(AttributeSet attrs) {

    PrintWriter writer;
    try {
      writer =
          new PrintWriter(VhdlSimConstants.SIM_SRC_PATH + getSimName(attrs) + ".vhdl",
              StandardCharsets.UTF_8);

      String content = this.content.getContent();

      content = content.replaceAll("(?i)" + getHDLName(attrs), getSimName(attrs));

      writer.print(content);
      writer.close();
    } catch (IOException e) {
      logger.error("Could not create VHDL file: {}", e.getMessage());     // FIXME: hardcoded string
      e.printStackTrace();
    }
  }

  private VhdlAppearance appearance;

  private ArrayList<Instance> getPins() {
    final var pins = new ArrayList<Instance>();
    var yPos = 0;
    for (final var port : content.getPorts()) {
      final var attr = Pin.FACTORY.createAttributeSet();
      attr.setValue(StdAttr.LABEL, port.getName());
      attr.setValue(Pin.ATTR_TYPE, port.getType() == Port.INPUT ? Pin.INPUT : Pin.OUTPUT);
      attr.setValue(StdAttr.FACING, port.getType() == Port.INPUT ? Direction.EAST : Direction.WEST);
      attr.setValue(StdAttr.WIDTH, port.getWidth());
      final var component = (InstanceComponent) Pin.FACTORY.createComponent(Location.create(100, yPos, true), attr);
      pins.add(component.getInstance());
      yPos += 10;
    }
    return pins;
  }

  void updatePorts(Instance instance) {
    final var style = instance.getAttributeValue(StdAttr.APPEARANCE);
    appearance = VhdlAppearance.create(getPins(), getName(), style);

    final var facing = instance.getAttributeValue(StdAttr.FACING);
    final var portLocs = appearance.getPortOffsets(facing);

    // Port i is always the content's port i, whatever the facing: the appearance returns the pins
    // sorted by location, which puts outputs first when the component faces west, while the HDL
    // generators (and the external simulator) address the ports in declaration order.
    final var declared = content.getPorts();
    final var ports = new Port[portLocs.size()];
    var extra = declared.size();
    for (final var portLoc : portLocs.entrySet()) {
      final var loc = portLoc.getKey();
      final var pin = portLoc.getValue();
      final var label = pin.getAttributeValue(StdAttr.LABEL);
      var idx = -1;
      for (var i = 0; i < declared.size(); i++) {
        if (declared.get(i).getName().equals(label) && ports[i] == null) {
          idx = i;
          break;
        }
      }
      var type = Pin.FACTORY.isInputPin(pin) ? Port.INPUT : Port.OUTPUT;
      if (idx >= 0) {
        type = declared.get(idx).getType();
      } else {
        idx = extra < ports.length ? extra++ : firstFree(ports);
      }
      final var width = pin.getAttributeValue(StdAttr.WIDTH);
      ports[idx] = new Port(loc.getX(), loc.getY(), type, width);

      if (label != null && label.length() > 0) {
        ports[idx].setToolTip(StringUtil.constantGetter(label));
      }
    }
    instance.setPorts(ports);
    instance.recomputeBounds();
  }

  private static int firstFree(Port[] ports) {
    for (var i = 0; i < ports.length; i++) {
      if (ports[i] == null) return i;
    }
    throw new IllegalStateException("more pins than ports");
  }

  @Override
  public void contentSet(HdlModel source) {
    typeIcon.setInvalid(!content.isValid());
    // The entity header may have gained, lost or resized ports: placed instances must follow now,
    // not only after the project is reloaded.
    for (final var instance : myInstances) {
      updatePorts(instance);
      instance.fireInvalidated();
    }
  }

  @Override
  public void appearanceChanged(HdlModel source) {
    for (final var instance : myInstances) {
      updatePorts(instance);
      instance.fireInvalidated();
    }
  }

  private final WeakHashMap<Component, Circuit> circuitsUsingThis = new WeakHashMap<>();

  public Collection<Circuit> getCircuitsUsingThis() {
    return circuitsUsingThis.values();
  }

  public void addCircuitUsing(Component comp, Circuit circ) {
    circuitsUsingThis.put(comp, circ);
  }

  public void removeCircuitUsing(Component comp) {
    circuitsUsingThis.remove(comp);
  }

  @Override
  public void removeComponent(Circuit circ, Component c, CircuitState state) {
    removeCircuitUsing(c);
  }
}

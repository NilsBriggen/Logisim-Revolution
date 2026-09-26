/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.gui.main;

import com.cburch.logisim.circuit.CircuitAttributes;
import com.cburch.logisim.circuit.CircuitEvent;
import com.cburch.logisim.circuit.CircuitListener;
import com.cburch.logisim.circuit.CircuitState;
import com.cburch.logisim.circuit.SubcircuitFactory;
import com.cburch.logisim.comp.Component;
import com.cburch.logisim.comp.ComponentFactory;
import com.cburch.logisim.data.AttributeEvent;
import com.cburch.logisim.data.AttributeListener;
import com.cburch.logisim.instance.StdAttr;
import java.util.ArrayList;
import java.util.Comparator;
import javax.swing.tree.TreeNode;

class SimulationTreeCircuitNode extends SimulationTreeNode
    implements CircuitListener, AttributeListener, Comparator<Component> {
  private final CircuitState circuitState;
  private final Component subcircComp;

  public SimulationTreeCircuitNode(
      SimulationTreeModel model,
      SimulationTreeCircuitNode parent,
      CircuitState circuitState,
      Component subcircComp) {
    super(model, parent);
    this.circuitState = circuitState;
    this.subcircComp = subcircComp;
    circuitState.getCircuit().addCircuitListener(this);
    if (subcircComp != null) {
      subcircComp.getAttributeSet().addAttributeListener(this);
    } else {
      circuitState.getCircuit().getStaticAttributes().addAttributeListener(this);
    }
    computeChildren();
  }

  //
  // AttributeListener methods
  @Override
  public void attributeValueChanged(AttributeEvent e) {
    Object attr = e.getAttribute();
    if (attr == CircuitAttributes.CIRCUIT_LABEL_ATTR || attr == StdAttr.LABEL) {
      model.fireNodeChanged(this);
      // The label is the name the parent sorts by.
      if (parent instanceof SimulationTreeCircuitNode node && node.computeChildren()) {
        model.fireStructureChanged(node);
      }
    }
  }

  @Override
  public void circuitChanged(CircuitEvent event) {
    final var action = event.getAction();
    if (action == CircuitEvent.ACTION_SET_NAME) {
      model.fireNodeChanged(this);
    } else {
      if (computeChildren()) {
        model.fireStructureChanged(this);
      }
    }
  }

  /**
   * Orders subcircuit instances by the name shown for them (label, else circuit name), then top to
   * bottom and left to right. Locations used to be compared as text, so (100,20) came before
   * (20,20).
   */
  @Override
  public int compare(Component a, Component b) {
    if (a == b) return 0;
    final var ret = sortName(a).compareToIgnoreCase(sortName(b));
    if (ret != 0) return ret;
    final var locA = a.getLocation();
    final var locB = b.getLocation();
    if (locA.getY() != locB.getY()) return Integer.compare(locA.getY(), locB.getY());
    return Integer.compare(locA.getX(), locB.getX());
  }

  private static String sortName(Component comp) {
    final var label = comp.getAttributeSet().getValue(StdAttr.LABEL);
    return label != null && !label.isEmpty() ? label : circuitName(comp);
  }

  private static String circuitName(Component comp) {
    return comp.getFactory() instanceof SubcircuitFactory factory
        ? factory.getSubcircuit().getName()
        : comp.getFactory().getDisplayName();
  }

  /**
   * The name a subcircuit instance is listed under: its label with the circuit name, or, when it
   * has no label, the circuit name and where it sits.
   */
  static String displayName(Component comp) {
    final var circuitName = circuitName(comp);
    final var label = comp.getAttributeSet().getValue(StdAttr.LABEL);
    if (label != null && !label.isEmpty()) return label + " (" + circuitName + ")";
    final var loc = comp.getLocation();
    return circuitName + " @ " + loc.getX() + "," + loc.getY();
  }

  // returns true if changed
  private boolean computeChildren() {
    final var newChildren = new ArrayList<TreeNode>();
    final var subcircs = new ArrayList<Component>();
    for (final var comp : circuitState.getCircuit().getNonWires()) {
      if (comp.getFactory() instanceof SubcircuitFactory) {
        subcircs.add(comp);
      } else {
        final var toAdd = model.mapComponentToNode(comp);
        if (toAdd != null) {
          newChildren.add(toAdd);
        }
      }
    }
    newChildren.sort(new CompareByName());
    subcircs.sort(this);
    for (final var comp : subcircs) {
      final var factory = (SubcircuitFactory) comp.getFactory();
      final var state = factory.getSubstate(circuitState, comp);
      SimulationTreeCircuitNode toAdd = null;
      for (final var treeNode : children) {
        if (treeNode instanceof SimulationTreeCircuitNode node) {
          if (node.circuitState == state) {
            toAdd = node;
            break;
          }
        }
      }
      if (toAdd == null) {
        toAdd = new SimulationTreeCircuitNode(model, this, state, comp);
      }
      newChildren.add(toAdd);
    }

    if (!children.equals(newChildren)) {
      children = newChildren;
      return true;
    } else {
      return false;
    }
  }

  public CircuitState getCircuitState() {
    return circuitState;
  }

  @Override
  public ComponentFactory getComponentFactory() {
    return circuitState.getCircuit().getSubcircuitFactory();
  }

  @Override
  public boolean isCurrentView(SimulationTreeModel model) {
    return model.getCurrentView() == circuitState;
  }

  @Override
  public String toString() {
    return subcircComp == null ? circuitState.getCircuit().getName() : displayName(subcircComp);
  }

  private static class CompareByName implements Comparator<Object> {
    @Override
    public int compare(Object a, Object b) {
      return a.toString().compareToIgnoreCase(b.toString());
    }
  }
}

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

import com.cburch.logisim.circuit.Circuit;
import com.cburch.logisim.circuit.CircuitAttributes;
import com.cburch.logisim.circuit.CircuitMutation;
import com.cburch.logisim.circuit.CircuitNameValidator;
import com.cburch.logisim.data.Attribute;
import com.cburch.logisim.gui.generic.AttrTableSetException;
import com.cburch.logisim.gui.generic.AttributeSetTableModel;
import com.cburch.logisim.proj.Project;
import java.util.Map;
import java.util.Objects;

public class AttrTableCircuitModel extends AttributeSetTableModel {
  private final Project proj;
  private final Circuit circ;

  public AttrTableCircuitModel(final Project proj, final Circuit circ) {
    super(circ.getStaticAttributes());
    this.proj = proj;
    this.circ = circ;
  }

  @Override
  public String getTitle() {
    return S.get("circuitAttrTitle", circ.getName());
  }

  @Override
  protected boolean isEditLocked() {
    return circ.isEditLocked();
  }

  @Override
  public String getEditLockNote() {
    return circ.isEditLocked() ? S.get("attrLockedCircuitNote", circ.getName()) : null;
  }

  @Override
  public void setValueRequested(final Attribute<Object> attr, Object value)
      throws AttrTableSetException {
    setValuesRequested(Map.of(attr, value));
  }

  @Override
  protected boolean supportsMultiEdit() {
    return true;
  }

  @Override
  protected void setValuesRequested(Map<Attribute<Object>, Object> values)
      throws AttrTableSetException {
    if (!proj.getLogisimFile().contains(circ)) {
      final var msg = S.get("cannotModifyCircuitError");
      throw new AttrTableSetException(msg);
    } else {
      final var name = values.get((Attribute<?>) CircuitAttributes.NAME_ATTR);
      if (name instanceof String newName && !newName.equals(circ.getName())) {
        // Refused here, the table keeps the typed name for correction instead of reverting it.
        final var problem =
            CircuitNameValidator.problemWith(proj.getLogisimFile(), newName, circ);
        if (problem != null) throw new AttrTableSetException(problem);
      }
      final var xn = new CircuitMutation(circ);
      for (final var entry : values.entrySet()) {
        if (!Objects.equals(
                circ.getStaticAttributes().getValue(entry.getKey()), entry.getValue())) {
          xn.setForCircuit(entry.getKey(), entry.getValue());
        }
      }
      if (!xn.isEmpty()) proj.doAction(xn.toAction(
          AttributeActionNames.forAttributes(values.keySet(), "changeCircuitAttrAction")));
    }
  }
}

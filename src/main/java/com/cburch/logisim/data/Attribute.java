/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.data;

import com.cburch.logisim.util.StringGetter;
import java.awt.Window;
import javax.swing.JTextField;

public abstract class Attribute<V> {
  private final String name;
  private final StringGetter displayName;
  private boolean hidden;

  /**
   * A sentence saying what this attribute does, or {@code null} when none has been written.
   *
   * <p>Attributes were a bare name and a value, so the only way to find out what one did was to
   * change it and watch, or to go and read the documentation. Held lazily as a {@link
   * StringGetter} like the display name, so it follows the chosen language.
   */
  private StringGetter description;

  public Attribute() {
    this("dummy", null, true);
  }

  public Attribute(String name, StringGetter disp) {
    this(name, disp, false);
  }

  public Attribute(String name, StringGetter disp, boolean hidden) {
    this.name = name;
    this.displayName = disp;
    this.hidden = hidden;
  }

  protected java.awt.Component getCellEditor(V value) {
    return new JTextField(toDisplayString(value));
  }

  public java.awt.Component getCellEditor(Window source, V value) {
    return getCellEditor(value);
  }

  /**
   * The editor for a value that belongs to {@code context}, for attributes whose text depends on
   * other attributes of the same set (see {@link #toDisplayString(Object, AttributeSet)}).
   */
  public java.awt.Component getCellEditor(Window source, V value, AttributeSet context) {
    return getCellEditor(source, value);
  }

  public String getDisplayName() {
    return (displayName != null) ? displayName.toString() : name;
  }

  /**
   * Adds the sentence describing this attribute.
   *
   * <p>Meant to be chained onto the declaration, which is run once for each attribute at class
   * load: {@code Attributes.forBoolean("x", S.getter("xAttr")).withDescription(S.getter("xDesc"))}.
   *
   * @return this attribute, so the call can be chained
   */
  public Attribute<V> withDescription(StringGetter description) {
    this.description = description;
    return this;
  }

  /** What this attribute does, or {@code null} when nobody has written it down yet. */
  public String getDescription() {
    return (description == null) ? null : description.toString();
  }

  public String getName() {
    return name;
  }

  public V parse(Window source, String value) {
    return parse(value);
  }

  public abstract V parse(String value);

  /**
   * The text shown for a value that belongs to {@code context}. Most attributes do not care; one
   * whose presentation follows another attribute of the same set (a pin's reset value follows the
   * pin's radix) overrides this. {@link #parse(String)} must accept what this returns.
   */
  public String toDisplayString(V value, AttributeSet context) {
    return toDisplayString(value);
  }

  public String toDisplayString(V value) {
    return value == null ? "" : value.toString();
  }

  public String toStandardString(V value) {
    return value.toString().replaceAll("[\u0000-\u001f]", "").replaceAll("&#.*?;", "");
  }

  public void setHidden(boolean val) {
    this.hidden = val;
  }

  public boolean isHidden() {
    return hidden;
  }

  public boolean isToSave() {
    return true;
  }

  @Override
  public String toString() {
    return name;
  }
}

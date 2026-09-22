/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.util;

import java.awt.Insets;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

/**
 * Shared spacing scale for dialogs, panels and custom painted widgets.
 *
 * <p>Gaps and paddings used to be written as bare integers at each call site, which is why the
 * application's forms line up inconsistently. Picking a step from this class keeps that rhythm
 * uniform, and every value is passed through {@link UiScale} so layouts follow the user's interface
 * scale preference.
 *
 * <p>The steps follow a 4 pixel grid. Prefer the smallest step that reads clearly: {@link #xs()}
 * separates tightly related controls, {@link #sm()} is the default gap inside a group, {@link #md()}
 * pads a panel, and the larger steps separate unrelated groups.
 */
public final class Spacing {

  /** Design-time size, in unscaled pixels, of the smallest step. */
  public static final int XS = 4;

  /** Design-time size, in unscaled pixels, of the default intra-group gap. */
  public static final int SM = 8;

  /** Design-time size, in unscaled pixels, of the default panel padding. */
  public static final int MD = 12;

  /** Design-time size, in unscaled pixels, of the gap between groups. */
  public static final int LG = 16;

  /** Design-time size, in unscaled pixels, of the gap between major sections. */
  public static final int XL = 24;

  private Spacing() {
    // Utility class, not instantiable.
  }

  /** Returns {@link #XS} in device pixels. */
  public static int xs() {
    return UiScale.scaled(XS);
  }

  /** Returns {@link #SM} in device pixels. */
  public static int sm() {
    return UiScale.scaled(SM);
  }

  /** Returns {@link #MD} in device pixels. */
  public static int md() {
    return UiScale.scaled(MD);
  }

  /** Returns {@link #LG} in device pixels. */
  public static int lg() {
    return UiScale.scaled(LG);
  }

  /** Returns {@link #XL} in device pixels. */
  public static int xl() {
    return UiScale.scaled(XL);
  }

  /** Returns the standard padding for the inside edge of a panel or dialog. */
  public static Border panelBorder() {
    return border(MD);
  }

  /** Returns the standard padding for a panel nested inside another padded panel. */
  public static Border innerBorder() {
    return border(SM);
  }

  /** Returns an empty border whose four sides are the given unscaled value. */
  public static Border border(int unscaled) {
    final var scaled = UiScale.scaled(unscaled);
    return new EmptyBorder(scaled, scaled, scaled, scaled);
  }

  /** Returns an empty border built from unscaled values, scaled per side. */
  public static Border border(int top, int left, int bottom, int right) {
    return new EmptyBorder(
        UiScale.scaled(top), UiScale.scaled(left), UiScale.scaled(bottom), UiScale.scaled(right));
  }

  /** Returns the insets a form should leave between its rows and columns. */
  public static Insets formGaps() {
    final var vertical = UiScale.scaled(XS);
    final var horizontal = UiScale.scaled(SM);
    return new Insets(vertical, horizontal, vertical, horizontal);
  }
}

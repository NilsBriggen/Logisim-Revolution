/*
 * Logisim-evolution - digital logic design tool and simulator
 * Copyright by the Logisim-evolution developers
 *
 * https://github.com/logisim-evolution/
 *
 * This is free software released under GNU GPLv3 license
 */

package com.cburch.logisim.std.ttl;

/**
 * TTL 74574: octal rising-edge D flip-flops with three-state outputs, inputs on one side of the
 * package and outputs on the other (flow-through pinout of the 74374).
 *
 * <p>Model based on the Texas Instruments SN74LS574 data sheet.
 */
public class Ttl74574 extends AbstractOctalTriStateFlops {
  /**
   * Unique identifier of the tool, used as reference in project files. Do NOT change as it will
   * prevent project files from loading.
   *
   * <p>Identifier value must MUST be unique string among all tools.
   */
  public static final String _ID = "74574";

  public Ttl74574() {
    super(
        _ID,
        new byte[] {12, 13, 14, 15, 16, 17, 18, 19},
        new String[] {
          "nOE (Output enable, active LOW)", "D1", "D2", "D3", "D4", "D5", "D6", "D7", "D8", "CLK",
          "Q8", "Q7", "Q6", "Q5", "Q4", "Q3", "Q2", "Q1"
        },
        new int[] {1, 2, 3, 4, 5, 6, 7, 8},
        new int[] {17, 16, 15, 14, 13, 12, 11, 10},
        new String[] {
          "OE", "D1", "D2", "D3", "D4", "D5", "D6", "D7", "D8", null,
          "CLK", "Q8", "Q7", "Q6", "Q5", "Q4", "Q3", "Q2", "Q1", null
        });
  }
}
